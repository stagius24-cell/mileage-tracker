package com.businesstracker.mileage

import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.model.ClassificationSource
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.model.TripPurpose
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.ui.history.TripHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TripLongPressActionsTest {

    private val testDispatcher = StandardTestDispatcher()

    private fun createSampleTrip(
        id: String,
        distance: Double = 10.0,
        classification: String = "BUSINESS",
        entity: String = BusinessEntity.MY_SUCCULENT_CORNER.displayName,
        purpose: String = TripPurpose.NURSERY_PICKUP,
        rate: Double = 0.760
    ): TripEntity {
        return TripEntity(
            id = id,
            date = "2026-11-15",
            startTime = 1731670000000L,
            endTime = 1731671800000L,
            startAddress = "2392 Bailey Avenue, San Jose, CA",
            endAddress = "470 North Santa Cruz Avenue, Los Gatos, CA",
            startLatitude = 37.25,
            startLongitude = -121.85,
            endLatitude = 37.22,
            endLongitude = -121.98,
            totalDistanceMiles = distance,
            businessMiles = if (classification == "BUSINESS") distance else 0.0,
            businessEntity = entity,
            purpose = purpose,
            trackingMethod = "AUTO_DETECTED",
            classification = classification,
            applicableRate = rate,
            calculatedDeduction = if (classification == "BUSINESS") distance * rate else 0.0,
            notes = null,
            waypointsJson = "[]",
            syncStatus = "SYNCED",
            syncedAt = 1731671850000L,
            classificationSource = ClassificationSource.SMART_AUTO.value
        )
    }

    private class TestTripRepository(initialTrips: List<TripEntity>) : TripRepository {
        private val _trips = MutableStateFlow(initialTrips)
        val deletedTripIds = mutableListOf<String>()

        override fun getAllTrips(): Flow<List<TripEntity>> = _trips.asStateFlow()
        override fun getUnclassifiedTrips(): Flow<List<TripEntity>> = MutableStateFlow(emptyList())
        override fun getUnclassifiedTripsCount(): Flow<Int> = MutableStateFlow(0)
        override fun getTripsByDateRange(startDate: String, endDate: String): Flow<List<TripEntity>> = MutableStateFlow(emptyList())
        override fun getTripsByBusinessEntity(entity: String): Flow<List<TripEntity>> = MutableStateFlow(emptyList())
        override fun getBusinessEntitySummaries(): Flow<List<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>> = MutableStateFlow(emptyList())
        override fun getBusinessEntitySummariesForDateRange(startDate: String, endDate: String): Flow<List<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>> = MutableStateFlow(emptyList())
        override fun getTripCountByClassification(): Flow<List<com.businesstracker.mileage.data.local.model.ClassificationCount>> = MutableStateFlow(emptyList())

        override suspend fun getTripById(tripId: String): TripEntity? {
            return _trips.value.firstOrNull { it.id == tripId }
        }

        override fun observeTripById(tripId: String): Flow<TripEntity?> {
            return MutableStateFlow(_trips.value.firstOrNull { it.id == tripId })
        }

        override suspend fun insertTrip(trip: TripEntity) {
            _trips.value = _trips.value.filterNot { it.id == trip.id } + trip
        }

        override suspend fun updateTrip(trip: TripEntity) {
            _trips.value = _trips.value.map { if (it.id == trip.id) trip else it }
        }

        override suspend fun deleteTrip(tripId: String) {
            deletedTripIds.add(tripId)
            _trips.value = _trips.value.filterNot { it.id == tripId }
        }

        override suspend fun updateTripDetails(
            tripId: String,
            classification: String,
            businessEntity: String,
            purpose: String,
            notes: String?,
            source: ClassificationSource
        ) {
            _trips.value = _trips.value.map { trip ->
                if (trip.id == tripId) {
                    val isBiz = classification == "BUSINESS"
                    trip.copy(
                        classification = classification,
                        businessEntity = businessEntity,
                        purpose = purpose,
                        businessMiles = if (isBiz) trip.totalDistanceMiles else 0.0,
                        calculatedDeduction = if (isBiz) trip.totalDistanceMiles * trip.applicableRate else 0.0,
                        notes = notes,
                        syncStatus = "PENDING",
                        classificationSource = source.value
                    )
                } else trip
            }
        }

        override fun getTripsBySyncStatus(syncStatus: String): Flow<List<TripEntity>> = MutableStateFlow(emptyList())
        override fun getTripsByClassification(classification: String): Flow<List<TripEntity>> = MutableStateFlow(emptyList())
        override suspend fun getPendingSyncTrips(): List<TripEntity> = _trips.value.filter { it.syncStatus == "PENDING" }
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
    fun categoryEdit_businessToPersonal_recalculatesDeductionAndMarksManual() = runTest {
        val initial = createSampleTrip("trip-1", distance = 10.0, classification = "BUSINESS", rate = 0.760)
        val repository = TestTripRepository(listOf(initial))
        val viewModel = TripHistoryViewModel(repository)
        advanceUntilIdle()

        // Switch to PERSONAL via long-press category edit workflow
        viewModel.updateTripDetails(
            tripId = "trip-1",
            classification = TripClassification.PERSONAL.value,
            businessEntity = BusinessEntity.PERSONAL.displayName,
            purpose = "Personal Drive",
            notes = initial.notes
        )
        advanceUntilIdle()

        val updated = repository.getTripById("trip-1")
        assertNotNull(updated)
        assertEquals(TripClassification.PERSONAL.value, updated!!.classification)
        assertEquals(BusinessEntity.PERSONAL.displayName, updated.businessEntity)
        assertEquals("Personal Drive", updated.purpose)
        assertEquals(0.0, updated.businessMiles, 0.001)
        assertEquals(0.0, updated.calculatedDeduction, 0.001)
        assertEquals("PENDING", updated.syncStatus)
        assertEquals(ClassificationSource.MANUAL.value, updated.classificationSource)
    }

    @Test
    fun categoryEdit_personalToBusiness_recalculatesDeductionAndMarksManual() = runTest {
        val initial = createSampleTrip(
            "trip-2",
            distance = 20.0,
            classification = "PERSONAL",
            entity = BusinessEntity.PERSONAL.displayName,
            purpose = "Personal Drive",
            rate = 0.760
        )
        val repository = TestTripRepository(listOf(initial))
        val viewModel = TripHistoryViewModel(repository)
        advanceUntilIdle()

        // Switch to BUSINESS via long-press category edit workflow
        viewModel.updateTripDetails(
            tripId = "trip-2",
            classification = TripClassification.BUSINESS.value,
            businessEntity = BusinessEntity.MY_SUCCULENT_CORNER.displayName,
            purpose = TripPurpose.NURSERY_PICKUP,
            notes = initial.notes
        )
        advanceUntilIdle()

        val updated = repository.getTripById("trip-2")
        assertNotNull(updated)
        assertEquals(TripClassification.BUSINESS.value, updated!!.classification)
        assertEquals(BusinessEntity.MY_SUCCULENT_CORNER.displayName, updated.businessEntity)
        assertEquals(TripPurpose.NURSERY_PICKUP, updated.purpose)
        assertEquals(20.0, updated.businessMiles, 0.001)
        assertEquals(20.0 * 0.760, updated.calculatedDeduction, 0.001)
        assertEquals("PENDING", updated.syncStatus)
        assertEquals(ClassificationSource.MANUAL.value, updated.classificationSource)
    }

    @Test
    fun deleteTrip_fromLongPress_removesTripAndPopulatesUndoState() = runTest {
        val trip1 = createSampleTrip("trip-1")
        val trip2 = createSampleTrip("trip-2")
        val repository = TestTripRepository(listOf(trip1, trip2))
        val viewModel = TripHistoryViewModel(repository)
        advanceUntilIdle()

        // Trigger delete
        viewModel.deleteTrip("trip-1")
        advanceUntilIdle()

        assertEquals(listOf("trip-1"), repository.deletedTripIds)
        assertNull(repository.getTripById("trip-1"))
        assertNotNull(repository.getTripById("trip-2"))

        // Verify UI state reflects recently deleted trip for undo snackbar
        val uiState = viewModel.uiState.value
        assertEquals("trip-1", uiState.recentlyDeletedTrip?.id)
        assertEquals(1, uiState.trips.size)
        assertEquals("trip-2", uiState.trips.first().id)
    }
}
