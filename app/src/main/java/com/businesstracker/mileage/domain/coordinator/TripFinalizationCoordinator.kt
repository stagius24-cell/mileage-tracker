package com.businesstracker.mileage.domain.coordinator

import android.util.Log
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.model.TripPurpose
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
 * Used by both LocationTrackingService and TripDebounceWorker to eliminate race conditions
 * and guarantee database idempotency.
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
            // 1. Idempotency Check: if trip already exists in trips table, return it directly
            val existingTrip = tripRepository.getTripById(tripId)
            if (existingTrip != null) {
                try {
                    activeTripRepository.clearActiveTrip(tripId)
                    preferencesManager.clearActiveTripId(tripId)
                } catch (_: Exception) {}
                return@withLock existingTrip
            }

            // 2. Fetch active trip data
            val activeTrip = activeTripRepository.getActiveTrip()
            if (activeTrip == null || (activeTrip.id != tripId && tripId.isNotEmpty())) {
                // Active session not in table; check persisted trips again
                return@withLock tripRepository.getTripById(tripId)
            }

            val waypoints = activeTripRepository.getWaypoints(activeTrip.id)
            val startTime = activeTrip.startTime
            val endTime = System.currentTimeMillis()
            val dateString = rateCalculator.getFormattedDate(startTime)

            val startLat = activeTrip.startLatitude
            val startLng = activeTrip.startLongitude
            val endLat = activeTrip.lastLatitude
            val endLng = activeTrip.lastLongitude

            // 3. Prevent false trips: discard stationary jitter or movements under 200m (0.12 miles)
            val totalDistanceMiles = (activeTrip.totalDistanceMeters / METERS_PER_MILE).roundToDecimals(2)
            if (totalDistanceMiles < 0.12 && activeTrip.totalDistanceMeters < 200.0) {
                activeTripRepository.clearActiveTrip(activeTrip.id)
                preferencesManager.clearActiveTripId(activeTrip.id)
                preferencesManager.recordDiscardedTrip(activeTrip.id)
                return@withLock null
            }

            // 4. Geocode endpoints asynchronously
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

            // 5. Preserve original tracking method from active trip
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

            // 6. Atomic database persistence: inserts trip and clears active trip in single Room transaction
            database.tripDao().persistFinalizedTripAndClearActive(tripEntity)
            preferencesManager.clearActiveTripId(activeTrip.id)

            tripEntity
        }
    }

    suspend fun discardFalseTrip(tripId: String) = withContext(ioDispatcher) {
        finalizationMutex.withLock {
            try {
                activeTripRepository.clearActiveTrip(tripId)
                preferencesManager.clearActiveTripId(tripId)
                preferencesManager.recordDiscardedTrip(tripId)
            } catch (e: Exception) {
                Log.e("TripFinalizationCoordinator", "Error discarding false trip: ${e.message}", e)
            }
        }
    }

    private fun Double.roundToDecimals(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }
}
