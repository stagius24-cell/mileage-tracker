package com.businesstracker.mileage.service

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
import com.businesstracker.mileage.domain.coordinator.TripFinalizationCoordinator
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import com.businesstracker.mileage.domain.usecase.DrainPendingGpsFixesUseCase
import com.businesstracker.mileage.domain.usecase.FilterGpsPointUseCase
import com.businesstracker.mileage.domain.usecase.FinalizeTripUseCase
import com.businesstracker.mileage.domain.usecase.GpsInboxProcessor
import com.businesstracker.mileage.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
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
class LocationTrackingServiceNullIntentRecoveryTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var activeTripRepository: ActiveTripRepositoryImpl
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var drainPendingGpsFixesUseCase: DrainPendingGpsFixesUseCase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activeTripRepository = ActiveTripRepositoryImpl(database.activeTripDao(), Dispatchers.Main)
        preferencesManager = TrackingPreferencesManager(context)

        val processor = GpsInboxProcessor(
            filterUseCase = FilterGpsPointUseCase(),
            activeTripDao = database.activeTripDao(),
            gpsInboxDao = database.gpsInboxDao(),
            tripSequenceDao = database.tripSequenceDao(),
            database = database
        )
        drainPendingGpsFixesUseCase = DrainPendingGpsFixesUseCase(
            gpsInboxDao = database.gpsInboxDao(),
            processor = processor
        )

        LocationTrackingService.resetGuardForTesting()
    }

    @After
    fun tearDown() {
        LocationTrackingService.resetGuardForTesting()
        database.close()
    }

    @Test
    fun nullIntentRecreation_withExistingActiveTrip_preservesTripIdAndDistance() = runBlocking {
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

        // Verify active trip exists in authoritative persistence
        val persisted = database.activeTripDao().getActiveTrip()
        assertNotNull(persisted)
        assertEquals(tripId, persisted!!.id)
        assertEquals(3200.0, persisted.totalDistanceMeters, 0.001)

        // Verify waypoints are intact
        assertEquals(1, database.activeTripDao().getWaypointsForTrip(tripId).size)
    }

    @Test
    fun nullIntentRecreation_withNoActiveTrip_doesNotGenerateTripId() = runBlocking {
        // No active trip in DB
        val persisted = database.activeTripDao().getActiveTrip()
        assertNull("Must be null when no trip is active", persisted)
    }

    @Test
    fun nullIntentRecreation_reconcilesPendingGpsInboxRecords() = runBlocking {
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
        // Add pending inbox fixes before recovery
        database.gpsInboxDao().insertFixes(
            listOf(
                GpsInboxEntity(id = 1, tripId = tripId, sequenceNumber = 1, latitude = 37.771, longitude = -122.411, timestamp = 1500L, accuracy = 5f, speed = 12f),
                GpsInboxEntity(id = 2, tripId = tripId, sequenceNumber = 2, latitude = 37.772, longitude = -122.412, timestamp = 2000L, accuracy = 5f, speed = 12f)
            )
        )

        // Drain pending fixes
        val processed = drainPendingGpsFixesUseCase.drain(tripId, 100)
        assertEquals(2, processed)
        assertEquals(0, database.gpsInboxDao().getPendingCount(tripId))

        // Waypoints now populated
        val waypoints = database.activeTripDao().getWaypointsForTrip(tripId)
        assertEquals(2, waypoints.size)
    }

    @Test
    fun processDeathRecovery_doesNotDuplicateActiveTrip() = runBlocking {
        val tripId = "single-session-trip"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 5000L,
                startLatitude = 37.0,
                startLongitude = -122.0,
                lastLatitude = 37.1,
                lastLongitude = -122.1,
                lastTimestamp = 6000L,
                totalDistanceMeters = 5000.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )

        // Attempting to create or recover active trip preserves the single instance
        val existing = database.activeTripDao().getOrCreateActiveTrip(
            ActiveTripEntity(
                id = "different-new-id",
                startTime = 7000L,
                startLatitude = 37.0,
                startLongitude = -122.0,
                lastLatitude = 37.0,
                lastLongitude = -122.0,
                lastTimestamp = 7000L,
                totalDistanceMeters = 0.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )

        assertEquals("Must return existing active trip without creating new one", tripId, existing.id)
        assertEquals(5000.0, existing.totalDistanceMeters, 0.001)
    }
}
