package com.businesstracker.mileage.service

import android.location.Location
import android.util.Log
import com.businesstracker.mileage.data.local.GpsInboxDao
import com.businesstracker.mileage.data.local.entity.GpsInboxEntity
import com.businesstracker.mileage.domain.usecase.DrainPendingGpsFixesUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Durable Room-backed GPS location queue.
 * - Race-safe admission barrier (GpsAdmissionBarrier) prevents lost callback batches.
 * - Assigns monotonic sequence numbers and preserves Android monotonic acquisition time (elapsedRealtimeNanos).
 * - Enforces bounded memory by reading in limited batches (batchFetchLimit).
 * - Guarantees strict global chronological ordering via SQL ORDER BY sequenceNumber ASC, elapsedRealtimeNanos ASC, timestamp ASC, id ASC.
 * - Deletes a GPS fix ONLY after transactional processing completes successfully.
 * - Retains unprocessed fixes in SQLite on process termination, service restart, or persistence failure.
 * - Never converts database exceptions into empty lists or zero counts.
 */
class GpsLocationQueue(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val gpsInboxDao: GpsInboxDao,
    private val tripIdProvider: () -> String,
    private val batchFetchLimit: Int = 100,
    private val drainPendingGpsFixesUseCase: DrainPendingGpsFixesUseCase? = null,
    private val onLocationProcessed: (suspend (Location) -> Unit)? = null
) {
    private val signalChannel = Channel<Unit>(Channel.CONFLATED)
    private val isStopping = AtomicBoolean(false)
    val admissionBarrier = GpsAdmissionBarrier()
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
                if (currentTripId.isEmpty()) {
                    signalChannel.receiveCatching()
                    continue
                }

                if (drainPendingGpsFixesUseCase != null) {
                    try {
                        val processed = drainPendingGpsFixesUseCase.drain(
                            tripId = currentTripId,
                            batchLimit = batchFetchLimit
                        )
                        if (isStopping.get()) {
                            val count = gpsInboxDao.getPendingCount(currentTripId)
                            if (count == 0 && admissionBarrier.inFlight() == 0) {
                                break
                            }
                        }
                        if (processed == 0) {
                            signalChannel.receiveCatching()
                        }
                    } catch (e: Throwable) {
                        processingError = e
                        Log.e("GpsLocationQueue", "Error in drainPendingGpsFixesUseCase consumer loop: ${e.message}", e)
                        break
                    }
                } else {
                    // Direct processing path (used when useCase is not provided, e.g. in standalone unit tests)
                    val pendingFixes = try {
                        gpsInboxDao.getPendingFixes(currentTripId, limit = batchFetchLimit)
                    } catch (e: Throwable) {
                        processingError = e
                        Log.e("GpsLocationQueue", "Error querying pending fixes: ${e.message}", e)
                        break
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
                                elapsedRealtimeNanos = fix.elapsedRealtimeNanos
                            }

                            try {
                                onLocationProcessed?.invoke(location)
                                gpsInboxDao.deleteFixById(fix.id)
                            } catch (e: Throwable) {
                                processingError = e
                                Log.e("GpsLocationQueue", "Waypoint processing failed for fix ${fix.id}: ${e.message}", e)
                                break
                            }
                        }

                        if (processingError != null) {
                            break
                        }
                    } else {
                        val count = try {
                            gpsInboxDao.getPendingCount(currentTripId)
                        } catch (e: Throwable) {
                            processingError = e
                            Log.e("GpsLocationQueue", "Error querying pending count: ${e.message}", e)
                            break
                        }

                        if (isStopping.get() && count == 0 && admissionBarrier.inFlight() == 0) {
                            break
                        }
                        signalChannel.receiveCatching()
                    }
                }
            }
        }
    }

    /**
     * Enqueues a batch of incoming GPS fixes durably into Room SQLite inbox.
     * Uses GpsAdmissionBarrier for race-safe admission.
     */
    fun enqueueBatch(locations: List<Location>) {
        if (locations.isEmpty()) return
        val currentTripId = tripIdProvider()
        if (currentTripId.isEmpty()) return

        val sequence = admissionBarrier.acquireSequenceOrNull() ?: return
        // Monotonic sorting within batch: Android monotonic acquisition time (elapsedRealtimeNanos)
        val sorted = locations.sortedWith(
            compareBy<Location> { loc ->
                if (loc.elapsedRealtimeNanos != 0L) loc.elapsedRealtimeNanos else loc.time * 1_000_000L
            }.thenBy { it.time }
        )

        val entities = sorted.map { loc ->
            val monotonicTime = if (loc.elapsedRealtimeNanos != 0L) {
                loc.elapsedRealtimeNanos
            } else {
                loc.time * 1_000_000L
            }
            GpsInboxEntity(
                tripId = currentTripId,
                sequenceNumber = sequence,
                elapsedRealtimeNanos = monotonicTime,
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitude = loc.altitude,
                accuracy = loc.accuracy,
                speed = loc.speed,
                timestamp = loc.time
            )
        }

        scope.launch(dispatcher) {
            try {
                gpsInboxDao.insertFixes(entities)
                signalChannel.trySend(Unit)
            } catch (e: Throwable) {
                Log.e("GpsLocationQueue", "Critical error inserting fixes into inbox: ${e.message}", e)
                processingError = e
            } finally {
                admissionBarrier.releaseSequence()
            }
        }
    }

    suspend fun enqueueBatchSync(locations: List<Location>) {
        if (locations.isEmpty()) return
        val currentTripId = tripIdProvider()
        if (currentTripId.isEmpty()) return

        val sequence = admissionBarrier.acquireSequenceOrNull() ?: return
        try {
            val sorted = locations.sortedWith(
                compareBy<Location> { loc ->
                    if (loc.elapsedRealtimeNanos != 0L) loc.elapsedRealtimeNanos else loc.time * 1_000_000L
                }.thenBy { it.time }
            )

            val entities = sorted.map { loc ->
                val monotonicTime = if (loc.elapsedRealtimeNanos != 0L) {
                    loc.elapsedRealtimeNanos
                } else {
                    loc.time * 1_000_000L
                }
                GpsInboxEntity(
                    tripId = currentTripId,
                    sequenceNumber = sequence,
                    elapsedRealtimeNanos = monotonicTime,
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
        } finally {
            admissionBarrier.releaseSequence()
        }
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

        // 1. Close admission barrier and await completion of all in-flight Room insertions
        admissionBarrier.closeAndWait()

        // 2. Wake consumer to drain
        signalChannel.trySend(Unit)

        try {
            consumerJob?.join()
        } catch (e: Exception) {
            Log.w("GpsLocationQueue", "Interrupted awaiting consumer drain: ${e.message}")
        }

        processingError?.let { err ->
            throw IllegalStateException("Waypoint processing failed during tracking: ${err.message}", err)
        }

        // 3. Verify exact pending count is 0
        val currentTripId = tripIdProvider()
        if (currentTripId.isNotEmpty()) {
            val pendingCount = gpsInboxDao.getPendingCount(currentTripId)
            if (pendingCount > 0) {
                throw IllegalStateException("Queue drain verification failed: $pendingCount fixes remain pending for trip $currentTripId")
            }
        }
    }

    /**
     * Stops consumer without deleting pending fixes from SQLite inbox.
     * Preserves pending data across process death or service destruction.
     */
    fun stopConsumer() {
        isStopping.set(true)
        admissionBarrier.closeAndWait()
        consumerJob?.cancel()
    }

    suspend fun getPendingCount(): Int {
        val currentTripId = tripIdProvider()
        return if (currentTripId.isNotEmpty()) gpsInboxDao.getPendingCount(currentTripId) else 0
    }

    fun isClosed(): Boolean = isStopping.get()
}
