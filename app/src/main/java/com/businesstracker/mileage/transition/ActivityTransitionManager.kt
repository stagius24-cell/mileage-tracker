package com.businesstracker.mileage.transition

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
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

    companion object {
        private const val TAG = "ActivityTransitionMgr"
    }

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

    @androidx.annotation.VisibleForTesting
    internal var transitionRegistrar: (suspend () -> Boolean)? = null

    @androidx.annotation.VisibleForTesting
    internal var transitionUnregistrar: (suspend () -> Boolean)? = null

    @SuppressLint("MissingPermission")
    suspend fun registerTransitions(): Boolean {
        transitionRegistrar?.let { return it.invoke() }
        // Enforce required permissions before registering background transitions
        val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            Log.w(TAG, "Cannot register transitions: Location permission not granted")
            return false
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val activityGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!activityGranted) {
                Log.w(TAG, "Cannot register transitions: Activity Recognition permission not granted")
                return false
            }
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
            Log.d(TAG, "Successfully registered activity transitions with Google Play Services")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register activity transition updates: ${e.message}", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun unregisterTransitions(): Boolean {
        transitionUnregistrar?.let { return it.invoke() }
        return try {
            activityRecognitionClient.removeActivityTransitionUpdates(transitionPendingIntent).await()
            Log.d(TAG, "Successfully unregistered activity transitions")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister activity transitions: ${e.message}", e)
            false
        }
    }
}
