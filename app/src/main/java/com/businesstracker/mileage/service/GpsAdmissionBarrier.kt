package com.businesstracker.mileage.service

/**
 * Race-safe admission barrier and sequential ordering coordinator for GPS callback batches.
 * Guarantees that:
 * 1. An accepted callback batch is assigned a strictly ascending sequence number.
 * 2. When closeAndWait() is called, no new callback batches can pass the barrier.
 * 3. closeAndWait() blocks until all in-flight callback batches complete their Room insertion.
 * 4. Eliminates race conditions between late-arriving GPS callbacks and service shutdown.
 */
class GpsAdmissionBarrier(initialSequence: Long = 0L) {
    private val lock = Any()
    private var isClosed = false
    private var inFlightCount = 0
    private var sequenceGenerator = initialSequence

    /**
     * Initializes sequence number from durable storage if greater than current generator.
     */
    fun initializeSequence(seq: Long) {
        synchronized(lock) {
            if (seq > sequenceGenerator) {
                sequenceGenerator = seq
            }
        }
    }

    /**
     * Attempts to acquire a sequence number and increment the in-flight counter.
     * Returns null if the barrier is closed.
     */
    fun acquireSequenceOrNull(): Long? {
        synchronized(lock) {
            if (isClosed) return null
            inFlightCount++
            return ++sequenceGenerator
        }
    }

    /**
     * Decrements the in-flight counter and notifies waiting drain threads.
     */
    fun releaseSequence() {
        synchronized(lock) {
            if (inFlightCount > 0) {
                inFlightCount--
                (lock as Object).notifyAll()
            }
        }
    }

    /**
     * Attempts to admit a batch for ingestion.
     * If the barrier is closed, returns null immediately.
     * Otherwise, increments in-flight count, assigns a monotonic sequence number,
     * executes the ingestion block, and decrements in-flight count upon completion.
     */
    fun <T> tryAcquire(block: (sequence: Long) -> T): T? {
        val seq = acquireSequenceOrNull() ?: return null
        try {
            return block(seq)
        } finally {
            releaseSequence()
        }
    }

    /**
     * Closes the admission barrier to new batches and blocks until all in-flight batches complete.
     * If interrupted, sets an internal flag and CONTINUES waiting until inFlightCount == 0.
     * Restores interrupted status only after all in-flight batches have completed.
     */
    fun closeAndWait() {
        var interrupted = false
        synchronized(lock) {
            isClosed = true
            while (inFlightCount > 0) {
                try {
                    (lock as Object).wait()
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt()
        }
    }

    fun isClosed(): Boolean = synchronized(lock) { isClosed }

    fun inFlight(): Int = synchronized(lock) { inFlightCount }

    fun currentSequence(): Long = synchronized(lock) { sequenceGenerator }
}
