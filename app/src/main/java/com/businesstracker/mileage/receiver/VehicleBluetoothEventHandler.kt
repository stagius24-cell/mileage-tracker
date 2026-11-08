package com.businesstracker.mileage.receiver

import android.bluetooth.BluetoothDevice
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.worker.TripDebounceScheduler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleBluetoothEventHandler @Inject constructor(
    private val preferencesManager: TrackingPreferencesManager,
    private val activeTripRepository: ActiveTripRepository,
    private val debounceScheduler: TripDebounceScheduler
) {
    suspend fun handle(action: String, deviceAddress: String) {
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED &&
            action != BluetoothDevice.ACTION_ACL_DISCONNECTED
        ) return
        if (!preferencesManager.isBluetoothAutoStopActive()) return

        val selectedAddress = preferencesManager.getVehicleBluetoothAddress()
        if (selectedAddress.isBlank() ||
            !selectedAddress.equals(deviceAddress.trim(), ignoreCase = true)
        ) return

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
