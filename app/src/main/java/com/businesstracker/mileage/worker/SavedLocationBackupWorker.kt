package com.businesstracker.mileage.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.businesstracker.mileage.data.remote.SavedLocationsSheetsService
import com.businesstracker.mileage.data.remote.SavedLocationsSyncResult
import com.businesstracker.mileage.domain.repository.SavedLocationBackupScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class SavedLocationBackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val savedLocationsSheetsService: SavedLocationsSheetsService
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val UNIQUE_SAVED_LOCATIONS_BACKUP = "saved_locations_sheets_backup_onetime"

        fun enqueueOneTimeBackup(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val backupRequest = OneTimeWorkRequestBuilder<SavedLocationBackupWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_SAVED_LOCATIONS_BACKUP,
                ExistingWorkPolicy.REPLACE,
                backupRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        return when (val result = savedLocationsSheetsService.backupSavedLocations(fullSnapshot = false)) {
            is SavedLocationsSyncResult.Success -> Result.success()
            is SavedLocationsSyncResult.NotConfigured -> Result.success()
            is SavedLocationsSyncResult.PartialSuccess -> Result.retry()
            is SavedLocationsSyncResult.NetworkError -> Result.retry()
            is SavedLocationsSyncResult.RateLimited -> Result.retry()
            is SavedLocationsSyncResult.ServerError -> Result.retry()
            is SavedLocationsSyncResult.TokenExpired -> Result.failure()
            is SavedLocationsSyncResult.AccessDenied -> Result.failure()
            is SavedLocationsSyncResult.NotFound -> Result.failure()
            is SavedLocationsSyncResult.UnsupportedWebhookOnly -> Result.failure()
            is SavedLocationsSyncResult.InvalidConfiguration -> Result.failure()
            is SavedLocationsSyncResult.HttpError -> {
                if (result.statusCode in 500..599) Result.retry() else Result.failure()
            }
            is SavedLocationsSyncResult.UnexpectedError -> Result.failure()
        }
    }
}

@Singleton
class WorkManagerSavedLocationBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : SavedLocationBackupScheduler {

    override fun scheduleAutoBackup() {
        runCatching {
            SavedLocationBackupWorker.enqueueOneTimeBackup(context)
        }
    }
}
