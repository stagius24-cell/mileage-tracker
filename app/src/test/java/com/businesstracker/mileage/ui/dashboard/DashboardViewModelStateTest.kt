package com.businesstracker.mileage.ui.dashboard

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleState
import com.businesstracker.mileage.data.repository.ActiveTripRepositoryImpl
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import com.businesstracker.mileage.domain.usecase.GetDashboardSummaryUseCase
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.service.TrackingServiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DashboardViewModelStateTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var activeTripRepository: ActiveTripRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()

    private val fakeTripRepo = object : TripRepository {
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activeTripRepository = ActiveTripRepositoryImpl(database.activeTripDao(), testDispatcher)
        LocationTrackingService.resetGuardForTesting()
    }

    @After
    fun tearDown() {
        LocationTrackingService.resetGuardForTesting()
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun dashboard_whenTripMarkedRecoveryRequired_showsInterruptedWithoutActivelyTracking() = runTest(testDispatcher) {
        val tripId = "db-interrupted-trip"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis() - 60000L,
                startLatitude = 37.77,
                startLongitude = -122.41,
                lastLatitude = 37.78,
                lastLongitude = -122.42,
                lastTimestamp = System.currentTimeMillis(),
                totalDistanceMeters = 1609.344, // 1.00 mile
                trackingMethod = "AUTO_DETECTED",
                isPaused = false
            )
        )
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(
                tripId = tripId,
                state = TripLifecycleState.RECOVERY_REQUIRED.value
            )
        )

        // In-memory serviceState is stopped
        assertFalse(LocationTrackingService.serviceState.value.isTracking)

        val viewModel = DashboardViewModel(
            tripRepository = fakeTripRepo,
            rateUseCase = CalculateMileageRateUseCase(),
            summaryUseCase = GetDashboardSummaryUseCase(),
            appContext = context,
            activeTripRepository = activeTripRepository,
            tripLifecycleDao = database.tripLifecycleDao()
        )
        advanceUntilIdle()

        val state = viewModel.serviceState.first { it.tripId == tripId }
        assertFalse("Must NOT be displayed as actively tracking", state.isTracking)
        assertTrue("Interrupted state must be marked recovery required", state.isRecoveryRequired)
        assertEquals(tripId, state.tripId)
        assertEquals(1.0, state.distanceMiles, 0.01)
        assertFalse(state.isPaused)
    }

    @Test
    fun dashboard_whenTripPaused_preservesPausedStatus() = runTest(testDispatcher) {
        val tripId = "db-paused-trip"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis() - 60000L,
                startLatitude = 37.77,
                startLongitude = -122.41,
                lastLatitude = 37.78,
                lastLongitude = -122.42,
                lastTimestamp = System.currentTimeMillis(),
                totalDistanceMeters = 3218.688, // 2.00 miles
                trackingMethod = "AUTO_DETECTED",
                isPaused = true
            )
        )
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(
                tripId = tripId,
                state = TripLifecycleState.TRACKING.value
            )
        )

        val viewModel = DashboardViewModel(
            tripRepository = fakeTripRepo,
            rateUseCase = CalculateMileageRateUseCase(),
            summaryUseCase = GetDashboardSummaryUseCase(),
            appContext = context,
            activeTripRepository = activeTripRepository,
            tripLifecycleDao = database.tripLifecycleDao()
        )
        advanceUntilIdle()

        val state = viewModel.serviceState.first { it.tripId == tripId }
        assertFalse(state.isTracking)
        assertTrue(state.isPaused)
        assertEquals(tripId, state.tripId)
        assertEquals(2.0, state.distanceMiles, 0.01)
    }

    @Test
    fun dashboard_whenActiveTripCleared_cleanlyTransitionsToStopped() = runTest(testDispatcher) {
        val tripId = "trip-to-finalize"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis(),
                startLatitude = 37.0,
                startLongitude = -122.0,
                lastLatitude = 37.1,
                lastLongitude = -122.1,
                lastTimestamp = System.currentTimeMillis(),
                totalDistanceMeters = 3000.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(
                tripId = tripId,
                state = TripLifecycleState.DRAINING.value
            )
        )

        val viewModel = DashboardViewModel(
            tripRepository = fakeTripRepo,
            rateUseCase = CalculateMileageRateUseCase(),
            summaryUseCase = GetDashboardSummaryUseCase(),
            appContext = context,
            activeTripRepository = activeTripRepository,
            tripLifecycleDao = database.tripLifecycleDao()
        )
        advanceUntilIdle()

        val state = viewModel.serviceState.first { it.tripId == tripId }
        assertFalse(state.isTracking)
        assertEquals("Finalizing Trip...", state.statusMessage)

        // Clear active trip from DB (simulating finalization)
        database.activeTripDao().clearActiveTrip()
        val stoppedState = viewModel.serviceState.first { it.tripId.isEmpty() }

        assertFalse("Dashboard must immediately transition to not-tracking after active trip cleared", stoppedState.isTracking)
    }
}
