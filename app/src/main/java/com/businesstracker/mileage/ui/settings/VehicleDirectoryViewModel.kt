package com.businesstracker.mileage.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.businesstracker.mileage.data.local.entity.VehicleEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.device.VehicleBluetoothMatcher
import com.businesstracker.mileage.domain.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class VehicleDirectoryState(
    val vehicles: List<VehicleEntity> = emptyList(),
    val currentVehicleId: String? = null
) {
    val currentVehicle: VehicleEntity?
        get() = vehicles.firstOrNull { it.id == currentVehicleId } ?: vehicles.firstOrNull()
}

@HiltViewModel
class VehicleDirectoryViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val preferences: TrackingPreferencesManager
) : ViewModel() {
    private val initialized = MutableStateFlow(false)
    val state: StateFlow<VehicleDirectoryState> = combine(
        vehicleRepository.observeVehicles(), preferences.currentVehicleId, initialized
    ) { vehicles, currentId, _ -> VehicleDirectoryState(vehicles, currentId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleDirectoryState())

    init {
        viewModelScope.launch {
            var vehicles = vehicleRepository.getVehicles()
            if (vehicles.isEmpty()) {
                val now = System.currentTimeMillis()
                val legacy = VehicleEntity(
                    id = VehicleBluetoothMatcher.LEGACY_VEHICLE_ID,
                    name = preferences.defaultVehicle.first().trim().ifBlank { "Vehicle" },
                    bluetoothAddress = preferences.vehicleBluetoothAddress.first().trim().uppercase().ifBlank { null },
                    bluetoothDeviceName = preferences.vehicleBluetoothName.first().trim().ifBlank { null },
                    createdAt = now,
                    updatedAt = now
                )
                runCatching { vehicleRepository.save(legacy) }
                vehicles = vehicleRepository.getVehicles()
            }
            val storedId = preferences.currentVehicleId.first()
            val selected = vehicles.firstOrNull { it.id == storedId }
                ?: vehicles.firstOrNull { it.name.equals(preferences.defaultVehicle.first(), ignoreCase = true) }
                ?: vehicles.firstOrNull()
            if (selected != null && selected.id != storedId) selectCurrentVehicle(selected.id)
            initialized.value = true
        }
    }

    suspend fun saveVehicle(
        vehicleId: String?,
        name: String,
        bluetoothAddress: String?,
        bluetoothDeviceName: String?
    ): Result<VehicleEntity> {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty() || normalizedName.length > 60) {
            return Result.failure(IllegalArgumentException("Vehicle name must be 1–60 characters"))
        }
        val now = System.currentTimeMillis()
        val existing = vehicleId?.let { vehicleRepository.getVehicle(it) }
        val normalizedAddress = bluetoothAddress?.trim()?.uppercase()?.ifBlank { null }
        val vehicle = VehicleEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = normalizedName,
            bluetoothAddress = normalizedAddress,
            bluetoothDeviceName = if (normalizedAddress == null) null else bluetoothDeviceName?.trim()?.ifBlank { null },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        return runCatching {
            vehicleRepository.save(vehicle)
            selectCurrentVehicle(vehicle.id)
            vehicle
        }.recoverCatching { error ->
            if (error.message?.contains("UNIQUE", ignoreCase = true) == true) {
                throw IllegalArgumentException("That Bluetooth device is already linked to another vehicle")
            }
            throw error
        }
    }

    suspend fun selectCurrentVehicle(vehicleId: String) {
        val vehicle = vehicleRepository.getVehicle(vehicleId) ?: return
        preferences.setCurrentVehicleId(vehicle.id)
        preferences.setDefaultVehicle(vehicle.name)
    }
}
