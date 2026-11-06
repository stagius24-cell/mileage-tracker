package com.businesstracker.mileage.worker

import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.service.LocationTrackingService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class TripDebounceWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val preferencesManager: TrackingPreferencesManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "trip_debounce_stop_work"
        private const val DEBOUNCE_DELAY_MINUTES = 4L

        fun scheduleDebounce(context: Context) {
            val debounceRequest = OneTimeWorkRequestBuilder<TripDebounceWorker>()
                .setInitialDelay(DEBOUNCE_DELAY_MINUTES, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE, // Restart 4-minute timer if another exit occurs
                debounceRequest
            )
        }

        fun cancelDebounce(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        // Debounce timer elapsed without resumption of vehicle motion
        // Verify in-vehicle state has indeed stopped
        val isInVehicle = preferencesManager.isInVehicle()
        if (!isInVehicle) {
            // Signal service to finalize active trip
            val stopIntent = Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_STOP
            }
            context.startService(stopIntent)
            preferencesManager.clearVehicleAnchor()
        }
        return Result.success()
    }
}
