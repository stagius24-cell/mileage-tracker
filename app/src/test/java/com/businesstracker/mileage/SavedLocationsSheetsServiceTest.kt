package com.businesstracker.mileage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import com.businesstracker.mileage.data.local.entity.SavedLocationSyncQueueEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.remote.RestoreApplyResult
import com.businesstracker.mileage.data.remote.RestoreItemStatus
import com.businesstracker.mileage.data.remote.RestorePreviewResult
import com.businesstracker.mileage.data.remote.SavedLocationsSheetsService
import com.businesstracker.mileage.data.remote.SavedLocationsSyncResult
import com.businesstracker.mileage.data.repository.SavedLocationRepositoryImpl
import com.businesstracker.mileage.data.security.GoogleAuthManager
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.data.security.TestSoftwareKeyProvider
import com.businesstracker.mileage.domain.repository.SavedLocationBackupScheduler
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
class SavedLocationsSheetsServiceTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var secureStorageManager: SecureStorageManager
    private lateinit var repository: SavedLocationRepositoryImpl
    private val gson = Gson()
    private var scheduledBackupCalls = 0
    private val scheduler = object : SavedLocationBackupScheduler {
        override fun scheduleAutoBackup() {
            scheduledBackupCalls++
        }
    }

    private val secretToken = "secret_oauth_token_999"

    private class RecordedHttpCall(
        val method: String,
        val url: String,
        val authHeader: String?,
        val body: String
    )

    private class FakeSheetsInterceptor(
        private val gson: Gson,
        private val overrideHandler: ((Request, String, List<RecordedHttpCall>) -> Response?)? = null
    ) : okhttp3.Interceptor {
        val sheets = linkedMapOf<String, Int>()
        val sheetValues = linkedMapOf<String, MutableList<MutableList<Any?>>>()
        val calls = mutableListOf<RecordedHttpCall>()
        private var nextSheetId = 2001

        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            val req = chain.request()
            val bodyStr = Buffer().also { req.body?.writeTo(it) }.readUtf8()
            val recorded = RecordedHttpCall(
                method = req.method,
                url = req.url.toString(),
                authHeader = req.header("Authorization"),
                body = bodyStr
            )
            calls.add(recorded)

            overrideHandler?.invoke(req, bodyStr, calls)?.let { return it }

            val decodedUrl = URLDecoder.decode(req.url.toString(), StandardCharsets.UTF_8.name())

            // GET spreadsheet metadata
            if (req.method == "GET" && decodedUrl.contains("fields=sheets.properties")) {
                val arr = JsonArray()
                for ((title, id) in sheets) {
                    arr.add(JsonObject().apply {
                        add("properties", JsonObject().apply {
                            addProperty("sheetId", id)
                            addProperty("title", title)
                        })
                    })
                }
                return jsonResponse(req, 200, gson.toJson(mapOf("sheets" to arr)))
            }

            // POST :batchUpdate
            if (req.method == "POST" && decodedUrl.contains(":batchUpdate")) {
                val json = gson.fromJson(bodyStr, JsonObject::class.java)
                val reqArray = json.getAsJsonArray("requests")
                val replies = JsonArray()
                for (el in reqArray) {
                    val obj = el.asJsonObject
                    when {
                        obj.has("addSheet") -> {
                            val title = obj.getAsJsonObject("addSheet")
                                .getAsJsonObject("properties")
                                .get("title").asString
                            val newId = nextSheetId++
                            sheets[title] = newId
                            sheetValues.getOrPut(title) { mutableListOf() }
                            replies.add(JsonObject().apply {
                                add("addSheet", JsonObject().apply {
                                    add("properties", JsonObject().apply {
                                        addProperty("sheetId", newId)
                                        addProperty("title", title)
                                    })
                                })
                            })
                        }
                        obj.has("deleteDimension") -> {
                            val range = obj.getAsJsonObject("deleteDimension").getAsJsonObject("range")
                            val targetSheetId = range.get("sheetId").asInt
                            val startIdx = range.get("startIndex").asInt
                            val endIdx = range.get("endIndex").asInt
                            val title = sheets.entries.firstOrNull { it.value == targetSheetId }?.key
                            if (title != null) {
                                val rows = sheetValues[title]
                                if (rows != null) {
                                    for (idx in (endIdx - 1) downTo startIdx) {
                                        if (idx in rows.indices) {
                                            rows.removeAt(idx)
                                        }
                                    }
                                }
                            }
                            replies.add(JsonObject())
                        }
                    }
                }
                return jsonResponse(req, 200, gson.toJson(mapOf("replies" to replies)))
            }

            // GET values
            if (req.method == "GET" && decodedUrl.contains("/values/")) {
                val sheetTitle = extractSheetTitle(decodedUrl)
                val rows = sheetValues[sheetTitle] ?: mutableListOf()
                return jsonResponse(
                    req,
                    200,
                    gson.toJson(mapOf("range" to "'$sheetTitle'!A1:K1000", "values" to rows))
                )
            }

            // POST :append
            if (req.method == "POST" && decodedUrl.contains(":append")) {
                val sheetTitle = extractSheetTitle(decodedUrl)
                val rows = sheetValues.getOrPut(sheetTitle) { mutableListOf() }
                val payload = gson.fromJson(bodyStr, JsonObject::class.java)
                val newRows = payload.getAsJsonArray("values")
                for (r in newRows) {
                    val rowList = mutableListOf<Any?>()
                    for (cell in r.asJsonArray) {
                        rowList.add(jsonElementToAny(cell))
                    }
                    rows.add(rowList)
                }
                return jsonResponse(req, 200, gson.toJson(mapOf("updates" to mapOf("updatedRows" to newRows.size()))))
            }

            // PUT values (header or row update)
            if (req.method == "PUT" && decodedUrl.contains("/values/")) {
                val sheetTitle = extractSheetTitle(decodedUrl)
                val rowNum = extractTargetRowNumber(decodedUrl)
                val rows = sheetValues.getOrPut(sheetTitle) { mutableListOf() }
                val payload = gson.fromJson(bodyStr, JsonObject::class.java)
                val newRows = payload.getAsJsonArray("values")
                val rowValues = mutableListOf<Any?>()
                if (newRows.size() > 0) {
                    for (cell in newRows[0].asJsonArray) {
                        rowValues.add(jsonElementToAny(cell))
                    }
                }
                while (rows.size < rowNum) {
                    rows.add(mutableListOf())
                }
                rows[rowNum - 1] = rowValues
                return jsonResponse(req, 200, gson.toJson(mapOf("updatedRows" to 1)))
            }

            return jsonResponse(req, 404, """{"error":{"message":"Unhandled mock route: $decodedUrl"}}""")
        }

        private fun extractSheetTitle(decodedUrl: String): String {
            val match = Regex("'([^']+)'!").find(decodedUrl)
            return match?.groupValues?.get(1) ?: SavedLocationsSheetsService.WORKSHEET_NAME
        }

        private fun extractTargetRowNumber(decodedUrl: String): Int {
            val match = Regex("!A(\\d+):").find(decodedUrl)
            return match?.groupValues?.get(1)?.toIntOrNull() ?: 1
        }

        private fun jsonElementToAny(el: com.google.gson.JsonElement): Any? {
            return when {
                el.isJsonNull -> null
                el.isJsonPrimitive -> {
                    val p = el.asJsonPrimitive
                    when {
                        p.isNumber -> p.asDouble
                        p.isBoolean -> p.asBoolean
                        else -> p.asString
                    }
                }
                else -> el.toString()
            }
        }

        private fun jsonResponse(req: Request, code: Int, json: String): Response {
            return Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Error")
                .body(json.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }
    }

    @Before
    fun setUp() {
        scheduledBackupCalls = 0
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferencesManager = TrackingPreferencesManager(context)
        secureStorageManager = SecureStorageManager(context, TestSoftwareKeyProvider())
        secureStorageManager.clearAllCredentials()
        repository = SavedLocationRepositoryImpl(
            savedLocationDao = database.savedLocationDao(),
            ioDispatcher = Dispatchers.IO,
            database = database,
            syncQueueDao = database.savedLocationSyncQueueDao(),
            backupScheduler = scheduler
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun configureOAuthSheets(sheetId: String = "sheet_abc_123", token: String = secretToken) {
        preferencesManager.setSheetsSpreadsheetId(sheetId)
        preferencesManager.setSheetsWebhookUrl("")
        secureStorageManager.saveOAuthToken(token)
        preferencesManager.setSheetsConnected(true)
    }

    private fun createService(
        interceptor: okhttp3.Interceptor,
        authManager: GoogleAuthManager? = null
    ): SavedLocationsSheetsService {
        return SavedLocationsSheetsService(
            preferencesManager = preferencesManager,
            savedLocationRepository = repository,
            syncQueueDao = database.savedLocationSyncQueueDao(),
            secureStorage = secureStorageManager,
            googleAuthManager = authManager,
            gson = gson,
            ioDispatcher = Dispatchers.IO
        ).also {
            it.client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        }
    }

    private fun sampleLocation(
        id: String = "loc-1",
        name: String = "Succulent Nursery",
        address: String = "123 Plant Way, San Jose, CA",
        lat: Double = 37.3382,
        lon: Double = -121.8863,
        radius: Int = 150,
        icon: String = "store",
        classification: String? = "BUSINESS",
        entity: String? = "MySucculentCorner",
        createdAt: Long = 1700000000000L,
        updatedAt: Long = 1700000001000L
    ) = SavedLocationEntity(
        id = id,
        name = name,
        latitude = lat,
        longitude = lon,
        address = address,
        radiusMeters = radius,
        icon = icon,
        classificationPreference = classification,
        businessEntity = entity,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    @Test
    fun backupAfterCreateEditAndDelete_writesAllFieldsInSavedLocationsTab_noSecretsInCells_preservesTrips() = runBlocking {
        configureOAuthSheets()
        val trip = TripEntity(
            id = "trip-preserved-1",
            date = "2026-11-11",
            startTime = 1000L,
            endTime = 2000L,
            startAddress = "100 Main St",
            endAddress = "200 Oak Ave",
            startLatitude = 37.33,
            startLongitude = -121.88,
            endLatitude = 37.35,
            endLongitude = -121.89,
            totalDistanceMiles = 14.2,
            businessMiles = 14.2,
            businessEntity = "MySucculentCorner",
            purpose = "Delivery",
            trackingMethod = "AUTO_DETECTED",
            classification = "BUSINESS",
            applicableRate = 0.67,
            calculatedDeduction = 9.514,
            notes = null,
            waypointsJson = "[]",
            syncStatus = "SYNCED",
            syncedAt = 2500L
        )
        database.tripDao().insertTrip(trip)

        val interceptor = FakeSheetsInterceptor(gson)
        val service = createService(interceptor)

        // 1. CREATE
        val loc = sampleLocation()
        repository.saveLocation(loc)
        assertEquals(1, scheduledBackupCalls)
        assertEquals(1, database.savedLocationSyncQueueDao().getPendingCount())

        val createRes = service.backupSavedLocations(fullSnapshot = false)
        assertTrue(createRes is SavedLocationsSyncResult.Success)
        assertEquals(1, (createRes as SavedLocationsSyncResult.Success).uploadedCount)
        assertEquals(0, database.savedLocationSyncQueueDao().getPendingCount())
        assertTrue(preferencesManager.savedLocationsLastBackupTime.first() > 0L)

        // Verify worksheet exists and all 11 fields written cleanly without secrets
        assertTrue(interceptor.sheets.containsKey(SavedLocationsSheetsService.WORKSHEET_NAME))
        val rowsAfterCreate = interceptor.sheetValues[SavedLocationsSheetsService.WORKSHEET_NAME]!!
        assertEquals(2, rowsAfterCreate.size)
        assertEquals(SavedLocationsSheetsService.EXPECTED_HEADERS, rowsAfterCreate[0])
        assertEquals("loc-1", rowsAfterCreate[1][0])
        assertEquals("Succulent Nursery", rowsAfterCreate[1][1])
        assertEquals("123 Plant Way, San Jose, CA", rowsAfterCreate[1][2])
        assertEquals(37.3382, (rowsAfterCreate[1][3] as Number).toDouble(), 1e-6)
        assertEquals(-121.8863, (rowsAfterCreate[1][4] as Number).toDouble(), 1e-6)
        assertEquals(150, (rowsAfterCreate[1][5] as Number).toInt())
        assertEquals("store", rowsAfterCreate[1][6])
        assertEquals("BUSINESS", rowsAfterCreate[1][7])
        assertEquals("MySucculentCorner", rowsAfterCreate[1][8])

        // Ensure token NEVER appeared in cell payloads or URLs
        for (call in interceptor.calls) {
            assertFalse("Token must never appear in request body", call.body.contains(secretToken))
            assertFalse("Token must never appear in URL", call.url.contains(secretToken))
        }

        // 2. EDIT (updates same row in-place; no duplicate row created)
        val edited = loc.copy(name = "Main Nursery HQ", radiusMeters = 250, updatedAt = 1700000009000L)
        repository.saveLocation(edited)
        assertEquals(2, scheduledBackupCalls)
        val editRes = service.backupSavedLocations(fullSnapshot = false)
        assertTrue(editRes is SavedLocationsSyncResult.Success)
        val rowsAfterEdit = interceptor.sheetValues[SavedLocationsSheetsService.WORKSHEET_NAME]!!
        assertEquals(2, rowsAfterEdit.size) // Still header + 1 data row!
        assertEquals("Main Nursery HQ", rowsAfterEdit[1][1])
        assertEquals(250, (rowsAfterEdit[1][5] as Number).toInt())

        // 3. DELETE (removes row via batchUpdate deleteDimension)
        repository.deleteLocation("loc-1")
        assertEquals(3, scheduledBackupCalls)
        val deleteRes = service.backupSavedLocations(fullSnapshot = false)
        assertTrue(deleteRes is SavedLocationsSyncResult.Success)
        assertEquals(1, (deleteRes as SavedLocationsSyncResult.Success).deletedCount)
        assertEquals(0, database.savedLocationSyncQueueDao().getPendingCount())
        val rowsAfterDelete = interceptor.sheetValues[SavedLocationsSheetsService.WORKSHEET_NAME]!!
        assertEquals(1, rowsAfterDelete.size) // Only header remains

        // Verify trips table and Trips worksheet completely untouched
        val storedTrips = database.tripDao().getTripById("trip-preserved-1")
        assertEquals(trip, storedTrips)
        assertFalse(interceptor.sheets.containsKey("Trips"))
    }

    @Test
    fun offlineBackupRetries_preservesPendingQueueUntilNetworkReturns() = runBlocking {
        configureOAuthSheets()
        var isOffline = true
        val delegate = FakeSheetsInterceptor(gson) { req, _, _ ->
            if (isOffline) throw IOException("No network connection")
            null
        }
        val service = createService(delegate)

        repository.saveLocation(sampleLocation(id = "loc-offline-1", name = "Warehouse"))
        assertEquals(1, database.savedLocationSyncQueueDao().getPendingCount())

        val offlineResult = service.backupSavedLocations(fullSnapshot = false)
        assertTrue(offlineResult is SavedLocationsSyncResult.NetworkError)
        // Durable queue MUST remain intact after offline failure
        val pending = database.savedLocationSyncQueueDao().getAllPending()
        assertEquals(1, pending.size)
        assertEquals("loc-offline-1", pending[0].locationId)
        assertEquals(SavedLocationSyncQueueEntity.OP_UPSERT, pending[0].operation)

        // Network recovers and retry runs
        isOffline = false
        val retryResult = service.backupSavedLocations(fullSnapshot = false)
        assertTrue(retryResult is SavedLocationsSyncResult.Success)
        assertEquals(0, database.savedLocationSyncQueueDao().getPendingCount())
        assertEquals(2, delegate.sheetValues[SavedLocationsSheetsService.WORKSHEET_NAME]!!.size)
    }

    @Test
    fun partialBackupFailure_retainsOnlyFailedItemsInQueueForRetry() = runBlocking {
        configureOAuthSheets()
        var appendCount = 0
        val interceptor = FakeSheetsInterceptor(gson) { req, body, _ ->
            if (req.method == "POST" && req.url.toString().contains("append")) {
                appendCount++
                if (appendCount == 2) {
                    throw IOException("Connection reset on second item")
                }
            }
            null
        }
        val service = createService(interceptor)

        repository.saveLocation(sampleLocation(id = "loc-1", name = "First", updatedAt = 100L))
        repository.saveLocation(sampleLocation(id = "loc-2", name = "Second", updatedAt = 200L))

        val partialResult = service.backupSavedLocations(fullSnapshot = false)
        assertTrue(partialResult is SavedLocationsSyncResult.PartialSuccess)
        val p = partialResult as SavedLocationsSyncResult.PartialSuccess
        assertEquals(1, p.succeededCount)
        assertEquals(1, p.failedCount)

        // loc-1 succeeded and was removed from queue; loc-2 stayed in queue
        val remaining = database.savedLocationSyncQueueDao().getAllPending()
        assertEquals(1, remaining.size)
        assertEquals("loc-2", remaining[0].locationId)
    }

    @Test
    fun duplicatePrevention_deduplicatesExtraCloudRowsDuringBackupAndRestore() = runBlocking {
        configureOAuthSheets()
        val interceptor = FakeSheetsInterceptor(gson)
        interceptor.sheets[SavedLocationsSheetsService.WORKSHEET_NAME] = 301
        interceptor.sheetValues[SavedLocationsSheetsService.WORKSHEET_NAME] = mutableListOf(
            SavedLocationsSheetsService.EXPECTED_HEADERS.toMutableList<Any?>(),
            mutableListOf("dup-1", "Old Copy", "10 Old St", 37.30, -121.80, 100, "place", "", "", "2026-11-10T10:00:00Z", "2026-11-10T10:00:00Z"),
            mutableListOf("dup-1", "Newer Copy", "20 New St", 37.31, -121.81, 200, "store", "BUSINESS", "MySucculentCorner", "2026-11-10T10:00:00Z", "2026-11-11T10:00:00Z")
        )

        val service = createService(interceptor)
        val previewRes = service.previewRestoreFromSheets() as RestorePreviewResult.Success
        assertEquals(1, previewRes.preview.candidates.size)
        assertEquals(1, previewRes.preview.duplicateCloudIdsMerged)
        assertEquals("Newer Copy", previewRes.preview.candidates[0].cloudLocation.name)

        // Apply restore -> saves deduplicated record and now test backup cleans duplicate extra row on sheet
        val applyRes = service.applyRestoreSelection(previewRes.preview, setOf("dup-1"))
        assertTrue(applyRes is RestoreApplyResult.Success)
        assertEquals(1, repository.getAllLocations().size)

        val backupRes = service.backupSavedLocations(fullSnapshot = true)
        assertTrue(backupRes is SavedLocationsSyncResult.Success)
        val sheetRows = interceptor.sheetValues[SavedLocationsSheetsService.WORKSHEET_NAME]!!
        assertEquals(2, sheetRows.size) // Header + single deduplicated row!
    }

    @Test
    fun restoreIntoEmptyDevice_importsAllWithoutReenqueueingBackupQueue() = runBlocking {
        configureOAuthSheets()
        val interceptor = FakeSheetsInterceptor(gson)
        interceptor.sheets[SavedLocationsSheetsService.WORKSHEET_NAME] = 302
        interceptor.sheetValues[SavedLocationsSheetsService.WORKSHEET_NAME] = mutableListOf(
            SavedLocationsSheetsService.EXPECTED_HEADERS.toMutableList<Any?>(),
            mutableListOf("cloud-1", "Home", "50 Home Rd", 37.40, -121.90, 120, "home", "PERSONAL", "", "2026-11-10T10:00:00Z", "2026-11-10T12:00:00Z"),
            mutableListOf("cloud-2", "Greenhouse", "80 Flora Ave", 37.45, -121.95, 250, "store", "BUSINESS", "MySucculentCorner", "2026-11-10T10:00:00Z", "2026-11-10T12:00:00Z")
        )
        val service = createService(interceptor)

        val preview = (service.previewRestoreFromSheets() as RestorePreviewResult.Success).preview
        assertEquals(2, preview.candidates.size)
        assertTrue(preview.candidates.all { it.status == RestoreItemStatus.NEW_FROM_CLOUD && it.selectedByDefault })

        val apply = service.applyRestoreSelection(preview, setOf("cloud-1", "cloud-2")) as RestoreApplyResult.Success
        assertEquals(2, apply.importedCount)
        assertEquals(0, apply.updatedCount)

        val stored = repository.getAllLocations()
        assertEquals(2, stored.size)
        // Applying restore must NOT enqueue bounce-back UPSERT jobs or trigger backup scheduling
        assertEquals(0, database.savedLocationSyncQueueDao().getPendingCount())
        assertEquals(0, scheduledBackupCalls)
    }

    @Test
    fun restoreWithConflictingLocalRecords_preservesLocalUnlessExplicitlyApproved() = runBlocking {
        configureOAuthSheets()
        val localUpdated = sampleLocation(id = "shared-1", name = "Local Custom Name", radius = 300, updatedAt = 1800000000000L)
        val localOnly = sampleLocation(id = "local-only", name = "Local Private Spot", updatedAt = 1800000000000L)
        repository.saveLocation(localUpdated)
        repository.saveLocation(localOnly)

        val interceptor = FakeSheetsInterceptor(gson)
        interceptor.sheets[SavedLocationsSheetsService.WORKSHEET_NAME] = 303
        interceptor.sheetValues[SavedLocationsSheetsService.WORKSHEET_NAME] = mutableListOf(
            SavedLocationsSheetsService.EXPECTED_HEADERS.toMutableList<Any?>(),
            mutableListOf("shared-1", "Cloud Older Name", "999 Cloud St", 37.3382, -121.8863, 150, "store", "BUSINESS", "MySucculentCorner", "2026-11-01T10:00:00Z", "2026-11-05T10:00:00Z"),
            mutableListOf("cloud-new", "New Supplier", "77 Market St", 37.39, -121.92, 180, "warehouse", "BUSINESS", "MySucculentCorner", "2026-11-11T10:00:00Z", "2026-11-11T10:00:00Z")
        )
        val service = createService(interceptor)

        val preview = (service.previewRestoreFromSheets() as RestorePreviewResult.Success).preview
        val conflict = preview.candidates.first { it.cloudLocation.id == "shared-1" }
        assertEquals(RestoreItemStatus.LOCAL_NEWER, conflict.status)
        assertFalse(conflict.selectedByDefault)
        assertTrue(conflict.requiresOverwriteApproval)
        assertEquals(1, preview.localOnlyLocations.size)

        // Attempt apply without approving conflict overwrite -> preserves local shared-1 AND local-only, imports cloud-new
        val applyPreserving = service.applyRestoreSelection(
            preview = preview,
            selectedCandidateIds = setOf("cloud-new", "shared-1"),
            approvedConflictIds = emptySet()
        ) as RestoreApplyResult.Success
        assertEquals(1, applyPreserving.importedCount)
        assertEquals(0, applyPreserving.updatedCount)
        assertEquals("Local Custom Name", repository.getLocationById("shared-1")?.name)
        assertNotNull(repository.getLocationById("local-only"))
        assertNotNull(repository.getLocationById("cloud-new"))

        // Explicit approval overwrites shared-1 while still keeping local-only untouched
        val applyOverwrite = service.applyRestoreSelection(
            preview = preview,
            selectedCandidateIds = setOf("shared-1"),
            approvedConflictIds = setOf("shared-1")
        ) as RestoreApplyResult.Success
        assertEquals(1, applyOverwrite.updatedCount)
        assertEquals("Cloud Older Name", repository.getLocationById("shared-1")?.name)
        assertNotNull(repository.getLocationById("local-only"))
    }

    @Test
    fun emptyOrIncompleteCloudResponse_neverErasesLocalRecords() = runBlocking {
        configureOAuthSheets()
        val local = sampleLocation(id = "keep-me", name = "Protected Local")
        repository.saveLocation(local)

        // 1. Header-only empty cloud worksheet
        val emptyInterceptor = FakeSheetsInterceptor(gson)
        emptyInterceptor.sheets[SavedLocationsSheetsService.WORKSHEET_NAME] = 304
        emptyInterceptor.sheetValues[SavedLocationsSheetsService.WORKSHEET_NAME] = mutableListOf(
            SavedLocationsSheetsService.EXPECTED_HEADERS.toMutableList<Any?>()
        )
        val emptyResult = createService(emptyInterceptor).previewRestoreFromSheets()
        assertTrue(emptyResult is RestorePreviewResult.EmptyCloudSheet)
        assertEquals(1, repository.getAllLocations().size)

        // 2. Truncated/incomplete cloud JSON {}
        val brokenInterceptor = FakeSheetsInterceptor(gson) { req, _, _ ->
            if (req.url.toString().contains("/values/")) {
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json; charset=utf-8".toMediaType()))
                    .build()
            } else {
                null
            }
        }
        brokenInterceptor.sheets[SavedLocationsSheetsService.WORKSHEET_NAME] = 304
        val brokenResult = createService(brokenInterceptor).previewRestoreFromSheets()
        assertTrue(brokenResult is RestorePreviewResult.Error)
        assertEquals("Protected Local", repository.getLocationById("keep-me")?.name)
    }

    @Test
    fun deletedLocationSynchronization_flagsPendingDeleteSafelyDuringPreview() = runBlocking {
        configureOAuthSheets()
        val toDelete = sampleLocation(id = "del-1", name = "Closed Supplier")
        repository.saveLocation(toDelete)
        repository.deleteLocation("del-1")
        assertNull(repository.getLocationById("del-1"))

        val interceptor = FakeSheetsInterceptor(gson)
        interceptor.sheets[SavedLocationsSheetsService.WORKSHEET_NAME] = 305
        interceptor.sheetValues[SavedLocationsSheetsService.WORKSHEET_NAME] = mutableListOf(
            SavedLocationsSheetsService.EXPECTED_HEADERS.toMutableList<Any?>(),
            mutableListOf("del-1", "Closed Supplier", "123 Plant Way", 37.3382, -121.8863, 150, "store", "BUSINESS", "MySucculentCorner", "2026-11-10T10:00:00Z", "2026-11-10T12:00:00Z")
        )
        val service = createService(interceptor)

        // Restore preview before offline delete sync finishes must NOT resurrect del-1 by default
        val preview = (service.previewRestoreFromSheets() as RestorePreviewResult.Success).preview
        val deletedCandidate = preview.candidates.single()
        assertEquals(RestoreItemStatus.PREVIOUSLY_DELETED_LOCALLY, deletedCandidate.status)
        assertFalse(deletedCandidate.selectedByDefault)
        assertTrue(deletedCandidate.hasPendingLocalDelete)

        // Automatic backup removes del-1 from sheet
        val syncRes = service.backupSavedLocations(fullSnapshot = false)
        assertTrue(syncRes is SavedLocationsSyncResult.Success)
        assertEquals(1, interceptor.sheetValues[SavedLocationsSheetsService.WORKSHEET_NAME]!!.size)
        assertEquals(0, database.savedLocationSyncQueueDao().getPendingCount())
    }

    @Test
    fun authenticationFailures_401RefreshAndWebhookOnlyRejection() = runBlocking {
        // 1. Webhook-only mode rejects cleanly with guidance & zero network requests
        preferencesManager.setSheetsSpreadsheetId("")
        preferencesManager.setSheetsWebhookUrl("https://script.google.com/macros/s/test/exec")
        preferencesManager.setSheetsConnected(true)
        val webhookInterceptor = FakeSheetsInterceptor(gson)
        val webhookService = createService(webhookInterceptor)
        val webhookBackupRes = webhookService.backupSavedLocations(fullSnapshot = true)
        assertTrue(webhookBackupRes is SavedLocationsSyncResult.UnsupportedWebhookOnly)
        val webhookRestoreRes = webhookService.previewRestoreFromSheets()
        assertTrue((webhookRestoreRes as RestorePreviewResult.Error).failure is SavedLocationsSyncResult.UnsupportedWebhookOnly)
        assertEquals(0, webhookInterceptor.calls.size)

        // 2. 401 token expired with no refresh -> TokenExpired and queue stays preserved
        configureOAuthSheets(token = "expired_token")
        repository.saveLocation(sampleLocation(id = "auth-loc"))
        val unauthorizedInterceptor = FakeSheetsInterceptor(gson) { req, _, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .body("""{"error":{"message":"Invalid Credentials"}}""".toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }
        val unauthService = createService(unauthorizedInterceptor, authManager = null)
        val unauthRes = unauthService.backupSavedLocations(fullSnapshot = false)
        assertTrue(unauthRes is SavedLocationsSyncResult.TokenExpired)
        assertEquals(1, database.savedLocationSyncQueueDao().getPendingCount())

        // 3. 401 -> GoogleAuthManager refreshes token -> succeeds on retry
        val refreshedToken = "fresh_oauth_token_456"
        val authManager = object : GoogleAuthManager(context, secureStorageManager, preferencesManager) {
            override suspend fun refreshOAuthToken(): String {
                secureStorageManager.saveOAuthToken(refreshedToken)
                return refreshedToken
            }
        }
        val refreshInterceptor = FakeSheetsInterceptor(gson) { req, _, _ ->
            if (req.header("Authorization") != "Bearer $refreshedToken") {
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("""{"error":{"message":"Expired token"}}""".toResponseBody("application/json; charset=utf-8".toMediaType()))
                    .build()
            } else {
                null
            }
        }
        val refreshRes = createService(refreshInterceptor, authManager).backupSavedLocations(fullSnapshot = false)
        assertTrue(refreshRes is SavedLocationsSyncResult.Success)
        assertEquals(0, database.savedLocationSyncQueueDao().getPendingCount())
    }
}
