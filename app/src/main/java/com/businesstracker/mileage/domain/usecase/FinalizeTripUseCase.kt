package com.businesstracker.mileage.domain.usecase

import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.model.TripPurpose
import com.businesstracker.mileage.domain.model.Waypoint
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.domain.repository.TripRepository
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FinalizeTripUseCase @Inject constructor(
    private val activeTripRepository: ActiveTripRepository,
    private val tripRepository: TripRepository,
    private val geocodeUseCase: GeocodeLocationUseCase,
    private val rateCalculator: CalculateMileageRateUseCase,
    private val gson: Gson,
    private val ioDispatcher: CoroutineDispatcher
) {

    companion object {
        const val METERS_PER_MILE = 1609.344
    }

    suspend fun execute(tripId: String, trackingMethod: TrackingMethod = TrackingMethod.AUTO_DETECTED): TripEntity? = withContext(ioDispatcher) {
        val activeTrip = activeTripRepository.getActiveTrip() ?: return@withContext null
        val waypoints = activeTripRepository.getWaypoints(tripId)

        val startTime = activeTrip.startTime
        val endTime = System.currentTimeMillis()
        val dateString = rateCalculator.getFormattedDate(startTime)

        val startLat = activeTrip.startLatitude
        val startLng = activeTrip.startLongitude
        val endLat = activeTrip.lastLatitude
        val endLng = activeTrip.lastLongitude

        // Reverse-geocode endpoints asynchronously
        val startAddress = geocodeUseCase.getFormattedAddress(startLat, startLng)
        val endAddress = geocodeUseCase.getFormattedAddress(endLat, endLng)

        // Calculate distances
        val totalDistanceMiles = (activeTrip.totalDistanceMeters / METERS_PER_MILE).roundToDecimals(2)

        // For unreviewed auto-detected trips, default businessMiles to 0 or total until user classifies
        val businessMiles = 0.0
        val applicableRate = rateCalculator.getApplicableRate(dateString)
        val calculatedDeduction = rateCalculator.calculateDeduction(businessMiles, applicableRate)

        // Serialize waypoints JSON
        val waypointsJson = gson.toJson(waypoints)

        // Default business entity
        val businessEntity = activeTrip.suggestedBusiness.ifEmpty {
            BusinessEntity.MY_SUCCULENT_CORNER.displayName
        }

        val tripEntity = TripEntity(
            id = tripId,
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
            purpose = TripPurpose.NURSERY_PICKUP,
            trackingMethod = trackingMethod.value,
            classification = TripClassification.UNREVIEWED.value,
            applicableRate = applicableRate,
            calculatedDeduction = calculatedDeduction,
            notes = null,
            waypointsJson = waypointsJson,
            syncStatus = "PENDING",
            syncedAt = null
        )

        // Persist to Room
        tripRepository.insertTrip(tripEntity)

        // Clear active session
        activeTripRepository.clearActiveTrip(tripId)

        tripEntity
    }

    private fun Double.roundToDecimals(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }
}
