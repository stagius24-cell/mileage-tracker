package com.businesstracker.mileage.domain.repository

import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.domain.model.Waypoint
import kotlinx.coroutines.flow.Flow

interface ActiveTripRepository {
    suspend fun getActiveTrip(): ActiveTripEntity?
    fun observeActiveTrip(): Flow<ActiveTripEntity?>
    suspend fun createActiveTrip(
        tripId: String,
        latitude: Double,
        longitude: Double,
        trackingMethod: String,
        suggestedBusiness: String
    ): ActiveTripEntity
    suspend fun recordWaypoint(tripId: String, waypoint: Waypoint)
    suspend fun updateTripProgress(
        tripId: String,
        distanceMeters: Double,
        latitude: Double,
        longitude: Double,
        timestamp: Long
    )
    suspend fun setPaused(tripId: String, isPaused: Boolean)
    suspend fun getWaypoints(tripId: String): List<Waypoint>
    fun observeWaypoints(tripId: String): Flow<List<Waypoint>>
    suspend fun clearActiveTrip(tripId: String)
}
