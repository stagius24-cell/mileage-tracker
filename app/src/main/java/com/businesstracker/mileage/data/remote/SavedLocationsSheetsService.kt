package com.businesstracker.mileage.data.remote

import android.util.Log
import com.businesstracker.mileage.data.local.SavedLocationSyncQueueDao
import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import com.businesstracker.mileage.data.local.entity.SavedLocationSyncQueueEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.security.GoogleAuthManager
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.domain.repository.SavedLocationRepository
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
import kotlin.math.abs

sealed class SavedLocationsSyncResult {
    data class Success(
        val uploadedCount: Int,
        val deletedCount: Int,
        val timestamp: Long
    ) : SavedLocationsSyncResult()

    object NotConfigured : SavedLocationsSyncResult()

    data class PartialSuccess(
        val succeededCount: Int,
        val failedCount: Int,
        val lastError: String
    ) : SavedLocationsSyncResult()

    data class NetworkError(val message: String) : SavedLocationsSyncResult()
    data class TokenExpired(val message: String) : SavedLocationsSyncResult()
    data class AccessDenied(val message: String) : SavedLocationsSyncResult()
    data class NotFound(val message: String) : SavedLocationsSyncResult()
    data class RateLimited(val message: String) : SavedLocationsSyncResult()
    data class ServerError(val statusCode: Int, val message: String) : SavedLocationsSyncResult()
    data class UnsupportedWebhookOnly(val message: String) : SavedLocationsSyncResult()
    data class InvalidConfiguration(val message: String) : SavedLocationsSyncResult()
    data class HttpError(val statusCode: Int, val message: String) : SavedLocationsSyncResult()
    data class UnexpectedError(val message: String) : SavedLocationsSyncResult()
}

enum class RestoreItemStatus {
    NEW_FROM_CLOUD,
    IDENTICAL,
    CLOUD_NEWER,
    LOCAL_NEWER,
    CONFLICT_MODIFIED,
    PREVIOUSLY_DELETED_LOCALLY
}

data class RestoreCandidate(
    val cloudLocation: SavedLocationEntity,
    val localLocation: SavedLocationEntity?,
    val status: RestoreItemStatus,
    val selectedByDefault: Boolean,
    val hasPendingLocalDelete: Boolean
) {
    val requiresOverwriteApproval: Boolean
        get() = status == RestoreItemStatus.CLOUD_NEWER ||
            status == RestoreItemStatus.LOCAL_NEWER ||
            status == RestoreItemStatus.CONFLICT_MODIFIED ||
            status == RestoreItemStatus.PREVIOUSLY_DELETED_LOCALLY
}

data class RestorePreviewData(
    val candidates: List<RestoreCandidate>,
    val localOnlyLocations: List<SavedLocationEntity>,
    val skippedInvalidRows: Int = 0,
    val duplicateCloudIdsMerged: Int = 0
)

sealed class RestorePreviewResult {
    data class Success(val preview: RestorePreviewData) : RestorePreviewResult()
    data class EmptyCloudSheet(val localCount: Int, val message: String) : RestorePreviewResult()
    data class Error(val failure: SavedLocationsSyncResult) : RestorePreviewResult()
}

sealed class RestoreApplyResult {
    data class Success(
        val importedCount: Int,
        val updatedCount: Int,
        val preservedLocalCount: Int
    ) : RestoreApplyResult()

    object NothingSelected : RestoreApplyResult()
    data class Failed(val message: String) : RestoreApplyResult()
}

