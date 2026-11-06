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
import com.businesstracker.mileage.data.remote.GoogleSheetsSyncService
import com.businesstracker.mileage.data.remote.SheetsSyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class GoogleSheetsSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sheetsSyncService: GoogleSheetsSyncService
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val UNIQUE_SHEETS_ONE_TIME = "google_sheets_sync_onetime"
        const val UNIQUE_SHEETS_PERIODIC = "google_sheets_sync_periodic"

        fun enqueueOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<GoogleSheetsSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_SHEETS_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        }

        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<GoogleSheetsSyncWorker>(
                4,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_SHEETS_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        return when (val result = sheetsSyncService.syncPendingTrips()) {
            is SheetsSyncResult.Success -> Result.success()
            is SheetsSyncResult.NotConfigured -> Result.success()
            is SheetsSyncResult.NetworkError -> Result.retry()
            is SheetsSyncResult.TokenExpired -> Result.failure()
            is SheetsSyncResult.HttpError -> {
                if (result.statusCode in 500..599) Result.retry() else Result.failure()
            }
            is SheetsSyncResult.UnexpectedError -> Result.retry()
        }
    }
}
