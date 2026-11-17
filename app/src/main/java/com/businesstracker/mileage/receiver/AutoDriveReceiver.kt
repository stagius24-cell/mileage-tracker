package com.businesstracker.mileage.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.businesstracker.mileage.data.local.TripLifecycleDao
import com.businesstracker.mileage.data.local.entity.TripLifecycleState
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.domain.service.BootCountProvider
import com.businesstracker.mileage.domain.tracking.AutoDriveAction
import com.businesstracker.mileage.domain.tracking.AutoDriveDecisionEngine
import com.businesstracker.mileage.domain.tracking.TrackingDiagnostics
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.worker.TripDebounceWorker
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class AutoDriveReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "AutoDriveReceiver"
        const val ACTION_ACTIVITY_TRANSITION = "com.businesstracker.mileage.ACTION_ACTIVITY_TRANSITION"
        const val TRANSITION_REQUEST_CODE = 3001
        const val START_TRIGGER_DISPLACEMENT_METERS = 200.0f
        const val MAX_TRANSITION_AGE_NANOS = 120_000_000_000L // 2 minutes
    }

    @Inject lateinit var preferencesManager: TrackingPreferencesManager
    @Inject lateinit var activeTripRepository: ActiveTripRepository
    @Inject lateinit var tripLifecycleDao: TripLifecycleDao
    @Inject lateinit var decisionEngine: AutoDriveDecisionEngine
    @Inject lateinit var bluetoothArmedDriveMonitor: BluetoothArmedDriveMonitor
    @Inject lateinit var ioDispatcher: CoroutineDispatcher
    @Inject lateinit var bootCountProvider: BootCountProvider

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

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
        val hasSelectedDevice = preferencesManager.getVehicleBluetoothAddress().isNotBlank()
        val isCarConnected = preferencesManager.isCarBluetoothConnected()
        if (!isAutoTrackingEnabled || !hasSelectedDevice || !isCarConnected) {
            TrackingDiagnostics.logArmedMonitor("UPDATE_IGNORED", "tracking/device/connection gate is false")
            return
        }

        // Candidate and active sessions already have bounded location verification. Do not
        // request additional location samples or create another trip while one exists.
        if (LocationTrackingService.serviceState.value.isTracking ||
            activeTripRepository.getActiveTrip() != null
        ) return

        if (bluetoothArmedDriveMonitor.observeActivityUpdate(probableActivities)) {
            TrackingDiagnostics.logArmedMonitor("MOVEMENT_CONFIRMED", "starting existing automatic candidate flow")
            handleVehicleEnter(context)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleVehicleEnter(context: Context) {
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
        TrackingDiagnostics.logArmedMonitor(
            "VEHICLE_ENTER_DECISION",
            "result=${action::class.simpleName} autoTracking=$isAutoTrackingEnabled active=$isTracking permissions=${hasForegroundLocation && hasBackgroundLocation && hasActivity}"
        )

        when (action) {
            is AutoDriveAction.CancelDebounce -> {
                // Request motion validation from service rather than unconditionally cancelling debounce without fresh GPS movement.
                // Do NOT set inVehicleState(true) until motion is corroborated.
                val validateIntent = Intent(context, LocationTrackingService::class.java).apply {
                    this.action = LocationTrackingService.ACTION_VALIDATE_MOTION
                }
                try {
                    context.startService(validateIntent)
                } catch (_: Exception) {}
            }
            is AutoDriveAction.RecoverTrip -> {
                preferencesManager.setInVehicleState(true)
                startTrackingService(context, action.tripId, "MySucculentCorner")
            }
            is AutoDriveAction.StartCandidateDrive -> {
                val newTripId = UUID.randomUUID().toString()
                // Persist newTripId, inVehicle state, and vehicle enter evidence for new candidate drive
                preferencesManager.setInVehicleState(true)
                preferencesManager.setActiveTripId(newTripId)
                preferencesManager.recordVehicleEnter(
                    newTripId,
                    SystemClock.elapsedRealtime(),
                    bootCountProvider.getBootCount()
                )
                startTrackingService(context, newTripId, action.suggestedBusiness)
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
            activeTripId?.let { TripDebounceWorker.scheduleDebounce(context, it) }
        }
    }

    private suspend fun handleStillEnter(context: Context) {
        val isInVehicle = preferencesManager.isInVehicle()
        val isTracking = LocationTrackingService.serviceState.value.isTracking

        decisionEngine.evaluateStillEnter(isInVehicle, isTracking)

        // Request stationary check from the tracking service without prematurely ending the trip at traffic lights
        if (isTracking) {
            val checkIntent = Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_CHECK_STATIONARY
            }
            try {
                context.startService(checkIntent)
            } catch (_: Exception) {}
        }
    }

    private fun startTrackingService(context: Context, tripId: String, suggestedBusiness: String) {
        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_TRIP_ID, tripId)
            putExtra(LocationTrackingService.EXTRA_TRACKING_METHOD, TrackingMethod.AUTO_DETECTED.value)
            putExtra(LocationTrackingService.EXTRA_SUGGESTED_BUSINESS, suggestedBusiness)
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
            android.util.Log.d(TAG, "startForegroundService initiated for trip $tripId")
            TrackingDiagnostics.logCandidate(tripId, "ServiceStarted", 0L, 240_000L, 0.0)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start LocationTrackingService: ${e.message}", e)
            // Reconcile reserved candidate ID safely if service launch fails
            receiverScope.launch(ioDispatcher) {
                try {
                    if (preferencesManager.activeTripId.first() == tripId) {
                        preferencesManager.clearActiveTripId(tripId)
                        preferencesManager.clearCandidateDeadline(tripId)
                        TrackingDiagnostics.logCandidate(tripId, "LaunchFailedReconciled", 0L, 0L, 0.0)
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
