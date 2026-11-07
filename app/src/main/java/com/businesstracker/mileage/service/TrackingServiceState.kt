package com.businesstracker.mileage.service

import com.businesstracker.mileage.domain.model.Waypoint

data class TrackingServiceState(
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val tripId: String? = null,
    val distanceMeters: Double = 0.0,
    val distanceMiles: Double = 0.0,
    val durationMillis: Long = 0L,
    val currentSpeedMph: Double = 0.0,
    val lastWaypoint: Waypoint? = null,
    val waypointCount: Int = 0,
    val isRecoveryRequired: Boolean = false,
    val statusMessage: String? = null
)
