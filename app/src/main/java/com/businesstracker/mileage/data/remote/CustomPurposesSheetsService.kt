package com.businesstracker.mileage.data.remote

import android.util.Log
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.security.GoogleAuthManager
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class CustomPurposesSyncResult {
    data class Success(val count: Int, val timestamp: Long) : CustomPurposesSyncResult()
    object NotConfigured : CustomPurposesSyncResult()
    data class NetworkError(val message: String) : CustomPurposesSyncResult()
    data class TokenExpired(val message: String) : CustomPurposesSyncResult()
    data class AccessDenied(val message: String) : CustomPurposesSyncResult()
    data class NotFound(val message: String) : CustomPurposesSyncResult()
    data class RateLimited(val message: String) : CustomPurposesSyncResult()
    data class ServerError(val statusCode: Int, val message: String) : CustomPurposesSyncResult()
    data class InvalidConfiguration(val message: String) : CustomPurposesSyncResult()
    data class HttpError(val statusCode: Int, val message: String) : CustomPurposesSyncResult()
    data class UnexpectedError(val message: String) : CustomPurposesSyncResult()

    fun toMessage(): String = when (this) {
        is Success -> "Successfully backed up $count custom purposes."
        is NotConfigured -> "Google Sheets is not configured."
        is NetworkError -> message
        is TokenExpired -> "Google authorization expired: $message"
        is AccessDenied -> "Access denied to spreadsheet: $message"
        is NotFound -> "Spreadsheet not found: $message"
        is RateLimited -> "Rate limit exceeded: $message"
        is ServerError -> "Server error ($statusCode): $message"
        is InvalidConfiguration -> message
        is HttpError -> "HTTP error ($statusCode): $message"
        is UnexpectedError -> message
    }
}

sealed class CustomPurposesRestoreResult {
    data class Success(val addedCount: Int, val totalCloudCount: Int) : CustomPurposesRestoreResult()
    object NotConfigured : CustomPurposesRestoreResult()
    data class Empty(val message: String) : CustomPurposesRestoreResult()
    data class Failure(val message: String) : CustomPurposesRestoreResult()
}

