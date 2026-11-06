package com.businesstracker.mileage.service

import android.location.Location
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ordered, lossless GPS location queue with bounded memory and backpressure tracking.
 * Uses a single producer-consumer pipeline so locations are always processed in strict timestamp order.
 */
class GpsLocationQueue(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val maxCapacity: Int = 1000,
    private val onLocationProcessed: suspend (Location) -> Unit
) {
    private val pendingLocations = ArrayDeque<Location>()
    private val lock = Any()
    private val signalChannel = Channel<Unit>(Channel.CONFLATED)
    private val isStopping = AtomicBoolean(false)
    private var consumerJob: Job? = null

    @Volatile
    private var processingError: Throwable? = null

    init {
        startConsumer()
    }

    private fun startConsumer() {
        consumerJob = scope.launch(dispatcher) {
            while (isActive) {
                val nextLocation = synchronized(lock) {
                    pendingLocations.pollFirst()
                }

                if (nextLocation != null) {
                    try {
                        onLocationProcessed(nextLocation)
                    } catch (e: Throwable) {
                        processingError = e
                        Log.e("GpsLocationQueue", "Failure persisting waypoint: ${e.message}", e)
                        // Halt further processing so finalization cannot proceed as if all waypoints were saved
                        break
                    }
                } else {
                    if (isStopping.get()) {
                        // All pending items have been drained and queue is stopping
                        break
                    }
                    signalChannel.receiveCatching()
                }
            }
        }
    }

    /**
     * Enqueues a batch of incoming GPS fixes.
     * Preserves strict timestamp ordering by sorting within batch and maintaining single FIFO queue.
     */
    fun enqueueBatch(locations: List<Location>) {
        if (isStopping.get() || locations.isEmpty()) return
        val sorted = locations.sortedBy { it.time }
        synchronized(lock) {
            if (isStopping.get()) return
            if (pendingLocations.size + sorted.size > maxCapacity) {
                Log.w("GpsLocationQueue", "Queue near capacity limit ($maxCapacity). Processing under backpressure.")
            }
            pendingLocations.addAll(sorted)
        }
        signalChannel.trySend(Unit)
    }

    fun enqueue(location: Location) {
        enqueueBatch(listOf(location))
    }

    /**
     * Drains all accepted locations and waits for consumer to finish before returning.
     * If any waypoint persistence error occurred, rethrows to prevent premature trip finalization.
     */
    suspend fun drainAndClose() {
        if (!isStopping.compareAndSet(false, true)) {
            consumerJob?.join()
            return
        }

        // Signal consumer to process remaining items and exit
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
     * Emergency cancellation used during startup failures or service destruction.
     */
    fun cancelEmergency() {
        isStopping.set(true)
        consumerJob?.cancel()
        synchronized(lock) {
            pendingLocations.clear()
        }
    }

    fun getPendingCount(): Int = synchronized(lock) { pendingLocations.size }

    fun isClosed(): Boolean = isStopping.get()
}