@Singleton
open class SavedLocationsSheetsService(
    private val preferencesManager: TrackingPreferencesManager,
    private val savedLocationRepository: SavedLocationRepository,
    private val syncQueueDao: SavedLocationSyncQueueDao,
    private val secureStorage: SecureStorageManager,
    private val googleAuthManager: GoogleAuthManager?,
    private val gson: Gson,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    internal var sheetsApiBaseUrl: String = DEFAULT_SHEETS_API_BASE_URL
) {

    @Inject
    constructor(
        preferencesManager: TrackingPreferencesManager,
        savedLocationRepository: SavedLocationRepository,
        syncQueueDao: SavedLocationSyncQueueDao,
        secureStorage: SecureStorageManager,
        googleAuthManager: GoogleAuthManager,
        gson: Gson
    ) : this(
        preferencesManager = preferencesManager,
        savedLocationRepository = savedLocationRepository,
        syncQueueDao = syncQueueDao,
        secureStorage = secureStorage,
        googleAuthManager = googleAuthManager,
        gson = gson,
        ioDispatcher = Dispatchers.IO,
        sheetsApiBaseUrl = DEFAULT_SHEETS_API_BASE_URL
    )

    companion object {
        private const val TAG = "SavedLocationsSheets"
        const val DEFAULT_SHEETS_API_BASE_URL = "https://sheets.googleapis.com/v4/spreadsheets"
        const val WORKSHEET_NAME = "Saved Locations"
        private const val VALUE_INPUT_OPTION = "RAW"

        val EXPECTED_HEADERS = listOf(
            "Location ID",
            "Custom Name",
            "Original Address",
            "Latitude",
            "Longitude",
            "Matching Radius",
            "Icon",
            "Classification Preference",
            "Business Entity",
            "Created At",
            "Updated At"
        )
    }

    internal var client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val syncMutex = Mutex()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private class NonRetryableSyncException(val result: SavedLocationsSyncResult) : Exception(result.toMessage())

    private sealed class SheetReadOutcome {
        data class Loaded(val sheetId: Int, val rows: List<List<Any?>>) : SheetReadOutcome()
        object SheetNotFound : SheetReadOutcome()
        data class Failed(val failure: SavedLocationsSyncResult) : SheetReadOutcome()
    }

    /**
     * Backs up Saved Locations to the "Saved Locations" worksheet.
     *
     * @param fullSnapshot When true ("Back Up Now"), ensures every current local location is
     * synced to Sheets in addition to flushing queued deletions. When false (automatic sync),
     * processes pending outbox operations created by adds/edits/deletions.
     */
    open suspend fun backupSavedLocations(fullSnapshot: Boolean): SavedLocationsSyncResult = withContext(ioDispatcher) {
        syncMutex.withLock {
            try {
                secureStorage
                val tokenResult = resolveAuthorizedConfig()
                val config = when (tokenResult) {
                    is ConfigOutcome.Ready -> tokenResult
                    is ConfigOutcome.Error -> {
                        if (tokenResult.result !is SavedLocationsSyncResult.NotConfigured) {
                            preferencesManager.setSavedLocationsLastBackupError(tokenResult.result.toMessage())
                        }
                        return@withLock tokenResult.result
                    }
                }

                val pendingItems = syncQueueDao.getAllPending()
                val localLocations = savedLocationRepository.getAllLocations()

                if (!fullSnapshot && pendingItems.isEmpty()) {
                    val now = System.currentTimeMillis()
                    preferencesManager.setSavedLocationsLastBackupError(null)
                    return@withLock SavedLocationsSyncResult.Success(0, 0, now)
                }

                val ensured = ensureWorksheetAndHeaders(config.spreadsheetId, config.token)
                val sheetState = when (ensured) {
                    is EnsureOutcome.Success -> ensured
                    is EnsureOutcome.Failure -> {
                        preferencesManager.setSavedLocationsLastBackupError(ensured.result.toMessage())
                        return@withLock ensured.result
                    }
                }

                val currentToken = sheetState.activeToken
                val existingRows = readSheetValues(config.spreadsheetId, currentToken)
                val rowIndexMap = buildIdToRowIndicesMap(existingRows.rows)

                val upsertsById = linkedMapOf<String, SavedLocationEntity>()
                val deletesById = linkedMapOf<String, SavedLocationSyncQueueEntity>()

                // Apply pending queue first to preserve deletion intent
                for (item in pendingItems) {
                    if (item.operation == SavedLocationSyncQueueEntity.OP_DELETE) {
                        deletesById[item.locationId] = item
                    } else if (item.operation == SavedLocationSyncQueueEntity.OP_UPSERT) {
                        val local = localLocations.firstOrNull { it.id == item.locationId }
                            ?: savedLocationRepository.getLocationById(item.locationId)
                        if (local != null) {
                            upsertsById[local.id] = local
                        } else {
                            // Local record removed after UPSERT was queued
                            deletesById[item.locationId] = item.copy(operation = SavedLocationSyncQueueEntity.OP_DELETE)
                        }
                    }
                }

                if (fullSnapshot) {
                    for (local in localLocations) {
                        if (!deletesById.containsKey(local.id)) {
                            upsertsById[local.id] = local
                        }
                    }
                }

                var uploadedCount = 0
                var deletedCount = 0
                var failedCount = 0
                var firstFailureMessage: String? = null
                var token = currentToken
                val duplicateExtraRowsToDelete = mutableListOf<Int>()

                for ((id, location) in upsertsById) {
                    val matchedRows = rowIndexMap[id].orEmpty()
                    val rowValues = entityToRow(location)

                    val opResult = if (matchedRows.isNotEmpty()) {
                        val primaryRow = matchedRows.first()
                        if (matchedRows.size > 1) {
                            duplicateExtraRowsToDelete.addAll(matchedRows.drop(1))
                        }
                        updateRow(config.spreadsheetId, primaryRow, rowValues, token)
                    } else {
                        appendRow(config.spreadsheetId, rowValues, token)
                    }

                    when (opResult) {
                        is HttpWriteOutcome.Success -> {
                            token = opResult.activeToken
                            uploadedCount++
                            // Remove queue entry only if no newer mutation happened concurrently
                            val latestQueue = syncQueueDao.getPendingById(id)
                            if (latestQueue != null && latestQueue.operation == SavedLocationSyncQueueEntity.OP_UPSERT && latestQueue.updatedAt <= location.updatedAt) {
                                syncQueueDao.deletePendingById(id)
                            }
                        }
                        is HttpWriteOutcome.Fatal -> {
                            val totalSucceeded = uploadedCount + deletedCount
                            if (totalSucceeded > 0) {
                                val partial = SavedLocationsSyncResult.PartialSuccess(
                                    succeededCount = totalSucceeded,
                                    failedCount = failedCount + 1,
                                    lastError = opResult.result.toMessage()
                                )
                                preferencesManager.setSavedLocationsLastBackupError(partial.lastError)
                                return@withLock partial
                            }
                            preferencesManager.setSavedLocationsLastBackupError(opResult.result.toMessage())
                            return@withLock opResult.result
                        }
                        is HttpWriteOutcome.Retryable -> {
                            failedCount++
                            if (firstFailureMessage == null) {
                                firstFailureMessage = opResult.message
                            }
                        }
                    }
                }

                // Determine row numbers to delete from cloud for explicit deletes + duplicate cleanup
                val rowsForDeletion = linkedSetOf<Int>()
                val deletedIdsToClear = mutableListOf<String>()
                for ((id, queueItem) in deletesById) {
                    val matched = rowIndexMap[id].orEmpty()
                    if (matched.isEmpty()) {
                        // Already absent from sheet; safe to clear queue item
                        val latest = syncQueueDao.getPendingById(id)
                        if (latest == null || (latest.operation == SavedLocationSyncQueueEntity.OP_DELETE && latest.updatedAt <= queueItem.updatedAt)) {
                            syncQueueDao.deletePendingById(id)
                        } else if (queueItem.operation == SavedLocationSyncQueueEntity.OP_DELETE) {
                            syncQueueDao.deletePendingById(id)
                        }
                    } else {
                        rowsForDeletion.addAll(matched)
                        deletedIdsToClear.add(id)
                    }
                }
                rowsForDeletion.addAll(duplicateExtraRowsToDelete)

                if (rowsForDeletion.isNotEmpty()) {
                    val deleteOutcome = deleteRowsDescending(
                        spreadsheetId = config.spreadsheetId,
                        sheetId = sheetState.sheetId,
                        oneBasedRowNumbers = rowsForDeletion,
                        token = token
                    )
                    when (deleteOutcome) {
                        is HttpWriteOutcome.Success -> {
                            token = deleteOutcome.activeToken
                            deletedCount += deletedIdsToClear.size
                            for (deletedId in deletedIdsToClear) {
                                syncQueueDao.deletePendingById(deletedId)
                            }
                        }
                        is HttpWriteOutcome.Fatal -> {
                            val totalSucceeded = uploadedCount + deletedCount
                            val errMsg = deleteOutcome.result.toMessage()
                            preferencesManager.setSavedLocationsLastBackupError(errMsg)
                            return@withLock if (totalSucceeded > 0) {
                                SavedLocationsSyncResult.PartialSuccess(totalSucceeded, failedCount + deletedIdsToClear.size, errMsg)
                            } else {
                                deleteOutcome.result
                            }
                        }
                        is HttpWriteOutcome.Retryable -> {
                            failedCount += deletedIdsToClear.size
                            if (firstFailureMessage == null) {
                                firstFailureMessage = deleteOutcome.message
                            }
                        }
                    }
                }

                val now = System.currentTimeMillis()
                if (failedCount > 0) {
                    val msg = firstFailureMessage ?: "Failed to back up some Saved Locations"
                    val totalSucceeded = uploadedCount + deletedCount
                    preferencesManager.setSavedLocationsLastBackupError(msg)
                    return@withLock if (totalSucceeded > 0) {
                        SavedLocationsSyncResult.PartialSuccess(totalSucceeded, failedCount, msg)
                    } else {
                        SavedLocationsSyncResult.NetworkError(msg)
                    }
                }

                preferencesManager.setSavedLocationsLastBackupTime(now)
                preferencesManager.setSavedLocationsLastBackupError(null)
                SavedLocationsSyncResult.Success(uploadedCount, deletedCount, now)
            } catch (e: NonRetryableSyncException) {
                preferencesManager.setSavedLocationsLastBackupError(e.result.toMessage())
                e.result
            } catch (e: IOException) {
                val msg = e.localizedMessage ?: "Network error during Saved Locations backup"
                Log.w(TAG, "Backup IO error", e)
                preferencesManager.setSavedLocationsLastBackupError(msg)
                SavedLocationsSyncResult.NetworkError(msg)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val msg = e.localizedMessage ?: "Unexpected error during Saved Locations backup"
                Log.e(TAG, "Backup failed unexpectedly", e)
                preferencesManager.setSavedLocationsLastBackupError(msg)
                SavedLocationsSyncResult.UnexpectedError(msg)
            }
        }
    }

    /**
     * Reads "Saved Locations" from Google Sheets and builds a safe preview comparing cloud rows
     * against local Room records and pending local deletions without mutating local data.
     */
    open suspend fun previewRestoreFromSheets(): RestorePreviewResult = withContext(ioDispatcher) {
        syncMutex.withLock {
            try {
                val localLocations = savedLocationRepository.getAllLocations()
                val config = when (val resolved = resolveAuthorizedConfig()) {
                    is ConfigOutcome.Ready -> resolved
                    is ConfigOutcome.Error -> {
                        val failure = if (resolved.result is SavedLocationsSyncResult.NotConfigured) {
                            SavedLocationsSyncResult.InvalidConfiguration("Google Sheets is not connected.")
                        } else {
                            resolved.result
                        }
                        return@withLock RestorePreviewResult.Error(failure)
                    }
                }

                when (val outcome = readWorksheetForRestore(config.spreadsheetId, config.token)) {
                    is SheetReadOutcome.SheetNotFound -> {
                        RestorePreviewResult.EmptyCloudSheet(
                            localCount = localLocations.size,
                            message = "Worksheet '$WORKSHEET_NAME' was not found in this spreadsheet. Local Saved Locations (${localLocations.size}) remain unchanged."
                        )
                    }
                    is SheetReadOutcome.Failed -> RestorePreviewResult.Error(outcome.failure)
                    is SheetReadOutcome.Loaded -> {
                        val parsed = parseCloudRows(outcome.rows)
                        if (parsed.locations.isEmpty()) {
                            if (parsed.skippedInvalidRows > 0 && parsed.totalDataRows > 0) {
                                return@withLock RestorePreviewResult.Error(
                                    SavedLocationsSyncResult.UnexpectedError(
                                        "Could not parse any valid Saved Locations from '$WORKSHEET_NAME' (${parsed.skippedInvalidRows} invalid rows). Local records were left untouched."
                                    )
                                )
                            }
                            return@withLock RestorePreviewResult.EmptyCloudSheet(
                                localCount = localLocations.size,
                                message = "The '$WORKSHEET_NAME' worksheet has no Saved Locations yet. Local Saved Locations (${localLocations.size}) remain unchanged."
                            )
                        }

                        val localById = localLocations.associateBy { it.id }
                        val pendingById = syncQueueDao.getAllPending().associateBy { it.locationId }

                        val candidates = mutableListOf<RestoreCandidate>()
                        for (cloudLoc in parsed.locations) {
                            val localLoc = localById[cloudLoc.id]
                            val pending = pendingById[cloudLoc.id]
                            val hasPendingDelete = pending?.operation == SavedLocationSyncQueueEntity.OP_DELETE

                            val (status, selectedByDefault) = when {
                                hasPendingDelete && localLoc == null -> {
                                    RestoreItemStatus.PREVIOUSLY_DELETED_LOCALLY to false
                                }
                                localLoc == null -> {
                                    RestoreItemStatus.NEW_FROM_CLOUD to true
                                }
                                hasSameLocationData(localLoc, cloudLoc) -> {
                                    RestoreItemStatus.IDENTICAL to false
                                }
                                pending?.operation == SavedLocationSyncQueueEntity.OP_UPSERT ||
                                    localLoc.updatedAt > cloudLoc.updatedAt -> {
                                    RestoreItemStatus.LOCAL_NEWER to false
                                }
                                cloudLoc.updatedAt > localLoc.updatedAt -> {
                                    RestoreItemStatus.CLOUD_NEWER to false
                                }
                                else -> {
                                    RestoreItemStatus.CONFLICT_MODIFIED to false
                                }
                            }

                            candidates.add(
                                RestoreCandidate(
                                    cloudLocation = cloudLoc,
                                    localLocation = localLoc,
                                    status = status,
                                    selectedByDefault = selectedByDefault,
                                    hasPendingLocalDelete = hasPendingDelete
                                )
                            )
                        }

                        val cloudIds = parsed.locations.map { it.id }.toSet()
                        val localOnly = localLocations.filter { it.id !in cloudIds }

                        RestorePreviewResult.Success(
                            RestorePreviewData(
                                candidates = candidates,
                                localOnlyLocations = localOnly,
                                skippedInvalidRows = parsed.skippedInvalidRows,
                                duplicateCloudIdsMerged = parsed.duplicateCount
                            )
                        )
                    }
                }
            } catch (e: IOException) {
                RestorePreviewResult.Error(
                    SavedLocationsSyncResult.NetworkError(
                        e.localizedMessage ?: "Network error while reading Saved Locations"
                    )
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                RestorePreviewResult.Error(
                    SavedLocationsSyncResult.UnexpectedError(
                        e.localizedMessage ?: "Failed to load Saved Locations preview"
                    )
                )
            }
        }
    }

    /**
     * Applies user-confirmed restore choices into Room without deleting local-only records or
     * overwriting conflicting/deleted items unless explicitly approved in [approvedConflictIds].
     */
    open suspend fun applyRestoreSelection(
        preview: RestorePreviewData,
        selectedCandidateIds: Set<String>,
        approvedConflictIds: Set<String> = emptySet()
    ): RestoreApplyResult = withContext(ioDispatcher) {
        if (selectedCandidateIds.isEmpty()) {
            return@withContext RestoreApplyResult.NothingSelected
        }

        val toSave = mutableListOf<SavedLocationEntity>()
        val deleteQueueToClear = mutableSetOf<String>()
        var importedCount = 0
        var updatedCount = 0
        var preservedLocalCount = preview.localOnlyLocations.size

        for (candidate in preview.candidates) {
            val id = candidate.cloudLocation.id
            if (candidate.status == RestoreItemStatus.IDENTICAL) {
                continue
            }

            if (id !in selectedCandidateIds) {
                if (candidate.localLocation != null) {
                    preservedLocalCount++
                }
                continue
            }

            if (candidate.requiresOverwriteApproval && id !in approvedConflictIds) {
                if (candidate.localLocation != null) {
                    preservedLocalCount++
                }
                continue
            }

            toSave.add(candidate.cloudLocation)
            if (candidate.hasPendingLocalDelete) {
                deleteQueueToClear.add(id)
            }
            if (candidate.localLocation == null) {
                importedCount++
            } else {
                updatedCount++
            }
        }

        if (toSave.isEmpty()) {
            return@withContext RestoreApplyResult.NothingSelected
        }

        return@withContext try {
            savedLocationRepository.applyRestoredLocations(
                locationsToSave = toSave,
                pendingDeleteIdsToClear = deleteQueueToClear
            )
            RestoreApplyResult.Success(
                importedCount = importedCount,
                updatedCount = updatedCount,
                preservedLocalCount = preservedLocalCount
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            RestoreApplyResult.Failed(e.localizedMessage ?: "Failed to save restored locations")
        }
    }

    private sealed class ConfigOutcome {
        data class Ready(val spreadsheetId: String, val token: String) : ConfigOutcome()
        data class Error(val result: SavedLocationsSyncResult) : ConfigOutcome()
    }

    private suspend fun resolveAuthorizedConfig(): ConfigOutcome {
        preferencesManager.migrateLegacyAuthToken(secureStorage)

        val isConnected = preferencesManager.isSheetsConnected.first()
        if (!isConnected) {
            return ConfigOutcome.Error(SavedLocationsSyncResult.NotConfigured)
        }

        val spreadsheetId = preferencesManager.sheetsSpreadsheetId.first().trim()
        val webhookUrl = preferencesManager.sheetsWebhookUrl.first().trim()

        if (spreadsheetId.isBlank()) {
            return if (webhookUrl.isNotBlank()) {
                ConfigOutcome.Error(
                    SavedLocationsSyncResult.UnsupportedWebhookOnly(
                        "Apps Script Webhook mode only supports write-only trip uploads. Connect with Google OAuth + Spreadsheet ID to back up and restore Saved Locations."
                    )
                )
            } else {
                ConfigOutcome.Error(
                    SavedLocationsSyncResult.InvalidConfiguration(
                        "Spreadsheet ID is missing. Reconnect Google Sheets in Settings."
                    )
                )
            }
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
                SavedLocationsSyncResult.TokenExpired(
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
        data class Failure(val result: SavedLocationsSyncResult) : EnsureOutcome()
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

        var sheetId: Int? = null
        metaResp.use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return EnsureOutcome.Failure(mapHttpFailure(resp.code, bodyStr))
            }
            sheetId = findWorksheetId(bodyStr, WORKSHEET_NAME)
        }

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
            SavedLocationsSyncResult.UnexpectedError("Unable to resolve worksheet ID for '$WORKSHEET_NAME'")
        )

        // Ensure header row A1:K1 exists and matches schema
        val valuesState = readSheetValues(spreadsheetId, token)
        token = valuesState.activeToken
        val currentHeader = valuesState.rows.firstOrNull().orEmpty().map { it?.toString()?.trim().orEmpty() }
        if (currentHeader != EXPECTED_HEADERS) {
            val headerWrite = writeRange(
                spreadsheetId = spreadsheetId,
                rangeA1 = "'$WORKSHEET_NAME'!A1:K1",
                values = listOf(EXPECTED_HEADERS),
                token = token
            )
            when (headerWrite) {
                is HttpWriteOutcome.Success -> token = headerWrite.activeToken
                is HttpWriteOutcome.Fatal -> return EnsureOutcome.Failure(headerWrite.result)
                is HttpWriteOutcome.Retryable -> return EnsureOutcome.Failure(
                    SavedLocationsSyncResult.NetworkError(headerWrite.message)
                )
            }
        }

        return EnsureOutcome.Success(resolvedSheetId, token)
    }

    private suspend fun readWorksheetForRestore(spreadsheetId: String, initialToken: String): SheetReadOutcome {
        var token = initialToken
        val metaUrl = baseHttpUrlBuilder(spreadsheetId)
            .addQueryParameter("fields", "sheets.properties")
            .build()
        val (metaResp, tokenAfterMeta) = executeWithAuthRetry(
            Request.Builder().url(metaUrl).get(),
            token
        )
        token = tokenAfterMeta

        var sheetId: Int? = null
        metaResp.use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return SheetReadOutcome.Failed(mapHttpFailure(resp.code, bodyStr))
            }
            sheetId = findWorksheetId(bodyStr, WORKSHEET_NAME)
        }

        val resolvedSheetId = sheetId ?: return SheetReadOutcome.SheetNotFound
        val valuesState = readSheetValues(spreadsheetId, token)
        return SheetReadOutcome.Loaded(resolvedSheetId, valuesState.rows)
    }

    private data class ReadValuesOutcome(val rows: List<List<Any?>>, val activeToken: String)

    private suspend fun readSheetValues(spreadsheetId: String, token: String): ReadValuesOutcome {
        val valuesUrl = baseHttpUrlBuilder(spreadsheetId)
            .addPathSegment("values")
            .addPathSegment("'$WORKSHEET_NAME'!A:K")
            .addQueryParameter("valueRenderOption", "UNFORMATTED_VALUE")
            .build()

        val (resp, finalToken) = executeWithAuthRetry(
            Request.Builder().url(valuesUrl).get(),
            token
        )
        resp.use {
            val bodyStr = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                throw NonRetryableSyncException(mapHttpFailure(it.code, bodyStr))
            }
            val parsed = gson.fromJson(bodyStr, ValueRangeResponse::class.java)
                ?: throw IOException("Empty or malformed JSON response from Google Sheets")
            if (parsed.range == null && parsed.values == null) {
                throw IOException("Incomplete response payload from Google Sheets")
            }
            return ReadValuesOutcome(parsed.values ?: emptyList(), finalToken)
        }
    }

    private sealed class HttpWriteOutcome {
        data class Success(val activeToken: String) : HttpWriteOutcome()
        data class Fatal(val result: SavedLocationsSyncResult) : HttpWriteOutcome()
        data class Retryable(val message: String) : HttpWriteOutcome()
    }

    private suspend fun updateRow(
        spreadsheetId: String,
        oneBasedRow: Int,
        rowValues: List<Any>,
        token: String
    ): HttpWriteOutcome {
        return writeRange(
            spreadsheetId = spreadsheetId,
            rangeA1 = "'$WORKSHEET_NAME'!A$oneBasedRow:K$oneBasedRow",
            values = listOf(rowValues),
            token = token
        )
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

    private suspend fun appendRow(
        spreadsheetId: String,
        rowValues: List<Any>,
        token: String
    ): HttpWriteOutcome {
        val url = baseHttpUrlBuilder(spreadsheetId)
            .addPathSegment("values")
            .addPathSegment("'$WORKSHEET_NAME'!A:K")
            .encodedPath(
                baseHttpUrlBuilder(spreadsheetId)
                    .addPathSegment("values")
                    .addPathSegment("'$WORKSHEET_NAME'!A:K")
                    .build()
                    .encodedPath + ":append"
            )
            .addQueryParameter("valueInputOption", VALUE_INPUT_OPTION)
            .addQueryParameter("insertDataOption", "INSERT_ROWS")
            .build()
        val payload = gson.toJson(mapOf("values" to listOf(rowValues)))
        return executeWriteCall(
            Request.Builder().url(url).post(payload.toRequestBody(jsonMediaType)),
            token
        )
    }

    private suspend fun deleteRowsDescending(
        spreadsheetId: String,
        sheetId: Int,
        oneBasedRowNumbers: Collection<Int>,
        token: String
    ): HttpWriteOutcome {
        val sortedDesc = oneBasedRowNumbers.filter { it >= 2 }.distinct().sortedDescending()
        if (sortedDesc.isEmpty()) {
            return HttpWriteOutcome.Success(token)
        }

        val requests = JsonArray()
        for (row in sortedDesc) {
            requests.add(JsonObject().apply {
                add("deleteDimension", JsonObject().apply {
                    add("range", JsonObject().apply {
                        addProperty("sheetId", sheetId)
                        addProperty("dimension", "ROWS")
                        addProperty("startIndex", row - 1)
                        addProperty("endIndex", row)
                    })
                })
            })
        }
        val bodyJson = JsonObject().apply { add("requests", requests) }
        val batchUrl = baseHttpUrlBuilder(spreadsheetId)
            .encodedPath(basePath(spreadsheetId) + ":batchUpdate")
            .build()
        return executeWriteCall(
            Request.Builder().url(batchUrl).post(gson.toJson(bodyJson).toRequestBody(jsonMediaType)),
            token
        )
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

        val retryReq = builder.header("Authorization", "Bearer $refreshed").build()
        return client.newCall(retryReq).execute() to refreshed
    }

    private fun baseHttpUrlBuilder(spreadsheetId: String): HttpUrl.Builder {
        return sheetsApiBaseUrl.trimEnd('/').toHttpUrl().newBuilder()
            .addPathSegment(spreadsheetId)
    }

    private fun basePath(spreadsheetId: String): String {
        return baseHttpUrlBuilder(spreadsheetId).build().encodedPath
    }

    private fun entityToRow(location: SavedLocationEntity): List<Any> {
        return listOf(
            location.id,
            location.name,
            location.address,
            location.latitude,
            location.longitude,
            location.radiusMeters,
            location.icon,
            location.classificationPreference ?: "",
            location.businessEntity ?: "",
            Instant.ofEpochMilli(location.createdAt).toString(),
            Instant.ofEpochMilli(location.updatedAt).toString()
        )
    }

    private fun buildIdToRowIndicesMap(rows: List<List<Any?>>): Map<String, List<Int>> {
        val map = linkedMapOf<String, MutableList<Int>>()
        rows.forEachIndexed { zeroIndex, row ->
            if (zeroIndex == 0) return@forEachIndexed
            val id = row.firstOrNull()?.toString()?.trim().orEmpty()
            if (id.isNotBlank()) {
                map.getOrPut(id) { mutableListOf() }.add(zeroIndex + 1)
            }
        }
        return map
    }

    private data class ParsedRows(
        val locations: List<SavedLocationEntity>,
        val skippedInvalidRows: Int,
        val duplicateCount: Int,
        val totalDataRows: Int
    )

    private fun parseCloudRows(rows: List<List<Any?>>): ParsedRows {
        if (rows.isEmpty()) {
            return ParsedRows(emptyList(), 0, 0, 0)
        }

        val startIndex = if (rows.first().firstOrNull()?.toString()?.trim()?.equals("Location ID", ignoreCase = true) == true) {
            1
        } else {
            0
        }

        val byId = linkedMapOf<String, SavedLocationEntity>()
        var skipped = 0
        var duplicates = 0
        var totalDataRows = 0

        for (i in startIndex until rows.size) {
            val row = rows[i]
            if (row.all { it == null || it.toString().isBlank() }) {
                continue
            }
            totalDataRows++
            val parsed = parseRowToEntity(row)
            if (parsed == null) {
                skipped++
                continue
            }
            val existing = byId[parsed.id]
            if (existing == null) {
                byId[parsed.id] = parsed
            } else {
                duplicates++
                if (parsed.updatedAt >= existing.updatedAt) {
                    byId[parsed.id] = parsed
                }
            }
        }

        return ParsedRows(
            locations = byId.values.toList(),
            skippedInvalidRows = skipped,
            duplicateCount = duplicates,
            totalDataRows = totalDataRows
        )
    }

    private fun parseRowToEntity(row: List<Any?>): SavedLocationEntity? {
        val id = row.getOrNull(0)?.toString()?.trim().orEmpty()
        val name = row.getOrNull(1)?.toString()?.trim().orEmpty()
        val address = row.getOrNull(2)?.toString()?.trim().orEmpty()
        val lat = parseDoubleValue(row.getOrNull(3))
        val lon = parseDoubleValue(row.getOrNull(4))
        val radius = parseIntValue(row.getOrNull(5)) ?: 150
        val icon = row.getOrNull(6)?.toString()?.trim().takeUnless { it.isNullOrBlank() } ?: "location"
        val classificationPref = row.getOrNull(7)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val businessEntity = row.getOrNull(8)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val now = System.currentTimeMillis()
        val createdAt = parseTimestamp(row.getOrNull(9)) ?: now
        val updatedAt = parseTimestamp(row.getOrNull(10)) ?: createdAt

        if (id.isBlank() || name.isBlank() || lat == null || lon == null) {
            return null
        }
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            return null
        }

        return SavedLocationEntity(
            id = id,
            name = name,
            latitude = lat,
            longitude = lon,
            address = address,
            radiusMeters = radius.coerceIn(50, 500),
            icon = icon,
            classificationPreference = classificationPref,
            businessEntity = if (classificationPref == "BUSINESS") businessEntity else null,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun parseDoubleValue(raw: Any?): Double? {
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.trim().toDoubleOrNull()
            else -> null
        }
    }

    private fun parseIntValue(raw: Any?): Int? {
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.trim().toDoubleOrNull()?.toInt()
            else -> null
        }
    }

    private fun parseTimestamp(raw: Any?): Long? {
        return when (raw) {
            is Number -> raw.toLong()
            is String -> {
                val trimmed = raw.trim()
                if (trimmed.isBlank()) return null
                trimmed.toLongOrNull() ?: runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()
            }
            else -> null
        }
    }

    private fun hasSameLocationData(a: SavedLocationEntity, b: SavedLocationEntity): Boolean {
        return a.name == b.name &&
            a.address == b.address &&
            abs(a.latitude - b.latitude) < 1e-6 &&
            abs(a.longitude - b.longitude) < 1e-6 &&
            a.radiusMeters == b.radiusMeters &&
            a.icon == b.icon &&
            a.classificationPreference == b.classificationPreference &&
            a.businessEntity == b.businessEntity
    }

    private fun findWorksheetId(metaBody: String, title: String): Int? {
        val root = gson.fromJson(metaBody, JsonObject::class.java) ?: return null
        val sheets = root.getAsJsonArray("sheets") ?: return null
        for (el in sheets) {
            val props = el.asJsonObject.getAsJsonObject("properties") ?: continue
            val sheetTitle = props.get("title")?.asString
            if (sheetTitle == title) {
                return props.get("sheetId")?.asInt
            }
        }
        return null
    }

    private fun extractAddedSheetId(batchResponseBody: String): Int? {
        val root = gson.fromJson(batchResponseBody, JsonObject::class.java) ?: return null
        val replies = root.getAsJsonArray("replies") ?: return null
        val first = replies.firstOrNull()?.asJsonObject ?: return null
        val addSheet = first.getAsJsonObject("addSheet") ?: return null
        return addSheet.getAsJsonObject("properties")?.get("sheetId")?.asInt
    }

    private fun mapHttpFailure(code: Int, bodyStr: String): SavedLocationsSyncResult {
        val detail = extractErrorMessage(bodyStr)
        return when (code) {
            401 -> SavedLocationsSyncResult.TokenExpired("Google authentication expired. Please reconnect your account.")
            403 -> SavedLocationsSyncResult.AccessDenied("Permission denied for spreadsheet access. $detail".trim())
            404 -> SavedLocationsSyncResult.NotFound("Connected Google Spreadsheet was not found. $detail".trim())
            429 -> SavedLocationsSyncResult.RateLimited("Google Sheets rate limit exceeded. Will retry shortly.")
            in 500..599 -> SavedLocationsSyncResult.ServerError(code, "Google Sheets server error ($code). Will retry shortly.")
            else -> SavedLocationsSyncResult.HttpError(code, "Google Sheets error ($code): $detail")
        }
    }

    private fun extractErrorMessage(body: String): String {
        return try {
            val obj = gson.fromJson(body, JsonObject::class.java)
            obj?.getAsJsonObject("error")?.get("message")?.asString ?: body.take(160)
        } catch (_: Exception) {
            body.take(160)
        }
    }

    private data class ValueRangeResponse(
        val range: String? = null,
        val values: List<List<Any?>>? = null
    )
}

fun SavedLocationsSyncResult.toMessage(): String = when (this) {
    is SavedLocationsSyncResult.Success -> "Backed up $uploadedCount updated, $deletedCount removed"
    is SavedLocationsSyncResult.NotConfigured -> "Google Sheets is not connected"
    is SavedLocationsSyncResult.PartialSuccess -> "Backed up $succeededCount items; $failedCount failed: $lastError"
    is SavedLocationsSyncResult.NetworkError -> message
    is SavedLocationsSyncResult.TokenExpired -> message
    is SavedLocationsSyncResult.AccessDenied -> message
    is SavedLocationsSyncResult.NotFound -> message
    is SavedLocationsSyncResult.RateLimited -> message
    is SavedLocationsSyncResult.ServerError -> message
    is SavedLocationsSyncResult.UnsupportedWebhookOnly -> message
    is SavedLocationsSyncResult.InvalidConfiguration -> message
    is SavedLocationsSyncResult.HttpError -> message
    is SavedLocationsSyncResult.UnexpectedError -> message
}
