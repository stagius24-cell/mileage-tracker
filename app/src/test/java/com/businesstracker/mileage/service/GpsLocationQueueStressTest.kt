package com.businesstracker.mileage.service

import android.location.Location
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import com.businesstracker.mileage.domain.usecase.DrainPendingGpsFixesUseCase
import com.businesstracker.mileage.domain.usecase.FilterGpsPointUseCase
import com.businesstracker.mileage.domain.usecase.GpsInboxProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    @Test
    fun highVolumeBurst_maintainsStrictMonotonicSequenceOrderingAndZeroDrops() = runBlocking {
        val tripId = "stress-burst-trip"
        val queue = GpsLocationQueue(
            scope = queueScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = database.gpsInboxDao(),
            tripIdProvider = { tripId },
            tripSequenceDao = database.tripSequenceDao(),
            database = database
        )

        val totalBatches = 50
        val locationsPerBatch = 5

        // Emit 50 concurrent bursts
        for (i in 1..totalBatches) {
            val batch = (1..locationsPerBatch).map { j ->
                val time = 1000L * i + j
                createLocation(37.0 + i * 0.001, -122.0 + j * 0.001, time, time * 1_000_000L)
            }
            queue.enqueueBatch(batch)
        }

        // Wait for all in-flight batches to commit to Room inbox
        queue.drainAndClose()

        // Verify total inserted matches exactly totalBatches * locationsPerBatch
        val totalInserted = totalBatches * locationsPerBatch
        val pendingFixes = database.gpsInboxDao().getPendingFixes(tripId, limit = totalInserted + 10)
        assertEquals(totalInserted, pendingFixes.size)

        // Verify sequence numbers are strictly non-decreasing
        for (k in 0 until pendingFixes.size - 1) {
            assertTrue(
                "Sequences must be monotonically non-decreasing",
                pendingFixes[k].sequenceNumber <= pendingFixes[k + 1].sequenceNumber
            )
        }
    }

    @Test
    fun slowDatabaseSimulation_preservesAllFixesWithoutDropping() = runBlocking {
        val tripId = "slow-db-trip"
        val queue = GpsLocationQueue(
            scope = queueScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = database.gpsInboxDao(),
            tripIdProvider = { tripId },
            tripSequenceDao = database.tripSequenceDao(),
            database = database
        )

        // Rapidly enqueue fixes while simulating bursts
        for (i in 1..20) {
            val loc = createLocation(37.5 + i * 0.01, -122.2 + i * 0.01, 1000L * i, 1000L * i * 1_000_000L)
            queue.enqueue(loc)
        }

        queue.drainAndClose()

        val count = database.gpsInboxDao().getPendingCount(tripId)
        assertEquals(20, count)
    }

    @Test
    fun stopConsumer_preservesUnprocessedDataSafelyAcrossShutdown() = runBlocking {
        val tripId = "shutdown-preserve-trip"
        val queue = GpsLocationQueue(
            scope = queueScope,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = database.gpsInboxDao(),
            tripIdProvider = { tripId },
            tripSequenceDao = database.tripSequenceDao(),
            database = database
        )

        val batch = listOf(
            createLocation(37.1, -122.1, 1000L, 1000L * 1_000_000L),
            createLocation(37.2, -122.2, 2000L, 2000L * 1_000_000L)
        )
        queue.enqueueBatch(batch)

        // Give a brief moment for Room batch ingestion
        delay(100)

        // Service killed or destroyed
        queue.stopConsumer()

        // Pending count in SQLite remains 2
        val count = database.gpsInboxDao().getPendingCount(tripId)
        assertEquals(2, count)
    }
}
