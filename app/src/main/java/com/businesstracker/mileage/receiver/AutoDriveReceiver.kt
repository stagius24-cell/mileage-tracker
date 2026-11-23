package com.businesstracker.mileage.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.businesstracker.mileage.data.device.VehicleBluetoothMatcher
import com.businesstracker.mileage.data.local.TripLifecycleDao
import com.businesstracker.mileage.data.local.entity.TripLifecycleEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleState
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.coordinator.TripFinalizationCoordinator
import com.businesstracker.mileage.domain.model.FilterResult
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.model.Waypoint
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.domain.service.BootCountProvider
import com.businesstracker.mileage.domain.tracking.AutoDriveAction
import com.businesstracker.mileage.domain.tracking.AutoDriveDecisionEngine
import com.businesstracker.mileage.domain.tracking.TrackingDiagnostics
import com.businesstracker.mileage.domain.tracking.VehicleResolutionResult
import com.businesstracker.mileage.domain.usecase.FilterGpsPointUseCase
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.service.NotificationHelper
import com.businesstracker.mileage.worker.TripDebounceWorker
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class AutoDriveReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "AutoDriveReceiver"
        const val ACTION_ACTIVITY_TRANSITION = "com.businesstracker.mileage.ACTION_ACTIVITY_TRANSITION"
        const val ACTION_BACKGROUND_GPS_UPDATE = "com.businesstracker.mileage.ACTION_BACKGROUND_GPS_UPDATE"
        const val TRANSITION_REQUEST_CODE = 3001
        const val BACKGROUND_GPS_REQUEST_CODE = 3003
        const val START_TRIGGER_DISPLACEMENT_METERS = 200.0f
        const val MAX_TRANSITION_AGE_NANOS = 120_000_000_000L // 2 minutes
        const val CANDIDATE_WINDOW_MILLIS = 240_000L

        @Volatile
        private var lastLoggedActiveSessionTripId: String? = null

        @VisibleForTesting
        internal fun resetDiagnosticDedupeForTesting() {
            lastLoggedActiveSessionTripId = null
        }

        fun buildBackgroundGpsPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, AutoDriveReceiver::class.java).apply {
                action = ACTION_BACKGROUND_GPS_UPDATE
            }
            return PendingIntent.getBroadcast(
                context,
                BACKGROUND_GPS_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }

        fun cancelBackgroundGpsFallback(context: Context) {
            runCatching {
                LocationServices.getFusedLocationProviderClient(context)
                    .removeLocationUpdates(buildBackgroundGpsPendingIntent(context))
            }
        }
    }

    @Inject lateinit var preferencesManager: TrackingPreferencesManager
    @Inject lateinit var activeTripRepository: ActiveTripRepository
    @Inject lateinit var tripLifecycleDao: TripLifecycleDao
    @Inject lateinit var decisionEngine: AutoDriveDecisionEngine
    @Inject lateinit var bluetoothArmedDriveMonitor: BluetoothArmedDriveMonitor
    @Inject lateinit var ioDispatcher: CoroutineDispatcher
    @Inject lateinit var bootCountProvider: BootCountProvider
    @Inject lateinit var vehicleBluetoothMatcher: VehicleBluetoothMatcher
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var filterGpsPointUseCase: FilterGpsPointUseCase
    @Inject lateinit var tripFinalizationCoordinator: TripFinalizationCoordinator

    @VisibleForTesting
    internal var serviceLauncher: ((Context, Intent) -> Unit)? = null

    @VisibleForTesting
    internal var backgroundLocationRegistrar: (suspend (Context, PendingIntent) -> Boolean)? = null

    @VisibleForTesting
    internal var elapsedClock: () -> Long = SystemClock::elapsedRealtime

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        if (intent.action == ACTION_BACKGROUND_GPS_UPDATE) {
            val locations = LocationResult.extractResult(intent)?.locations.orEmpty()
            if (locations.isNotEmpty()) {
                val pendingResult = goAsync()
                receiverScope.launch(ioDispatcher) {
                    try {
                        handleBackgroundGpsUpdate(context, locations)
                    } finally {
                        pendingResult?.finish()
                    }
                }
            }
            return
        }

        if (intent.action == BluetoothArmedDriveMonitor.ACTION_ACTIVITY_UPDATE &&
            ActivityRecognitionResult.hasResult(intent)
        ) {
            val activityResult = ActivityRecognitionResult.extractResult(intent) ?: return
            val pendingResult = goAsync()
            receiverScope.launch(ioDispatcher) {
                try {
                    handleArmedActivityUpdate(context, activityResult.probableActivities)
                } finally {
                    pendingResult?.finish()
                }
            }
            return
        }
        if (!ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val pendingResult = goAsync()

        receiverScope.launch(ioDispatcher) {
            try {
                val currentBoot = bootCountProvider.getBootCount()
                val receiptTimeNanos = SystemClock.elapsedRealtimeNanos()

                // Process transition events strictly in timestamp order
                val sortedEvents = result.transitionEvents.sortedBy { it.elapsedRealTimeNanos }
                for (event in sortedEvents) {
                    // Cross-delivery deduplication check
                    val last = preferencesManager.getLastTransitionTimestamp()
                    if (last != null && last.second == currentBoot && event.elapsedRealTimeNanos <= last.first) {
                        TrackingDiagnostics.logTransition(
                            eventTimeNanos = event.elapsedRealTimeNanos,
                            receiptTimeNanos = receiptTimeNanos,
                            activityType = event.activityType,
                            transitionType = event.transitionType,
                            bootCount = currentBoot,
                            isAccepted = false,
                            reason = "Duplicate or older event across deliveries"
                        )
                        continue
                    }
                    preferencesManager.recordLastTransitionTimestamp(event.elapsedRealTimeNanos, currentBoot)

                    // 2-minute age freshness check: treat older events as historical signals, not auto-start triggers
                    val ageNanos = receiptTimeNanos - event.elapsedRealTimeNanos
                    if (ageNanos > MAX_TRANSITION_AGE_NANOS) {
                        TrackingDiagnostics.logTransition(
                            eventTimeNanos = event.elapsedRealTimeNanos,
                            receiptTimeNanos = receiptTimeNanos,
                            activityType = event.activityType,
                            transitionType = event.transitionType,
                            bootCount = currentBoot,
                            isAccepted = false,
                            reason = "Event older than 2 minutes (${ageNanos / 1_000_000L}ms); historical signal only"
                        )
                        continue
                    }

                    TrackingDiagnostics.logTransition(
                        eventTimeNanos = event.elapsedRealTimeNanos,
                        receiptTimeNanos = receiptTimeNanos,
                        activityType = event.activityType,
                        transitionType = event.transitionType,
                        bootCount = currentBoot,
                        isAccepted = true,
                        reason = "Fresh transition accepted"
                    )

                    when (event.activityType) {
                        DetectedActivity.IN_VEHICLE -> {
                            when (event.transitionType) {
                                ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                                    android.util.Log.d(TAG, "Transition IN_VEHICLE ENTER received")
                                    handleVehicleEnter(context)
                                }
                                ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                                    android.util.Log.d(TAG, "Transition IN_VEHICLE EXIT received")
                                    handleVehicleExit(context)
                                }
                            }
                        }
                        DetectedActivity.STILL -> {
                            if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                                android.util.Log.d(TAG, "Transition STILL ENTER received")
                                handleStillEnter(context)
                            }
                        }
                    }
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    internal suspend fun handleArmedActivityUpdate(
        context: Context,
        probableActivities: List<DetectedActivity>
    ) {
        val isAutoTrackingEnabled = preferencesManager.isAutoTrackingEnabled.first()
        val isCarConnected = preferencesManager.isCarBluetoothConnected() ||
            preferencesManager.getConnectedCarAddresses().isNotEmpty()
        if (!isAutoTrackingEnabled || !isCarConnected) {
            TrackingDiagnostics.logAutoStartDecision(
                source = "BT_ARMED",
                accepted = false,
                reasonCode = if (!isAutoTrackingEnabled) "AUTO_TRACKING_DISABLED" else "NO_CONNECTED_VEHICLE",
                detail = "autoTracking=$isAutoTrackingEnabled btConnected=$isCarConnected"
            )
            return
        }

        // Candidate and active sessions already have bounded location verification. Do not
        // request additional location samples or create another trip while one exists.
        val serviceTracking = LocationTrackingService.serviceState.value.isTracking
        val serviceTripId = LocationTrackingService.serviceState.value.tripId?.takeIf { it.isNotBlank() }
        val activeTrip = if (serviceTracking) null else activeTripRepository.getActiveTrip()
        if (serviceTracking || activeTrip != null) {
            val activeSessionId = serviceTripId ?: activeTrip?.id
            val dedupeKey = activeSessionId ?: "active"
            if (lastLoggedActiveSessionTripId != dedupeKey) {
                lastLoggedActiveSessionTripId = dedupeKey
                TrackingDiagnostics.logAutoStartDecision(
                    source = "BT_ARMED",
                    accepted = false,
                    reasonCode = "SESSION_ALREADY_ACTIVE",
                    detail = "serviceTracking=$serviceTracking trip=${activeSessionId ?: "unknown"}"
                )
            }
            return
        }
        lastLoggedActiveSessionTripId = null

        if (bluetoothArmedDriveMonitor.observeActivityUpdate(probableActivities)) {
            TrackingDiagnostics.logArmedMonitor("MOVEMENT_CONFIRMED", "starting existing automatic candidate flow")
            handleVehicleEnter(context, source = "BT_ARMED")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleVehicleEnter(context: Context, source: String = "ACTIVITY_TRANSITION") {
        // 1. Strict permission verification (Foreground location and Activity Recognition)
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasForegroundLocation = hasFine || hasCoarse

        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        val hasActivity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        val isAutoTrackingEnabled = preferencesManager.isAutoTrackingEnabled.first()
        val isTracking = LocationTrackingService.serviceState.value.isTracking

        // 2. Authoritative active trip & lifecycle validation:
        // Do not attempt to recover a trip that is null, finalized, or discarded in the database!
        val activeTripId = preferencesManager.activeTripId.first()
        var effectiveActiveTripId: String? = null
        if (activeTripId != null) {
            val activeTrip = activeTripRepository.getActiveTrip()
            val lifecycleState = tripLifecycleDao.getState(activeTripId)
            val isTerminal = lifecycleState == TripLifecycleState.FINALIZED.value ||
                    lifecycleState == TripLifecycleState.DISCARDED.value
            if (activeTrip != null && !isTerminal && activeTrip.id == activeTripId) {
                effectiveActiveTripId = activeTripId
                // Do not record vehicle enter evidence here for existing trips;
                // motion must be corroborated before in-vehicle state or enter evidence is recorded.
            } else {
                // Clear dangling preference ID
                preferencesManager.clearActiveTripId(activeTripId)
            }
        }

        val isWithinSchedule = preferencesManager.isWithinWorkSchedule()
        val outsideBehavior = preferencesManager.getOutsideScheduleBehavior()

        val action = decisionEngine.evaluateVehicleEnter(
            isAutoTrackingEnabled = isAutoTrackingEnabled,
            hasForegroundLocationPermission = hasForegroundLocation,
            hasBackgroundLocationPermission = hasBackgroundLocation,
            hasActivityRecognitionPermission = hasActivity,
            isServiceTracking = isTracking,
            activeTripId = effectiveActiveTripId,
            isWithinSchedule = isWithinSchedule,
            outsideScheduleBehavior = outsideBehavior
        )
        android.util.Log.d(TAG, "evaluateVehicleEnter result: $action (autoTrack=$isAutoTrackingEnabled, isTracking=$isTracking)")
        TrackingDiagnostics.logAutoStartDecision(
            source = source,
            accepted = action is AutoDriveAction.StartCandidateDrive || action is AutoDriveAction.RecoverTrip,
            reasonCode = action::class.simpleName ?: "UNKNOWN",
            detail = "autoTracking=$isAutoTrackingEnabled active=$isTracking withinSchedule=$isWithinSchedule outside=$outsideBehavior " +
                "fgLoc=$hasForegroundLocation bgLoc=$hasBackgroundLocation act=$hasActivity"
        )

        when (action) {
            is AutoDriveAction.CancelDebounce -> {
                // Request motion validation from service rather than unconditionally cancelling debounce without fresh GPS movement.
                // Do NOT set inVehicleState(true) until motion is corroborated.
                if (!LocationTrackingService.requestStationaryCheckInProcess()) {
                    val validateIntent = Intent(context, LocationTrackingService::class.java).apply {
                        this.action = LocationTrackingService.ACTION_VALIDATE_MOTION
                    }
                    try {
                        context.startService(validateIntent)
                    } catch (_: Exception) {}
                }
            }
            is AutoDriveAction.RecoverTrip -> {
                preferencesManager.setInVehicleState(true)
                val recovered = activeTripRepository.getActiveTrip()?.takeIf { it.id == action.tripId }
                val existingDeadline = preferencesManager.getCandidateDeadline()?.takeIf { it.tripId == action.tripId }
                val nowElapsed = elapsedClock()
                val deadlineElapsed = existingDeadline?.deadlineElapsedMillis ?: (nowElapsed + CANDIDATE_WINDOW_MILLIS)
                startTrackingService(
                    context = context,
                    tripId = action.tripId,
                    suggestedBusiness = recovered?.suggestedBusiness ?: "MySucculentCorner",
                    source = source,
                    requestElapsedMs = nowElapsed,
                    deadlineElapsedMs = deadlineElapsed,
                    vehicleId = recovered?.vehicleId,
                    vehicleName = null,
                    vehicleBluetoothConnected = preferencesManager.isCarBluetoothConnected(),
                    ambiguousVehicleCount = 0
                )
            }
            is AutoDriveAction.StartCandidateDrive -> {
                val requestElapsed = elapsedClock()
                val currentBoot = bootCountProvider.getBootCount()
                val deadlineElapsed = requestElapsed + CANDIDATE_WINDOW_MILLIS
                val candidateUuid = UUID.randomUUID().toString()

                // Persist candidate trip and 4-minute deadline BEFORE launching the service so a
                // transient FGS restriction never leaves an uninitialized deadline=0 state.
                val createdTrip = try {
                    activeTripRepository.createActiveTrip(
                        tripId = candidateUuid,
                        latitude = 0.0,
                        longitude = 0.0,
                        trackingMethod = TrackingMethod.AUTO_DETECTED.value,
                        suggestedBusiness = action.suggestedBusiness
                    )
                } catch (e: Exception) {
                    TrackingDiagnostics.logCandidateLaunchDetail(
                        tripId = candidateUuid,
                        source = source,
                        outcome = "PERSIST_FAILED",
                        exceptionClass = e.javaClass.simpleName,
                        exceptionMessage = e.message,
                        appInForeground = isAppInForeground(context),
                        ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(context),
                        fgLoc = hasForegroundLocation,
                        bgLoc = hasBackgroundLocation,
                        actPerm = hasActivity,
                        candidateStateBefore = "NONE",
                        candidateStateAfter = "NONE",
                        acknowledged = false,
                        elapsedSinceRequestMs = (elapsedClock() - requestElapsed).coerceAtLeast(0L),
                        deadlineRemainingMs = CANDIDATE_WINDOW_MILLIS
                    )
                    return
                }
                val effectiveTripId = createdTrip.id
                preferencesManager.setInVehicleState(true)
                preferencesManager.setActiveTripId(effectiveTripId)
                preferencesManager.recordVehicleEnter(effectiveTripId, requestElapsed, currentBoot)
                preferencesManager.recordCandidateDeadline(effectiveTripId, deadlineElapsed, currentBoot)
                try {
                    tripLifecycleDao.insertOrUpdate(
                        TripLifecycleEntity(effectiveTripId, TripLifecycleState.CANDIDATE.value)
                    )
                } catch (_: Exception) {}

                var resolvedVehicleId: String? = createdTrip.vehicleId
                var resolvedVehicleName: String? = null
                var ambiguousCount = 0
                val btConnected = preferencesManager.isCarBluetoothConnected()
                if (resolvedVehicleId == null && ::vehicleBluetoothMatcher.isInitialized) {
                    val resolution = runCatching { vehicleBluetoothMatcher.resolveConnectedVehicle() }.getOrNull()
                    when (resolution) {
                        is VehicleResolutionResult.Matched -> {
                            activeTripRepository.assignVehicleIfAbsent(effectiveTripId, resolution.vehicle.id)
                            resolvedVehicleId = resolution.vehicle.id
                            resolvedVehicleName = resolution.vehicle.name
                            TrackingDiagnostics.logVehicleResolution(
                                tripId = effectiveTripId,
                                resultCode = "ASSIGNED_ON_CANDIDATE_CREATE",
                                vehicleId = resolvedVehicleId,
                                candidateCount = 1,
                                source = resolution.source
                            )
                        }
                        is VehicleResolutionResult.Ambiguous -> {
                            ambiguousCount = resolution.candidates.size
                            TrackingDiagnostics.logVehicleResolution(
                                tripId = effectiveTripId,
                                resultCode = "AMBIGUOUS_ON_CANDIDATE_CREATE",
                                vehicleId = null,
                                candidateCount = ambiguousCount,
                                source = resolution.source
                            )
                        }
                        else -> Unit
                    }
                }

                TrackingDiagnostics.logCandidate(effectiveTripId, "CANDIDATE_CREATED", 0L, CANDIDATE_WINDOW_MILLIS, createdTrip.totalDistanceMeters)
                startTrackingService(
                    context = context,
                    tripId = effectiveTripId,
                    suggestedBusiness = action.suggestedBusiness,
                    source = source,
                    requestElapsedMs = requestElapsed,
                    deadlineElapsedMs = deadlineElapsed,
                    vehicleId = resolvedVehicleId,
                    vehicleName = resolvedVehicleName,
                    vehicleBluetoothConnected = btConnected,
                    ambiguousVehicleCount = ambiguousCount
                )
            }
            is AutoDriveAction.ScheduleDebounce -> {
                effectiveActiveTripId?.let { TripDebounceWorker.scheduleDebounce(context, it) }
            }
            is AutoDriveAction.Ignore -> {
                // Tracking disabled, permissions missing, or battery saver outside schedule
            }
        }
    }

    private suspend fun handleVehicleExit(context: Context) {
        val activeTrip = activeTripRepository.getActiveTrip()
        if (activeTrip != null && activeTrip.trackingMethod != TrackingMethod.AUTO_DETECTED.value) {
            android.util.Log.d(TAG, "Vehicle exit ignored: active trip is not AUTO_DETECTED (${activeTrip.trackingMethod})")
            return
        }

        preferencesManager.setInVehicleState(false)

        val isTracking = LocationTrackingService.serviceState.value.isTracking
        val activeTripId = preferencesManager.activeTripId.first()
        val hasActiveTrip = activeTripId != null

        val action = decisionEngine.evaluateVehicleExit(
            isServiceTracking = isTracking,
            hasActiveTrip = hasActiveTrip
        )
        android.util.Log.d(TAG, "evaluateVehicleExit result: $action (isTracking=$isTracking, hasActiveTrip=$hasActiveTrip)")

        if (action == AutoDriveAction.ScheduleDebounce) {
            activeTripId?.let {
                LocationTrackingService.scheduleStopDeadline(it, TripDebounceWorker.TRIGGER_ACTIVITY, 240_000L)
                TripDebounceWorker.scheduleDebounce(context, it)
            }
        }
    }

    private suspend fun handleStillEnter(context: Context) {
        val isInVehicle = preferencesManager.isInVehicle()
        val isTracking = LocationTrackingService.serviceState.value.isTracking

        decisionEngine.evaluateStillEnter(isInVehicle, isTracking)

        // Request stationary check from the tracking service without prematurely ending the trip at traffic lights
        if (isTracking && !LocationTrackingService.requestStationaryCheckInProcess()) {
            val checkIntent = Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_CHECK_STATIONARY
            }
            try {
                context.startService(checkIntent)
            } catch (_: Exception) {}
        }
    }

    private suspend fun startTrackingService(
        context: Context,
        tripId: String,
        suggestedBusiness: String,
        source: String,
        requestElapsedMs: Long,
        deadlineElapsedMs: Long,
        vehicleId: String?,
        vehicleName: String?,
        vehicleBluetoothConnected: Boolean,
        ambiguousVehicleCount: Int
    ) {
        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_TRIP_ID, tripId)
            putExtra(LocationTrackingService.EXTRA_TRACKING_METHOD, TrackingMethod.AUTO_DETECTED.value)
            putExtra(LocationTrackingService.EXTRA_SUGGESTED_BUSINESS, suggestedBusiness)
        }
        val fgLoc = hasForegroundLocationPermission(context)
        val bgLoc = hasBackgroundLocationPermission(context)
        val actPerm = hasActivityPermission(context)
        val inForeground = isAppInForeground(context)
        val dozeExempt = isIgnoringBatteryOptimizations(context)

        try {
            val launcher = serviceLauncher
            if (launcher != null) {
                launcher.invoke(context, serviceIntent)
            } else {
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            val nowElapsed = elapsedClock()
            val elapsedSinceReq = (nowElapsed - requestElapsedMs).coerceAtLeast(0L)
            val deadlineRem = (deadlineElapsedMs - nowElapsed).coerceAtLeast(0L)
            android.util.Log.d(TAG, "startForegroundService initiated for trip $tripId")
            TrackingDiagnostics.logCandidate(tripId, "ServiceStarted", elapsedSinceReq, deadlineRem, 0.0)
            TrackingDiagnostics.logCandidateLaunchDetail(
                tripId = tripId,
                source = source,
                outcome = "FGS_STARTED",
                appInForeground = inForeground,
                ignoringBatteryOptimizations = dozeExempt,
                fgLoc = fgLoc,
                bgLoc = bgLoc,
                actPerm = actPerm,
                candidateStateBefore = TripLifecycleState.CANDIDATE.value,
                candidateStateAfter = TripLifecycleState.CANDIDATE.value,
                acknowledged = true,
                elapsedSinceRequestMs = elapsedSinceReq,
                deadlineRemainingMs = deadlineRem
            )
        } catch (e: Exception) {
            val nowElapsed = elapsedClock()
            val elapsedSinceReq = (nowElapsed - requestElapsedMs).coerceAtLeast(0L)
            val deadlineRem = (deadlineElapsedMs - nowElapsed).coerceAtLeast(0L)
            android.util.Log.w(TAG, "startForegroundService blocked/failed for trip $tripId: ${e.javaClass.simpleName}: ${e.message}")

            // Keep candidate alive with background PendingIntent location updates when location
            // permission is granted so Android 12+ background FGS restrictions never drop a real drive.
            val fallbackRegistered = if (fgLoc) {
                registerBackgroundGpsFallback(context)
            } else {
                false
            }

            if (fallbackRegistered) {
                val activeTrip = activeTripRepository.getActiveTrip()?.takeIf { it.id == tripId }
                val startTime = activeTrip?.startTime ?: System.currentTimeMillis()
                val distMeters = activeTrip?.totalDistanceMeters ?: 0.0
                LocationTrackingService.activateFallbackCandidateState(
                    tripId = tripId,
                    startTime = startTime,
                    isCandidate = distMeters < 200.0,
                    distanceMeters = distMeters,
                    vehicleId = activeTrip?.vehicleId ?: vehicleId,
                    vehicleName = vehicleName,
                    vehicleBluetoothConnected = vehicleBluetoothConnected,
                    ambiguousVehicleCount = ambiguousVehicleCount
                )
                if (::notificationHelper.isInitialized) {
                    runCatching {
                        notificationHelper.updateTrackingNotification(
                            distanceMiles = distMeters / 1609.344,
                            durationMillis = 0L,
                            isPaused = false,
                            isCandidate = distMeters < 200.0
                        )
                    }
                }
                TrackingDiagnostics.logCandidate(tripId, "LaunchFallbackActivated", elapsedSinceReq, deadlineRem, distMeters)
                TrackingDiagnostics.logCandidateLaunchDetail(
                    tripId = tripId,
                    source = source,
                    outcome = "BG_LOCATION_FALLBACK_ACTIVE",
                    exceptionClass = e.javaClass.simpleName,
                    exceptionMessage = e.message,
                    appInForeground = inForeground,
                    ignoringBatteryOptimizations = dozeExempt,
                    fgLoc = fgLoc,
                    bgLoc = bgLoc,
                    actPerm = actPerm,
                    candidateStateBefore = TripLifecycleState.CANDIDATE.value,
                    candidateStateAfter = TripLifecycleState.CANDIDATE.value,
                    acknowledged = true,
                    elapsedSinceRequestMs = elapsedSinceReq,
                    deadlineRemainingMs = deadlineRem
                )
                return
            }

            // Genuine unrecoverable failure (no fallback possible or missing location permission)
            try {
                if (preferencesManager.activeTripId.first() == tripId) {
                    preferencesManager.clearActiveTripId(tripId)
                    preferencesManager.clearCandidateDeadline(tripId)
                    activeTripRepository.clearActiveTrip(tripId)
                    try {
                        tripLifecycleDao.insertOrUpdate(
                            TripLifecycleEntity(tripId, TripLifecycleState.DISCARDED.value)
                        )
                    } catch (_: Exception) {}
                    LocationTrackingService.clearFallbackCandidateState(tripId)
                    if (::bluetoothArmedDriveMonitor.isInitialized) {
                        bluetoothArmedDriveMonitor.resetTriggerCooldown()
                    }
                    TrackingDiagnostics.logCandidate(tripId, "LaunchFailedReconciled", elapsedSinceReq, deadlineRem, 0.0)
                    TrackingDiagnostics.logCandidateLaunchDetail(
                        tripId = tripId,
                        source = source,
                        outcome = "LAUNCH_FAILED_RECONCILED",
                        exceptionClass = e.javaClass.simpleName,
                        exceptionMessage = e.message,
                        appInForeground = inForeground,
                        ignoringBatteryOptimizations = dozeExempt,
                        fgLoc = fgLoc,
                        bgLoc = bgLoc,
                        actPerm = actPerm,
                        candidateStateBefore = TripLifecycleState.CANDIDATE.value,
                        candidateStateAfter = TripLifecycleState.DISCARDED.value,
                        acknowledged = false,
                        elapsedSinceRequestMs = elapsedSinceReq,
                        deadlineRemainingMs = deadlineRem
                    )
                }
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun registerBackgroundGpsFallback(context: Context): Boolean {
        val pendingIntent = buildBackgroundGpsPendingIntent(context)
        val testRegistrar = backgroundLocationRegistrar
        if (testRegistrar != null) {
            return testRegistrar.invoke(context, pendingIntent)
        }
        return try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
                .setMinUpdateIntervalMillis(1_500L)
                .setWaitForAccurateLocation(false)
                .build()
            LocationServices.getFusedLocationProviderClient(context)
                .requestLocationUpdates(request, pendingIntent)
                .await()
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to register background GPS fallback: ${e.message}", e)
            false
        }
    }

    internal suspend fun handleBackgroundGpsUpdate(context: Context, locations: List<Location>) {
        if (LocationTrackingService.isServiceInstanceRunning()) {
            cancelBackgroundGpsFallback(context)
            return
        }
        val activeTrip = activeTripRepository.getActiveTrip()
        if (activeTrip == null) {
            cancelBackgroundGpsFallback(context)
            LocationTrackingService.clearFallbackCandidateState(null)
            return
        }
        val lifecycleState = runCatching { tripLifecycleDao.getState(activeTrip.id) }.getOrNull()
        if (lifecycleState == TripLifecycleState.FINALIZED.value ||
            lifecycleState == TripLifecycleState.DISCARDED.value
        ) {
            cancelBackgroundGpsFallback(context)
            LocationTrackingService.clearFallbackCandidateState(activeTrip.id)
            return
        }

        // Try to promote to full foreground service cleanly if now eligible
        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_TRIP_ID, activeTrip.id)
            putExtra(LocationTrackingService.EXTRA_TRACKING_METHOD, activeTrip.trackingMethod)
            putExtra(LocationTrackingService.EXTRA_SUGGESTED_BUSINESS, activeTrip.suggestedBusiness)
        }
        val promoted = try {
            val launcher = serviceLauncher
            if (launcher != null) launcher.invoke(context, serviceIntent)
            else ContextCompat.startForegroundService(context, serviceIntent)
            true
        } catch (_: Exception) {
            false
        }
        if (promoted) {
            cancelBackgroundGpsFallback(context)
            TrackingDiagnostics.logCandidateLaunchDetail(
                tripId = activeTrip.id,
                source = "BG_GPS_PROMOTION",
                outcome = "FGS_PROMOTED_FROM_BG_GPS",
                appInForeground = isAppInForeground(context),
                ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(context),
                fgLoc = hasForegroundLocationPermission(context),
                bgLoc = hasBackgroundLocationPermission(context),
                actPerm = hasActivityPermission(context),
                candidateStateBefore = lifecycleState ?: TripLifecycleState.CANDIDATE.value,
                candidateStateAfter = TripLifecycleState.TRACKING.value,
                acknowledged = true,
                elapsedSinceRequestMs = 0L,
                deadlineRemainingMs = 0L
            )
        }

        val filter = if (::filterGpsPointUseCase.isInitialized) filterGpsPointUseCase else FilterGpsPointUseCase()
        val existingWaypoints = activeTripRepository.getWaypoints(activeTrip.id)
        var lastWp: Waypoint? = existingWaypoints.lastOrNull()
        val anchorWp: Waypoint? = existingWaypoints.firstOrNull()
        var currentDistanceMeters = activeTrip.totalDistanceMeters
        var currentSpeedMph = 0.0
        var waypointCount = existingWaypoints.size

        for (loc in locations.sortedBy { it.elapsedRealtimeNanos }) {
            when (val res = filter.filter(loc, lastWp, bootCountProvider.getBootCount())) {
                is FilterResult.Accepted -> {
                    activeTripRepository.recordWaypoint(activeTrip.id, res.waypoint)
                    currentDistanceMeters += res.deltaDistanceMeters
                    currentSpeedMph = res.speedMph
                    lastWp = res.waypoint
                    waypointCount++
                    activeTripRepository.updateTripProgress(
                        tripId = activeTrip.id,
                        distanceMeters = currentDistanceMeters,
                        latitude = res.waypoint.latitude,
                        longitude = res.waypoint.longitude,
                        timestamp = res.waypoint.timestamp
                    )
                }
                is FilterResult.Rejected -> Unit
            }
        }

        var displacementConfirmed = currentDistanceMeters >= 200.0
        val effectiveAnchor = anchorWp ?: lastWp
        if (!displacementConfirmed && effectiveAnchor != null && lastWp != null) {
            val out = FloatArray(1)
            Location.distanceBetween(
                effectiveAnchor.latitude, effectiveAnchor.longitude,
                lastWp.latitude, lastWp.longitude,
                out
            )
            if (out[0] >= 200.0f) {
                displacementConfirmed = true
            }
        }

        val deadline = preferencesManager.getCandidateDeadline()?.takeIf { it.tripId == activeTrip.id }
        val nowElapsed = elapsedClock()
        if (displacementConfirmed) {
            preferencesManager.clearCandidateDeadline(activeTrip.id)
            runCatching { tripLifecycleDao.insertOrUpdate(TripLifecycleEntity(activeTrip.id, TripLifecycleState.TRACKING.value)) }
            TrackingDiagnostics.logCandidateTransition(activeTrip.id, "CONFIRMED_BY_DISPLACEMENT", "Fallback distance=${currentDistanceMeters}m")
        } else if (deadline != null && deadline.bootCount == bootCountProvider.getBootCount() &&
            nowElapsed >= deadline.deadlineElapsedMillis
        ) {
            TrackingDiagnostics.logCandidateTransition(activeTrip.id, "EXPIRED_FALLBACK_DISCARD", "Distance=${currentDistanceMeters}m < 200m")
            cancelBackgroundGpsFallback(context)
            if (::tripFinalizationCoordinator.isInitialized) {
                tripFinalizationCoordinator.discardFalseTrip(activeTrip.id)
            } else {
                activeTripRepository.clearActiveTrip(activeTrip.id)
                runCatching { tripLifecycleDao.insertOrUpdate(TripLifecycleEntity(activeTrip.id, TripLifecycleState.DISCARDED.value)) }
            }
            preferencesManager.clearActiveTripId(activeTrip.id)
            preferencesManager.clearCandidateDeadline(activeTrip.id)
            preferencesManager.setInVehicleState(false)
            LocationTrackingService.clearFallbackCandidateState(activeTrip.id)
            if (::bluetoothArmedDriveMonitor.isInitialized) {
                bluetoothArmedDriveMonitor.resetTriggerCooldown()
                bluetoothArmedDriveMonitor.reconcile()
            }
            return
        }

        var vehicleId = activeTrip.vehicleId
        var vehicleName: String? = null
        var ambiguousCount = 0
        if (vehicleId == null && ::vehicleBluetoothMatcher.isInitialized && !preferencesManager.isManualVehicleAssignment(activeTrip.id)) {
            when (val res = runCatching { vehicleBluetoothMatcher.resolveConnectedVehicle() }.getOrNull()) {
                is VehicleResolutionResult.Matched -> {
                    activeTripRepository.assignVehicleIfAbsent(activeTrip.id, res.vehicle.id)
                    vehicleId = res.vehicle.id
                    vehicleName = res.vehicle.name
                }
                is VehicleResolutionResult.Ambiguous -> {
                    ambiguousCount = res.candidates.size
                }
                else -> Unit
            }
        }

        if (!promoted) {
            LocationTrackingService.activateFallbackCandidateState(
                tripId = activeTrip.id,
                startTime = activeTrip.startTime,
                isCandidate = !displacementConfirmed,
                distanceMeters = currentDistanceMeters,
                speedMph = currentSpeedMph,
                waypointCount = waypointCount,
                lastWaypoint = lastWp,
                vehicleId = vehicleId,
                vehicleName = vehicleName,
                vehicleBluetoothConnected = preferencesManager.isCarBluetoothConnected(),
                ambiguousVehicleCount = ambiguousCount
            )
            if (::notificationHelper.isInitialized) {
                runCatching {
                    notificationHelper.updateTrackingNotification(
                        distanceMiles = currentDistanceMeters / 1609.344,
                        durationMillis = maxOf(0L, System.currentTimeMillis() - activeTrip.startTime),
                        isPaused = false,
                        isCandidate = !displacementConfirmed
                    )
                }
            }
        }
    }

    private fun hasForegroundLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun hasActivityPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun isAppInForeground(context: Context): Boolean = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return@runCatching false
        am.runningAppProcesses?.any {
            it.processName == context.packageName &&
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } ?: false
    }.getOrDefault(false)

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return@runCatching false
        pm.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)
}
