package com.businesstracker.mileage.service

/**
 * Race-safe admission barrier and sequential ordering coordinator for GPS callback batches.
 * Guarantees that:
 * 1. An accepted callback batch is assigned a strictly ascending sequence number.
 * 2. When closeAndWait() is called, no new callback batches can pass the barrier.
 * 3. closeAndWait() blocks until all in-flight callback batches complete their Room insertion.
 * 4. Eliminates race conditions between late-arriving GPS callbacks and service shutdown.
 */
class GpsAdmissionBarrier {
    private val lock = Any()
    private var isClosed = false
    private var inFlightCount = 0
    private var sequenceGenerator = 0L

    /**
     * Attempts to admit a batch for ingestion.
     * If the barrier is closed, returns null immediately.
     * Otherwise, increments in-flight count, assigns a monotonic sequence number,
     * executes the ingestion block, and decrements in-flight count upon completion.
     */
    fun <T> tryAcquire(block: (sequence: Long) -> T): T? {
        val seq: Long
        synchronized(lock) {
            if (isClosed) return null
            inFlightCount++
            seq = ++sequenceGenerator
        }
        try {
            return block(seq)
        } finally {
            synchronized(lock) {
                inFlightCount--
                (lock as Object).notifyAll()
            }
        }
    }

    /**
     * Closes the admission barrier to new batches and blocks until all in-flight batches complete.
     */
    fun closeAndWait() {
        synchronized(lock) {
            isClosed = true
            while (inFlightCount > 0) {
                try {
                    (lock as Object).wait()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
    }

    fun isClosed(): Boolean = synchronized(lock) { isClosed }

    fun inFlight(): Int = synchronized(lock) { inFlightCount }

    fun currentSequence(): Long = synchronized(lock) { sequenceGenerator }
}
