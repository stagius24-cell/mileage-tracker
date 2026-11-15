package com.businesstracker.mileage.domain.matcher

import com.businesstracker.mileage.data.local.RoutePatternDao
import com.businesstracker.mileage.data.local.entity.RoutePatternEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class TripPatternMatcher @Inject constructor(
    private val routePatternDao: RoutePatternDao,
    private val ioDispatcher: CoroutineDispatcher
) {

    companion object {
        const val MATCH_RADIUS_METERS: Double = 200.0
        const val DISTANCE_CONSISTENCY_TOLERANCE_PERCENT: Double = 25.0
        const val METERS_PER_MILE: Double = 1609.344

        // 1 degree latitude ≈ 111,000 meters
        const val METERS_PER_DEGREE_LATITUDE: Double = 111000.0
        const val BOUNDING_BOX_MARGIN_METERS: Double = 300.0

        fun haversineDistanceMeters(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Double {
            val earthRadius = 6371000.0 // meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return earthRadius * c
        }

        fun calculateInitialBearing(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Double {
            val lat1Rad = Math.toRadians(lat1)
            val lat2Rad = Math.toRadians(lat2)
            val dLonRad = Math.toRadians(lon2 - lon1)
            val y = sin(dLonRad) * cos(lat2Rad)
            val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLonRad)
            val bearingRad = atan2(y, x)
            return (Math.toDegrees(bearingRad) + 360.0) % 360.0
        }

        fun angleDifference(angle1: Double, angle2: Double): Double {
            val diff = abs(angle1 - angle2) % 360.0
            return if (diff > 180.0) 360.0 - diff else diff
        }
    }

    /**
     * Matches a finalized or candidate TripEntity against stored route patterns.
     */
    suspend fun matchTrip(trip: TripEntity): RouteMatchResult = withContext(ioDispatcher) {
        val validationError = validateCoordinates(
            trip.startLatitude, trip.startLongitude,
            trip.endLatitude, trip.endLongitude,
            trip.totalDistanceMiles
        )
        if (validationError != null) {
            return@withContext RouteMatchResult.InvalidCoordinates(validationError)
        }

        // Query candidate patterns using indexed bounding box query for performance
        val candidates = queryCandidatePatterns(
            trip.startLatitude, trip.startLongitude,
            trip.endLatitude, trip.endLongitude
        )

        matchAgainstPatterns(
            startLat = trip.startLatitude,
            startLon = trip.startLongitude,
            endLat = trip.endLatitude,
            endLon = trip.endLongitude,
            totalDistanceMiles = trip.totalDistanceMiles,
            startTimeMillis = trip.startTime,
            patterns = candidates
        )
    }

    /**
     * Pure matching engine evaluated against a provided list of route patterns.
     * Thread-safe, deterministic, and offline.
     */
    fun matchAgainstPatterns(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        totalDistanceMiles: Double,
        startTimeMillis: Long,
        patterns: List<RoutePatternEntity>
    ): RouteMatchResult {
        val validationError = validateCoordinates(startLat, startLon, endLat, endLon, totalDistanceMiles)
        if (validationError != null) {
            return RouteMatchResult.InvalidCoordinates(validationError)
        }

        if (patterns.isEmpty()) {
            return RouteMatchResult.NoMatch("No route patterns stored in database.")
        }

        val tripDisplacementMeters = haversineDistanceMeters(startLat, startLon, endLat, endLon)
        val tripBearing = calculateInitialBearing(startLat, startLon, endLat, endLon)

        data class CandidateMatch(
            val pattern: RoutePatternEntity,
            val startDistanceMeters: Double,
            val endDistanceMeters: Double,
            val totalEndpointError: Double,
            val distanceDifferencePercent: Double,
            val isDistanceConsistent: Boolean,
            val timeSimilarityScore: Double,
            val isSameDayOfWeek: Boolean,
            val geographicSimilarityScore: Double
        )

        val qualifyingMatches = mutableListOf<CandidateMatch>()

        for (pattern in patterns) {
            val startDist = haversineDistanceMeters(startLat, startLon, pattern.startLatitude, pattern.startLongitude)
            val endDist = haversineDistanceMeters(endLat, endLon, pattern.endLatitude, pattern.endLongitude)

            // Primary Requirement: Both start and end must be within 200 meters
            if (startDist > MATCH_RADIUS_METERS || endDist > MATCH_RADIUS_METERS) {
                continue
            }

            // Direction of travel check: Trip start must match pattern start, not pattern end
            val patternDisplacementMeters = haversineDistanceMeters(
                pattern.startLatitude, pattern.startLongitude,
                pattern.endLatitude, pattern.endLongitude
            )
            val patternBearing = calculateInitialBearing(
                pattern.startLatitude, pattern.startLongitude,
                pattern.endLatitude, pattern.endLongitude
            )

            // If the route has significant length (> 500m), travel direction must align within 60 degrees
            if (tripDisplacementMeters > 500.0 && patternDisplacementMeters > 500.0) {
                val bearingDiff = angleDifference(tripBearing, patternBearing)
                if (bearingDiff > 60.0) {
                    continue // Wrong direction of travel (e.g. reversed route)
                }
            }

            // Secondary signal: Distance consistency (within approximately ±25%)
            val patternExpectedMiles = patternDisplacementMeters / METERS_PER_MILE
            val distanceDifferencePercent = if (patternExpectedMiles > 0.0) {
                (abs(totalDistanceMiles - patternExpectedMiles) / patternExpectedMiles) * 100.0
            } else {
                0.0
            }
            val isDistanceConsistent = distanceDifferencePercent <= DISTANCE_CONSISTENCY_TOLERANCE_PERCENT

            // Secondary signal: Time of Day and Day of Week
            val (timeSimilarity, sameDayOfWeek) = calculateTemporalSignals(startTimeMillis, pattern.lastLearnedAt)

            // Geographic similarity score (0.0 to 1.0)
            val startScore = max(0.0, 1.0 - (startDist / MATCH_RADIUS_METERS))
            val endScore = max(0.0, 1.0 - (endDist / MATCH_RADIUS_METERS))
            val geoScore = (startScore + endScore) / 2.0

            qualifyingMatches.add(
                CandidateMatch(
                    pattern = pattern,
                    startDistanceMeters = startDist,
                    endDistanceMeters = endDist,
                    totalEndpointError = startDist + endDist,
                    distanceDifferencePercent = distanceDifferencePercent,
                    isDistanceConsistent = isDistanceConsistent,
                    timeSimilarityScore = timeSimilarity,
                    isSameDayOfWeek = sameDayOfWeek,
                    geographicSimilarityScore = geoScore
                )
            )
        }

        if (qualifyingMatches.isEmpty()) {
            return RouteMatchResult.NoMatch("No route pattern within 200 meters of start and end coordinates.")
        }

        if (qualifyingMatches.size == 1) {
            val match = qualifyingMatches[0]
            return RouteMatchResult.ReliableMatch(
                pattern = match.pattern,
                startDistanceMeters = match.startDistanceMeters,
                endDistanceMeters = match.endDistanceMeters,
                distanceDifferencePercent = match.distanceDifferencePercent,
                isDistanceConsistent = match.isDistanceConsistent,
                timeSimilarityScore = match.timeSimilarityScore,
                isSameDayOfWeek = match.isSameDayOfWeek,
                geographicSimilarityScore = match.geographicSimilarityScore
            )
        }

        // Multiple candidate patterns qualify: Check for Ambiguity
        // Sort by total endpoint error ascending (closest first)
        qualifyingMatches.sortBy { it.totalEndpointError }

        val first = qualifyingMatches[0]
        val second = qualifyingMatches[1]

        // Case 1: Conflicting classifications or business entities
        val hasConflictingClassification = qualifyingMatches.any {
            !it.pattern.classification.equals(first.pattern.classification, ignoreCase = true)
        }
        val hasDifferentEntityOrPurpose = qualifyingMatches.any {
            !it.pattern.businessEntity.equals(first.pattern.businessEntity, ignoreCase = true) ||
            !it.pattern.purpose.equals(first.pattern.purpose, ignoreCase = true)
        }

        if (hasConflictingClassification || hasDifferentEntityOrPurpose) {
            return RouteMatchResult.AmbiguousMatch(
                candidatePatterns = qualifyingMatches.map { it.pattern },
                reason = "Multiple overlapping patterns with conflicting classification or business purpose."
            )
        }

        // Case 2: Patterns represent similar entities, but endpoint error difference is too small (< 50m)
        val errorDifference = abs(second.totalEndpointError - first.totalEndpointError)
        if (errorDifference < 50.0) {
            return RouteMatchResult.AmbiguousMatch(
                candidatePatterns = qualifyingMatches.map { it.pattern },
                reason = "Multiple plausible patterns within similar proximity (< 50m difference)."
            )
        }

        // Clear separation (> 50m closer) and identical classification/entity/purpose -> return the distinctly closer one
        return RouteMatchResult.ReliableMatch(
            pattern = first.pattern,
            startDistanceMeters = first.startDistanceMeters,
            endDistanceMeters = first.endDistanceMeters,
            distanceDifferencePercent = first.distanceDifferencePercent,
            isDistanceConsistent = first.isDistanceConsistent,
            timeSimilarityScore = first.timeSimilarityScore,
            isSameDayOfWeek = first.isSameDayOfWeek,
            geographicSimilarityScore = first.geographicSimilarityScore
        )
    }

    private fun validateCoordinates(
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double,
        distanceMiles: Double
    ): String? {
        if (startLat.isNaN() || startLat.isInfinite() || startLon.isNaN() || startLon.isInfinite()) {
            return "Starting coordinates must be finite valid numbers"
        }
        if (endLat.isNaN() || endLat.isInfinite() || endLon.isNaN() || endLon.isInfinite()) {
            return "Ending coordinates must be finite valid numbers"
        }
        if (startLat == 0.0 && startLon == 0.0) {
            return "Starting coordinates cannot be 0.0, 0.0"
        }
        if (endLat == 0.0 && endLon == 0.0) {
            return "Ending coordinates cannot be 0.0, 0.0"
        }
        if (distanceMiles.isNaN() || distanceMiles.isInfinite() || distanceMiles < 0.0) {
            return "Trip distance must be a valid non-negative number"
        }

        // Follow the app's existing trip finalization threshold (TripFinalizationCoordinator):
        // Trips with under 200 meters of total distance traveled are discarded as false trips.
        val totalDistanceMeters = distanceMiles * METERS_PER_MILE
        if (totalDistanceMeters < 200.0) {
            return "Trip total distance (${totalDistanceMeters.toInt()}m) is under the application 200m validity threshold"
        }

        return null
    }

    private suspend fun queryCandidatePatterns(
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double
    ): List<RoutePatternEntity> {
        // Calculate degree deltas for 300m bounding box
        val latDelta = BOUNDING_BOX_MARGIN_METERS / METERS_PER_DEGREE_LATITUDE
        val startLonDelta = BOUNDING_BOX_MARGIN_METERS / (METERS_PER_DEGREE_LATITUDE * max(0.1, cos(Math.toRadians(startLat))))
        val endLonDelta = BOUNDING_BOX_MARGIN_METERS / (METERS_PER_DEGREE_LATITUDE * max(0.1, cos(Math.toRadians(endLat))))

        return routePatternDao.findCandidatePatterns(
            minStartLat = startLat - latDelta,
            maxStartLat = startLat + latDelta,
            minStartLon = startLon - startLonDelta,
            maxStartLon = startLon + startLonDelta,
            minEndLat = endLat - latDelta,
            maxEndLat = endLat + latDelta,
            minEndLon = endLon - endLonDelta,
            maxEndLon = endLon + endLonDelta
        )
    }

    private fun calculateTemporalSignals(
        tripTimeMillis: Long,
        patternTimeMillis: Long
    ): Pair<Double, Boolean> {
        val zone = ZoneId.systemDefault()
        val tripZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(tripTimeMillis), zone)
        val patternZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(patternTimeMillis), zone)

        // Day of week match
        val sameDay = tripZdt.dayOfWeek == patternZdt.dayOfWeek

        // Time of day circular difference in minutes (0 to 720 minutes)
        val tripMinuteOfDay = tripZdt.hour * 60 + tripZdt.minute
        val patternMinuteOfDay = patternZdt.hour * 60 + patternZdt.minute
        val diffMinutes = abs(tripMinuteOfDay - patternMinuteOfDay)
        val circularDiffMinutes = min(diffMinutes, 1440 - diffMinutes)

        // Score: 1.0 at identical time, dropping to 0.0 at 12 hours difference
        val timeScore = max(0.0, 1.0 - (circularDiffMinutes / 720.0))

        return Pair(timeScore, sameDay)
    }
}
