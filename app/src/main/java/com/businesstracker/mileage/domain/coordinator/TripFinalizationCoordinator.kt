package com.businesstracker.mileage.domain.coordinator

import android.util.Log
import androidx.room.withTransaction
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleState
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.model.TripPurpose
import com.businesstracker.mileage.domain.model.Waypoint
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import com.businesstracker.mileage.domain.usecase.GeocodeLocationUseCase
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-owner, thread-safe, and crash-safe trip finalization coordinator.
 * Authoritative in Room: manages trip lifecycle states, eliminates race conditions,
 * and guarantees exact-ID finalization idempotency without global clear operations.
 */
@Singleton
class TripFinalizationCoordinator @Inject constructor(
    private val database: ExpenseTrackerDatabase,
    private val activeTripRepository: ActiveTripRepository,
    private val tripRepository: TripRepository,
    private val geocodeUseCase: GeocodeLocationUseCase,
    private val rateCalculator: CalculateMileageRateUseCase,
    private val preferencesManager: TrackingPreferencesManager,
    private val gson: Gson,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val finalizationMutex = Mutex()

    companion object {
        private const val METERS_PER_MILE = 1609.344
    }

    suspend fun finalizeTrip(
        tripId: String,
        fallbackTrackingMethod: TrackingMethod = TrackingMethod.AUTO_DETECTED
    ): TripEntity? = withContext(ioDispatcher) {
        finalizationMutex.withLock {
            if (tripId.isEmpty()) return@withLock null

            // 1. Idempotency Check via Room Lifecycle and Trips table
            val lifecycle = database.tripLifecycleDao().getLifecycle(tripId)
            if (lifecycle?.state == TripLifecycleState.FINALIZED.value) {
                return@withLock tripRepository.getTripById(tripId)
            }
            if (lifecycle?.state == TripLifecycleState.DISCARDED.value) {
                return@withLock null
            }

            val existingTrip = tripRepository.getTripById(tripId)
            if (existingTrip != null) {
                database.tripLifecycleDao().insertOrUpdate(
                    TripLifecycleEntity(tripId = tripId, state = TripLifecycleState.FINALIZED.value)
                )
                return@withLock existingTrip
            }

            // 2. Fetch active trip data strictly by exact tripId
            val activeTrip = database.activeTripDao().getActiveTripById(tripId)
            if (activeTrip == null) {
                // Trip is neither active nor finalized; check if discarded
                if (preferencesManager.isTripDiscarded(tripId)) {
                    return@withLock null
                }
                return@withLock tripRepository.getTripById(tripId)
            }

            // 3. Verify exact trip has zero pending GPS fixes
            val pendingGpsCount = database.gpsInboxDao().getPendingCount(tripId)
            if (pendingGpsCount > 0) {
                throw IllegalStateException("Cannot finalize trip $tripId: $pendingGpsCount GPS fixes remain pending in Room inbox")
            }

            // 4. Claim finalization state transition in Room
            database.tripLifecycleDao().insertOrUpdate(
                TripLifecycleEntity(tripId = tripId, state = TripLifecycleState.DRAINING.value)
            )

            // 5. Check false trip threshold using persisted Room data: discard movement < 200m (0.12 miles)
            val totalDistanceMiles = (activeTrip.totalDistanceMeters / METERS_PER_MILE).roundToDecimals(2)
            if (totalDistanceMiles < 0.12 && activeTrip.totalDistanceMeters < 200.0) {
                discardFalseTripInternal(tripId)
                return@withLock null
            }

            val waypointsEntities = database.activeTripDao().getWaypointsForTrip(tripId)
            val waypoints = waypointsEntities.map { entity ->
                Waypoint(
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    altitude = entity.altitude,
                    accuracy = entity.accuracy,
                    speed = entity.speed,
                    timestamp = entity.timestamp
                )
            }

            val startTime = activeTrip.startTime
            val endTime = System.currentTimeMillis()
            val dateString = rateCalculator.getFormattedDate(startTime)

            val startLat = activeTrip.startLatitude
            val startLng = activeTrip.startLongitude
            val endLat = activeTrip.lastLatitude
            val endLng = activeTrip.lastLongitude

            // 6. Geocode endpoints asynchronously
            val startAddress = geocodeUseCase.getFormattedAddress(startLat, startLng)
            val endAddress = geocodeUseCase.getFormattedAddress(endLat, endLng)

            val businessMiles = 0.0
            val applicableRate = rateCalculator.getApplicableRate(dateString)
            val calculatedDeduction = rateCalculator.calculateDeduction(businessMiles, applicableRate)
            val waypointsJson = gson.toJson(waypoints)

            val isPersonalDrive = activeTrip.suggestedBusiness.equals("Personal", ignoreCase = true)
            val businessEntity = if (isPersonalDrive) {
                BusinessEntity.PERSONAL.displayName
            } else {
                activeTrip.suggestedBusiness.ifEmpty {
                    BusinessEntity.MY_SUCCULENT_CORNER.displayName
                }
            }
            val classification = if (isPersonalDrive) {
                TripClassification.PERSONAL.value
            } else {
                TripClassification.UNREVIEWED.value
            }
            val purpose = if (isPersonalDrive) "Personal Drive" else TripPurpose.NURSERY_PICKUP

            val method = if (activeTrip.trackingMethod.isNotEmpty()) {
                activeTrip.trackingMethod
            } else {
                fallbackTrackingMethod.value
            }

            val tripEntity = TripEntity(
                id = activeTrip.id,
                date = dateString,
                startTime = startTime,
                endTime = endTime,
                startAddress = startAddress,
                endAddress = endAddress,
                startLatitude = startLat,
                startLongitude = startLng,
                endLatitude = endLat,
                endLongitude = endLng,
                totalDistanceMiles = totalDistanceMiles,
                businessMiles = businessMiles,
                businessEntity = businessEntity,
                purpose = purpose,
                trackingMethod = method,
                classification = classification,
                applicableRate = applicableRate,
                calculatedDeduction = calculatedDeduction,
                notes = null,
                waypointsJson = waypointsJson,
                syncStatus = "PENDING",
                syncedAt = null
            )

            // 7. Atomic database transaction: persists trip, marks lifecycle FINALIZED,
            // and cleans up ONLY this exact trip's active records
            database.withTransaction {
                database.tripDao().insertTripIgnore(tripEntity)
                database.tripLifecycleDao().insertOrUpdate(
                    TripLifecycleEntity(tripId = tripId, state = TripLifecycleState.FINALIZED.value)
                )
                database.activeTripDao().deleteActiveTripById(tripId)
                database.activeTripDao().deleteWaypointsForTrip(tripId)
                database.gpsInboxDao().deleteFixesForTrip(tripId)
            }

            preferencesManager.clearActiveTripId(activeTrip.id)
            tripEntity
        }
    }

    suspend fun discardFalseTrip(tripId: String) = withContext(ioDispatcher) {
        finalizationMutex.withLock {
            discardFalseTripInternal(tripId)
        }
    }

    private suspend fun discardFalseTripInternal(tripId: String) {
        try {
            database.withTransaction {
                database.tripLifecycleDao().insertOrUpdate(
                    TripLifecycleEntity(tripId = tripId, state = TripLifecycleState.DISCARDED.value, reason = "False trip (<200m)")
                )
                database.activeTripDao().deleteActiveTripById(tripId)
                database.activeTripDao().deleteWaypointsForTrip(tripId)
                database.gpsInboxDao().deleteFixesForTrip(tripId)
            }
            preferencesManager.clearActiveTripId(tripId)
            preferencesManager.recordDiscardedTrip(tripId)
        } catch (e: Exception) {
            Log.e("TripFinalizationCoordinator", "Error discarding false trip: ${e.message}", e)
            throw e
        }
    }

    private fun Double.roundToDecimals(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }
}
