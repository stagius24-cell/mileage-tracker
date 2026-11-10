package com.businesstracker.mileage

import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.model.SortOrder
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.ui.history.TripHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TripHistoryFilterAndSortTest {

    private val testDispatcher = StandardTestDispatcher()

    private fun createTrip(
        id: String,
        date: String,
        startTime: Long,
        distance: Double,
        classification: String,
        businessEntity: String = "MySucculentCorner",
        purpose: String = "Nursery Pickup",
        startAddress: String? = "123 Main St",
        notes: String? = null,
        syncStatus: String = "SYNCED"
    ): TripEntity {
        return TripEntity(
            id = id,
            date = date,
            startTime = startTime,
            endTime = startTime + 1800000L,
            startAddress = startAddress,
            endAddress = "456 Market St",
            startLatitude = 37.77,
            startLongitude = -122.41,
            endLatitude = 37.78,
            endLongitude = -122.42,
            totalDistanceMiles = distance,
            businessMiles = if (classification == "BUSINESS") distance else 0.0,
            businessEntity = businessEntity,
            purpose = purpose,
            trackingMethod = "AUTO_DETECTED",
            classification = classification,
            applicableRate = 0.760,
            calculatedDeduction = if (classification == "BUSINESS") distance * 0.760 else 0.0,
            notes = notes,
            waypointsJson = "[]",
            syncStatus = syncStatus,
            syncedAt = if (syncStatus == "SYNCED") 1000L else null
        )
    }

    private val sampleTrips = listOf(
        createTrip("1", "2026-11-01", 1000L, 10.0, "BUSINESS", purpose = "Nursery Pickup", syncStatus = "SYNCED"),
        createTrip("2", "2026-11-03", 3000L, 25.0, "BUSINESS", purpose = "Wholesale Supplies", syncStatus = "PENDING"),
        createTrip("3", "2026-11-02", 2000L, 5.0, "PERSONAL", businessEntity = "Personal", purpose = "Personal", syncStatus = "SYNCED"),
        createTrip("4", "2026-11-04", 4000L, 15.0, "UNREVIEWED", notes = "Urgent errand", syncStatus = "PENDING")
    )

    private val fakeRepository = object : TripRepository {
        override fun getAllTrips(): Flow<List<TripEntity>> = flowOf(sampleTrips)
        override fun getUnclassifiedTrips(): Flow<List<TripEntity>> = flowOf(sampleTrips.filter { it.classification == "UNREVIEWED" })
        override fun getUnclassifiedTripsCount(): Flow<Int> = flowOf(1)
        override fun getTripsByDateRange(startDate: String, endDate: String): Flow<List<TripEntity>> = flowOf(emptyList())
        override fun getTripsByBusinessEntity(entity: String): Flow<List<TripEntity>> = flowOf(emptyList())
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
    fun filter_byClassification_returnsOnlyMatchingTrips() = runTest {
        val viewModel = TripHistoryViewModel(fakeRepository)
        advanceUntilIdle()

        viewModel.setClassificationFilter("BUSINESS")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.trips.size)
        assertTrue(state.trips.all { it.classification == "BUSINESS" })
    }

    @Test
    fun filter_bySyncStatus_returnsOnlyMatchingTrips() = runTest {
        val viewModel = TripHistoryViewModel(fakeRepository)
        advanceUntilIdle()

        viewModel.setSyncStatusFilter("PENDING")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.trips.size)
        assertTrue(state.trips.all { it.syncStatus == "PENDING" })
    }

    @Test
    fun sorting_byHighestMileage_ordersDescending() = runTest {
        val viewModel = TripHistoryViewModel(fakeRepository)
        advanceUntilIdle()

        viewModel.setSortOrder(SortOrder.HIGHEST_MILEAGE)
        advanceUntilIdle()

        val trips = viewModel.uiState.value.trips
        assertEquals("2", trips[0].id) // 25.0 mi
        assertEquals("4", trips[1].id) // 15.0 mi
        assertEquals("1", trips[2].id) // 10.0 mi
        assertEquals("3", trips[3].id) // 5.0 mi
    }

    @Test
    fun sorting_byOldestFirst_ordersChronologically() = runTest {
        val viewModel = TripHistoryViewModel(fakeRepository)
        advanceUntilIdle()

        viewModel.setSortOrder(SortOrder.OLDEST_FIRST)
        advanceUntilIdle()

        val trips = viewModel.uiState.value.trips
        assertEquals("1", trips[0].id) // startTime = 1000
        assertEquals("3", trips[1].id) // startTime = 2000
        assertEquals("2", trips[2].id) // startTime = 3000
        assertEquals("4", trips[3].id) // startTime = 4000
    }

    @Test
    fun search_matchesNotesOrPurpose() = runTest {
        val viewModel = TripHistoryViewModel(fakeRepository)
        advanceUntilIdle()

        viewModel.setSearchQuery("errand")
        advanceUntilIdle()

        val trips = viewModel.uiState.value.trips
        assertEquals(1, trips.size)
        assertEquals("4", trips[0].id)
    }

    @Test
    fun dateRange_fromReportMonth_returnsOnlyThatMonthsTrips() = runTest {
        val repository = object : TripRepository by fakeRepository {
            override fun getAllTrips(): Flow<List<TripEntity>> = flowOf(
                sampleTrips + createTrip(
                    id = "october",
                    date = "2026-10-31",
                    startTime = 5000L,
                    distance = 8.0,
                    classification = "PERSONAL"
                )
            )
        }
        val viewModel = TripHistoryViewModel(repository)
        advanceUntilIdle()

        viewModel.setDateRange("2026-11-01", "2026-11-30")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(4, state.trips.size)
        assertTrue(state.trips.all { it.date.startsWith("2026-11") })
        assertEquals("2026-11-01", state.startDateFilter)
        assertEquals("2026-11-30", state.endDateFilter)
    }

    @Test
    fun deleteTrip_andUndo_restoresTripSuccessfully() = runTest {
        val tripList = sampleTrips.toMutableList()
        val repo = object : TripRepository by fakeRepository {
            override fun getAllTrips(): Flow<List<TripEntity>> = flowOf(tripList)
            override suspend fun getTripById(tripId: String): TripEntity? = tripList.find { it.id == tripId }
            override suspend fun deleteTrip(tripId: String) {
                tripList.removeAll { it.id == tripId }
            }
            override suspend fun insertTrip(trip: TripEntity) {
                tripList.add(trip)
            }
        }
        val viewModel = TripHistoryViewModel(repo)
        advanceUntilIdle()

        assertEquals(4, viewModel.uiState.value.totalRecordedTripsCount)

        // Delete trip 1
        viewModel.deleteTrip("1")
        advanceUntilIdle()

        assertEquals("1", viewModel.uiState.value.recentlyDeletedTrip?.id)

        // Undo deletion
        viewModel.undoDeleteTrip()
        advanceUntilIdle()

        org.junit.Assert.assertNull(viewModel.uiState.value.recentlyDeletedTrip)
        assertTrue(tripList.any { it.id == "1" })
    }

    @Test
    fun clearAllFilters_resetsQueryAndFilters() = runTest {
        val viewModel = TripHistoryViewModel(fakeRepository)
        advanceUntilIdle()

        viewModel.setSearchQuery("errand")
        viewModel.setClassificationFilter("PERSONAL")
        viewModel.setBusinessEntityFilter("MySucculentCorner")
        viewModel.setSyncStatusFilter("SYNCED")
        viewModel.setDateRange("2026-11-01", "2026-11-30")
        advanceUntilIdle()

        assertEquals("errand", viewModel.uiState.value.searchQuery)
        assertEquals("PERSONAL", viewModel.uiState.value.selectedClassificationFilter)

        viewModel.clearAllFilters()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.searchQuery)
        assertEquals("ALL", state.selectedClassificationFilter)
        assertEquals("ALL", state.selectedBusinessEntityFilter)
        assertEquals("ALL", state.selectedSyncStatusFilter)
        assertEquals("", state.startDateFilter)
        assertEquals("", state.endDateFilter)
    }

    @Test
    fun rapidRepeatedDeletion_overwritesRecentlyDeletedWithLatest() = runTest {
        val tripList = sampleTrips.toMutableList()
        val repo = object : TripRepository by fakeRepository {
            override fun getAllTrips(): Flow<List<TripEntity>> = flowOf(tripList)
            override suspend fun getTripById(tripId: String): TripEntity? = tripList.find { it.id == tripId }
            override suspend fun deleteTrip(tripId: String) {
                tripList.removeAll { it.id == tripId }
            }
            override suspend fun insertTrip(trip: TripEntity) {
                tripList.add(trip)
            }
        }
        val viewModel = TripHistoryViewModel(repo)
        advanceUntilIdle()

        // Delete trip "1" rapidly followed by trip "2"
        viewModel.deleteTrip("1")
        viewModel.deleteTrip("2")
        advanceUntilIdle()

        // The latest deleted trip ("2") is staged in recentlyDeletedTrip
        assertEquals("2", viewModel.uiState.value.recentlyDeletedTrip?.id)

        // Undo restores trip "2"
        viewModel.undoDeleteTrip()
        advanceUntilIdle()

        org.junit.Assert.assertNull(viewModel.uiState.value.recentlyDeletedTrip)
        assertTrue(tripList.any { it.id == "2" })
    }

    @Test
    fun undoRestoreFailure_retainsRecentlyDeletedTripAndReportsError() = runTest {
        val tripList = sampleTrips.toMutableList()
        var shouldThrowOnInsert = true
        val repo = object : TripRepository by fakeRepository {
            override fun getAllTrips(): Flow<List<TripEntity>> = flowOf(tripList)
            override suspend fun getTripById(tripId: String): TripEntity? = tripList.find { it.id == tripId }
            override suspend fun deleteTrip(tripId: String) {
                tripList.removeAll { it.id == tripId }
            }
            override suspend fun insertTrip(trip: TripEntity) {
                if (shouldThrowOnInsert) throw java.io.IOException("Disk full")
                tripList.add(trip)
            }
        }
        val viewModel = TripHistoryViewModel(repo)
        advanceUntilIdle()

        viewModel.deleteTrip("1")
        advanceUntilIdle()

        // Attempt undo when repository throws
        viewModel.undoDeleteTrip()
        advanceUntilIdle()

        // Verify failure was captured and trip was not discarded
        assertEquals("Unable to restore trip. Please try again.", viewModel.uiState.value.actionErrorMessage)
        assertEquals("1", viewModel.uiState.value.recentlyDeletedTrip?.id)

        // Retry after issue resolved
        shouldThrowOnInsert = false
        viewModel.undoDeleteTrip()
        advanceUntilIdle()

        org.junit.Assert.assertNull(viewModel.uiState.value.recentlyDeletedTrip)
        assertTrue(tripList.any { it.id == "1" })
    }

    @Test
    fun updateTripDetails_onPersistenceFailure_invokesOnErrorCallback() = runTest {
        val repo = object : TripRepository by fakeRepository {
            override suspend fun updateTripDetails(
                tripId: String,
                classification: String,
                businessEntity: String,
                purpose: String,
                notes: String?
            ) {
                throw android.database.sqlite.SQLiteDiskIOException("Database write failed")
            }
        }
        val viewModel = TripHistoryViewModel(repo)
        advanceUntilIdle()

        var errorReported: String? = null
        var successReported = false

        viewModel.updateTripDetails(
            tripId = "1",
            classification = "BUSINESS",
            businessEntity = "MySucculentCorner",
            purpose = "Nursery Pickup",
            notes = "Test Note",
            onError = { errorReported = it },
            onSuccess = { successReported = true }
        )
        advanceUntilIdle()

        org.junit.Assert.assertFalse(successReported)
        assertEquals("Unable to save trip changes. Please try again.", errorReported)
        assertEquals("Unable to save trip changes. Please try again.", viewModel.uiState.value.actionErrorMessage)
    }
}
