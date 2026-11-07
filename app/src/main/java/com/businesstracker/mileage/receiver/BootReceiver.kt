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
import kotlinx.coroutines.Job
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

    @VisibleForTesting internal var testActivityTransitionManager: ActivityTransitionManager? = null
    @VisibleForTesting internal var testPreferencesManager: TrackingPreferencesManager? = null
    @VisibleForTesting internal var testActiveTripRepository: ActiveTripRepository? = null
    @VisibleForTesting internal var testTripLifecycleDao: TripLifecycleDao? = null

    private val transitionManager get() = testActivityTransitionManager ?: activityTransitionManager
    private val prefsManager get() = testPreferencesManager ?: preferencesManager
    private val activeTripRepo get() = testActiveTripRepository ?: activeTripRepository
    private val lifecycleDao get() = testTripLifecycleDao ?: tripLifecycleDao

    private var receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @VisibleForTesting
    internal var lastJob: Job? = null

    @VisibleForTesting
    internal var activityTransitionRegistrar: (suspend () -> Boolean)? = null

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            lastJob = receiverScope.launch {
                try {
                    // 1. Reconcile in-progress trips from previous boot without data loss
                    reconcileBootActiveTrip()

                    // 2. Register Activity Transitions if auto tracking enabled and permission granted
                    val isAutoTrackingEnabled = prefsManager.isAutoTrackingEnabled.first()
                    val hasActivityRecognition = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACTIVITY_RECOGNITION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else true

                    if (isAutoTrackingEnabled && hasActivityRecognition) {
                        if (activityTransitionRegistrar != null) {
                            activityTransitionRegistrar?.invoke()
                        } else {
                            transitionManager.registerTransitions()
                        }
                    }
                } finally {
                    pendingResult?.finish()
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
        val activeTrip = activeTripRepo.getActiveTrip() ?: return
        val tripId = activeTrip.id

        val lifecycle = lifecycleDao.getLifecycle(tripId)
        val currentState = lifecycle?.state?.let { TripLifecycleState.fromString(it) }

        if (currentState == TripLifecycleState.FINALIZED ||
            currentState == TripLifecycleState.DISCARDED ||
            currentState == TripLifecycleState.RECOVERY_REQUIRED
        ) {
            return
        }

        lifecycleDao.insertOrUpdate(
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
        lifecycleDao: TripLifecycleDao,
        scope: CoroutineScope? = null
    ) {
        this.testActivityTransitionManager = transitionManager
        this.testPreferencesManager = prefs
        this.testActiveTripRepository = activeTripRepo
        this.testTripLifecycleDao = lifecycleDao
        this.activityTransitionManager = transitionManager
        this.preferencesManager = prefs
        this.activeTripRepository = activeTripRepo
        this.tripLifecycleDao = lifecycleDao
        scope?.let { this.receiverScope = it }
    }
}
