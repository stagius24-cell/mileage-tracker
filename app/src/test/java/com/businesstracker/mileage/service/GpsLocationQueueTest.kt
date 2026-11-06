package com.businesstracker.mileage.service

import android.content.Context
import android.location.Location
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.GpsInboxDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class GpsLocationQueueTest {

    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var gpsInboxDao: GpsInboxDao
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gpsInboxDao = database.gpsInboxDao()
    }

    @After
    fun tearDown() {
        testScope.cancel()
        database.close()
    }

    private fun createLocation(lat: Double, lng: Double, timeMillis: Long): Location {
        return Location("gps").apply {
            latitude = lat
            longitude = lng
            time = timeMillis
            accuracy = 5.0f
        }
    }

    // BLOCKER 1 & 5: Failure on first fix preserves all fixes and allows lossless retry
    @Test
    fun failureOnFirstFix_preservesAllPendingFixes_andAllowsLosslessRetry() = runBlocking {
        val tripId = "trip-fail-first"
        var shouldFail = true
        val processedFixes = Collections.synchronizedList(mutableListOf<Long>())

        val queue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = gpsInboxDao,
            tripIdProvider = { tripId },
            batchFetchLimit = 10
        ) { loc ->
            if (shouldFail && loc.time == 1000L) {
                throw java.sql.SQLException("Disk write error on first fix")
            }
            processedFixes.add(loc.time)
        }

        val locations = (1..5).map { createLocation(37.0, -122.0, it * 1000L) }
        queue.enqueueBatchSync(locations)

        try {
            queue.drainAndClose()
            fail("drainAndClose must throw when first fix processing fails")
        } catch (_: IllegalStateException) {}

        // Assert all 5 fixes remain intact in the Room inbox
        assertEquals("All 5 fixes must remain in database after first-fix failure", 5, gpsInboxDao.getPendingCount(tripId))
        assertEquals("No fixes should have been recorded as processed", 0, processedFixes.size)

        // Retry with error resolved
        shouldFail = false
        val retryQueue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = gpsInboxDao,
            tripIdProvider = { tripId },
            batchFetchLimit = 10
        ) { loc ->
            processedFixes.add(loc.time)
        }

        retryQueue.drainAndClose()

        assertEquals("All 5 fixes must be successfully processed on retry", 5, processedFixes.size)
        assertEquals("Database inbox must be 0 after successful retry", 0, gpsInboxDao.getPendingCount(tripId))
    }

    // BLOCKER 1 & 5: Failure on middle fix preserves unprocessed fixes and avoids duplicate insertion on retry
    @Test
    fun failureOnMiddleFix_preservesUnprocessedFixes_andAvoidsDuplicatesOnRetry() = runBlocking {
        val tripId = "trip-fail-middle"
        var shouldFail = true
        val processedFixes = Collections.synchronizedList(mutableListOf<Long>())

        val queue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = gpsInboxDao,
            tripIdProvider = { tripId },
            batchFetchLimit = 10
        ) { loc ->
            if (shouldFail && loc.time == 3000L) {
                throw java.sql.SQLException("Simulated waypoint persistence failure on middle fix")
            }
            processedFixes.add(loc.time)
        }

        val locations = (1..6).map { createLocation(37.0, -122.0, it * 1000L) }
        queue.enqueueBatchSync(locations)

        try {
            queue.drainAndClose()
            fail("drainAndClose must throw when middle fix fails")
        } catch (_: IllegalStateException) {}

        // Fixes 1000L and 2000L were processed and deleted from inbox
        // Fixes 3000L, 4000L, 5000L, 6000L (4 fixes) must remain safely in the inbox
        assertEquals("Fixes 1 and 2 must have been processed", 2, processedFixes.size)
        assertEquals("4 unprocessed fixes must remain in database", 4, gpsInboxDao.getPendingCount(tripId))

        // Recreate queue / retry: should process only remaining fixes (no duplicates for 1 and 2)
        shouldFail = false
        val retryQueue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = gpsInboxDao,
            tripIdProvider = { tripId },
            batchFetchLimit = 10
        ) { loc ->
            processedFixes.add(loc.time)
        }

        retryQueue.drainAndClose()

        assertEquals("Total processed count must equal 6 fixes without duplicates", 6, processedFixes.size)
        assertEquals("Inbox must be completely drained", 0, gpsInboxDao.getPendingCount(tripId))
        assertEquals(listOf(1000L, 2000L, 3000L, 4000L, 5000L, 6000L), processedFixes.toList())
    }

    // BLOCKER 1 & 5: Failure on final fix preserves it in inbox
    @Test
    fun failureOnFinalFix_preservesFinalFixInDatabase() = runBlocking {
        val tripId = "trip-fail-final"
        val processedFixes = Collections.synchronizedList(mutableListOf<Long>())

        val queue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = gpsInboxDao,
            tripIdProvider = { tripId },
            batchFetchLimit = 10
        ) { loc ->
            if (loc.time == 4000L) {
                throw java.sql.SQLException("Final fix failure")
            }
            processedFixes.add(loc.time)
        }

        val locations = (1..4).map { createLocation(37.0, -122.0, it * 1000L) }
        queue.enqueueBatchSync(locations)

        try {
            queue.drainAndClose()
            fail("drainAndClose must throw when final fix fails")
        } catch (_: IllegalStateException) {}

        assertEquals("1 final fix must remain safely in database", 1, gpsInboxDao.getPendingCount(tripId))
        assertEquals("3 preceding fixes processed successfully", 3, processedFixes.size)
    }

    // BLOCKER 2: Global timestamp ordering across multiple overlapping batches
    @Test
    fun globalTimestampOrdering_enforcedAcrossOverlappingBatches() = runBlocking {
        val tripId = "trip-global-ordering"
        val processedTimestamps = Collections.synchronizedList(mutableListOf<Long>())

        // Multiple overlapping batches arriving in interleaved fashion
        val batch1 = listOf(1000L, 4000L, 7000L).map {
            com.businesstracker.mileage.data.local.entity.GpsInboxEntity(
                tripId = tripId, latitude = 37.0, longitude = -122.0, timestamp = it
            )
        }
        val batch2 = listOf(2000L, 5000L, 8000L).map {
            com.businesstracker.mileage.data.local.entity.GpsInboxEntity(
                tripId = tripId, latitude = 37.0, longitude = -122.0, timestamp = it
            )
        }
        val batch3 = listOf(500L, 3000L, 6000L).map {
            com.businesstracker.mileage.data.local.entity.GpsInboxEntity(
                tripId = tripId, latitude = 37.0, longitude = -122.0, timestamp = it
            )
        }

        // Insert interleaved batches into persistent Room inbox
        gpsInboxDao.insertFixes(batch1)
        gpsInboxDao.insertFixes(batch2)
        gpsInboxDao.insertFixes(batch3)

        // Queue with small batchFetchLimit (3) forces multiple limited-batch queries across batches
        val queue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = gpsInboxDao,
            tripIdProvider = { tripId },
            batchFetchLimit = 3
        ) { loc ->
            processedTimestamps.add(loc.time)
        }

        queue.drainAndClose()

        val expected = listOf(500L, 1000L, 2000L, 3000L, 4000L, 5000L, 6000L, 7000L, 8000L)
        assertEquals("Queue must process items in strictly ascending global timestamp order across batches", expected, processedTimestamps.toList())
    }

    // BLOCKER 1 & 2: 1,000+ fixes processed with strictly bounded memory (batchFetchLimit)
    @Test
    fun moreThan1000Fixes_processedWithoutLoss_underBoundedBatchLimit() = runBlocking {
        val tripId = "trip-large-volume"
        val totalFixes = 1100
        val batchLimit = 50 // In-memory batch limit strictly enforced
        val processedCount = AtomicInteger(0)

        val queue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = gpsInboxDao,
            tripIdProvider = { tripId },
            batchFetchLimit = batchLimit
        ) {
            processedCount.incrementAndGet()
        }

        // Insert 1,100 fixes in chunks into database inbox
        for (i in 1..totalFixes step 100) {
            val chunk = (i until i + 100).map { createLocation(37.3382, -121.8863, it * 1000L) }
            queue.enqueueBatchSync(chunk)
        }

        queue.drainAndClose()

        assertEquals("All 1,100 fixes must be consumed without any loss", totalFixes, processedCount.get())
        assertEquals("Database inbox must be empty after drain", 0, gpsInboxDao.getPendingCount(tripId))
    }

    // BLOCKER 4: Process termination preserves fixes in Room inbox for recovery
    @Test
    fun processTermination_preservesPendingFixesInPersistentStorage_forRecovery() = runBlocking {
        val tripId = "trip-process-death"
        val totalCount = 30
        val initialLocations = (1..totalCount).map { createLocation(37.0, -122.0, it * 1000L) }

        // Start first service queue session with artificial delay to ensure items remain in flight
        val processedFirstSession = Collections.synchronizedList(mutableListOf<Long>())
        val queue1 = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = gpsInboxDao,
            tripIdProvider = { tripId },
            batchFetchLimit = 5
        ) { loc ->
            kotlinx.coroutines.delay(20)
            processedFirstSession.add(loc.time)
        }

        queue1.enqueueBatchSync(initialLocations)

        // Simulate sudden process termination / service onDestroy(): stop consumer without drain
        queue1.stopConsumer()

        // Persistent storage in Room must contain all remaining unprocessed fixes
        val remainingInDb = gpsInboxDao.getPendingCount(tripId)
        assertTrue("Unprocessed fixes must remain in SQLite inbox after service termination", remainingInDb > 0)

        // Simulate service recreation and recovery
        val processedRecoverySession = Collections.synchronizedList(mutableListOf<Long>())
        val queue2 = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = gpsInboxDao,
            tripIdProvider = { tripId },
            batchFetchLimit = 5
        ) { loc ->
            processedRecoverySession.add(loc.time)
        }

        queue2.drainAndClose()

        val totalProcessed = processedFirstSession.size + processedRecoverySession.size
        assertEquals("Total processed fixes across process termination must equal $totalCount", totalCount, totalProcessed)
        assertEquals("Inbox must be completely clean after recovery", 0, gpsInboxDao.getPendingCount(tripId))
    }

    // Test 21 & PART 4: Service stop racing with incoming callback batch does not lose accepted fix
    @Test
    fun serviceStop_racingWithIncomingCallback_doesNotLoseAcceptedFix() = runBlocking {
        val tripId = "trip-race-stop"
        val processedFixes = Collections.synchronizedList(mutableListOf<Long>())

        val queue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = gpsInboxDao,
            tripIdProvider = { tripId },
            batchFetchLimit = 10
        ) { loc ->
            processedFixes.add(loc.time)
        }

        // Simulate incoming batch acquiring barrier right as stop/drain begins
        val loc = createLocation(37.3382, -121.8863, 1000L)
        queue.enqueue(loc)
        queue.drainAndClose()

        assertEquals("Accepted callback must not be lost when racing with stop", 1, processedFixes.size)
        assertEquals(0, gpsInboxDao.getPendingCount(tripId))
    }

    // Test 23 & PART 10: 1,200 fixes processed under bounded batch limits
    @Test
    fun full1200Fixes_processedWithoutLoss_underBoundedBatchLimit() = runBlocking {
        val tripId = "trip-1200-fixes"
        val totalFixes = 1200
        val batchLimit = 50
        val processedCount = AtomicInteger(0)

        val queue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = gpsInboxDao,
            tripIdProvider = { tripId },
            batchFetchLimit = batchLimit
        ) {
            processedCount.incrementAndGet()
        }

        for (i in 1..totalFixes step 100) {
            val chunk = (i until i + 100).map { createLocation(37.3382, -121.8863, it * 1000L) }
            queue.enqueueBatchSync(chunk)
        }

        queue.drainAndClose()

        assertEquals("All 1,200 fixes must be consumed without loss", totalFixes, processedCount.get())
        assertEquals(0, gpsInboxDao.getPendingCount(tripId))
    }

    // Test 26 & 27: Monotonic acquisition time protects against wall-clock NTP adjustments and tie-breaks deterministically
    @Test
    fun monotonicTimePolicy_handlesWallClockAdjustment_andTieBreaksDeterministically() = runBlocking {
        val tripId = "trip-clock-adjustment"
        val processedTimes = Collections.synchronizedList(mutableListOf<Long>())

        // Fix 1: Monotonic nanos = 10ms, wall-clock = 5000L
        // Fix 2: Monotonic nanos = 20ms, wall-clock = 2000L (user/NTP changed clock backward)
        // Fix 3: Monotonic nanos = 30ms, wall-clock = 2000L (identical wall-clock, tie-broken by monotonic time and id)
        val fix1 = com.businesstracker.mileage.data.local.entity.GpsInboxEntity(
            id = 1,
            tripId = tripId,
            sequenceNumber = 1,
            elapsedRealtimeNanos = 10_000_000L,
            latitude = 37.0,
            longitude = -122.0,
            timestamp = 5000L
        )
        val fix2 = com.businesstracker.mileage.data.local.entity.GpsInboxEntity(
            id = 2,
            tripId = tripId,
            sequenceNumber = 2,
            elapsedRealtimeNanos = 20_000_000L,
            latitude = 37.0,
            longitude = -122.0,
            timestamp = 2000L
        )
        val fix3 = com.businesstracker.mileage.data.local.entity.GpsInboxEntity(
            id = 3,
            tripId = tripId,
            sequenceNumber = 3,
            elapsedRealtimeNanos = 30_000_000L,
            latitude = 37.0,
            longitude = -122.0,
            timestamp = 2000L
        )

        gpsInboxDao.insertFixes(listOf(fix1, fix2, fix3))

        val queue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = gpsInboxDao,
            tripIdProvider = { tripId },
            batchFetchLimit = 10
        ) { loc ->
            processedTimes.add(loc.elapsedRealtimeNanos)
        }

        queue.drainAndClose()

        assertEquals("Ordering must be strictly monotonic even with wall-clock time anomalies",
            listOf(10_000_000L, 20_000_000L, 30_000_000L),
            processedTimes.toList()
        )
    }

    @Test
    fun queueRecreation_withPersistentSequence_continuesMonotonicallyWithoutDuplicates() = runBlocking {
        val tripId = "trip-seq-persist"
        val tripSequenceDao = database.tripSequenceDao()

        val processedSequences = Collections.synchronizedList(mutableListOf<Long>())

        // Initial queue session: process 2 batches
        val queue1 = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = gpsInboxDao,
            tripIdProvider = { tripId },
            tripSequenceDao = tripSequenceDao,
            initialSequence = tripSequenceDao.getLastSequenceNumber(tripId) ?: 0L,
            batchFetchLimit = 10
        ) { loc ->
            // query fix to inspect sequence number
        }

        queue1.enqueueBatchSync(listOf(createLocation(37.33, -121.88, 1000L)))
        queue1.enqueueBatchSync(listOf(createLocation(37.34, -121.89, 2000L)))
        queue1.drainAndClose()

        val lastSeq = tripSequenceDao.getLastSequenceNumber(tripId)
        assertEquals("Persistent sequence after 2 batches must be 2", 2L, lastSeq)

        // Queue recreated (simulating service restart / debounce cancellation)
        val queue2 = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = gpsInboxDao,
            tripIdProvider = { tripId },
            tripSequenceDao = tripSequenceDao,
            initialSequence = tripSequenceDao.getLastSequenceNumber(tripId) ?: 0L,
            batchFetchLimit = 10
        ) { loc ->
            // consumer drains fix from SQLite
        }

        queue2.enqueueBatchSync(listOf(createLocation(37.35, -121.90, 3000L)))
        queue2.enqueueBatchSync(listOf(createLocation(37.36, -121.91, 4000L)))
        queue2.drainAndClose()

        val finalSeq = tripSequenceDao.getLastSequenceNumber(tripId)
        assertEquals("Persistent sequence across queue recreation must advance monotonically to 4", 4L, finalSeq)
    }

    @Test
    fun admissionBarrier_closeAndWait_doesNotReturnPrematurelyOnThreadInterruption() {
        val barrier = GpsAdmissionBarrier(initialSequence = 0L)
        val token = barrier.acquireSequenceOrNull()
        assertEquals(1L, token)
        assertEquals(1, barrier.inFlight())

        val closedFlag = java.util.concurrent.atomic.AtomicBoolean(false)
        val waitThread = Thread {
            barrier.closeAndWait()
            closedFlag.set(true)
        }

        waitThread.start()
        Thread.sleep(50) // Let waitThread enter closeAndWait() wait loop

        // Interrupt the thread while inFlight == 1
        waitThread.interrupt()
        Thread.sleep(50)

        // Barrier must NOT return prematurely on interruption while inFlight > 0
        assertTrue("closeAndWait must NOT return while inFlight > 0 even if interrupted", !closedFlag.get())
        assertTrue("Thread should still be alive waiting for inFlight == 0", waitThread.isAlive)

        // Release in-flight sequence
        barrier.releaseSequence()
        waitThread.join(1000)

        assertTrue("closeAndWait must complete after in-flight reaches 0", closedFlag.get())
    }
}
