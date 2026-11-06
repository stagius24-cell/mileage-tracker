package com.businesstracker.mileage.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.domain.usecase.FinalizeTripUseCase
import com.businesstracker.mileage.service.NotificationHelper
import com.businesstracker.mileage.service.LocationTrackingService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class TripDebounceWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val preferencesManager: TrackingPreferencesManager,
    private val activeTripRepository: ActiveTripRepository,
    private val finalizeTripUseCase: FinalizeTripUseCase,
    private val notificationHelper: NotificationHelper
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
        return try {
            // 1. Verify in-vehicle state is still false (vehicle motion has not resumed)
            val isInVehicle = preferencesManager.isInVehicle()
            if (isInVehicle) {
                // Motion resumed during debounce; active drive continues uninterrupted
                return Result.success()
            }

            // 2. Verify an active trip exists
            val activeTrip = activeTripRepository.getActiveTrip()
            if (activeTrip == null) {
                // Trip already finalized by service or discarded
                preferencesManager.clearVehicleAnchor()
                return Result.success()
            }

            // 3. Coordinate finalization with LocationTrackingService
            val method = TrackingMethod.fromString(activeTrip.trackingMethod)
            if (LocationTrackingService.isTrackingRunning()) {
                // Delegate to active service so it stops receiving GPS, drains the queue, and finalizes cleanly
                val stopIntent = Intent(context, LocationTrackingService::class.java).apply {
                    action = LocationTrackingService.ACTION_STOP
                }
                try {
                    context.startService(stopIntent)
                } catch (e: Exception) {
                    // In background restrictions if startService fails, finalize directly
                    val finalizedTrip = finalizeTripUseCase.execute(activeTrip.id, method)
                    if (finalizedTrip != null && finalizedTrip.classification == TripClassification.UNREVIEWED.value) {
                        notificationHelper.showTripDetectedNotification(
                            finalizedTrip.id,
                            finalizedTrip.totalDistanceMiles
                        )
                    }
                }
            } else {
                // Service was stopped or terminated; finalize directly from persisted Room state
                val finalizedTrip = finalizeTripUseCase.execute(activeTrip.id, method)
                if (finalizedTrip != null && finalizedTrip.classification == TripClassification.UNREVIEWED.value) {
                    notificationHelper.showTripDetectedNotification(
                        finalizedTrip.id,
                        finalizedTrip.totalDistanceMiles
                    )
                }
            }

            preferencesManager.setActiveTripId(null)
            preferencesManager.clearVehicleAnchor()

            Result.success()
        } catch (e: Exception) {
            Log.e("TripDebounceWorker", "Recoverable failure during trip debounce finalization", e)
            Result.retry()
        }
    }
}
