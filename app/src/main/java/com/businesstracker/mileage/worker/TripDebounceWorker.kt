package com.businesstracker.mileage.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.businesstracker.mileage.data.local.GpsInboxDao
import com.businesstracker.mileage.data.local.TripLifecycleDao
import com.businesstracker.mileage.data.local.entity.TripLifecycleState
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.domain.usecase.DrainPendingGpsFixesUseCase
import com.businesstracker.mileage.domain.usecase.FinalizeTripUseCase
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.service.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@HiltWorker
class TripDebounceWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val preferencesManager: TrackingPreferencesManager,
    private val activeTripRepository: ActiveTripRepository,
    private val tripRepository: TripRepository,
    private val finalizeTripUseCase: FinalizeTripUseCase,
    private val notificationHelper: NotificationHelper,
    private val drainPendingGpsFixesUseCase: DrainPendingGpsFixesUseCase,
    private val gpsInboxDao: GpsInboxDao,
    private val tripLifecycleDao: TripLifecycleDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "trip_debounce_stop_work"
        internal const val INPUT_TRIGGER = "debounce_trigger"
        internal const val INPUT_TRIP_ID = "debounce_trip_id"
        internal const val TRIGGER_ACTIVITY = "activity"
        internal const val TRIGGER_BLUETOOTH = "bluetooth"
        private const val DEBOUNCE_DELAY_MINUTES = 4L

        fun scheduleDebounce(context: Context, tripId: String) {
            scheduleDebounce(context, TRIGGER_ACTIVITY, tripId)
        }

        fun scheduleBluetoothDebounce(context: Context, tripId: String) {
            scheduleDebounce(context, TRIGGER_BLUETOOTH, tripId)
        }

        private fun scheduleDebounce(context: Context, trigger: String, tripId: String) {
            val debounceRequest = OneTimeWorkRequestBuilder<TripDebounceWorker>()
                .setInitialDelay(DEBOUNCE_DELAY_MINUTES, TimeUnit.MINUTES)
                .setInputData(Data.Builder().putString(INPUT_TRIGGER, trigger).putString(INPUT_TRIP_ID, tripId).build())
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
            val scheduledTripId = inputData.getString(INPUT_TRIP_ID) ?: return Result.success()
            val trigger = inputData.getString(INPUT_TRIGGER) ?: TRIGGER_ACTIVITY
            if (trigger == TRIGGER_BLUETOOTH &&
                (!preferencesManager.isBluetoothAutoStopActive() ||
                    !preferencesManager.isBluetoothDisconnectPending())
            ) {
                return Result.success()
            }

            // 1. Verify in-vehicle state is still false (vehicle motion has not resumed)
            val isInVehicle = preferencesManager.isInVehicle()
            if (isInVehicle) {
                // Motion resumed during debounce; active drive continues uninterrupted
                return Result.success()
            }

            // 2. Verify an active trip exists
            val activeTrip = activeTripRepository.getActiveTrip()
            if (activeTrip == null) {
                return Result.success()
            }
            if (activeTrip.id != scheduledTripId ||
                activeTrip.trackingMethod != TrackingMethod.AUTO_DETECTED.value
            ) return Result.success()

            // 3. Coordinate finalization with LocationTrackingService
            val method = TrackingMethod.fromString(activeTrip.trackingMethod)
            val isServiceActive = LocationTrackingService.isTrackingRunning() || LocationTrackingService.isDraining()

            if (isServiceActive) {
                // Delegate stop to the running service so it stops receiving GPS, drains the queue, and finalizes cleanly
                val stopIntent = Intent(context, LocationTrackingService::class.java).apply {
                    action = LocationTrackingService.ACTION_STOP
                    putExtra(LocationTrackingService.EXTRA_TRIP_ID, scheduledTripId)
                    putExtra(LocationTrackingService.EXTRA_DEBOUNCE_TRIGGER, trigger)
                }
                try {
                    context.startService(stopIntent)
                } catch (e: Exception) {
                    Log.w("TripDebounceWorker", "Could not send stop intent to service: ${e.message}")
                }

                // Wait for service to finish draining all GPS fixes and finalize the trip
                var isTerminated = false
                var attempts = 0
                while (attempts < 20) { // Poll up to 4 seconds
                    delay(200L)
                    if (isTripTerminated(activeTrip.id)) {
                        isTerminated = true
                        break
                    }
                    if (!LocationTrackingService.isTrackingRunning() && !LocationTrackingService.isDraining()) {
                        break
                    }
                    attempts++
                }

                if (!isTerminated) {
                    // Service is still actively running or draining GPS points.
                    // The worker MUST NOT finalize prematurely while GPS points are draining.
                    // Return Result.retry() and preserve the active trip and its preferences.
                    Log.i("TripDebounceWorker", "Service still running/draining; retrying worker without clearing preferences")
                    return Result.retry()
                }

                // Finalization confirmed by service
                preferencesManager.clearActiveTripId(activeTrip.id)
                preferencesManager.clearVehicleAnchor()
                preferencesManager.setBluetoothDisconnectPending(false)
                return Result.success()
            }

            // 4. Safe recovery path ONLY when service is completely inactive (killed by OS or device rebooted)
            // Drain all pending GPS fixes from the Room inbox before attempting finalization!
            try {
                if (activeTripRepository.getActiveTrip()?.id != scheduledTripId || preferencesManager.isInVehicle()) {
                    return Result.success()
                }
                drainPendingGpsFixesUseCase.drain(activeTrip.id)
            } catch (e: Exception) {
                Log.e("TripDebounceWorker", "Failed to drain pending GPS fixes during recovery: ${e.message}", e)
                return Result.retry()
            }

            // Confirm pending count is strictly zero
            val remainingFixes = gpsInboxDao.getPendingCount(activeTrip.id)
            if (remainingFixes > 0) {
                Log.w("TripDebounceWorker", "Pending fixes remain ($remainingFixes); retrying worker")
                return Result.retry()
            }

            if (activeTripRepository.getActiveTrip()?.id != scheduledTripId || preferencesManager.isInVehicle()) {
                return Result.success()
            }
            val finalizedTrip = finalizeTripUseCase.execute(activeTrip.id, method)
            if (!isTripTerminated(activeTrip.id)) {
                // Ambiguous recovery: preserve active state and return retry
                return Result.retry()
            }

            if (finalizedTrip != null && finalizedTrip.classification == TripClassification.UNREVIEWED.value) {
                notificationHelper.showTripDetectedNotification(
                    finalizedTrip.id,
                    finalizedTrip.totalDistanceMiles
                )
            }

            // Only clear preferences after confirmed finalization
            preferencesManager.clearActiveTripId(activeTrip.id)
            preferencesManager.clearVehicleAnchor()
            preferencesManager.setBluetoothDisconnectPending(false)

            Result.success()
        } catch (e: Exception) {
            Log.e("TripDebounceWorker", "Recoverable failure during trip debounce finalization", e)
            Result.retry()
        }
    }

    private suspend fun isTripTerminated(tripId: String): Boolean {
        val isFinalizedInDb = tripRepository.getTripById(tripId) != null
        val lifecycleState = tripLifecycleDao.getState(tripId)
        val isDiscarded = lifecycleState == TripLifecycleState.DISCARDED.value || preferencesManager.isTripDiscarded(tripId)
        val isFinalized = isFinalizedInDb || lifecycleState == TripLifecycleState.FINALIZED.value
        return isFinalized || isDiscarded
    }
}
