package com.businesstracker.mileage.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.GpsInboxDao
import com.businesstracker.mileage.data.local.TripLifecycleDao
import com.businesstracker.mileage.data.local.TripSequenceDao
import com.businesstracker.mileage.data.local.entity.TripLifecycleEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleState
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.coordinator.TripFinalizationCoordinator
import com.businesstracker.mileage.domain.model.FilterResult
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.model.Waypoint
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.domain.service.BootCountProvider
import com.businesstracker.mileage.domain.tracking.StationaryAutoStopMonitor
import com.businesstracker.mileage.domain.tracking.StationaryWatchdogPolicy
import com.businesstracker.mileage.domain.usecase.DrainPendingGpsFixesUseCase
import com.businesstracker.mileage.domain.usecase.FilterGpsPointUseCase
import com.businesstracker.mileage.domain.usecase.FinalizeTripUseCase
import com.businesstracker.mileage.domain.usecase.GpsInboxProcessor
import com.businesstracker.mileage.worker.TripDebounceWorker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.businesstracker.mileage.data.preferences.StationaryEvidence
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
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
        const val EXTRA_DEBOUNCE_TRIGGER = "EXTRA_DEBOUNCE_TRIGGER"
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
    @Inject lateinit var gpsInboxDao: GpsInboxDao
    @Inject lateinit var drainPendingGpsFixesUseCase: DrainPendingGpsFixesUseCase
    @Inject lateinit var tripLifecycleDao: TripLifecycleDao
    @Inject lateinit var tripSequenceDao: TripSequenceDao
    @Inject lateinit var database: ExpenseTrackerDatabase
    @Inject lateinit var bootCountProvider: BootCountProvider
    @Inject lateinit var ioDispatcher: CoroutineDispatcher

    private val safeBootCountProvider: BootCountProvider
        get() = if (::bootCountProvider.isInitialized) bootCountProvider else object : BootCountProvider {
            override fun getBootCount(): Int = 1
        }

    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var tickerJob: Job? = null
    private var gpsLocationQueue: GpsLocationQueue? = null
    private var gpsHandlerThread: HandlerThread? = null

    @VisibleForTesting
    internal var lastRecoveryJob: Job? = null

    private val isStopping = AtomicBoolean(false)
    private var currentTripId: String = ""
    private var trackingMethod: TrackingMethod = TrackingMethod.AUTO_DETECTED
    private var tripStartTime: Long = 0L
    private var totalDistanceMeters: Double = 0.0
    private var currentSpeedMph: Double = 0.0
    private var lastAcceptedWaypoint: Waypoint? = null
    private var anchorWaypoint: Waypoint? = null
    private var isDisplacementConfirmed: Boolean = false
    private var isPaused: Boolean = false
    private var waypointCount: Int = 0
    private val stationaryAutoStopMonitor = StationaryAutoStopMonitor()
    private val stationaryAutoStopRequested = AtomicBoolean(false)
    private var lastStationaryRecheckElapsed = 0L
    private val evidenceWriteMutex = Mutex()
    private val evidenceRevision = AtomicLong(0L)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (isPaused || isStopping.get()) return
            gpsLocationQueue?.enqueueBatch(result.locations)
            evaluateStationaryAutoStop(result.locations)
        }
    }

    private fun evaluateStationaryAutoStop(locations: List<Location>) {
        if (trackingMethod != TrackingMethod.AUTO_DETECTED ||
            !isDisplacementConfirmed ||
            stationaryAutoStopRequested.get()
        ) return

        val orderedLocations = locations.sortedBy { it.elapsedRealtimeNanos }
        var shouldStop = false
        orderedLocations.forEachIndexed { index, location ->
            val elapsedMillis = if (location.elapsedRealtimeNanos > 0L) {
                location.elapsedRealtimeNanos / 1_000_000L
            } else {
                android.os.SystemClock.elapsedRealtime()
            }
            shouldStop = stationaryAutoStopMonitor.record(
                latitude = location.latitude,
                longitude = location.longitude,
                speedMetersPerSecond = if (location.hasSpeed()) location.speed else 0f,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
                elapsedRealtimeMillis = elapsedMillis,
                allowStop = index == orderedLocations.lastIndex
            )
        }

        val evidence = stationaryAutoStopMonitor.snapshot()?.let {
            StationaryEvidence(currentTripId, it.latitude, it.longitude, it.elapsedMillis,
                safeBootCountProvider.getBootCount())
        }
        val tripAtCall = currentTripId
        val revision = evidenceRevision.incrementAndGet()
        serviceScope.launch(ioDispatcher) {
            evidenceWriteMutex.withLock {
                if (revision == evidenceRevision.get() && currentTripId == tripAtCall) {
                    preferencesManager.saveStationaryEvidence(evidence)
                }
            }
        }

        if (shouldStop && stationaryAutoStopRequested.compareAndSet(false, true)) {
            serviceScope.launch(ioDispatcher) {
                preferencesManager.setInVehicleState(false)
                preferencesManager.setBluetoothDisconnectPending(false)
                TripDebounceWorker.cancelDebounce(applicationContext)
                stopTracking()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == null) {
            recoverActiveTripSession()
            return START_STICKY
        }
        when (intent.action) {
            ACTION_START -> {
                val tripId = intent.getStringExtra(EXTRA_TRIP_ID) ?: UUID.randomUUID().toString()
                val methodStr = intent.getStringExtra(EXTRA_TRACKING_METHOD) ?: TrackingMethod.AUTO_DETECTED.value
                val suggestedBusiness = intent.getStringExtra(EXTRA_SUGGESTED_BUSINESS) ?: "MySucculentCorner"
                startTracking(tripId, TrackingMethod.fromString(methodStr), suggestedBusiness)
            }
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> {
                val expectedTripId = intent.getStringExtra(EXTRA_TRIP_ID)
                val trigger = intent.getStringExtra(EXTRA_DEBOUNCE_TRIGGER)
                if (trigger == null) {
                    if (expectedTripId == null || expectedTripId == currentTripId) stopTracking()
                } else if (expectedTripId != null) {
                    serviceScope.launch(ioDispatcher) {
                        if (currentTripId == expectedTripId && trackingMethod == TrackingMethod.AUTO_DETECTED &&
                            !preferencesManager.isInVehicle() &&
                            (trigger != TripDebounceWorker.TRIGGER_BLUETOOTH ||
                                (preferencesManager.isBluetoothAutoStopActive() &&
                                    preferencesManager.isBluetoothDisconnectPending()))
                        ) stopTracking()
                    }
                }
            }
            else -> recoverActiveTripSession()
        }
        return START_STICKY // System will recreate service if killed, enabling interruption recovery
    }

    @VisibleForTesting
    internal var locationUpdatesRegistrar: (suspend () -> Boolean)? = null

    @VisibleForTesting
    internal fun attachContextForTesting(context: Context) {
        attachBaseContext(context)
    }

    /**
     * Safe process recreation recovery when the OS restarts the service with a null intent.
     * Enters foreground promptly on main thread before lengthy operations to satisfy Android 14+ requirements.
     * Inspects persistent active trip and recovers existing trip state without generating new IDs.
     */
    private fun recoverActiveTripSession() {
        if (isStartingOrRunning.getAndSet(true)) {
            // Service already starting or tracking
            return
        }

        // Check location permissions safely without crashing
        val hasFine = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Log.w("LocationTrackingService", "Location permissions missing during process recreation; preserving active trip state.")
            lastRecoveryJob = serviceScope.launch(ioDispatcher) {
                try {
                    val activeTrip = activeTripRepository.getActiveTrip()
                    if (activeTrip != null) {
                        try {
                            tripLifecycleDao.updateState(
                                activeTrip.id,
                                TripLifecycleState.RECOVERY_REQUIRED.value
                            )
                        } catch (_: Exception) {}
                        _serviceState.value = TrackingServiceState(
                            isTracking = false,
                            isRecoveryRequired = true,
                            tripId = activeTrip.id,
                            distanceMiles = activeTrip.totalDistanceMeters * 0.000621371,
                            statusMessage = "Drive Interrupted: Permission Required"
                        )
                    }
                } catch (_: Exception) {}
            }
            isStartingOrRunning.set(false)
            stopSelf()
            return
        }

        // Satisfy Android foreground service timing requirements immediately on main thread
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

        lastRecoveryJob = serviceScope.launch(ioDispatcher) {
            try {
                val activeTrip = activeTripRepository.getActiveTrip()
                if (activeTrip == null) {
                    Log.d("LocationTrackingService", "Null intent received but no active trip found; stopping service.")
                    isStartingOrRunning.set(false)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                isStopping.set(false)
                stationaryAutoStopMonitor.reset()
                stationaryAutoStopRequested.set(false)
                currentTripId = activeTrip.id
                trackingMethod = TrackingMethod.fromString(activeTrip.trackingMethod)
                tripStartTime = activeTrip.startTime
                totalDistanceMeters = activeTrip.totalDistanceMeters
                isPaused = activeTrip.isPaused
                currentSpeedMph = 0.0

                preferencesManager.setActiveTripId(currentTripId)

                // Update notification with recovered metrics
                notificationHelper.updateTrackingNotification(
                    distanceMiles = totalDistanceMeters * 0.000621371,
                    durationMillis = maxOf(0L, System.currentTimeMillis() - tripStartTime),
                    isPaused = isPaused
                )

                // Recover waypoints and displacement confirmation state
                val recoveredWaypoints = activeTripRepository.getWaypoints(currentTripId)
                waypointCount = recoveredWaypoints.size
                lastAcceptedWaypoint = recoveredWaypoints.lastOrNull()
                anchorWaypoint = recoveredWaypoints.firstOrNull() ?: if (activeTrip.startLatitude != 0.0 || activeTrip.startLongitude != 0.0) {
                    Waypoint(
                        latitude = activeTrip.startLatitude,
                        longitude = activeTrip.startLongitude,
                        timestamp = activeTrip.startTime
                    )
                } else null

                if (totalDistanceMeters >= 200.0) {
                    isDisplacementConfirmed = true
                } else if (anchorWaypoint != null && lastAcceptedWaypoint != null) {
                    val distanceResults = FloatArray(1)
                    Location.distanceBetween(
                        anchorWaypoint!!.latitude,
                        anchorWaypoint!!.longitude,
                        lastAcceptedWaypoint!!.latitude,
                        lastAcceptedWaypoint!!.longitude,
                        distanceResults
                    )
                    if (distanceResults[0] >= 200.0f) {
                        isDisplacementConfirmed = true
                    }
                }

                restoreStationaryEvidence()

                // Initialize durable Room-backed GPS location queue
                gpsLocationQueue?.stopConsumer()
                gpsLocationQueue = GpsLocationQueue(
                    scope = serviceScope,
                    dispatcher = ioDispatcher,
                    gpsInboxDao = gpsInboxDao,
                    tripIdProvider = { currentTripId },
                    batchFetchLimit = 100,
                    drainPendingGpsFixesUseCase = drainPendingGpsFixesUseCase,
                    tripSequenceDao = tripSequenceDao,
                    database = database,
                    bootCountProvider = safeBootCountProvider,
                    onFixProcessed = { result ->
                        handleFixProcessed(result)
                    }
                )

                // Reconcile sequence monotonically
                val initialSeq = tripSequenceDao.getReconciledMaxSequence(currentTripId)
                gpsLocationQueue?.admissionBarrier?.initializeSequence(initialSeq)

                // Update authoritative lifecycle in Room
                try {
                    tripLifecycleDao.insertOrUpdate(
                        TripLifecycleEntity(
                            tripId = currentTripId,
                            state = TripLifecycleState.TRACKING.value
                        )
                    )
                } catch (_: Exception) {}

                // Drain any pending GPS inbox records before accepting new fixes
                try {
                    drainPendingGpsFixesUseCase.drain(
                        tripId = currentTripId,
                        batchLimit = 100,
                        onFixProcessed = { handleFixProcessed(it) }
                    )
                } catch (e: Exception) {
                    Log.w("LocationTrackingService", "Error draining inbox fixes during recovery: ${e.message}")
                }

                if (isPaused) {
                    _serviceState.value = TrackingServiceState(
                        isTracking = true,
                        isPaused = true,
                        tripId = currentTripId,
                        distanceMeters = totalDistanceMeters,
                        distanceMiles = totalDistanceMeters * 0.000621371,
                        durationMillis = maxOf(0L, System.currentTimeMillis() - tripStartTime),
                        currentSpeedMph = 0.0,
                        lastWaypoint = lastAcceptedWaypoint,
                        waypointCount = waypointCount
                    )
                    return@launch
                }

                // Register location updates with FusedLocationProviderClient on dedicated HandlerThread
                val updatesRegistered = startLocationUpdates()
                if (isStopping.get()) {
                    return@launch
                }

                if (!updatesRegistered) {
                    Log.e("LocationTrackingService", "Location updates could not be started during recovery; preserving active trip state.")
                    try {
                        tripLifecycleDao.updateState(currentTripId, TripLifecycleState.RECOVERY_REQUIRED.value)
                    } catch (_: Exception) {}
                    _serviceState.value = TrackingServiceState(
                        isTracking = false,
                        isRecoveryRequired = true,
                        tripId = currentTripId,
                        distanceMeters = totalDistanceMeters,
                        distanceMiles = totalDistanceMeters * 0.000621371,
                        durationMillis = maxOf(0L, System.currentTimeMillis() - tripStartTime),
                        statusMessage = "Drive Interrupted: Registration Failed"
                    )
                    cleanupFailedStartup()
                    return@launch
                }

                startDurationTicker()
                updateServiceState()
            } catch (e: Exception) {
                Log.e("LocationTrackingService", "Failed during process recreation recovery", e)
                try {
                    if (currentTripId.isNotEmpty()) {
                        tripLifecycleDao.updateState(currentTripId, TripLifecycleState.RECOVERY_REQUIRED.value)
                    }
                } catch (_: Exception) {}
                cleanupFailedStartup()
            }
        }
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
            stationaryAutoStopMonitor.reset()
            stationaryAutoStopRequested.set(false)
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

            // Initialize durable Room-backed GPS location queue
            gpsLocationQueue?.stopConsumer()
            gpsLocationQueue = GpsLocationQueue(
                scope = serviceScope,
                dispatcher = ioDispatcher,
                gpsInboxDao = gpsInboxDao,
                tripIdProvider = { currentTripId },
                batchFetchLimit = 100,
                drainPendingGpsFixesUseCase = drainPendingGpsFixesUseCase,
                tripSequenceDao = tripSequenceDao,
                database = database,
                bootCountProvider = safeBootCountProvider,
                onFixProcessed = { result ->
                    handleFixProcessed(result)
                }
            )

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

                    // Crash-safe sequence reconciliation across trip_sequence, gps_inbox, and active_waypoints
                    val initialSeq = tripSequenceDao.getReconciledMaxSequence(currentTripId)
                    gpsLocationQueue?.admissionBarrier?.initializeSequence(initialSeq)

                    // Track authoritative lifecycle in Room
                    try {
                        tripLifecycleDao.insertOrUpdate(
                            com.businesstracker.mileage.data.local.entity.TripLifecycleEntity(
                                tripId = currentTripId,
                                state = com.businesstracker.mileage.data.local.entity.TripLifecycleState.TRACKING.value
                            )
                        )
                    } catch (_: Exception) {}

                    val recoveredWaypoints = activeTripRepository.getWaypoints(currentTripId)
                    val isRecoveredSession = activeTrip.totalDistanceMeters > 0.0 || activeTrip.id != tripId || recoveredWaypoints.isNotEmpty()

                    if (isRecoveredSession) {
                        // Resume in-progress trip after process termination or system recovery
                        tripStartTime = activeTrip.startTime
                        totalDistanceMeters = activeTrip.totalDistanceMeters
                        waypointCount = recoveredWaypoints.size
                        lastAcceptedWaypoint = recoveredWaypoints.lastOrNull()
                        anchorWaypoint = recoveredWaypoints.firstOrNull() ?: if (activeTrip.startLatitude != 0.0 || activeTrip.startLongitude != 0.0) {
                            Waypoint(
                                latitude = activeTrip.startLatitude,
                                longitude = activeTrip.startLongitude,
                                timestamp = activeTrip.startTime
                            )
                        } else null
                        if (totalDistanceMeters >= 200.0) {
                            isDisplacementConfirmed = true
                        } else if (anchorWaypoint != null && lastAcceptedWaypoint != null) {
                            val distanceResults = FloatArray(1)
                            Location.distanceBetween(
                                anchorWaypoint!!.latitude,
                                anchorWaypoint!!.longitude,
                                lastAcceptedWaypoint!!.latitude,
                                lastAcceptedWaypoint!!.longitude,
                                distanceResults
                            )
                            if (distanceResults[0] >= 200.0f) {
                                isDisplacementConfirmed = true
                            }
                        }
                    } else {
                        // New tracking session
                        tripStartTime = activeTrip.startTime
                        totalDistanceMeters = 0.0
                        waypointCount = 0
                        lastAcceptedWaypoint = null
                        anchorWaypoint = null
                        isDisplacementConfirmed = false
                    }
                    currentSpeedMph = 0.0

                    if (isRecoveredSession) restoreStationaryEvidence()
                    else preferencesManager.saveStationaryEvidence(null)

                    // Register location updates with FusedLocationProviderClient asynchronously
                    val updatesRegistered = startLocationUpdates()
                    if (isStopping.get()) {
                        // Avoid racing startup cleanup against an already-started stop request
                        return@launch
                    }

                    if (!updatesRegistered) {
                        Log.e("LocationTrackingService", "Location updates could not be started; halting startup without deleting recovered data")
                        // Never clear active trip or preferences on transient registration failure!
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
        gpsLocationQueue?.stopConsumer() // Never wipe or cancelEmergency persisted data!
        tickerJob?.cancel()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
        stopGpsHandlerThread()
        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (_: Exception) {}
        }
        currentTripId = ""
        totalDistanceMeters = 0.0
        waypointCount = 0
        lastAcceptedWaypoint = null
        anchorWaypoint = null
        isDisplacementConfirmed = false
        currentSpeedMph = 0.0
        if (!_serviceState.value.isRecoveryRequired) {
            _serviceState.value = TrackingServiceState(isTracking = false)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopGpsHandlerThread() {
        gpsHandlerThread?.let { thread ->
            try {
                thread.quitSafely()
            } catch (_: Exception) {
                try { thread.quit() } catch (_: Exception) {}
            }
        }
        gpsHandlerThread = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun startLocationUpdates(): Boolean {
        locationUpdatesRegistrar?.let { return it.invoke() }
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MILLIS
        ).apply {
            setMinUpdateIntervalMillis(MIN_LOCATION_INTERVAL_MILLIS)
            setWaitForAccurateLocation(false)
        }.build()

        return try {
            stopGpsHandlerThread()
            val thread = HandlerThread("GpsLocationHandlerThread").apply { start() }
            gpsHandlerThread = thread

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                thread.looper
            ).await()
            true
        } catch (e: SecurityException) {
            stopGpsHandlerThread()
            Log.e("LocationTrackingService", "Location permission missing when requesting updates", e)
            false
        } catch (e: Exception) {
            stopGpsHandlerThread()
            Log.e("LocationTrackingService", "Failed to register location updates: ${e.message}", e)
            false
        }
    }

    @VisibleForTesting
    internal fun handleFixProcessed(result: GpsInboxProcessor.ProcessResult) {
        when (result) {
            is GpsInboxProcessor.ProcessResult.Accepted -> {
                if (result.tripId != currentTripId) return
                totalDistanceMeters = result.totalDistanceMeters
                waypointCount++

                val wp = result.waypoint ?: Waypoint(
                    latitude = result.lastLatitude,
                    longitude = result.lastLongitude,
                    speed = (result.speedMph / 2.23694).toFloat()
                )
                lastAcceptedWaypoint = wp
                if (anchorWaypoint == null) {
                    anchorWaypoint = wp
                }

                if (!isDisplacementConfirmed) {
                    if (result.isDisplacementConfirmed || totalDistanceMeters >= 200.0) {
                        isDisplacementConfirmed = true
                    } else if (anchorWaypoint != null) {
                        val distanceResults = FloatArray(1)
                        Location.distanceBetween(
                            anchorWaypoint!!.latitude,
                            anchorWaypoint!!.longitude,
                            wp.latitude,
                            wp.longitude,
                            distanceResults
                        )
                        if (distanceResults[0] >= 200.0f) {
                            isDisplacementConfirmed = true
                        }
                    }
                }

                currentSpeedMph = result.speedMph
                updateNotificationAndState(currentSpeedMph)
            }
            is GpsInboxProcessor.ProcessResult.AlreadyConsumed -> {
                if (result.tripId != currentTripId) return
                // Synchronize from authoritative persisted values without adding mileage twice
                totalDistanceMeters = result.totalDistanceMeters
                if (result.isDisplacementConfirmed || totalDistanceMeters >= 200.0) {
                    isDisplacementConfirmed = true
                }
                result.waypoint?.let { wp ->
                    if (lastAcceptedWaypoint == null) {
                        lastAcceptedWaypoint = wp
                    }
                    if (anchorWaypoint == null) {
                        anchorWaypoint = wp
                    }
                }
                updateNotificationAndState(currentSpeedMph)
            }
            is GpsInboxProcessor.ProcessResult.Rejected -> {
                // Rejected: do not increase distance or waypoint count
            }
            is GpsInboxProcessor.ProcessResult.StaleOrphanAcknowledged -> {
                // Stale orphan: do not update active-trip tracking state
            }
            is GpsInboxProcessor.ProcessResult.UnresolvedOrphan,
            is GpsInboxProcessor.ProcessResult.TripNotFound -> {
                // Unresolved orphan or processing failure: preserve current recovery-required behavior
            }
        }
    }

    private fun pauseTracking() {
        isPaused = true
        currentSpeedMph = 0.0
        evidenceRevision.incrementAndGet()
        stationaryAutoStopMonitor.reset()
        stationaryAutoStopRequested.set(false)
        serviceScope.launch(ioDispatcher) {
            preferencesManager.saveStationaryEvidence(null)
            activeTripRepository.setPaused(currentTripId, true)
            updateNotificationAndState(0.0)
        }
    }

    private fun resumeTracking() {
        isPaused = false
        currentSpeedMph = 0.0
        stationaryAutoStopMonitor.reset()
        evidenceRevision.incrementAndGet()
        stationaryAutoStopRequested.set(false)
        serviceScope.launch(ioDispatcher) {
            preferencesManager.saveStationaryEvidence(null)
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
                tripLifecycleDao.updateState(
                    currentTripId,
                    com.businesstracker.mileage.data.local.entity.TripLifecycleState.DRAINING.value
                )
            } catch (_: Exception) {}

            try {
                // 1. Stop receiving new location callbacks
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (e: Exception) {
                Log.w("LocationTrackingService", "Error removing location updates: ${e.message}")
            }
            stopGpsHandlerThread()

            // 2. Stop ticker
            tickerJob?.cancel()

            // 3. Drain all queued GPS locations before finalizing
            try {
                gpsLocationQueue?.drainAndClose()
            } catch (e: Exception) {
                Log.e("LocationTrackingService", "Waypoint persistence failed during tracking; halting finalization: ${e.message}", e)
                try {
                    tripLifecycleDao.updateState(
                        currentTripId,
                        com.businesstracker.mileage.data.local.entity.TripLifecycleState.RECOVERY_REQUIRED.value
                    )
                } catch (_: Exception) {}
                isDrainingGps.set(false)
                _serviceState.value = TrackingServiceState(isTracking = false)
                isStartingOrRunning.set(false)
                isStopping.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            isDrainingGps.set(false)

            try {
                tripLifecycleDao.updateState(
                    currentTripId,
                    com.businesstracker.mileage.data.local.entity.TripLifecycleState.READY_TO_FINALIZE.value
                )
            } catch (_: Exception) {}

            // 4. False trip prevention: if movement is under 200m and not confirmed, discard
            // Persisted active trip in Room is authoritative
            val persistedActiveTrip = activeTripRepository.getActiveTrip()
            val totalMeters = persistedActiveTrip?.totalDistanceMeters ?: totalDistanceMeters
            if (!isDisplacementConfirmed && totalMeters < 200.0) {
                if (currentTripId.isNotEmpty()) {
                    tripFinalizationCoordinator.discardFalseTrip(currentTripId)
                    preferencesManager.clearActiveTripId(currentTripId)
                }
                _serviceState.value = TrackingServiceState(isTracking = false)
                currentTripId = ""
                totalDistanceMeters = 0.0
                waypointCount = 0
                lastAcceptedWaypoint = null
                anchorWaypoint = null
                isDisplacementConfirmed = false
                currentSpeedMph = 0.0
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
            evidenceRevision.incrementAndGet()
            preferencesManager.saveStationaryEvidence(null)
            currentTripId = ""
            totalDistanceMeters = 0.0
            waypointCount = 0
            lastAcceptedWaypoint = null
            anchorWaypoint = null
            isDisplacementConfirmed = false
            currentSpeedMph = 0.0
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
                    updateNotificationAndState(currentSpeedMph)
                    val now = android.os.SystemClock.elapsedRealtime()
                    val evidence = stationaryAutoStopMonitor.snapshot()
                    if (StationaryWatchdogPolicy.shouldRecheck(
                        evidence, now, lastStationaryRecheckElapsed,
                        trackingMethod == TrackingMethod.AUTO_DETECTED && isDisplacementConfirmed,
                        isPaused || isStopping.get() || stationaryAutoStopRequested.get()
                    )) {
                        lastStationaryRecheckElapsed = now
                        serviceScope.launch(ioDispatcher) { recheckStationaryLocation() }
                    }
                }
            }
        }
    }

    private suspend fun restoreStationaryEvidence() {
        if (trackingMethod != TrackingMethod.AUTO_DETECTED || !isDisplacementConfirmed || isPaused) return
        val evidence = preferencesManager.getStationaryEvidence() ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!StationaryWatchdogPolicy.canRestore(evidence.tripId, currentTripId,
                evidence.bootCount, safeBootCountProvider.getBootCount(),
                evidence.elapsedMillis, now)) return
        stationaryAutoStopMonitor.restore(
            StationaryAutoStopMonitor.Snapshot(evidence.latitude, evidence.longitude, evidence.elapsedMillis),
            now
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun recheckStationaryLocation() {
        if (isStopping.get() || isPaused || currentTripId.isBlank()) return
        val cancellation = CancellationTokenSource()
        val location = try {
            withTimeoutOrNull(15_000L) {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token).await()
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            cancellation.cancel()
        } ?: return
        val evidence = stationaryAutoStopMonitor.snapshot() ?: return
        if (!StationaryWatchdogPolicy.isFreshDeadlineFix(evidence,
                location.elapsedRealtimeNanos / 1_000_000L,
                android.os.SystemClock.elapsedRealtime())) return
        if (!isStopping.get() && !isPaused) {
            gpsLocationQueue?.enqueueBatch(listOf(location))
            evaluateStationaryAutoStop(listOf(location))
        }
    }

    private fun updateNotificationAndState(speedMph: Double) {
        currentSpeedMph = speedMph
        val miles = totalDistanceMeters / METERS_PER_MILE
        val duration = if (tripStartTime > 0) System.currentTimeMillis() - tripStartTime else 0L

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
        updateNotificationAndState(currentSpeedMph)
    }

    @VisibleForTesting
    internal fun setTestDependencies(
        activeTripRepo: ActiveTripRepository,
        helper: NotificationHelper,
        tripId: String = "test-trip",
        startTime: Long = System.currentTimeMillis(),
        lifecycleDao: TripLifecycleDao? = null,
        inboxDao: GpsInboxDao? = null,
        drainUseCase: DrainPendingGpsFixesUseCase? = null,
        seqDao: TripSequenceDao? = null,
        db: ExpenseTrackerDatabase? = null,
        prefs: TrackingPreferencesManager? = null,
        bootProvider: BootCountProvider? = null,
        fusedClient: FusedLocationProviderClient? = null,
        dispatcher: CoroutineDispatcher? = null,
        scope: CoroutineScope? = null
    ) {
        this.activeTripRepository = activeTripRepo
        this.notificationHelper = helper
        this.currentTripId = tripId
        this.tripStartTime = startTime
        lifecycleDao?.let { this.tripLifecycleDao = it }
        inboxDao?.let { this.gpsInboxDao = it }
        drainUseCase?.let { this.drainPendingGpsFixesUseCase = it }
        seqDao?.let { this.tripSequenceDao = it }
        db?.let { this.database = it }
        prefs?.let { this.preferencesManager = it }
        this.bootCountProvider = bootProvider ?: safeBootCountProvider
        fusedClient?.let { this.fusedLocationClient = it }
        dispatcher?.let { this.ioDispatcher = it }
        scope?.let { this.serviceScope = it }
    }

    @VisibleForTesting
    internal fun setTrackingStateForTesting(
        tripId: String,
        distanceMeters: Double = 0.0,
        waypoints: Int = 0,
        lastWaypoint: Waypoint? = null,
        anchor: Waypoint? = null,
        displacementConfirmed: Boolean = false,
        startTime: Long = System.currentTimeMillis()
    ) {
        this.currentTripId = tripId
        this.totalDistanceMeters = distanceMeters
        this.waypointCount = waypoints
        this.lastAcceptedWaypoint = lastWaypoint
        this.anchorWaypoint = anchor
        this.isDisplacementConfirmed = displacementConfirmed
        this.tripStartTime = startTime
        updateServiceState()
    }

    @VisibleForTesting
    internal fun recoverActiveTripSessionForTesting() {
        recoverActiveTripSession()
    }

    @VisibleForTesting
    internal fun getInternalDistanceMeters(): Double = totalDistanceMeters

    @VisibleForTesting
    internal fun getInternalWaypointCount(): Int = waypointCount

    @VisibleForTesting
    internal fun getInternalDisplacementConfirmed(): Boolean = isDisplacementConfirmed

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
        stationaryAutoStopMonitor.reset()
        stationaryAutoStopRequested.set(false)
        gpsLocationQueue?.stopConsumer()
        tickerJob?.cancel()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
        stopGpsHandlerThread()
        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (_: Exception) {}
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
