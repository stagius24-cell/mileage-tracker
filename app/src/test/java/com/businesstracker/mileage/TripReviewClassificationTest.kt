package com.businesstracker.mileage

import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import com.businesstracker.mileage.domain.usecase.GetDashboardSummaryUseCase
import com.businesstracker.mileage.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TripReviewClassificationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tripsFlow = MutableStateFlow<List<TripEntity>>(emptyList())
    private var classifiedTripId: String? = null
    private var classifiedType: String? = null
    private var classifiedEntity: String? = null
    private var classifiedPurpose: String? = null

    private fun createUnreviewedTrip(id: String, distance: Double): TripEntity {
        return TripEntity(
            id = id,
            date = "2026-11-06",
            startTime = 1000L,
            endTime = 2000L,
            startAddress = "Start",
            endAddress = "End",
            startLatitude = 37.77,
            startLongitude = -122.41,
            endLatitude = 37.78,
            endLongitude = -122.42,
            totalDistanceMiles = distance,
            businessMiles = 0.0,
            businessEntity = "Personal",
            purpose = "Unclassified",
            trackingMethod = "AUTO_DETECTED",
            classification = TripClassification.UNREVIEWED.value,
            applicableRate = 0.760,
            calculatedDeduction = 0.0,
            notes = null,
            waypointsJson = "[]",
            syncStatus = "PENDING",
            syncedAt = null
        )
    }

    private val fakeRepo = object : TripRepository {
        override fun getAllTrips(): Flow<List<TripEntity>> = tripsFlow
        override fun getUnclassifiedTrips(): Flow<List<TripEntity>> = flowOf(tripsFlow.value.filter { it.classification == "UNREVIEWED" })
        override fun getUnclassifiedTripsCount(): Flow<Int> = flowOf(tripsFlow.value.count { it.classification == "UNREVIEWED" })
        override fun getTripsByDateRange(startDate: String, endDate: String): Flow<List<TripEntity>> = flowOf(emptyList())
        override fun getTripsByBusinessEntity(entity: String): Flow<List<TripEntity>> = flowOf(emptyList())
        override fun getBusinessEntitySummaries() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
        override fun getBusinessEntitySummariesForDateRange(startDate: String, endDate: String) = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
        override fun getTripCountByClassification() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.ClassificationCount>())
        override suspend fun getTripById(tripId: String): TripEntity? = tripsFlow.value.firstOrNull { it.id == tripId }
        override fun observeTripById(tripId: String): Flow<TripEntity?> = flowOf(tripsFlow.value.firstOrNull { it.id == tripId })
        override suspend fun insertTrip(trip: TripEntity) {}
        override suspend fun updateTrip(trip: TripEntity) {}
        override suspend fun deleteTrip(tripId: String) {}
        override suspend fun classifyTrip(
            tripId: String,
            classification: String,
            businessEntity: String,
            purpose: String,
            notes: String?
        ) {
            classifiedTripId = tripId
            classifiedType = classification
            classifiedEntity = businessEntity
            classifiedPurpose = purpose

            val updated = tripsFlow.value.map {
                if (it.id == tripId) {
                    it.copy(
                        classification = classification,
                        businessEntity = businessEntity,
                        purpose = purpose,
                        businessMiles = if (classification == "BUSINESS") it.totalDistanceMiles else 0.0,
                        calculatedDeduction = if (classification == "BUSINESS") it.totalDistanceMiles * it.applicableRate else 0.0
                    )
                } else it
            }
            tripsFlow.value = updated
        }
        override suspend fun updateTripDetails(tripId: String, classification: String, businessEntity: String, purpose: String, notes: String?) {}
        override fun getTripsBySyncStatus(syncStatus: String) = flowOf(emptyList<TripEntity>())
        override fun getTripsByClassification(classification: String) = flowOf(emptyList<TripEntity>())
        override suspend fun getPendingSyncTrips() = emptyList<TripEntity>()
        override suspend fun markSynced(tripId: String, timestamp: Long) {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tripsFlow.value = listOf(
            createUnreviewedTrip("trip_1", 10.0),
            createUnreviewedTrip("trip_2", 20.0)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun classifyBusiness_updatesClassificationAndDeduction() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val viewModel = DashboardViewModel(
            tripRepository = fakeRepo,
            rateUseCase = CalculateMileageRateUseCase(),
            summaryUseCase = GetDashboardSummaryUseCase(),
            appContext = context
        )
        advanceUntilIdle()

        viewModel.classifyTripAsBusiness("trip_1", BusinessEntity.MY_SUCCULENT_CORNER.displayName, "Nursery Pickup")
        advanceUntilIdle()

        assertEquals("trip_1", classifiedTripId)
        assertEquals("BUSINESS", classifiedType)
        assertEquals("MySucculentCorner", classifiedEntity)
        assertEquals("Nursery Pickup", classifiedPurpose)

        val updatedTrip = fakeRepo.getTripById("trip_1")
        assertNotNull(updatedTrip)
        assertEquals(10.0, updatedTrip!!.businessMiles, 0.001)
        assertEquals(7.60, updatedTrip.calculatedDeduction, 0.001)
    }

    @Test
    fun classifyPersonal_setsBusinessMilesAndDeductionToZero() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val viewModel = DashboardViewModel(
            tripRepository = fakeRepo,
            rateUseCase = CalculateMileageRateUseCase(),
            summaryUseCase = GetDashboardSummaryUseCase(),
            appContext = context
        )
        advanceUntilIdle()

        viewModel.classifyTripAsPersonal("trip_2")
        advanceUntilIdle()

        assertEquals("trip_2", classifiedTripId)
        assertEquals("PERSONAL", classifiedType)
        assertEquals("Personal", classifiedEntity)

        val updatedTrip = fakeRepo.getTripById("trip_2")
        assertNotNull(updatedTrip)
        assertEquals(20.0, updatedTrip!!.totalDistanceMiles, 0.001) // Preserved
        assertEquals(0.0, updatedTrip.businessMiles, 0.001)          // Zeroed
        assertEquals(0.0, updatedTrip.calculatedDeduction, 0.001)   // Zeroed
    }

    @Test
    fun undoLastClassification_revertsToOriginalClassification() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val viewModel = DashboardViewModel(
            tripRepository = fakeRepo,
            rateUseCase = CalculateMileageRateUseCase(),
            summaryUseCase = GetDashboardSummaryUseCase(),
            appContext = context
        )
        advanceUntilIdle()

        viewModel.classifyTripAsBusiness("trip_1", BusinessEntity.MY_SUCCULENT_CORNER.displayName, "Nursery Pickup")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUndoAvailable)

        viewModel.undoLastClassification()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUndoAvailable)
        val revertedTrip = fakeRepo.getTripById("trip_1")
        assertNotNull(revertedTrip)
        assertEquals(TripClassification.UNREVIEWED.value, revertedTrip!!.classification)
    }
}
