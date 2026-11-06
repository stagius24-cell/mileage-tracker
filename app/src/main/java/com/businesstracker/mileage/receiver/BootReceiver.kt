package com.businesstracker.mileage.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
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
}