@Singleton
open class CustomPurposesSheetsService(
    private val preferencesManager: TrackingPreferencesManager,
    private val secureStorage: SecureStorageManager,
    private val googleAuthManager: GoogleAuthManager?,
    private val gson: Gson,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    internal var sheetsApiBaseUrl: String = DEFAULT_SHEETS_API_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    @Inject
    constructor(
        preferencesManager: TrackingPreferencesManager,
        secureStorage: SecureStorageManager,
        googleAuthManager: GoogleAuthManager,
        gson: Gson
    ) : this(
        preferencesManager = preferencesManager,
        secureStorage = secureStorage,
        googleAuthManager = googleAuthManager,
        gson = gson,
        ioDispatcher = Dispatchers.IO,
        sheetsApiBaseUrl = DEFAULT_SHEETS_API_BASE_URL
    )

    private val syncMutex = Mutex()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val TAG = "CustomPurposesSheets"
        const val WORKSHEET_NAME = "Custom Purposes"
        val EXPECTED_HEADERS = listOf("Purpose", "Last Updated")
        const val VALUE_INPUT_OPTION = "USER_ENTERED"
        const val DEFAULT_SHEETS_API_BASE_URL = "https://sheets.googleapis.com"
    }

    private class NonRetryableSyncException(val result: CustomPurposesSyncResult) : Exception(result.toMessage())

    private data class ReadValuesOutcome(
        val rows: List<List<Any?>>,
        val activeToken: String
    )

    private sealed class ConfigOutcome {
        data class Ready(val spreadsheetId: String, val token: String) : ConfigOutcome()
        data class Error(val result: CustomPurposesSyncResult) : ConfigOutcome()
    }

    private suspend fun resolveAuthorizedConfig(): ConfigOutcome {
        val isConnected = preferencesManager.isSheetsConnected.first()
        val spreadsheetId = preferencesManager.sheetsSpreadsheetId.first().trim()

        if (!isConnected || spreadsheetId.isBlank()) {
            return ConfigOutcome.Error(CustomPurposesSyncResult.NotConfigured)
        }

        var token = secureStorage.getOAuthToken()?.trim().orEmpty()
        if (token.isBlank()) {
            val refreshed = tryRefreshToken()
            if (!refreshed.isNullOrBlank()) {
                token = refreshed
            }
        }

        if (token.isBlank()) {
            return ConfigOutcome.Error(
                CustomPurposesSyncResult.TokenExpired(
                    "Google authentication expired. Please reconnect your Google account in Settings."
                )
            )
        }

        return ConfigOutcome.Ready(spreadsheetId, token)
    }

    private suspend fun tryRefreshToken(): String? {
        return try {
            googleAuthManager?.refreshOAuthToken()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "OAuth refresh failed", e)
            null
        }
    }

    private sealed class EnsureOutcome {
        data class Success(val sheetId: Int, val activeToken: String) : EnsureOutcome()
        data class Failure(val result: CustomPurposesSyncResult) : EnsureOutcome()
    }

    private data class SheetPropertyInfo(
        val sheetId: Int,
        val title: String
    )

    private fun extractSheetProperties(metadataJson: String): List<SheetPropertyInfo> {
        return try {
            val root = gson.fromJson(metadataJson, JsonObject::class.java) ?: return emptyList()
            val sheets = root.getAsJsonArray("sheets") ?: return emptyList()
            val list = mutableListOf<SheetPropertyInfo>()
            for (i in 0 until sheets.size()) {
                val sheetObj = sheets.get(i).asJsonObject ?: continue
                val props = sheetObj.getAsJsonObject("properties") ?: continue
                val title = props.get("title")?.asString?.trim() ?: continue
                val id = props.get("sheetId")?.asInt ?: continue
                list.add(SheetPropertyInfo(id, title))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun findCustomPurposesSheet(sheets: List<SheetPropertyInfo>): SheetPropertyInfo? {
        return sheets.firstOrNull { it.title.equals(WORKSHEET_NAME, ignoreCase = true) }
            ?: sheets.firstOrNull { it.title.startsWith("Custom Purpose", ignoreCase = true) }
            ?: sheets.firstOrNull { it.title.contains("purpose", ignoreCase = true) }
    }

    private suspend fun ensureWorksheetAndHeaders(spreadsheetId: String, initialToken: String): EnsureOutcome {
        var token = initialToken
        val metaUrl = baseHttpUrlBuilder(spreadsheetId)
            .addQueryParameter("fields", "sheets.properties")
            .build()

        val (metaResp, tokenAfterMeta) = executeWithAuthRetry(
            Request.Builder().url(metaUrl).get(),
            token
        )
        token = tokenAfterMeta

        var existingSheet: SheetPropertyInfo? = null
        metaResp.use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return EnsureOutcome.Failure(mapHttpFailure(resp.code, bodyStr))
            }
            val sheets = extractSheetProperties(bodyStr)
            existingSheet = findCustomPurposesSheet(sheets)
        }

        var sheetId = existingSheet?.sheetId
        val sheetTitle = existingSheet?.title ?: WORKSHEET_NAME

        if (sheetId == null) {
            val addSheetJson = JsonObject().apply {
                add("requests", JsonArray().apply {
                    add(JsonObject().apply {
                        add("addSheet", JsonObject().apply {
                            add("properties", JsonObject().apply {
                                addProperty("title", WORKSHEET_NAME)
                            })
                        })
                    })
                })
            }
            val batchUrl = baseHttpUrlBuilder(spreadsheetId)
                .encodedPath(basePath(spreadsheetId) + ":batchUpdate")
                .build()
            val (createResp, tokenAfterCreate) = executeWithAuthRetry(
                Request.Builder().url(batchUrl).post(gson.toJson(addSheetJson).toRequestBody(jsonMediaType)),
                token
            )
            token = tokenAfterCreate
            createResp.use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return EnsureOutcome.Failure(mapHttpFailure(resp.code, bodyStr))
                }
                sheetId = extractAddedSheetId(bodyStr)
            }
        }

        val resolvedSheetId = sheetId ?: return EnsureOutcome.Failure(
            CustomPurposesSyncResult.UnexpectedError("Unable to resolve worksheet ID for '$WORKSHEET_NAME'")
        )

        val headerRange = if (sheetTitle.contains(" ") && !sheetTitle.startsWith("'")) {
            "'$sheetTitle'!A1:B1"
        } else {
            "$sheetTitle!A1:B1"
        }

        // Ensure header row A1:B1 matches expected headers
        val (headerResp, tokenAfterHeader) = executeWithAuthRetry(
            Request.Builder().url(
                baseHttpUrlBuilder(spreadsheetId)
                    .addPathSegment("values")
                    .addPathSegment(headerRange)
                    .build()
            ).get(),
            token
        )
        token = tokenAfterHeader

        var headerMatches = false
        headerResp.use { resp ->
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string().orEmpty()
                val parsed = gson.fromJson(bodyStr, JsonObject::class.java)
                val values = parsed?.getAsJsonArray("values")
                if (values != null && values.size() > 0) {
                    val firstRow = values.get(0).asJsonArray
                    val headers = (0 until firstRow.size()).map { firstRow.get(it).asString.trim() }
                    if (headers == EXPECTED_HEADERS) {
                        headerMatches = true
                    }
                }
            }
        }

        if (!headerMatches) {
            val writeOutcome = writeRange(
                spreadsheetId = spreadsheetId,
                rangeA1 = headerRange,
                values = listOf(EXPECTED_HEADERS),
                token = token
            )
            when (writeOutcome) {
                is HttpWriteOutcome.Success -> token = writeOutcome.activeToken
                is HttpWriteOutcome.Fatal -> return EnsureOutcome.Failure(writeOutcome.result)
                is HttpWriteOutcome.Retryable -> return EnsureOutcome.Failure(
                    CustomPurposesSyncResult.NetworkError("Failed to initialize headers: ${writeOutcome.message}")
                )
            }
        }

        return EnsureOutcome.Success(resolvedSheetId, token)
    }

    private fun findWorksheetId(metadataJson: String, targetTitle: String): Int? {
        val sheets = extractSheetProperties(metadataJson)
        return sheets.firstOrNull { it.title.equals(targetTitle, ignoreCase = true) }?.sheetId
            ?: sheets.firstOrNull { it.title.contains(targetTitle, ignoreCase = true) }?.sheetId
    }

    private fun extractAddedSheetId(batchUpdateResponseJson: String): Int? {
        return try {
            val root = gson.fromJson(batchUpdateResponseJson, JsonObject::class.java) ?: return null
            val replies = root.getAsJsonArray("replies") ?: return null
            if (replies.size() == 0) return null
            val first = replies.get(0).asJsonObject ?: return null
            val addSheet = first.getAsJsonObject("addSheet") ?: return null
            addSheet.getAsJsonObject("properties")?.get("sheetId")?.asInt
        } catch (_: Exception) {
            null
        }
    }

    private sealed class HttpWriteOutcome {
        data class Success(val activeToken: String) : HttpWriteOutcome()
        data class Fatal(val result: CustomPurposesSyncResult) : HttpWriteOutcome()
        data class Retryable(val message: String) : HttpWriteOutcome()
    }

    private suspend fun writeRange(
        spreadsheetId: String,
        rangeA1: String,
        values: List<List<Any>>,
        token: String
    ): HttpWriteOutcome {
        val putUrl = baseHttpUrlBuilder(spreadsheetId)
            .addPathSegment("values")
            .addPathSegment(rangeA1)
            .addQueryParameter("valueInputOption", VALUE_INPUT_OPTION)
            .build()
        val payload = gson.toJson(mapOf("values" to values))
        return executeWriteCall(
            Request.Builder().url(putUrl).put(payload.toRequestBody(jsonMediaType)),
            token
        )
    }

    private suspend fun clearRange(
        spreadsheetId: String,
        rangeA1: String,
        token: String
    ): HttpWriteOutcome {
        val clearUrl = baseHttpUrlBuilder(spreadsheetId)
            .addPathSegment("values")
            .addPathSegment(rangeA1 + ":clear")
            .build()
        val emptyBody = "{}".toRequestBody(jsonMediaType)
        return executeWriteCall(
            Request.Builder().url(clearUrl).post(emptyBody),
            token
        )
    }

    private suspend fun readSheetValues(spreadsheetId: String, token: String): ReadValuesOutcome {
        return readRangeSafely(spreadsheetId, "'$WORKSHEET_NAME'!A2:B", token)
    }

    private suspend fun readRangeSafely(spreadsheetId: String, rangeA1: String, token: String): ReadValuesOutcome {
        val valuesUrl = baseHttpUrlBuilder(spreadsheetId)
            .addPathSegment("values")
            .addPathSegment(rangeA1)
            .build()

        val (resp, finalToken) = executeWithAuthRetry(
            Request.Builder().url(valuesUrl).get(),
            token
        )
        resp.use {
            val bodyStr = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                // If it failed with 400 and range was quoted with single quotes, retry without quotes (or vice-versa)
                if (it.code == 400 && rangeA1.startsWith("'") && rangeA1.contains("'!")) {
                    val unquoted = rangeA1.removeSurrounding("'").substringBefore("'!") + "!" + rangeA1.substringAfter("'!")
                    val fallbackUrl = baseHttpUrlBuilder(spreadsheetId)
                        .addPathSegment("values")
                        .addPathSegment(unquoted)
                        .build()
                    val (fbResp, fbToken) = executeWithAuthRetry(
                        Request.Builder().url(fallbackUrl).get(),
                        finalToken
                    )
                    fbResp.use { fb ->
                        val fbBody = fb.body?.string().orEmpty()
                        if (fb.isSuccessful) {
                            return parseValuesResponse(fbBody, fbToken)
                        }
                    }
                }
                throw NonRetryableSyncException(mapHttpFailure(it.code, bodyStr))
            }
            return parseValuesResponse(bodyStr, finalToken)
        }
    }

    private fun parseValuesResponse(bodyStr: String, token: String): ReadValuesOutcome {
        val root = try {
            gson.fromJson(bodyStr, JsonObject::class.java)
        } catch (_: Exception) {
            null
        }
        val jsonValues = root?.getAsJsonArray("values")
        val rows = mutableListOf<List<Any?>>()
        if (jsonValues != null) {
            for (i in 0 until jsonValues.size()) {
                val rowElem = jsonValues.get(i)
                if (rowElem != null && rowElem.isJsonArray) {
                    val rowArr = rowElem.asJsonArray
                    val rowList = mutableListOf<Any?>()
                    for (colIdx in 0 until rowArr.size()) {
                        val elem = rowArr.get(colIdx)
                        if (elem == null || elem.isJsonNull) {
                            rowList.add(null)
                        } else if (elem.isJsonPrimitive) {
                            rowList.add(elem.asString)
                        } else {
                            rowList.add(elem.toString())
                        }
                    }
                    rows.add(rowList)
                }
            }
        }
        return ReadValuesOutcome(rows, token)
    }

    private suspend fun executeWriteCall(builder: Request.Builder, token: String): HttpWriteOutcome {
        return try {
            val (resp, finalToken) = executeWithAuthRetry(builder, token)
            resp.use {
                val bodyStr = it.body?.string().orEmpty()
                if (it.isSuccessful) {
                    HttpWriteOutcome.Success(finalToken)
                } else if (it.code == 429 || it.code in 500..599) {
                    HttpWriteOutcome.Retryable("HTTP ${it.code}: ${extractErrorMessage(bodyStr)}")
                } else {
                    HttpWriteOutcome.Fatal(mapHttpFailure(it.code, bodyStr))
                }
            }
        } catch (e: IOException) {
            HttpWriteOutcome.Retryable(e.localizedMessage ?: "Network error")
        }
    }

    private suspend fun executeWithAuthRetry(
        builder: Request.Builder,
        initialToken: String
    ): Pair<okhttp3.Response, String> {
        val firstReq = builder.header("Authorization", "Bearer $initialToken").build()
        val firstResp = client.newCall(firstReq).execute()
        if (firstResp.code != 401) {
            return firstResp to initialToken
        }

        firstResp.close()
        val refreshed = tryRefreshToken()
        if (refreshed.isNullOrBlank()) {
            val unauthReq = builder.header("Authorization", "Bearer $initialToken").build()
            return client.newCall(unauthReq).execute() to initialToken
        }

        val secondReq = builder.header("Authorization", "Bearer $refreshed").build()
        val secondResp = client.newCall(secondReq).execute()
        return secondResp to refreshed
    }

    private fun mapHttpFailure(statusCode: Int, responseBody: String): CustomPurposesSyncResult {
        val message = extractErrorMessage(responseBody)
        return when (statusCode) {
            401 -> CustomPurposesSyncResult.TokenExpired(message)
            403 -> CustomPurposesSyncResult.AccessDenied(message)
            404 -> CustomPurposesSyncResult.NotFound(message)
            429 -> CustomPurposesSyncResult.RateLimited(message)
            in 500..599 -> CustomPurposesSyncResult.ServerError(statusCode, message)
            else -> CustomPurposesSyncResult.HttpError(statusCode, message)
        }
    }

    private fun extractErrorMessage(responseBody: String): String {
        return try {
            val root = gson.fromJson(responseBody, JsonObject::class.java)
            val error = root.getAsJsonObject("error") ?: return responseBody.take(200)
            error.get("message")?.asString ?: responseBody.take(200)
        } catch (_: Exception) {
            responseBody.take(200).ifBlank { "HTTP request failed" }
        }
    }

    private fun baseHttpUrlBuilder(spreadsheetId: String): HttpUrl.Builder {
        val base = sheetsApiBaseUrl.toHttpUrl()
        return base.newBuilder()
            .addPathSegment("v4")
            .addPathSegment("spreadsheets")
            .addPathSegment(spreadsheetId)
    }

    private fun basePath(spreadsheetId: String): String {
        val baseSegments = sheetsApiBaseUrl.toHttpUrl().encodedPath.trimEnd('/')
        return if (baseSegments.isEmpty()) {
            "/v4/spreadsheets/$spreadsheetId"
        } else {
            "$baseSegments/v4/spreadsheets/$spreadsheetId"
        }
    }

    /**
     * Backs up all custom business purposes to Google Sheets on the 'Custom Purposes' worksheet tab.
     */
    open suspend fun backupCustomPurposes(): CustomPurposesSyncResult = withContext(ioDispatcher) {
        syncMutex.withLock {
            try {
                val config = when (val resolved = resolveAuthorizedConfig()) {
                    is ConfigOutcome.Ready -> resolved
                    is ConfigOutcome.Error -> return@withLock resolved.result
                }

                var token = config.token
                val sheetState = when (val ensureResult = ensureWorksheetAndHeaders(config.spreadsheetId, token)) {
                    is EnsureOutcome.Success -> {
                        token = ensureResult.activeToken
                        ensureResult
                    }
                    is EnsureOutcome.Failure -> {
                        preferencesManager.setCustomPurposesLastBackupError(ensureResult.result.toMessage())
                        return@withLock ensureResult.result
                    }
                }

                val localPurposes = preferencesManager.getCustomPurposesList()
                val currentRowsState = readSheetValues(config.spreadsheetId, token)
                token = currentRowsState.activeToken
                val existingRowsCount = currentRowsState.rows.size

                // If existing rows on sheet exceed new count, clear all data rows first to avoid trailing stale entries
                if (existingRowsCount > localPurposes.size) {
                    val clearOutcome = clearRange(
                        spreadsheetId = config.spreadsheetId,
                        rangeA1 = "'$WORKSHEET_NAME'!A2:B",
                        token = token
                    )
                    when (clearOutcome) {
                        is HttpWriteOutcome.Success -> token = clearOutcome.activeToken
                        is HttpWriteOutcome.Fatal -> {
                            preferencesManager.setCustomPurposesLastBackupError(clearOutcome.result.toMessage())
                            return@withLock clearOutcome.result
                        }
                        is HttpWriteOutcome.Retryable -> {
                            val msg = "Failed to clear stale purposes: ${clearOutcome.message}"
                            preferencesManager.setCustomPurposesLastBackupError(msg)
                            return@withLock CustomPurposesSyncResult.NetworkError(msg)
                        }
                    }
                }

                if (localPurposes.isNotEmpty()) {
                    val timestamp = Instant.now().toString()
                    val rowsToWrite = localPurposes.map { purpose ->
                        listOf(purpose, timestamp)
                    }
                    val writeOutcome = writeRange(
                        spreadsheetId = config.spreadsheetId,
                        rangeA1 = "'$WORKSHEET_NAME'!A2:B${localPurposes.size + 1}",
                        values = rowsToWrite,
                        token = token
                    )
                    when (writeOutcome) {
                        is HttpWriteOutcome.Success -> token = writeOutcome.activeToken
                        is HttpWriteOutcome.Fatal -> {
                            preferencesManager.setCustomPurposesLastBackupError(writeOutcome.result.toMessage())
                            return@withLock writeOutcome.result
                        }
                        is HttpWriteOutcome.Retryable -> {
                            val msg = "Failed to write custom purposes: ${writeOutcome.message}"
                            preferencesManager.setCustomPurposesLastBackupError(msg)
                            return@withLock CustomPurposesSyncResult.NetworkError(msg)
                        }
                    }
                }

                val now = System.currentTimeMillis()
                preferencesManager.setCustomPurposesLastBackupTime(now)
                preferencesManager.setCustomPurposesLastBackupError(null)
                CustomPurposesSyncResult.Success(localPurposes.size, now)
            } catch (e: NonRetryableSyncException) {
                preferencesManager.setCustomPurposesLastBackupError(e.result.toMessage())
                e.result
            } catch (e: IOException) {
                val msg = e.localizedMessage ?: "Network error during Custom Purposes backup"
                Log.w(TAG, "Backup IO error", e)
                preferencesManager.setCustomPurposesLastBackupError(msg)
                CustomPurposesSyncResult.NetworkError(msg)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val msg = e.localizedMessage ?: "Unexpected error during Custom Purposes backup"
                Log.e(TAG, "Backup failed unexpectedly", e)
                preferencesManager.setCustomPurposesLastBackupError(msg)
                CustomPurposesSyncResult.UnexpectedError(msg)
            }
        }
    }

    /**
     * Reads custom business purposes from the spreadsheet (from 'Custom Purposes' tab and/or Sheet1 trips)
     * and merges them into local preferences.
     */
    open suspend fun restoreCustomPurposes(): CustomPurposesRestoreResult = withContext(ioDispatcher) {
        syncMutex.withLock {
            try {
                val config = when (val resolved = resolveAuthorizedConfig()) {
                    is ConfigOutcome.Ready -> resolved
                    is ConfigOutcome.Error -> return@withLock CustomPurposesRestoreResult.NotConfigured
                }

                var token = config.token
                val metaUrl = baseHttpUrlBuilder(config.spreadsheetId)
                    .addQueryParameter("fields", "sheets.properties")
                    .build()

                val (metaResp, tokenAfterMeta) = executeWithAuthRetry(
                    Request.Builder().url(metaUrl).get(),
                    token
                )
                token = tokenAfterMeta

                val allSheets = metaResp.use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        extractSheetProperties(bodyStr)
                    } else emptyList()
                }

                val targetSheet = findCustomPurposesSheet(allSheets)
                val discoveredPurposes = mutableSetOf<String>()

                // 1. Read from dedicated Purposes worksheet (e.g. 'Custom Purposes'!A:B)
                if (targetSheet != null) {
                    try {
                        val rangeA1 = if (targetSheet.title.contains(" ") && !targetSheet.title.startsWith("'")) {
                            "'${targetSheet.title}'!A:B"
                        } else {
                            "${targetSheet.title}!A:B"
                        }
                        val currentRowsState = readRangeSafely(config.spreadsheetId, rangeA1, token)
                        token = currentRowsState.activeToken
                        currentRowsState.rows
                            .mapNotNull { it.firstOrNull()?.toString()?.trim() }
                            .filter { it.isNotEmpty() }
                            .filterNot { it.equals("Purpose", ignoreCase = true) }
                            .filterNot { it.equals("Last Updated", ignoreCase = true) }
                            .filterNot { it.equals("Personal", ignoreCase = true) }
                            .filterNot { it.equals("Unreviewed", ignoreCase = true) }
                            .forEach { discoveredPurposes.add(it) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed reading custom purposes tab: ${e.message}")
                    }
                }

                // 2. Also inspect the primary trips sheet (e.g. 'Sheet1'!A2:K or first sheet) for recorded trip purposes
                val tripSheet = allSheets.firstOrNull { it.title.equals("Sheet1", ignoreCase = true) }
                    ?: allSheets.firstOrNull { it != targetSheet }
                if (tripSheet != null) {
                    try {
                        val tripRange = if (tripSheet.title.contains(" ") && !tripSheet.title.startsWith("'")) {
                            "'${tripSheet.title}'!A2:K"
                        } else {
                            "${tripSheet.title}!A2:K"
                        }
                        val tripRowsState = readRangeSafely(config.spreadsheetId, tripRange, token)
                        token = tripRowsState.activeToken
                        tripRowsState.rows.forEach { row ->
                            // Column K (index 10) is Purpose in Sheet1
                            val purpose = if (row.size > 10) row[10]?.toString()?.trim() else null
                            if (!purpose.isNullOrBlank() &&
                                !purpose.equals("Purpose", ignoreCase = true) &&
                                !purpose.equals("Personal", ignoreCase = true) &&
                                !purpose.equals("Unreviewed", ignoreCase = true)
                            ) {
                                discoveredPurposes.add(purpose)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed reading trips sheet for custom purposes: ${e.message}")
                    }
                }

                val finalPurposesList = discoveredPurposes.toList()
                if (finalPurposesList.isEmpty()) {
                    return@withLock CustomPurposesRestoreResult.Empty("No custom purposes found on Google Sheets.")
                }

                val addedCount = preferencesManager.mergeCustomPurposes(finalPurposesList)
                CustomPurposesRestoreResult.Success(addedCount = addedCount, totalCloudCount = finalPurposesList.size)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Restore failed", e)
                CustomPurposesRestoreResult.Failure(e.localizedMessage ?: "Failed to restore custom purposes")
            }
        }
    }
}
