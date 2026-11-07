package com.businesstracker.mileage

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
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
    @Inject lateinit var preferencesManager: TrackingPreferencesManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Initialize transition recognition if enabled by user
        appScope.launch {
            val autoTrackEnabled = preferencesManager.isAutoTrackingEnabled.first()
            if (autoTrackEnabled) {
                activityTransitionManager.registerTransitions()
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
