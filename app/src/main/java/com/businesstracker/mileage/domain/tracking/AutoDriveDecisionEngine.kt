package com.businesstracker.mileage.domain.tracking

import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AutoDriveAction {
    data class StartCandidateDrive(val suggestedBusiness: String) : AutoDriveAction
    data class RecoverTrip(val tripId: String) : AutoDriveAction
    object CancelDebounce : AutoDriveAction
    object ScheduleDebounce : AutoDriveAction
    object Ignore : AutoDriveAction
}

enum class TripStopDecision {
    FINALIZE_TRIP,
    DISCARD_FALSE_TRIP
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
    }

    /**
     * Evaluates an IN_VEHICLE ENTER activity transition event.
     * Operates reliably on a SINGLE transition event without requiring subsequent ENTER broadcasts.
     */
    fun evaluateVehicleEnter(
        isAutoTrackingEnabled: Boolean,
        hasForegroundLocationPermission: Boolean,
        hasBackgroundLocationPermission: Boolean,
        hasActivityRecognitionPermission: Boolean,
        isServiceTracking: Boolean,
        activeTripId: String?,
        isWithinSchedule: Boolean,
        outsideScheduleBehavior: String
    ): AutoDriveAction {
        // 1. Strict permission & setting enforcement
        val hasAllPermissions = hasForegroundLocationPermission &&
                hasBackgroundLocationPermission &&
                hasActivityRecognitionPermission

        if (!isAutoTrackingEnabled || !hasAllPermissions) {
            return AutoDriveAction.Ignore
        }

        // 2. Prevent duplicate starts if already tracking
        if (isServiceTracking) {
            return AutoDriveAction.CancelDebounce
        }

        // 3. Interruption recovery: Recover existing active trip if present
        if (activeTripId != null) {
            return AutoDriveAction.RecoverTrip(activeTripId)
        }

        // 4. Work schedule evaluation
        if (!isWithinSchedule && outsideScheduleBehavior == TrackingPreferencesManager.BEHAVIOR_CONSERVE_BATTERY) {
            return AutoDriveAction.Ignore
        }

        val suggestedBusiness = if (isWithinSchedule) "MySucculentCorner" else "Personal"

        // 5. Start candidate drive tracking session immediately from the single ENTER event
        return AutoDriveAction.StartCandidateDrive(suggestedBusiness)
    }

    /**
     * Evaluates continuous GPS updates during an uninterrupted drive to confirm 200m movement.
     */
    fun evaluateCandidateMovement(
        anchorLatitude: Double,
        anchorLongitude: Double,
        currentLatitude: Double,
        currentLongitude: Double,
        accumulatedDistanceMeters: Double,
        calculateDistanceMeters: (Double, Double, Double, Double) -> Float
    ): Boolean {
        if (accumulatedDistanceMeters >= MIN_DISPLACEMENT_METERS) {
            return true
        }
        val distanceResults = calculateDistanceMeters(
            anchorLatitude, anchorLongitude,
            currentLatitude, currentLongitude
        )
        return distanceResults >= MIN_DISPLACEMENT_METERS
    }

    /**
     * Evaluates whether a stopping drive has met the displacement threshold or was a false trip.
     */
    fun evaluateTripStop(
        isDisplacementConfirmed: Boolean,
        totalDistanceMeters: Double
    ): TripStopDecision {
        return if (isDisplacementConfirmed || totalDistanceMeters >= MIN_DISPLACEMENT_METERS) {
            TripStopDecision.FINALIZE_TRIP
        } else {
            TripStopDecision.DISCARD_FALSE_TRIP
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
        return AutoDriveAction.Ignore
    }

    /**
     * Evaluates 4-minute debounce elapsed timer.
     */
    fun evaluateDebounceElapsed(isInVehicle: Boolean): Boolean {
        // True if trip should finalize; false if vehicle resumed motion
        return !isInVehicle
    }
}
