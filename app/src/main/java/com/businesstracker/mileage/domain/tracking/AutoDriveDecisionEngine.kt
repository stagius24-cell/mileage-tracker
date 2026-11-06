package com.businesstracker.mileage.domain.tracking

import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.preferences.VehicleAnchor
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AutoDriveAction {
    data class SetAnchor(val latitude: Double, val longitude: Double, val timestamp: Long) : AutoDriveAction
    data class StartTrip(val suggestedBusiness: String) : AutoDriveAction
    data class RecoverTrip(val tripId: String) : AutoDriveAction
    object CancelDebounce : AutoDriveAction
    object ScheduleDebounce : AutoDriveAction
    object Ignore : AutoDriveAction
}

data class CurrentLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestamp: Long
)

@Singleton
class AutoDriveDecisionEngine @Inject constructor() {

    companion object {
        const val MIN_DISPLACEMENT_METERS = 200.0f
        const val MAX_ACCURACY_THRESHOLD_METERS = 50.0f
        const val ANCHOR_MAX_AGE_MS = 30 * 60 * 1000L // 30 minutes
    }

    /**
     * Evaluates an IN_VEHICLE ENTER activity transition event.
     */
    fun evaluateVehicleEnter(
        isAutoTrackingEnabled: Boolean,
        hasPermissions: Boolean,
        isServiceTracking: Boolean,
        activeTripId: String?,
        existingAnchor: VehicleAnchor?,
        currentLocation: CurrentLocation?,
        isWithinSchedule: Boolean,
        outsideScheduleBehavior: String,
        calculateDistanceMeters: (Double, Double, Double, Double) -> Float
    ): AutoDriveAction {
        // 1. Respect user settings & required permissions
        if (!isAutoTrackingEnabled || !hasPermissions) {
            return AutoDriveAction.Ignore
        }

        // 2. Prevent duplicate service & trips if already tracking
        if (isServiceTracking) {
            return AutoDriveAction.CancelDebounce
        }

        // 3. Interruption recovery: Recover existing active trip if present
        if (activeTripId != null) {
            return AutoDriveAction.RecoverTrip(activeTripId)
        }

        // 4. Require valid GPS fix
        if (currentLocation == null) {
            return AutoDriveAction.Ignore
        }

        // 5. GPS Accuracy check: Reject poor fixes to avoid GPS drift false trips
        if (currentLocation.accuracyMeters > MAX_ACCURACY_THRESHOLD_METERS) {
            return AutoDriveAction.Ignore
        }

        // 6. Anchor evaluation
        val isAnchorExpired = existingAnchor != null &&
                (currentLocation.timestamp - existingAnchor.timestamp) > ANCHOR_MAX_AGE_MS

        if (existingAnchor == null || isAnchorExpired) {
            // First IN_VEHICLE event or expired anchor: establish initial vehicle anchor
            return AutoDriveAction.SetAnchor(
                latitude = currentLocation.latitude,
                longitude = currentLocation.longitude,
                timestamp = currentLocation.timestamp
            )
        }

        // 7. Calculate actual displacement from anchor
        val displacementMeters = calculateDistanceMeters(
            existingAnchor.latitude,
            existingAnchor.longitude,
            currentLocation.latitude,
            currentLocation.longitude
        )

        return if (displacementMeters >= MIN_DISPLACEMENT_METERS) {
            // Meaningful movement confirmed >= 200m
            if (!isWithinSchedule && outsideScheduleBehavior == TrackingPreferencesManager.BEHAVIOR_CONSERVE_BATTERY) {
                AutoDriveAction.Ignore
            } else {
                val suggestedBusiness = if (isWithinSchedule) "MySucculentCorner" else "Personal"
                AutoDriveAction.StartTrip(suggestedBusiness)
            }
        } else {
            // Movement below 200m: maintain anchor, do not start trip
            AutoDriveAction.Ignore
        }
    }

    /**
     * Evaluates an IN_VEHICLE EXIT activity transition event.
     */
    fun evaluateVehicleExit(
        isServiceTracking: Boolean,
        hasActiveTrip: Boolean
    ): AutoDriveAction {
        return if (isServiceTracking || hasActiveTrip) {
            AutoDriveAction.ScheduleDebounce
        } else {
            AutoDriveAction.Ignore
        }
    }

    /**
     * Evaluates a STILL ENTER transition event.
     * STILL ENTER while in a vehicle (traffic light, congestion) does NOT stop or debounce the trip.
     */
    fun evaluateStillEnter(
        isInVehicle: Boolean,
        isServiceTracking: Boolean
    ): AutoDriveAction {
        // If in-vehicle or tracking, STILL is just a temporary stop (e.g. red light). Do not end trip.
        return AutoDriveAction.Ignore
    }
}
