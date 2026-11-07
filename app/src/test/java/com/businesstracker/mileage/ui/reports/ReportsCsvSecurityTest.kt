package com.businesstracker.mileage.ui.reports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReportsCsvSecurityTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createDummyTrip(
        id: String = "test-1",
        startAddress: String = "100 Main St",
        endAddress: String = "200 Market St",
        purpose: String = "Nursery Pickup",
        notes: String? = null
    ): TripEntity {
        return TripEntity(
            id = id,
            date = "2026-06-15",
            startTime = 1000L,
            endTime = 2000L,
            startAddress = startAddress,
            endAddress = endAddress,
            startLatitude = 37.77,
            startLongitude = -122.41,
            endLatitude = 37.78,
            endLongitude = -122.42,
            totalDistanceMiles = 25.0,
            businessMiles = 25.0,
            businessEntity = "MySucculentCorner",
            purpose = purpose,
            trackingMethod = "AUTO_DETECTED",
            classification = "BUSINESS",
            applicableRate = 0.760,
            calculatedDeduction = 19.00,
            notes = notes,
            waypointsJson = "[]",
            syncStatus = "SYNCED",
            syncedAt = 1000L
        )
    }

    @Test
    fun escapeCsv_sanitizesFormulaInjectionPrefixes() {
        val dummyRepo = object : TripRepository {
            override fun getAllTrips() = flowOf(emptyList<TripEntity>())
            override fun getUnclassifiedTrips() = flowOf(emptyList<TripEntity>())
            override fun getUnclassifiedTripsCount() = flowOf(0)
            override fun getTripsByDateRange(s: String, e: String) = flowOf(emptyList<TripEntity>())
            override fun getTripsByBusinessEntity(entity: String) = flowOf(emptyList<TripEntity>())
            override fun getBusinessEntitySummaries() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
            override fun getBusinessEntitySummariesForDateRange(s: String, e: String) = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
            override fun getTripCountByClassification() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.ClassificationCount>())
            override suspend fun getTripById(id: String) = null
            override fun observeTripById(id: String) = flowOf(null)
            override suspend fun insertTrip(trip: TripEntity) {}
            override suspend fun updateTrip(trip: TripEntity) {}
            override suspend fun deleteTrip(id: String) {}
            override suspend fun classifyTrip(id: String, c: String, b: String, p: String, n: String?) {}
            override suspend fun updateTripDetails(id: String, c: String, b: String, p: String, n: String?) {}
            override fun getTripsBySyncStatus(s: String) = flowOf(emptyList<TripEntity>())
            override fun getTripsByClassification(c: String) = flowOf(emptyList<TripEntity>())
            override suspend fun getPendingSyncTrips() = emptyList<TripEntity>()
            override suspend fun markSynced(id: String, t: Long) {}
        }
        val viewModel = ReportsExportViewModel(dummyRepo, CalculateMileageRateUseCase(), context)

        // Verifies prefixes: =, +, -, @, \t, \r, |, %
        assertEquals("'=SUM(A1:A10)", viewModel.escapeCsv("=SUM(A1:A10)"))
        assertEquals("'+123456", viewModel.escapeCsv("+123456"))
        assertEquals("'-cmd|' /C calc'!A0", viewModel.escapeCsv("-cmd|' /C calc'!A0"))
        assertEquals("'@SUM(1,2)", viewModel.escapeCsv("@SUM(1,2)"))
        assertEquals("'\tTabSeparated", viewModel.escapeCsv("\tTabSeparated"))
        assertEquals("'\rReturnSeparated", viewModel.escapeCsv("\rReturnSeparated"))
        assertEquals("'\nNewlineSeparated", viewModel.escapeCsv("\nNewlineSeparated"))
        assertEquals("'|pipeTrigger", viewModel.escapeCsv("|pipeTrigger"))
        assertEquals("'%percentTrigger", viewModel.escapeCsv("%percentTrigger"))
    }

    @Test
    fun escapeCsv_neutralizesLeadingWhitespaceAndTabsAndNewlinesWithFormulaTriggers() {
        val dummyRepo = object : TripRepository {
            override fun getAllTrips() = flowOf(emptyList<TripEntity>())
            override fun getUnclassifiedTrips() = flowOf(emptyList<TripEntity>())
            override fun getUnclassifiedTripsCount() = flowOf(0)
            override fun getTripsByDateRange(s: String, e: String) = flowOf(emptyList<TripEntity>())
            override fun getTripsByBusinessEntity(entity: String) = flowOf(emptyList<TripEntity>())
            override fun getBusinessEntitySummaries() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
            override fun getBusinessEntitySummariesForDateRange(s: String, e: String) = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
            override fun getTripCountByClassification() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.ClassificationCount>())
            override suspend fun getTripById(id: String) = null
            override fun observeTripById(id: String) = flowOf(null)
            override suspend fun insertTrip(trip: TripEntity) {}
            override suspend fun updateTrip(trip: TripEntity) {}
            override suspend fun deleteTrip(id: String) {}
            override suspend fun classifyTrip(id: String, c: String, b: String, p: String, n: String?) {}
            override suspend fun updateTripDetails(id: String, c: String, b: String, p: String, n: String?) {}
            override fun getTripsBySyncStatus(s: String) = flowOf(emptyList<TripEntity>())
            override fun getTripsByClassification(c: String) = flowOf(emptyList<TripEntity>())
            override suspend fun getPendingSyncTrips() = emptyList<TripEntity>()
            override suspend fun markSynced(id: String, t: Long) {}
        }
        val viewModel = ReportsExportViewModel(dummyRepo, CalculateMileageRateUseCase(), context)

        // Leading spaces followed by formula triggers
        assertEquals("'   =SUM(1,2)", viewModel.escapeCsv("   =SUM(1,2)"))
        assertEquals("'  +4412345", viewModel.escapeCsv("  +4412345"))
        assertEquals("' -cmd|' /C calc'!A0", viewModel.escapeCsv(" -cmd|' /C calc'!A0"))
        assertEquals("'    @dangerous_macro", viewModel.escapeCsv("    @dangerous_macro"))
        assertEquals("'  |pipeTrigger", viewModel.escapeCsv("  |pipeTrigger"))
        assertEquals("'  %percentTrigger", viewModel.escapeCsv("  %percentTrigger"))

        // Leading tabs and carriage returns / newlines before formula
        assertEquals("'\t=cmd", viewModel.escapeCsv("\t=cmd"))
        assertEquals("'\r+2+5", viewModel.escapeCsv("\r+2+5"))
        assertEquals("'\n-10", viewModel.escapeCsv("\n-10"))
        assertEquals("' \t  =SUM(A1:A5)", viewModel.escapeCsv(" \t  =SUM(A1:A5)"))

        // Neutral whitespace without triggers
        assertEquals("   ", viewModel.escapeCsv("   "))
        assertEquals("", viewModel.escapeCsv(""))
        assertEquals("Just regular notes", viewModel.escapeCsv("Just regular notes"))
    }

    @Test
    fun escapeCsv_preservesOrdinaryTextAndQuotesAndUnicode() {
        val dummyRepo = object : TripRepository {
            override fun getAllTrips() = flowOf(emptyList<TripEntity>())
            override fun getUnclassifiedTrips() = flowOf(emptyList<TripEntity>())
            override fun getUnclassifiedTripsCount() = flowOf(0)
            override fun getTripsByDateRange(s: String, e: String) = flowOf(emptyList<TripEntity>())
            override fun getTripsByBusinessEntity(entity: String) = flowOf(emptyList<TripEntity>())
            override fun getBusinessEntitySummaries() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
            override fun getBusinessEntitySummariesForDateRange(s: String, e: String) = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
            override fun getTripCountByClassification() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.ClassificationCount>())
            override suspend fun getTripById(id: String) = null
            override fun observeTripById(id: String) = flowOf(null)
            override suspend fun insertTrip(trip: TripEntity) {}
            override suspend fun updateTrip(trip: TripEntity) {}
            override suspend fun deleteTrip(id: String) {}
            override suspend fun classifyTrip(id: String, c: String, b: String, p: String, n: String?) {}
            override suspend fun updateTripDetails(id: String, c: String, b: String, p: String, n: String?) {}
            override fun getTripsBySyncStatus(s: String) = flowOf(emptyList<TripEntity>())
            override fun getTripsByClassification(c: String) = flowOf(emptyList<TripEntity>())
            override suspend fun getPendingSyncTrips() = emptyList<TripEntity>()
            override suspend fun markSynced(id: String, t: Long) {}
        }
        val viewModel = ReportsExportViewModel(dummyRepo, CalculateMileageRateUseCase(), context)

        // Ordinary text
        assertEquals("Nursery / Plant Pickup", viewModel.escapeCsv("Nursery / Plant Pickup"))
        // Quotes doubled
        assertEquals("Ernie\"\"s Pots", viewModel.escapeCsv("Ernie\"s Pots"))
        // Unicode text
        assertEquals("Hāneelove • 100% Cotton 🌿", viewModel.escapeCsv("Hāneelove • 100% Cotton 🌿"))
    }

    @Test
    fun exportToCsv_withMaliciousPayload_escapesSafelyAndPreservesOriginalData() = runTest(testDispatcher) {
        val appCtx = RuntimeEnvironment.getApplication()
        val maliciousTrip = createDummyTrip(
            id = "malicious-1",
            startAddress = "   =SUM(1,2)",
            purpose = "=cmd|' /C calc'!A0",
            notes = "@dangerous_macro"
        )

        val repo = object : TripRepository {
            override fun getAllTrips() = flowOf(listOf(maliciousTrip))
            override fun getUnclassifiedTrips() = flowOf(emptyList<TripEntity>())
            override fun getUnclassifiedTripsCount() = flowOf(0)
            override fun getTripsByDateRange(s: String, e: String) = flowOf(emptyList<TripEntity>())
            override fun getTripsByBusinessEntity(entity: String) = flowOf(emptyList<TripEntity>())
            override fun getBusinessEntitySummaries() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
            override fun getBusinessEntitySummariesForDateRange(s: String, e: String) = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
            override fun getTripCountByClassification() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.ClassificationCount>())
            override suspend fun getTripById(id: String) = maliciousTrip
            override fun observeTripById(id: String) = flowOf(maliciousTrip)
            override suspend fun insertTrip(trip: TripEntity) {}
            override suspend fun updateTrip(trip: TripEntity) {}
            override suspend fun deleteTrip(id: String) {}
            override suspend fun classifyTrip(id: String, c: String, b: String, p: String, n: String?) {}
            override suspend fun updateTripDetails(id: String, c: String, b: String, p: String, n: String?) {}
            override fun getTripsBySyncStatus(s: String) = flowOf(emptyList<TripEntity>())
            override fun getTripsByClassification(c: String) = flowOf(emptyList<TripEntity>())
            override suspend fun getPendingSyncTrips() = emptyList<TripEntity>()
            override suspend fun markSynced(id: String, t: Long) {}
        }

        val viewModel = ReportsExportViewModel(repo, CalculateMileageRateUseCase(), appCtx)
        advanceUntilIdle()

        var capturedIntent: android.content.Intent? = null
        viewModel.exportToCsv { intent -> capturedIntent = intent }
        advanceUntilIdle()

        assertNotNull(capturedIntent)
        val exportDir = File(appCtx.cacheDir, "exports")
        val csvFiles = exportDir.listFiles { _, name -> name.endsWith(".csv") }
        assertTrue(csvFiles != null && csvFiles.isNotEmpty())
        val csvFile = csvFiles!!.maxByOrNull { it.lastModified() }!!

        val content = csvFile.readText()
        // Verifies the malicious formulas were safely prepended with '
        assertTrue("Formula with leading spaces must be prepended with single quote", content.contains("\"'   =SUM(1,2)\""))
        assertTrue("Formula must be prepended with single quote", content.contains("\"'=cmd|' /C calc'!A0\""))
        assertTrue("Macro must be prepended with single quote", content.contains("\"'@dangerous_macro\""))

        // Original database entity must remain unmutated
        assertEquals("   =SUM(1,2)", maliciousTrip.startAddress)
        assertEquals("=cmd|' /C calc'!A0", maliciousTrip.purpose)
        assertEquals("@dangerous_macro", maliciousTrip.notes)
    }
}
