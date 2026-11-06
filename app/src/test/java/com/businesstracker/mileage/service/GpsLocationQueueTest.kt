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
import org.junit.Assert.fail
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

    // FIX 1 & Regression Test 1: GPS queue saturation without dropped or reordered locations
    @Test
    fun gpsQueueSaturation_preservesAllLocationsInExactTimestampOrder() = runBlocking {
        val processedTimestamps = Collections.synchronizedList(mutableListOf<Long>())
        val totalLocations = 80

        val queue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            maxCapacity = 200
        ) { location ->
            delay(5) // Introduce processing latency to saturate pipeline
            processedTimestamps.add(location.time)
        }

        // Deliver fixes in rapid batches
        val batch1 = (1..40).map { createLocation(37.3382, -121.8863, it * 1000L) }
        val batch2 = (41..80).map { createLocation(37.3382, -121.8863, it * 1000L) }

        queue.enqueueBatch(batch1)
        queue.enqueueBatch(batch2)

        queue.drainAndClose()

        assertEquals("All enqueued GPS fixes must be processed without loss", totalLocations, processedTimestamps.size)

        // Verify strictly ascending timestamp order
        for (i in 0 until totalLocations) {
            assertEquals((i + 1) * 1000L, processedTimestamps[i])
        }
    }

    // FIX 1 & Regression Test 2: Queue drain waiting for all pending producer work
    @Test
    fun queueDrain_waitsForAllPendingProducerWork_beforeReturning() = runBlocking {
        val processedCount = AtomicInteger(0)
        val itemCount = 25

        val queue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            maxCapacity = 100
        ) {
            delay(10)
            processedCount.incrementAndGet()
        }

        val locations = (1..itemCount).map { createLocation(37.0 + it * 0.001, -122.0, it * 1000L) }
        queue.enqueueBatch(locations)

        // drainAndClose must suspend until all items are fully consumed
        queue.drainAndClose()

        assertEquals("drainAndClose must ensure 100% of pending items are consumed prior to returning", itemCount, processedCount.get())
        assertTrue("Queue must be closed after drainAndClose", queue.isClosed())
    }

    // FIX 1 & Regression Test 3: GPS persistence failure prevents premature finalization
    @Test
    fun gpsPersistenceFailure_haltsProcessingAndSurfacesErrorOnDrain() = runBlocking {
        val queue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            maxCapacity = 100
        ) { location ->
            if (location.time >= 5000L) {
                throw java.sql.SQLException("Simulated SQLite disk/constraint failure")
            }
        }

        val locations = (1..10).map { createLocation(37.0, -122.0, it * 1000L) }
        queue.enqueueBatch(locations)

        try {
            queue.drainAndClose()
            fail("drainAndClose must throw when waypoint persistence fails")
        } catch (e: IllegalStateException) {
            assertTrue("Exception message must detail persistence failure", e.message?.contains("Waypoint processing failed") == true)
        }
    }
}
