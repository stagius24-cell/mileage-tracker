package com.businesstracker.mileage

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.security.SecureStorageManager
import androidx.work.WorkManager
import com.businesstracker.mileage.domain.tracking.TrackingDiagnostics
import com.businesstracker.mileage.receiver.BluetoothArmedDriveMonitor
import com.businesstracker.mileage.service.NotificationHelper
import com.businesstracker.mileage.transition.ActivityTransitionManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class MileageTrackerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var activityTransitionManager: ActivityTransitionManager
    @Inject lateinit var bluetoothArmedDriveMonitor: BluetoothArmedDriveMonitor
    @Inject lateinit var preferencesManager: TrackingPreferencesManager
    @Inject lateinit var secureStorageManager: SecureStorageManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        TrackingDiagnostics.initialize(applicationContext)

        try {
            com.google.android.gms.maps.MapsInitializer.initialize(applicationContext)
        } catch (_: Exception) {
            // Maps will initialize when MapView or GoogleMap is created
        }

        // Initialize transition recognition and Bluetooth armed monitoring if enabled by user
        appScope.launch {
            preferencesManager.checkAndApplyAutoTrackingResumeIfDue()
            val autoTrackEnabled = preferencesManager.isAutoTrackingEnabled.first()
            if (autoTrackEnabled) {
                activityTransitionManager.registerTransitions()
                bluetoothArmedDriveMonitor.reconcile()
            }
        }

        // Mileage records are backed up to the dedicated Cloudflare D1 service.
        // Stop any previously scheduled automatic Sheets mileage uploads; manual legacy
        // Sheets tools remain available during the migration period.
        appScope.launch {
            preferencesManager.migrateLegacyCloudflareAccessCredentials()
            val workManager = WorkManager.getInstance(applicationContext)
            workManager.cancelUniqueWork(com.businesstracker.mileage.worker.GoogleSheetsSyncWorker.UNIQUE_SHEETS_PERIODIC)
            workManager.cancelUniqueWork(com.businesstracker.mileage.worker.GoogleSheetsSyncWorker.UNIQUE_SHEETS_ONE_TIME)
            if (secureStorageManager.hasOAuthToken()) {
                com.businesstracker.mileage.worker.MileageSyncWorker.enqueuePeriodicSync(applicationContext)
                com.businesstracker.mileage.worker.MileageSyncWorker.enqueueOneTimeSync(applicationContext)
            }
        }
    }

    override fun onTerminate() {
        // Android calls this only for emulated application shutdown. Wait for
        // startup work to stop before its Android context is torn down.
        runBlocking {
            appScope.coroutineContext[Job]?.cancelAndJoin()
        }
        super.onTerminate()
    }
}
