package com.businesstracker.mileage.domain.usecase

import com.businesstracker.mileage.data.local.TripDao
import com.businesstracker.mileage.data.local.entity.TripEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class ResolveTripAddressesUseCase @Inject constructor(
    private val tripDao: TripDao,
    private val geocoder: GeocodeLocationUseCase
) {
    suspend fun execute(tripId: String): TripEntity? = coroutineScope {
        val trip = tripDao.getTripById(tripId) ?: return@coroutineScope null
        val start = async {
            if (needsAddress(trip.startAddress)) geocoder.getFormattedAddress(trip.startLatitude, trip.startLongitude)?.takeIf { !needsAddress(it) } else null
        }
        val end = async {
            if (needsAddress(trip.endAddress)) geocoder.getFormattedAddress(trip.endLatitude, trip.endLongitude)?.takeIf { !needsAddress(it) } else null
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
    }
}
