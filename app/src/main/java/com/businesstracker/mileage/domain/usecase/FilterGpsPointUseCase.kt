package com.businesstracker.mileage.domain.usecase

import android.location.Location
import com.businesstracker.mileage.domain.model.FilterResult
import com.businesstracker.mileage.domain.model.Waypoint
import com.businesstracker.mileage.domain.service.BootCountProvider
import javax.inject.Inject

class FilterGpsPointUseCase @Inject constructor(
    private val bootCountProvider: BootCountProvider
) {
    constructor() : this(object : BootCountProvider { override fun getBootCount(): Int = 0 })

    companion object {
        const val MAX_ACCURACY_THRESHOLD_METERS = 35.0f
        const val MIN_DISPLACEMENT_THRESHOLD_METERS = 15.0 // Prevent stationary drift
        const val MAX_SPEED_MPH = 110.0
        const val MPH_TO_METERS_PER_SECOND = 0.44704
        const val METERS_PER_SECOND_TO_MPH = 2.23694
        const val MAX_SPEED_METERS_PER_SECOND = MAX_SPEED_MPH * MPH_TO_METERS_PER_SECOND // ~49.17 m/s
    }

    /**
     * Evaluates a candidate location against anti-jitter and physical feasibility constraints.
     * Uses monotonic elapsed time within the same boot session to remain immune to wall-clock or NTP jumps.
     * Detects boot session changes (reboots) reliably via BootCountProvider (Settings.Global.BOOT_COUNT)
     * and safely re-baselines using sensor Doppler speed without calculating cross-boot elapsed time deltas.
     *
     * @param newLocation The incoming Android Location
     * @param lastAcceptedWaypoint The last accepted valid waypoint (null if first point)
     * @param currentBootCount Optional explicit boot count override (defaults to BootCountProvider or 0)
     * @return FilterResult indicating Accepted or Rejected with reason
     */
    fun filter(
        newLocation: Location,
        lastAcceptedWaypoint: Waypoint?,
        currentBootCount: Int? = null
    ): FilterResult {
        // 1. Reject GPS points with poor accuracy > 35 meters
        if (newLocation.accuracy > MAX_ACCURACY_THRESHOLD_METERS) {
            return FilterResult.Rejected(
                FilterResult.RejectionReason.POOR_ACCURACY,
                "Accuracy ${newLocation.accuracy}m exceeds maximum allowed threshold of ${MAX_ACCURACY_THRESHOLD_METERS}m"
            )
        }

        val effectiveBootCount = currentBootCount ?: bootCountProvider.getBootCount()

        val candidateWaypoint = Waypoint(
            latitude = newLocation.latitude,
            longitude = newLocation.longitude,
            altitude = newLocation.altitude,
            accuracy = newLocation.accuracy,
            speed = newLocation.speed,
            timestamp = newLocation.time,
            sequenceNumber = lastAcceptedWaypoint?.sequenceNumber?.plus(1L) ?: 0L,
            elapsedRealtimeNanos = newLocation.elapsedRealtimeNanos,
            bootCount = effectiveBootCount
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

        // 3. Monotonic timing & reboot detection:
        // Case A: Both points have valid, known boot identifiers (> 0).
        // If effectiveBootCount != lastAcceptedWaypoint.bootCount, a device reboot occurred.
        // We must NEVER calculate an elapsed time delta across distinct boot sessions, even if
        // the new boot elapsedRealtimeNanos happens to be greater than the old one (e.g., 60h vs 50h).
        //
        // Case B: Either point lacks a valid boot identifier (<= 0), e.g. legacy persisted records or unit tests.
        // Fall back to detecting monotonic counter reset: new.elapsedRealtimeNanos < last.elapsedRealtimeNanos.
        val hasKnownBoots = effectiveBootCount > 0 && lastAcceptedWaypoint.bootCount > 0
        val isBootMismatch = hasKnownBoots && (effectiveBootCount != lastAcceptedWaypoint.bootCount)

        val hasMonotonicBoth = newLocation.elapsedRealtimeNanos > 0L && lastAcceptedWaypoint.elapsedRealtimeNanos > 0L
        val isMonotonicReset = hasMonotonicBoth && (newLocation.elapsedRealtimeNanos < lastAcceptedWaypoint.elapsedRealtimeNanos)

        val isReboot = isBootMismatch || (!hasKnownBoots && isMonotonicReset)

        val deltaTimeSeconds: Double? = when {
            isReboot -> {
                // Device reboot detected: reset baseline and do not compute cross-boot delta
                null
            }
            hasMonotonicBoth && newLocation.elapsedRealtimeNanos >= lastAcceptedWaypoint.elapsedRealtimeNanos -> {
                val deltaNanos = newLocation.elapsedRealtimeNanos - lastAcceptedWaypoint.elapsedRealtimeNanos
                (deltaNanos / 1_000_000_000.0).coerceAtLeast(0.001)
            }
            else -> {
                // Fallback for tests or environments without elapsedRealtimeNanos: use wall-clock delta if positive
                val wallDeltaMs = newLocation.time - lastAcceptedWaypoint.timestamp
                if (wallDeltaMs > 0L) wallDeltaMs / 1000.0 else null
            }
        }

        val calculatedSpeedMps = if (deltaTimeSeconds != null) {
            deltaDistance / deltaTimeSeconds
        } else null

        val sensorSpeedMps = if (newLocation.hasSpeed()) newLocation.speed.toDouble() else null

        val speedToCheck = if (calculatedSpeedMps != null && sensorSpeedMps != null) {
            maxOf(calculatedSpeedMps, sensorSpeedMps)
        } else {
            calculatedSpeedMps ?: sensorSpeedMps ?: (deltaDistance / 5.0)
        }
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
