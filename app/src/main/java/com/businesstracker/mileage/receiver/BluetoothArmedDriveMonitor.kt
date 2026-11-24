package com.businesstracker.mileage.receiver

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.businesstracker.mileage.data.device.VehicleBluetoothMatcher
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.tracking.TrackingDiagnostics
import com.businesstracker.mileage.domain.tracking.VehicleMotionEvidencePolicy
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Low-power re-arm path used while any saved or configured vehicle Bluetooth device is connected.
 * Activity Recognition runs at a coarse interval; balanced-power one-shot locations are requested
 * only for in-vehicle or post-Bluetooth-connect observations and never keep a GPS foreground service alive.
 */
@Singleton
class BluetoothArmedDriveMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: TrackingPreferencesManager,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val vehicleBluetoothMatcher: VehicleBluetoothMatcher? = null
) {
    companion object {
        const val ACTION_ACTIVITY_UPDATE = "com.businesstracker.mileage.ACTION_VEHICLE_ACTIVITY_UPDATE"
        const val ACTIVITY_REQUEST_CODE = 3002
        const val ACTIVITY_UPDATE_INTERVAL_MILLIS = 30_000L
        const val MIN_LOCATION_SAMPLE_INTERVAL_MILLIS = 30_000L
        const val ACTIVE_POST_CONNECT_WINDOW_MILLIS = 10L * 60_000L
        const val EXTENDED_CONNECTED_SAMPLE_INTERVAL_MILLIS = 60_000L
        const val CONNECT_PROBE_MIN_GAP_MILLIS = 15_000L
        const val CONNECT_PROBE_MIN_SPEED_MPS = 3.0f // ~6.7 mph direct speed gate for immediate connect probe
        const val CONNECT_PROBE_MAX_ACCURACY_METERS = 35f
        const val STARTUP_TRIGGER_COOLDOWN_MILLIS = 60_000L
        const val MAX_FUTURE_CLOCK_SKEW_MILLIS = 15_000L
        private const val LOW_CONFIDENCE_IN_VEHICLE_FLOOR = 25
        private const val ON_FOOT_SUPPRESS_CONFIDENCE = 75
        private const val STILL_REDUCED_CADENCE_CONFIDENCE = 85
        private const val TAG = "BluetoothDriveMonitor"
    }

    private val activityClient: ActivityRecognitionClient = ActivityRecognition.getClient(context)
    private val reconcileMutex = Mutex()
    private val sampleMutex = Mutex()
    private var previousSample: VehicleMotionEvidencePolicy.Sample? = null
    private var lastLocationSampleElapsed = 0L
    private var lastConnectProbeElapsed = 0L
    private var lastTriggeredElapsed = 0L
    private var updatesRegistered = false

    @VisibleForTesting
    internal var postConnectProbeDispatcher: (() -> Unit)? = null

    @VisibleForTesting
    internal var activityUpdateRegistrar: (suspend (Long, PendingIntent) -> Unit)? = null

    @VisibleForTesting
    internal var activityUpdateUnregistrar: (suspend (PendingIntent) -> Unit)? = null

    @VisibleForTesting
    internal var locationProvider: (suspend () -> Location?)? = null

    @VisibleForTesting
    internal var elapsedClock: () -> Long = SystemClock::elapsedRealtime

    @VisibleForTesting
    internal var connectionStateChecker: (suspend (String) -> Boolean)? = null

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

    suspend fun reconcile(allowProfileDiscovery: Boolean = true) = reconcileMutex.withLock {
        val enabled = preferencesManager.isAutoTrackingEnabled.first()
        val selectedAddress = preferencesManager.getVehicleBluetoothAddress()
        var connectedAddresses = preferencesManager.getConnectedCarAddresses()
        var connected = preferencesManager.isCarBluetoothConnected() || connectedAddresses.isNotEmpty()

        val hasConfiguredVehicle = selectedAddress.isNotBlank() ||
            connected ||
            (vehicleBluetoothMatcher?.hasConfiguredVehicles() == true)

        if (enabled && hasConfiguredVehicle && allowProfileDiscovery) {
            val testChecker = connectionStateChecker
            if (!connected && testChecker != null && selectedAddress.isNotBlank()) {
                if (testChecker(selectedAddress)) {
                    connected = true
                    preferencesManager.setCarBluetoothConnected(true)
                    TrackingDiagnostics.logArmedMonitor("RECONCILED_FROM_TEST_CHECKER", "connected=true")
                }
            } else if (vehicleBluetoothMatcher != null) {
                val activeProfileOrGatt = vehicleBluetoothMatcher.queryCurrentlyConnectedCarAddresses()
                if (activeProfileOrGatt.isNotEmpty() && !connected) {
                    preferencesManager.setConnectedCarAddresses(activeProfileOrGatt)
                    connectedAddresses = activeProfileOrGatt
                    connected = true
                    TrackingDiagnostics.logArmedMonitor(
                        "RECONCILED_FROM_PROFILES",
                        "connectedCars=${activeProfileOrGatt.size}"
                    )
                } else if (!connected && selectedAddress.isNotBlank() && isSelectedDeviceCurrentlyConnected(selectedAddress)) {
                    connected = true
                    preferencesManager.setCarBluetoothConnected(true)
                    TrackingDiagnostics.logArmedMonitor("RECONCILED_LEGACY_FROM_PROFILES", "connected=true")
                }
            } else if (!connected && selectedAddress.isNotBlank() && isSelectedDeviceCurrentlyConnected(selectedAddress)) {
                connected = true
                preferencesManager.setCarBluetoothConnected(true)
                TrackingDiagnostics.logArmedMonitor("RECONCILED_LEGACY_FROM_PROFILES", "connected=true")
            }
        }

        val hasActivityPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else true

        val shouldArm = enabled && hasConfiguredVehicle && connected && hasActivityPermission &&
            hasLocationPermission && hasBackgroundLocationPermission

        if (shouldArm) {
            if (updatesRegistered) return@withLock
            try {
                val registrar = activityUpdateRegistrar
                if (registrar != null) registrar(ACTIVITY_UPDATE_INTERVAL_MILLIS, updatePendingIntent)
                else activityClient.requestActivityUpdates(ACTIVITY_UPDATE_INTERVAL_MILLIS, updatePendingIntent).await()
                updatesRegistered = true
                Log.i(TAG, "monitor armed: vehicle Bluetooth connected; interval=${ACTIVITY_UPDATE_INTERVAL_MILLIS}ms")
                TrackingDiagnostics.logArmedMonitor(
                    "ARMED",
                    "connectedCount=${connectedAddresses.size.coerceAtLeast(1)} interval=${ACTIVITY_UPDATE_INTERVAL_MILLIS}ms"
                )
            } catch (e: Exception) {
                updatesRegistered = false
                Log.w(TAG, "monitor arm failed: ${e.javaClass.simpleName}")
                TrackingDiagnostics.logArmedMonitor("ARM_FAILED", e.javaClass.simpleName)
            }
        } else {
            unregisterInternal(
                reason = "enabled=$enabled configured=$hasConfiguredVehicle connected=$connected " +
                    "actPerm=$hasActivityPermission locPerm=$hasLocationPermission bgPerm=$hasBackgroundLocationPermission"
            )
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun isSelectedDeviceCurrentlyConnected(selectedAddress: String): Boolean {
        val testChecker = connectionStateChecker
        if (testChecker != null) return testChecker(selectedAddress)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val gattConnected = manager?.getConnectedDevices(BluetoothProfile.GATT).orEmpty().any { device ->
                device.address.equals(selectedAddress, ignoreCase = true)
            }
            if (gattConnected) return true

            val adapter = runCatching { manager?.adapter }.getOrNull() ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter?.isEnabled == true) {
                isAddressConnectedInProfile(adapter, BluetoothProfile.A2DP, selectedAddress) ||
                    isAddressConnectedInProfile(adapter, BluetoothProfile.HEADSET, selectedAddress)
            } else false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun isAddressConnectedInProfile(
        adapter: BluetoothAdapter,
        profileType: Int,
        targetAddress: String
    ): Boolean {
        return withTimeoutOrNull(2000L) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                var proxyRef: BluetoothProfile? = null
                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                        proxyRef = proxy
                        val connected = runCatching {
                            proxy?.connectedDevices.orEmpty().any {
                                it.address.equals(targetAddress, ignoreCase = true)
                            }
                        }.getOrDefault(false)
                        if (proxy != null) {
                            runCatching { adapter.closeProfileProxy(profile, proxy) }
                        }
                        proxyRef = null
                        if (continuation.isActive) continuation.resume(connected)
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        proxyRef?.let { runCatching { adapter.closeProfileProxy(profile, it) } }
                        proxyRef = null
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
                val requested = try {
                    adapter.getProfileProxy(context, listener, profileType)
                } catch (_: Exception) {
                    false
                }
                if (!requested) {
                    if (continuation.isActive) continuation.resume(false)
                }
                continuation.invokeOnCancellation {
                    proxyRef?.let { runCatching { adapter.closeProfileProxy(profileType, it) } }
                    proxyRef = null
                }
            }
        } ?: false
    }

    suspend fun stop() = reconcileMutex.withLock {
        unregisterInternal("explicit_stop")
    }

    suspend fun resetTriggerCooldown() {
        sampleMutex.withLock {
            lastTriggeredElapsed = 0L
            lastConnectProbeElapsed = 0L
            previousSample = null
        }
    }

    fun requestPostConnectMovementProbe() {
        val dispatcher = postConnectProbeDispatcher
        if (dispatcher != null) {
            dispatcher()
            return
        }
        val intent = Intent(context, AutoDriveReceiver::class.java).apply {
            action = AutoDriveReceiver.ACTION_BT_CONNECT_PROBE
        }
        runCatching { context.sendBroadcast(intent) }
    }

    @VisibleForTesting
    internal suspend fun isArmedForTesting(): Boolean = reconcileMutex.withLock { updatesRegistered }

    private suspend fun unregisterInternal(reason: String) {
        val wasRegistered = updatesRegistered
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
            lastConnectProbeElapsed = 0L
            lastTriggeredElapsed = 0L
        }
        if (wasRegistered) {
            Log.i(TAG, "monitor disarmed ($reason)")
            TrackingDiagnostics.logArmedMonitor("DISARMED", reason)
        }
    }

    /**
     * Returns true only after fresh, corroborated vehicle movement. Stationary occupancy is insufficient.
     * Also tolerates smooth-acceleration / wireless-charger dampening during an active post-Bluetooth-connect
     * window by allowing a single balanced location check when IN_VEHICLE confidence is suppressed, requiring
     * strong GPS speed (>= 2 m/s with accuracy <= 50m) or >= 45m displacement over 10s..180s.
     */
    suspend fun observeActivityUpdate(probableActivities: List<DetectedActivity>): Boolean = sampleMutex.withLock {
        val inVehicle = probableActivities
            .filter { it.type == DetectedActivity.IN_VEHICLE }
            .maxByOrNull { it.confidence }
        val onFoot = probableActivities
            .filter {
                it.type == DetectedActivity.ON_FOOT ||
                    it.type == DetectedActivity.WALKING ||
                    it.type == DetectedActivity.RUNNING
            }
            .maxByOrNull { it.confidence }
        val still = probableActivities
            .filter { it.type == DetectedActivity.STILL }
            .maxByOrNull { it.confidence }

        val now = elapsedClock()
        val isCarConnected = preferencesManager.isCarBluetoothConnected()
        val lastConnectElapsed = if (isCarConnected) preferencesManager.getLastBluetoothConnectElapsed() else 0L
        val inPostConnectWindow = isCarConnected &&
            lastConnectElapsed > 0L &&
            now >= lastConnectElapsed &&
            (now - lastConnectElapsed) <= ACTIVE_POST_CONNECT_WINDOW_MILLIS

        val rawConfidence = inVehicle?.confidence ?: 0
        val highConfidenceVehicle = rawConfidence >= VehicleMotionEvidencePolicy.MIN_IN_VEHICLE_CONFIDENCE
        val moderateVehicle = rawConfidence >= LOW_CONFIDENCE_IN_VEHICLE_FLOOR
        val clearlyWalking = (onFoot?.confidence ?: 0) >= ON_FOOT_SUPPRESS_CONFIDENCE && !moderateVehicle
        val clearlyStill = (still?.confidence ?: 0) >= STILL_REDUCED_CADENCE_CONFIDENCE

        if (!highConfidenceVehicle) {
            if (clearlyWalking) {
                previousSample = null
                TrackingDiagnostics.logArmedMonitor("ACTIVITY_IGNORED_ON_FOOT", "onFoot=${onFoot?.confidence}")
                return@withLock false
            }
            if (!isCarConnected && !moderateVehicle) {
                if (previousSample != null && now - previousSample!!.elapsedRealtimeMillis > VehicleMotionEvidencePolicy.MAX_SAMPLE_GAP_MILLIS) {
                    previousSample = null
                }
                TrackingDiagnostics.logArmedMonitor("ACTIVITY_IGNORED_LOW_CONFIDENCE", "inVehicleConf=$rawConfidence")
                return@withLock false
            }
        }

        val requiredSampleInterval = if (highConfidenceVehicle || inPostConnectWindow || moderateVehicle || !clearlyStill) {
            MIN_LOCATION_SAMPLE_INTERVAL_MILLIS
        } else {
            EXTENDED_CONNECTED_SAMPLE_INTERVAL_MILLIS
        }
        if (lastLocationSampleElapsed > 0L && now - lastLocationSampleElapsed < requiredSampleInterval) {
            return@withLock false
        }
        lastLocationSampleElapsed = now

        val location = try {
            val provider = locationProvider
            if (provider != null) provider() else {
                val cancellation = CancellationTokenSource()
                try {
                    val request = CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setMaxUpdateAgeMillis(5_000L)
                        .setDurationMillis(10_000L)
                        .build()
                    withTimeoutOrNull(12_000L) {
                        fusedLocationClient.getCurrentLocation(request, cancellation.token).await()
                    }
                } finally {
                    cancellation.cancel()
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "movement sample unavailable: ${e.javaClass.simpleName}")
            TrackingDiagnostics.logArmedMonitor("GPS_SAMPLE_UNAVAILABLE", e.javaClass.simpleName)
            null
        } ?: return@withLock false

        val postSampleNow = maxOf(now, elapsedClock())
        val fixElapsed = location.elapsedRealtimeNanos.takeIf { it > 0L }?.div(1_000_000L) ?: 0L
        val futureSkewMs = fixElapsed - postSampleNow
        val sampleAgeMs = (postSampleNow - fixElapsed).coerceAtLeast(0L)
        if (fixElapsed <= 0L || futureSkewMs > MAX_FUTURE_CLOCK_SKEW_MILLIS ||
            sampleAgeMs > VehicleMotionEvidencePolicy.MAX_SAMPLE_GAP_MILLIS
        ) {
            val prevSnapshot = previousSample
            if (prevSnapshot != null &&
                postSampleNow - prevSnapshot.elapsedRealtimeMillis > VehicleMotionEvidencePolicy.MAX_SAMPLE_GAP_MILLIS
            ) {
                previousSample = null
            }
            Log.i(TAG, "movement sample ignored: location fix is missing or stale (age=${sampleAgeMs}ms skew=${futureSkewMs}ms)")
            TrackingDiagnostics.logArmedMonitor(
                "GPS_SAMPLE_STALE",
                "fixElapsed=$fixElapsed now=$postSampleNow age=${sampleAgeMs}ms futureSkew=${futureSkewMs}ms"
            )
            TrackingDiagnostics.logMovementGate(
                gate = "FRESHNESS",
                passed = false,
                rawConf = rawConfidence,
                effectiveConf = rawConfidence,
                speedMps = if (location.hasSpeed()) location.speed else null,
                displacementMeters = null,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
                sampleAgeMs = sampleAgeMs,
                sampleGapMs = 0L,
                inPostConnectWindow = inPostConnectWindow,
                hasPrev = prevSnapshot != null,
                detail = "fixElapsed=$fixElapsed postNow=$postSampleNow skew=${futureSkewMs}ms"
            )
            return@withLock false
        }

        val accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE
        val effectiveConfidence = if (highConfidenceVehicle) {
            rawConfidence
        } else if (accuracyMeters <= VehicleMotionEvidencePolicy.MAX_ACCURACY_METERS) {
            VehicleMotionEvidencePolicy.MIN_IN_VEHICLE_CONFIDENCE
        } else {
            rawConfidence
        }

        val speedMps = if (location.hasSpeed()) location.speed else null
        val current = VehicleMotionEvidencePolicy.Sample(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = accuracyMeters,
            speedMetersPerSecond = speedMps,
            elapsedRealtimeMillis = fixElapsed,
            inVehicleConfidence = effectiveConfidence
        )
        val previous = previousSample

        if (previous != null && current.elapsedRealtimeMillis <= previous.elapsedRealtimeMillis) {
            val speedOnlyConfirmed = if (highConfidenceVehicle) {
                VehicleMotionEvidencePolicy.confirmsMovement(null, current)
            } else {
                accuracyMeters <= VehicleMotionEvidencePolicy.MAX_ACCURACY_METERS &&
                    VehicleMotionEvidencePolicy.confirmsMovement(null, current)
            }
            if (!speedOnlyConfirmed) {
                TrackingDiagnostics.logMovementGate(
                    gate = "DUPLICATE_FIX_TIMESTAMP",
                    passed = false,
                    rawConf = rawConfidence,
                    effectiveConf = effectiveConfidence,
                    speedMps = speedMps,
                    displacementMeters = 0.0,
                    accuracyMeters = accuracyMeters,
                    sampleAgeMs = sampleAgeMs,
                    sampleGapMs = current.elapsedRealtimeMillis - previous.elapsedRealtimeMillis,
                    inPostConnectWindow = inPostConnectWindow,
                    hasPrev = true,
                    detail = "preserving origin anchor; fixElapsed=${current.elapsedRealtimeMillis} <= prev=${previous.elapsedRealtimeMillis}"
                )
                TrackingDiagnostics.logArmedMonitor(
                    "AWAITING_MOVEMENT_CORROBORATION",
                    "rawConf=$rawConfidence effConf=$effectiveConfidence acc=${accuracyMeters}m hasPrev=true duplicateTimestamp=true"
                )
                return@withLock false
            }
        }

        val confirmed = if (highConfidenceVehicle) {
            VehicleMotionEvidencePolicy.confirmsMovement(previous, current)
        } else {
            // When IN_VEHICLE confidence was dampened (e.g. smooth EV acceleration on wireless charger),
            // require stricter accuracy <= 50m on speed fixes too so cell-tower jumps never false-start.
            accuracyMeters <= VehicleMotionEvidencePolicy.MAX_ACCURACY_METERS &&
                VehicleMotionEvidencePolicy.confirmsMovement(previous, current)
        }

        val displacementMeters = previous?.let { prev ->
            val out = FloatArray(1)
            Location.distanceBetween(prev.latitude, prev.longitude, current.latitude, current.longitude, out)
            out[0].toDouble()
        }
        val sampleGapMs = previous?.let { current.elapsedRealtimeMillis - it.elapsedRealtimeMillis } ?: 0L

        if (confirmed && (lastTriggeredElapsed == 0L || postSampleNow - lastTriggeredElapsed >= STARTUP_TRIGGER_COOLDOWN_MILLIS)) {
            lastTriggeredElapsed = postSampleNow
            previousSample = null
            Log.i(TAG, "vehicle movement corroborated; requesting candidate startup (rawConf=$rawConfidence)")
            TrackingDiagnostics.logMovementGate(
                gate = "CORROBORATION",
                passed = true,
                rawConf = rawConfidence,
                effectiveConf = effectiveConfidence,
                speedMps = speedMps,
                displacementMeters = displacementMeters,
                accuracyMeters = accuracyMeters,
                sampleAgeMs = sampleAgeMs,
                sampleGapMs = sampleGapMs,
                inPostConnectWindow = inPostConnectWindow,
                hasPrev = previous != null,
                detail = "confirmed=true cooldownOk=true"
            )
            TrackingDiagnostics.logAutoStartDecision(
                source = "BT_ARMED",
                accepted = true,
                reasonCode = "MOVEMENT_CORROBORATED",
                detail = "rawInVehicleConf=$rawConfidence effConf=$effectiveConfidence postConnect=$inPostConnectWindow acc=${accuracyMeters}m speed=${speedMps ?: "null"} disp=${displacementMeters ?: "null"}"
            )
            true
        } else {
            val cooldownBlocked = confirmed
            val shouldKeepOriginAnchor = !cooldownBlocked &&
                previous != null &&
                previous.inVehicleConfidence >= VehicleMotionEvidencePolicy.MIN_IN_VEHICLE_CONFIDENCE &&
                previous.accuracyMeters in 0f..VehicleMotionEvidencePolicy.MAX_ACCURACY_METERS &&
                sampleGapMs in 0L..VehicleMotionEvidencePolicy.MAX_SAMPLE_GAP_MILLIS
            previousSample = if (shouldKeepOriginAnchor) previous else current

            TrackingDiagnostics.logMovementGate(
                gate = if (cooldownBlocked) "COOLDOWN" else "CORROBORATION",
                passed = false,
                rawConf = rawConfidence,
                effectiveConf = effectiveConfidence,
                speedMps = speedMps,
                displacementMeters = displacementMeters,
                accuracyMeters = accuracyMeters,
                sampleAgeMs = sampleAgeMs,
                sampleGapMs = sampleGapMs,
                inPostConnectWindow = inPostConnectWindow,
                hasPrev = previous != null,
                detail = if (cooldownBlocked) {
                    "cooldownRemMs=${STARTUP_TRIGGER_COOLDOWN_MILLIS - (postSampleNow - lastTriggeredElapsed)}"
                } else {
                    "keptOrigin=$shouldKeepOriginAnchor"
                }
            )
            Log.i(TAG, "movement sample not sufficient; waiting for corroboration (rawConf=$rawConfidence)")
            TrackingDiagnostics.logArmedMonitor(
                "AWAITING_MOVEMENT_CORROBORATION",
                "rawConf=$rawConfidence effConf=$effectiveConfidence acc=${accuracyMeters}m speed=${speedMps ?: "null"} disp=${displacementMeters ?: "null"} hasPrev=${previous != null}"
            )
            false
        }
    }

    /**
     * Immediate one-shot GPS probe triggered when a saved vehicle connects or Bluetooth adapter
     * turns back ON while already driving. Confirms immediately only on reliable GPS speed
     * (>= 3.0 m/s, acc <= 35m) or corroborated displacement (>= 45m) vs a preserved anchor, and
     * otherwise stores a parked origin anchor for subsequent corroboration.
     */
    suspend fun probeMovementOnConnect(): Boolean = sampleMutex.withLock {
        val now = elapsedClock()
        if (!preferencesManager.isCarBluetoothConnected()) return@withLock false
        if (lastConnectProbeElapsed > 0L && now - lastConnectProbeElapsed < CONNECT_PROBE_MIN_GAP_MILLIS) {
            return@withLock false
        }
        lastConnectProbeElapsed = now
        lastLocationSampleElapsed = now

        val location = try {
            val provider = locationProvider
            if (provider != null) provider() else {
                val cancellation = CancellationTokenSource()
                try {
                    val request = CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setMaxUpdateAgeMillis(5_000L)
                        .setDurationMillis(8_000L)
                        .build()
                    withTimeoutOrNull(10_000L) {
                        fusedLocationClient.getCurrentLocation(request, cancellation.token).await()
                    }
                } finally {
                    cancellation.cancel()
                }
            }
        } catch (e: Exception) {
            TrackingDiagnostics.logArmedMonitor("CONNECT_PROBE_UNAVAILABLE", e.javaClass.simpleName)
            null
        } ?: return@withLock false

        val postSampleNow = maxOf(now, elapsedClock())
        val fixElapsed = location.elapsedRealtimeNanos.takeIf { it > 0L }?.div(1_000_000L) ?: 0L
        val futureSkewMs = fixElapsed - postSampleNow
        val sampleAgeMs = (postSampleNow - fixElapsed).coerceAtLeast(0L)
        if (fixElapsed <= 0L || futureSkewMs > MAX_FUTURE_CLOCK_SKEW_MILLIS ||
            sampleAgeMs > VehicleMotionEvidencePolicy.MAX_SAMPLE_GAP_MILLIS
        ) {
            TrackingDiagnostics.logMovementGate(
                gate = "CONNECT_PROBE_FRESHNESS",
                passed = false,
                rawConf = 0,
                effectiveConf = 0,
                speedMps = if (location.hasSpeed()) location.speed else null,
                displacementMeters = null,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
                sampleAgeMs = sampleAgeMs,
                sampleGapMs = 0L,
                inPostConnectWindow = true,
                hasPrev = previousSample != null,
                detail = "fixElapsed=$fixElapsed postNow=$postSampleNow skew=${futureSkewMs}ms"
            )
            return@withLock false
        }

        val accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE
        val effectiveConfidence = if (accuracyMeters <= VehicleMotionEvidencePolicy.MAX_ACCURACY_METERS) {
            VehicleMotionEvidencePolicy.MIN_IN_VEHICLE_CONFIDENCE
        } else {
            0
        }
        val speedMps = if (location.hasSpeed()) location.speed else null
        val current = VehicleMotionEvidencePolicy.Sample(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = accuracyMeters,
            speedMetersPerSecond = speedMps,
            elapsedRealtimeMillis = fixElapsed,
            inVehicleConfidence = effectiveConfidence
        )
        val previous = previousSample

        val directSpeedConfirmed = accuracyMeters in 0f..CONNECT_PROBE_MAX_ACCURACY_METERS &&
            speedMps != null && speedMps.isFinite() && speedMps >= CONNECT_PROBE_MIN_SPEED_MPS
        val displacementConfirmed = accuracyMeters in 0f..VehicleMotionEvidencePolicy.MAX_ACCURACY_METERS &&
            previous != null &&
            current.elapsedRealtimeMillis > previous.elapsedRealtimeMillis &&
            VehicleMotionEvidencePolicy.confirmsMovement(previous, current)
        val confirmed = directSpeedConfirmed || displacementConfirmed

        val displacementMeters = previous?.let { prev ->
            val out = FloatArray(1)
            Location.distanceBetween(prev.latitude, prev.longitude, current.latitude, current.longitude, out)
            out[0].toDouble()
        }
        val sampleGapMs = previous?.let { current.elapsedRealtimeMillis - it.elapsedRealtimeMillis } ?: 0L

        if (confirmed && (lastTriggeredElapsed == 0L || postSampleNow - lastTriggeredElapsed >= STARTUP_TRIGGER_COOLDOWN_MILLIS)) {
            lastTriggeredElapsed = postSampleNow
            previousSample = null
            TrackingDiagnostics.logMovementGate(
                gate = "CONNECT_PROBE",
                passed = true,
                rawConf = 0,
                effectiveConf = effectiveConfidence,
                speedMps = speedMps,
                displacementMeters = displacementMeters,
                accuracyMeters = accuracyMeters,
                sampleAgeMs = sampleAgeMs,
                sampleGapMs = sampleGapMs,
                inPostConnectWindow = true,
                hasPrev = previous != null,
                detail = "directSpeed=$directSpeedConfirmed disp=$displacementConfirmed"
            )
            TrackingDiagnostics.logAutoStartDecision(
                source = "BT_CONNECT_PROBE",
                accepted = true,
                reasonCode = "MOVEMENT_CORROBORATED",
                detail = "acc=${accuracyMeters}m speed=${speedMps ?: "null"} disp=${displacementMeters ?: "null"}"
            )
            true
        } else {
            val cooldownBlocked = confirmed
            val shouldKeepOriginAnchor = !cooldownBlocked &&
                previous != null &&
                previous.inVehicleConfidence >= VehicleMotionEvidencePolicy.MIN_IN_VEHICLE_CONFIDENCE &&
                previous.accuracyMeters in 0f..VehicleMotionEvidencePolicy.MAX_ACCURACY_METERS &&
                sampleGapMs in 0L..VehicleMotionEvidencePolicy.MAX_SAMPLE_GAP_MILLIS
            if (!cooldownBlocked) {
                previousSample = when {
                    shouldKeepOriginAnchor -> previous
                    accuracyMeters in 0f..VehicleMotionEvidencePolicy.MAX_ACCURACY_METERS -> current
                    else -> null
                }
            }
            TrackingDiagnostics.logMovementGate(
                gate = if (cooldownBlocked) "CONNECT_PROBE_COOLDOWN" else "CONNECT_PROBE",
                passed = false,
                rawConf = 0,
                effectiveConf = effectiveConfidence,
                speedMps = speedMps,
                displacementMeters = displacementMeters,
                accuracyMeters = accuracyMeters,
                sampleAgeMs = sampleAgeMs,
                sampleGapMs = sampleGapMs,
                inPostConnectWindow = true,
                hasPrev = previous != null,
                detail = if (cooldownBlocked) {
                    "cooldownRemMs=${STARTUP_TRIGGER_COOLDOWN_MILLIS - (postSampleNow - lastTriggeredElapsed)}"
                } else {
                    "anchorStored=${previousSample != null} keptOrigin=$shouldKeepOriginAnchor"
                }
            )
            false
        }
    }
}
