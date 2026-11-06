package com.businesstracker.mileage.service

import android.location.Location
import android.util.Log
import com.businesstracker.mileage.data.local.GpsInboxDao
import com.businesstracker.mileage.data.local.entity.GpsInboxEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Durable Room-backed GPS location queue.
 * - Stores all incoming GPS fixes in SQLite `gps_inbox` indexed by (tripId, timestamp).
 * - Enforces bounded memory by reading in limited batches (batchFetchLimit).
 * - Guarantees strict global chronological ordering via SQL `ORDER BY timestamp ASC, id ASC`.
 * - Deletes a GPS fix ONLY after onLocationProcessed() completes successfully.
 * - Retains unprocessed fixes in SQLite on process termination, service restart, or persistence failure.
 * - Prevents premature finalization by surfacing any processing errors to drainAndClose().
 */
class GpsLocationQueue(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val gpsInboxDao: GpsInboxDao,
    private val tripIdProvider: () -> String,
    private val batchFetchLimit: Int = 100,
    private val onLocationProcessed: suspend (Location) -> Unit
) {
    private val signalChannel = Channel<Unit>(Channel.CONFLATED)
    private val isStopping = AtomicBoolean(false)
    private val activeProducers = AtomicInteger(0)
    private var consumerJob: Job? = null

    @Volatile
    private var processingError: Throwable? = null

    init {
        startConsumer()
    }

    private fun startConsumer() {
        consumerJob = scope.launch(dispatcher) {
            while (isActive) {
                val currentTripId = tripIdProvider()
                val pendingFixes = if (currentTripId.isNotEmpty()) {
                    try {
                        gpsInboxDao.getPendingFixes(currentTripId, limit = batchFetchLimit)
                    } catch (e: Exception) {
                        Log.e("GpsLocationQueue", "Error querying pending fixes: ${e.message}", e)
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                if (pendingFixes.isNotEmpty()) {
                    for (fix in pendingFixes) {
                        if (!isActive) break
                        val location = Location("gps").apply {
                            latitude = fix.latitude
                            longitude = fix.longitude
                            altitude = fix.altitude
                            accuracy = fix.accuracy
                            speed = fix.speed
                            time = fix.timestamp
                        }

                        try {
                            onLocationProcessed(location)
                            // Remove from inbox ONLY after successful processing!
                            gpsInboxDao.deleteFixById(fix.id)
                        } catch (e: Throwable) {
                            processingError = e
                            Log.e("GpsLocationQueue", "Waypoint processing failed for fix ${fix.id}: ${e.message}", e)
                            // Halt consumer loop on persistence failure to protect unprocessed fixes!
                            break
                        }
                    }

                    if (processingError != null) {
                        break
                    }
                } else {
                    val currentId = tripIdProvider()
                    val count = if (currentId.isNotEmpty()) {
                        try { gpsInboxDao.getPendingCount(currentId) } catch (_: Exception) { 0 }
                    } else 0

                    if (isStopping.get() && count == 0 && activeProducers.get() == 0) {
                        break
                    }
                    signalChannel.receiveCatching()
                }
            }
        }
    }

    /**
     * Enqueues a batch of incoming GPS fixes durably into Room SQLite inbox.
     */
    fun enqueueBatch(locations: List<Location>) {
        if (isStopping.get() || locations.isEmpty()) return
        val currentTripId = tripIdProvider()
        if (currentTripId.isEmpty()) return

        val entities = locations.map { loc ->
            GpsInboxEntity(
                tripId = currentTripId,
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitude = loc.altitude,
                accuracy = loc.accuracy,
                speed = loc.speed,
                timestamp = loc.time
            )
        }

        activeProducers.incrementAndGet()
        scope.launch(dispatcher) {
            try {
                gpsInboxDao.insertFixes(entities)
                signalChannel.trySend(Unit)
            } catch (e: Exception) {
                Log.e("GpsLocationQueue", "Critical error inserting fixes into inbox: ${e.message}", e)
                processingError = e
            } finally {
                activeProducers.decrementAndGet()
                signalChannel.trySend(Unit)
            }
        }
    }

    suspend fun enqueueBatchSync(locations: List<Location>) {
        if (isStopping.get() || locations.isEmpty()) return
        val currentTripId = tripIdProvider()
        if (currentTripId.isEmpty()) return

        val entities = locations.map { loc ->
            GpsInboxEntity(
                tripId = currentTripId,
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitude = loc.altitude,
                accuracy = loc.accuracy,
                speed = loc.speed,
                timestamp = loc.time
            )
        }
        gpsInboxDao.insertFixes(entities)
        signalChannel.trySend(Unit)
    }

    fun enqueue(location: Location) {
        enqueueBatch(listOf(location))
    }

    /**
     * Drains all accepted locations from SQLite inbox and waits for consumer to complete.
     * Throws IllegalStateException if waypoint processing failed, preventing premature finalization.
     */
    suspend fun drainAndClose() {
        if (!isStopping.compareAndSet(false, true)) {
            consumerJob?.join()
            processingError?.let { err ->
                throw IllegalStateException("Waypoint processing failed during tracking: ${err.message}", err)
            }
            return
        }

        // Wait for all accepted producer batches in flight to finish writing to SQLite
        while (activeProducers.get() > 0) {
            delay(10L)
        }

        // Wake consumer to drain all remaining items from SQLite inbox
        signalChannel.trySend(Unit)

        try {
            consumerJob?.join()
        } catch (e: Exception) {
            Log.w("GpsLocationQueue", "Interrupted awaiting consumer drain: ${e.message}")
        }

        processingError?.let { err ->
            throw IllegalStateException("Waypoint processing failed during tracking: ${err.message}", err)
        }
    }

    /**
     * Stops the in-memory consumer without deleting any pending fixes from SQLite inbox.
     * Used on normal service destruction or system kill so work can be recovered later.
     */
    fun stopConsumer() {
        isStopping.set(true)
        consumerJob?.cancel()
    }

    /**
     * Unrecoverable cancellation (e.g. startup failure when permission is missing).
     */
    fun cancelEmergency() {
        isStopping.set(true)
        consumerJob?.cancel()
        val currentTripId = tripIdProvider()
        if (currentTripId.isNotEmpty()) {
            scope.launch(dispatcher) {
                try {
                    gpsInboxDao.deleteFixesForTrip(currentTripId)
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun getPendingCount(): Int {
        val currentTripId = tripIdProvider()
        return if (currentTripId.isNotEmpty()) gpsInboxDao.getPendingCount(currentTripId) else 0
    }

    fun isClosed(): Boolean = isStopping.get()
}
