package com.businesstracker.mileage.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.businesstracker.mileage.data.local.TripLifecycleDao
import com.businesstracker.mileage.data.local.entity.TripLifecycleEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleState
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.transition.ActivityTransitionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var activityTransitionManager: ActivityTransitionManager
    @Inject lateinit var preferencesManager: TrackingPreferencesManager
    @Inject lateinit var activeTripRepository: ActiveTripRepository
    @Inject lateinit var tripLifecycleDao: TripLifecycleDao

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            receiverScope.launch {
                try {
                    // 1. Reconcile in-progress trips from previous boot without data loss
                    reconcileBootActiveTrip()

                    // 2. Register Activity Transitions if auto tracking enabled and permission granted
                    val isAutoTrackingEnabled = preferencesManager.isAutoTrackingEnabled.first()
                    val hasActivityRecognition = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACTIVITY_RECOGNITION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else true

                    if (isAutoTrackingEnabled && hasActivityRecognition) {
                        activityTransitionManager.registerTransitions()
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    /**
     * Idempotently inspects persistent active trip on reboot.
     * Preserves all recorded mileage and GPS evidence, transitioning interrupted
     * tracking trips to RECOVERY_REQUIRED rather than deleting or prematurely finalizing.
     */
    suspend fun reconcileBootActiveTrip() {
        val activeTrip = activeTripRepository.getActiveTrip() ?: return
        val tripId = activeTrip.id

        val lifecycle = tripLifecycleDao.getLifecycle(tripId)
        val currentState = lifecycle?.state?.let { TripLifecycleState.fromString(it) }

        if (currentState == TripLifecycleState.FINALIZED ||
            currentState == TripLifecycleState.DISCARDED ||
            currentState == TripLifecycleState.RECOVERY_REQUIRED
        ) {
            return
        }

        tripLifecycleDao.insertOrUpdate(
            TripLifecycleEntity(
                tripId = tripId,
                state = TripLifecycleState.RECOVERY_REQUIRED.value,
                updatedAt = System.currentTimeMillis(),
                reason = "Interrupted by device reboot; awaiting user review or resumption"
            )
        )
    }

    @VisibleForTesting
    internal fun setTestDependencies(
        transitionManager: ActivityTransitionManager,
        prefs: TrackingPreferencesManager,
        activeTripRepo: ActiveTripRepository,
        lifecycleDao: TripLifecycleDao
    ) {
        this.activityTransitionManager = transitionManager
        this.preferencesManager = prefs
        this.activeTripRepository = activeTripRepo
        this.tripLifecycleDao = lifecycleDao
    }
}
