package com.businesstracker.mileage.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.receiver.BluetoothArmedDriveMonitor
import com.businesstracker.mileage.transition.ActivityTransitionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoTrackingResumeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val preferencesManager: TrackingPreferencesManager,
    private val activityTransitionManager: ActivityTransitionManager,
    private val bluetoothArmedDriveMonitor: BluetoothArmedDriveMonitor
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val reactivated = preferencesManager.checkAndApplyAutoTrackingResumeIfDue()
        if (reactivated) {
            try {
                activityTransitionManager.registerTransitions(force = true)
                bluetoothArmedDriveMonitor.reconcile()
            } catch (_: Exception) {
                // Preferences are already updated; next app launch or receiver will also reconcile.
            }
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "auto_tracking_scheduled_resume"

        fun scheduleForDate(context: Context, isoDate: String) {
            val targetDate = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return
            val targetTime = targetDate.atTime(0, 5)
            val delayMillis = Duration.between(LocalDateTime.now(), targetTime).toMillis().coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<AutoTrackingResumeWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }
        }

        fun cancel(context: Context) {
            runCatching {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            }
        }
    }
}
