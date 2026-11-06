package com.businesstracker.mileage.data.remote

import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.domain.repository.TripRepository
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync result taxonomy distinguishing complete success, partial success,
 * authorization expiration, rate-limiting, network failures, and configuration errors.
 */
sealed class SheetsSyncResult {
    data class Success(val insertedCount: Int, val updatedCount: Int) : SheetsSyncResult()
    data class PartialSuccess(val successCount: Int, val failedCount: Int, val message: String) : SheetsSyncResult()
    data class TokenExpired(val message: String) : SheetsSyncResult()
    data class AccessDenied(val message: String) : SheetsSyncResult()
    data class NotFound(val message: String) : SheetsSyncResult()
    data class RateLimited(val message: String) : SheetsSyncResult()
    data class ServerError(val statusCode: Int, val message: String) : SheetsSyncResult()
    data class HttpError(val statusCode: Int, val message: String) : SheetsSyncResult()
    data class NetworkError(val message: String, val exception: IOException) : SheetsSyncResult()
    data class InvalidConfiguration(val message: String) : SheetsSyncResult()
    data class UnexpectedError(val exception: Exception) : SheetsSyncResult()
    data object NotConfigured : SheetsSyncResult()
}

/**
 * Service orchestrating duplicate-proof synchronization of finalized business trips
 * with Google Sheets or secure HTTPS Webhooks.
 *
 * Concurrency & Multi-Device Architecture Notes:
 * 1. Process-Level Concurrency: A coroutine [Mutex] ensures that within this application
 *    process, only one sync operation can run at any given moment. Concurrent triggers
 *    from background WorkManager and manual UI button presses are safely serialized.
 * 2. Idempotent Matching: Remote Trip IDs in Column A are queried before writing. Existing
 *    rows are updated in-place via PUT requests rather than blindly appended, avoiding duplicates.
 * 3. Multi-Device Concurrency Limitations: While this client-side Mutex guarantees single-threaded
 *    execution within this Android installation, Google Sheets REST API does not provide
 *    distributed ACID table transactions across distinct devices or external concurrent writers.
 *    If multiple distinct devices simultaneously sync to the same spreadsheet without an intermediary
 *    central database, cell-level race conditions are theoretically possible at the Sheets API level.
 */
