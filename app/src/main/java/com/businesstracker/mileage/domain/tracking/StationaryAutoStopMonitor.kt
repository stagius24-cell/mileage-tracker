package com.businesstracker.mileage.domain.tracking

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GPS fallback for devices that do not reliably deliver IN_VEHICLE EXIT transitions.
 * A confirmed automatic drive is eligible to stop after ten continuous minutes without
 * meaningful speed or displacement. Activity-transition and Bluetooth debounce behavior
 * remains independent and unchanged.
 */
class StationaryAutoStopMonitor(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val movementRadiusMeters: Double = DEFAULT_MOVEMENT_RADIUS_METERS,
    private val movingSpeedMetersPerSecond: Float = DEFAULT_MOVING_SPEED_METERS_PER_SECOND,
    private val maximumAccuracyMeters: Float = DEFAULT_MAXIMUM_ACCURACY_METERS
) {
    data class Snapshot(val latitude: Double, val longitude: Double, val elapsedMillis: Long)
    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10L * 60L * 1000L
        const val DEFAULT_MOVEMENT_RADIUS_METERS = 60.0
        const val DEFAULT_MOVING_SPEED_METERS_PER_SECOND = 2.5f
        const val DEFAULT_MAXIMUM_ACCURACY_METERS = 35f
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }

    private var anchorLatitude: Double? = null
    private var anchorLongitude: Double? = null
    private var lastMovementElapsedMillis: Long? = null
    private var stopRequested = false
    private var hasStationaryEvidence = false

    @Synchronized fun reset() {
        anchorLatitude = null
        anchorLongitude = null
        lastMovementElapsedMillis = null
        stopRequested = false
        hasStationaryEvidence = false
    }

    @Synchronized fun snapshot(): Snapshot? {
        if (!hasStationaryEvidence) return null
        val latitude = anchorLatitude ?: return null
        val longitude = anchorLongitude ?: return null
        val elapsed = lastMovementElapsedMillis ?: return null
        return Snapshot(latitude, longitude, elapsed)
    }

    @Synchronized fun restore(snapshot: Snapshot, currentElapsedMillis: Long): Boolean {
        if (snapshot.elapsedMillis < 0 || snapshot.elapsedMillis > currentElapsedMillis ||
            !snapshot.latitude.isFinite() || !snapshot.longitude.isFinite() ||
            snapshot.latitude !in -90.0..90.0 || snapshot.longitude !in -180.0..180.0
        ) return false
        anchorLatitude = snapshot.latitude
        anchorLongitude = snapshot.longitude
        lastMovementElapsedMillis = snapshot.elapsedMillis
        stopRequested = false
        hasStationaryEvidence = true
        return true
    }

    @Synchronized fun record(
        latitude: Double,
        longitude: Double,
        speedMetersPerSecond: Float,
        accuracyMeters: Float,
        elapsedRealtimeMillis: Long
    ): Boolean {
        if (stopRequested || elapsedRealtimeMillis < 0L ||
            !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0 ||
            !accuracyMeters.isFinite() || accuracyMeters < 0f ||
            !speedMetersPerSecond.isFinite() || speedMetersPerSecond < 0f ||
            accuracyMeters > maximumAccuracyMeters) {
            return false
        }

        val previousMovementTime = lastMovementElapsedMillis
        val anchorLat = anchorLatitude
        val anchorLon = anchorLongitude
        if (previousMovementTime == null || anchorLat == null || anchorLon == null) {
            setAnchor(latitude, longitude, elapsedRealtimeMillis)
            return false
        }

        if (elapsedRealtimeMillis < previousMovementTime) {
            return false
        }

        val displacementMeters = distanceMeters(anchorLat, anchorLon, latitude, longitude)
        if (speedMetersPerSecond >= movingSpeedMetersPerSecond || displacementMeters >= movementRadiusMeters) {
            setAnchor(latitude, longitude, elapsedRealtimeMillis)
            hasStationaryEvidence = false
            return false
        }

        hasStationaryEvidence = true

        if (elapsedRealtimeMillis - previousMovementTime >= timeoutMillis) {
            stopRequested = true
            return true
        }
        return false
    }

    private fun setAnchor(latitude: Double, longitude: Double, elapsedRealtimeMillis: Long) {
        anchorLatitude = latitude
        anchorLongitude = longitude
        lastMovementElapsedMillis = elapsedRealtimeMillis
    }

    private fun distanceMeters(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double
    ): Double {
        val latitudeDelta = Math.toRadians(endLatitude - startLatitude)
        val longitudeDelta = Math.toRadians(endLongitude - startLongitude)
        val startLatRadians = Math.toRadians(startLatitude)
        val endLatRadians = Math.toRadians(endLatitude)
        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(startLatRadians) * cos(endLatRadians) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
