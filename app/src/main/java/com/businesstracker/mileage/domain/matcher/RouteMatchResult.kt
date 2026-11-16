package com.businesstracker.mileage.domain.matcher

import com.businesstracker.mileage.data.local.entity.RoutePatternEntity

sealed class RouteMatchResult {

    /**
     * Unique, reliable route match meeting all geographic and directional constraints.
     */
    data class ReliableMatch(
        val pattern: RoutePatternEntity,
        val startDistanceMeters: Double,
        val endDistanceMeters: Double,
        val distanceDifferencePercent: Double,
        val isDistanceConsistent: Boolean,
        val timeSimilarityScore: Double,
        val isSameDayOfWeek: Boolean,
        val geographicSimilarityScore: Double
    ) : RouteMatchResult() {
        val patternId: String get() = pattern.id
    }

    /**
     * Multiple plausible patterns match the route endpoints within tolerance,
     * but they conflict in classification, purpose, or cannot be reliably distinguished.
     */
    data class AmbiguousMatch(
        val candidatePatterns: List<RoutePatternEntity>,
        val reason: String
    ) : RouteMatchResult()

    /**
     * No route pattern matched the trip endpoints within tolerance.
     */
    data class NoMatch(
        val reason: String
    ) : RouteMatchResult()

    /**
     * Trip coordinates are invalid, non-finite, missing, or represent a sub-threshold trip.
     */
    data class InvalidCoordinates(
        val reason: String
    ) : RouteMatchResult()
}