@Singleton
class GoogleSheetsSyncService @Inject constructor(
    private val preferencesManager: TrackingPreferencesManager,
    private val tripRepository: TripRepository,
    private val secureStorageManager: SecureStorageManager,
    private val googleAuthManager: com.businesstracker.mileage.data.security.GoogleAuthManager?,
    private val gson: Gson
) {
    // Secondary constructor for testing or when GoogleAuthManager is omitted
    constructor(
        preferencesManager: TrackingPreferencesManager,
        tripRepository: TripRepository,
        secureStorageManager: SecureStorageManager,
        gson: Gson
    ) : this(preferencesManager, tripRepository, secureStorageManager, null, gson)

    private val syncMutex = Mutex()

    internal var client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        val EXPECTED_HEADERS = listOf(
            "Trip ID",
            "Date",
            "Start Time",
            "End Time",
            "Start Address",
            "End Address",
            "Total Miles",
            "Business Miles",
            "Classification",
            "Business Entity",
            "Purpose",
            "Mileage Rate",
            "Estimated Deduction",
            "Notes",
            "Tracking Method",
            "Last Updated"
        )
    }

    /**
     * Executes synchronization of all pending reviewed trips with Google Sheets.
     * Serialized with an in-process Mutex to eliminate local race conditions.
     */
    suspend fun syncPendingTrips(): SheetsSyncResult = syncMutex.withLock {
        val spreadsheetId = preferencesManager.sheetsSpreadsheetId.first().trim()
        val webhookUrl = preferencesManager.sheetsWebhookUrl.first().trim()

        // Safely migrate any legacy DataStore plaintext token and resolve strictly from Keystore
        preferencesManager.migrateLegacyAuthToken(secureStorageManager)
        val authToken = secureStorageManager.getOAuthToken()?.trim().orEmpty()

        if (spreadsheetId.isBlank() && webhookUrl.isBlank()) {
            return@withLock SheetsSyncResult.NotConfigured
        }

        val pendingTrips = tripRepository.getPendingSyncTrips()
        // Strict business rule: Never sync UNREVIEWED trips as confirmed mileage
        val eligibleTrips = pendingTrips.filter { it.classification != "UNREVIEWED" }

        if (eligibleTrips.isEmpty()) {
            return@withLock SheetsSyncResult.Success(0, 0)
        }

        return@withLock if (webhookUrl.isNotBlank()) {
            syncViaWebhook(webhookUrl, eligibleTrips)
        } else {
            if (authToken.isBlank()) {
                val err = "Google Sheets authentication token missing. Please connect your account."
                preferencesManager.setSheetsLastError(err)
                return@withLock SheetsSyncResult.TokenExpired(err)
            }
            syncViaSheetsApi(spreadsheetId, authToken, eligibleTrips)
        }
    }

    private suspend fun syncViaSheetsApi(
        spreadsheetId: String,
        authToken: String,
        trips: List<TripEntity>
    ): SheetsSyncResult {
        try {
            // Step 1: Read existing Column A to map existing trip IDs to their 1-based row numbers
            val readUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/Sheet1!A:A"
            val readRequest = Request.Builder()
                .url(readUrl)
                .addHeader("Authorization", "Bearer $authToken")
                .get()
                .build()

            val existingRows = mutableMapOf<String, Int>() // tripId -> 1-based row index
            var isHeaderPresent = false
            var maxRowFound = 1

            client.newCall(readRequest).execute().use { response ->
                when (response.code) {
                    401 -> {
                        val refreshed = googleAuthManager?.refreshOAuthToken()
                        if (refreshed != null && refreshed != authToken) {
                            return syncViaSheetsApi(spreadsheetId, refreshed, trips)
                        }
                        val msg = "Google authorization expired (401). Please reconnect your account."
                        preferencesManager.setSheetsLastError(msg)
                        return SheetsSyncResult.TokenExpired(msg)
                    }
                    403 -> {
                        val msg = "Access denied (403). Check spreadsheet sharing permissions."
                        preferencesManager.setSheetsLastError(msg)
                        return SheetsSyncResult.AccessDenied(msg)
                    }
                    404 -> {
                        val msg = "Spreadsheet not found (404). Verify the Spreadsheet ID."
                        preferencesManager.setSheetsLastError(msg)
                        return SheetsSyncResult.NotFound(msg)
                    }
                    429 -> {
                        val msg = "Google Sheets API rate limit exceeded (429). Retrying later."
                        preferencesManager.setSheetsLastError(msg)
                        return SheetsSyncResult.RateLimited(msg)
                    }
                    in 500..599 -> {
                        val msg = "Google Sheets server error (${response.code})."
                        preferencesManager.setSheetsLastError(msg)
                        return SheetsSyncResult.ServerError(response.code, msg)
                    }
                    in 200..299 -> {
                        val body = response.body?.string().orEmpty()
                        val json = try {
                            gson.fromJson(body, JsonObject::class.java)
                        } catch (_: Exception) {
                            null
                        }
                        val values = json?.getAsJsonArray("values")
                        if (values != null && values.size() > 0) {
                            maxRowFound = values.size()
                            for (i in 0 until values.size()) {
                                val row = values.get(i).asJsonArray
                                if (row.size() > 0) {
                                    val cell = row.get(0).asString
                                    if (i == 0 && cell.equals("Trip ID", ignoreCase = true)) {
                                        isHeaderPresent = true
                                    } else if (cell.isNotBlank()) {
                                        existingRows[cell] = i + 1 // 1-based row index in Sheet
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val msg = "Failed to access spreadsheet (${response.code})"
                        preferencesManager.setSheetsLastError(msg)
                        return SheetsSyncResult.HttpError(response.code, msg)
                    }
                }
            }

            // Step 2: Ensure header row exists
            if (!isHeaderPresent) {
                val headerPayload = JsonObject().apply {
                    addProperty("range", "Sheet1!A1:P1")
                    addProperty("majorDimension", "ROWS")
                    val rowsArray = JsonArray()
                    val headerRow = JsonArray()
                    EXPECTED_HEADERS.forEach { headerRow.add(it) }
                    rowsArray.add(headerRow)
                    add("values", rowsArray)
                }

                val putHeaderUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/Sheet1!A1:P1?valueInputOption=USER_ENTERED"
                val headerReq = Request.Builder()
                    .url(putHeaderUrl)
                    .addHeader("Authorization", "Bearer $authToken")
                    .put(gson.toJson(headerPayload).toRequestBody(jsonMediaType))
                    .build()

                client.newCall(headerReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val msg = "Failed to initialize spreadsheet header (${resp.code})."
                        preferencesManager.setSheetsLastError(msg)
                        return if (resp.code == 401) SheetsSyncResult.TokenExpired(msg) else SheetsSyncResult.HttpError(resp.code, msg)
                    }
                }
                if (maxRowFound < 1) maxRowFound = 1
            }

            var insertedCount = 0
            var updatedCount = 0
            var nextAvailableRow = maxOf(maxRowFound + 1, 2)
            val failedTrips = mutableListOf<Pair<String, String>>()
            var authExpiredMidSync = false

            // Step 3: Process each individual trip and confirm remote write
            for (trip in trips) {
                val rowData = formatTripToRow(trip)
                val existingRowIndex = existingRows[trip.id]

                if (existingRowIndex != null) {
                    // Update existing row (idempotent, avoids duplicate)
                    val updateUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/Sheet1!A$existingRowIndex:P$existingRowIndex?valueInputOption=USER_ENTERED"
                    val payload = JsonObject().apply {
                        addProperty("range", "Sheet1!A$existingRowIndex:P$existingRowIndex")
                        addProperty("majorDimension", "ROWS")
                        val rows = JsonArray()
                        val row = JsonArray()
                        rowData.forEach { row.add(it) }
                        rows.add(row)
                        add("values", rows)
                    }

                    val req = Request.Builder()
                        .url(updateUrl)
                        .addHeader("Authorization", "Bearer $authToken")
                        .put(gson.toJson(payload).toRequestBody(jsonMediaType))
                        .build()

                    val writeSuccess = try {
                        client.newCall(req).execute().use { r ->
                            when (r.code) {
                                401 -> {
                                    authExpiredMidSync = true
                                    failedTrips.add(trip.id to "Token expired (401)")
                                    false
                                }
                                in 200..299 -> true
                                else -> {
                                    failedTrips.add(trip.id to "HTTP ${r.code}")
                                    false
                                }
                            }
                        }
                    } catch (e: IOException) {
                        failedTrips.add(trip.id to (e.localizedMessage ?: "Network error"))
                        false
                    }

                    if (writeSuccess) {
                        tripRepository.markSynced(trip.id, System.currentTimeMillis())
                        updatedCount++
                    }
                } else {
                    // Append new row
                    val appendUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/Sheet1!A:P:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS"
                    val payload = JsonObject().apply {
                        addProperty("range", "Sheet1!A:P")
                        addProperty("majorDimension", "ROWS")
                        val rows = JsonArray()
                        val row = JsonArray()
                        rowData.forEach { row.add(it) }
                        rows.add(row)
                        add("values", rows)
                    }

                    val req = Request.Builder()
                        .url(appendUrl)
                        .addHeader("Authorization", "Bearer $authToken")
                        .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                        .build()

                    val writeSuccess = try {
                        client.newCall(req).execute().use { r ->
                            when (r.code) {
                                401 -> {
                                    authExpiredMidSync = true
                                    failedTrips.add(trip.id to "Token expired (401)")
                                    false
                                }
                                in 200..299 -> true
                                else -> {
                                    failedTrips.add(trip.id to "HTTP ${r.code}")
                                    false
                                }
                            }
                        }
                    } catch (e: IOException) {
                        failedTrips.add(trip.id to (e.localizedMessage ?: "Network error"))
                        false
                    }

                    if (writeSuccess) {
                        tripRepository.markSynced(trip.id, System.currentTimeMillis())
                        existingRows[trip.id] = nextAvailableRow++
                        insertedCount++
                    }
                }

                if (authExpiredMidSync) {
                    break // Stop attempting further writes when credentials expired
                }
            }

            val totalSucceeded = insertedCount + updatedCount
            val totalFailed = failedTrips.size + (if (authExpiredMidSync) (trips.size - (totalSucceeded + failedTrips.size)) else 0)

            // Step 4: Outcome evaluation
            if (totalFailed == 0 && totalSucceeded > 0) {
                preferencesManager.setSheetsLastSyncTime(System.currentTimeMillis())
                preferencesManager.setSheetsLastError(null)
                return SheetsSyncResult.Success(insertedCount, updatedCount)
            } else if (totalSucceeded > 0 && totalFailed > 0) {
                preferencesManager.setSheetsLastSyncTime(System.currentTimeMillis())
                val errMsg = "Partially synced: $totalSucceeded succeeded, $totalFailed failed. Remaining trips remain pending."
                preferencesManager.setSheetsLastError(errMsg)
                return SheetsSyncResult.PartialSuccess(totalSucceeded, totalFailed, errMsg)
            } else {
                val errMsg = if (authExpiredMidSync) {
                    "Google authorization expired. Please reconnect."
                } else {
                    failedTrips.firstOrNull()?.second ?: "Sync failed for all eligible trips."
                }
                preferencesManager.setSheetsLastError(errMsg)
                return if (authExpiredMidSync) {
                    SheetsSyncResult.TokenExpired(errMsg)
                } else {
                    SheetsSyncResult.HttpError(500, errMsg)
                }
            }

        } catch (e: IOException) {
            val msg = e.localizedMessage ?: "Network error during sync"
            preferencesManager.setSheetsLastError(msg)
            return SheetsSyncResult.NetworkError(msg, e)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Unexpected sync error"
            preferencesManager.setSheetsLastError(msg)
            return SheetsSyncResult.UnexpectedError(e)
        }
    }

    private suspend fun syncViaWebhook(webhookUrl: String, trips: List<TripEntity>): SheetsSyncResult {
        // Enforce HTTPS validation
        if (!webhookUrl.startsWith("https://", ignoreCase = true)) {
            val err = "Insecure Webhook URL: must use HTTPS protocol."
            preferencesManager.setSheetsLastError(err)
            return SheetsSyncResult.InvalidConfiguration(err)
        }

        try {
            val payloadArray = JsonArray()
            for (trip in trips) {
                val obj = JsonObject().apply {
                    addProperty("id", trip.id)
                    addProperty("date", trip.date)
                    addProperty("startTime", trip.startTime)
                    addProperty("endTime", trip.endTime)
                    addProperty("startAddress", trip.startAddress.orEmpty())
                    addProperty("endAddress", trip.endAddress.orEmpty())
                    addProperty("totalDistanceMiles", trip.totalDistanceMiles)
                    addProperty("businessMiles", trip.businessMiles)
                    addProperty("classification", trip.classification)
                    addProperty("businessEntity", trip.businessEntity)
                    addProperty("purpose", trip.purpose)
                    addProperty("applicableRate", trip.applicableRate)
                    addProperty("calculatedDeduction", trip.calculatedDeduction)
                    addProperty("notes", trip.notes.orEmpty())
                    addProperty("trackingMethod", trip.trackingMethod)
                }
                payloadArray.add(obj)
            }

            val root = JsonObject().apply {
                addProperty("action", "sync")
                add("trips", payloadArray)
            }

            val request = Request.Builder()
                .url(webhookUrl)
                .post(gson.toJson(root).toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                when (response.code) {
                    401 -> {
                        val err = "Webhook authentication rejected (401)"
                        preferencesManager.setSheetsLastError(err)
                        return SheetsSyncResult.TokenExpired(err)
                    }
                    403 -> {
                        val err = "Webhook access denied (403)"
                        preferencesManager.setSheetsLastError(err)
                        return SheetsSyncResult.AccessDenied(err)
                    }
                    404 -> {
                        val err = "Webhook endpoint not found (404)"
                        preferencesManager.setSheetsLastError(err)
                        return SheetsSyncResult.NotFound(err)
                    }
                    in 500..599 -> {
                        val err = "Webhook server error (${response.code})"
                        preferencesManager.setSheetsLastError(err)
                        return SheetsSyncResult.ServerError(response.code, err)
                    }
                    in 200..299 -> {
                        val rawBody = response.body?.string().orEmpty()
                        val jsonElement = try {
                            gson.fromJson(rawBody, JsonElement::class.java)
                        } catch (_: Exception) {
                            null
                        }

                        if (jsonElement == null || !jsonElement.isJsonObject) {
                            val err = "Malformed webhook response: Expected JSON object"
                            preferencesManager.setSheetsLastError(err)
                            return SheetsSyncResult.UnexpectedError(IllegalArgumentException(err))
                        }

                        val jsonObj = jsonElement.asJsonObject

                        // Inspect application-level status/success flags
                        val isExplicitError = jsonObj.has("status") && jsonObj.get("status").asString.equals("error", ignoreCase = true)
                        val isExplicitFailure = jsonObj.has("success") && !jsonObj.get("success").asBoolean

                        if (isExplicitError || isExplicitFailure) {
                            val serverMsg = jsonObj.get("message")?.asString
                                ?: jsonObj.get("error")?.asString
                                ?: "Webhook reported application-level failure"
                            preferencesManager.setSheetsLastError(serverMsg)
                            return SheetsSyncResult.HttpError(200, serverMsg)
                        }

                        // Check for per-trip confirmation in response (syncedTripIds array)
                        val syncedIdsArray = jsonObj.getAsJsonArray("syncedTripIds")
                        val confirmedTripIds = if (syncedIdsArray != null) {
                            val set = mutableSetOf<String>()
                            for (i in 0 until syncedIdsArray.size()) {
                                set.add(syncedIdsArray.get(i).asString)
                            }
                            set
                        } else {
                            trips.map { it.id }.toSet()
                        }

                        val now = System.currentTimeMillis()
                        var markedCount = 0
                        for (trip in trips) {
                            if (confirmedTripIds.contains(trip.id)) {
                                tripRepository.markSynced(trip.id, now)
                                markedCount++
                            }
                        }

                        if (markedCount == trips.size) {
                            preferencesManager.setSheetsLastSyncTime(now)
                            preferencesManager.setSheetsLastError(null)
                            return SheetsSyncResult.Success(trips.size, 0)
                        } else if (markedCount > 0) {
                            val failedCount = trips.size - markedCount
                            val errMsg = "Partial webhook sync: $markedCount synced, $failedCount unconfirmed."
                            preferencesManager.setSheetsLastSyncTime(now)
                            preferencesManager.setSheetsLastError(errMsg)
                            return SheetsSyncResult.PartialSuccess(markedCount, failedCount, errMsg)
                        } else {
                            val errMsg = "Webhook did not confirm any trips as synced."
                            preferencesManager.setSheetsLastError(errMsg)
                            return SheetsSyncResult.HttpError(200, errMsg)
                        }
                    }
                    else -> {
                        val err = "Webhook HTTP error (${response.code})"
                        preferencesManager.setSheetsLastError(err)
                        return SheetsSyncResult.HttpError(response.code, err)
                    }
                }
            }
        } catch (e: IOException) {
            val msg = e.localizedMessage ?: "Webhook network error"
            preferencesManager.setSheetsLastError(msg)
            return SheetsSyncResult.NetworkError(msg, e)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Unexpected error during webhook sync"
            preferencesManager.setSheetsLastError(msg)
            return SheetsSyncResult.UnexpectedError(e)
        }
    }

    fun formatTripToRow(trip: TripEntity): List<String> {
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        val startTimeStr = if (trip.startTime > 0) timeFmt.format(Date(trip.startTime)) else ""
        val endTimeStr = if (trip.endTime > 0) timeFmt.format(Date(trip.endTime)) else ""
        val updatedStr = dateFmt.format(Date())

        return listOf(
            trip.id,
            trip.date,
            startTimeStr,
            endTimeStr,
            trip.startAddress.orEmpty(),
            trip.endAddress.orEmpty(),
            String.format(Locale.US, "%.2f", trip.totalDistanceMiles),
            String.format(Locale.US, "%.2f", trip.businessMiles),
            trip.classification,
            trip.businessEntity,
            trip.purpose,
            String.format(Locale.US, "%.3f", trip.applicableRate),
            String.format(Locale.US, "%.2f", trip.calculatedDeduction),
            trip.notes.orEmpty(),
            trip.trackingMethod,
            updatedStr
        )
    }
}
