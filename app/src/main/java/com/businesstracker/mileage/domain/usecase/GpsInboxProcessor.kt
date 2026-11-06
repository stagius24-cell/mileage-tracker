package com.businesstracker.mileage.domain.usecase

import android.location.Location
import androidx.room.withTransaction
import com.businesstracker.mileage.data.local.ActiveTripDao
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.GpsInboxDao
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleState
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
            val isDisplacementConfirmed: Boolean,
            val waypoint: Waypoint? = null
        ) : ProcessResult()

        data class AlreadyConsumed(
            val fixId: Long,
            val tripId: String,
            val totalDistanceMeters: Double,
            val lastLatitude: Double,
            val lastLongitude: Double,
            val isDisplacementConfirmed: Boolean,
            val waypoint: Waypoint? = null
        ) : ProcessResult()

        data class Rejected(
            val fixId: Long,
            val tripId: String,
            val reason: String
        ) : ProcessResult()

        data class StaleOrphanAcknowledged(
            val fixId: Long,
            val tripId: String,
            val reason: String
        ) : ProcessResult()

        data class UnresolvedOrphan(
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
     * - Distinguishes terminal orphans (acknowledged) from unresolved orphans (preserved, halts drain)
     * - Checks idempotency: if fix was already recorded as waypoint, acknowledges inbox without double-counting mileage
     * - Obtains latest persisted accepted waypoint from Room (authoritative persisted state)
     * - Runs FilterGpsPointUseCase with monotonic timing and boot session detection
     * - If accepted: inserts waypoint with sequenceNumber and elapsedRealtimeNanos, updates active trip distance & coordinates, acknowledges inbox
     * - If rejected: does not alter mileage, acknowledges inbox
     * - Rolls back everything if any operation throws
     */
    suspend fun processFixTransaction(fixId: Long): ProcessResult = database.withTransaction {
        // 1. Read exact pending fix by its database ID
        val fix = gpsInboxDao.getFixById(fixId) ?: return@withTransaction ProcessResult.TripNotFound("")
        val tripId = fix.tripId

        // 2. Confirm corresponding active trip still exists and matches fix's trip ID
        val activeTrip = activeTripDao.getActiveTripById(tripId)
        if (activeTrip == null) {
            val lifecycle = database.tripLifecycleDao().getLifecycle(tripId)
            val isTerminal = lifecycle?.state == TripLifecycleState.FINALIZED.value ||
                    lifecycle?.state == TripLifecycleState.DISCARDED.value ||
                    database.tripDao().getTripById(tripId) != null

            if (isTerminal) {
                // Trip is confirmed finalized or discarded: safe to delete stale orphan from inbox
                gpsInboxDao.deleteFixById(fix.id)
                return@withTransaction ProcessResult.StaleOrphanAcknowledged(
                    fixId = fix.id,
                    tripId = tripId,
                    reason = "Trip confirmed terminal (${lifecycle?.state ?: "FINALIZED in trips table"})"
                )
            } else {
                // Active trip missing without terminal state: preserve fix, do NOT delete, halt drain
                return@withTransaction ProcessResult.UnresolvedOrphan(
                    fixId = fix.id,
                    tripId = tripId,
                    reason = "Active trip missing without terminal lifecycle state (state=${lifecycle?.state})"
                )
            }
        }

        // Idempotency check: if this fix was already recorded, acknowledge inbox without incrementing mileage
        if (activeTripDao.countWaypointsForFix(tripId, fix.id) > 0) {
            gpsInboxDao.deleteFixById(fix.id)
            val lastWaypointEntity = activeTripDao.getLastWaypointForTrip(tripId)
            val lastWaypoint = lastWaypointEntity?.let {
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
            return@withTransaction ProcessResult.AlreadyConsumed(
                fixId = fix.id,
                tripId = tripId,
                totalDistanceMeters = activeTrip.totalDistanceMeters,
                lastLatitude = activeTrip.lastLatitude,
                lastLongitude = activeTrip.lastLongitude,
                isDisplacementConfirmed = activeTrip.totalDistanceMeters >= 200.0,
                waypoint = lastWaypoint
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
                timestamp = it.timestamp,
                sequenceNumber = it.sequenceNumber,
                elapsedRealtimeNanos = it.elapsedRealtimeNanos,
                bootCount = it.bootCount
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
        val filterResult = filterGpsPointUseCase.filter(location, lastWaypoint, currentBootCount = fix.bootCount)

        when (filterResult) {
            is FilterResult.Accepted -> {
                // 5. Insert waypoint with sequenceNumber, elapsedRealtimeNanos, and stable source identifier
                val newWaypoint = ActiveWaypointEntity(
                    tripId = tripId,
                    sourceFixId = fix.id,
                    sequenceNumber = fix.sequenceNumber,
                    elapsedRealtimeNanos = fix.elapsedRealtimeNanos,
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    altitude = fix.altitude,
                    accuracy = fix.accuracy,
                    speed = fix.speed,
                    timestamp = fix.timestamp,
                    bootCount = fix.bootCount
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

                val anchorLat: Double
                val anchorLng: Double
                if (activeTrip.startLatitude != 0.0 || activeTrip.startLongitude != 0.0) {
                    anchorLat = activeTrip.startLatitude
                    anchorLng = activeTrip.startLongitude
                } else {
                    val firstWp = activeTripDao.getWaypointsForTrip(tripId).firstOrNull()
                    if (firstWp != null) {
                        anchorLat = firstWp.latitude
                        anchorLng = firstWp.longitude
                    } else {
                        anchorLat = fix.latitude
                        anchorLng = fix.longitude
                    }
                }

                val distanceResults = FloatArray(1)
                Location.distanceBetween(
                    anchorLat,
                    anchorLng,
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
                    isDisplacementConfirmed = isConfirmed,
                    waypoint = filterResult.waypoint
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
