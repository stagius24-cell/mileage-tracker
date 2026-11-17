package com.businesstracker.mileage.receiver

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.tracking.VehicleMotionEvidencePolicy
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-power re-arm path used only while the selected vehicle Bluetooth device is connected.
 * Activity Recognition runs at a coarse interval; balanced-power one-shot locations are requested
 * only for in-vehicle observations and never keep a GPS foreground service alive.
 */
@Singleton
class BluetoothArmedDriveMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: TrackingPreferencesManager,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    companion object {
        const val ACTION_ACTIVITY_UPDATE = "com.businesstracker.mileage.ACTION_VEHICLE_ACTIVITY_UPDATE"
        const val ACTIVITY_REQUEST_CODE = 3002
        const val ACTIVITY_UPDATE_INTERVAL_MILLIS = 30_000L
        const val MIN_LOCATION_SAMPLE_INTERVAL_MILLIS = 30_000L
        private const val TAG = "BluetoothDriveMonitor"
    }

    private val activityClient: ActivityRecognitionClient = ActivityRecognition.getClient(context)
    private val sampleMutex = Mutex()
    private var previousSample: VehicleMotionEvidencePolicy.Sample? = null
    private var lastLocationSampleElapsed = 0L
    private var lastTriggeredElapsed = 0L
    private var updatesRegistered = false

    @VisibleForTesting
    internal var activityUpdateRegistrar: (suspend (Long, PendingIntent) -> Unit)? = null

    @VisibleForTesting
    internal var activityUpdateUnregistrar: (suspend (PendingIntent) -> Unit)? = null

    @VisibleForTesting
    internal var locationProvider: (suspend () -> Location?)? = null

    @VisibleForTesting
    internal var elapsedClock: () -> Long = SystemClock::elapsedRealtime

    private val updatePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, AutoDriveReceiver::class.java).apply {
            action = ACTION_ACTIVITY_UPDATE
        }
        PendingIntent.getBroadcast(
            context,
            ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    suspend fun reconcile() {
        val enabled = preferencesManager.isAutoTrackingEnabled.first()
        val selectedAddress = preferencesManager.getVehicleBluetoothAddress()
        val hasSelectedDevice = selectedAddress.isNotBlank()
        var connected = preferencesManager.isCarBluetoothConnected()
        if (enabled && hasSelectedDevice && !connected && isSelectedDeviceCurrentlyConnected(selectedAddress)) {
            connected = true
            preferencesManager.setCarBluetoothConnected(true)
            Log.i(TAG, "selected vehicle connection reconciled from Bluetooth profiles")
        }
        val hasActivityPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocationPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        val shouldArm = enabled && hasSelectedDevice && connected && hasActivityPermission &&
            hasLocationPermission && hasBackgroundLocationPermission

        if (shouldArm) {
            if (updatesRegistered) return
            try {
                val registrar = activityUpdateRegistrar
                if (registrar != null) registrar(ACTIVITY_UPDATE_INTERVAL_MILLIS, updatePendingIntent)
                else activityClient.requestActivityUpdates(ACTIVITY_UPDATE_INTERVAL_MILLIS, updatePendingIntent).await()
                updatesRegistered = true
                Log.i(TAG, "monitor armed: selected vehicle connected; interval=${ACTIVITY_UPDATE_INTERVAL_MILLIS}ms")
            } catch (e: Exception) {
                updatesRegistered = false
                Log.w(TAG, "monitor arm failed: ${e.javaClass.simpleName}")
            }
        } else {
            unregister()
        }
    }

    @Suppress("DEPRECATION")
    private fun isSelectedDeviceCurrentlyConnected(selectedAddress: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
            listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, BluetoothProfile.GATT)
                .asSequence()
                .flatMap { profile -> manager.getConnectedDevices(profile).asSequence() }
                .any { device -> device.address.equals(selectedAddress, ignoreCase = true) }
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun stop() = unregister()

    private suspend fun unregister() {
        try {
            val unregistrar = activityUpdateUnregistrar
            if (unregistrar != null) unregistrar(updatePendingIntent)
            else activityClient.removeActivityUpdates(updatePendingIntent).await()
        } catch (_: Exception) {
            // Idempotent removal is expected when no request has been registered.
        }
        updatesRegistered = false
        sampleMutex.withLock {
            previousSample = null
            lastLocationSampleElapsed = 0L
            lastTriggeredElapsed = 0L
        }
        Log.i(TAG, "monitor disarmed")
    }

    /** Returns true only after fresh, corroborated vehicle movement; stationary occupancy is insufficient. */
    suspend fun observeActivityUpdate(probableActivities: List<DetectedActivity>): Boolean = sampleMutex.withLock {
        val inVehicle = probableActivities
            .filter { it.type == DetectedActivity.IN_VEHICLE }
            .maxByOrNull { it.confidence }
        if (inVehicle == null || inVehicle.confidence < VehicleMotionEvidencePolicy.MIN_IN_VEHICLE_CONFIDENCE) {
            previousSample = null
            Log.i(TAG, "activity ignored: no confident IN_VEHICLE observation")
            return@withLock false
        }

        val now = elapsedClock()
        if (lastLocationSampleElapsed > 0L && now - lastLocationSampleElapsed < MIN_LOCATION_SAMPLE_INTERVAL_MILLIS) {
            return@withLock false
        }
        lastLocationSampleElapsed = now

        val location = try {
            val provider = locationProvider
            if (provider != null) provider() else {
                val cancellation = CancellationTokenSource()
                try {
                    withTimeoutOrNull(12_000L) {
                        fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                            cancellation.token
                        ).await()
                    }
                } finally {
                    cancellation.cancel()
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "movement sample unavailable: ${e.javaClass.simpleName}")
            null
        } ?: return@withLock false

        val fixElapsed = location.elapsedRealtimeNanos.takeIf { it > 0L }?.div(1_000_000L) ?: 0L
        if (fixElapsed <= 0L || fixElapsed > now || now - fixElapsed > VehicleMotionEvidencePolicy.MAX_SAMPLE_GAP_MILLIS) {
            previousSample = null
            Log.i(TAG, "movement sample ignored: location fix is missing or stale")
            return@withLock false
        }
        val current = VehicleMotionEvidencePolicy.Sample(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
            speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
            elapsedRealtimeMillis = fixElapsed,
            inVehicleConfidence = inVehicle.confidence
        )
        val previous = previousSample
        previousSample = current
        val confirmed = VehicleMotionEvidencePolicy.confirmsMovement(previous, current)
        if (confirmed && (lastTriggeredElapsed == 0L || now - lastTriggeredElapsed >= 60_000L)) {
            lastTriggeredElapsed = now
            previousSample = null
            Log.i(TAG, "vehicle movement corroborated; requesting candidate startup")
            true
        } else {
            Log.i(TAG, "movement sample not sufficient; waiting for corroboration")
            false
        }
    }
}
