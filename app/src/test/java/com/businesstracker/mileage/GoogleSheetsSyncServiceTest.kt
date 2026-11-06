package com.businesstracker.mileage

import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.remote.GoogleSheetsSyncService
import com.businesstracker.mileage.data.remote.SheetsSyncResult
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.domain.repository.TripRepository
import com.google.gson.Gson
import kotlinx.coroutines.async
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
        secureStorageManager = SecureStorageManager(context)
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
        preferencesManager.setSheetsSpreadsheetId("test_sheet_id")
        secureStorageManager.saveOAuthToken("valid_test_token")
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

        preferencesManager.setSheetsSpreadsheetId("test_sheet_123")
        secureStorageManager.saveOAuthToken("test_valid_token")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.method == "GET") {
                    // Empty sheet with header
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

        preferencesManager.setSheetsSpreadsheetId("test_sheet_123")
        secureStorageManager.saveOAuthToken("test_valid_token")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.method == "GET") {
                    val body = "{\"values\":[[\"Trip ID\"]]}"
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                } else {
                    // Check if request body contains "trip_fail"
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

        // Only trip_ok is marked synced; trip_fail remains PENDING!
        assertEquals(listOf("trip_ok"), markedSyncedTripIds)
        assertEquals("SYNCED", tripsList.first { it.id == "trip_ok" }.syncStatus)
        assertEquals("PENDING", tripsList.first { it.id == "trip_fail" }.syncStatus)
    }

    @Test
    fun syncViaSheetsApi_oneRowUpdateFails_preservesFailedRowAsPending() = runTest {
        tripsList.add(createTrip("trip_exist_1", "BUSINESS", 10.0))
        tripsList.add(createTrip("trip_exist_2", "BUSINESS", 20.0))

        preferencesManager.setSheetsSpreadsheetId("test_sheet_123")
        secureStorageManager.saveOAuthToken("test_valid_token")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.method == "GET") {
                    // Both exist in Column A: row 2 and row 3
                    val body = "{\"values\":[[\"Trip ID\"],[\"trip_exist_1\"],[\"trip_exist_2\"]]}"
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                } else if (req.method == "PUT") {
                    if (req.url.toString().contains("A3:P3")) {
                        // Fail row 3 update
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
        preferencesManager.setSheetsSpreadsheetId("test_sheet")
        secureStorageManager.saveOAuthToken("expired_tok")

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
        preferencesManager.setSheetsSpreadsheetId("test_sheet")
        secureStorageManager.saveOAuthToken("valid_tok")

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
        preferencesManager.setSheetsSpreadsheetId("nonexistent_id")
        secureStorageManager.saveOAuthToken("valid_tok")

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
        preferencesManager.setSheetsSpreadsheetId("test_sheet")
        secureStorageManager.saveOAuthToken("valid_tok")

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
        preferencesManager.setSheetsSpreadsheetId("test_sheet")
        secureStorageManager.saveOAuthToken("valid_tok")

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
        preferencesManager.setSheetsWebhookUrl("http://insecure.example.com/sync") // Insecure HTTP!
        val service = createService()

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.InvalidConfiguration)
        assertEquals(0, markedSyncedTripIds.size)
    }

    @Test
    fun syncViaWebhook_success_marksTripsSynced() = runTest {
        val trip = createTrip("trip_web_1", "BUSINESS", 20.0)
        tripsList.add(trip)

        preferencesManager.setSheetsWebhookUrl("https://example.com/macros/exec")

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{\"status\":\"success\"}".toResponseBody("application/json".toMediaType())).build()
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
        preferencesManager.setSheetsWebhookUrl("https://example.com/macros/exec")

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
        preferencesManager.setSheetsWebhookUrl("https://example.com/macros/exec")

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
        preferencesManager.setSheetsWebhookUrl("https://example.com/macros/exec")

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
        // Initially synced trip
        val trip = createTrip("trip_edit", "BUSINESS", 10.0, syncStatus = "SYNCED")
        tripsList.add(trip)

        // User edits trip classification/notes in app, setting syncStatus back to PENDING
        fakeRepo.updateTripDetails("trip_edit", "BUSINESS", "MySucculentCorner", "Client Delivery", "Updated note")
        assertEquals("PENDING", tripsList[0].syncStatus)

        preferencesManager.setSheetsSpreadsheetId("test_sheet")
        secureStorageManager.saveOAuthToken("valid_tok")

        var putUrlCalled: String? = null
        var postCalled = false

        val service = createService()
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.method == "GET") {
                    // Column A already has "trip_edit" at row 4
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
        preferencesManager.setSheetsSpreadsheetId("test_sheet")
        secureStorageManager.saveOAuthToken("valid_tok")

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

        // Launch two concurrent sync operations simultaneously
        val deferred1 = async { service.syncPendingTrips() }
        val deferred2 = async { service.syncPendingTrips() }

        val res1 = deferred1.await()
        val res2 = deferred2.await()

        assertTrue(res1 is SheetsSyncResult.Success)
        assertTrue(res2 is SheetsSyncResult.Success)

        // Mutex ensures only one append occurred; second sync saw 0 pending trips!
        assertEquals(1, appendCallCount)
        assertEquals(listOf("trip_concurrent"), markedSyncedTripIds)
    }
}
