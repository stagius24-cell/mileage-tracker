package com.businesstracker.mileage.data.remote

import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.entity.VehicleEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.security.GoogleAuthManager
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.domain.repository.SavedLocationRepository
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.data.local.VehicleDao
import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CloudflareBackupResult {
    data class Success(val trips: Int, val vehicles: Int, val savedLocations: Int) : CloudflareBackupResult
    data class Failure(val message: String, val retryable: Boolean) : CloudflareBackupResult
}

sealed interface CloudflareRestoreResult {
    data class Success(val trips: Int, val vehicles: Int, val savedLocations: Int) : CloudflareRestoreResult
    data class Failure(val message: String) : CloudflareRestoreResult
}

/**
 * Full-detail, account-scoped D1 backup. Room remains the offline source of truth.
 * Google OAuth is used only to authenticate the account; no Google Sheets API is called here.
 */
@Singleton
class CloudflareD1BackupService @Inject constructor(
    private val preferencesManager: TrackingPreferencesManager,
    private val tripRepository: TripRepository,
    private val savedLocationRepository: SavedLocationRepository,
    private val vehicleDao: VehicleDao,
    private val secureStorageManager: SecureStorageManager,
    private val googleAuthManager: GoogleAuthManager,
    private val sheetsSyncService: GoogleSheetsSyncService,
    private val gson: Gson
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun backupAll(): CloudflareBackupResult = withContext(Dispatchers.IO) {
        val token = currentToken()
            ?: return@withContext CloudflareBackupResult.Failure(
                "Connect your Google account before backing up to Cloudflare.", false
            )
        try {
            var canCompleteLegacyImport = false
            if (!preferencesManager.cfInitialMigrationComplete.first() &&
                preferencesManager.isSheetsConnected.first()
            ) {
                // Import trips that exist only in the old sheet before the first D1 snapshot.
                canCompleteLegacyImport = when (sheetsSyncService.restoreTripsFromSheets()) {
                    is GoogleSheetsSyncService.TripRestoreResult.Success,
                    is GoogleSheetsSyncService.TripRestoreResult.Empty -> true
                    is GoogleSheetsSyncService.TripRestoreResult.Failed -> false
                }
            }
            val isInitialSnapshot = !preferencesManager.cfInitialSnapshotComplete.first()
            val trips = if (isInitialSnapshot || canCompleteLegacyImport) {
                tripRepository.getAllTrips().first()
            } else {
                tripRepository.getPendingSyncTrips()
            }
            val vehicles = vehicleDao.getVehicles()
            val locations = savedLocationRepository.getAllSavedLocations().first()
            val chunks = trips.chunked(50).ifEmpty { listOf(emptyList()) }

            for ((index, chunk) in chunks.withIndex()) {
                val requestBody = gson.toJson(
                    mapOf(
                        "trips" to chunk,
                        "vehicles" to if (index == 0) vehicles else emptyList<VehicleEntity>(),
                        "savedLocations" to if (index == 0) locations else emptyList<SavedLocationEntity>()
                    )
                ).toRequestBody(jsonType)
                var response = execute(
                    Request.Builder().url(syncEndpoint()).post(requestBody)
                        .header("Authorization", "Bearer $token")
                        .header("Content-Type", "application/json")
                        .build()
                )
                if (response.code == 401) {
                    response.close()
                    val refreshed = googleAuthManager.refreshOAuthToken()
                        ?: return@withContext CloudflareBackupResult.Failure(
                            "Google sign-in expired. Reconnect your account and retry.", false
                        )
                    response = execute(
                        Request.Builder().url(syncEndpoint()).post(requestBody)
                            .header("Authorization", "Bearer $refreshed")
                            .header("Content-Type", "application/json")
                            .build()
                    )
                }
                val code = response.code
                val responseText = response.use { it.body?.string().orEmpty() }
                if (code !in 200..299) {
                    return@withContext CloudflareBackupResult.Failure(
                        "Cloudflare backup failed (HTTP $code).", code >= 500 || code == 429
                    )
                }
                val ack = gson.fromJson(responseText, JsonObject::class.java)
                    ?: return@withContext CloudflareBackupResult.Failure("Invalid Cloudflare response.", true)
                if (chunk.isNotEmpty() && ack.getAsJsonArray("syncedTripIds")?.size() != chunk.size) {
                    return@withContext CloudflareBackupResult.Failure(
                        "Cloudflare did not confirm every trip; retry is safe.", true
                    )
                }
                if (index == 0 && (ack.getAsJsonArray("syncedVehicleIds")?.size() != vehicles.size ||
                    ack.getAsJsonArray("syncedSavedLocationIds")?.size() != locations.size)
                ) {
                    return@withContext CloudflareBackupResult.Failure(
                        "Cloudflare did not confirm every vehicle and saved location; retry is safe.", true
                    )
                }
                chunk.forEach { tripRepository.markSynced(it.id, System.currentTimeMillis()) }
            }
            if (isInitialSnapshot || canCompleteLegacyImport) preferencesManager.setCfInitialSnapshotComplete(true)
            if (canCompleteLegacyImport) preferencesManager.setCfInitialMigrationComplete(true)
            CloudflareBackupResult.Success(trips.size, vehicles.size, locations.size)
        } catch (e: IOException) {
            CloudflareBackupResult.Failure("Network error while backing up to Cloudflare: ${e.localizedMessage ?: "connection failed"}", true)
        } catch (e: Exception) {
            CloudflareBackupResult.Failure("Cloudflare backup failed: ${e.localizedMessage ?: "unexpected error"}", true)
        }
    }

    suspend fun restoreAll(): CloudflareRestoreResult = withContext(Dispatchers.IO) {
        var token = currentToken()
            ?: return@withContext CloudflareRestoreResult.Failure(
                "Connect your Google account before restoring from Cloudflare."
            )
        try {
            val restoredTrips = mutableListOf<TripEntity>()
            val restoredVehicles = mutableListOf<VehicleEntity>()
            val restoredLocations = mutableListOf<SavedLocationEntity>()
            var cursor: String? = null
            var firstPage = true
            do {
                val url = backupEndpoint() + (cursor?.let { "?cursor=$it" } ?: "")
                var response = execute(
                    Request.Builder().url(url).get().header("Authorization", "Bearer $token").build()
                )
                if (response.code == 401) {
                    response.close()
                    token = googleAuthManager.refreshOAuthToken()
                        ?: return@withContext CloudflareRestoreResult.Failure(
                            "Google sign-in expired. Reconnect your account and retry."
                        )
                    response = execute(
                        Request.Builder().url(url).get().header("Authorization", "Bearer $token").build()
                    )
                }
                val code = response.code
                val body = response.use { it.body?.string().orEmpty() }
                if (code !in 200..299) {
                    return@withContext CloudflareRestoreResult.Failure("Cloudflare restore failed (HTTP $code).")
                }
                val page = gson.fromJson(body, BackupPage::class.java)
                    ?: return@withContext CloudflareRestoreResult.Failure("Invalid Cloudflare backup response.")
                if (page.schemaVersion != 1) {
                    return@withContext CloudflareRestoreResult.Failure("Unsupported Cloudflare backup version.")
                }
                restoredTrips += page.trips.orEmpty()
                if (firstPage) {
                    restoredVehicles += page.vehicles.orEmpty()
                    restoredLocations += page.savedLocations.orEmpty()
                    firstPage = false
                }
                cursor = page.nextCursor?.takeIf { it.isNotBlank() }
            } while (cursor != null)

            restoredTrips.forEach { tripRepository.insertTrip(it) }
            restoredLocations.forEach { savedLocationRepository.saveLocation(it) }
            restoredVehicles.forEach { vehicle ->
                if (vehicleDao.getVehicle(vehicle.id) == null) vehicleDao.insert(vehicle)
                else vehicleDao.update(vehicle)
            }
            preferencesManager.setCfInitialMigrationComplete(true)
            CloudflareRestoreResult.Success(restoredTrips.size, restoredVehicles.size, restoredLocations.size)
        } catch (e: IOException) {
            CloudflareRestoreResult.Failure("Network error while restoring from Cloudflare: ${e.localizedMessage ?: "connection failed"}")
        } catch (e: Exception) {
            CloudflareRestoreResult.Failure("Cloudflare restore failed: ${e.localizedMessage ?: "unexpected error"}")
        }
    }

    private suspend fun currentToken(): String? =
        secureStorageManager.getOAuthToken()?.takeIf { it.isNotBlank() }
            ?: googleAuthManager.refreshOAuthToken()

    private suspend fun execute(request: Request): okhttp3.Response =
        withContext(Dispatchers.IO) { client.newCall(request).execute() }

    private suspend fun syncEndpoint(): String {
        val configured = preferencesManager.cfEndpoint.first().trim()
        return configured.takeIf { it.startsWith("https://") }
            ?: TrackingPreferencesManager.DEFAULT_CF_ENDPOINT
    }

    private suspend fun backupEndpoint(): String =
        syncEndpoint().removeSuffix("/sync").let { "$it/backup" }

    private data class BackupPage(
        val schemaVersion: Int,
        val trips: List<TripEntity>?,
        val vehicles: List<VehicleEntity>?,
        val savedLocations: List<SavedLocationEntity>?,
        val nextCursor: String?
    )
}
