package com.businesstracker.mileage.domain.model

sealed class FilterResult {
    data class Accepted(
        val waypoint: Waypoint,
        val deltaDistanceMeters: Double,
        val speedMph: Double
    ) : FilterResult()

    data class Rejected(
        val reason: RejectionReason,
        val details: String
    ) : FilterResult()

    enum class RejectionReason {
        POOR_ACCURACY,
        STATIONARY_JITTER,
        IMPOSSIBLE_SPEED_SPIKE
    }
}
