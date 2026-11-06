package com.businesstracker.mileage.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.coordinator.TripFinalizationCoordinator
import com.businesstracker.mileage.domain.model.FilterResult
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.model.Waypoint
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.domain.usecase.FilterGpsPointUseCase
import com.businesstracker.mileage.domain.usecase.FinalizeTripUseCase
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

    companion object {
        const val ACTION_START = "com.businesstracker.mileage.ACTION_START"
        const val ACTION_PAUSE = "com.businesstracker.mileage.ACTION_PAUSE"
        const val ACTION_RESUME = "com.businesstracker.mileage.ACTION_RESUME"
        const val ACTION_STOP = "com.businesstracker.mileage.ACTION_STOP"

        const val EXTRA_TRIP_ID = "EXTRA_TRIP_ID"
        const val EXTRA_TRACKING_METHOD = "EXTRA_TRACKING_METHOD"
        const val EXTRA_SUGGESTED_BUSINESS = "EXTRA_SUGGESTED_BUSINESS"

        private const val WAKELOCK_TAG = "BusinessMileageTracker::TrackingWakeLock"
        private const val METERS_PER_MILE = 1609.344
        private const val LOCATION_INTERVAL_MILLIS = 3000L
        private const val MIN_LOCATION_INTERVAL_MILLIS = 1500L

        private val _serviceState = MutableStateFlow(TrackingServiceState())
        val serviceState: StateFlow<TrackingServiceState> = _serviceState.asStateFlow()

        // Atomic guard ensuring single-session initialization and preventing race conditions
        private val isStartingOrRunning = AtomicBoolean(false)
        private val isDrainingGps = AtomicBoolean(false)

        fun isDraining(): Boolean = isDrainingGps.get()

        fun isTrackingRunning(): Boolean = isStartingOrRunning.get() || _serviceState.value.isTracking || isDrainingGps.get()

        fun setDrainingForTesting(draining: Boolean) {
            isDrainingGps.set(draining)
        }

        fun resetGuardForTesting() {
            isStartingOrRunning.set(false)
            isDrainingGps.set(false)
            _serviceState.value = TrackingServiceState()
        }
    }

    @Inject lateinit var fusedLocationClient: FusedLocationProviderClient
    @Inject lateinit var filterGpsPointUseCase: FilterGpsPointUseCase
    @Inject lateinit var activeTripRepository: ActiveTripRepository
    @Inject lateinit var tripFinalizationCoordinator: TripFinalizationCoordinator
    @Inject lateinit var finalizeTripUseCase: FinalizeTripUseCase
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var preferencesManager: TrackingPreferencesManager
    @Inject lateinit var ioDispatcher: CoroutineDispatcher

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var tickerJob: Job? = null
    private var gpsLocationQueue: GpsLocationQueue? = null

    private val isStopping = AtomicBoolean(false)
    private var currentTripId: String = ""
    private var trackingMethod: TrackingMethod = TrackingMethod.AUTO_DETECTED
    private var tripStartTime: Long = 0L
    private var totalDistanceMeters: Double = 0.0
    private var lastAcceptedWaypoint: Waypoint? = null
    private var anchorWaypoint: Waypoint? = null
    private var isDisplacementConfirmed: Boolean = false
    private var isPaused: Boolean = false
    private var waypointCount: Int = 0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (isPaused || isStopping.get()) return
            gpsLocationQueue?.enqueueBatch(result.locations)
        }
    }

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val tripId = intent.getStringExtra(EXTRA_TRIP_ID) ?: UUID.randomUUID().toString()
                val methodStr = intent.getStringExtra(EXTRA_TRACKING_METHOD) ?: TrackingMethod.AUTO_DETECTED.value
                val suggestedBusiness = intent.getStringExtra(EXTRA_SUGGESTED_BUSINESS) ?: "MySucculentCorner"
                startTracking(tripId, TrackingMethod.fromString(methodStr), suggestedBusiness)
            }
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY // System will recreate service if killed, enabling interruption recovery
    }

    private fun startTracking(
        tripId: String,
        method: TrackingMethod,
        suggestedBusiness: String
    ) {
        if (isStartingOrRunning.getAndSet(true)) {
            // Atomic guard: synchronous race-free check preventing duplicate starts
            return
        }

        try {
            isStopping.set(false)
            currentTripId = tripId
            trackingMethod = method
            isPaused = false

            // Start Foreground immediately with initial notification
            val initialNotification = notificationHelper.buildTrackingNotification(
                distanceMiles = 0.0,
                durationMillis = 0L,
                isPaused = false
            )

            val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }

            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_TRACKING_ID,
                initialNotification,
                foregroundType
            )

            // Initialize serialized GPS location queue with non-loss backpressure
            gpsLocationQueue?.cancelEmergency()
            gpsLocationQueue = GpsLocationQueue(
                scope = serviceScope,
                dispatcher = ioDispatcher,
                maxCapacity = 1000,
                bufferDir = cacheDir
            ) { location ->
                handleLocationSerialized(location)
            }

            serviceScope.launch(ioDispatcher) {
                try {
                    // Database-backed guard ensures only one active trip exists
                    val activeTrip = activeTripRepository.createActiveTrip(
                        tripId = currentTripId,
                        latitude = 0.0,
                        longitude = 0.0,
                        trackingMethod = trackingMethod.value,
                        suggestedBusiness = suggestedBusiness
                    )
                    currentTripId = activeTrip.id
                    preferencesManager.setActiveTripId(currentTripId)

                    val existingTrip = if (activeTrip.totalDistanceMeters > 0.0 || activeTrip.id != tripId) activeTrip else null

                    if (existingTrip != null) {
                        // Resume in-progress trip after process termination or system recovery
                        tripStartTime = existingTrip.startTime
                        totalDistanceMeters = existingTrip.totalDistanceMeters
                        if (totalDistanceMeters >= 200.0) {
                            isDisplacementConfirmed = true
                        }
                        val recoveredWaypoints = activeTripRepository.getWaypoints(currentTripId)
                        waypointCount = recoveredWaypoints.size
                        lastAcceptedWaypoint = recoveredWaypoints.lastOrNull()
                        anchorWaypoint = recoveredWaypoints.firstOrNull()
                    } else {
                        // New tracking session
                        tripStartTime = activeTrip.startTime
                        totalDistanceMeters = 0.0
                        waypointCount = 0
                        lastAcceptedWaypoint = null
                        anchorWaypoint = null
                        isDisplacementConfirmed = false
                    }

                    // Register location updates with FusedLocationProviderClient asynchronously
                    val updatesRegistered = startLocationUpdates()
                    if (isStopping.get()) {
                        // Avoid racing startup cleanup against an already-started stop request
                        return@launch
                    }

                    if (!updatesRegistered) {
                        Log.e("LocationTrackingService", "Location updates could not be started; cleaning up failed startup")
                        if (currentTripId.isNotEmpty()) {
                            try {
                                activeTripRepository.clearActiveTrip(currentTripId)
                                preferencesManager.clearActiveTripId(currentTripId)
                            } catch (_: Exception) {}
                        }
                        cleanupFailedStartup()
                        return@launch
                    }

                    startDurationTicker()
                    updateServiceState()
                } catch (e: Exception) {
                    Log.e("LocationTrackingService", "Failed to initialize active trip in database", e)
                    cleanupFailedStartup()
                }
            }
        } catch (e: Exception) {
            Log.e("LocationTrackingService", "Failed to start foreground tracking service", e)
            cleanupFailedStartup()
        }
    }

    private fun cleanupFailedStartup() {
        isStartingOrRunning.set(false)
        isStopping.set(false)
        isDrainingGps.set(false)
        gpsLocationQueue?.cancelEmergency()
        tickerJob?.cancel()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (_: Exception) {}
        }
        _serviceState.value = TrackingServiceState(isTracking = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private suspend fun startLocationUpdates(): Boolean {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MILLIS
        ).apply {
            setMinUpdateIntervalMillis(MIN_LOCATION_INTERVAL_MILLIS)
            setWaitForAccurateLocation(false)
        }.build()

        return try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            ).await()
            true
        } catch (e: SecurityException) {
            Log.e("LocationTrackingService", "Location permission missing when requesting updates", e)
            false
        } catch (e: Exception) {
            Log.e("LocationTrackingService", "Failed to register location updates: ${e.message}", e)
            false
        }
    }

    private suspend fun handleLocationSerialized(location: Location) {
        if (isPaused) return

        when (val filterResult = filterGpsPointUseCase.filter(location, lastAcceptedWaypoint)) {
            is FilterResult.Accepted -> {
                val acceptedWaypoint = filterResult.waypoint
                totalDistanceMeters += filterResult.deltaDistanceMeters
                lastAcceptedWaypoint = acceptedWaypoint
                waypointCount++

                if (anchorWaypoint == null) {
                    anchorWaypoint = acceptedWaypoint
                }

                // Continuous drive displacement verification:
                // Verifies movement >= 200m during an uninterrupted drive
                if (!isDisplacementConfirmed) {
                    val distanceResults = FloatArray(1)
                    Location.distanceBetween(
                        anchorWaypoint!!.latitude,
                        anchorWaypoint!!.longitude,
                        acceptedWaypoint.latitude,
                        acceptedWaypoint.longitude,
                        distanceResults
                    )
                    if (distanceResults[0] >= 200.0f || totalDistanceMeters >= 200.0) {
                        isDisplacementConfirmed = true
                    }
                }

                // Interruption Recovery: Persist waypoint and progress directly to Room
                activeTripRepository.recordWaypoint(currentTripId, acceptedWaypoint)
                activeTripRepository.updateTripProgress(
                    tripId = currentTripId,
                    distanceMeters = totalDistanceMeters,
                    latitude = acceptedWaypoint.latitude,
                    longitude = acceptedWaypoint.longitude,
                    timestamp = acceptedWaypoint.timestamp
                )

                updateNotificationAndState(filterResult.speedMph)
            }
            is FilterResult.Rejected -> {
                // Ignore stationary jitter or inaccurate points without corrupting mileage
            }
        }
    }

    private fun pauseTracking() {
        isPaused = true
        serviceScope.launch(ioDispatcher) {
            activeTripRepository.setPaused(currentTripId, true)
            updateNotificationAndState(0.0)
        }
    }

    private fun resumeTracking() {
        isPaused = false
        serviceScope.launch(ioDispatcher) {
            activeTripRepository.setPaused(currentTripId, false)
            updateNotificationAndState(0.0)
        }
    }

    private fun stopTracking() {
        if (!isStopping.compareAndSet(false, true)) {
            return
        }
        isDrainingGps.set(true)
        serviceScope.launch(ioDispatcher) {
            try {
                // 1. Stop receiving new location callbacks
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (e: Exception) {
                Log.w("LocationTrackingService", "Error removing location updates: ${e.message}")
            }

            // 2. Stop ticker
            tickerJob?.cancel()

            // 3. Drain all queued GPS locations before finalizing
            try {
                gpsLocationQueue?.drainAndClose()
            } catch (e: Exception) {
                Log.e("LocationTrackingService", "Waypoint persistence failed during tracking; halting finalization: ${e.message}", e)
                isDrainingGps.set(false)
                _serviceState.value = TrackingServiceState(isTracking = false)
                isStartingOrRunning.set(false)
                isStopping.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            isDrainingGps.set(false)

            // 4. False trip prevention: if movement is under 200m and not confirmed, discard
            val distanceMiles = totalDistanceMeters / METERS_PER_MILE
            if (!isDisplacementConfirmed && totalDistanceMeters < 200.0 && distanceMiles < 0.12) {
                if (currentTripId.isNotEmpty()) {
                    tripFinalizationCoordinator.discardFalseTrip(currentTripId)
                    preferencesManager.clearActiveTripId(currentTripId)
                }
                _serviceState.value = TrackingServiceState(isTracking = false)
                isStartingOrRunning.set(false)
                isStopping.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            // 5. Finalize trip using single-owner idempotent coordinator
            if (currentTripId.isNotEmpty()) {
                val finalizedTrip = tripFinalizationCoordinator.finalizeTrip(currentTripId, trackingMethod)
                finalizedTrip?.let { trip ->
                    if (trip.classification == com.businesstracker.mileage.domain.model.TripClassification.UNREVIEWED.value) {
                        notificationHelper.showTripDetectedNotification(trip.id, trip.totalDistanceMiles)
                    }
                }
                preferencesManager.clearActiveTripId(currentTripId)
            }

            _serviceState.value = TrackingServiceState(isTracking = false)
            isStartingOrRunning.set(false)
            isStopping.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startDurationTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                if (!isPaused) {
                    updateNotificationAndState(lastAcceptedWaypoint?.speed?.toDouble() ?: 0.0)
                }
            }
        }
    }

    private fun updateNotificationAndState(currentSpeedMph: Double) {
        val miles = totalDistanceMeters / METERS_PER_MILE
        val duration = System.currentTimeMillis() - tripStartTime

        notificationHelper.updateTrackingNotification(
            distanceMiles = miles,
            durationMillis = duration,
            isPaused = isPaused
        )

        _serviceState.value = TrackingServiceState(
            isTracking = true,
            isPaused = isPaused,
            tripId = currentTripId,
            distanceMeters = totalDistanceMeters,
            distanceMiles = miles,
            durationMillis = duration,
            currentSpeedMph = currentSpeedMph,
            lastWaypoint = lastAcceptedWaypoint,
            waypointCount = waypointCount
        )
    }

    private fun updateServiceState() {
        val miles = totalDistanceMeters / METERS_PER_MILE
        val duration = if (tripStartTime > 0) System.currentTimeMillis() - tripStartTime else 0L

        _serviceState.value = TrackingServiceState(
            isTracking = true,
            isPaused = isPaused,
            tripId = currentTripId,
            distanceMeters = totalDistanceMeters,
            distanceMiles = miles,
            durationMillis = duration,
            currentSpeedMph = 0.0,
            lastWaypoint = lastAcceptedWaypoint,
            waypointCount = waypointCount
        )
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG
        ).apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L) // Safety timeout: 12 hours
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isStartingOrRunning.set(false)
        isStopping.set(false)
        isDrainingGps.set(false)
        gpsLocationQueue?.cancelEmergency()
        tickerJob?.cancel()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (_: Exception) {}
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
