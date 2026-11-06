package com.businesstracker.mileage.service

import android.location.Location
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Serialized GPS location queue with non-loss backpressure and graceful draining.
 * Ensures GPS points are processed sequentially without silent drops or race conditions.
 */
class GpsLocationQueue(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    capacity: Int = 64,
    private val onLocationProcessed: suspend (Location) -> Unit
) {
    private val channel = Channel<Location>(capacity = capacity)
    private val isStopping = AtomicBoolean(false)
    private var consumerJob: Job? = null

    init {
        startConsumer()
    }

    private fun startConsumer() {
        consumerJob = scope.launch(dispatcher) {
            for (location in channel) {
                if (!isActive) break
                try {
                    onLocationProcessed(location)
                } catch (e: Exception) {
                    Log.e("GpsLocationQueue", "Error processing serialized location: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Enqueues an incoming GPS location fix.
     * If the channel buffer is full, applies coroutine-based backpressure so callbacks are never silently lost.
     */
    fun enqueue(location: Location) {
        if (isStopping.get()) return
        val result = channel.trySend(location)
        if (result.isFailure && !channel.isClosedForSend) {
            scope.launch(dispatcher) {
                try {
                    if (!channel.isClosedForSend) {
                        channel.send(location)
                    }
                } catch (e: Exception) {
                    Log.e("GpsLocationQueue", "Failed to enqueue location fix with backpressure: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Closes the channel and drains all queued locations before completing.
     * Prevents discarding queued GPS updates on normal trip stop.
     */
    suspend fun drainAndClose() {
        if (!isStopping.compareAndSet(false, true)) return
        try {
            channel.close()
        } catch (e: Exception) {
            Log.e("GpsLocationQueue", "Error closing location channel: ${e.message}", e)
        }
        try {
            consumerJob?.join()
        } catch (e: Exception) {
            Log.w("GpsLocationQueue", "Error awaiting consumer drain: ${e.message}")
        }
    }

    /**
     * Emergency cancellation used during startup failures or service destruction.
     */
    fun cancelEmergency() {
        isStopping.set(true)
        try {
            channel.cancel()
        } catch (_: Exception) {}
        consumerJob?.cancel()
    }

    fun isClosed(): Boolean = channel.isClosedForSend
}
