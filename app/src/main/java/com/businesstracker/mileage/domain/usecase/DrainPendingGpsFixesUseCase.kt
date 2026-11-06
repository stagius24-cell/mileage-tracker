package com.businesstracker.mileage.domain.usecase

import android.util.Log
import com.businesstracker.mileage.data.local.GpsInboxDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared production recovery and draining component used by both
 * LocationTrackingService and TripDebounceWorker.
 *
 * Guarantees:
 * - Drains pending GPS fixes using the atomic GpsInboxProcessor.
 * - Database and processing errors are NEVER swallowed or converted to empty lists.
 * - Confirms exact trip pending count is zero before returning.
 * - Throws on any database failure, halting premature finalization.
 */
@Singleton
class DrainPendingGpsFixesUseCase @Inject constructor(
    private val gpsInboxDao: GpsInboxDao,
    private val gpsInboxProcessor: GpsInboxProcessor
) {
    suspend fun drain(
        tripId: String,
        batchLimit: Int = 100,
        onFixProcessed: ((GpsInboxProcessor.ProcessResult) -> Unit)? = null
    ): Int {
        if (tripId.isEmpty()) return 0
        var totalProcessed = 0

        while (true) {
            // Never catch and swallow database exceptions! Propagate so callers halt finalization.
            val pendingFixes = gpsInboxDao.getPendingFixes(tripId, batchLimit)
            if (pendingFixes.isEmpty()) {
                val remainingCount = gpsInboxDao.getPendingCount(tripId)
                if (remainingCount > 0) {
                    throw IllegalStateException("Queue drain verification failed: $remainingCount fixes remain pending in Room inbox for trip $tripId")
                }
                break
            }

            for (fix in pendingFixes) {
                val result = gpsInboxProcessor.processFixTransaction(fix.id)
                totalProcessed++
                onFixProcessed?.invoke(result)
            }
        }

        val finalCheck = gpsInboxDao.getPendingCount(tripId)
        if (finalCheck > 0) {
            throw IllegalStateException("Queue drain verification failed: $finalCheck fixes remain pending after drain loop for trip $tripId")
        }

        Log.d("DrainPendingGpsFixes", "Successfully drained $totalProcessed pending fixes for trip $tripId")
        return totalProcessed
    }
}
