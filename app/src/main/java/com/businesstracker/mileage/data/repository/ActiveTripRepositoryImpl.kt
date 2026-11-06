package com.businesstracker.mileage.data.repository

import com.businesstracker.mileage.data.local.ActiveTripDao
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import com.businesstracker.mileage.domain.model.Waypoint
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveTripRepositoryImpl @Inject constructor(
    private val activeTripDao: ActiveTripDao,
    private val ioDispatcher: CoroutineDispatcher
) : ActiveTripRepository {

    override suspend fun getActiveTrip(): ActiveTripEntity? = withContext(ioDispatcher) {
        activeTripDao.getActiveTrip()
    }

    override fun observeActiveTrip(): Flow<ActiveTripEntity?> =
        activeTripDao.observeActiveTrip().flowOn(ioDispatcher)

    override suspend fun createActiveTrip(
        tripId: String,
        latitude: Double,
        longitude: Double,
        trackingMethod: String,
        suggestedBusiness: String
    ): ActiveTripEntity = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val candidateTrip = ActiveTripEntity(
            id = tripId,
            startTime = now,
            startLatitude = latitude,
            startLongitude = longitude,
            lastLatitude = latitude,
            lastLongitude = longitude,
            lastTimestamp = now,
            totalDistanceMeters = 0.0,
            trackingMethod = trackingMethod,
            isPaused = false,
            pausedDurationMillis = 0L,
            suggestedBusiness = suggestedBusiness
        )

        // Atomic Room @Transaction operation ensures concurrent callers receive the same active trip
        val activeTrip = activeTripDao.getOrCreateActiveTrip(candidateTrip)

        if (activeTrip.id == tripId && (latitude != 0.0 || longitude != 0.0)) {
            activeTripDao.insertWaypoint(
                ActiveWaypointEntity(
                    tripId = tripId,
                    latitude = latitude,
                    longitude = longitude,
                    altitude = 0.0,
                    accuracy = 10f,
                    speed = 0f,
                    timestamp = now
                )
            )
        }
        activeTrip
    }

    override suspend fun recordWaypoint(tripId: String, waypoint: Waypoint): Unit = withContext(ioDispatcher) {
        activeTripDao.insertWaypoint(
            ActiveWaypointEntity(
                tripId = tripId,
                sequenceNumber = waypoint.sequenceNumber,
                elapsedRealtimeNanos = waypoint.elapsedRealtimeNanos,
                latitude = waypoint.latitude,
                longitude = waypoint.longitude,
                altitude = waypoint.altitude,
                accuracy = waypoint.accuracy,
                speed = waypoint.speed,
                timestamp = waypoint.timestamp,
                bootCount = waypoint.bootCount
            )
        )
        Unit
    }

    override suspend fun updateTripProgress(
        tripId: String,
        distanceMeters: Double,
        latitude: Double,
        longitude: Double,
        timestamp: Long
    ) = withContext(ioDispatcher) {
        activeTripDao.updateActiveTripProgress(tripId, distanceMeters, latitude, longitude, timestamp)
    }

    override suspend fun setPaused(tripId: String, isPaused: Boolean) = withContext(ioDispatcher) {
        activeTripDao.setPausedState(tripId, isPaused)
    }

    override suspend fun getWaypoints(tripId: String): List<Waypoint> = withContext(ioDispatcher) {
        activeTripDao.getWaypointsForTrip(tripId).map {
            Waypoint(
                latitude = it.latitude,
                longitude = it.longitude,
                altitude = it.altitude,
                accuracy = it.accuracy,
                speed = it.speed,
                timestamp = it.timestamp,
                sequenceNumber = it.sequenceNumber,
                elapsedRealtimeNanos = it.elapsedRealtimeNanos,
                bootCount = it.bootCount
            )
        }
    }

    override fun observeWaypoints(tripId: String): Flow<List<Waypoint>> =
        activeTripDao.observeWaypointsForTrip(tripId).map { entities ->
            entities.map {
                Waypoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude = it.altitude,
                    accuracy = it.accuracy,
                    speed = it.speed,
                    timestamp = it.timestamp,
                    sequenceNumber = it.sequenceNumber,
                    elapsedRealtimeNanos = it.elapsedRealtimeNanos,
                    bootCount = it.bootCount
                )
            }
        }.flowOn(ioDispatcher)

    override suspend fun clearActiveTrip(tripId: String): Unit = withContext(ioDispatcher) {
        if (tripId.isNotEmpty()) {
            activeTripDao.clearActiveTripById(tripId)
        } else {
            activeTripDao.clearAllActiveData()
        }
    }
}
