package com.businesstracker.mileage.worker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import com.businesstracker.mileage.data.local.entity.GpsInboxEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleState
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.repository.ActiveTripRepositoryImpl
import com.businesstracker.mileage.data.repository.TripRepositoryImpl
import com.businesstracker.mileage.domain.coordinator.TripFinalizationCoordinator
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import com.businesstracker.mileage.domain.usecase.DrainPendingGpsFixesUseCase
import com.businesstracker.mileage.domain.usecase.FilterGpsPointUseCase
import com.businesstracker.mileage.domain.usecase.FinalizeTripUseCase
import com.businesstracker.mileage.domain.usecase.GeocodeLocationUseCase
import com.businesstracker.mileage.domain.usecase.GpsInboxProcessor
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.service.NotificationHelper
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private lateinit var drainPendingGpsFixesUseCase: DrainPendingGpsFixesUseCase

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

        val processor = GpsInboxProcessor(
            database = database,
            activeTripDao = database.activeTripDao(),
            gpsInboxDao = database.gpsInboxDao(),
            filterGpsPointUseCase = FilterGpsPointUseCase()
        )
        drainPendingGpsFixesUseCase = DrainPendingGpsFixesUseCase(
            gpsInboxDao = database.gpsInboxDao(),
            gpsInboxProcessor = processor
        )

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
                        notificationHelper = notificationHelper,
                        drainPendingGpsFixesUseCase = drainPendingGpsFixesUseCase,
                        gpsInboxDao = database.gpsInboxDao(),
                        tripLifecycleDao = database.tripLifecycleDao()
                    )
                }
            })
            .build()
    }

    // Test 13: When service is still draining, doWork() returns Result.retry() and active preferences remain intact
    @Test
    fun doWork_whileServiceIsDraining_returnsRetry_andPreservesActivePreferences() = runBlocking {
        val tripId = "trip-active-draining-1"
        activeTripRepository.createActiveTrip(tripId, 37.3382, -121.8863, "AUTO_DETECTED", "MySucculentCorner")
        preferencesManager.setActiveTripId(tripId)
        preferencesManager.setVehicleAnchor(37.3382, -121.8863, System.currentTimeMillis())
        preferencesManager.setInVehicleState(false)

        LocationTrackingService.setDrainingForTesting(true)

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(tripId, preferencesManager.activeTripId.first())
        val activeInDb = activeTripRepository.getActiveTrip()
        assertNotNull(activeInDb)
        assertEquals(tripId, activeInDb?.id)

        val finalizedInDb = tripRepository.getTripById(tripId)
        assertNull(finalizedInDb)
    }

    // Tests 9 & 10: Inactive service recovery drains pending GPS fixes before finalization, and saved trip contains last pending fix
    @Test
    fun doWork_afterServiceInterruption_drainsPendingGpsInbox_andFinalizesSuccessfully() = runBlocking {
        val tripId = "trip-recovery-with-pending"
        val activeTrip = ActiveTripEntity(
            id = tripId,
            startTime = 1000L,
            startLatitude = 37.3382,
            startLongitude = -121.8863,
            lastLatitude = 37.3382,
            lastLongitude = -121.8863,
            lastTimestamp = 1000L,
            totalDistanceMeters = 0.0,
            trackingMethod = "AUTO_DETECTED",
            suggestedBusiness = "MySucculentCorner"
        )
        database.activeTripDao().insertActiveTrip(activeTrip)
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(tripId = tripId, state = TripLifecycleState.TRACKING.value)
        )

        // Initial waypoint
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

        // Two pending fixes in GPS inbox waiting to be drained (> 200m movement)
        database.gpsInboxDao().insertFixes(
            listOf(
                GpsInboxEntity(
                    id = 101,
                    tripId = tripId,
                    sequenceNumber = 1,
                    elapsedRealtimeNanos = 2000L * 1_000_000L,
                    latitude = 37.3400,
                    longitude = -121.8863,
                    altitude = 0.0,
                    accuracy = 5f,
                    speed = 15f,
                    timestamp = 2000L
                ),
                GpsInboxEntity(
                    id = 102,
                    tripId = tripId,
                    sequenceNumber = 2,
                    elapsedRealtimeNanos = 3000L * 1_000_000L,
                    latitude = 37.3450, // Final fix > 700m away
                    longitude = -121.8863,
                    altitude = 0.0,
                    accuracy = 5f,
                    speed = 15f,
                    timestamp = 3000L
                )
            )
        )

        preferencesManager.setActiveTripId(tripId)
        preferencesManager.setInVehicleState(false)
        LocationTrackingService.resetGuardForTesting()

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // Verify inbox is completely drained
        assertEquals(0, database.gpsInboxDao().getPendingCount(tripId))

        // Verify trip was finalized and contains the final waypoint from the inbox
        val finalizedTrip = tripRepository.getTripById(tripId)
        assertNotNull(finalizedTrip)
        assertEquals(tripId, finalizedTrip?.id)
        assertTrue("Finalized trip must contain waypoints including last pending fix", finalizedTrip!!.waypointsJson.contains("37.345"))
        assertEquals(37.3450, finalizedTrip.endLatitude, 0.001)

        // Authoritative lifecycle state in Room is FINALIZED
        val lifecycle = database.tripLifecycleDao().getLifecycle(tripId)
        assertEquals(TripLifecycleState.FINALIZED.value, lifecycle?.state)
    }

    // Test 17: Mandatory Regression Scenario: Delayed Trip A retry cannot modify Trip B!
    @Test
    fun delayedTripARetry_cannotModifyOrDeleteTripB() = runBlocking {
        val tripAId = "trip-a-finalized"
        val tripBId = "trip-b-active"

        // 1. Trip A finalized previously
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(tripId = tripAId, state = TripLifecycleState.FINALIZED.value)
        )
        database.tripDao().insertTripIgnore(
            com.businesstracker.mileage.data.local.entity.TripEntity(
                id = tripAId,
                date = "2026-11-06",
                startTime = 1000L,
                endTime = 2000L,
                startAddress = null,
                endAddress = null,
                startLatitude = 37.0,
                startLongitude = -122.0,
                endLatitude = 37.1,
                endLongitude = -122.1,
                totalDistanceMiles = 5.0,
                businessMiles = 5.0,
                businessEntity = "MySucculentCorner",
                purpose = "Nursery Pickup",
                trackingMethod = "AUTO_DETECTED",
                classification = "BUSINESS",
                applicableRate = 0.67,
                calculatedDeduction = 3.35,
                notes = null,
                waypointsJson = "[]",
                syncStatus = "PENDING",
                syncedAt = null
            )
        )

        // 2. Trip B is currently actively tracking with pending fixes and waypoints
        val tripB = ActiveTripEntity(
            id = tripBId,
            startTime = 5000L,
            startLatitude = 37.5,
            startLongitude = -122.5,
            lastLatitude = 37.51,
            lastLongitude = -122.51,
            lastTimestamp = 6000L,
            totalDistanceMeters = 800.0,
            trackingMethod = "AUTO_DETECTED",
            suggestedBusiness = "MySucculentCorner"
        )
        database.activeTripDao().insertActiveTrip(tripB)
        database.activeTripDao().insertWaypoint(
            ActiveWaypointEntity(tripId = tripBId, latitude = 37.5, longitude = -122.5, altitude = 0.0, accuracy = 4f, speed = 10f, timestamp = 5000L)
        )
        database.gpsInboxDao().insertFix(
            GpsInboxEntity(id = 501, tripId = tripBId, sequenceNumber = 1, elapsedRealtimeNanos = 6000L * 1_000_000L, latitude = 37.51, longitude = -122.51, timestamp = 6000L)
        )
        preferencesManager.setActiveTripId(tripBId)

        // 3. Delayed finalization retry executes for Trip A
        val finalTripA = coordinator.finalizeTrip(tripAId)
        assertNotNull(finalTripA)
        assertEquals(tripAId, finalTripA?.id)

        // 4. VERIFY: Trip B is 100% UNTOUCHED!
        val activeTripBInDb = database.activeTripDao().getActiveTripById(tripBId)
        assertNotNull("Trip B must remain active in Room", activeTripBInDb)
        assertEquals(800.0, activeTripBInDb?.totalDistanceMeters ?: 0.0, 0.001)

        val waypointsB = database.activeTripDao().getWaypointsForTrip(tripBId)
        assertEquals(1, waypointsB.size)

        val pendingGpsB = database.gpsInboxDao().getPendingCount(tripBId)
        assertEquals(1, pendingGpsB)

        assertEquals("Preferences must still point to active Trip B", tripBId, preferencesManager.activeTripId.first())
    }

    // Test 16: Discarded false trip retry returns null and does not corrupt database
    @Test
    fun discardedTripRetry_returnsNull_andPreservesDiscardedState() = runBlocking {
        val tripId = "trip-false-discarded"
        coordinator.discardFalseTrip(tripId)

        val lifecycle = database.tripLifecycleDao().getLifecycle(tripId)
        assertEquals(TripLifecycleState.DISCARDED.value, lifecycle?.state)
        assertTrue(preferencesManager.isTripDiscarded(tripId))

        // Retrying finalization returns null cleanly
        val retryResult = coordinator.finalizeTrip(tripId)
        assertNull("Discarded trip finalization retry must return null", retryResult)
    }

    // Test: Resumed vehicle motion cancels debounce
    @Test
    fun doWork_whenVehicleMotionResumed_returnsSuccessWithoutFinalizing() = runBlocking {
        val tripId = "trip-resumed-motion"
        activeTripRepository.createActiveTrip(tripId, 37.3382, -121.8863, "AUTO_DETECTED", "MySucculentCorner")
        preferencesManager.setActiveTripId(tripId)
        preferencesManager.setInVehicleState(true)

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertNotNull(activeTripRepository.getActiveTrip())
        assertNull(tripRepository.getTripById(tripId))
    }
}
