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

    // Regression Test 1: More than 1,000 incoming GPS fixes without data loss and bounded memory
    @Test
    fun moreThan1000GpsFixes_processedWithoutDataLoss_withBoundedMemory() = runBlocking {
        val processedTimestamps = Collections.synchronizedList(mutableListOf<Long>())
        val totalLocations = 1200
        val boundedCapacity = 150 // Strict memory limit

        val queue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            maxCapacity = boundedCapacity
        ) { location ->
            processedTimestamps.add(location.time)
        }

        // Enqueue 1,200 locations in chunks
        for (chunkStart in 1..totalLocations step 100) {
            val chunk = (chunkStart until chunkStart + 100).map { i ->
                createLocation(37.3382, -121.8863, i * 1000L)
            }
            queue.enqueueBatch(chunk)
        }

        queue.drainAndClose()

        assertEquals("Exactly 1,200 GPS fixes must be consumed without any loss", totalLocations, processedTimestamps.size)
        assertEquals("Spillover count must be 0 after draining", 0, queue.getSpilloverCount())

        // Verify order across entire 1,200 locations
        for (i in 0 until totalLocations) {
            assertEquals((i + 1) * 1000L, processedTimestamps[i])
        }
    }

    // Regression Test 2: Multiple batches with overlapping or out-of-order timestamps
    @Test
    fun multipleBatches_withOverlappingTimestamps_processedInStrictTimestampOrder() = runBlocking {
        val processedTimestamps = Collections.synchronizedList(mutableListOf<Long>())

        val queue = GpsLocationQueue(
            scope = testScope,
            dispatcher = Dispatchers.Default,
            maxCapacity = 50
        ) { location ->
            delay(5)
            processedTimestamps.add(location.time)
        }

        // Three overlapping batches arriving concurrently / out-of-order
        val batch1 = listOf(1000L, 4000L, 7000L).map { createLocation(37.0, -122.0, it) }
        val batch2 = listOf(2000L, 5000L, 8000L).map { createLocation(37.0, -122.0, it) }
        val batch3 = listOf(500L, 3000L, 6000L).map { createLocation(37.0, -122.0, it) }

        queue.enqueueBatch(batch1)
        queue.enqueueBatch(batch2)
        queue.enqueueBatch(batch3)

        queue.drainAndClose()

        val expected = listOf(500L, 1000L, 2000L, 3000L, 4000L, 5000L, 6000L, 7000L, 8000L)
        assertEquals("Consumer must receive points in strictly ascending timestamp order across all batches", expected, processedTimestamps)
    }

    // Regression Test: GPS persistence failure prevents premature finalization
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
