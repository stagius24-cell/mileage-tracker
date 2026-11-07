package com.businesstracker.mileage.service

import android.app.Application
import android.app.Service
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import com.businesstracker.mileage.data.local.entity.GpsInboxEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleState
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.repository.ActiveTripRepositoryImpl
import com.businesstracker.mileage.domain.usecase.DrainPendingGpsFixesUseCase
import com.businesstracker.mileage.domain.usecase.FilterGpsPointUseCase
import com.businesstracker.mileage.domain.usecase.GpsInboxProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LocationTrackingServiceNullIntentRecoveryTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var activeTripRepository: ActiveTripRepositoryImpl
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var drainPendingGpsFixesUseCase: DrainPendingGpsFixesUseCase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activeTripRepository = ActiveTripRepositoryImpl(database.activeTripDao(), Dispatchers.Main)
        preferencesManager = TrackingPreferencesManager(context)
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
        LocationTrackingService.resetGuardForTesting()
        database.close()
    }

    private fun createService(): LocationTrackingService {
        val service = LocationTrackingService()
        service.attachContextForTesting(context)
        service.setTestDependencies(
            activeTripRepo = activeTripRepository,
            helper = notificationHelper,
            lifecycleDao = database.tripLifecycleDao(),
            inboxDao = database.gpsInboxDao(),
            drainUseCase = drainPendingGpsFixesUseCase,
            seqDao = database.tripSequenceDao(),
            db = database,
            prefs = preferencesManager,
            dispatcher = Dispatchers.Main
        )
        service.locationUpdatesRegistrar = { true }
        return service
    }

    @Test
    fun nullIntentRecovery_restoresExistingTripWithoutDuplicating() = runBlocking {
        val tripId = "recovered-active-trip"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 1000L,
                startLatitude = 37.77,
                startLongitude = -122.41,
                lastLatitude = 37.79,
                lastLongitude = -122.43,
                lastTimestamp = 2000L,
                totalDistanceMeters = 3200.0,
                trackingMethod = "AUTO_DETECTED",
                isPaused = false
            )
        )
        database.activeTripDao().insertWaypoint(
            ActiveWaypointEntity(
                tripId = tripId,
                latitude = 37.77,
                longitude = -122.41,
                accuracy = 5f,
                speed = 10f,
                timestamp = 1000L
            )
        )
        preferencesManager.setActiveTripId(tripId)

        val service = createService()
        val result = service.onStartCommand(null, 0, 1)
        assertEquals(Service.START_STICKY, result)

        val state = LocationTrackingService.serviceState.value
        assertTrue("Service must be actively tracking after recovery", state.isTracking)
        assertEquals(tripId, state.tripId)
        assertEquals(3200.0, state.distanceMeters, 0.001)

        val persisted = database.activeTripDao().getActiveTrip()
        assertNotNull(persisted)
        assertEquals(tripId, persisted!!.id)
        assertEquals(3200.0, persisted.totalDistanceMeters, 0.001)

        service.onDestroy()
    }

    @Test
    fun nullIntentRecovery_processesPendingGpsInboxRecordsExactlyOnce() = runBlocking {
        val tripId = "reconcile-inbox-trip"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 1000L,
                startLatitude = 37.77,
                startLongitude = -122.41,
                lastLatitude = 37.77,
                lastLongitude = -122.41,
                lastTimestamp = 1000L,
                totalDistanceMeters = 0.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )
        database.gpsInboxDao().insertFixes(
            listOf(
                GpsInboxEntity(id = 1, tripId = tripId, sequenceNumber = 1, latitude = 37.771, longitude = -122.411, timestamp = 1500L, accuracy = 5f, speed = 12f),
                GpsInboxEntity(id = 2, tripId = tripId, sequenceNumber = 2, latitude = 37.772, longitude = -122.412, timestamp = 2000L, accuracy = 5f, speed = 12f)
            )
        )

        val service = createService()
        service.onStartCommand(null, 0, 1)

        assertEquals("Pending inbox count must be 0 after drain during recovery", 0, database.gpsInboxDao().getPendingCount(tripId))
        val waypoints = database.activeTripDao().getWaypointsForTrip(tripId)
        assertEquals("Waypoints must be populated from drained inbox fixes", 2, waypoints.size)

        service.onDestroy()
    }

    @Test
    fun nullIntentRecovery_pausedTripRemainsPaused() = runBlocking {
        val tripId = "paused-trip"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 1000L,
                startLatitude = 37.77,
                startLongitude = -122.41,
                lastLatitude = 37.79,
                lastLongitude = -122.43,
                lastTimestamp = 2000L,
                totalDistanceMeters = 4500.0,
                trackingMethod = "AUTO_DETECTED",
                isPaused = true
            )
        )

        val service = createService()
        service.onStartCommand(null, 0, 1)

        val state = LocationTrackingService.serviceState.value
        assertTrue("Service state must reflect paused status", state.isPaused)
        assertEquals(tripId, state.tripId)
        assertEquals(4500.0, state.distanceMeters, 0.001)

        service.onDestroy()
    }

    @Test
    fun nullIntentRecovery_missingPermissions_preservesActiveTripAndSetsRecoveryRequired() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val tripId = "perm-missing-trip"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 1000L,
                startLatitude = 37.77,
                startLongitude = -122.41,
                lastLatitude = 37.79,
                lastLongitude = -122.43,
                lastTimestamp = 2000L,
                totalDistanceMeters = 5500.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(
                tripId = tripId,
                state = TripLifecycleState.TRACKING.value
            )
        )
        preferencesManager.setActiveTripId(tripId)

        val service = createService()
        service.onStartCommand(null, 0, 1)

        val activeTrip = database.activeTripDao().getActiveTripById(tripId)
        assertNotNull("Active trip must NOT be deleted when permissions are missing", activeTrip)
        assertEquals(5500.0, activeTrip!!.totalDistanceMeters, 0.001)

        val lifecycle = database.tripLifecycleDao().getState(tripId)
        assertEquals(TripLifecycleState.RECOVERY_REQUIRED.value, lifecycle)

        val state = LocationTrackingService.serviceState.value
        assertFalse(state.isTracking)
        assertTrue(state.isRecoveryRequired)
        assertEquals(tripId, state.tripId)

        service.onDestroy()
    }

    @Test
    fun nullIntentRecovery_failedLocationRegistration_preservesPersistedData() = runBlocking {
        val tripId = "failed-reg-trip"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 1000L,
                startLatitude = 37.77,
                startLongitude = -122.41,
                lastLatitude = 37.79,
                lastLongitude = -122.43,
                lastTimestamp = 2000L,
                totalDistanceMeters = 6200.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )
        database.activeTripDao().insertWaypoint(
            ActiveWaypointEntity(
                tripId = tripId,
                latitude = 37.77,
                longitude = -122.41,
                accuracy = 5f,
                speed = 10f,
                timestamp = 1000L
            )
        )
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(
                tripId = tripId,
                state = TripLifecycleState.TRACKING.value
            )
        )

        val service = createService()
        // Simulate registration failure
        service.locationUpdatesRegistrar = { false }
        service.onStartCommand(null, 0, 1)

        val activeTrip = database.activeTripDao().getActiveTripById(tripId)
        assertNotNull("Active trip must NOT be deleted when registration fails", activeTrip)
        assertEquals(6200.0, activeTrip!!.totalDistanceMeters, 0.001)

        assertEquals("Waypoints must be completely preserved", 1, database.activeTripDao().getWaypointsForTrip(tripId).size)

        val lifecycle = database.tripLifecycleDao().getState(tripId)
        assertEquals(TripLifecycleState.RECOVERY_REQUIRED.value, lifecycle)

        val state = LocationTrackingService.serviceState.value
        assertFalse(state.isTracking)
        assertTrue(state.isRecoveryRequired)

        service.onDestroy()
    }

    @Test
    fun nullIntentRecovery_withNoActiveTrip_stopsCleanly() = runBlocking {
        val service = createService()
        service.onStartCommand(null, 0, 1)

        assertNull("No active trip should be generated", database.activeTripDao().getActiveTrip())
        assertFalse(LocationTrackingService.serviceState.value.isTracking)

        service.onDestroy()
    }
}
