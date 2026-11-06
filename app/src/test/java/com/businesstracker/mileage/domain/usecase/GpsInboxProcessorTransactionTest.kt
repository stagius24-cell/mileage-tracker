package com.businesstracker.mileage.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.GpsInboxEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GpsInboxProcessorTransactionTest {

    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var processor: GpsInboxProcessor
    private lateinit var drainUseCase: DrainPendingGpsFixesUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        processor = GpsInboxProcessor(
            database = database,
            activeTripDao = database.activeTripDao(),
            gpsInboxDao = database.gpsInboxDao(),
            filterGpsPointUseCase = FilterGpsPointUseCase()
        )
        drainUseCase = DrainPendingGpsFixesUseCase(
            gpsInboxDao = database.gpsInboxDao(),
            gpsInboxProcessor = processor
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // Test 1: Successfully process a pending fix and verify waypoint, progress, and inbox acknowledgement are committed together
    @Test
    fun processFix_commitsWaypoint_progress_andInboxAck_atomically() = runBlocking {
        val tripId = "trip-atomic-1"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 1000L,
                startLatitude = 37.3382,
                startLongitude = -121.8863,
                lastLatitude = 37.3382,
                lastLongitude = -121.8863,
                lastTimestamp = 1000L,
                totalDistanceMeters = 0.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )
        // Initial waypoint at start of trip
        database.activeTripDao().insertWaypoint(
            com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity(
                tripId = tripId,
                latitude = 37.3382,
                longitude = -121.8863,
                accuracy = 5f,
                speed = 0f,
                timestamp = 1000L
            )
        )

        val fixId = database.gpsInboxDao().insertFix(
            GpsInboxEntity(
                tripId = tripId,
                sequenceNumber = 1,
                elapsedRealtimeNanos = 10000L * 1_000_000L,
                latitude = 37.3400,
                longitude = -121.8863,
                altitude = 0.0,
                accuracy = 5f,
                speed = 22.2f,
                timestamp = 10000L
            )
        )

        val result = processor.processFixTransaction(fixId)
        assertTrue(result is GpsInboxProcessor.ProcessResult.Accepted)

        // 1. Waypoint inserted with sourceFixId matching fixId
        val waypoints = database.activeTripDao().getWaypointsForTrip(tripId)
        assertEquals(2, waypoints.size)
        assertEquals(fixId, waypoints[1].sourceFixId)

        // 2. Active trip mileage updated
        val activeTrip = database.activeTripDao().getActiveTripById(tripId)
        assertNotNull(activeTrip)
        assertTrue(activeTrip!!.totalDistanceMeters > 0.0)

        // 3. Exact inbox fix acknowledged and deleted
        assertEquals(0, database.gpsInboxDao().getPendingCount(tripId))
    }

    // Test 2 & 8: Transaction rollback on failure ensures pending fix is preserved and no partial state is left
    @Test
    fun transactionRollback_preservesPendingFix_andLeavesZeroPartialState() = runBlocking {
        val tripId = "trip-rollback-1"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 1000L,
                startLatitude = 37.3382,
                startLongitude = -121.8863,
                lastLatitude = 37.3382,
                lastLongitude = -121.8863,
                lastTimestamp = 1000L,
                totalDistanceMeters = 0.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )
        // Initial waypoint
        database.activeTripDao().insertWaypoint(
            com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity(
                tripId = tripId,
                latitude = 37.3382,
                longitude = -121.8863,
                accuracy = 5f,
                speed = 0f,
                timestamp = 1000L
            )
        )

        val fixId = database.gpsInboxDao().insertFix(
            GpsInboxEntity(
                tripId = tripId,
                sequenceNumber = 1,
                elapsedRealtimeNanos = 10000L * 1_000_000L,
                latitude = 37.3400,
                longitude = -121.8863,
                accuracy = 5f,
                speed = 22.2f,
                timestamp = 10000L
            )
        )

        // Custom failing DAO that throws during waypoint insertion inside the transaction
        val failingActiveTripDao = object : com.businesstracker.mileage.data.local.ActiveTripDao by database.activeTripDao() {
            override suspend fun insertWaypoint(waypoint: com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity): Long {
                throw java.sql.SQLException("Simulated disk error during transaction")
            }
        }

        val failingProcessor = GpsInboxProcessor(
            database = database,
            activeTripDao = failingActiveTripDao,
            gpsInboxDao = database.gpsInboxDao(),
            filterGpsPointUseCase = FilterGpsPointUseCase()
        )

        try {
            failingProcessor.processFixTransaction(fixId)
            fail("Must throw SQLException")
        } catch (_: java.sql.SQLException) {}

        // Assert transaction rolled back: fix is intact, only initial waypoint remains, zero distance change
        assertEquals(1, database.gpsInboxDao().getPendingCount(tripId))
        assertEquals(1, database.activeTripDao().getWaypointsForTrip(tripId).size)
        assertEquals(0.0, database.activeTripDao().getActiveTripById(tripId)!!.totalDistanceMeters, 0.001)
    }

    // Test 3: Idempotency: Retrying a fix already recorded as a waypoint never increases mileage twice
    @Test
    fun retrySameFix_doesNotDoubleCountMileage() = runBlocking {
        val tripId = "trip-idempotency"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 1000L,
                startLatitude = 37.3382,
                startLongitude = -121.8863,
                lastLatitude = 37.3382,
                lastLongitude = -121.8863,
                lastTimestamp = 1000L,
                totalDistanceMeters = 0.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )
        // Initial waypoint
        database.activeTripDao().insertWaypoint(
            com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity(
                tripId = tripId,
                latitude = 37.3382,
                longitude = -121.8863,
                accuracy = 5f,
                speed = 0f,
                timestamp = 1000L
            )
        )

        val fixId = database.gpsInboxDao().insertFix(
            GpsInboxEntity(
                tripId = tripId,
                sequenceNumber = 1,
                elapsedRealtimeNanos = 10000L * 1_000_000L,
                latitude = 37.3400,
                longitude = -121.8863,
                accuracy = 5f,
                speed = 22.2f,
                timestamp = 10000L
            )
        )

        // First execution commits
        val result1 = processor.processFixTransaction(fixId)
        assertTrue(result1 is GpsInboxProcessor.ProcessResult.Accepted)
        val distanceAfterFirst = database.activeTripDao().getActiveTripById(tripId)!!.totalDistanceMeters
        assertTrue(distanceAfterFirst > 0.0)

        // Simulate crash right before fix deletion from inbox: re-insert the fix with same ID
        database.gpsInboxDao().insertFix(
            GpsInboxEntity(
                id = fixId,
                tripId = tripId,
                sequenceNumber = 1,
                elapsedRealtimeNanos = 10000L * 1_000_000L,
                latitude = 37.3400,
                longitude = -121.8863,
                accuracy = 5f,
                speed = 22.2f,
                timestamp = 10000L
            )
        )

        // Retry the exact same fix: idempotency guard detects existing waypoint with sourceFixId = fixId
        val result2 = processor.processFixTransaction(fixId)
        assertTrue("Idempotency guard must return AlreadyConsumed", result2 is GpsInboxProcessor.ProcessResult.AlreadyConsumed)
        val distanceAfterRetry = database.activeTripDao().getActiveTripById(tripId)!!.totalDistanceMeters

        assertEquals("Mileage must not be counted twice on retry of the same fix", distanceAfterFirst, distanceAfterRetry, 0.001)
        assertEquals("Waypoint count must remain 2", 2, database.activeTripDao().getWaypointsForTrip(tripId).size)
        assertEquals(0, database.gpsInboxDao().getPendingCount(tripId))
    }

    // Test 4: Rejected fixes (e.g. poor accuracy > 35m) are removed without altering mileage
    @Test
    fun rejectedFix_isRemovedWithoutAlteringMileage() = runBlocking {
        val tripId = "trip-rejected"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 1000L,
                startLatitude = 37.3382,
                startLongitude = -121.8863,
                lastLatitude = 37.3382,
                lastLongitude = -121.8863,
                lastTimestamp = 1000L,
                totalDistanceMeters = 0.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )

        // Accuracy is 50m (exceeds 35m threshold)
        val fixId = database.gpsInboxDao().insertFix(
            GpsInboxEntity(
                tripId = tripId,
                sequenceNumber = 1,
                elapsedRealtimeNanos = 2000L * 1_000_000L,
                latitude = 37.3400,
                longitude = -121.8863,
                accuracy = 50.0f,
                timestamp = 2000L
            )
        )

        val result = processor.processFixTransaction(fixId)
        assertTrue(result is GpsInboxProcessor.ProcessResult.Rejected)

        assertEquals("Mileage must not be altered for rejected fix", 0.0, database.activeTripDao().getActiveTripById(tripId)!!.totalDistanceMeters, 0.001)
        assertEquals("Zero waypoints inserted for rejected fix", 0, database.activeTripDao().getWaypointsForTrip(tripId).size)
        assertEquals("Rejected fix must be acknowledged and removed from inbox", 0, database.gpsInboxDao().getPendingCount(tripId))
    }

    // Test 5, 6, 7: Database errors during drain propagate immediately and prevent successful drain
    @Test
    fun databaseError_duringDrain_propagatesAndHaltsFinalization() = runBlocking {
        val tripId = "trip-drain-fail"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 1000L,
                startLatitude = 37.3382,
                startLongitude = -121.8863,
                lastLatitude = 37.3382,
                lastLongitude = -121.8863,
                lastTimestamp = 1000L,
                totalDistanceMeters = 0.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )

        database.gpsInboxDao().insertFix(
            GpsInboxEntity(tripId = tripId, sequenceNumber = 1, latitude = 37.34, longitude = -121.88, timestamp = 2000L)
        )

        // Simulate failing DAO
        val failingDao = object : com.businesstracker.mileage.data.local.GpsInboxDao by database.gpsInboxDao() {
            override suspend fun getPendingFixes(tripId: String, limit: Int): List<GpsInboxEntity> {
                throw java.sql.SQLException("Simulated database read failure")
            }
        }

        val failingDrain = DrainPendingGpsFixesUseCase(failingDao, processor)

        try {
            failingDrain.drain(tripId)
            fail("Drain must propagate database read failure")
        } catch (_: java.sql.SQLException) {}

        // Pending fix remains intact in database
        assertEquals(1, database.gpsInboxDao().getPendingCount(tripId))
    }

    @Test
    fun staleOrphanFix_forFinalizedTrip_isAcknowledgedAndRemovedFromInbox() = runBlocking {
        val tripId = "trip-orphan-finalized"
        database.tripLifecycleDao().insertOrUpdate(
            com.businesstracker.mileage.data.local.entity.TripLifecycleEntity(
                tripId = tripId,
                state = com.businesstracker.mileage.data.local.entity.TripLifecycleState.FINALIZED.value
            )
        )

        val fixId = database.gpsInboxDao().insertFix(
            GpsInboxEntity(tripId = tripId, sequenceNumber = 1, latitude = 37.33, longitude = -121.88, timestamp = 2000L)
        )

        val result = processor.processFixTransaction(fixId)
        assertTrue("Stale orphan fix for finalized trip must be StaleOrphanAcknowledged", result is GpsInboxProcessor.ProcessResult.StaleOrphanAcknowledged)
        assertEquals("Stale orphan fix must be deleted from inbox", 0, database.gpsInboxDao().getPendingCount(tripId))
    }

    @Test
    fun staleOrphanFix_forDiscardedTrip_isAcknowledgedAndRemovedFromInbox() = runBlocking {
        val tripId = "trip-orphan-discarded"
        database.tripLifecycleDao().insertOrUpdate(
            com.businesstracker.mileage.data.local.entity.TripLifecycleEntity(
                tripId = tripId,
                state = com.businesstracker.mileage.data.local.entity.TripLifecycleState.DISCARDED.value
            )
        )

        val fixId = database.gpsInboxDao().insertFix(
            GpsInboxEntity(tripId = tripId, sequenceNumber = 1, latitude = 37.33, longitude = -121.88, timestamp = 2000L)
        )

        val result = processor.processFixTransaction(fixId)
        assertTrue("Stale orphan fix for discarded trip must be StaleOrphanAcknowledged", result is GpsInboxProcessor.ProcessResult.StaleOrphanAcknowledged)
        assertEquals("Stale orphan fix must be deleted from inbox", 0, database.gpsInboxDao().getPendingCount(tripId))
    }

    @Test
    fun unresolvedOrphanFix_activeTripMissingWithoutTerminalLifecycle_preservesFixAndHaltsDrain() = runBlocking {
        val tripId = "trip-unresolved-orphan"
        // Active trip missing and no terminal lifecycle state
        val fixId = database.gpsInboxDao().insertFix(
            GpsInboxEntity(tripId = tripId, sequenceNumber = 1, latitude = 37.33, longitude = -121.88, timestamp = 2000L)
        )

        val result = processor.processFixTransaction(fixId)
        assertTrue("Unresolved orphan without terminal lifecycle must be UnresolvedOrphan", result is GpsInboxProcessor.ProcessResult.UnresolvedOrphan)
        assertEquals("Unresolved orphan must be preserved in inbox", 1, database.gpsInboxDao().getPendingCount(tripId))

        // Draining must throw and halt
        try {
            drainUseCase.drain(tripId)
            fail("Drain must halt and throw on unresolved orphan")
        } catch (_: IllegalStateException) {}

        assertEquals("Unresolved orphan must NOT be deleted after halted drain", 1, database.gpsInboxDao().getPendingCount(tripId))
    }

    @Test
    fun wallClockBackwardJump_withMonotonicNanos_processesCorrectlyWithoutSpike() = runBlocking {
        val tripId = "trip-wall-jump"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 10000L,
                startLatitude = 37.3382,
                startLongitude = -121.8863,
                lastLatitude = 37.3382,
                lastLongitude = -121.8863,
                lastTimestamp = 10000L,
                totalDistanceMeters = 0.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )

        // Seed initial accepted waypoint with monotonic time 10s
        database.activeTripDao().insertWaypoint(
            com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity(
                tripId = tripId,
                sequenceNumber = 1L,
                elapsedRealtimeNanos = 10_000_000_000L,
                latitude = 37.3382,
                longitude = -121.8863,
                accuracy = 5f,
                speed = 20f,
                timestamp = 10000L
            )
        )

        // Fix 2: Wall-clock jumped BACKWARDS to 5000L (-5 seconds), but monotonic time advanced by 9 seconds
        // Movement is ~200 meters. Over 9 seconds monotonic, speed is ~22 m/s (~49 mph <= 110 mph)
        val fixId = database.gpsInboxDao().insertFix(
            GpsInboxEntity(
                tripId = tripId,
                sequenceNumber = 2L,
                elapsedRealtimeNanos = 19_000_000_000L,
                latitude = 37.3400,
                longitude = -121.8863,
                accuracy = 5f,
                speed = 22f,
                timestamp = 5000L // backwards wall-clock jump!
            )
        )

        val result = processor.processFixTransaction(fixId)
        assertTrue("Fix must be accepted using monotonic clock despite wall-clock backwards jump", result is GpsInboxProcessor.ProcessResult.Accepted)
        assertEquals(0, database.gpsInboxDao().getPendingCount(tripId))
        assertEquals(2, database.activeTripDao().getWaypointsForTrip(tripId).size)
    }

    @Test
    fun rebootDetection_elapsedRealtimeNanosReset_rebaselinesSafelyWithSensorSpeed() = runBlocking {
        val tripId = "trip-reboot-rebaseline"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 10000L,
                startLatitude = 37.3382,
                startLongitude = -121.8863,
                lastLatitude = 37.3382,
                lastLongitude = -121.8863,
                lastTimestamp = 10000L,
                totalDistanceMeters = 0.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )

        // Waypoint before reboot had monotonic nanos = 50_000_000_000L
        database.activeTripDao().insertWaypoint(
            com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity(
                tripId = tripId,
                sequenceNumber = 1L,
                elapsedRealtimeNanos = 50_000_000_000L,
                latitude = 37.3382,
                longitude = -121.8863,
                accuracy = 5f,
                speed = 15f,
                timestamp = 10000L
            )
        )

        // Device reboots! Monotonic nanos resets to 2_000_000_000L (< 50_000_000_000L)
        // Hardware GPS provides sensor speed = 15.0 m/s (~33.5 mph)
        val fixId = database.gpsInboxDao().insertFix(
            GpsInboxEntity(
                tripId = tripId,
                sequenceNumber = 2L,
                elapsedRealtimeNanos = 2_000_000_000L,
                latitude = 37.3400,
                longitude = -121.8863,
                accuracy = 5f,
                speed = 15f,
                timestamp = 20000L
            )
        )

        val result = processor.processFixTransaction(fixId)
        assertTrue("Fix after reboot must be accepted via safe Doppler sensor re-baselining", result is GpsInboxProcessor.ProcessResult.Accepted)
        assertEquals(0, database.gpsInboxDao().getPendingCount(tripId))
        assertEquals(2, database.activeTripDao().getWaypointsForTrip(tripId).size)
    }
}
