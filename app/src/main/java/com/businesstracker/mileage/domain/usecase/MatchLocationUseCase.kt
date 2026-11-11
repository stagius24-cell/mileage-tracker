package com.businesstracker.mileage.domain.usecase

import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class MatchResult(
    val location: SavedLocationEntity,
    val distanceMeters: Double
)

@Singleton
class MatchLocationUseCase @Inject constructor() {

    companion object {
        private const val EARTH_RADIUS_METERS = 6371000.0
    }

    /**
     * Finds the closest saved location within its configured radius.
     * Returns null if coordinates are invalid or no saved location is within its radius.
     */
    fun findMatch(
        latitude: Double,
        longitude: Double,
        savedLocations: List<SavedLocationEntity>
    ): MatchResult? {
        if (!isValidCoordinate(latitude, longitude) || savedLocations.isEmpty()) {
            return null
        }

        var closestMatch: MatchResult? = null

        for (loc in savedLocations) {
            if (!isValidCoordinate(loc.latitude, loc.longitude)) continue

            val distance = calculateDistanceMeters(
                latitude, longitude,
                loc.latitude, loc.longitude
            )

            val effectiveRadius = loc.radiusMeters.coerceIn(50, 500)
            if (distance <= effectiveRadius) {
                if (closestMatch == null || distance < closestMatch.distanceMeters ||
                    (distance == closestMatch.distanceMeters && loc.updatedAt > closestMatch.location.updatedAt)
                ) {
                    closestMatch = MatchResult(loc, distance)
                }
            }
        }

        return closestMatch
    }

    fun calculateDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    private fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        return lat.isFinite() && lng.isFinite() &&
                lat in -90.0..90.0 && lng in -180.0..180.0 &&
                !(lat == 0.0 && lng == 0.0)
    }
}
