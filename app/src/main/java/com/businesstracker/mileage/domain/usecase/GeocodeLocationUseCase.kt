package com.businesstracker.mileage.domain.usecase

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume

class GeocodeLocationUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun getFormattedAddress(latitude: Double, longitude: Double): String? = withContext(ioDispatcher) {
        if (!Geocoder.isPresent()) return@withContext null

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        latitude,
                        longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                continuation.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                continuation.resume(null)
                            }
                        }
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocation(latitude, longitude, 1)
                results?.firstOrNull()
            }

            address?.let { formatAddress(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatAddress(address: Address): String {
        val streetNumber = address.subThoroughfare
        val streetName = address.thoroughfare
        val locality = address.locality ?: address.subAdminArea
        val adminArea = address.adminArea // State

        return when {
            streetNumber != null && streetName != null && locality != null && adminArea != null -> {
                "$streetNumber $streetName, $locality, $adminArea"
            }
            streetName != null && locality != null -> {
                "$streetName, $locality"
            }
            address.maxAddressLineIndex >= 0 -> {
                address.getAddressLine(0)
            }
            else -> {
                locality ?: "Unknown Location"
            }
        }
    }
}
