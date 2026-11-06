package com.businesstracker.mileage.transition

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.businesstracker.mileage.receiver.AutoDriveReceiver
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityTransitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val activityRecognitionClient: ActivityRecognitionClient =
        ActivityRecognition.getClient(context)

    private val transitionPendingIntent: PendingIntent by lazy {
        val intent = Intent(context, AutoDriveReceiver::class.java).apply {
            action = AutoDriveReceiver.ACTION_ACTIVITY_TRANSITION
        }
        PendingIntent.getBroadcast(
            context,
            AutoDriveReceiver.TRANSITION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun registerTransitions(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        val transitions = listOf(
            // In-vehicle enter (user starts driving)
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),

            // In-vehicle exit (user stops / exits car)
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),

            // Still enter (stationary confirmation for debounce)
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        )

        val request = ActivityTransitionRequest(transitions)

        return try {
            activityRecognitionClient.requestActivityTransitionUpdates(request, transitionPendingIntent).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun unregisterTransitions(): Boolean {
        return try {
            activityRecognitionClient.removeActivityTransitionUpdates(transitionPendingIntent).await()
            true
        } catch (_: Exception) {
            false
        }
    }
}
