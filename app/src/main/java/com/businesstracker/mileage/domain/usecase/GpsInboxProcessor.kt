package com.businesstracker.mileage.domain.usecase

import android.location.Location
import androidx.room.withTransaction
import com.businesstracker.mileage.data.local.ActiveTripDao
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.GpsInboxDao
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import com.businesstracker.mileage.domain.model.FilterResult
import com.businesstracker.mileage.domain.model.Waypoint
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpsInboxProcessor @Inject constructor(
    private val database: ExpenseTrackerDatabase,
    private val activeTripDao: ActiveTripDao,
    private val gpsInboxDao: GpsInboxDao,
    private val filterGpsPointUseCase: FilterGpsPointUseCase
) {
    sealed class ProcessResult {
        data class Accepted(
            val fixId: Long,
            val tripId: String,
            val deltaDistanceMeters: Double,
            val totalDistanceMeters: Double,
            val lastLatitude: Double,
            val lastLongitude: Double,
            val speedMph: Double,
            val isDisplacementConfirmed: Boolean
        ) : ProcessResult()

        data class Rejected(
            val fixId: Long,
            val tripId: String,
            val reason: String
        ) : ProcessResult()

        data class TripNotFound(val tripId: String) : ProcessResult()
    }

    /**
     * Executes single transactional GPS processing operation.
     * Enforces strict Room withTransaction atomicity:
     * - Reads exact pending fix by ID
     * - Confirms active trip exists and matches trip ID
     * - Checks idempotency: if fix was already recorded as waypoint, acknowledges inbox without double-counting mileage
     * - Obtains latest persisted accepted waypoint from Room (authoritative persisted state)
     * - Runs FilterGpsPointUseCase
     * - If accepted: inserts waypoint, updates active trip distance & coordinates, acknowledges inbox
     * - If rejected: does not alter mileage, acknowledges inbox
     * - Rolls back everything if any operation throws
     */
    suspend fun processFixTransaction(fixId: Long): ProcessResult = database.withTransaction {
        // 1. Read exact pending fix by its database ID
        val fix = gpsInboxDao.getFixById(fixId) ?: return@withTransaction ProcessResult.TripNotFound("")
        val tripId = fix.tripId

        // 2. Confirm corresponding active trip still exists and matches fix's trip ID
        val activeTrip = activeTripDao.getActiveTripById(tripId)
            ?: return@withTransaction ProcessResult.TripNotFound(tripId)

        // Idempotency check: if this fix was already recorded, acknowledge inbox without incrementing mileage
        if (activeTripDao.countWaypointsForFix(tripId, fix.id) > 0) {
            gpsInboxDao.deleteFixById(fix.id)
            return@withTransaction ProcessResult.Accepted(
                fixId = fix.id,
                tripId = tripId,
                deltaDistanceMeters = 0.0,
                totalDistanceMeters = activeTrip.totalDistanceMeters,
                lastLatitude = activeTrip.lastLatitude,
                lastLongitude = activeTrip.lastLongitude,
                speedMph = (fix.speed * FilterGpsPointUseCase.METERS_PER_SECOND_TO_MPH).toDouble(),
                isDisplacementConfirmed = activeTrip.totalDistanceMeters >= 200.0
            )
        }

        // 3. Obtain latest persisted accepted waypoint from Room (source of truth)
        val lastWaypointEntity = activeTripDao.getLastWaypointForTrip(tripId)
        val lastWaypoint = lastWaypointEntity?.let {
            Waypoint(
                latitude = it.latitude,
                longitude = it.longitude,
                altitude = it.altitude,
                accuracy = it.accuracy,
                speed = it.speed,
                timestamp = it.timestamp
            )
        }

        val location = Location("gps").apply {
            latitude = fix.latitude
            longitude = fix.longitude
            altitude = fix.altitude
            accuracy = fix.accuracy
            speed = fix.speed
            time = fix.timestamp
            elapsedRealtimeNanos = fix.elapsedRealtimeNanos
        }

        // 4. Execute existing FilterGpsPointUseCase using correct persisted state
        val filterResult = filterGpsPointUseCase.filter(location, lastWaypoint)

        when (filterResult) {
            is FilterResult.Accepted -> {
                // 5. Insert waypoint with stable source identifier
                val newWaypoint = ActiveWaypointEntity(
                    tripId = tripId,
                    sourceFixId = fix.id,
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    altitude = fix.altitude,
                    accuracy = fix.accuracy,
                    speed = fix.speed,
                    timestamp = fix.timestamp
                )
                activeTripDao.insertWaypoint(newWaypoint)

                // Update active trip accumulated distance, last latitude, longitude, and timestamp
                val newTotalDistance = activeTrip.totalDistanceMeters + filterResult.deltaDistanceMeters
                activeTripDao.updateActiveTripProgress(
                    tripId = tripId,
                    distanceMeters = newTotalDistance,
                    lat = fix.latitude,
                    lng = fix.longitude,
                    timestamp = fix.timestamp
                )

                // 7. Acknowledge/delete exact inbox record in same transaction
                gpsInboxDao.deleteFixById(fix.id)

                val distanceResults = FloatArray(1)
                Location.distanceBetween(
                    activeTrip.startLatitude,
                    activeTrip.startLongitude,
                    fix.latitude,
                    fix.longitude,
                    distanceResults
                )
                val isConfirmed = newTotalDistance >= 200.0 || distanceResults[0] >= 200.0f

                ProcessResult.Accepted(
                    fixId = fix.id,
                    tripId = tripId,
                    deltaDistanceMeters = filterResult.deltaDistanceMeters,
                    totalDistanceMeters = newTotalDistance,
                    lastLatitude = fix.latitude,
                    lastLongitude = fix.longitude,
                    speedMph = filterResult.speedMph,
                    isDisplacementConfirmed = isConfirmed
                )
            }
            is FilterResult.Rejected -> {
                // 6. Do not alter mileage or insert accepted waypoint
                // 7. Acknowledge/delete exact inbox record transactionally
                gpsInboxDao.deleteFixById(fix.id)

                ProcessResult.Rejected(
                    fixId = fix.id,
                    tripId = tripId,
                    reason = "${filterResult.reason}: ${filterResult.details}"
                )
            }
        }
    }
}
