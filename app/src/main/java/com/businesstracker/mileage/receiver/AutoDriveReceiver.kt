package com.businesstracker.mileage.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.tracking.AutoDriveAction
import com.businesstracker.mileage.domain.tracking.AutoDriveDecisionEngine
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.worker.TripDebounceWorker
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
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
        const val ACTION_ACTIVITY_TRANSITION = "com.businesstracker.mileage.ACTION_ACTIVITY_TRANSITION"
        const val TRANSITION_REQUEST_CODE = 3001
        const val START_TRIGGER_DISPLACEMENT_METERS = 200.0f
    }

    @Inject lateinit var preferencesManager: TrackingPreferencesManager
    @Inject lateinit var decisionEngine: AutoDriveDecisionEngine
    @Inject lateinit var ioDispatcher: CoroutineDispatcher

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || !ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val pendingResult = goAsync()

        receiverScope.launch(ioDispatcher) {
            try {
                val sortedEvents = result.transitionEvents.sortedBy { it.elapsedRealTimeNanos }
                for (event in sortedEvents) {
                    when (event.activityType) {
                        DetectedActivity.IN_VEHICLE -> {
                            when (event.transitionType) {
                                ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                                    handleVehicleEnter(context)
                                }
                                ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                                    handleVehicleExit(context)
                                }
                            }
                        }
                        DetectedActivity.STILL -> {
                            if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                                handleStillEnter(context)
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleVehicleEnter(context: Context) {
        // 1. Update in-vehicle state independently
        preferencesManager.setInVehicleState(true)

        // 2. Strict permission verification (Foreground, Background, Activity Recognition)
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
        val activeTripId = preferencesManager.activeTripId.first()
        val isWithinSchedule = preferencesManager.isWithinWorkSchedule()
        val outsideBehavior = preferencesManager.getOutsideScheduleBehavior()

        val action = decisionEngine.evaluateVehicleEnter(
            isAutoTrackingEnabled = isAutoTrackingEnabled,
            hasForegroundLocationPermission = hasForegroundLocation,
            hasBackgroundLocationPermission = hasBackgroundLocation,
            hasActivityRecognitionPermission = hasActivity,
            isServiceTracking = isTracking,
            activeTripId = activeTripId,
            isWithinSchedule = isWithinSchedule,
            outsideScheduleBehavior = outsideBehavior
        )

        when (action) {
            is AutoDriveAction.CancelDebounce -> {
                TripDebounceWorker.cancelDebounce(context)
            }
            is AutoDriveAction.RecoverTrip -> {
                TripDebounceWorker.cancelDebounce(context)
                startTrackingService(context, action.tripId, "MySucculentCorner")
            }
            is AutoDriveAction.StartCandidateDrive -> {
                TripDebounceWorker.cancelDebounce(context)
                val newTripId = UUID.randomUUID().toString()
                startTrackingService(context, newTripId, action.suggestedBusiness)
            }
            is AutoDriveAction.ScheduleDebounce -> {
                activeTripId?.let { TripDebounceWorker.scheduleDebounce(context, it) }
            }
            is AutoDriveAction.Ignore -> {
                // Tracking disabled, permissions missing, or battery saver outside schedule
            }
        }
    }

    private suspend fun handleVehicleExit(context: Context) {
        preferencesManager.setInVehicleState(false)

        val isTracking = LocationTrackingService.serviceState.value.isTracking
        val activeTripId = preferencesManager.activeTripId.first()
        val hasActiveTrip = activeTripId != null

        val action = decisionEngine.evaluateVehicleExit(
            isServiceTracking = isTracking,
            hasActiveTrip = hasActiveTrip
        )

        if (action == AutoDriveAction.ScheduleDebounce) {
            activeTripId?.let { TripDebounceWorker.scheduleDebounce(context, it) }
        }
    }

    private suspend fun handleStillEnter(context: Context) {
        val isInVehicle = preferencesManager.isInVehicle()
        val isTracking = LocationTrackingService.serviceState.value.isTracking

        decisionEngine.evaluateStillEnter(isInVehicle, isTracking)
    }

    private fun startTrackingService(context: Context, tripId: String, suggestedBusiness: String) {
        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_TRIP_ID, tripId)
            putExtra(LocationTrackingService.EXTRA_TRACKING_METHOD, TrackingMethod.AUTO_DETECTED.value)
            putExtra(LocationTrackingService.EXTRA_SUGGESTED_BUSINESS, suggestedBusiness)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
