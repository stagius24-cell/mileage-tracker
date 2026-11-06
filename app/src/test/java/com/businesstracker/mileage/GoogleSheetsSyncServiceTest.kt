package com.businesstracker.mileage

import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.remote.GoogleSheetsSyncService
import com.businesstracker.mileage.data.remote.SheetsSyncResult
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.domain.repository.TripRepository
import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class GoogleSheetsSyncServiceTest {

    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var secureStorageManager: SecureStorageManager
    private val markedSyncedTripIds = mutableListOf<String>()
    private val tripsList = mutableListOf<TripEntity>()

    private fun createTrip(id: String, classification: String, distance: Double, syncStatus: String = "PENDING"): TripEntity {
        return TripEntity(
            id = id,
            date = "2026-11-06",
            startTime = 1730880000000L,
            endTime = 1730883600000L,
            startAddress = "123 Main St",
            endAddress = "456 Market St",
            startLatitude = 37.77,
            startLongitude = -122.41,
            endLatitude = 37.78,
            endLongitude = -122.42,
            totalDistanceMiles = distance,
            businessMiles = if (classification == "BUSINESS") distance else 0.0,
            businessEntity = if (classification == "BUSINESS") "MySucculentCorner" else "Personal",
            purpose = if (classification == "BUSINESS") "Nursery Pickup" else "Personal",
            trackingMethod = "AUTO_DETECTED",
            classification = classification,
            applicableRate = 0.760,
            calculatedDeduction = if (classification == "BUSINESS") distance * 0.760 else 0.0,
            notes = "Test note",
            waypointsJson = "[]",
            syncStatus = syncStatus,
            syncedAt = if (syncStatus == "SYNCED") 1730885000000L else null
        )
    }

    private val fakeRepo = object : TripRepository {
        override fun getAllTrips() = flowOf(tripsList)
        override fun getUnclassifiedTrips() = flowOf(tripsList.filter { it.classification == "UNREVIEWED" })
        override fun getUnclassifiedTripsCount() = flowOf(tripsList.count { it.classification == "UNREVIEWED" })
        override fun getTripsByDateRange(startDate: String, endDate: String) = flowOf(emptyList<TripEntity>())
        override fun getTripsByBusinessEntity(entity: String) = flowOf(emptyList<TripEntity>())
        override fun getBusinessEntitySummaries() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
        override fun getBusinessEntitySummariesForDateRange(startDate: String, endDate: String) = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
        override fun getTripCountByClassification() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.ClassificationCount>())
        override suspend fun getTripById(tripId: String) = tripsList.firstOrNull { it.id == tripId }
        override fun observeTripById(tripId: String) = flowOf(tripsList.firstOrNull { it.id == tripId })
        override suspend fun insertTrip(trip: TripEntity) { tripsList.add(trip) }
        override suspend fun updateTrip(trip: TripEntity) {
            val idx = tripsList.indexOfFirst { it.id == trip.id }
            if (idx != -1) tripsList[idx] = trip
        }
        override suspend fun deleteTrip(tripId: String) { tripsList.removeAll { it.id == tripId } }
        override suspend fun classifyTrip(tripId: String, classification: String, businessEntity: String, purpose: String, notes: String?) {
            val idx = tripsList.indexOfFirst { it.id == tripId }
            if (idx != -1) {
                val old = tripsList[idx]
                tripsList[idx] = old.copy(
                    classification = classification,
                    businessEntity = businessEntity,
                    purpose = purpose,
                    notes = notes,
                    syncStatus = "PENDING"
                )
            }
        }
        override suspend fun updateTripDetails(tripId: String, classification: String, businessEntity: String, purpose: String, notes: String?) {
            classifyTrip(tripId, classification, businessEntity, purpose, notes)
        }
        override fun getTripsBySyncStatus(syncStatus: String) = flowOf(tripsList.filter { it.syncStatus == syncStatus })
        override fun getTripsByClassification(classification: String) = flowOf(tripsList.filter { it.classification == classification })
        override suspend fun getPendingSyncTrips(): List<TripEntity> = tripsList.filter { it.syncStatus == "PENDING" }
        override suspend fun markSynced(tripId: String, timestamp: Long) {
            markedSyncedTripIds.add(tripId)
            val index = tripsList.indexOfFirst { it.id == tripId }
            if (index != -1) {
                tripsList[index] = tripsList[index].copy(syncStatus = "SYNCED", syncedAt = timestamp)
            }
        }
    }

    @Before
    fun setUp() {
        markedSyncedTripIds.clear()
        tripsList.clear()

        val context = RuntimeEnvironment.getApplication()
        preferencesManager = TrackingPreferencesManager(context)
        val testKeyProvider = com.businesstracker.mileage.data.security.TestSoftwareKeyProvider()
        secureStorageManager = SecureStorageManager(context, testKeyProvider)
        secureStorageManager.clearAllCredentials()
    }

    private suspend fun configureSheetsApi(sheetId: String = "test_sheet_123", token: String = "test_valid_token") {
        preferencesManager.setSheetsSpreadsheetId(sheetId)
        preferencesManager.setSheetsWebhookUrl("")
        secureStorageManager.saveOAuthToken(token)
    }

    private suspend fun configureWebhook(url: String = "https://example.com/macros/exec") {
        preferencesManager.setSheetsSpreadsheetId("")
        preferencesManager.setSheetsWebhookUrl(url)
        secureStorageManager.clearAllCredentials()
    }

    private fun createService(): GoogleSheetsSyncService {
        return GoogleSheetsSyncService(preferencesManager, fakeRepo, secureStorageManager, Gson())
    }

    @Test
    fun formatTripToRow_hasExact16ColumnsInCorrectOrder() {
        val service = createService()
        val trip = createTrip("trip_abc_123", "BUSINESS", 12.5)
        val row = service.formatTripToRow(trip)

        assertEquals(16, row.size)
        assertEquals("trip_abc_123", row[0])         // 1. Trip ID
        assertEquals("2026-11-06", row[1])            // 2. Date
        assertTrue(row[2].isNotBlank())               // 3. Start Time
        assertTrue(row[3].isNotBlank())               // 4. End Time
        assertEquals("123 Main St", row[4])           // 5. Start Address
        assertEquals("456 Market St", row[5])         // 6. End Address
        assertEquals("12.50", row[6])                 // 7. Total Miles
        assertEquals("12.50", row[7])                 // 8. Business Miles
        assertEquals("BUSINESS", row[8])              // 9. Classification
        assertEquals("MySucculentCorner", row[9])     // 10. Business Entity
        assertEquals("Nursery Pickup", row[10])       // 11. Purpose
        assertEquals("0.760", row[11])                // 12. Mileage Rate
        assertEquals("9.50", row[12])                 // 13. Estimated Deduction
        assertEquals("Test note", row[13])            // 14. Notes
        assertEquals("AUTO_DETECTED", row[14])        // 15. Tracking Method
        assertTrue(row[15].isNotBlank())              // 16. Last Updated
    }

    @Test
    fun syncPendingTrips_notConfigured_returnsNotConfigured() = runTest {
        preferencesManager.setSheetsSpreadsheetId("")
        preferencesManager.setSheetsWebhookUrl("")
        val service = createService()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.NotConfigured)
    }

    @Test
    fun syncPendingTrips_unreviewedTripsAreNeverUploaded() = runTest {
        tripsList.add(createTrip("unreviewed_1", "UNREVIEWED", 15.0))
        configureSheetsApi("test_sheet_id", "valid_test_token")
        val service = createService()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.Success)
        assertEquals(0, (result as SheetsSyncResult.Success).insertedCount)
        assertEquals(0, markedSyncedTripIds.size)
    }

    @Test
    fun syncViaSheetsApi_allWritesSuccessful_appendsRowsAndMarksSynced() = runTest {
        tripsList.add(createTrip("trip_1", "BUSINESS", 10.0))
        tripsList.add(createTrip("trip_2", "PERSONAL", 5.0))
        configureSheetsApi("test_sheet_123", "test_valid_token")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.method == "GET") {
                    val body = "{\"values\":[[\"Trip ID\"]]}"
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                } else {
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("{\"updates\":{\"updatedRows\":1}}".toResponseBody("application/json".toMediaType())).build()
                }
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.Success)
        val success = result as SheetsSyncResult.Success
        assertEquals(2, success.insertedCount)
        assertEquals(0, success.updatedCount)
        assertEquals(listOf("trip_1", "trip_2"), markedSyncedTripIds)
        assertTrue(tripsList.all { it.syncStatus == "SYNCED" })
    }

    @Test
    fun syncViaSheetsApi_oneRowAppendFails_returnsPartialSuccessAndPreservesFailed() = runTest {
        tripsList.add(createTrip("trip_ok", "BUSINESS", 10.0))
        tripsList.add(createTrip("trip_fail", "BUSINESS", 12.0))
        configureSheetsApi("test_sheet_123", "test_valid_token")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.method == "GET") {
                    val body = "{\"values\":[[\"Trip ID\"]]}"
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                } else {
                    val buffer = okio.Buffer()
                    req.body?.writeTo(buffer)
                    val bodyStr = buffer.readUtf8()
                    if (bodyStr.contains("trip_fail")) {
                        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(500).message("Server Error")
                            .body("Internal error".toResponseBody("text/plain".toMediaType())).build()
                    } else {
                        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                            .body("{\"updates\":{\"updatedRows\":1}}".toResponseBody("application/json".toMediaType())).build()
                    }
                }
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.PartialSuccess)
        val partial = result as SheetsSyncResult.PartialSuccess
        assertEquals(1, partial.successCount)
        assertEquals(1, partial.failedCount)

        assertEquals(listOf("trip_ok"), markedSyncedTripIds)
        assertEquals("SYNCED", tripsList.first { it.id == "trip_ok" }.syncStatus)
        assertEquals("PENDING", tripsList.first { it.id == "trip_fail" }.syncStatus)
    }

    @Test
    fun syncViaSheetsApi_oneRowUpdateFails_preservesFailedRowAsPending() = runTest {
        tripsList.add(createTrip("trip_exist_1", "BUSINESS", 10.0))
        tripsList.add(createTrip("trip_exist_2", "BUSINESS", 20.0))
        configureSheetsApi("test_sheet_123", "test_valid_token")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.method == "GET") {
                    val body = "{\"values\":[[\"Trip ID\"],[\"trip_exist_1\"],[\"trip_exist_2\"]]}"
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                } else if (req.method == "PUT") {
                    if (req.url.toString().contains("A3:P3")) {
                        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(400).message("Bad Request")
                            .body("Cell error".toResponseBody("text/plain".toMediaType())).build()
                    } else {
                        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                            .body("{\"updatedRows\":1}".toResponseBody("application/json".toMediaType())).build()
                    }
                } else {
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("{}".toResponseBody("application/json".toMediaType())).build()
                }
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.PartialSuccess)
        val partial = result as SheetsSyncResult.PartialSuccess
        assertEquals(1, partial.successCount)
        assertEquals(1, partial.failedCount)

        assertEquals(listOf("trip_exist_1"), markedSyncedTripIds)
        assertEquals("SYNCED", tripsList.first { it.id == "trip_exist_1" }.syncStatus)
        assertEquals("PENDING", tripsList.first { it.id == "trip_exist_2" }.syncStatus)
    }

    @Test
    fun syncViaSheetsApi_tokenExpired_returnsTokenExpiredAndPreservesData() = runTest {
        tripsList.add(createTrip("trip_1", "BUSINESS", 10.0))
        configureSheetsApi("test_sheet", "expired_tok")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(401)
                    .message("Unauthorized")
                    .body("{\"error\": \"Invalid Credentials\"}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.TokenExpired)
        assertEquals(0, markedSyncedTripIds.size)
        assertEquals("PENDING", tripsList[0].syncStatus)
    }

    @Test
    fun syncViaSheetsApi_accessDenied403_returnsAccessDenied() = runTest {
        tripsList.add(createTrip("trip_1", "BUSINESS", 10.0))
        configureSheetsApi("test_sheet", "valid_tok")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(403)
                    .message("Forbidden")
                    .body("{\"error\": \"Permission denied\"}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.AccessDenied)
        assertEquals(0, markedSyncedTripIds.size)
    }

    @Test
    fun syncViaSheetsApi_notFound404_returnsNotFound() = runTest {
        tripsList.add(createTrip("trip_1", "BUSINESS", 10.0))
        configureSheetsApi("nonexistent_id", "valid_tok")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(404)
                    .message("Not Found")
                    .body("{\"error\": \"Spreadsheet not found\"}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.NotFound)
        assertEquals(0, markedSyncedTripIds.size)
    }

    @Test
    fun syncViaSheetsApi_rateLimited429_returnsRateLimited() = runTest {
        tripsList.add(createTrip("trip_1", "BUSINESS", 10.0))
        configureSheetsApi("test_sheet", "valid_tok")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(429)
                    .message("Too Many Requests")
                    .body("{\"error\": \"Quota exceeded\"}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.RateLimited)
        assertEquals(0, markedSyncedTripIds.size)
    }

    @Test
    fun syncViaSheetsApi_serverError500_returnsServerError() = runTest {
        tripsList.add(createTrip("trip_1", "BUSINESS", 10.0))
        configureSheetsApi("test_sheet", "valid_tok")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503)
                    .message("Service Unavailable")
                    .body("Server error".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.ServerError)
        assertEquals(503, (result as SheetsSyncResult.ServerError).statusCode)
        assertEquals(0, markedSyncedTripIds.size)
    }

    @Test
    fun syncViaWebhook_insecureHttp_returnsInvalidConfiguration() = runTest {
        tripsList.add(createTrip("trip_1", "BUSINESS", 10.0))
        configureWebhook("http://insecure.example.com/sync") // Insecure HTTP
        val service = createService()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.InvalidConfiguration)
        assertEquals(0, markedSyncedTripIds.size)
    }

    @Test
    fun syncViaWebhook_success_marksTripsSynced() = runTest {
        val trip = createTrip("trip_web_1", "BUSINESS", 20.0)
        tripsList.add(trip)
        configureWebhook("https://example.com/macros/exec")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{\"status\":\"success\",\"syncedTripIds\":[\"trip_web_1\"]}".toResponseBody("application/json".toMediaType())).build()
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.Success)
        assertEquals(1, (result as SheetsSyncResult.Success).insertedCount)
        assertEquals(listOf("trip_web_1"), markedSyncedTripIds)
        assertEquals("SYNCED", tripsList[0].syncStatus)
    }

    @Test
    fun syncViaWebhook_malformedJson_returnsUnexpectedErrorAndPreservesData() = runTest {
        tripsList.add(createTrip("trip_1", "BUSINESS", 10.0))
        configureWebhook("https://example.com/macros/exec")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("<html><body>Not JSON</body></html>".toResponseBody("text/html".toMediaType())).build()
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.UnexpectedError)
        assertEquals(0, markedSyncedTripIds.size)
        assertEquals("PENDING", tripsList[0].syncStatus)
    }

    @Test
    fun syncViaWebhook_applicationLevelFailureWithHttp200_doesNotMarkSynced() = runTest {
        tripsList.add(createTrip("trip_1", "BUSINESS", 10.0))
        configureWebhook("https://example.com/macros/exec")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{\"status\":\"error\",\"message\":\"Spreadsheet lock failed\"}".toResponseBody("application/json".toMediaType())).build()
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.HttpError)
        assertEquals(200, (result as SheetsSyncResult.HttpError).statusCode)
        assertEquals("Spreadsheet lock failed", (result as SheetsSyncResult.HttpError).message)
        assertEquals(0, markedSyncedTripIds.size)
        assertEquals("PENDING", tripsList[0].syncStatus)
    }

    @Test
    fun syncViaWebhook_partialConfirmation_marksOnlyConfirmedTrips() = runTest {
        tripsList.add(createTrip("trip_a", "BUSINESS", 10.0))
        tripsList.add(createTrip("trip_b", "BUSINESS", 15.0))
        configureWebhook("https://example.com/macros/exec")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{\"status\":\"success\",\"syncedTripIds\":[\"trip_a\"]}".toResponseBody("application/json".toMediaType())).build()
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.PartialSuccess)
        val partial = result as SheetsSyncResult.PartialSuccess
        assertEquals(1, partial.successCount)
        assertEquals(1, partial.failedCount)
        assertEquals(listOf("trip_a"), markedSyncedTripIds)
        assertEquals("SYNCED", tripsList.first { it.id == "trip_a" }.syncStatus)
        assertEquals("PENDING", tripsList.first { it.id == "trip_b" }.syncStatus)
    }

    @Test
    fun syncViaSheetsApi_editedTripResyncsIdempotently_updatesRowWithoutDuplication() = runTest {
        val trip = createTrip("trip_edit", "BUSINESS", 10.0, syncStatus = "SYNCED")
        tripsList.add(trip)

        fakeRepo.updateTripDetails("trip_edit", "BUSINESS", "MySucculentCorner", "Client Delivery", "Updated note")
        assertEquals("PENDING", tripsList[0].syncStatus)
        configureSheetsApi("test_sheet", "valid_tok")

        var putUrlCalled: String? = null
        var postCalled = false

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.method == "GET") {
                    val body = "{\"values\":[[\"Trip ID\"],[\"other_1\"],[\"other_2\"],[\"trip_edit\"]]}"
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                } else if (req.method == "PUT") {
                    putUrlCalled = req.url.toString()
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("{\"updatedRows\":1}".toResponseBody("application/json".toMediaType())).build()
                } else if (req.method == "POST") {
                    postCalled = true
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("{}".toResponseBody("application/json".toMediaType())).build()
                } else {
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("{}".toResponseBody("application/json".toMediaType())).build()
                }
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.Success)
        val success = result as SheetsSyncResult.Success
        assertEquals(0, success.insertedCount)
        assertEquals(1, success.updatedCount)
        assertFalse("Should NOT append duplicate row when trip already exists in sheet", postCalled)
        assertTrue(putUrlCalled?.contains("Sheet1!A4:P4") == true)
        assertEquals("SYNCED", tripsList[0].syncStatus)
    }

    @Test
    fun syncPendingTrips_concurrentInvocations_serializedByMutexWithoutDuplicateRows() = runTest {
        tripsList.add(createTrip("trip_concurrent", "BUSINESS", 8.0))
        configureSheetsApi("test_sheet", "valid_tok")

        var appendCallCount = 0

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.method == "GET") {
                    val body = "{\"values\":[[\"Trip ID\"]]}"
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                } else if (req.method == "POST") {
                    appendCallCount++
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("{\"updates\":{\"updatedRows\":1}}".toResponseBody("application/json".toMediaType())).build()
                } else {
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("{}".toResponseBody("application/json".toMediaType())).build()
                }
            }
            .build()

        val deferred1 = async { service.syncPendingTrips() }
        val deferred2 = async { service.syncPendingTrips() }

        val res1 = deferred1.await()
        val res2 = deferred2.await()

        assertTrue(res1 is SheetsSyncResult.Success)
        assertTrue(res2 is SheetsSyncResult.Success)

        assertEquals(1, appendCallCount)
        assertEquals(listOf("trip_concurrent"), markedSyncedTripIds)
    }

    @Test
    fun legacyTokenMigration_migratesToKeystore_and_removesPlaintextFromDataStore() = runTest {
        val legacySecret = "legacy_bearer_token_xyz_999"
        @Suppress("DEPRECATION")
        preferencesManager.setSheetsAuthToken(legacySecret)

        // Ensure Keystore is empty before migration
        secureStorageManager.clearAllCredentials()
        assertFalse(secureStorageManager.hasOAuthToken())

        val success = preferencesManager.migrateLegacyAuthToken(secureStorageManager)
        assertTrue("Migration must report success", success)

        // Verify token is securely stored in Keystore
        assertTrue(secureStorageManager.hasOAuthToken())
        assertEquals(legacySecret, secureStorageManager.getOAuthToken())

        // Verify plaintext preference is permanently wiped from DataStore
        @Suppress("DEPRECATION")
        val remainingPlaintext = preferencesManager.sheetsAuthToken.first()
        assertTrue("Plaintext token must be permanently removed from DataStore", remainingPlaintext.isBlank())
    }

    @Test
    fun syncPendingTrips_autoMigratesLegacyPlaintextToken_and_completesSync() = runTest {
        tripsList.add(createTrip("trip_migrated", "BUSINESS", 5.0))
        preferencesManager.setSheetsSpreadsheetId("test_sheet")
        preferencesManager.setSheetsWebhookUrl("")
        @Suppress("DEPRECATION")
        preferencesManager.setSheetsAuthToken("auto_migrated_tok_123")
        secureStorageManager.clearAllCredentials()

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                // Confirm the Authorization header used the migrated token
                val authHeader = req.header("Authorization")
                assertEquals("Bearer auto_migrated_tok_123", authHeader)

                if (req.method == "GET") {
                    val body = "{\"values\":[[\"Trip ID\"]]}"
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                } else {
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("{\"updates\":{\"updatedRows\":1}}".toResponseBody("application/json".toMediaType())).build()
                }
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue("Sync should succeed following automatic migration", result is SheetsSyncResult.Success)
        assertTrue("Token must now reside in SecureStorageManager", secureStorageManager.hasOAuthToken())
        assertEquals("auto_migrated_tok_123", secureStorageManager.getOAuthToken())

        @Suppress("DEPRECATION")
        val remainingPlaintext = preferencesManager.sheetsAuthToken.first()
        assertTrue("Plaintext token must be removed from DataStore", remainingPlaintext.isBlank())
    }

    @Test
    fun syncViaSheetsApi_malformedJsonRead_returnsUnexpectedError_andMarksZeroTrips() = runTest {
        tripsList.add(createTrip("trip_malformed", "BUSINESS", 10.0))
        configureSheetsApi("test_sheet", "valid_tok")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                // Return 200 OK with HTML/malformed JSON
                Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("<html><head><title>502 Bad Gateway</title></head></html>".toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue("Malformed JSON must fail closed with UnexpectedError", result is SheetsSyncResult.UnexpectedError)
        assertEquals("Zero trips should be marked synced", 0, markedSyncedTripIds.size)
        assertEquals("Trip must remain pending", "PENDING", tripsList[0].syncStatus)
    }

    @Test
    fun syncViaSheetsApi_missingRangeAndValues_failsClosed() = runTest {
        tripsList.add(createTrip("trip_bad_struct", "BUSINESS", 4.0))
        configureSheetsApi("test_sheet", "valid_tok")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                // Return empty JSON object lacking both range and values
                Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue("Unexpected structure must fail closed", result is SheetsSyncResult.UnexpectedError)
        assertEquals(0, markedSyncedTripIds.size)
    }

    @Test
    fun syncViaSheetsApi_malformedRowData_failsClosed() = runTest {
        tripsList.add(createTrip("trip_bad_row", "BUSINESS", 4.0))
        configureSheetsApi("test_sheet", "valid_tok")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                // Return 'values' where elements are not arrays
                Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{\"range\":\"Sheet1!A:A\",\"values\":[123, true]}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue("Malformed row data must fail closed", result is SheetsSyncResult.UnexpectedError)
        assertEquals(0, markedSyncedTripIds.size)
    }

    @Test
    fun syncViaSheetsApi_validEmptySheet_initializesHeaderAndAppendsRows() = runTest {
        tripsList.add(createTrip("trip_empty_sheet", "BUSINESS", 7.5))
        configureSheetsApi("test_sheet", "valid_tok")

        var headerPutCalled = false
        var rowAppended = false

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.method == "GET") {
                    // Valid empty range response from Google Sheets (omits "values")
                    val body = "{\"range\":\"Sheet1!A:A\",\"majorDimension\":\"ROWS\"}"
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                } else if (req.method == "PUT" && req.url.toString().contains("A1:P1")) {
                    headerPutCalled = true
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("{\"updatedRows\":1}".toResponseBody("application/json".toMediaType())).build()
                } else if (req.method == "POST") {
                    rowAppended = true
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("{\"updates\":{\"updatedRows\":1}}".toResponseBody("application/json".toMediaType())).build()
                } else {
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("{}".toResponseBody("application/json".toMediaType())).build()
                }
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue("Valid empty sheet should succeed", result is SheetsSyncResult.Success)
        assertTrue("Header row must be written", headerPutCalled)
        assertTrue("Trip row must be appended", rowAppended)
        assertEquals("SYNCED", tripsList[0].syncStatus)
    }

    @Test
    fun syncViaSheetsApi_preservesExactHttpStatus_onWriteFailure() = runTest {
        tripsList.add(createTrip("trip_status_test", "BUSINESS", 6.0))
        configureSheetsApi("test_sheet", "valid_tok")

        // Test 403 Forbidden
        val service403 = createService()
        service403.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.method == "GET") {
                    val body = "{\"values\":[[\"Trip ID\"]]}"
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                } else {
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(403).message("Forbidden")
                        .body("{\"error\":{\"code\":403,\"message\":\"The caller does not have permission\"}}".toResponseBody("application/json".toMediaType())).build()
                }
            }
            .build()

        val res403 = service403.syncPendingTrips()
        assertTrue("Must return AccessDenied on 403 write failure, not 500", res403 is SheetsSyncResult.AccessDenied)

        // Test 429 Rate Limit
        val service429 = createService()
        service429.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.method == "GET") {
                    val body = "{\"values\":[[\"Trip ID\"]]}"
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                } else {
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(429).message("Too Many Requests")
                        .body("{\"error\":{\"code\":429}}".toResponseBody("application/json".toMediaType())).build()
                }
            }
            .build()

        val res429 = service429.syncPendingTrips()
        assertTrue("Must return RateLimited on 429 write failure, not 500", res429 is SheetsSyncResult.RateLimited)

        // Test 503 Server Error
        val service503 = createService()
        service503.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.method == "GET") {
                    val body = "{\"values\":[[\"Trip ID\"]]}"
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                } else {
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(503).message("Service Unavailable")
                        .body("{}".toResponseBody("application/json".toMediaType())).build()
                }
            }
            .build()

        val res503 = service503.syncPendingTrips()
        assertTrue("Must return ServerError on 503 write failure", res503 is SheetsSyncResult.ServerError)
        assertEquals(503, (res503 as SheetsSyncResult.ServerError).statusCode)
    }

    @Test
    fun syncViaWebhook_missingSyncedTripIds_failsClosedWithoutMarkingTrips() = runTest {
        tripsList.add(createTrip("trip_webhook_1", "BUSINESS", 5.0))
        configureWebhook("https://example.com/macros/exec")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                // Response with HTTP 200 but omitting 'syncedTripIds'
                Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{\"status\":\"success\",\"message\":\"Processed successfully\"}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue("Must fail closed with HttpError when syncedTripIds array is missing", result is SheetsSyncResult.HttpError)
        assertEquals("Zero trips should be marked synced", 0, markedSyncedTripIds.size)
        assertEquals("Trip must remain pending", "PENDING", tripsList[0].syncStatus)
    }

    @Test
    fun syncViaWebhook_partialConfirmation_leavesUnconfirmedPending() = runTest {
        tripsList.add(createTrip("trip_w1", "BUSINESS", 5.0))
        tripsList.add(createTrip("trip_w2", "BUSINESS", 8.0))
        configureWebhook("https://example.com/macros/exec")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                // Explicitly confirm ONLY trip_w1
                Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{\"status\":\"success\",\"syncedTripIds\":[\"trip_w1\"]}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue("Must return PartialSuccess", result is SheetsSyncResult.PartialSuccess)
        val partial = result as SheetsSyncResult.PartialSuccess
        assertEquals(1, partial.successCount)
        assertEquals(1, partial.failedCount)

        assertEquals(listOf("trip_w1"), markedSyncedTripIds)
        assertEquals("SYNCED", tripsList[0].syncStatus)
        assertEquals("PENDING", tripsList[1].syncStatus)
    }

    @Test
    fun syncViaWebhook_unknownTripIds_marksOnlyMatchingSubmittedTrips() = runTest {
        tripsList.add(createTrip("trip_target", "BUSINESS", 12.0))
        configureWebhook("https://example.com/macros/exec")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                // Confirms an unknown trip ID alongside valid trip ID
                Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{\"status\":\"success\",\"syncedTripIds\":[\"trip_target\",\"unknown_ghost_id\"]}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue("Must confirm submitted trip", result is SheetsSyncResult.Success)
        assertEquals(listOf("trip_target"), markedSyncedTripIds)
        assertEquals("SYNCED", tripsList[0].syncStatus)
    }

    @Test
    fun syncViaWebhook_explicitFailure_failsClosed() = runTest {
        tripsList.add(createTrip("trip_fail", "BUSINESS", 5.0))
        configureWebhook("https://example.com/macros/exec")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{\"status\":\"error\",\"message\":\"Daily quota exceeded\"}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val result = service.syncPendingTrips()
        assertTrue("Explicit failure status must fail closed", result is SheetsSyncResult.HttpError)
        assertEquals(0, markedSyncedTripIds.size)
        assertEquals("PENDING", tripsList[0].syncStatus)
    }
}
