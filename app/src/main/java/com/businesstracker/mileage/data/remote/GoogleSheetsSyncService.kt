package com.businesstracker.mileage.data.remote

import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.security.GoogleAuthManager
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.domain.repository.TripRepository
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
 * 4. Webhook Response Contract: Requires explicit JSON response containing a "syncedTripIds" string
 *    array confirming individual trip IDs. Trips are never marked synced on HTTP 200 alone.
 */
@Singleton
open class GoogleSheetsSyncService @Inject constructor(
    private val preferencesManager: TrackingPreferencesManager,
    private val tripRepository: TripRepository,
    private val secureStorageManager: SecureStorageManager,
    private val googleAuthManager: GoogleAuthManager?,
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
    open suspend fun syncPendingTrips(): SheetsSyncResult = syncMutex.withLock {
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
            syncViaSheetsApi(spreadsheetId, authToken, eligibleTrips, isRetryAfterRefresh = false)
        }
    }

    private suspend fun syncViaSheetsApi(
        spreadsheetId: String,
        authToken: String,
        trips: List<TripEntity>,
        isRetryAfterRefresh: Boolean = false
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
                        if (!isRetryAfterRefresh) {
                            val refreshed = googleAuthManager?.refreshOAuthToken()
                            if (refreshed != null && refreshed != authToken) {
                                return syncViaSheetsApi(spreadsheetId, refreshed, trips, isRetryAfterRefresh = true)
                            }
                        }
                        val msg = if (isRetryAfterRefresh) {
                            "Google authorization expired (401) after token refresh. Please reconnect your account."
                        } else {
                            "Google authorization expired (401). Please reconnect your account."
                        }
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
                        if (body.isBlank()) {
                            val msg = "Empty response body received from Google Sheets API"
                            preferencesManager.setSheetsLastError(msg)
                            return SheetsSyncResult.HttpError(response.code, msg)
                        }

                        val jsonElement = try {
                            gson.fromJson(body, JsonElement::class.java)
                        } catch (e: Exception) {
                            null
                        }

                        if (jsonElement == null || !jsonElement.isJsonObject) {
                            val msg = "Malformed JSON received from Google Sheets API"
                            preferencesManager.setSheetsLastError(msg)
                            return SheetsSyncResult.UnexpectedError(IllegalArgumentException(msg))
                        }

                        val json = jsonElement.asJsonObject

                        // Validate Google Sheets response structure
                        // A valid values.get response must contain either "range" or "values"
                        if (!json.has("range") && !json.has("values")) {
                            val msg = "Invalid Google Sheets response structure: missing required range or values"
                            preferencesManager.setSheetsLastError(msg)
                            return SheetsSyncResult.UnexpectedError(IllegalArgumentException(msg))
                        }

                        // Distinguish genuinely empty sheet from missing/malformed values
                        if (json.has("values")) {
                            val valuesElement = json.get("values")
                            if (!valuesElement.isJsonArray) {
                                val msg = "Malformed Google Sheets response: 'values' must be an array"
                                preferencesManager.setSheetsLastError(msg)
                                return SheetsSyncResult.UnexpectedError(IllegalArgumentException(msg))
                            }
                            val values = valuesElement.asJsonArray
                            maxRowFound = values.size()
                            for (i in 0 until values.size()) {
                                val rowElement = values.get(i)
                                if (!rowElement.isJsonArray) {
                                    val msg = "Malformed row at index $i: expected JSON array"
                                    preferencesManager.setSheetsLastError(msg)
                                    return SheetsSyncResult.UnexpectedError(IllegalArgumentException(msg))
                                }
                                val row = rowElement.asJsonArray
                                if (row.size() > 0) {
                                    val cell = try {
                                        row.get(0).asString
                                    } catch (_: Exception) {
                                        val msg = "Malformed cell at row ${i + 1}: expected string"
                                        preferencesManager.setSheetsLastError(msg)
                                        return SheetsSyncResult.UnexpectedError(IllegalArgumentException(msg))
                                    }
                                    if (i == 0 && cell.equals("Trip ID", ignoreCase = true)) {
                                        isHeaderPresent = true
                                    } else if (cell.isNotBlank()) {
                                        existingRows[cell] = i + 1 // 1-based row index in Sheet
                                    }
                                }
                            }
                        } else {
                            // Genuinely empty sheet: Google Sheets API omits "values" when range has no data
                            maxRowFound = 0
                            isHeaderPresent = false
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
                        return when (resp.code) {
                            401 -> SheetsSyncResult.TokenExpired(msg)
                            403 -> SheetsSyncResult.AccessDenied(msg)
                            404 -> SheetsSyncResult.NotFound(msg)
                            429 -> SheetsSyncResult.RateLimited(msg)
                            in 500..599 -> SheetsSyncResult.ServerError(resp.code, msg)
                            else -> SheetsSyncResult.HttpError(resp.code, msg)
                        }
                    }
                }
                if (maxRowFound < 1) maxRowFound = 1
            }

            var insertedCount = 0
            var updatedCount = 0
            var nextAvailableRow = maxOf(maxRowFound + 1, 2)
            val failedTrips = mutableListOf<Pair<String, String>>()
            var authExpiredMidSync = false
            var primaryFailureResult: SheetsSyncResult? = null

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
                                    val err = "Token expired (401)"
                                    failedTrips.add(trip.id to err)
                                    if (primaryFailureResult == null) primaryFailureResult = SheetsSyncResult.TokenExpired(err)
                                    false
                                }
                                403 -> {
                                    val err = "Access denied (403)"
                                    failedTrips.add(trip.id to err)
                                    if (primaryFailureResult == null) primaryFailureResult = SheetsSyncResult.AccessDenied(err)
                                    false
                                }
                                404 -> {
                                    val err = "Spreadsheet not found (404)"
                                    failedTrips.add(trip.id to err)
                                    if (primaryFailureResult == null) primaryFailureResult = SheetsSyncResult.NotFound(err)
                                    false
                                }
                                429 -> {
                                    val err = "Rate limit exceeded (429)"
                                    failedTrips.add(trip.id to err)
                                    if (primaryFailureResult == null) primaryFailureResult = SheetsSyncResult.RateLimited(err)
                                    false
                                }
                                in 500..599 -> {
                                    val err = "Server error (${r.code})"
                                    failedTrips.add(trip.id to err)
                                    if (primaryFailureResult == null) primaryFailureResult = SheetsSyncResult.ServerError(r.code, err)
                                    false
                                }
                                in 200..299 -> true
                                else -> {
                                    val err = "HTTP error (${r.code})"
                                    failedTrips.add(trip.id to err)
                                    if (primaryFailureResult == null) primaryFailureResult = SheetsSyncResult.HttpError(r.code, err)
                                    false
                                }
                            }
                        }
                    } catch (e: IOException) {
                        val err = e.localizedMessage ?: "Network error"
                        failedTrips.add(trip.id to err)
                        if (primaryFailureResult == null) primaryFailureResult = SheetsSyncResult.NetworkError(err, e)
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
                                    val err = "Token expired (401)"
                                    failedTrips.add(trip.id to err)
                                    if (primaryFailureResult == null) primaryFailureResult = SheetsSyncResult.TokenExpired(err)
                                    false
                                }
                                403 -> {
                                    val err = "Access denied (403)"
                                    failedTrips.add(trip.id to err)
                                    if (primaryFailureResult == null) primaryFailureResult = SheetsSyncResult.AccessDenied(err)
                                    false
                                }
                                404 -> {
                                    val err = "Spreadsheet not found (404)"
                                    failedTrips.add(trip.id to err)
                                    if (primaryFailureResult == null) primaryFailureResult = SheetsSyncResult.NotFound(err)
                                    false
                                }
                                429 -> {
                                    val err = "Rate limit exceeded (429)"
                                    failedTrips.add(trip.id to err)
                                    if (primaryFailureResult == null) primaryFailureResult = SheetsSyncResult.RateLimited(err)
                                    false
                                }
                                in 500..599 -> {
                                    val err = "Server error (${r.code})"
                                    failedTrips.add(trip.id to err)
                                    if (primaryFailureResult == null) primaryFailureResult = SheetsSyncResult.ServerError(r.code, err)
                                    false
                                }
                                in 200..299 -> true
                                else -> {
                                    val err = "HTTP error (${r.code})"
                                    failedTrips.add(trip.id to err)
                                    if (primaryFailureResult == null) primaryFailureResult = SheetsSyncResult.HttpError(r.code, err)
                                    false
                                }
                            }
                        }
                    } catch (e: IOException) {
                        val err = e.localizedMessage ?: "Network error"
                        failedTrips.add(trip.id to err)
                        if (primaryFailureResult == null) primaryFailureResult = SheetsSyncResult.NetworkError(err, e)
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

            // Step 4: Outcome evaluation preserving real HTTP status codes
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
                val categorized = primaryFailureResult ?: SheetsSyncResult.HttpError(500, "Sync failed for all eligible trips.")
                val errMsg = when (categorized) {
                    is SheetsSyncResult.TokenExpired -> categorized.message
                    is SheetsSyncResult.AccessDenied -> categorized.message
                    is SheetsSyncResult.NotFound -> categorized.message
                    is SheetsSyncResult.RateLimited -> categorized.message
                    is SheetsSyncResult.ServerError -> categorized.message
                    is SheetsSyncResult.NetworkError -> categorized.message
                    is SheetsSyncResult.HttpError -> categorized.message
                    else -> "Sync failed for all eligible trips."
                }
                preferencesManager.setSheetsLastError(errMsg)
                return categorized
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

    internal suspend fun syncViaWebhook(webhookUrl: String, trips: List<TripEntity>): SheetsSyncResult {
        // Enforce HTTPS validation using OkHttp HttpUrl and strict host presence
        val parsedUrl = webhookUrl.toHttpUrlOrNull()
        val schemeEnd = webhookUrl.indexOf("://")
        val rawHost = if (schemeEnd != -1) {
            webhookUrl.substring(schemeEnd + 3)
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringBefore(':')
        } else ""

        if (parsedUrl == null || !parsedUrl.isHttps || parsedUrl.host.isBlank() || rawHost.isBlank()) {
            val err = "Invalid Webhook URL: must be a valid URL using HTTPS protocol."
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
                    429 -> {
                        val err = "Webhook rate limit exceeded (429)"
                        preferencesManager.setSheetsLastError(err)
                        return SheetsSyncResult.RateLimited(err)
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

                        // Webhook Response Contract: Requires explicit 'syncedTripIds' JSON array.
                        // Trips are never confirmed on HTTP 200 alone.
                        if (!jsonObj.has("syncedTripIds") || !jsonObj.get("syncedTripIds").isJsonArray) {
                            val err = "Webhook response contract violation: missing or invalid 'syncedTripIds' array"
                            preferencesManager.setSheetsLastError(err)
                            return SheetsSyncResult.HttpError(200, err)
                        }

                        val syncedIdsArray = jsonObj.getAsJsonArray("syncedTripIds")
                        val confirmedTripIds = mutableSetOf<String>()
                        for (i in 0 until syncedIdsArray.size()) {
                            val elem = syncedIdsArray.get(i)
                            if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString) {
                                val id = elem.asString.trim()
                                if (id.isNotEmpty()) {
                                    confirmedTripIds.add(id)
                                }
                            }
                        }

                        val submittedIds = trips.map { it.id }.toSet()
                        val validConfirmedIds = confirmedTripIds.filter { submittedIds.contains(it) }.toSet()

                        val now = System.currentTimeMillis()
                        var markedCount = 0
                        for (trip in trips) {
                            if (validConfirmedIds.contains(trip.id)) {
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
                            val errMsg = "Partial webhook sync: $markedCount confirmed, $failedCount unconfirmed."
                            preferencesManager.setSheetsLastSyncTime(now)
                            preferencesManager.setSheetsLastError(errMsg)
                            return SheetsSyncResult.PartialSuccess(markedCount, failedCount, errMsg)
                        } else {
                            val errMsg = "Webhook did not confirm any submitted trips as synced."
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
