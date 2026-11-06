package com.businesstracker.mileage.worker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.repository.ActiveTripRepositoryImpl
import com.businesstracker.mileage.data.repository.TripRepositoryImpl
import com.businesstracker.mileage.domain.coordinator.TripFinalizationCoordinator
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import com.businesstracker.mileage.domain.usecase.FinalizeTripUseCase
import com.businesstracker.mileage.domain.usecase.GeocodeLocationUseCase
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.service.NotificationHelper
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TripDebounceWorkerTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var activeTripRepository: ActiveTripRepositoryImpl
    private lateinit var tripRepository: TripRepositoryImpl
    private lateinit var coordinator: TripFinalizationCoordinator
    private lateinit var finalizeTripUseCase: FinalizeTripUseCase
    private lateinit var notificationHelper: NotificationHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferencesManager = TrackingPreferencesManager(context)
        activeTripRepository = ActiveTripRepositoryImpl(database.activeTripDao(), Dispatchers.IO)
        tripRepository = TripRepositoryImpl(database.tripDao(), Dispatchers.IO)

        coordinator = TripFinalizationCoordinator(
            database = database,
            activeTripRepository = activeTripRepository,
            tripRepository = tripRepository,
            geocodeUseCase = GeocodeLocationUseCase(context, Dispatchers.IO),
            rateCalculator = CalculateMileageRateUseCase(),
            preferencesManager = preferencesManager,
            gson = Gson(),
            ioDispatcher = Dispatchers.IO
        )
        finalizeTripUseCase = FinalizeTripUseCase(coordinator)
        notificationHelper = NotificationHelper(context)

        LocationTrackingService.resetGuardForTesting()
    }

    @After
    fun tearDown() {
        database.close()
        LocationTrackingService.resetGuardForTesting()
    }

    private fun createWorker(): TripDebounceWorker {
        return TestListenableWorkerBuilder<TripDebounceWorker>(context)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters
                ): ListenableWorker {
                    return TripDebounceWorker(
                        context = appContext,
                        workerParams = workerParameters,
                        preferencesManager = preferencesManager,
                        activeTripRepository = activeTripRepository,
                        tripRepository = tripRepository,
                        finalizeTripUseCase = finalizeTripUseCase,
                        notificationHelper = notificationHelper
                    )
                }
            })
            .build()
    }

    // BLOCKER 3 & 5: When service is still draining, doWork() returns Result.retry() and active preferences remain intact
    @Test
    fun doWork_whileServiceIsDraining_returnsRetry_andPreservesActivePreferences() = runBlocking {
        val tripId = "trip-active-draining-1"
        activeTripRepository.createActiveTrip(tripId, 37.3382, -121.8863, "AUTO_DETECTED", "MySucculentCorner")
        preferencesManager.setActiveTripId(tripId)
        preferencesManager.setVehicleAnchor(37.3382, -121.8863, System.currentTimeMillis())
        preferencesManager.setInVehicleState(false) // User exited vehicle

        // Service is actively draining GPS
        LocationTrackingService.setDrainingForTesting(true)

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals("Worker must yield with Result.retry() while GPS draining is incomplete", ListenableWorker.Result.retry(), result)

        // Verify active trip and preferences were NOT cleared
        assertEquals("Active trip preference must be preserved during retry", tripId, preferencesManager.activeTripId.first())
        val activeInDb = activeTripRepository.getActiveTrip()
        assertNotNull("Active trip in Room must remain intact during retry", activeInDb)
        assertEquals(tripId, activeInDb?.id)

        // Verify finalized trips table remains empty (premature finalization was prevented)
        val finalizedInDb = tripRepository.getTripById(tripId)
        assertTrue("Trip must not be prematurely finalized in database", finalizedInDb == null)
    }

    // BLOCKER 3 & 5: When service is terminated/inactive, doWork() safely finalizes trip from Room and clears preferences
    @Test
    fun doWork_afterServiceInterruption_finalizesDirectlyFromRoom_andReturnsSuccess() = runBlocking {
        val tripId = "trip-interrupted-recovery"
        val activeTrip = ActiveTripEntity(
            id = tripId,
            startTime = 1000L,
            startLatitude = 37.3382,
            startLongitude = -121.8863,
            lastLatitude = 37.3500,
            lastLongitude = -121.8863,
            lastTimestamp = 5000L,
            totalDistanceMeters = 1500.0, // > 200m confirmed drive
            trackingMethod = "AUTO_DETECTED",
            suggestedBusiness = "MySucculentCorner"
        )
        database.activeTripDao().insertActiveTrip(activeTrip)
        database.activeTripDao().insertWaypoint(
            ActiveWaypointEntity(
                tripId = tripId,
                latitude = 37.3382,
                longitude = -121.8863,
                altitude = 0.0,
                timestamp = 1000L,
                speed = 10f,
                accuracy = 4f
            )
        )
        preferencesManager.setActiveTripId(tripId)
        preferencesManager.setInVehicleState(false)

        // Service is completely inactive
        LocationTrackingService.resetGuardForTesting()

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals("Worker must successfully finalize interrupted drive from Room", ListenableWorker.Result.success(), result)

        // Verify trip was finalized in trips table
        val finalizedTrip = tripRepository.getTripById(tripId)
        assertNotNull("Trip must exist in finalized database table", finalizedTrip)
        assertEquals(tripId, finalizedTrip?.id)

        // Preferences cleared only after confirmed terminal outcome
        assertEquals("Active trip preference must be cleared after verified finalization", null, preferencesManager.activeTripId.first())
    }

    // Motion resumed cancels finalization
    @Test
    fun doWork_whenVehicleMotionResumed_returnsSuccessWithoutFinalizing() = runBlocking {
        val tripId = "trip-resumed-motion"
        activeTripRepository.createActiveTrip(tripId, 37.3382, -121.8863, "AUTO_DETECTED", "MySucculentCorner")
        preferencesManager.setActiveTripId(tripId)
        preferencesManager.setInVehicleState(true) // Driving resumed during 4-min debounce

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals("Resumed motion must return Result.success() without modifying active trip", ListenableWorker.Result.success(), result)

        val activeInDb = activeTripRepository.getActiveTrip()
        assertNotNull("Active trip must remain active when driving resumed", activeInDb)
        assertEquals(tripId, activeInDb?.id)
        val finalizedInDb = tripRepository.getTripById(tripId)
        assertTrue("Trip must NOT be finalized when driving resumed", finalizedInDb == null)
    }
}
