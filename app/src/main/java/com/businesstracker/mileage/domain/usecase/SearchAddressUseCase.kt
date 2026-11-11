package com.businesstracker.mileage.domain.usecase

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class SearchAddressResult(
    val formattedAddress: String,
    val latitude: Double,
    val longitude: Double
)

@Singleton
class SearchAddressUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun search(query: String, maxResults: Int = 5): List<SearchAddressResult> = withContext(ioDispatcher) {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 3) return@withContext emptyList()
        if (!Geocoder.isPresent()) return@withContext emptyList()

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(8_000L) {
                    suspendCancellableCoroutine<List<Address>> { continuation ->
                        val delivered = AtomicBoolean(false)
                        continuation.invokeOnCancellation { delivered.set(true) }
                        geocoder.getFromLocationName(
                            cleanQuery,
                            maxResults,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(results: MutableList<Address>) {
                                    if (delivered.compareAndSet(false, true)) {
                                        continuation.resume(results.toList())
                                    }
                                }

                                override fun onError(errorMessage: String?) {
                                    if (delivered.compareAndSet(false, true)) {
                                        continuation.resume(emptyList())
                                    }
                                }
                            }
                        )
                    }
                } ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocationName(cleanQuery, maxResults)
                results?.toList() ?: emptyList()
            }

            addresses.mapNotNull { address ->
                if (address.hasLatitude() && address.hasLongitude()) {
                    val formatted = formatAddress(address)
                    SearchAddressResult(
                        formattedAddress = formatted,
                        latitude = address.latitude,
                        longitude = address.longitude
                    )
                } else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun formatAddress(address: Address): String {
        val streetNumber = address.subThoroughfare
        val streetName = address.thoroughfare
        val locality = address.locality ?: address.subAdminArea
        val adminArea = address.adminArea

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
                locality ?: "${address.latitude}, ${address.longitude}"
            }
        }
    }
}
