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
import com.businesstracker.mileage.data.remote.CloudflareBackupResult
import com.businesstracker.mileage.data.remote.CloudflareD1BackupService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class MileageSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val cloudflareBackupService: CloudflareD1BackupService
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val UNIQUE_ONE_TIME_WORK = "mileage_sync_onetime"
        const val UNIQUE_PERIODIC_WORK = "mileage_sync_periodic"

        fun enqueueOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<MileageSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_TIME_WORK, ExistingWorkPolicy.REPLACE, request
            )
        }

        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<MileageSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }

    override suspend fun doWork(): Result =
        when (val result = cloudflareBackupService.backupAll()) {
            is CloudflareBackupResult.Success -> Result.success()
            is CloudflareBackupResult.Failure ->
                if (result.retryable) Result.retry() else Result.failure()
        }
}
