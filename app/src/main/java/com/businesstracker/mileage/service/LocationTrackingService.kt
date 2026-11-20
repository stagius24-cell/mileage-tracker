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
import com.businesstracker.mileage.data.device.VehicleBluetoothMatcher
import android.os.SystemClock
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
import com.businesstracker.mileage.domain.repository.VehicleRepository
import com.businesstracker.mileage.domain.service.BootCountProvider
import com.businesstracker.mileage.domain.tracking.MotionResumptionValidator
import com.businesstracker.mileage.domain.tracking.MotionValidationResult
import com.businesstracker.mileage.domain.tracking.StationaryAutoStopMonitor
import com.businesstracker.mileage.domain.tracking.StationaryWatchdogPolicy
import com.businesstracker.mileage.domain.tracking.TrackingDiagnostics
import com.businesstracker.mileage.domain.tracking.VehicleResolutionResult
import com.businesstracker.mileage.receiver.BluetoothArmedDriveMonitor
import com.businesstracker.mileage.domain.usecase.DrainPendingGpsFixesUseCase
import com.businesstracker.mileage.domain.usecase.FilterGpsPointUseCase
import com.businesstracker.mileage.domain.usecase.FinalizeTripUseCase
import com.businesstracker.mileage.domain.usecase.GpsInboxProcessor
import com.businesstracker.mileage.worker.GoogleSheetsSyncWorker
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
        const val ACTION_CHECK_STATIONARY = "com.businesstracker.mileage.ACTION_CHECK_STATIONARY"
        const val ACTION_VALIDATE_MOTION = "com.businesstracker.mileage.ACTION_VALIDATE_MOTION"

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

        @Volatile
        private var activeInstance: LocationTrackingService? = null

        // Atomic guard ensuring single-session initialization and preventing race conditions
        private val isStartingOrRunning = AtomicBoolean(false)
        private val isDrainingGps = AtomicBoolean(false)
        private val isFallbackTrackingActive = AtomicBoolean(false)

        fun isDraining(): Boolean = isDrainingGps.get()

        fun isServiceInstanceRunning(): Boolean = isStartingOrRunning.get()

        fun isFallbackActive(): Boolean = isFallbackTrackingActive.get()

        fun isTrackingRunning(): Boolean = isStartingOrRunning.get() || _serviceState.value.isTracking || isDrainingGps.get()

        fun setDrainingForTesting(draining: Boolean) {
            isDrainingGps.set(draining)
        }

        fun resetGuardForTesting() {
            val instance = activeInstance
            activeInstance = null
            instance?.vehicleIdentificationJob?.cancel()
            instance?.tickerJob?.cancel()
            instance?.gpsLocationQueue?.stopConsumer()
            isStartingOrRunning.set(false)
            isDrainingGps.set(false)
            isFallbackTrackingActive.set(false)
            _serviceState.value = TrackingServiceState()
        }

        fun activateFallbackCandidateState(
            tripId: String,
            startTime: Long,
            isCandidate: Boolean = true,
            distanceMeters: Double = 0.0,
            speedMph: Double = 0.0,
            waypointCount: Int = 0,
            lastWaypoint: Waypoint? = null,
            vehicleId: String? = null,
            vehicleName: String? = null,
            vehicleBluetoothConnected: Boolean = false,
            ambiguousVehicleCount: Int = 0
        ) {
            isFallbackTrackingActive.set(true)
            val existing = _serviceState.value.takeIf { it.tripId == tripId }
            val resolvedVehicleId = vehicleId ?: existing?.vehicleId
            val resolvedVehicleName = if (resolvedVehicleId != null && resolvedVehicleId == vehicleId && !vehicleName.isNullOrBlank()) {
                vehicleName
            } else {
                existing?.vehicleName ?: vehicleName
            }
            _serviceState.value = TrackingServiceState(
                isTracking = true,
                isPaused = false,
                isCandidate = isCandidate,
                tripId = tripId,
                distanceMeters = distanceMeters,
                distanceMiles = distanceMeters / METERS_PER_MILE,
                durationMillis = maxOf(0L, System.currentTimeMillis() - startTime),
                currentSpeedMph = speedMph,
                lastWaypoint = lastWaypoint,
                waypointCount = waypointCount,
                vehicleId = resolvedVehicleId,
                vehicleName = resolvedVehicleName,
                vehicleBluetoothConnected = vehicleBluetoothConnected,
                ambiguousVehicleCount = if (resolvedVehicleId != null) 0 else ambiguousVehicleCount
            )
        }

        fun clearFallbackCandidateState(tripId: String? = null) {
            isFallbackTrackingActive.set(false)
            if (!isStartingOrRunning.get() && (tripId == null || _serviceState.value.tripId == tripId)) {
                _serviceState.value = TrackingServiceState(isTracking = false)
            }
        }

        fun updateVehicleDisplay(
            tripId: String,
            vehicleId: String?,
            name: String?,
            connected: Boolean,
            ambiguousCount: Int = 0
        ) {
            val instance = activeInstance?.takeIf { it.currentTripId == tripId }
            if (instance != null) {
                if (vehicleId != null && instance.currentVehicleId == null) {
                    instance.currentVehicleId = vehicleId
                    instance.currentVehicleName = name
                } else if (vehicleId != null && instance.currentVehicleId == vehicleId && !name.isNullOrBlank()) {
                    instance.currentVehicleName = name
                }
                instance.currentVehicleBluetoothConnected = connected
                instance.currentAmbiguousVehicleCount = if ((vehicleId ?: instance.currentVehicleId) != null) 0 else ambiguousCount
            }
            val state = _serviceState.value
            if (state.tripId != tripId) return
            val resolvedId = state.vehicleId ?: vehicleId
            val resolvedName = if (resolvedId != null && resolvedId == vehicleId && !name.isNullOrBlank()) {
                name
            } else {
                state.vehicleName ?: name
            }
            _serviceState.value = state.copy(
                vehicleId = resolvedId,
                vehicleName = resolvedName,
                vehicleBluetoothConnected = connected,
                ambiguousVehicleCount = if (resolvedId != null) 0 else ambiguousCount
            )
        }

        fun updateExplicitVehicleDisplay(
            tripId: String,
            vehicleId: String?,
            name: String?,
            connected: Boolean = false
        ) {
            val instance = activeInstance?.takeIf { it.currentTripId == tripId }
            if (instance != null) {
                instance.currentVehicleId = vehicleId
                instance.currentVehicleName = name
                instance.currentVehicleBluetoothConnected = connected
                instance.currentAmbiguousVehicleCount = 0
            }
            val state = _serviceState.value
            if (state.tripId != tripId) return
            _serviceState.value = state.copy(
                vehicleId = vehicleId,
                vehicleName = name,
                vehicleBluetoothConnected = connected,
                ambiguousVehicleCount = 0
            )
        }
    }

    @Inject lateinit var fusedLocationClient: FusedLocationProviderClient
    @Inject lateinit var filterGpsPointUseCase: FilterGpsPointUseCase
    @Inject lateinit var activeTripRepository: ActiveTripRepository
    @Inject lateinit var vehicleRepository: VehicleRepository
    @Inject lateinit var vehicleBluetoothMatcher: VehicleBluetoothMatcher
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
    @Inject lateinit var bluetoothArmedDriveMonitor: BluetoothArmedDriveMonitor

    private val safeBootCountProvider: BootCountProvider
        get() = if (::bootCountProvider.isInitialized) bootCountProvider else object : BootCountProvider {
            override fun getBootCount(): Int = 1
        }

    private val safeIoDispatcher: CoroutineDispatcher
        get() = if (::ioDispatcher.isInitialized) ioDispatcher else Dispatchers.IO

    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var tickerJob: Job? = null
    @VisibleForTesting
    internal var vehicleIdentificationJob: Job? = null
    private var gpsLocationQueue: GpsLocationQueue? = null
    private var gpsHandlerThread: HandlerThread? = null

    @VisibleForTesting
    internal var lastRecoveryJob: Job? = null
    @VisibleForTesting
    internal var lastStartupJob: Job? = null

    private val isStopping = AtomicBoolean(false)
    private var currentTripId: String = ""
    private var trackingMethod: TrackingMethod = TrackingMethod.AUTO_DETECTED
    private var tripStartTime: Long = 0L
    private var totalDistanceMeters: Double = 0.0
    private var currentSpeedMph: Double = 0.0
    private var lastAcceptedWaypoint: Waypoint? = null
    private var anchorWaypoint: Waypoint? = null
    private var isDisplacementConfirmed: Boolean = false
    private var isCandidate: Boolean = false
    private var candidateDeadlineElapsed: Long = 0L
    private var candidateDeadlineBoot: Int = 0
    private val motionResumptionValidator = MotionResumptionValidator()
    private var lastLocationReceivedElapsed: Long = 0L
    private val decisionMutex = Mutex()
    private var isPaused: Boolean = false
    @Volatile private var currentVehicleId: String? = null
    @Volatile private var currentVehicleName: String? = null
    @Volatile private var currentVehicleBluetoothConnected: Boolean = false
    @Volatile private var currentAmbiguousVehicleCount: Int = 0
    private var waypointCount: Int = 0
    private val stationaryAutoStopMonitor = StationaryAutoStopMonitor()
    private val stationaryAutoStopRequested = AtomicBoolean(false)
    private var lastStationaryRecheckElapsed = 0L
    private val evidenceWriteMutex = Mutex()
    private val evidenceRevision = AtomicLong(0L)
    @Volatile private var lastPersistedStationaryEvidence: StationaryEvidence? = null

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
                if (revision == evidenceRevision.get() && currentTripId == tripAtCall &&
                    evidence != lastPersistedStationaryEvidence) {
                    preferencesManager.saveStationaryEvidence(evidence)
                    lastPersistedStationaryEvidence = evidence
                }
            }
        }

        if (shouldStop && stationaryAutoStopRequested.compareAndSet(false, true)) {
            serviceScope.launch(ioDispatcher) {
                val snapshot = stationaryAutoStopMonitor.snapshot()
                if (snapshot != null && hasLaterVehicleEnter(snapshot)) {
                    evidenceRevision.incrementAndGet()
                    stationaryAutoStopMonitor.reset()
                    preferencesManager.saveStationaryEvidence(null)
                    lastPersistedStationaryEvidence = null
                    stationaryAutoStopRequested.set(false)
                    return@launch
                }
                preferencesManager.setInVehicleState(false)
                preferencesManager.setBluetoothDisconnectPending(false)
                TripDebounceWorker.cancelDebounce(applicationContext)
                stopTracking()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
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
            ACTION_CHECK_STATIONARY -> {
                if (isStartingOrRunning.get() && !isStopping.get() && !isPaused) {
                    serviceScope.launch(ioDispatcher) { recheckStationaryLocation() }
                }
            }
            ACTION_VALIDATE_MOTION -> {
                if (isStartingOrRunning.get() && !isStopping.get() && !isPaused) {
                    serviceScope.launch(ioDispatcher) { recheckStationaryLocation() }
                }
            }
            ACTION_STOP -> {
                val expectedTripId = intent.getStringExtra(EXTRA_TRIP_ID)
                val trigger = intent.getStringExtra(EXTRA_DEBOUNCE_TRIGGER)
                val matchesFallback = currentTripId.isEmpty() && isFallbackTrackingActive.get() &&
                    (expectedTripId == null || expectedTripId == _serviceState.value.tripId)
                if (trigger == null) {
                    if (expectedTripId == null || expectedTripId == currentTripId || matchesFallback) stopTracking()
                } else if (expectedTripId != null) {
                    serviceScope.launch(ioDispatcher) {
                        val persisted = if (currentTripId.isEmpty() && isFallbackTrackingActive.get()) {
                            activeTripRepository.getActiveTrip()
                        } else null
                        val matchesTrip = currentTripId == expectedTripId || persisted?.id == expectedTripId
                        val method = persisted?.trackingMethod?.let(TrackingMethod::fromString) ?: trackingMethod
                        if (matchesTrip && method == TrackingMethod.AUTO_DETECTED &&
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
        val targetContext = try { applicationContext ?: this } catch (_: Throwable) { this }
        val hasFine = ContextCompat.checkSelfPermission(
            targetContext,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            targetContext,
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

                val lifecycleState = try { tripLifecycleDao.getState(activeTrip.id) } catch (_: Exception) { null }
                if (lifecycleState != null && (lifecycleState == TripLifecycleState.FINALIZED.value || lifecycleState == TripLifecycleState.DISCARDED.value)) {
                    TrackingDiagnostics.logLifecycleState(activeTrip.id, lifecycleState, "Recover found terminal state in DB; clearing active trip")
                    preferencesManager.clearActiveTripId(activeTrip.id)
                    isStartingOrRunning.set(false)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                isStopping.set(false)
                isFallbackTrackingActive.set(false)
                runCatching {
                    val ctx = applicationContext
                    if (ctx != null) com.businesstracker.mileage.receiver.AutoDriveReceiver.cancelBackgroundGpsFallback(ctx)
                }
                activeInstance = this@LocationTrackingService
                stationaryAutoStopMonitor.reset()
                stationaryAutoStopRequested.set(false)
                currentTripId = activeTrip.id
                TrackingDiagnostics.logCandidateTransition(currentTripId, "SERVICE_STARTUP_ACKNOWLEDGED", "mode=RECOVERY method=${activeTrip.trackingMethod}")
                currentVehicleId = activeTrip.vehicleId
                currentVehicleName = null
                currentVehicleBluetoothConnected = false
                currentAmbiguousVehicleCount = 0
                launchVehicleIdentification(activeTrip.id)
                trackingMethod = TrackingMethod.fromString(activeTrip.trackingMethod)
                tripStartTime = activeTrip.startTime
                totalDistanceMeters = activeTrip.totalDistanceMeters
                isPaused = activeTrip.isPaused
                currentSpeedMph = 0.0

                preferencesManager.setActiveTripId(currentTripId)

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

                if (trackingMethod == TrackingMethod.AUTO_DETECTED && !isDisplacementConfirmed) {
                    val candidateDeadline = preferencesManager.getCandidateDeadline()
                    val currentBoot = safeBootCountProvider.getBootCount()
                    val nowElapsed = SystemClock.elapsedRealtime()
                    if (candidateDeadline != null && candidateDeadline.tripId == currentTripId && candidateDeadline.bootCount == currentBoot && candidateDeadline.deadlineElapsedMillis > nowElapsed) {
                        isCandidate = true
                        candidateDeadlineElapsed = candidateDeadline.deadlineElapsedMillis
                        candidateDeadlineBoot = candidateDeadline.bootCount
                        TrackingDiagnostics.logCandidateTransition(currentTripId, "RECOVERED_CANDIDATE", "Deadline in ${(candidateDeadlineElapsed - nowElapsed) / 1000}s")
                    } else {
                        // Explicit reconciliation: expired, missing, or incompatible boot must NEVER enter a 0-deadline state that bypasses expiry
                        isCandidate = true
                        candidateDeadlineElapsed = nowElapsed
                        candidateDeadlineBoot = currentBoot
                        TrackingDiagnostics.logCandidateTransition(currentTripId, "RECONCILE_RECOVERED", "Reconciling candidate session")
                        val reconciliation = expireCandidateSession()
                        if (reconciliation == CandidateReconciliation.TERMINATED) {
                            Log.d("LocationTrackingService", "Session was terminated or discarded during candidate expiry; aborting recovery initialization")
                            isStartingOrRunning.set(false)
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                            return@launch
                        }
                    }
                } else {
                    isCandidate = false
                    candidateDeadlineElapsed = 0L
                    candidateDeadlineBoot = 0
                }
                motionResumptionValidator.reset()
                lastLocationReceivedElapsed = SystemClock.elapsedRealtime()

                // Update notification with recovered metrics
                notificationHelper.updateTrackingNotification(
                    distanceMiles = totalDistanceMeters * 0.000621371,
                    durationMillis = maxOf(0L, System.currentTimeMillis() - tripStartTime),
                    isPaused = isPaused,
                    isCandidate = isCandidate
                )

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
                val stateToRecord = if (isCandidate) TripLifecycleState.CANDIDATE.value else TripLifecycleState.TRACKING.value
                try {
                    tripLifecycleDao.insertOrUpdate(
                        TripLifecycleEntity(
                            tripId = currentTripId,
                            state = stateToRecord
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
            activeInstance = this
            isStopping.set(false)
            isFallbackTrackingActive.set(false)
            runCatching {
                val ctx = try { applicationContext } catch (_: Throwable) { null }
                if (ctx != null) com.businesstracker.mileage.receiver.AutoDriveReceiver.cancelBackgroundGpsFallback(ctx)
            }
            stationaryAutoStopMonitor.reset()
            stationaryAutoStopRequested.set(false)
            currentTripId = tripId
            TrackingDiagnostics.logCandidateTransition(currentTripId, "SERVICE_STARTUP_ACKNOWLEDGED", "mode=ACTION_START method=${method.value}")
            currentVehicleId = null
            currentVehicleName = null
            currentVehicleBluetoothConnected = false
            currentAmbiguousVehicleCount = 0
            trackingMethod = method
            isPaused = false
            isDisplacementConfirmed = false
            motionResumptionValidator.reset()
            isCandidate = (method == TrackingMethod.AUTO_DETECTED)
            val nowElapsed = SystemClock.elapsedRealtime()
            val currentBoot = safeBootCountProvider.getBootCount()
            lastLocationReceivedElapsed = nowElapsed
            if (isCandidate) {
                candidateDeadlineElapsed = nowElapsed + 240_000L
                candidateDeadlineBoot = currentBoot
            } else {
                candidateDeadlineElapsed = 0L
                candidateDeadlineBoot = 0
            }

            // Start Foreground immediately with initial notification
            val initialNotification = notificationHelper.buildTrackingNotification(
                distanceMiles = 0.0,
                durationMillis = 0L,
                isPaused = false,
                isCandidate = isCandidate
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

            lastStartupJob = serviceScope.launch(ioDispatcher) {
                try {
                    if (isCandidate) {
                        val existingDeadline = preferencesManager.getCandidateDeadline()
                        if (existingDeadline != null && existingDeadline.tripId == currentTripId) {
                            // Recovering existing candidate drive via ACTION_START: preserve existing deadline
                            if (existingDeadline.bootCount == currentBoot && existingDeadline.deadlineElapsedMillis > nowElapsed) {
                                candidateDeadlineElapsed = existingDeadline.deadlineElapsedMillis
                                candidateDeadlineBoot = existingDeadline.bootCount
                                TrackingDiagnostics.logCandidateTransition(currentTripId, "PRESERVED_CANDIDATE_DEADLINE", "Deadline in ${(candidateDeadlineElapsed - nowElapsed) / 1000}s")
                            } else {
                                // Expired or incompatible boot: explicit reconciliation, never a 0-deadline
                                candidateDeadlineElapsed = nowElapsed
                                candidateDeadlineBoot = currentBoot
                                TrackingDiagnostics.logCandidateTransition(currentTripId, "RECONCILE_ACTION_START", "Reconciling candidate on ACTION_START")
                                val reconciliation = expireCandidateSession()
                                if (reconciliation == CandidateReconciliation.TERMINATED) {
                                    Log.d("LocationTrackingService", "Session was terminated or discarded during ACTION_START candidate expiry; aborting startup")
                                    cleanupFailedStartup()
                                    stopForeground(STOP_FOREGROUND_REMOVE)
                                    stopSelf()
                                    return@launch
                                }
                            }
                        } else {
                            preferencesManager.recordCandidateDeadline(
                                tripId = currentTripId,
                                deadlineElapsedMillis = candidateDeadlineElapsed,
                                bootCount = candidateDeadlineBoot
                            )
                            TrackingDiagnostics.logCandidateTransition(currentTripId, "CANDIDATE_STARTED", "4-minute window initialized")
                        }
                    }

                    // Database-backed guard ensures only one active trip exists
                    val activeTrip = activeTripRepository.createActiveTrip(
                        tripId = currentTripId,
                        latitude = 0.0,
                        longitude = 0.0,
                        trackingMethod = trackingMethod.value,
                        suggestedBusiness = suggestedBusiness
                    )
                    currentTripId = activeTrip.id
                    currentVehicleId = activeTrip.vehicleId
                    currentVehicleName = null
                    currentVehicleBluetoothConnected = false
                    currentAmbiguousVehicleCount = 0
                    launchVehicleIdentification(activeTrip.id)
                    preferencesManager.setActiveTripId(currentTripId)

                    // Crash-safe sequence reconciliation across trip_sequence, gps_inbox, and active_waypoints
                    val initialSeq = tripSequenceDao.getReconciledMaxSequence(currentTripId)
                    gpsLocationQueue?.admissionBarrier?.initializeSequence(initialSeq)

                    // Track authoritative lifecycle in Room
                    val initialLifecycle = if (isCandidate) {
                        TripLifecycleState.CANDIDATE.value
                    } else {
                        TripLifecycleState.TRACKING.value
                    }
                    try {
                        tripLifecycleDao.insertOrUpdate(
                            TripLifecycleEntity(
                                tripId = currentTripId,
                                state = initialLifecycle
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
                        if (isDisplacementConfirmed) {
                            isCandidate = false
                            candidateDeadlineElapsed = 0L
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
                    else {
                        preferencesManager.saveStationaryEvidence(null)
                        lastPersistedStationaryEvidence = null
                    }

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
        vehicleIdentificationJob?.cancel()
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
        currentVehicleId = null
        currentVehicleName = null
        currentVehicleBluetoothConnected = false
        currentAmbiguousVehicleCount = 0
        totalDistanceMeters = 0.0
        waypointCount = 0
        lastAcceptedWaypoint = null
        anchorWaypoint = null
        isDisplacementConfirmed = false
        isCandidate = false
        candidateDeadlineElapsed = 0L
        candidateDeadlineBoot = 0
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
                val freshFixElapsed = result.waypoint?.elapsedRealtimeNanos?.takeIf { it > 0L }?.div(1_000_000L)
                val observationElapsed = SystemClock.elapsedRealtime()
                if (freshFixElapsed != null && freshFixElapsed <= observationElapsed && observationElapsed - freshFixElapsed <= 30_000L) {
                    lastLocationReceivedElapsed = observationElapsed
                }
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
                    if (isDisplacementConfirmed) {
                        isCandidate = false
                        candidateDeadlineElapsed = 0L
                        TrackingDiagnostics.logCandidateTransition(currentTripId, "CONFIRMED_BY_DISPLACEMENT", "Distance: $totalDistanceMeters m")
                        val dispatcher = safeIoDispatcher
                        serviceScope.launch(dispatcher) {
                            if (::preferencesManager.isInitialized) {
                                preferencesManager.clearCandidateDeadline()
                            }
                            if (::tripLifecycleDao.isInitialized) {
                                try {
                                    tripLifecycleDao.updateState(currentTripId, TripLifecycleState.TRACKING.value)
                                } catch (_: Exception) {}
                            }
                        }
                        if (currentVehicleId == null) {
                            launchVehicleIdentification(currentTripId)
                        }
                    }
                }

                currentSpeedMph = result.speedMph
                updateNotificationAndState(currentSpeedMph)

                // Motion resumption evaluation with fresh corroboration (>= 10 mph, 2 distinct fixes within 30s)
                val nowElapsed = SystemClock.elapsedRealtime()
                val fixElapsed = if (wp.elapsedRealtimeNanos > 0L) wp.elapsedRealtimeNanos / 1_000_000L else 0L
                val motionResult = motionResumptionValidator.evaluateFix(
                    speedMph = currentSpeedMph,
                    accuracyMeters = wp.accuracy,
                    fixTimestampMillis = fixElapsed,
                    nowMillis = nowElapsed
                )
                when (motionResult) {
                    is MotionValidationResult.MotionCorroborated -> {
                        TrackingDiagnostics.logMotionResumption(currentTripId, "CORROBORATED", "First: ${motionResult.firstFixTimestamp}, Second: ${motionResult.secondFixTimestamp}")
                        onVehicleMotionResumed()
                    }
                    is MotionValidationResult.AwaitingCorroboration -> {
                        TrackingDiagnostics.logMotionResumption(currentTripId, "AWAITING_CORROBORATION", "First fix: ${motionResult.firstFixTimestamp}")
                    }
                    is MotionValidationResult.Rejected -> {
                        TrackingDiagnostics.logMotionResumption(currentTripId, "REJECTED", motionResult.reason)
                    }
                }
            }
            is GpsInboxProcessor.ProcessResult.AlreadyConsumed -> {
                if (result.tripId != currentTripId) return
                // Synchronize from authoritative persisted values without adding mileage twice
                totalDistanceMeters = result.totalDistanceMeters
                if (result.isDisplacementConfirmed || totalDistanceMeters >= 200.0) {
                    isDisplacementConfirmed = true
                    if (isCandidate) {
                        isCandidate = false
                        candidateDeadlineElapsed = 0L
                        val dispatcher = safeIoDispatcher
                        serviceScope.launch(dispatcher) {
                            if (::preferencesManager.isInitialized) {
                                preferencesManager.clearCandidateDeadline()
                            }
                            if (::tripLifecycleDao.isInitialized) {
                                try {
                                    tripLifecycleDao.updateState(currentTripId, TripLifecycleState.TRACKING.value)
                                } catch (_: Exception) {}
                            }
                        }
                        if (currentVehicleId == null) {
                            launchVehicleIdentification(currentTripId)
                        }
                    }
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

    private fun onVehicleMotionResumed() {
        if (trackingMethod == TrackingMethod.AUTO_DETECTED) {
            val tripIdAtCall = currentTripId
            if (tripIdAtCall.isEmpty() || isStopping.get()) return
            serviceScope.launch(safeIoDispatcher) {
                decisionMutex.withLock {
                    if (currentTripId != tripIdAtCall || isStopping.get()) return@withLock
                    try {
                        val ctx = try { applicationContext } catch (_: Throwable) { null }
                        if (ctx != null) {
                            TripDebounceWorker.cancelDebounce(ctx, tripIdAtCall)
                        }
                    } catch (_: Throwable) {}
                    if (::preferencesManager.isInitialized) {
                        try {
                            preferencesManager.setInVehicleState(true)
                            preferencesManager.recordVehicleEnter(
                                tripIdAtCall,
                                SystemClock.elapsedRealtime(),
                                safeBootCountProvider.getBootCount()
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun pauseTracking() {
        isPaused = true
        currentSpeedMph = 0.0
        motionResumptionValidator.reset()
        evidenceRevision.incrementAndGet()
        stationaryAutoStopMonitor.reset()
        stationaryAutoStopRequested.set(false)
        val tripIdAtCall = currentTripId
        serviceScope.launch(ioDispatcher) {
            decisionMutex.withLock {
                if (currentTripId != tripIdAtCall) return@withLock
                preferencesManager.saveStationaryEvidence(null)
                lastPersistedStationaryEvidence = null
                activeTripRepository.setPaused(tripIdAtCall, true)
                updateNotificationAndState(0.0)
            }
        }
    }

    private fun resumeTracking() {
        isPaused = false
        currentSpeedMph = 0.0
        motionResumptionValidator.reset()
        stationaryAutoStopMonitor.reset()
        evidenceRevision.incrementAndGet()
        stationaryAutoStopRequested.set(false)
        val tripIdAtCall = currentTripId
        serviceScope.launch(ioDispatcher) {
            decisionMutex.withLock {
                if (currentTripId != tripIdAtCall) return@withLock
                preferencesManager.saveStationaryEvidence(null)
                lastPersistedStationaryEvidence = null
                activeTripRepository.setPaused(tripIdAtCall, false)
                updateNotificationAndState(0.0)
            }
        }
    }

    private fun stopTracking() {
        if (!isStopping.compareAndSet(false, true)) {
            return
        }
        isFallbackTrackingActive.set(false)
        runCatching {
            val ctx = try { applicationContext } catch (_: Throwable) { null }
            if (ctx != null) com.businesstracker.mileage.receiver.AutoDriveReceiver.cancelBackgroundGpsFallback(ctx)
        }
        var tripIdAtCall = currentTripId
        isDrainingGps.set(true)
        serviceScope.launch(ioDispatcher) {
            decisionMutex.withLock {
                if (currentTripId.isEmpty() && tripIdAtCall.isEmpty() && ::activeTripRepository.isInitialized) {
                    val active = activeTripRepository.getActiveTrip()
                    if (active != null) {
                        currentTripId = active.id
                        tripIdAtCall = active.id
                        trackingMethod = TrackingMethod.fromString(active.trackingMethod)
                        totalDistanceMeters = active.totalDistanceMeters
                        isDisplacementConfirmed = active.totalDistanceMeters >= 200.0
                        isCandidate = !isDisplacementConfirmed && trackingMethod == TrackingMethod.AUTO_DETECTED
                        tripStartTime = active.startTime
                    }
                }
                if (tripIdAtCall.isNotEmpty() && currentTripId != tripIdAtCall && currentTripId.isNotEmpty()) {
                    isDrainingGps.set(false)
                    isStopping.set(false)
                    return@withLock
                }

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
                    return@withLock
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
                        if (currentTripId == tripIdAtCall) {
                            preferencesManager.clearActiveTripId(currentTripId)
                            preferencesManager.clearCandidateDeadline()
                            preferencesManager.setInVehicleState(false)
                            preferencesManager.setBluetoothDisconnectPending(false)
                        }
                    }
                    try {
                        val ctx = try { applicationContext } catch (_: Throwable) { null }
                        if (ctx != null) {
                            TripDebounceWorker.cancelDebounce(ctx, tripIdAtCall)
                        }
                    } catch (_: Throwable) {}

                    vehicleIdentificationJob?.cancel()
                    motionResumptionValidator.reset()
                    _serviceState.value = TrackingServiceState(isTracking = false)
                    currentTripId = ""
                    currentVehicleId = null
                    currentVehicleName = null
                    currentVehicleBluetoothConnected = false
                    currentAmbiguousVehicleCount = 0
                    totalDistanceMeters = 0.0
                    waypointCount = 0
                    lastAcceptedWaypoint = null
                    anchorWaypoint = null
                    isDisplacementConfirmed = false
                    isCandidate = false
                    candidateDeadlineElapsed = 0L
                    candidateDeadlineBoot = 0
                    currentSpeedMph = 0.0
                    isStartingOrRunning.set(false)
                    isStopping.set(false)
                    if (::bluetoothArmedDriveMonitor.isInitialized) {
                        runCatching {
                            bluetoothArmedDriveMonitor.resetTriggerCooldown()
                            bluetoothArmedDriveMonitor.reconcile()
                        }
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@withLock
                }

                // 5. Finalize trip using single-owner idempotent coordinator
                if (currentTripId.isNotEmpty()) {
                    val finalizedTrip = tripFinalizationCoordinator.finalizeTrip(currentTripId, trackingMethod)
                    if (!tripFinalizationCoordinator.isSmartRecognitionAttached()) {
                        finalizedTrip?.let { trip ->
                            if (trip.classification == com.businesstracker.mileage.domain.model.TripClassification.UNREVIEWED.value) {
                                notificationHelper.showTripDetectedNotification(trip.id, trip.totalDistanceMiles)
                            } else {
                                // Auto-classified trip (e.g. from Saved Location), sync immediately to Google Sheets
                                GoogleSheetsSyncWorker.enqueueOneTimeSync(applicationContext)
                            }
                        }
                    }
                    if (currentTripId == tripIdAtCall) {
                        preferencesManager.clearActiveTripId(currentTripId)
                        preferencesManager.clearCandidateDeadline()
                    }
                }

                try {
                    val ctx = try { applicationContext } catch (_: Throwable) { null }
                    if (ctx != null) {
                        TripDebounceWorker.cancelDebounce(ctx, tripIdAtCall)
                    }
                } catch (_: Throwable) {}

                vehicleIdentificationJob?.cancel()
                motionResumptionValidator.reset()
                _serviceState.value = TrackingServiceState(isTracking = false)
                evidenceRevision.incrementAndGet()
                preferencesManager.saveStationaryEvidence(null)
                lastPersistedStationaryEvidence = null
                currentTripId = ""
                currentVehicleId = null
                currentVehicleName = null
                currentVehicleBluetoothConnected = false
                currentAmbiguousVehicleCount = 0
                totalDistanceMeters = 0.0
                waypointCount = 0
                lastAcceptedWaypoint = null
                anchorWaypoint = null
                isDisplacementConfirmed = false
                isCandidate = false
                candidateDeadlineElapsed = 0L
                candidateDeadlineBoot = 0
                currentSpeedMph = 0.0
                isStartingOrRunning.set(false)
                isStopping.set(false)
                if (::bluetoothArmedDriveMonitor.isInitialized) {
                    runCatching {
                        bluetoothArmedDriveMonitor.resetTriggerCooldown()
                        bluetoothArmedDriveMonitor.reconcile()
                    }
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun startDurationTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                if (!isPaused) {
                    updateNotificationAndState(currentSpeedMph)
                    val now = SystemClock.elapsedRealtime()

                    // Candidate timeout: 4-minute maximum lifetime for unconfirmed automatic sessions
                    if (isCandidate && candidateDeadlineElapsed > 0L && now >= candidateDeadlineElapsed) {
                        lastStartupJob = serviceScope.launch(ioDispatcher) {
                            expireCandidateSession()
                        }
                    }

                    // Watchdog: check 60s GPS silence for confirmed trips (works even without an existing stationary snapshot)
                    if (trackingMethod == TrackingMethod.AUTO_DETECTED &&
                        !isCandidate &&
                        isDisplacementConfirmed &&
                        !isPaused &&
                        !isStopping.get() &&
                        !stationaryAutoStopRequested.get() &&
                        lastLocationReceivedElapsed > 0L &&
                        (now - lastLocationReceivedElapsed >= 60_000L)
                    ) {
                        if (now - lastStationaryRecheckElapsed >= 30_000L) {
                            lastStationaryRecheckElapsed = now
                            lastStartupJob = serviceScope.launch(ioDispatcher) { recheckStationaryLocation() }
                        }
                    }

                    val evidence = stationaryAutoStopMonitor.snapshot()
                    if (StationaryWatchdogPolicy.shouldRecheck(
                        evidence, now, lastStationaryRecheckElapsed,
                        trackingMethod == TrackingMethod.AUTO_DETECTED && isDisplacementConfirmed,
                        isPaused || isStopping.get() || stationaryAutoStopRequested.get()
                    )) {
                        lastStationaryRecheckElapsed = now
                        lastStartupJob = serviceScope.launch(ioDispatcher) { recheckStationaryLocation() }
                    }
                }
            }
        }
    }

    private enum class CandidateReconciliation { CONFIRMED, RETRY_REQUIRED, TERMINATED }

    private suspend fun expireCandidateSession(): CandidateReconciliation =
        decisionMutex.withLock {
            val tripIdAtCall = currentTripId
            if (isStopping.get() || tripIdAtCall.isEmpty()) return@withLock CandidateReconciliation.TERMINATED
            if (!isCandidate) return@withLock CandidateReconciliation.CONFIRMED
            TrackingDiagnostics.logCandidateTransition(tripIdAtCall, "EXPIRING_CANDIDATE", "Candidate timeout reached. Draining inbox...")

            // Coordinated drain using admission barrier and inbox drain
            isDrainingGps.set(true)
            val queue = gpsLocationQueue
            val drainSucceeded = try {
                if (queue != null) {
                    queue.drainAdmittedFixes()
                } else {
                    var drainedCount: Int
                    do {
                        drainedCount = drainPendingGpsFixesUseCase.drain(
                            tripId = tripIdAtCall,
                            batchLimit = 100,
                            onFixProcessed = { handleFixProcessed(it) }
                        )
                    } while (drainedCount > 0)
                    gpsInboxDao.getPendingCount(tripIdAtCall) == 0
                }
            } catch (e: Exception) {
                Log.e("LocationTrackingService", "Error draining inbox during candidate expiry: ${e.message}", e)
                false
            } finally {
                isDrainingGps.set(false)
            }

            // Never discard after a failed or incomplete drain; preserve candidate state for safe retry
            if (!drainSucceeded) {
                TrackingDiagnostics.logCandidateTransition(tripIdAtCall, "DRAIN_FAILED", "Preserving candidate state for safe retry")
                queue?.reopenAdmissionBarrier()
                return@withLock CandidateReconciliation.RETRY_REQUIRED
            }

            val persisted = activeTripRepository.getActiveTrip()
            val totalMeters = persisted?.totalDistanceMeters ?: totalDistanceMeters
            if (isDisplacementConfirmed || totalMeters >= 200.0) {
                isDisplacementConfirmed = true
                isCandidate = false
                candidateDeadlineElapsed = 0L
                candidateDeadlineBoot = 0
                preferencesManager.clearCandidateDeadline()
                try {
                    tripLifecycleDao.updateState(tripIdAtCall, TripLifecycleState.TRACKING.value)
                } catch (_: Exception) {}
                TrackingDiagnostics.logCandidateTransition(tripIdAtCall, "CONFIRMED_ON_DRAIN", "Meters: $totalMeters")
                queue?.reopenAdmissionBarrier()
                return@withLock CandidateReconciliation.CONFIRMED
            }

            TrackingDiagnostics.logCandidateTransition(tripIdAtCall, "DISCARDING_FALSE_CANDIDATE", "Meters: $totalMeters < 200m")
            if (isStopping.compareAndSet(false, true)) {
                queue?.stopConsumer()
                try {
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                } catch (_: Exception) {}
                stopGpsHandlerThread()
                tickerJob?.cancel()

                tripFinalizationCoordinator.discardFalseTrip(tripIdAtCall)
                if (currentTripId == tripIdAtCall) {
                    preferencesManager.clearActiveTripId(tripIdAtCall)
                    preferencesManager.clearCandidateDeadline()
                    preferencesManager.setInVehicleState(false)
                    preferencesManager.setBluetoothDisconnectPending(false)
                }
                try {
                    val ctx = try { applicationContext } catch (_: Throwable) { null }
                    if (ctx != null) {
                        TripDebounceWorker.cancelDebounce(ctx, tripIdAtCall)
                    }
                } catch (_: Throwable) {}

                vehicleIdentificationJob?.cancel()
                motionResumptionValidator.reset()
                _serviceState.value = TrackingServiceState(isTracking = false)
                currentTripId = ""
                currentVehicleId = null
                currentVehicleName = null
                currentVehicleBluetoothConnected = false
                currentAmbiguousVehicleCount = 0
                totalDistanceMeters = 0.0
                waypointCount = 0
                lastAcceptedWaypoint = null
                anchorWaypoint = null
                isDisplacementConfirmed = false
                isCandidate = false
                candidateDeadlineElapsed = 0L
                candidateDeadlineBoot = 0
                currentSpeedMph = 0.0
                isStartingOrRunning.set(false)
                isStopping.set(false)
                if (::bluetoothArmedDriveMonitor.isInitialized) {
                    runCatching {
                        bluetoothArmedDriveMonitor.resetTriggerCooldown()
                        bluetoothArmedDriveMonitor.reconcile()
                    }
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            CandidateReconciliation.TERMINATED
        }

    private suspend fun restoreStationaryEvidence() {
        if (trackingMethod != TrackingMethod.AUTO_DETECTED || !isDisplacementConfirmed || isPaused) return
        val evidence = preferencesManager.getStationaryEvidence() ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!StationaryWatchdogPolicy.canRestore(evidence.tripId, currentTripId,
                evidence.bootCount, safeBootCountProvider.getBootCount(),
                evidence.elapsedMillis, now)) return
        if (hasLaterVehicleEnter(StationaryAutoStopMonitor.Snapshot(
                evidence.latitude, evidence.longitude, evidence.elapsedMillis))) return
        if (stationaryAutoStopMonitor.restore(
            StationaryAutoStopMonitor.Snapshot(evidence.latitude, evidence.longitude, evidence.elapsedMillis),
            now
        )) lastPersistedStationaryEvidence = evidence
    }

    private suspend fun hasLaterVehicleEnter(snapshot: StationaryAutoStopMonitor.Snapshot): Boolean {
        val enter = preferencesManager.getVehicleEnterEvidence() ?: return false
        return StationaryWatchdogPolicy.hasLaterVehicleEnter(currentTripId, snapshot.elapsedMillis,
            enter.tripId, enter.elapsedMillis, enter.bootCount, safeBootCountProvider.getBootCount())
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
        } ?: return // Missing GPS does NOT finalize or stop a confirmed trip; preserves tracking!

        val evidence = stationaryAutoStopMonitor.snapshot()
        if (evidence != null) {
            if (!StationaryWatchdogPolicy.isFreshDeadlineFix(
                    evidence,
                    location.elapsedRealtimeNanos / 1_000_000L,
                    SystemClock.elapsedRealtime()
                )) return
        }
        // If evidence was null, fresh fix establishes evidence!
        if (!isStopping.get() && !isPaused) {
            if (location.hasAccuracy() && location.accuracy <= 50f) {
                gpsLocationQueue?.enqueueBatch(listOf(location))
                evaluateStationaryAutoStop(listOf(location))
            }
        }
    }

    private fun updateNotificationAndState(speedMph: Double) {
        currentSpeedMph = speedMph
        val miles = totalDistanceMeters / METERS_PER_MILE
        val duration = if (tripStartTime > 0) System.currentTimeMillis() - tripStartTime else 0L

        notificationHelper.updateTrackingNotification(
            distanceMiles = miles,
            durationMillis = duration,
            isPaused = isPaused,
            isCandidate = isCandidate
        )

        _serviceState.value = TrackingServiceState(
            isTracking = true,
            isPaused = isPaused,
            isCandidate = isCandidate,
            tripId = currentTripId,
            distanceMeters = totalDistanceMeters,
            distanceMiles = miles,
            durationMillis = duration,
            currentSpeedMph = currentSpeedMph,
            lastWaypoint = lastAcceptedWaypoint,
            waypointCount = waypointCount,
            vehicleId = currentVehicleId,
            vehicleName = currentVehicleName,
            vehicleBluetoothConnected = currentVehicleBluetoothConnected,
            ambiguousVehicleCount = currentAmbiguousVehicleCount
        )
    }

    private fun launchVehicleIdentification(tripId: String) {
        if (!::vehicleBluetoothMatcher.isInitialized || !::vehicleRepository.isInitialized) return
        vehicleIdentificationJob?.cancel()
        vehicleIdentificationJob = serviceScope.launch(safeIoDispatcher) {
            val retryDelays = if (::ioDispatcher.isInitialized && ioDispatcher !== Dispatchers.IO) {
                longArrayOf(0L)
            } else {
                longArrayOf(0L, 3_000L, 10_000L, 25_000L, 45_000L, 90_000L)
            }
            for (delayMs in retryDelays) {
                if (delayMs > 0L) delay(delayMs)
                if (currentTripId != tripId || isStopping.get()) return@launch
                try {
                    val active = activeTripRepository.getActiveTrip()?.takeIf { it.id == tripId } ?: return@launch
                    val isManual = if (::preferencesManager.isInitialized) {
                        preferencesManager.isManualVehicleAssignment(tripId)
                    } else false
                    val resolution = vehicleBluetoothMatcher.resolveConnectedVehicle()
                    if (active.vehicleId == null && !isManual && resolution is VehicleResolutionResult.Matched) {
                        activeTripRepository.assignVehicleIfAbsent(tripId, resolution.vehicle.id)
                    }
                    val persisted = activeTripRepository.getActiveTrip()?.takeIf { it.id == tripId } ?: return@launch
                    val vehicleId = persisted.vehicleId
                    val vehicle = vehicleId?.let { vehicleRepository.getVehicle(it) }
                    if (currentTripId != tripId) return@launch
                    currentVehicleId = vehicleId
                    currentVehicleName = vehicle?.name
                    currentVehicleBluetoothConnected = when {
                        vehicleId != null && resolution is VehicleResolutionResult.Matched -> resolution.vehicle.id == vehicleId
                        vehicleId != null && !vehicle?.bluetoothAddress.isNullOrBlank() ->
                            vehicleBluetoothMatcher.isDeviceConnected(vehicle!!.bluetoothAddress!!)
                        else -> false
                    }
                    currentAmbiguousVehicleCount = if (vehicleId == null && resolution is VehicleResolutionResult.Ambiguous) {
                        resolution.candidates.size
                    } else {
                        0
                    }
                    TrackingDiagnostics.logVehicleResolution(
                        tripId = tripId,
                        resultCode = when {
                            vehicleId != null -> "ASSIGNED"
                            resolution is VehicleResolutionResult.Ambiguous -> "AMBIGUOUS"
                            else -> "UNRESOLVED"
                        },
                        vehicleId = vehicleId,
                        candidateCount = when (resolution) {
                            is VehicleResolutionResult.Ambiguous -> resolution.candidates.size
                            is VehicleResolutionResult.Matched -> 1
                            VehicleResolutionResult.None -> 0
                        },
                        source = when (resolution) {
                            is VehicleResolutionResult.Matched -> resolution.source
                            is VehicleResolutionResult.Ambiguous -> resolution.source
                            VehicleResolutionResult.None -> "NONE"
                        }
                    )
                    val state = _serviceState.value
                    if (state.tripId == tripId) {
                        _serviceState.value = state.copy(
                            vehicleId = currentVehicleId,
                            vehicleName = currentVehicleName,
                            vehicleBluetoothConnected = currentVehicleBluetoothConnected,
                            ambiguousVehicleCount = currentAmbiguousVehicleCount
                        )
                    }
                    if (vehicleId != null) break
                } catch (error: Exception) {
                    Log.i("LocationTrackingService", "Vehicle identification unavailable: ${error.javaClass.simpleName}")
                }
            }
        }
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
        coordinator: TripFinalizationCoordinator? = null,
        dispatcher: CoroutineDispatcher? = null,
        scope: CoroutineScope? = null,
        context: Context? = null,
        vehicleRepo: VehicleRepository? = null,
        matcher: VehicleBluetoothMatcher? = null,
        armedMonitor: BluetoothArmedDriveMonitor? = null
    ) {
        activeInstance = this
        if (context != null && baseContext == null) {
            attachBaseContext(context)
        }
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
        coordinator?.let { this.tripFinalizationCoordinator = it }
        this.bootCountProvider = bootProvider ?: safeBootCountProvider
        fusedClient?.let { this.fusedLocationClient = it }
        dispatcher?.let { this.ioDispatcher = it }
        scope?.let { this.serviceScope = it }
        vehicleRepo?.let { this.vehicleRepository = it }
        matcher?.let { this.vehicleBluetoothMatcher = it }
        armedMonitor?.let { this.bluetoothArmedDriveMonitor = it }
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
        activeInstance = this
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

    @VisibleForTesting
    internal fun getLastLocationReceivedElapsedForTesting(): Long = lastLocationReceivedElapsed

    @VisibleForTesting
    internal fun getInternalIsCandidate(): Boolean = isCandidate

    @VisibleForTesting
    internal fun setCandidateForTesting(candidate: Boolean, deadlineElapsed: Long = 0L) {
        this.isCandidate = candidate
        this.candidateDeadlineElapsed = deadlineElapsed
    }

    @VisibleForTesting
    internal suspend fun expireCandidateSessionForTesting() {
        expireCandidateSession()
    }

    @VisibleForTesting
    internal fun setGpsLocationQueueForTesting(queue: GpsLocationQueue?) {
        this.gpsLocationQueue = queue
    }

    @VisibleForTesting
    internal fun getGpsLocationQueueForTesting(): GpsLocationQueue? = gpsLocationQueue

    @VisibleForTesting
    internal suspend fun recheckStationaryLocationForTesting() {
        recheckStationaryLocation()
    }

    @VisibleForTesting
    internal fun isStopping(): Boolean = isStopping.get()

    @VisibleForTesting
    internal fun getMotionResumptionValidator(): MotionResumptionValidator = motionResumptionValidator

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
        if (activeInstance === this) activeInstance = null
        vehicleIdentificationJob?.cancel()
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
