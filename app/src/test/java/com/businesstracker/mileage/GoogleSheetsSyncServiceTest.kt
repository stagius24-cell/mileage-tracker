package com.businesstracker.mileage

import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.remote.GoogleSheetsSyncService
import com.businesstracker.mileage.data.remote.SheetsSyncResult
import com.businesstracker.mileage.domain.repository.TripRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class GoogleSheetsSyncServiceTest {

    private lateinit var preferencesManager: TrackingPreferencesManager
    private val markedSyncedTripIds = mutableListOf<String>()
    private val tripsList = mutableListOf<TripEntity>()

    private fun createTrip(id: String, classification: String, distance: Double): TripEntity {
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
            syncStatus = "PENDING",
            syncedAt = null
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
        override suspend fun insertTrip(trip: TripEntity) {}
        override suspend fun updateTrip(trip: TripEntity) {}
        override suspend fun deleteTrip(tripId: String) {}
        override suspend fun classifyTrip(tripId: String, classification: String, businessEntity: String, purpose: String, notes: String?) {}
        override suspend fun updateTripDetails(tripId: String, classification: String, businessEntity: String, purpose: String, notes: String?) {}
        override fun getTripsBySyncStatus(syncStatus: String) = flowOf(emptyList<TripEntity>())
        override fun getTripsByClassification(classification: String) = flowOf(emptyList<TripEntity>())
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
    }

    @Test
    fun formatTripToRow_hasExact16ColumnsInCorrectOrder() {
        val service = GoogleSheetsSyncService(preferencesManager, fakeRepo, Gson())
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
        val service = GoogleSheetsSyncService(preferencesManager, fakeRepo, Gson())

        val result = service.syncPendingTrips()
        assertTrue(result is SheetsSyncResult.NotConfigured)
    }

    @Test
    fun syncPendingTrips_unreviewedTripsAreNeverUploaded() = runTest {
        tripsList.add(createTrip("unreviewed_1", "UNREVIEWED", 15.0))
        preferencesManager.setSheetsSpreadsheetId("test_sheet_id")
        preferencesManager.setSheetsAuthToken("test_token")
        val service = GoogleSheetsSyncService(preferencesManager, fakeRepo, Gson())

        val result = service.syncPendingTrips()
        // No eligible trips should be sent
        assertTrue(result is SheetsSyncResult.Success)
        assertEquals(0, (result as SheetsSyncResult.Success).syncedCount)
        assertEquals(0, markedSyncedTripIds.size)
    }

    @Test
    fun syncViaWebhook_success_marksTripsSynced() = runTest {
        val trip = createTrip("trip_web_1", "BUSINESS", 20.0)
        tripsList.add(trip)

        preferencesManager.setSheetsWebhookUrl("https://example.com/macros/exec")

        val service = GoogleSheetsSyncService(preferencesManager, fakeRepo, Gson())
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{\"status\":\"success\"}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val result = service.syncPendingTrips()

        assertTrue(result is SheetsSyncResult.Success)
        assertEquals(1, (result as SheetsSyncResult.Success).syncedCount)
        assertEquals(listOf("trip_web_1"), markedSyncedTripIds)
        assertEquals("SYNCED", tripsList[0].syncStatus)
    }

    @Test
    fun syncViaWebhook_serverError_preservesLocalData() = runTest {
        val trip = createTrip("trip_fail_1", "BUSINESS", 10.0)
        tripsList.add(trip)

        preferencesManager.setSheetsWebhookUrl("https://example.com/macros/exec")

        val service = GoogleSheetsSyncService(preferencesManager, fakeRepo, Gson())
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Server Error")
                    .body("Error".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()

        val result = service.syncPendingTrips()

        assertTrue(result is SheetsSyncResult.HttpError)
        assertEquals(500, (result as SheetsSyncResult.HttpError).statusCode)
        // Local data is NOT marked synced or deleted!
        assertEquals(0, markedSyncedTripIds.size)
        assertEquals("PENDING", tripsList[0].syncStatus)
    }

    @Test
    fun syncViaSheetsApi_tokenExpired_returnsTokenExpired() = runTest {
        val trip = createTrip("trip_exp_1", "BUSINESS", 10.0)
        tripsList.add(trip)

        preferencesManager.setSheetsSpreadsheetId("test_spreadsheet_id")
        preferencesManager.setSheetsAuthToken("expired_token")
        preferencesManager.setSheetsWebhookUrl("")

        val service = GoogleSheetsSyncService(preferencesManager, fakeRepo, Gson())
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("{\"error\": \"Invalid Credentials\"}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val result = service.syncPendingTrips()

        assertTrue(result is SheetsSyncResult.TokenExpired)
        // Local data remains PENDING
        assertEquals(0, markedSyncedTripIds.size)
        assertEquals("PENDING", tripsList[0].syncStatus)
    }

    @Test
    fun syncViaSheetsApi_duplicatePrevention_updatesExistingRow() = runTest {
        val trip = createTrip("trip_dup_1", "BUSINESS", 15.0)
        tripsList.add(trip)

        preferencesManager.setSheetsSpreadsheetId("test_sheet")
        preferencesManager.setSheetsAuthToken("valid_token")
        preferencesManager.setSheetsWebhookUrl("")

        var putRequestUrl: String? = null

        val service = GoogleSheetsSyncService(preferencesManager, fakeRepo, Gson())
        service.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.method == "GET" && req.url.encodedPath.contains("values/Sheet1!A:A")) {
                    // Return existing column A with "Trip ID" header and "trip_dup_1" at row 2
                    val responseJson = "{\"range\":\"Sheet1!A:A\",\"values\":[[\"Trip ID\"],[\"trip_dup_1\"]]}"
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(responseJson.toResponseBody("application/json".toMediaType()))
                        .build()
                } else if (req.method == "PUT") {
                    putRequestUrl = req.url.toString()
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{\"updatedRows\":1}".toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }
            .build()

        val result = service.syncPendingTrips()

        assertTrue(result is SheetsSyncResult.Success)
        val success = result as SheetsSyncResult.Success
        assertEquals(0, success.syncedCount)
        assertEquals(1, success.updatedCount) // 1 updated, 0 duplicates inserted!
        assertTrue(putRequestUrl?.contains("Sheet1!A2:P2") == true)
        assertEquals(listOf("trip_dup_1"), markedSyncedTripIds)
    }
}
