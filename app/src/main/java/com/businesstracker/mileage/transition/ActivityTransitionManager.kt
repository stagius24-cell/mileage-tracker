package com.businesstracker.mileage.transition

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.businesstracker.mileage.domain.tracking.TrackingDiagnostics
import com.businesstracker.mileage.receiver.AutoDriveReceiver
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class TransitionRegistrationState(
    val isRegistered: Boolean = false,
    val lastError: String? = null,
    val lastErrorCode: Int? = null,
    val lastAttemptTime: Long = 0L,
    val isPassiveDriveRegistered: Boolean = false
)

@Singleton
class ActivityTransitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "ActivityTransitionMgr"
        private const val PASSIVE_INTERVAL_MILLIS = 120_000L
        private const val PASSIVE_MIN_UPDATE_INTERVAL_MILLIS = 30_000L
        private const val PASSIVE_MIN_DISPLACEMENT_METERS = 150.0f

        /**
         * The activity types supported by Google Play Services ActivityTransition API:
         * IN_VEHICLE, ON_BICYCLE, RUNNING, STILL, WALKING.
         * (DetectedActivity.ON_FOOT is NOT supported in ActivityTransitionRequest and will fail registration).
         */
        val SUPPORTED_ACTIVITY_TYPES = listOf(
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.STILL
        )
    }

    private val activityRecognitionClient: ActivityRecognitionClient =
        ActivityRecognition.getClient(context)

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val _registrationState = MutableStateFlow(TransitionRegistrationState())
    val registrationState: StateFlow<TransitionRegistrationState> = _registrationState.asStateFlow()

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

    private val passiveDrivePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, AutoDriveReceiver::class.java).apply {
            action = AutoDriveReceiver.ACTION_PASSIVE_DRIVE_WAKEUP
        }
        PendingIntent.getBroadcast(
            context,
            AutoDriveReceiver.PASSIVE_DRIVE_WAKEUP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @androidx.annotation.VisibleForTesting
    internal var transitionRegistrar: (suspend () -> Boolean)? = null

    @androidx.annotation.VisibleForTesting
    internal var transitionUnregistrar: (suspend () -> Boolean)? = null

    @androidx.annotation.VisibleForTesting
    internal var passiveLocationRegistrar: (suspend () -> Boolean)? = null

    @androidx.annotation.VisibleForTesting
    internal var passiveLocationUnregistrar: (suspend () -> Boolean)? = null

    @SuppressLint("MissingPermission")
    suspend fun registerTransitions(force: Boolean = false): Boolean {
        transitionRegistrar?.let {
            val success = it.invoke()
            val passiveRegistered = passiveLocationRegistrar?.invoke() ?: false
            _registrationState.value = TransitionRegistrationState(
                isRegistered = success,
                lastError = if (success) null else "Registration mock returned false",
                lastAttemptTime = System.currentTimeMillis(),
                isPassiveDriveRegistered = passiveRegistered
            )
            return success
        }

        // Avoid duplicate registration attempts if already registered
        if (!force && _registrationState.value.isRegistered) {
            if (!_registrationState.value.isPassiveDriveRegistered) {
                val passiveRegistered = registerPassiveDriveUpdates()
                if (passiveRegistered) {
                    _registrationState.value = _registrationState.value.copy(
                        isPassiveDriveRegistered = true
                    )
                }
            }
            Log.d(TAG, "Activity transitions already registered; skipping duplicate request.")
            return true
        }

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
            val err = "Location permission not granted"
            Log.w(TAG, "Cannot register transitions: $err")
            _registrationState.value = TransitionRegistrationState(
                isRegistered = false,
                lastError = err,
                lastAttemptTime = System.currentTimeMillis(),
                isPassiveDriveRegistered = false
            )
            return false
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val activityGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!activityGranted) {
                val passiveRegistered = registerPassiveDriveUpdates()
                val err = "Activity Recognition permission not granted"
                Log.w(TAG, "Cannot register transitions: $err")
                _registrationState.value = TransitionRegistrationState(
                    isRegistered = false,
                    lastError = err,
                    lastAttemptTime = System.currentTimeMillis(),
                    isPassiveDriveRegistered = passiveRegistered
                )
                return false
            }
        }

        // ONLY supported activity transition types are included.
        // DetectedActivity.ON_FOOT is explicitly excluded as it causes Play Services status 16 / DEVELOPER_ERROR.
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
            val passiveRegistered = registerPassiveDriveUpdates()
            _registrationState.value = TransitionRegistrationState(
                isRegistered = true,
                lastError = null,
                lastErrorCode = null,
                lastAttemptTime = System.currentTimeMillis(),
                isPassiveDriveRegistered = passiveRegistered
            )
            true
        } catch (e: ApiException) {
            val code = e.statusCode
            val statusString = CommonStatusCodes.getStatusCodeString(code)
            val err = "Play Services error $code ($statusString)"
            Log.e(TAG, "Failed to register activity transitions: $err", e)
            val passiveRegistered = registerPassiveDriveUpdates()
            _registrationState.value = TransitionRegistrationState(
                isRegistered = false,
                lastError = err,
                lastErrorCode = code,
                lastAttemptTime = System.currentTimeMillis(),
                isPassiveDriveRegistered = passiveRegistered
            )
            false
        } catch (e: SecurityException) {
            val err = "Permission denied: ${e.message}"
            Log.e(TAG, "SecurityException registering transitions: $err", e)
            val passiveRegistered = registerPassiveDriveUpdates()
            _registrationState.value = TransitionRegistrationState(
                isRegistered = false,
                lastError = err,
                lastErrorCode = null,
                lastAttemptTime = System.currentTimeMillis(),
                isPassiveDriveRegistered = passiveRegistered
            )
            false
        } catch (e: Exception) {
            val err = e.message ?: "Failed to register transitions"
            Log.e(TAG, "Failed to register activity transition updates: $err", e)
            val passiveRegistered = registerPassiveDriveUpdates()
            _registrationState.value = TransitionRegistrationState(
                isRegistered = false,
                lastError = err,
                lastErrorCode = null,
                lastAttemptTime = System.currentTimeMillis(),
                isPassiveDriveRegistered = passiveRegistered
            )
            false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun unregisterTransitions(): Boolean {
        transitionUnregistrar?.let {
            val success = it.invoke()
            passiveLocationUnregistrar?.invoke()
            if (success) {
                _registrationState.value = TransitionRegistrationState(
                    isRegistered = false,
                    lastError = null,
                    isPassiveDriveRegistered = false
                )
            }
            return success
        }
        unregisterPassiveDriveUpdates()
        return try {
            activityRecognitionClient.removeActivityTransitionUpdates(transitionPendingIntent).await()
            Log.d(TAG, "Successfully unregistered activity transitions")
            _registrationState.value = TransitionRegistrationState(
                isRegistered = false,
                lastError = null,
                lastErrorCode = null,
                lastAttemptTime = System.currentTimeMillis(),
                isPassiveDriveRegistered = false
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister activity transitions: ${e.message}", e)
            _registrationState.value = _registrationState.value.copy(isPassiveDriveRegistered = false)
            false
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun registerPassiveDriveUpdates(): Boolean {
        passiveLocationRegistrar?.let { return it.invoke() }

        val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val bgGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
        if ((!fineGranted && !coarseGranted) || !bgGranted) {
            return false
        }

        return try {
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, PASSIVE_INTERVAL_MILLIS)
                .setMinUpdateIntervalMillis(PASSIVE_MIN_UPDATE_INTERVAL_MILLIS)
                .setMinUpdateDistanceMeters(PASSIVE_MIN_DISPLACEMENT_METERS)
                .build()
            fusedLocationClient.requestLocationUpdates(request, passiveDrivePendingIntent).await()
            TrackingDiagnostics.logPassiveMonitor(
                "REGISTERED",
                "priority=BALANCED interval=120s minInterval=30s minDisp=150m"
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register passive drive wakeup updates: ${e.message}")
            TrackingDiagnostics.logPassiveMonitor("REGISTRATION_FAILED", e.javaClass.simpleName)
            false
        }
    }

    private suspend fun unregisterPassiveDriveUpdates() {
        passiveLocationUnregistrar?.let {
            runCatching { it.invoke() }
            return
        }
        val wasRegistered = _registrationState.value.isPassiveDriveRegistered
        runCatching {
            fusedLocationClient.removeLocationUpdates(passiveDrivePendingIntent).await()
        }
        if (wasRegistered) {
            TrackingDiagnostics.logPassiveMonitor("UNREGISTERED", "Auto tracking disabled")
        }
    }

    fun isRegistered(): Boolean = _registrationState.value.isRegistered
}
