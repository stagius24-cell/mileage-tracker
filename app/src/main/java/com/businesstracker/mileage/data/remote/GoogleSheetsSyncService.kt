package com.businesstracker.mileage.data.remote

import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.repository.TripRepository
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
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

sealed class SheetsSyncResult {
    data class Success(val syncedCount: Int, val updatedCount: Int) : SheetsSyncResult()
    data class TokenExpired(val message: String) : SheetsSyncResult()
    data class HttpError(val statusCode: Int, val message: String) : SheetsSyncResult()
    data class NetworkError(val exception: IOException) : SheetsSyncResult()
    data class UnexpectedError(val exception: Exception) : SheetsSyncResult()
    data object NotConfigured : SheetsSyncResult()
}

@Singleton
class GoogleSheetsSyncService @Inject constructor(
    private val preferencesManager: TrackingPreferencesManager,
    private val tripRepository: TripRepository,
    private val gson: Gson
) {
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

    suspend fun syncPendingTrips(): SheetsSyncResult {
        val spreadsheetId = preferencesManager.sheetsSpreadsheetId.first().trim()
        val authToken = preferencesManager.sheetsAuthToken.first().trim()
        val webhookUrl = preferencesManager.sheetsWebhookUrl.first().trim()

        if (spreadsheetId.isBlank() && webhookUrl.isBlank()) {
            return SheetsSyncResult.NotConfigured
        }

        val pendingTrips = tripRepository.getPendingSyncTrips()
        // Never sync unreviewed trips as confirmed mileage
        val eligibleTrips = pendingTrips.filter { it.classification != "UNREVIEWED" }

        if (eligibleTrips.isEmpty()) {
            return SheetsSyncResult.Success(0, 0)
        }

        return if (webhookUrl.isNotBlank()) {
            syncViaWebhook(webhookUrl, eligibleTrips)
        } else {
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

            val existingRows = mutableMapOf<String, Int>() // tripId -> rowNumber
            var isHeaderPresent = false

            client.newCall(readRequest).execute().use { response ->
                when (response.code) {
                    401 -> return SheetsSyncResult.TokenExpired("Google authorization expired. Please reconnect.")
                    403 -> return SheetsSyncResult.HttpError(403, "Access denied. Check spreadsheet sharing permissions.")
                    404 -> return SheetsSyncResult.HttpError(404, "Spreadsheet not found. Verify the Spreadsheet ID.")
                    in 200..299 -> {
                        val body = response.body?.string().orEmpty()
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val values = json.getAsJsonArray("values")
                        if (values != null && values.size() > 0) {
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
                    else -> return SheetsSyncResult.HttpError(response.code, "Google Sheets API error (${response.code})")
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
                        return SheetsSyncResult.HttpError(resp.code, "Failed to initialize spreadsheet header.")
                    }
                }
            }

            var insertedCount = 0
            var updatedCount = 0

            // Step 3: Process trips - update if row already exists, otherwise append
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

                    client.newCall(req).execute().use { r ->
                        if (r.isSuccessful) {
                            tripRepository.markSynced(trip.id, System.currentTimeMillis())
                            updatedCount++
                        }
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

                    client.newCall(req).execute().use { r ->
                        if (r.isSuccessful) {
                            tripRepository.markSynced(trip.id, System.currentTimeMillis())
                            insertedCount++
                        }
                    }
                }
            }

            preferencesManager.setSheetsLastSyncTime(System.currentTimeMillis())
            preferencesManager.setSheetsLastError(null)
            return SheetsSyncResult.Success(insertedCount, updatedCount)

        } catch (e: IOException) {
            preferencesManager.setSheetsLastError(e.localizedMessage ?: "Network error")
            return SheetsSyncResult.NetworkError(e)
        } catch (e: Exception) {
            preferencesManager.setSheetsLastError(e.localizedMessage ?: "Unexpected sync error")
            return SheetsSyncResult.UnexpectedError(e)
        }
    }

    private suspend fun syncViaWebhook(webhookUrl: String, trips: List<TripEntity>): SheetsSyncResult {
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
                if (response.isSuccessful) {
                    val now = System.currentTimeMillis()
                    for (trip in trips) {
                        tripRepository.markSynced(trip.id, now)
                    }
                    preferencesManager.setSheetsLastSyncTime(now)
                    preferencesManager.setSheetsLastError(null)
                    return SheetsSyncResult.Success(trips.size, 0)
                } else {
                    val err = "Webhook responded with status ${response.code}"
                    preferencesManager.setSheetsLastError(err)
                    return SheetsSyncResult.HttpError(response.code, err)
                }
            }
        } catch (e: IOException) {
            preferencesManager.setSheetsLastError(e.localizedMessage ?: "Network error")
            return SheetsSyncResult.NetworkError(e)
        } catch (e: Exception) {
            preferencesManager.setSheetsLastError(e.localizedMessage ?: "Unexpected error")
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
