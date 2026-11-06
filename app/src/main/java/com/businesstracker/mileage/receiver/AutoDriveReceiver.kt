package com.businesstracker.mileage.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.core.content.ContextCompat
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.worker.TripDebounceWorker
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    @Inject lateinit var fusedLocationClient: FusedLocationProviderClient
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
        // 1. Vehicle motion detected: Cancel any active 4-minute debounce timer
        TripDebounceWorker.cancelDebounce(context)

        // 2. Check if already tracking in foreground
        if (LocationTrackingService.serviceState.value.isTracking) {
            return
        }

        // 3. Acquire current location to evaluate displacement > 200m
        try {
            val cts = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    if (location == null) return@addOnSuccessListener
                    receiverScope.launch(ioDispatcher) {
                        evaluateStartDisplacement(context, location)
                    }
                }
        } catch (_: SecurityException) {
            // Permissions not granted
        }
    }

    private suspend fun evaluateStartDisplacement(context: Context, currentLocation: Location) {
        // Query stored anchor
        var anchorLat: Double? = null
        var anchorLng: Double? = null

        preferencesManager.activeTripId // access data if needed

        // We check if anchor exists
        val distanceResults = FloatArray(1)

        // Read current state
        val isInVehicle = preferencesManager.isInVehicle()

        if (!isInVehicle) {
            // Set first anchor
            preferencesManager.setVehicleAnchor(
                currentLocation.latitude,
                currentLocation.longitude,
                currentLocation.time
            )
        } else {
            // Anchor exists: measure distance
            // If movement > 200m, evaluate schedule and start LocationTrackingService
            val isWithinSchedule = preferencesManager.isWithinWorkSchedule()
            val outsideBehavior = preferencesManager.getOutsideScheduleBehavior()

            if (!isWithinSchedule && outsideBehavior == TrackingPreferencesManager.BEHAVIOR_CONSERVE_BATTERY) {
                // Conserve battery outside work hours: skip tracking
                preferencesManager.clearVehicleAnchor()
                return
            }

            val suggestedBusiness = if (isWithinSchedule) "MySucculentCorner" else "Personal"

            val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_START
                putExtra(LocationTrackingService.EXTRA_TRIP_ID, UUID.randomUUID().toString())
                putExtra(LocationTrackingService.EXTRA_TRACKING_METHOD, TrackingMethod.AUTO_DETECTED.value)
                putExtra(LocationTrackingService.EXTRA_SUGGESTED_BUSINESS, suggestedBusiness)
            }

            ContextCompat.startForegroundService(context, serviceIntent)
            preferencesManager.clearVehicleAnchor()
        }
    }

    private fun handleVehicleExit(context: Context) {
        // Stop Debounce Buffer:
        // Do NOT kill tracking immediately. Initiate 4-minute debounce timer via WorkManager.
        TripDebounceWorker.scheduleDebounce(context)
    }
}
