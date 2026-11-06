package com.businesstracker.mileage.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.businesstracker.mileage.data.remote.CloudflareSyncService
import com.businesstracker.mileage.data.remote.SyncResult
import com.businesstracker.mileage.data.remote.model.MileageSyncPayload
import com.businesstracker.mileage.domain.repository.TripRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class MileageSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val tripRepository: TripRepository,
    private val syncService: CloudflareSyncService
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val UNIQUE_ONE_TIME_WORK = "mileage_sync_onetime"
        const val UNIQUE_PERIODIC_WORK = "mileage_sync_periodic"

        fun enqueueOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<MileageSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_TIME_WORK,
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        }

        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<MileageSyncWorker>(
                6,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        val pendingTrips = tripRepository.getPendingSyncTrips()

        if (pendingTrips.isEmpty()) {
            return Result.success()
        }

        var anyFailures = false

        for (trip in pendingTrips) {
            // Only sync classified trips (business drives, or personal records if categorized)
            if (trip.classification == "UNREVIEWED") {
                continue
            }

            val payload = MileageSyncPayload(
                id = trip.id,
                date = trip.date,
                businessEntity = trip.businessEntity,
                distanceMiles = trip.businessMiles,
                purpose = trip.purpose,
                startAddress = trip.startAddress,
                endAddress = trip.endAddress,
                rate = trip.applicableRate,
                deduction = trip.calculatedDeduction
            )

            when (val result = syncService.syncMileageEntry(payload)) {
                is SyncResult.Success -> {
                    tripRepository.markSynced(trip.id, System.currentTimeMillis())
                }
                is SyncResult.HttpError -> {
                    // Check if 4xx client error (do not infinite retry on validation) or 5xx server error
                    if (result.statusCode in 500..599) {
                        anyFailures = true
                    }
                }
                is SyncResult.NetworkError -> {
                    // Network connection dropped, trigger exponential backoff retry
                    anyFailures = true
                }
                is SyncResult.UnexpectedError -> {
                    anyFailures = true
                }
            }
        }

        return if (anyFailures) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
