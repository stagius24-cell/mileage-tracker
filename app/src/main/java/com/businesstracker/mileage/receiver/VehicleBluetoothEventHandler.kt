package com.businesstracker.mileage.receiver

import android.bluetooth.BluetoothDevice
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.device.VehicleBluetoothMatcher
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.domain.tracking.TrackingDiagnostics
import com.businesstracker.mileage.domain.tracking.VehicleBluetoothMatchPolicy
import com.businesstracker.mileage.worker.TripDebounceScheduler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleBluetoothEventHandler @Inject constructor(
    private val preferencesManager: TrackingPreferencesManager,
    private val activeTripRepository: ActiveTripRepository,
    private val debounceScheduler: TripDebounceScheduler,
    private val bluetoothArmedDriveMonitor: BluetoothArmedDriveMonitor,
    private val vehicleBluetoothMatcher: VehicleBluetoothMatcher
) {
    suspend fun handle(action: String, deviceAddress: String) {
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED &&
            action != BluetoothDevice.ACTION_ACL_DISCONNECTED
        ) return

        val connected = action == BluetoothDevice.ACTION_ACL_CONNECTED
        val normalizedEventAddress = VehicleBluetoothMatchPolicy.normalizeAddress(deviceAddress)
        val selectedAddress = preferencesManager.getVehicleBluetoothAddress()

        // Vehicle attribution uses a stable address match and remains independent of auto-stop.
        val matchedVehicle = vehicleBluetoothMatcher.forAddress(deviceAddress)
        val activeTrip = activeTripRepository.getActiveTrip()

        // Auto-stop and armed monitoring apply to ALL connected car Bluetooth devices:
        // 1. Any vehicle saved in the vehicle directory matching this Bluetooth address
        // 2. Any device configured as vehicle Bluetooth address
        val isAutoStopCar = matchedVehicle != null ||
            (selectedAddress.isNotBlank() && VehicleBluetoothMatchPolicy.normalizeAddress(selectedAddress) == normalizedEventAddress)

        if (isAutoStopCar) {
            preferencesManager.setCarBluetoothConnected(connected)
            bluetoothArmedDriveMonitor.reconcile()
            TrackingDiagnostics.logArmedMonitor(
                if (connected) "BLUETOOTH_CONNECTED" else "BLUETOOTH_DISCONNECTED",
                "car bluetooth connection state updated (device=$deviceAddress, vehicle=${matchedVehicle?.name ?: "auto-stop-car"})"
            )
        }

        if (matchedVehicle != null && activeTrip != null) {
            if (connected) activeTripRepository.assignVehicleIfAbsent(activeTrip.id, matchedVehicle.id)
            val persisted = activeTripRepository.getActiveTrip()?.takeIf { it.id == activeTrip.id }
            if (persisted?.vehicleId == matchedVehicle.id) {
                com.businesstracker.mileage.service.LocationTrackingService.updateVehicleDisplay(
                    activeTrip.id, matchedVehicle.id, matchedVehicle.name, connected
                )
            }
            TrackingDiagnostics.logArmedMonitor(
                if (connected) "TRIP_VEHICLE_MATCHED" else "TRIP_VEHICLE_DISCONNECTED",
                "trip=${activeTrip.id} vehicle=${matchedVehicle.id}"
            )
        }

        if (!isAutoStopCar) return

        // Bluetooth detection is an independent auto-start signal. The setting below controls
        // only the established disconnect-to-auto-stop behavior.
        if (!preferencesManager.isBluetoothAutoStopActive()) return

        val autoStopTrip = activeTrip ?: return
        if (autoStopTrip.trackingMethod != TrackingMethod.AUTO_DETECTED.value) return

        if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            preferencesManager.setBluetoothDisconnectPending(true)
            preferencesManager.setInVehicleState(false)
            debounceScheduler.scheduleForBluetoothDisconnect(autoStopTrip.id)
        } else {
            preferencesManager.setBluetoothDisconnectPending(false)
            preferencesManager.setInVehicleState(true)
            debounceScheduler.cancel()
        }
    }
}
