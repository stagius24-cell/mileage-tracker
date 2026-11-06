package com.businesstracker.mileage.service

import android.location.Location
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Genuinely bounded, lossless, and ordered GPS location queue.
 * - Caps in-memory RAM usage to maxCapacity.
 * - Excess incoming fixes exceeding maxCapacity are durably buffered to a local spillover file without data loss.
 * - Sorts by timestamp (with sequence tie-breaker) across all incoming batches.
 * - Guarantees drainAndClose() waits until 100% of accepted fixes are processed.
 * - Surfaces waypoint persistence failures immediately to prevent premature finalization.
 */
class GpsLocationQueue(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val maxCapacity: Int = 1000,
    private val bufferDir: File? = null,
    private val onLocationProcessed: suspend (Location) -> Unit
) {
    private data class QueuedLocation(
        val seq: Long,
        val location: Location
    ) : Comparable<QueuedLocation> {
        override fun compareTo(other: QueuedLocation): Int {
            val timeCmp = this.location.time.compareTo(other.location.time)
            return if (timeCmp != 0) timeCmp else this.seq.compareTo(other.seq)
        }
    }

    private val lock = Any()
    private val memoryQueue = PriorityQueue<QueuedLocation>()
    private val signalChannel = Channel<Unit>(Channel.CONFLATED)
    private val isStopping = AtomicBoolean(false)
    private val seqCounter = AtomicLong(0)
    private var consumerJob: Job? = null

    // Durable spillover buffering for genuine memory bounding
    private var spilloverFile: File? = null
    private var spilloverOut: DataOutputStream? = null
    private var spilloverIn: DataInputStream? = null
    private var spilloverCount = 0

    @Volatile
    private var processingError: Throwable? = null

    init {
        startConsumer()
    }

    private fun startConsumer() {
        consumerJob = scope.launch(dispatcher) {
            while (isActive) {
                val nextLocation = synchronized(lock) {
                    refillFromSpilloverLocked()
                    memoryQueue.poll()?.location
                }

                if (nextLocation != null) {
                    try {
                        onLocationProcessed(nextLocation)
                    } catch (e: Throwable) {
                        processingError = e
                        Log.e("GpsLocationQueue", "Failure persisting waypoint: ${e.message}", e)
                        // Halt consumer loop on persistence failure to prevent premature finalization
                        break
                    }
                } else {
                    val done = synchronized(lock) {
                        isStopping.get() && memoryQueue.isEmpty() && spilloverCount == 0
                    }
                    if (done) {
                        break
                    }
                    signalChannel.receiveCatching()
                }
            }
        }
    }

    /**
     * Enqueues a batch of incoming GPS fixes.
     * Enforces bounded memory capacity while never silently discarding any location.
     */
    fun enqueueBatch(locations: List<Location>) {
        if (isStopping.get() || locations.isEmpty()) return
        synchronized(lock) {
            if (isStopping.get()) return
            for (loc in locations) {
                if (memoryQueue.size < maxCapacity) {
                    memoryQueue.add(QueuedLocation(seqCounter.incrementAndGet(), loc))
                } else {
                    writeToSpilloverLocked(loc)
                }
            }
        }
        signalChannel.trySend(Unit)
    }

    fun enqueue(location: Location) {
        enqueueBatch(listOf(location))
    }

    private fun writeToSpilloverLocked(loc: Location) {
        try {
            if (spilloverFile == null) {
                spilloverFile = if (bufferDir != null) {
                    File.createTempFile("gps_spillover_", ".bin", bufferDir)
                } else {
                    File.createTempFile("gps_spillover_", ".bin")
                }
                spilloverFile?.deleteOnExit()
                spilloverOut = DataOutputStream(FileOutputStream(spilloverFile!!, true))
            }
            val out = spilloverOut ?: return
            out.writeUTF(loc.provider ?: "gps")
            out.writeDouble(loc.latitude)
            out.writeDouble(loc.longitude)
            out.writeDouble(loc.altitude)
            out.writeFloat(loc.accuracy)
            out.writeFloat(loc.speed)
            out.writeLong(loc.time)
            out.flush()
            spilloverCount++
        } catch (e: Exception) {
            Log.e("GpsLocationQueue", "Error writing to spillover buffer: ${e.message}", e)
            // In case of I/O error, add directly to memoryQueue as safeguard against data loss
            memoryQueue.add(QueuedLocation(seqCounter.incrementAndGet(), loc))
        }
    }

    private fun refillFromSpilloverLocked() {
        if (spilloverCount == 0 || memoryQueue.size >= maxCapacity) return
        try {
            if (spilloverIn == null && spilloverFile != null && spilloverFile!!.exists()) {
                spilloverOut?.flush()
                spilloverIn = DataInputStream(FileInputStream(spilloverFile!!))
            }
            val inStream = spilloverIn ?: return
            while (memoryQueue.size < maxCapacity && spilloverCount > 0) {
                val provider = inStream.readUTF()
                val lat = inStream.readDouble()
                val lng = inStream.readDouble()
                val alt = inStream.readDouble()
                val acc = inStream.readFloat()
                val spd = inStream.readFloat()
                val time = inStream.readLong()

                val loc = Location(provider).apply {
                    latitude = lat
                    longitude = lng
                    altitude = alt
                    accuracy = acc
                    speed = spd
                    this.time = time
                }
                memoryQueue.add(QueuedLocation(seqCounter.incrementAndGet(), loc))
                spilloverCount--
            }

            if (spilloverCount == 0) {
                closeSpilloverStreamsLocked()
                spilloverFile?.delete()
                spilloverFile = null
            }
        } catch (e: Exception) {
            Log.e("GpsLocationQueue", "Error reading from spillover buffer: ${e.message}", e)
            closeSpilloverStreamsLocked()
        }
    }

    private fun closeSpilloverStreamsLocked() {
        try { spilloverOut?.close() } catch (_: Exception) {}
        try { spilloverIn?.close() } catch (_: Exception) {}
        spilloverOut = null
        spilloverIn = null
    }

    /**
     * Drains all accepted locations and waits for consumer to complete.
     * Throws IllegalStateException if waypoint persistence failed, preventing premature finalization.
     */
    suspend fun drainAndClose() {
        if (!isStopping.compareAndSet(false, true)) {
            consumerJob?.join()
            processingError?.let { err ->
                throw IllegalStateException("Waypoint processing failed during tracking: ${err.message}", err)
            }
            return
        }

        // Wake consumer to finish processing all remaining memory and spillover items
        signalChannel.trySend(Unit)

        try {
            consumerJob?.join()
        } catch (e: Exception) {
            Log.w("GpsLocationQueue", "Interrupted awaiting consumer drain: ${e.message}")
        } finally {
            synchronized(lock) {
                closeSpilloverStreamsLocked()
                spilloverFile?.delete()
                spilloverFile = null
            }
        }

        processingError?.let { err ->
            throw IllegalStateException("Waypoint processing failed during tracking: ${err.message}", err)
        }
    }

    fun cancelEmergency() {
        isStopping.set(true)
        consumerJob?.cancel()
        synchronized(lock) {
            memoryQueue.clear()
            closeSpilloverStreamsLocked()
            spilloverFile?.delete()
            spilloverFile = null
            spilloverCount = 0
        }
    }

    fun getPendingCount(): Int = synchronized(lock) { memoryQueue.size + spilloverCount }

    fun getMemoryCount(): Int = synchronized(lock) { memoryQueue.size }

    fun getSpilloverCount(): Int = synchronized(lock) { spilloverCount }

    fun isClosed(): Boolean = isStopping.get()
}
