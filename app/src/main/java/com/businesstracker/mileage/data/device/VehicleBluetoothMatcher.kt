package com.businesstracker.mileage.data.device

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.businesstracker.mileage.data.local.entity.VehicleEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.repository.VehicleRepository
import com.businesstracker.mileage.domain.tracking.TrackingDiagnostics
import com.businesstracker.mileage.domain.tracking.VehicleBluetoothMatchPolicy
import com.businesstracker.mileage.domain.tracking.VehicleResolutionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class VehicleBluetoothMatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vehicles: VehicleRepository,
    private val preferences: TrackingPreferencesManager
) {
    @VisibleForTesting
    internal var connectedDevicesProvider: (suspend () -> Set<String>)? = null

    @VisibleForTesting
    internal var activeProfileDevicesProvider: (suspend () -> Set<String>)? = null

    @VisibleForTesting
    internal var fallbackDevicesProvider: (suspend () -> Set<String>)? = null

    suspend fun currentlyConnectedVehicle(): VehicleEntity? {
        return (resolveConnectedVehicle() as? VehicleResolutionResult.Matched)?.vehicle
    }

    suspend fun resolveConnectedVehicle(): VehicleResolutionResult {
        val savedVehicles = ensureLegacyVehicle()
        if (savedVehicles.isEmpty()) return VehicleResolutionResult.None

        val overrideProvider = connectedDevicesProvider
        if (overrideProvider != null && activeProfileDevicesProvider == null && fallbackDevicesProvider == null) {
            val connected = overrideProvider()
            return if (connected.isEmpty()) {
                VehicleResolutionResult.None
            } else {
                VehicleBluetoothMatchPolicy.resolveTiered(savedVehicles, connected, emptySet())
            }
        }

        val activeProfiles = activeProfileDevicesProvider?.invoke() ?: queryActiveAudioProfileAddresses()
        val fallback = fallbackDevicesProvider?.invoke() ?: (queryGattAddresses() + preferences.getConnectedCarAddresses())
        return VehicleBluetoothMatchPolicy.resolveTiered(
            vehicles = savedVehicles,
            activeProfileAddresses = activeProfiles,
            fallbackAddresses = fallback
        )
    }

    suspend fun queryCurrentlyConnectedCarAddresses(): Set<String> {
        val savedVehicles = ensureLegacyVehicle()
        val legacySelectedAddress = preferences.getVehicleBluetoothAddress().trim().uppercase()

        val allConnected = if (connectedDevicesProvider != null && activeProfileDevicesProvider == null && fallbackDevicesProvider == null) {
            connectedDevicesProvider!!.invoke()
        } else {
            val active = activeProfileDevicesProvider?.invoke() ?: queryActiveAudioProfileAddresses()
            val fallback = fallbackDevicesProvider?.invoke() ?: (queryGattAddresses() + preferences.getConnectedCarAddresses())
            active + fallback
        }

        if (allConnected.isEmpty()) return emptySet()

        val savedAddresses = savedVehicles.mapNotNull {
            it.bluetoothAddress?.let(VehicleBluetoothMatchPolicy::normalizeAddress)?.takeIf(String::isNotEmpty)
        }.toMutableSet()
        if (legacySelectedAddress.isNotEmpty()) {
            savedAddresses.add(legacySelectedAddress)
        }

        return allConnected
            .map(VehicleBluetoothMatchPolicy::normalizeAddress)
            .filter { it in savedAddresses }
            .toSet()
    }

    suspend fun hasConfiguredVehicles(): Boolean {
        val saved = ensureLegacyVehicle()
        if (saved.any { !it.bluetoothAddress.isNullOrBlank() }) return true
        return preferences.getVehicleBluetoothAddress().isNotBlank()
    }

    suspend fun forAddress(address: String): VehicleEntity? {
        if (address.isBlank()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        ensureLegacyVehicle()
        return runCatching { vehicles.getVehicleForBluetoothAddress(address) }.getOrNull()
    }

    suspend fun isDeviceConnected(targetAddress: String): Boolean {
        if (targetAddress.isBlank()) return false
        val normalizedTarget = VehicleBluetoothMatchPolicy.normalizeAddress(targetAddress)
        val connected = if (connectedDevicesProvider != null && activeProfileDevicesProvider == null && fallbackDevicesProvider == null) {
            connectedDevicesProvider!!.invoke()
        } else {
            val active = activeProfileDevicesProvider?.invoke() ?: queryActiveAudioProfileAddresses()
            val fallback = fallbackDevicesProvider?.invoke() ?: (queryGattAddresses() + preferences.getConnectedCarAddresses())
            active + fallback
        }
        return connected.any { VehicleBluetoothMatchPolicy.normalizeAddress(it) == normalizedTarget }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun getEnabledAdapter(): Pair<BluetoothManager, BluetoothAdapter>? {
        if (!hasBluetoothConnectPermission()) {
            Log.d(TAG, "BLUETOOTH_CONNECT permission not granted; cannot query connected devices")
            return null
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return null
        val adapter = runCatching { manager.adapter }.getOrNull() ?: BluetoothAdapter.getDefaultAdapter() ?: return null
        if (!adapter.isEnabled) return null
        return manager to adapter
    }

    private suspend fun queryActiveAudioProfileAddresses(): Set<String> {
        val (_, adapter) = getEnabledAdapter() ?: return emptySet()
        val addresses = mutableSetOf<String>()
        addresses.addAll(queryProfileConnectedDevices(adapter, BluetoothProfile.A2DP))
        addresses.addAll(queryProfileConnectedDevices(adapter, BluetoothProfile.HEADSET))
        return addresses
    }

    private fun queryGattAddresses(): Set<String> {
        val (manager, _) = getEnabledAdapter() ?: return emptySet()
        return try {
            manager.getConnectedDevices(BluetoothProfile.GATT)
                .mapNotNull { device -> runCatching { device.address }.getOrNull() }
                .toSet()
        } catch (e: Exception) {
            Log.d(TAG, "GATT connected device query skipped: ${e.javaClass.simpleName}")
            emptySet()
        }
    }

    private suspend fun queryConnectedDeviceAddresses(): Set<String> {
        val overrideProvider = connectedDevicesProvider
        if (overrideProvider != null) {
            return overrideProvider()
        }
        if (!hasBluetoothConnectPermission()) return emptySet()
        return queryActiveAudioProfileAddresses() + queryGattAddresses()
    }

    private suspend fun queryProfileConnectedDevices(
        adapter: BluetoothAdapter,
        profileType: Int
    ): Set<String> {
        return withTimeoutOrNull(PROFILE_PROXY_TIMEOUT_MS) {
            suspendCancellableCoroutine<Set<String>> { continuation ->
                var proxyToClose: BluetoothProfile? = null
                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                        if (proxy == null) {
                            if (continuation.isActive) continuation.resume(emptySet())
                            return
                        }
                        proxyToClose = proxy
                        try {
                            if (profile == profileType) {
                                val deviceAddresses = proxy.connectedDevices
                                    .mapNotNull { device -> runCatching { device.address }.getOrNull() }
                                    .toSet()
                                if (continuation.isActive) continuation.resume(deviceAddresses)
                            } else {
                                if (continuation.isActive) continuation.resume(emptySet())
                            }
                        } catch (e: SecurityException) {
                            TrackingDiagnostics.logArmedMonitor("BT_PROXY_SECURITY_ERROR", "profile=$profileType")
                            if (continuation.isActive) continuation.resume(emptySet())
                        } catch (e: Exception) {
                            Log.w(TAG, "Error obtaining devices for profile $profileType: ${e.message}")
                            if (continuation.isActive) continuation.resume(emptySet())
                        } finally {
                            // Always close even if coroutine already timed out before onServiceConnected fired.
                            runCatching { adapter.closeProfileProxy(profile, proxy) }
                            proxyToClose = null
                        }
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        proxyToClose?.let { p ->
                            runCatching { adapter.closeProfileProxy(profile, p) }
                            proxyToClose = null
                        }
                        if (continuation.isActive) continuation.resume(emptySet())
                    }
                }

                val proxyRequested = try {
                    adapter.getProfileProxy(context, listener, profileType)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed requesting profile proxy $profileType: ${e.message}")
                    false
                }

                if (!proxyRequested) {
                    if (continuation.isActive) continuation.resume(emptySet())
                    return@suspendCancellableCoroutine
                }

                continuation.invokeOnCancellation {
                    proxyToClose?.let { p ->
                        runCatching { adapter.closeProfileProxy(profileType, p) }
                        proxyToClose = null
                    }
                }
            }
        } ?: emptySet()
    }

    private suspend fun ensureLegacyVehicle(): List<VehicleEntity> {
        val existing = vehicles.getVehicles()
        val legacyAddress = preferences.vehicleBluetoothAddress.first().trim().uppercase().ifBlank { null }
        val legacyBtName = preferences.vehicleBluetoothName.first().trim().ifBlank { null }
        if (existing.isNotEmpty()) {
            val legacyOnly = existing.singleOrNull { it.id == LEGACY_VEHICLE_ID }
            if (legacyOnly != null && legacyOnly.bluetoothAddress.isNullOrBlank() && legacyAddress != null) {
                runCatching {
                    vehicles.save(
                        legacyOnly.copy(
                            bluetoothAddress = legacyAddress,
                            bluetoothDeviceName = legacyBtName ?: legacyOnly.bluetoothDeviceName,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                return vehicles.getVehicles()
            }
            return existing
        }
        val name = preferences.defaultVehicle.first().trim().ifBlank { "Vehicle" }
        val now = System.currentTimeMillis()
        runCatching {
            vehicles.save(VehicleEntity(
                id = LEGACY_VEHICLE_ID,
                name = name,
                bluetoothAddress = legacyAddress,
                bluetoothDeviceName = legacyBtName,
                createdAt = now,
                updatedAt = now
            ))
        }
        if (preferences.currentVehicleId.first().isNullOrBlank()) {
            preferences.setCurrentVehicleId(LEGACY_VEHICLE_ID)
        }
        return vehicles.getVehicles()
    }

    companion object {
        const val LEGACY_VEHICLE_ID = "legacy-default-vehicle"
        private const val TAG = "VehicleBluetoothMatcher"
        private const val PROFILE_PROXY_TIMEOUT_MS = 2000L
    }
}
