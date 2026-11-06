package com.businesstracker.mileage.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.tracking.AutoDriveAction
import com.businesstracker.mileage.domain.tracking.AutoDriveDecisionEngine
import com.businesstracker.mileage.domain.tracking.CurrentLocation
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    @Inject lateinit var decisionEngine: AutoDriveDecisionEngine
    @Inject lateinit var ioDispatcher: CoroutineDispatcher

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || !ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val pendingResult = goAsync()

        receiverScope.launch(ioDispatcher) {
            try {
                // Process transition events in chronological order
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
        // 1. Update in-vehicle state independently from anchor or trip state
        preferencesManager.setInVehicleState(true)

        // 2. Permission evaluation
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasLocation = hasFine || hasCoarse

        val hasActivity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        val hasPermissions = hasLocation && hasActivity
        val isAutoTrackingEnabled = preferencesManager.isAutoTrackingEnabled.first()
        val isTracking = LocationTrackingService.serviceState.value.isTracking
        val activeTripId = preferencesManager.activeTripId.first()
        val existingAnchor = preferencesManager.getVehicleAnchor()

        val currentLocation: CurrentLocation? = if (hasLocation) {
            getCurrentLocation()
        } else null

        val isWithinSchedule = preferencesManager.isWithinWorkSchedule()
        val outsideBehavior = preferencesManager.getOutsideScheduleBehavior()

        val action = decisionEngine.evaluateVehicleEnter(
            isAutoTrackingEnabled = isAutoTrackingEnabled,
            hasPermissions = hasPermissions,
            isServiceTracking = isTracking,
            activeTripId = activeTripId,
            existingAnchor = existingAnchor,
            currentLocation = currentLocation,
            isWithinSchedule = isWithinSchedule,
            outsideScheduleBehavior = outsideBehavior,
            calculateDistanceMeters = { lat1, lon1, lat2, lon2 ->
                val results = FloatArray(1)
                Location.distanceBetween(lat1, lon1, lat2, lon2, results)
                results[0]
            }
        )

        when (action) {
            is AutoDriveAction.CancelDebounce -> {
                TripDebounceWorker.cancelDebounce(context)
            }
            is AutoDriveAction.RecoverTrip -> {
                TripDebounceWorker.cancelDebounce(context)
                startTrackingService(context, action.tripId, "MySucculentCorner")
            }
            is AutoDriveAction.SetAnchor -> {
                TripDebounceWorker.cancelDebounce(context)
                preferencesManager.setVehicleAnchor(action.latitude, action.longitude, action.timestamp)
            }
            is AutoDriveAction.StartTrip -> {
                TripDebounceWorker.cancelDebounce(context)
                preferencesManager.clearVehicleAnchor()
                val newTripId = UUID.randomUUID().toString()
                startTrackingService(context, newTripId, action.suggestedBusiness)
            }
            is AutoDriveAction.ScheduleDebounce -> {
                TripDebounceWorker.scheduleDebounce(context)
            }
            is AutoDriveAction.Ignore -> {
                // Movement below threshold, invalid accuracy, or tracking disabled
            }
        }
    }

    private suspend fun handleVehicleExit(context: Context) {
        // Mark in-vehicle state as false
        preferencesManager.setInVehicleState(false)

        val isTracking = LocationTrackingService.serviceState.value.isTracking
        val hasActiveTrip = preferencesManager.activeTripId.first() != null

        val action = decisionEngine.evaluateVehicleExit(
            isServiceTracking = isTracking,
            hasActiveTrip = hasActiveTrip
        )

        if (action == AutoDriveAction.ScheduleDebounce) {
            TripDebounceWorker.scheduleDebounce(context)
        }
    }

    private suspend fun handleStillEnter(context: Context) {
        val isInVehicle = preferencesManager.isInVehicle()
        val isTracking = LocationTrackingService.serviceState.value.isTracking

        decisionEngine.evaluateStillEnter(isInVehicle, isTracking)
        // Does not terminate or debounce active drive (user is stationary at red light or in traffic)
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): CurrentLocation? {
        return try {
            val cts = CancellationTokenSource()
            val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                ?: fusedLocationClient.lastLocation.await()
            if (location != null) {
                CurrentLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = if (location.hasAccuracy()) location.accuracy else 999.0f,
                    timestamp = location.time
                )
            } else null
        } catch (_: Exception) {
            null
        }
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
