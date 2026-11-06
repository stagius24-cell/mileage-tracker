package com.businesstracker.mileage.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.TrackingMethod
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
    @Inject lateinit var ioDispatcher: CoroutineDispatcher

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || !ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val pendingResult = goAsync()

        receiverScope.launch(ioDispatcher) {
            try {
                for (event in result.transitionEvents) {
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
                                handleVehicleExit(context)
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
        // 1. Mark in-vehicle state as active and cancel any active 4-minute debounce timer
        preferencesManager.setInVehicleState(true)
        TripDebounceWorker.cancelDebounce(context)

        // 2. Check if auto tracking is enabled by user
        val isAutoTrackEnabled = preferencesManager.isAutoTrackingEnabled.first()
        if (!isAutoTrackEnabled) return

        // 3. Prevent duplicate trips: ensure no tracking service is already running
        if (LocationTrackingService.serviceState.value.isTracking) return
        if (preferencesManager.activeTripId.first() != null) return

        // 4. Evaluate work schedule & outside behavior
        val isWithinSchedule = preferencesManager.isWithinWorkSchedule()
        val outsideBehavior = preferencesManager.getOutsideScheduleBehavior()

        if (!isWithinSchedule && outsideBehavior == TrackingPreferencesManager.BEHAVIOR_CONSERVE_BATTERY) {
            // Conserve battery outside work hours: skip tracking completely
            return
        }

        val suggestedBusiness = if (isWithinSchedule) "MySucculentCorner" else "Personal"
        val tripId = UUID.randomUUID().toString()

        // 5. Start Foreground LocationTrackingService with location type (starts without opening app)
        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_TRIP_ID, tripId)
            putExtra(LocationTrackingService.EXTRA_TRACKING_METHOD, TrackingMethod.AUTO_DETECTED.value)
            putExtra(LocationTrackingService.EXTRA_SUGGESTED_BUSINESS, suggestedBusiness)
        }

        ContextCompat.startForegroundService(context, serviceIntent)
    }

    private suspend fun handleVehicleExit(context: Context) {
        // 1. Mark in-vehicle state as false
        preferencesManager.setInVehicleState(false)

        // 2. If a trip is currently active, initiate the 4-minute debounce timer via WorkManager
        if (LocationTrackingService.serviceState.value.isTracking) {
            TripDebounceWorker.scheduleDebounce(context)
        }
    }
}
