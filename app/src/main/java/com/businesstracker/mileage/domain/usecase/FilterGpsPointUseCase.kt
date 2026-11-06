package com.businesstracker.mileage.domain.usecase

import android.location.Location
import com.businesstracker.mileage.domain.model.FilterResult
import com.businesstracker.mileage.domain.model.Waypoint
import javax.inject.Inject

class FilterGpsPointUseCase @Inject constructor() {

    companion object {
        const val MAX_ACCURACY_THRESHOLD_METERS = 35.0f
        const val MIN_DISPLACEMENT_THRESHOLD_METERS = 15.0 // Prevent stationary drift
        const val MAX_SPEED_MPH = 110.0
        const val MPH_TO_METERS_PER_SECOND = 0.44704
        const val METERS_PER_SECOND_TO_MPH = 2.23694
        const val MAX_SPEED_METERS_PER_SECOND = MAX_SPEED_MPH * MPH_TO_METERS_PER_SECOND // ~49.17 m/s
    }

    /**
     * Evaluates a candidate location against anti-jitter constraints.
     *
     * @param newLocation The incoming Android Location
     * @param lastAcceptedWaypoint The last accepted valid waypoint (null if first point)
     * @return FilterResult indicating Accepted or Rejected with reason
     */
    fun filter(newLocation: Location, lastAcceptedWaypoint: Waypoint?): FilterResult {
        // 1. Reject GPS points with poor accuracy > 35 meters
        if (newLocation.accuracy > MAX_ACCURACY_THRESHOLD_METERS) {
            return FilterResult.Rejected(
                FilterResult.RejectionReason.POOR_ACCURACY,
                "Accuracy ${newLocation.accuracy}m exceeds maximum allowed threshold of ${MAX_ACCURACY_THRESHOLD_METERS}m"
            )
        }

        val candidateWaypoint = Waypoint(
            latitude = newLocation.latitude,
            longitude = newLocation.longitude,
            altitude = newLocation.altitude,
            accuracy = newLocation.accuracy,
            speed = newLocation.speed,
            timestamp = newLocation.time
        )

        // First point is automatically accepted if accuracy is acceptable
        if (lastAcceptedWaypoint == null) {
            return FilterResult.Accepted(
                waypoint = candidateWaypoint,
                deltaDistanceMeters = 0.0,
                speedMph = (newLocation.speed * METERS_PER_SECOND_TO_MPH).toDouble()
            )
        }

        // Calculate delta distance
        val distanceResults = FloatArray(1)
        Location.distanceBetween(
            lastAcceptedWaypoint.latitude,
            lastAcceptedWaypoint.longitude,
            newLocation.latitude,
            newLocation.longitude,
            distanceResults
        )
        val deltaDistance = distanceResults[0].toDouble()

        // 2. Ignore movements < 15 meters to prevent stationary jitter drift
        if (deltaDistance < MIN_DISPLACEMENT_THRESHOLD_METERS) {
            return FilterResult.Rejected(
                FilterResult.RejectionReason.STATIONARY_JITTER,
                "Displacement ${String.format("%.1f", deltaDistance)}m is below minimum stationary threshold (${MIN_DISPLACEMENT_THRESHOLD_METERS}m)"
            )
        }

        // 3. Reject impossible speed spikes (> 110 mph)
        val deltaTimeSeconds = (newLocation.time - lastAcceptedWaypoint.timestamp).coerceAtLeast(1L) / 1000.0
        val calculatedSpeedMps = deltaDistance / deltaTimeSeconds
        val sensorSpeedMps = if (newLocation.hasSpeed()) newLocation.speed.toDouble() else calculatedSpeedMps

        val speedToCheck = maxOf(calculatedSpeedMps, sensorSpeedMps)
        val speedMph = speedToCheck * METERS_PER_SECOND_TO_MPH

        if (speedMph > MAX_SPEED_MPH) {
            return FilterResult.Rejected(
                FilterResult.RejectionReason.IMPOSSIBLE_SPEED_SPIKE,
                "Calculated speed ${String.format("%.1f", speedMph)} mph exceeds maximum allowable threshold of $MAX_SPEED_MPH mph"
            )
        }

        return FilterResult.Accepted(
            waypoint = candidateWaypoint,
            deltaDistanceMeters = deltaDistance,
            speedMph = speedMph
        )
    }
}
