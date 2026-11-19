package com.businesstracker.mileage.data.device

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
import com.businesstracker.mileage.domain.tracking.VehicleBluetoothMatchPolicy
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

    suspend fun currentlyConnectedVehicle(): VehicleEntity? {
        val connectedAddresses = queryConnectedDeviceAddresses()
        if (connectedAddresses.isEmpty()) return null
        return VehicleBluetoothMatchPolicy.match(ensureLegacyVehicle(), connectedAddresses)
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
        val connected = queryConnectedDeviceAddresses()
        return connected.any { VehicleBluetoothMatchPolicy.normalizeAddress(it) == normalizedTarget }
    }

    private suspend fun queryConnectedDeviceAddresses(): Set<String> {
        val overrideProvider = connectedDevicesProvider
        if (overrideProvider != null) {
            return overrideProvider()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "BLUETOOTH_CONNECT permission not granted; cannot query connected devices")
            return emptySet()
        }

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = runCatching { manager?.adapter }.getOrNull() ?: BluetoothAdapter.getDefaultAdapter() ?: return emptySet()
        if (!adapter.isEnabled) return emptySet()

        val addresses = mutableSetOf<String>()

        // 1. Query A2DP connected devices via BluetoothProfile.ServiceListener
        val a2dpDevices = queryProfileConnectedDevices(adapter, BluetoothProfile.A2DP)
        addresses.addAll(a2dpDevices)

        // 2. Query HEADSET connected devices via BluetoothProfile.ServiceListener
        val headsetDevices = queryProfileConnectedDevices(adapter, BluetoothProfile.HEADSET)
        addresses.addAll(headsetDevices)

        // 3. Query GATT connected devices via standard BluetoothManager (GATT is supported without BLUETOOTH_PRIVILEGED)
        try {
            val gattDevices = manager?.getConnectedDevices(BluetoothProfile.GATT).orEmpty()
            for (device in gattDevices) {
                runCatching { device.address }.getOrNull()?.let { addresses.add(it) }
            }
        } catch (e: Exception) {
            Log.d(TAG, "GATT connected device query skipped: ${e.javaClass.simpleName}")
        }

        return addresses
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
                        if (profile != profileType || proxy == null) {
                            if (continuation.isActive) continuation.resume(emptySet())
                            return
                        }
                        proxyToClose = proxy
                        try {
                            val deviceAddresses = proxy.connectedDevices
                                .mapNotNull { device -> runCatching { device.address }.getOrNull() }
                                .toSet()
                            if (continuation.isActive) continuation.resume(deviceAddresses)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error obtaining devices for profile $profileType: ${e.message}")
                            if (continuation.isActive) continuation.resume(emptySet())
                        } finally {
                            runCatching { adapter.closeProfileProxy(profileType, proxy) }
                            proxyToClose = null
                        }
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        proxyToClose?.let { p ->
                            runCatching { adapter.closeProfileProxy(profileType, p) }
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
                    }
                }
            }
        } ?: emptySet()
    }

    private suspend fun ensureLegacyVehicle(): List<VehicleEntity> {
        val existing = vehicles.getVehicles()
        if (existing.isNotEmpty()) return existing
        val name = preferences.defaultVehicle.first().trim().ifBlank { "Vehicle" }
        val address = preferences.vehicleBluetoothAddress.first().trim().uppercase().ifBlank { null }
        val now = System.currentTimeMillis()
        runCatching {
            vehicles.save(VehicleEntity(
                id = LEGACY_VEHICLE_ID,
                name = name,
                bluetoothAddress = address,
                bluetoothDeviceName = preferences.vehicleBluetoothName.first().trim().ifBlank { null },
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
