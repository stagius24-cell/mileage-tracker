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

        // 2026 finalized business trip (March 2026 fixture uses pre-July 2026 rate $0.725)
        val trip2026Finalized = createTrip("t2026-biz", "2026-03-01", 50.0, "BUSINESS", 0.725)

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

        // 3. Select 2026: logged 50 * 0.725 = 36.25; unreviewed 100 * 0.760 = 76.00; total potential = 112.25
        viewModel.selectYear("2026")
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(36.25, state.ytdLoggedDeduction, 0.01)
        assertEquals(112.25, state.ytdPotentialDeduction, 0.01)

        // Reconcile with monthly summaries
        val summary2026March = state.monthlySummaries.firstOrNull { it.monthIso == "2026-03" }
        assertEquals(36.25, summary2026March?.potentialDeduction ?: 0.0, 0.01)
        val summary2026July = state.monthlySummaries.firstOrNull { it.monthIso == "2026-07" }
        assertEquals(76.00, summary2026July?.potentialDeduction ?: 0.0, 0.01)
    }

    @Test
    fun potentialDeduction_missingOrZeroApplicableRate_fallsBackToDateBasedHistoricalRate() = runTest(testDispatcher) {
        // Unreviewed trips with 0.0 applicableRate
        val trip2024Missing = createTrip("m2024", "2024-04-10", 100.0, "UNREVIEWED", 0.0)
        val trip2025Missing = createTrip("m2025", "2025-05-10", 100.0, "UNREVIEWED", 0.0)
        val trip2026H1Missing = createTrip("m2026h1", "2026-03-15", 100.0, "UNREVIEWED", 0.0)
        val trip2026H2Missing = createTrip("m2026h2", "2026-09-15", 100.0, "UNREVIEWED", 0.0)

        val trips = listOf(trip2024Missing, trip2025Missing, trip2026H1Missing, trip2026H2Missing)

        val repo = object : TripRepository {
            override fun getAllTrips() = flowOf(trips)
            override fun getUnclassifiedTrips() = flowOf(trips)
            override fun getUnclassifiedTripsCount() = flowOf(trips.size)
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

        // 2024 fallback: 100 * 0.670 = $67.00
        viewModel.selectYear("2024")
        advanceUntilIdle()
        assertEquals(67.00, viewModel.uiState.value.ytdPotentialDeduction, 0.01)
        val m2024 = viewModel.uiState.value.monthlySummaries.firstOrNull { it.monthIso == "2024-04" }
        assertEquals(67.00, m2024?.potentialDeduction ?: 0.0, 0.01)

        // 2025 fallback: 100 * 0.700 = $70.00
        viewModel.selectYear("2025")
        advanceUntilIdle()
        assertEquals(70.00, viewModel.uiState.value.ytdPotentialDeduction, 0.01)
        val m2025 = viewModel.uiState.value.monthlySummaries.firstOrNull { it.monthIso == "2025-05" }
        assertEquals(70.00, m2025?.potentialDeduction ?: 0.0, 0.01)

        // 2026 fallback: 100 * 0.725 (H1) + 100 * 0.760 (H2) = 72.50 + 76.00 = 148.50
        viewModel.selectYear("2026")
        advanceUntilIdle()
        assertEquals(148.50, viewModel.uiState.value.ytdPotentialDeduction, 0.01)
        val m2026H1 = viewModel.uiState.value.monthlySummaries.firstOrNull { it.monthIso == "2026-03" }
        assertEquals(72.50, m2026H1?.potentialDeduction ?: 0.0, 0.01)
        val m2026H2 = viewModel.uiState.value.monthlySummaries.firstOrNull { it.monthIso == "2026-09" }
        assertEquals(76.00, m2026H2?.potentialDeduction ?: 0.0, 0.01)
    }

    @Test
    fun ratePeriods2026_distinguishesPreAndPostJulyRatesAccurately() = runTest(testDispatcher) {
        val tripPreJuly = createTrip("h1", "2026-06-30", 100.0, "BUSINESS", 0.725)
        val tripPostJuly = createTrip("h2", "2026-07-01", 100.0, "BUSINESS", 0.760)

        val trips = listOf(tripPreJuly, tripPostJuly)

        val repo = object : TripRepository {
            override fun getAllTrips() = flowOf(trips)
            override fun getUnclassifiedTrips() = flowOf(emptyList<TripEntity>())
            override fun getUnclassifiedTripsCount() = flowOf(0)
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

        viewModel.selectYear("2026")
        advanceUntilIdle()

        val juneSummary = viewModel.uiState.value.monthlySummaries.firstOrNull { it.monthIso == "2026-06" }
        assertEquals(72.50, juneSummary?.loggedDeduction ?: 0.0, 0.01)

        val julySummary = viewModel.uiState.value.monthlySummaries.firstOrNull { it.monthIso == "2026-07" }
        assertEquals(76.00, julySummary?.loggedDeduction ?: 0.0, 0.01)

        assertEquals(148.50, viewModel.uiState.value.ytdLoggedDeduction, 0.01)
    }
}
