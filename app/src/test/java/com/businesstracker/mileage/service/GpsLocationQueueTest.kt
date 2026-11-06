package com.businesstracker.mileage.service

import android.location.Location
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
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class GpsLocationQueueTest {

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() {
        testScope.cancel()
    }

    private fun createLocation(lat: Double, lng: Double, timeMillis: Long): Location {
        return Location("gps").apply {
            latitude = lat
            longitude = lng
            time = timeMillis
            accuracy = 5.0f
        }
    }

    // FIX 5 - Requirement 1: Fill or block GPS channel and verify callbacks are not silently lost
    @Test
    fun fillGpsQueue_appliesBackpressure_andNeverSilentlyDiscardsFixes() = runBlocking {
        val processedLocations = Collections.synchronizedList(mutableListOf<Long>())
        val queueCapacity = 2 // Small buffer to intentionally trigger backpressure
        val totalLocations = 20

        val queue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            capacity = queueCapacity
        ) { location ->
            // Simulate slight processing latency
            delay(10)
            processedLocations.add(location.time)
        }

        // Rapidly push locations exceeding capacity
        for (i in 1..totalLocations) {
            val loc = createLocation(37.3382, -121.8863, i * 1000L)
            queue.enqueue(loc)
        }

        // Drain queue gracefully
        queue.drainAndClose()

        assertEquals(
            "Every GPS callback must be preserved and processed without silent loss",
            totalLocations,
            processedLocations.size
        )
        // Verify points were processed in order
        for (i in 0 until totalLocations) {
            assertEquals((i + 1) * 1000L, processedLocations[i])
        }
    }

    // FIX 5 - Requirement 2: Verify normal stop drains queued GPS updates before finalization
    @Test
    fun normalStop_drainsQueuedGpsLocations_beforeCompleting() = runBlocking {
        val processedCount = AtomicInteger(0)
        val itemCount = 15

        val queue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            capacity = 4
        ) {
            delay(15)
            processedCount.incrementAndGet()
        }

        for (i in 1..itemCount) {
            queue.enqueue(createLocation(37.0 + i * 0.001, -122.0, i * 1000L))
        }

        // drainAndClose must suspend until the consumer finishes all enqueued points
        queue.drainAndClose()

        assertEquals(
            "drainAndClose must ensure 100% of queued fixes are consumed prior to returning",
            itemCount,
            processedCount.get()
        )
        assertTrue("Queue must be closed after drainAndClose", queue.isClosed())
    }
}
