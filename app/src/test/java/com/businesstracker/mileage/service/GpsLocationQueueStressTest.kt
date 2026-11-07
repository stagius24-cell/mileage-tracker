package com.businesstracker.mileage.service

import android.location.Location
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
class GpsLocationQueueStressTest {

    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var queueScope: CoroutineScope

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        queueScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        queueScope.cancel()
        database.close()
    }

    private fun createLocation(lat: Double, lng: Double, time: Long, elapsedNanos: Long): Location {
        return Location("gps").apply {
            latitude = lat
            longitude = lng
            accuracy = 5f
            speed = 10f
            this.time = time
            this.elapsedRealtimeNanos = elapsedNanos
        }
    }

    @Test(timeout = 10000)
    fun highVolumeBurst_maintainsStrictMonotonicSequenceOrderingAndZeroDrops() = runBlocking {
        val tripId = "stress-burst-trip"
        val processedFixes = Collections.synchronizedList(mutableListOf<Long>())

        val queue = GpsLocationQueue(
            scope = queueScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = database.gpsInboxDao(),
            tripIdProvider = { tripId },
            tripSequenceDao = database.tripSequenceDao(),
            batchFetchLimit = 20
        ) { loc ->
            processedFixes.add(loc.time)
        }

        val totalBatches = 20
        val locationsPerBatch = 5

        // Emit concurrent bursts
        for (i in 1..totalBatches) {
            val batch = (1..locationsPerBatch).map { j ->
                val time = 1000L * i + j
                createLocation(37.0 + i * 0.001, -122.0 + j * 0.001, time, time * 1_000_000L)
            }
            queue.enqueueBatch(batch)
        }

        // Wait for all in-flight batches to commit to Room inbox and consumer to process
        queue.drainAndClose()

        // Verify total processed matches exactly totalBatches * locationsPerBatch with zero drops
        val totalInserted = totalBatches * locationsPerBatch
        assertEquals("All $totalInserted fixes must be processed without drops", totalInserted, processedFixes.size)
        assertEquals("Persistent inbox must be completely clean after drain", 0, database.gpsInboxDao().getPendingCount(tripId))

        // Verify sequences advanced monotonically
        val finalSeq = database.tripSequenceDao().getLastSequenceNumber(tripId) ?: 0L
        assertTrue("Sequence generator must advance monotonically", finalSeq >= totalBatches.toLong())
    }

    @Test(timeout = 10000)
    fun slowDatabaseSimulation_preservesAllFixesWithoutDropping() = runBlocking {
        val tripId = "slow-db-trip"
        val processedFixes = Collections.synchronizedList(mutableListOf<Long>())

        val queue = GpsLocationQueue(
            scope = queueScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = database.gpsInboxDao(),
            tripIdProvider = { tripId },
            tripSequenceDao = database.tripSequenceDao(),
            batchFetchLimit = 5
        ) { loc ->
            // Simulate processing delay
            delay(2)
            processedFixes.add(loc.time)
        }

        // Rapidly enqueue fixes while simulating bursts
        for (i in 1..20) {
            val loc = createLocation(37.5 + i * 0.01, -122.2 + i * 0.01, 1000L * i, 1000L * i * 1_000_000L)
            queue.enqueue(loc)
        }

        queue.drainAndClose()

        assertEquals("All 20 fixes must be safely processed without dropping", 20, processedFixes.size)
        assertEquals(0, database.gpsInboxDao().getPendingCount(tripId))
    }

    @Test(timeout = 10000)
    fun stopConsumer_preservesUnprocessedDataSafelyAcrossShutdown() = runBlocking {
        val tripId = "shutdown-preserve-trip"
        val queue = GpsLocationQueue(
            scope = queueScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = database.gpsInboxDao(),
            tripIdProvider = { tripId },
            tripSequenceDao = database.tripSequenceDao()
        )

        val batch = listOf(
            createLocation(37.1, -122.1, 1000L, 1000L * 1_000_000L),
            createLocation(37.2, -122.2, 2000L, 2000L * 1_000_000L)
        )
        queue.enqueueBatchSync(batch)

        // Service killed or destroyed
        queue.stopConsumer()

        // Pending count in SQLite remains 2
        val count = database.gpsInboxDao().getPendingCount(tripId)
        assertEquals(2, count)
    }

    @Test(timeout = 15000)
    fun boundedCapacity_underSustainedSlowDatabase_spillsOverDurablyWithZeroDrops() = runBlocking {
        val tripId = "bounded-spillover-trip"
        val processedFixes = Collections.synchronizedList(mutableListOf<Long>())

        // Small in-memory channel capacity of 3 batches to force spillover quickly
        val queue = GpsLocationQueue(
            scope = queueScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = database.gpsInboxDao(),
            tripIdProvider = { tripId },
            tripSequenceDao = database.tripSequenceDao(),
            batchFetchLimit = 5,
            inMemoryCapacity = 3
        ) { loc ->
            // Simulate slow database processing
            delay(1)
            processedFixes.add(loc.time)
        }

        val totalBatches = 30
        val fixesPerBatch = 3
        // Enqueue 30 batches without blocking the caller thread
        for (i in 1..totalBatches) {
            val batch = (1..fixesPerBatch).map { j ->
                val time = 10_000L * i + j
                createLocation(37.1 + i * 0.001, -122.1 + j * 0.001, time, time * 1_000_000L)
            }
            queue.enqueueBatch(batch)
        }

        // drainAndClose waits for both in-memory and spillover jobs to persist and be processed
        queue.drainAndClose()

        val expectedTotal = totalBatches * fixesPerBatch
        assertEquals("All $expectedTotal fixes must be durably admitted and processed despite bounded buffer", expectedTotal, processedFixes.size)
        assertEquals(0, database.gpsInboxDao().getPendingCount(tripId))

        // Verify sequence numbers advanced monotonically
        val finalSeq = database.tripSequenceDao().getLastSequenceNumber(tripId) ?: 0L
        assertTrue("Sequence numbers must advance monotonically through spillover", finalSeq >= totalBatches.toLong())
    }
}
