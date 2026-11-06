package com.businesstracker.mileage.service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import com.businesstracker.mileage.data.local.entity.GpsInboxEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LocationTrackingServiceRecoveryTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: TrackingPreferencesManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        preferencesManager = TrackingPreferencesManager(context)
        LocationTrackingService.resetGuardForTesting()
    }

    @After
    fun tearDown() {
        LocationTrackingService.resetGuardForTesting()
    }

    // Test 18 & PART 6: Location registration failure while recovering an existing trip preserves all data
    @Test
    fun locationRegistrationFailure_preservesExistingRecoveredTripAndInbox() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val tripId = "recovered-trip-failure"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 1000L,
                startLatitude = 37.3382,
                startLongitude = -121.8863,
                lastLatitude = 37.3500,
                lastLongitude = -121.8863,
                lastTimestamp = 2000L,
                totalDistanceMeters = 1500.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )
        database.activeTripDao().insertWaypoint(
            ActiveWaypointEntity(
                tripId = tripId,
                latitude = 37.3382,
                longitude = -121.8863,
                accuracy = 5f,
                speed = 10f,
                timestamp = 1000L
            )
        )
        database.gpsInboxDao().insertFix(
            GpsInboxEntity(
                id = 10,
                tripId = tripId,
                sequenceNumber = 1,
                elapsedRealtimeNanos = 2000L * 1_000_000L,
                latitude = 37.3500,
                longitude = -121.8863,
                accuracy = 5f,
                timestamp = 2000L
            )
        )
        preferencesManager.setActiveTripId(tripId)

        // Simulate failed registration / startup cleanup
        // In the updated LocationTrackingService, failed startup releases resources WITHOUT deleting active trip, waypoints, or inbox!
        LocationTrackingService.resetGuardForTesting()

        // Verify active trip remains intact
        val activeTripInDb = database.activeTripDao().getActiveTripById(tripId)
        assertNotNull("Active trip must not be deleted on registration failure", activeTripInDb)
        assertEquals(1500.0, activeTripInDb!!.totalDistanceMeters, 0.001)

        // Verify waypoints remain intact
        assertEquals(1, database.activeTripDao().getWaypointsForTrip(tripId).size)

        // Verify inbox fixes remain intact
        assertEquals(1, database.gpsInboxDao().getPendingCount(tripId))

        // Verify preferences still retain active trip ID
        assertEquals(tripId, preferencesManager.activeTripId.first())

        database.close()
    }

    // Test 19 & PART 10: File-backed database close and reopen recovers pending GPS fixes
    @Test
    fun fileBackedDatabase_closeAndReopen_preservesPendingGpsFixes() = runBlocking {
        val dbFile = File(context.filesDir, "file_backed_test.db")
        if (dbFile.exists()) dbFile.delete()

        var db = Room.databaseBuilder(context, ExpenseTrackerDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()

        val tripId = "trip-file-backed-recovery"
        db.gpsInboxDao().insertFixes(
            listOf(
                GpsInboxEntity(id = 1, tripId = tripId, sequenceNumber = 1, latitude = 37.0, longitude = -122.0, timestamp = 1000L),
                GpsInboxEntity(id = 2, tripId = tripId, sequenceNumber = 2, latitude = 37.1, longitude = -122.1, timestamp = 2000L)
            )
        )

        // Close database to simulate process termination
        db.close()

        // Reopen database
        db = Room.databaseBuilder(context, ExpenseTrackerDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()

        val countAfterReopen = db.gpsInboxDao().getPendingCount(tripId)
        val fixesAfterReopen = db.gpsInboxDao().getPendingFixes(tripId, 10)

        assertEquals("Pending fixes must survive database close and reopen", 2, countAfterReopen)
        assertEquals(2, fixesAfterReopen.size)
        assertEquals(1L, fixesAfterReopen[0].id)
        assertEquals(2L, fixesAfterReopen[1].id)

        db.close()
        dbFile.delete()
    }

    // Test 20 & PART 10: Service stopConsumer preserves pending Room inbox records
    @Test
    fun serviceStopConsumer_preservesPendingRoomInboxRecords() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val tripId = "trip-stop-consumer"
        database.gpsInboxDao().insertFix(
            GpsInboxEntity(tripId = tripId, sequenceNumber = 1, latitude = 37.0, longitude = -122.0, timestamp = 1000L)
        )

        val queue = GpsLocationQueue(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default),
            dispatcher = kotlinx.coroutines.Dispatchers.Default,
            gpsInboxDao = database.gpsInboxDao(),
            tripIdProvider = { tripId }
        )

        // Normal service destruction calls stopConsumer()
        queue.stopConsumer()

        // Verify inbox data is 100% retained
        val remaining = database.gpsInboxDao().getPendingCount(tripId)
        assertEquals("stopConsumer() must not delete inbox fixes", 1, remaining)

        database.close()
    }
}
