package com.businesstracker.mileage.receiver

import android.bluetooth.BluetoothDevice
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.domain.tracking.TrackingDiagnostics
import com.businesstracker.mileage.worker.TripDebounceScheduler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleBluetoothEventHandler @Inject constructor(
    private val preferencesManager: TrackingPreferencesManager,
    private val activeTripRepository: ActiveTripRepository,
    private val debounceScheduler: TripDebounceScheduler,
    private val bluetoothArmedDriveMonitor: BluetoothArmedDriveMonitor
) {
    suspend fun handle(action: String, deviceAddress: String) {
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED &&
            action != BluetoothDevice.ACTION_ACL_DISCONNECTED
        ) return
        val selectedAddress = preferencesManager.getVehicleBluetoothAddress()
        if (selectedAddress.isBlank() ||
            !selectedAddress.equals(deviceAddress.trim(), ignoreCase = true)
        ) {
            TrackingDiagnostics.logArmedMonitor("BLUETOOTH_EVENT_IGNORED", "event was not from selected vehicle device")
            return
        }

        val connected = action == BluetoothDevice.ACTION_ACL_CONNECTED
        preferencesManager.setCarBluetoothConnected(connected)
        bluetoothArmedDriveMonitor.reconcile()
        TrackingDiagnostics.logArmedMonitor(
            if (connected) "BLUETOOTH_CONNECTED" else "BLUETOOTH_DISCONNECTED",
            "selected vehicle connection state updated"
        )

        // Bluetooth detection is an independent auto-start signal. The setting below controls
        // only the established disconnect-to-auto-stop behavior.
        if (!preferencesManager.isBluetoothAutoStopActive()) return

        val activeTrip = activeTripRepository.getActiveTrip() ?: return
        if (activeTrip.trackingMethod != TrackingMethod.AUTO_DETECTED.value) return

        if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            preferencesManager.setBluetoothDisconnectPending(true)
            preferencesManager.setInVehicleState(false)
            debounceScheduler.scheduleForBluetoothDisconnect(activeTrip.id)
        } else {
            preferencesManager.setBluetoothDisconnectPending(false)
            preferencesManager.setInVehicleState(true)
            debounceScheduler.cancel()
        }
    }
}
