package com.businesstracker.mileage.ui.reports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReportsHistoricalDeductionTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()
    private val rateUseCase = CalculateMileageRateUseCase()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun createTrip(
        id: String,
        date: String,
        distance: Double,
        classification: String,
        applicableRate: Double
    ): TripEntity {
        val bMiles = if (classification == "BUSINESS") distance else 0.0
        val deduction = if (classification == "BUSINESS") distance * applicableRate else 0.0
        return TripEntity(
            id = id,
            date = date,
            startTime = 1000L,
            endTime = 2000L,
            startAddress = "A",
            endAddress = "B",
            startLatitude = 37.0,
            startLongitude = -122.0,
            endLatitude = 37.1,
            endLongitude = -122.1,
            totalDistanceMiles = distance,
            businessMiles = bMiles,
            businessEntity = "MySucculentCorner",
            purpose = "Errands",
            trackingMethod = "AUTO_DETECTED",
            classification = classification,
            applicableRate = applicableRate,
            calculatedDeduction = deduction,
            notes = null,
            waypointsJson = "[]",
            syncStatus = "PENDING",
            syncedAt = null
        )
    }

    @Test
    fun potentialDeduction_respectsHistoricalRatesAcrossMultiplePeriods() = runTest(testDispatcher) {
        // 2024 trip with 2024 rate 0.670
        val trip2024Unreviewed = createTrip("t2024", "2024-05-10", 100.0, "UNREVIEWED", 0.670)
        // 2025 trip with 2025 rate 0.700
        val trip2025Unreviewed = createTrip("t2025", "2025-06-15", 100.0, "UNREVIEWED", 0.700)
        // 2026 trip with 2026 rate 0.760
        val trip2026Unreviewed = createTrip("t2026", "2026-07-20", 100.0, "UNREVIEWED", 0.760)

        // 2026 finalized business trip
        val trip2026Finalized = createTrip("t2026-biz", "2026-03-01", 50.0, "BUSINESS", 0.760)

        val trips = listOf(trip2024Unreviewed, trip2025Unreviewed, trip2026Unreviewed, trip2026Finalized)

        val repo = object : TripRepository {
            override fun getAllTrips() = flowOf(trips)
            override fun getUnclassifiedTrips() = flowOf(trips.filter { it.classification == "UNREVIEWED" })
            override fun getUnclassifiedTripsCount() = flowOf(3)
            override fun getTripsByDateRange(s: String, e: String) = flowOf(emptyList<TripEntity>())
            override fun getTripsByBusinessEntity(entity: String) = flowOf(emptyList<TripEntity>())
            override fun getBusinessEntitySummaries() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
            override fun getBusinessEntitySummariesForDateRange(s: String, e: String) = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
            override fun getTripCountByClassification() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.ClassificationCount>())
            override suspend fun getTripById(id: String) = trips.firstOrNull { it.id == id }
            override fun observeTripById(id: String) = flowOf(trips.firstOrNull { it.id == id })
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

        val viewModel = ReportsExportViewModel(repo, rateUseCase, context)
        advanceUntilIdle()

        // 1. Select 2024: unreviewed 100 miles @ 0.670 = $67.00
        viewModel.selectYear("2024")
        advanceUntilIdle()
        var state = viewModel.uiState.value
        assertEquals(0.0, state.ytdLoggedDeduction, 0.001)
        assertEquals(67.00, state.ytdPotentialDeduction, 0.01)

        // 2. Select 2025: unreviewed 100 miles @ 0.700 = $70.00
        viewModel.selectYear("2025")
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(0.0, state.ytdLoggedDeduction, 0.001)
        assertEquals(70.00, state.ytdPotentialDeduction, 0.01)

        // 3. Select 2026: logged 50 * 0.760 = 38.00; unreviewed 100 * 0.760 = 76.00; total potential = 114.00
        viewModel.selectYear("2026")
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(38.00, state.ytdLoggedDeduction, 0.01)
        assertEquals(114.00, state.ytdPotentialDeduction, 0.01)

        // Reconcile with monthly summaries
        val summary2026 = state.monthlySummaries.firstOrNull { it.monthIso == "2026-07" }
        assertEquals(76.00, summary2026?.potentialDeduction ?: 0.0, 0.01)
    }
}
