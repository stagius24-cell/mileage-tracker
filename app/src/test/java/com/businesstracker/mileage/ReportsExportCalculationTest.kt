package com.businesstracker.mileage

import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import com.businesstracker.mileage.ui.reports.PdfReportGenerator
import com.businesstracker.mileage.ui.reports.ReportSummaryData
import com.businesstracker.mileage.ui.reports.ReportsExportViewModel
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReportsExportCalculationTest {

    private val testDispatcher = StandardTestDispatcher()

    private fun createTrip(
        id: String,
        date: String,
        distance: Double,
        classification: String,
        entity: String = "MySucculentCorner",
        purpose: String = "Nursery Pickup",
        rate: Double = 0.760
    ): TripEntity {
        return TripEntity(
            id = id,
            date = date,
            startTime = 1000L,
            endTime = 2000L,
            startAddress = "100 Start Ave",
            endAddress = "200 End Ave",
            startLatitude = 37.77,
            startLongitude = -122.41,
            endLatitude = 37.78,
            endLongitude = -122.42,
            totalDistanceMiles = distance,
            businessMiles = if (classification == "BUSINESS") distance else 0.0,
            businessEntity = entity,
            purpose = purpose,
            trackingMethod = "AUTO_DETECTED",
            classification = classification,
            applicableRate = rate,
            calculatedDeduction = if (classification == "BUSINESS") distance * rate else 0.0,
            notes = "Export test note",
            waypointsJson = "[]",
            syncStatus = "SYNCED",
            syncedAt = 1000L
        )
    }

    private val sampleTrips = listOf(
        createTrip("1", "2026-03-15", 40.0, "BUSINESS", entity = "MySucculentCorner", purpose = "Nursery Pickup"),
        createTrip("2", "2026-05-20", 60.0, "BUSINESS", entity = "MySucculentCorner", purpose = "Soil & Pots Sourcing"),
        createTrip("3", "2026-07-10", 30.0, "PERSONAL", entity = "Personal", purpose = "Personal"),
        createTrip("4", "2025-11-05", 50.0, "BUSINESS", entity = "MySucculentCorner", purpose = "Wholesale Supplies", rate = 0.700)
    )

    private val fakeRepo = object : TripRepository {
        override fun getAllTrips() = flowOf(sampleTrips)
        override fun getUnclassifiedTrips() = flowOf(emptyList<TripEntity>())
        override fun getUnclassifiedTripsCount() = flowOf(0)
        override fun getTripsByDateRange(startDate: String, endDate: String) = flowOf(emptyList<TripEntity>())
        override fun getTripsByBusinessEntity(entity: String) = flowOf(emptyList<TripEntity>())
        override fun getBusinessEntitySummaries() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
        override fun getBusinessEntitySummariesForDateRange(startDate: String, endDate: String) = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
        override fun getTripCountByClassification() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.ClassificationCount>())
        override suspend fun getTripById(tripId: String) = sampleTrips.firstOrNull { it.id == tripId }
        override fun observeTripById(tripId: String) = flowOf(sampleTrips.firstOrNull { it.id == tripId })
        override suspend fun insertTrip(trip: TripEntity) {}
        override suspend fun updateTrip(trip: TripEntity) {}
        override suspend fun deleteTrip(tripId: String) {}
        override suspend fun classifyTrip(tripId: String, classification: String, businessEntity: String, purpose: String, notes: String?) {}
        override suspend fun updateTripDetails(tripId: String, classification: String, businessEntity: String, purpose: String, notes: String?) {}
        override fun getTripsBySyncStatus(syncStatus: String) = flowOf(emptyList<TripEntity>())
        override fun getTripsByClassification(classification: String) = flowOf(emptyList<TripEntity>())
        override suspend fun getPendingSyncTrips() = emptyList<TripEntity>()
        override suspend fun markSynced(tripId: String, timestamp: Long) {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun reportsMetrics_aggregates2026TotalsAccurately() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val viewModel = ReportsExportViewModel(fakeRepo, CalculateMileageRateUseCase(), context)
        advanceUntilIdle()

        viewModel.selectYear("2026")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.totalTrips) // Trips 1, 2, 3 in 2026
        assertEquals(130.0, state.totalMiles, 0.01) // 40 + 60 + 30
        assertEquals(100.0, state.businessMiles, 0.01) // 40 + 60
        assertEquals(30.0, state.personalMiles, 0.01) // 30
        assertEquals(76.00, state.totalDeduction, 0.01) // 100 * 0.76
        assertEquals(43.33, state.averageDistance, 0.01) // 130 / 3
    }

    @Test
    fun reportsMetrics_filterByBusinessEntity_isolatesEntity() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val viewModel = ReportsExportViewModel(fakeRepo, CalculateMileageRateUseCase(), context)
        advanceUntilIdle()

        viewModel.selectBusinessEntity("Personal")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.totalTrips)
        assertEquals(30.0, state.totalMiles, 0.01)
        assertEquals(0.0, state.businessMiles, 0.01)
        assertEquals(0.0, state.totalDeduction, 0.01)
    }

    @Test
    fun pdfGenerator_generatesValidPdfDocument() {
        val context = RuntimeEnvironment.getApplication()
        val reportData = ReportSummaryData(
            title = "Annual Mileage Tax Report",
            dateRangeLabel = "2026-01-01 to 2026-12-31",
            businessEntity = "MySucculentCorner",
            totalTrips = 2,
            totalMiles = 100.0,
            businessMiles = 100.0,
            personalMiles = 0.0,
            unreviewedMiles = 0.0,
            totalDeduction = 76.00,
            averageDistance = 50.0,
            trips = listOf(
                createTrip("1", "2026-03-15", 40.0, "BUSINESS"),
                createTrip("2", "2026-05-20", 60.0, "BUSINESS")
            )
        )

        val file = PdfReportGenerator.generatePdfReport(context, reportData)
        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    @Test
    fun pdfGenerator_emptyTrips_handlesGracefully() {
        val context = RuntimeEnvironment.getApplication()
        val emptyData = ReportSummaryData(
            title = "Empty Report",
            dateRangeLabel = "2026",
            businessEntity = "ALL",
            totalTrips = 0,
            totalMiles = 0.0,
            businessMiles = 0.0,
            personalMiles = 0.0,
            unreviewedMiles = 0.0,
            totalDeduction = 0.0,
            averageDistance = 0.0,
            trips = emptyList()
        )

        val file = PdfReportGenerator.generatePdfReport(context, emptyData)
        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }
}
