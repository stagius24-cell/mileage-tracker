package com.businesstracker.mileage.data.device

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.businesstracker.mileage.data.local.entity.VehicleEntity
import com.businesstracker.mileage.domain.repository.VehicleRepository
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import kotlinx.coroutines.flow.first
import com.businesstracker.mileage.domain.tracking.VehicleBluetoothMatchPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleBluetoothMatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vehicles: VehicleRepository,
    private val preferences: TrackingPreferencesManager
) {
    suspend fun currentlyConnectedVehicle(): VehicleEntity? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return null
        val adapter = try { manager.adapter } catch (_: SecurityException) { return null } ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !adapter.isEnabled) return null
        val connected = try {
            listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, BluetoothProfile.GATT)
                .flatMap { profile -> manager.getConnectedDevices(profile) }
                .mapNotNull { device -> runCatching { device.address }.getOrNull() }
                .toSet()
        } catch (_: SecurityException) {
            return null
        } catch (_: RuntimeException) {
            return null
        }
        if (connected.isEmpty()) return null
        return VehicleBluetoothMatchPolicy.match(ensureLegacyVehicle(), connected)
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
    }
}
