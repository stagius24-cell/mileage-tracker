package com.businesstracker.mileage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.remote.CustomPurposesRestoreResult
import com.businesstracker.mileage.data.remote.CustomPurposesSheetsService
import com.businesstracker.mileage.data.remote.CustomPurposesSyncResult
import com.businesstracker.mileage.data.security.GoogleAuthManager
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.data.security.TestSoftwareKeyProvider
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
class CustomPurposesSheetsServiceTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var secureStorageManager: SecureStorageManager
    private val gson = Gson()
    private val secretToken = "valid_test_token_123"

    private class RecordedHttpCall(
        val method: String,
        val url: String,
        val authHeader: String?,
        val body: String
    )

    private class FakeSheetsInterceptor(
        private val gson: Gson
    ) : okhttp3.Interceptor {
        val sheets = linkedMapOf<String, Int>()
        val sheetValues = linkedMapOf<String, MutableList<MutableList<Any?>>>()
        val calls = mutableListOf<RecordedHttpCall>()
        var forceUnauthorizedCount = 0

        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            val req = chain.request()
            val buffer = Buffer()
            req.body?.writeTo(buffer)
            val bodyString = buffer.readUtf8()
            calls.add(RecordedHttpCall(req.method, req.url.toString(), req.header("Authorization"), bodyString))

            if (forceUnauthorizedCount > 0) {
                forceUnauthorizedCount--
                return jsonResponse(req, 401, """{"error":{"code":401,"message":"Invalid Credentials"}}""")
            }

            val decodedUrl = URLDecoder.decode(req.url.toString(), StandardCharsets.UTF_8.name())
            val path = req.url.encodedPath

            if (req.method == "GET" && path.endsWith("/spreadsheets/test-sheet-id")) {
                val sheetList = JsonArray()
                sheets.forEach { (title, id) ->
                    val sObj = JsonObject()
                    val pObj = JsonObject()
                    pObj.addProperty("title", title)
                    pObj.addProperty("sheetId", id)
                    sObj.add("properties", pObj)
                    sheetList.add(sObj)
                }
                val root = JsonObject().apply { add("sheets", sheetList) }
                return jsonResponse(req, 200, gson.toJson(root))
            }

            if (req.method == "POST" && path.endsWith(":batchUpdate")) {
                val parsed = gson.fromJson(bodyString, JsonObject::class.java)
                val replies = JsonArray()
                parsed?.getAsJsonArray("requests")?.forEach { elem ->
                    val addSheet = elem.asJsonObject.getAsJsonObject("addSheet")
                    if (addSheet != null) {
                        val title = addSheet.getAsJsonObject("properties").get("title").asString
                        val newId = 555
                        sheets[title] = newId
                        val repObj = JsonObject()
                        val repProp = JsonObject().apply {
                            addProperty("sheetId", newId)
                            addProperty("title", title)
                        }
                        repObj.add("addSheet", JsonObject().apply { add("properties", repProp) })
                        replies.add(repObj)
                    }
                }
                return jsonResponse(req, 200, gson.toJson(JsonObject().apply { add("replies", replies) }))
            }

            if (req.method == "GET" && decodedUrl.contains("/values/'Custom Purposes'!A1:B1")) {
                val rows = sheetValues["Custom Purposes"].orEmpty()
                val headerRow = rows.firstOrNull() ?: emptyList<Any?>()
                val valuesArr = JsonArray().apply {
                    if (headerRow.isNotEmpty()) {
                        val rowArr = JsonArray()
                        headerRow.forEach { rowArr.add(it?.toString().orEmpty()) }
                        add(rowArr)
                    }
                }
                val root = JsonObject().apply { add("values", valuesArr) }
                return jsonResponse(req, 200, gson.toJson(root))
            }

            if (req.method == "PUT" && decodedUrl.contains("/values/'Custom Purposes'!A1:B1")) {
                val list = sheetValues.getOrPut("Custom Purposes") { mutableListOf() }
                if (list.isEmpty()) {
                    list.add(mutableListOf("Purpose", "Last Updated"))
                } else {
                    list[0] = mutableListOf("Purpose", "Last Updated")
                }
                return jsonResponse(req, 200, """{"updatedRows":1}""")
            }

            if (req.method == "GET" && (decodedUrl.contains("Custom Purposes'!A:B") || decodedUrl.contains("Custom Purposes!A:B"))) {
                val allRows = sheetValues["Custom Purposes"].orEmpty()
                val valuesArr = JsonArray()
                allRows.forEach { row ->
                    val rowArr = JsonArray()
                    row.forEach { rowArr.add(it?.toString().orEmpty()) }
                    valuesArr.add(rowArr)
                }
                val root = JsonObject().apply { add("values", valuesArr) }
                return jsonResponse(req, 200, gson.toJson(root))
            }

            if (req.method == "GET" && (decodedUrl.contains("Custom Purposes'!A2:B") || decodedUrl.contains("Custom Purposes!A2:B"))) {
                val allRows = sheetValues["Custom Purposes"].orEmpty()
                val dataRows = if (allRows.size > 1) allRows.subList(1, allRows.size) else emptyList()
                val valuesArr = JsonArray()
                dataRows.forEach { row ->
                    val rowArr = JsonArray()
                    row.forEach { rowArr.add(it?.toString().orEmpty()) }
                    valuesArr.add(rowArr)
                }
                val root = JsonObject().apply { add("values", valuesArr) }
                return jsonResponse(req, 200, gson.toJson(root))
            }

            if (req.method == "GET" && (decodedUrl.contains("Sheet1!A2:K") || decodedUrl.contains("Sheet1!A:K"))) {
                val allRows = sheetValues["Sheet1"].orEmpty()
                val valuesArr = JsonArray()
                allRows.forEach { row ->
                    val rowArr = JsonArray()
                    row.forEach { rowArr.add(it?.toString().orEmpty()) }
                    valuesArr.add(rowArr)
                }
                val root = JsonObject().apply { add("values", valuesArr) }
                return jsonResponse(req, 200, gson.toJson(root))
            }

            if (req.method == "POST" && decodedUrl.contains("/values/'Custom Purposes'!A2:B:clear")) {
                val allRows = sheetValues["Custom Purposes"]
                if (allRows != null && allRows.isNotEmpty()) {
                    val header = allRows[0]
                    allRows.clear()
                    allRows.add(header)
                }
                return jsonResponse(req, 200, """{"clearedRange":"'Custom Purposes'!A2:B"}""")
            }

            if (req.method == "PUT" && decodedUrl.contains("/values/'Custom Purposes'!A2:B")) {
                val parsed = gson.fromJson(bodyString, JsonObject::class.java)
                val valuesArr = parsed?.getAsJsonArray("values")
                val allRows = sheetValues.getOrPut("Custom Purposes") { mutableListOf(mutableListOf("Purpose", "Last Updated")) }
                while (allRows.size > 1) {
                    allRows.removeAt(allRows.size - 1)
                }
                valuesArr?.forEach { rowElem ->
                    val rowArr = rowElem.asJsonArray
                    val r = mutableListOf<Any?>()
                    for (i in 0 until rowArr.size()) {
                        r.add(rowArr.get(i).asString)
                    }
                    allRows.add(r)
                }
                return jsonResponse(req, 200, """{"updatedRows":${valuesArr?.size() ?: 0}}""")
            }

            return jsonResponse(req, 404, """{"error":{"code":404,"message":"Not Found"}}""")
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
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            preferencesManager = TrackingPreferencesManager(context)
            secureStorageManager = SecureStorageManager(context, TestSoftwareKeyProvider())
            preferencesManager.setSheetsConnected(true)
            preferencesManager.setSheetsSpreadsheetId("test-sheet-id")
            secureStorageManager.saveOAuthToken(secretToken)
        }
    }

    private fun createService(interceptor: FakeSheetsInterceptor, authManager: GoogleAuthManager? = null): CustomPurposesSheetsService {
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        return CustomPurposesSheetsService(
            preferencesManager = preferencesManager,
            secureStorage = secureStorageManager,
            googleAuthManager = authManager,
            gson = gson,
            ioDispatcher = Dispatchers.Unconfined,
            sheetsApiBaseUrl = "https://sheets.googleapis.com",
            client = client
        )
    }

    @Test
    fun backupCustomPurposes_whenNotConfigured_returnsNotConfigured() = runBlocking {
        preferencesManager.setSheetsConnected(false)
        val interceptor = FakeSheetsInterceptor(gson)
        val service = createService(interceptor)

        val result = service.backupCustomPurposes()
        assertTrue(result is CustomPurposesSyncResult.NotConfigured)
    }

    @Test
    fun backupCustomPurposes_createsSheetAndWritesPurposes() = runBlocking {
        val interceptor = FakeSheetsInterceptor(gson)
        val service = createService(interceptor)

        preferencesManager.setCustomPurposes(listOf("Client Meeting", "Supply Run", "Inventory Pickup"))

        val result = service.backupCustomPurposes()
        assertTrue(result is CustomPurposesSyncResult.Success)
        assertEquals(3, (result as CustomPurposesSyncResult.Success).count)

        assertTrue(interceptor.sheets.containsKey("Custom Purposes"))
        val rows = interceptor.sheetValues["Custom Purposes"]
        assertEquals(4, rows?.size) // Header + 3 purposes
        assertEquals("Purpose", rows?.get(0)?.get(0))
        assertEquals("Client Meeting", rows?.get(1)?.get(0))
        assertEquals("Supply Run", rows?.get(2)?.get(0))
        assertEquals("Inventory Pickup", rows?.get(3)?.get(0))
    }

    @Test
    fun backupCustomPurposes_clearsStaleRowsWhenLocalCountDecreases() = runBlocking {
        val interceptor = FakeSheetsInterceptor(gson)
        interceptor.sheets["Custom Purposes"] = 555
        interceptor.sheetValues["Custom Purposes"] = mutableListOf(
            mutableListOf("Purpose", "Last Updated"),
            mutableListOf("Old 1", "2026-01-01"),
            mutableListOf("Old 2", "2026-01-01"),
            mutableListOf("Old 3", "2026-01-01")
        )

        val service = createService(interceptor)
        preferencesManager.setCustomPurposes(listOf("Client Meeting"))

        val result = service.backupCustomPurposes()
        assertTrue(result is CustomPurposesSyncResult.Success)
        assertEquals(1, (result as CustomPurposesSyncResult.Success).count)

        val rows = interceptor.sheetValues["Custom Purposes"]
        assertEquals(2, rows?.size) // Header + 1 purpose
        assertEquals("Client Meeting", rows?.get(1)?.get(0))
    }

    @Test
    fun restoreCustomPurposes_mergesPurposesIntoPreferences() = runBlocking {
        val interceptor = FakeSheetsInterceptor(gson)
        interceptor.sheets["Custom Purposes"] = 555
        interceptor.sheetValues["Custom Purposes"] = mutableListOf(
            mutableListOf("Purpose", "Last Updated"),
            mutableListOf("Vendor Visit", "2026-11-14T00:00:00Z"),
            mutableListOf("Bank Deposit", "2026-11-14T00:00:00Z")
        )

        val service = createService(interceptor)
        preferencesManager.setCustomPurposes(listOf("Client Meeting"))

        val result = service.restoreCustomPurposes()
        assertTrue(result is CustomPurposesRestoreResult.Success)
        val success = result as CustomPurposesRestoreResult.Success
        assertEquals(2, success.addedCount)
        assertEquals(2, success.totalCloudCount)

        val currentList = preferencesManager.getCustomPurposesList()
        assertTrue(currentList.contains("Client Meeting"))
        assertTrue(currentList.contains("Vendor Visit"))
        assertTrue(currentList.contains("Bank Deposit"))
    }

    @Test
    fun restoreCustomPurposes_emptySheet_returnsEmpty() = runBlocking {
        val interceptor = FakeSheetsInterceptor(gson)
        interceptor.sheets["Custom Purposes"] = 555
        interceptor.sheetValues["Custom Purposes"] = mutableListOf(
            mutableListOf("Purpose", "Last Updated")
        )

        val service = createService(interceptor)
        val result = service.restoreCustomPurposes()
        assertTrue(result is CustomPurposesRestoreResult.Empty)
    }

    @Test
    fun restoreCustomPurposes_nonExistentSheet_returnsEmpty() = runBlocking {
        val interceptor = FakeSheetsInterceptor(gson)
        val service = createService(interceptor)

        val result = service.restoreCustomPurposes()
        assertTrue(result is CustomPurposesRestoreResult.Empty)
    }

    @Test
    fun restoreCustomPurposes_whenPurposesOnlyInSheet1Trips_restoresThem() = runBlocking {
        val interceptor = FakeSheetsInterceptor(gson)
        interceptor.sheets["Sheet1"] = 0
        // Header + row where column K (index 10) is "Succulent Pop-up Sale"
        val tripRow = (0..18).map { "" }.toMutableList<Any?>()
        tripRow[0] = "trip-1"
        tripRow[8] = "BUSINESS"
        tripRow[10] = "Succulent Pop-up Sale"
        interceptor.sheetValues["Sheet1"] = mutableListOf(tripRow)

        val service = createService(interceptor)
        val result = service.restoreCustomPurposes()
        assertTrue(result is CustomPurposesRestoreResult.Success)
        val success = result as CustomPurposesRestoreResult.Success
        assertEquals(1, success.addedCount)
        assertTrue(preferencesManager.getCustomPurposesList().contains("Succulent Pop-up Sale"))
    }
}
