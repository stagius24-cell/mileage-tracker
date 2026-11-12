package com.businesstracker.mileage.domain.usecase

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class CurrentLocationResult(
    val latitude: Double,
    val longitude: Double,
    val formattedAddress: String?
)

@Singleton
class GetCurrentLocationUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val geocodeLocationUseCase: GeocodeLocationUseCase,
    private val ioDispatcher: CoroutineDispatcher
) {

    suspend operator fun invoke(): CurrentLocationResult? = getCurrentLocation()

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): CurrentLocationResult? = withContext(ioDispatcher) {
        val cts = CancellationTokenSource()
        try {
            val location: Location? = withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation { cts.cancel() }
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cts.token
                    ).addOnSuccessListener { loc ->
                        continuation.resume(loc)
                    }.addOnFailureListener {
                        continuation.resume(null)
                    }
                }
            } ?: run {
                // Fallback to last known location if available
                suspendCancellableCoroutine { continuation ->
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                        continuation.resume(loc)
                    }.addOnFailureListener {
                        continuation.resume(null)
                    }
                }
            }

            if (location != null && location.latitude != 0.0 && location.longitude != 0.0) {
                val address = geocodeLocationUseCase.getFormattedAddress(
                    location.latitude,
                    location.longitude
                )
                CurrentLocationResult(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    formattedAddress = address
                )
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            cts.cancel()
        }
    }
}
