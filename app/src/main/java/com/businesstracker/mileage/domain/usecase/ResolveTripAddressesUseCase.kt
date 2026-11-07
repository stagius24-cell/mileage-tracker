package com.businesstracker.mileage.domain.usecase

import com.businesstracker.mileage.data.local.TripDao
import com.businesstracker.mileage.data.local.entity.TripEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import javax.inject.Inject

class ResolveTripAddressesUseCase @Inject constructor(
    private val tripDao: TripDao,
    private val geocoder: GeocodeLocationUseCase
) {
    suspend fun execute(tripId: String): TripEntity? = coroutineScope {
        val trip = tripDao.getTripById(tripId) ?: return@coroutineScope null
        val routeCoordinates = recordedCoordinates(trip.waypointsJson)
        val startCoordinates = usableCoordinates(trip.startLatitude, trip.startLongitude)
            ?: routeCoordinates.firstOrNull()
        val endCoordinates = usableCoordinates(trip.endLatitude, trip.endLongitude)
            ?: routeCoordinates.lastOrNull()
        val start = async {
            if (needsAddress(trip.startAddress) && startCoordinates != null) {
                geocoder.getFormattedAddress(startCoordinates.first, startCoordinates.second)
                    ?.takeIf { !needsAddress(it) }
            } else null
        }
        val end = async {
            if (needsAddress(trip.endAddress) && endCoordinates != null) {
                geocoder.getFormattedAddress(endCoordinates.first, endCoordinates.second)
                    ?.takeIf { !needsAddress(it) }
            } else null
        }
        val startAddress = start.await()
        val endAddress = end.await()
        if (startAddress != null || endAddress != null) {
            tripDao.fillMissingAddresses(trip.id, startAddress, endAddress,
                trip.startLatitude, trip.startLongitude, trip.endLatitude, trip.endLongitude)
        }
        tripDao.getTripById(tripId)
    }

    companion object {
        fun needsAddress(address: String?): Boolean = address.isNullOrBlank() || address == "Unknown Location"

        internal fun recordedCoordinates(waypointsJson: String): List<Pair<Double, Double>> = try {
            val array = JSONArray(waypointsJson)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    usableCoordinates(
                        item.optDouble("latitude", Double.NaN),
                        item.optDouble("longitude", Double.NaN)
                    )?.let(::add)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }

        private fun usableCoordinates(latitude: Double, longitude: Double): Pair<Double, Double>? =
            if (latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
                (latitude != 0.0 || longitude != 0.0)
            ) latitude to longitude else null
    }
}
