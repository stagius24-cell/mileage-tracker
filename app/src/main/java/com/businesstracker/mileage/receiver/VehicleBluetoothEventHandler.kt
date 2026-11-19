package com.businesstracker.mileage.receiver

import android.bluetooth.BluetoothDevice
import com.businesstracker.mileage.data.device.VehicleBluetoothMatcher
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.domain.tracking.TrackingDiagnostics
import com.businesstracker.mileage.domain.tracking.VehicleBluetoothMatchPolicy
import com.businesstracker.mileage.domain.tracking.VehicleResolutionResult
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.worker.TripDebounceScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val eventMutex = Mutex()

    suspend fun handle(action: String, deviceAddress: String) {
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED &&
            action != BluetoothDevice.ACTION_ACL_DISCONNECTED
        ) return

        eventMutex.withLock {
            val connected = action == BluetoothDevice.ACTION_ACL_CONNECTED
            val normalizedEventAddress = VehicleBluetoothMatchPolicy.normalizeAddress(deviceAddress)
            val selectedAddress = preferencesManager.getVehicleBluetoothAddress()
            val matchedVehicle = vehicleBluetoothMatcher.forAddress(normalizedEventAddress)
            val activeTrip = activeTripRepository.getActiveTrip()

            val isAutoStopCar = matchedVehicle != null ||
                (selectedAddress.isNotBlank() &&
                    VehicleBluetoothMatchPolicy.normalizeAddress(selectedAddress) == normalizedEventAddress)

            if (isAutoStopCar) {
                val updatedConnectedAddresses = if (connected) {
                    preferencesManager.addConnectedCarAddress(normalizedEventAddress)
                } else {
                    preferencesManager.removeConnectedCarAddress(normalizedEventAddress)
                }
                bluetoothArmedDriveMonitor.reconcile()
                TrackingDiagnostics.logBluetoothEvent(
                    action = if (connected) "ACL_CONNECTED" else "ACL_DISCONNECTED",
                    redactedDevice = TrackingDiagnostics.redactAddress(normalizedEventAddress),
                    matchedVehicleId = matchedVehicle?.id,
                    candidateCount = updatedConnectedAddresses.size,
                    detail = "vehicle=${matchedVehicle?.name ?: "legacy-car"} remainingConnected=${updatedConnectedAddresses.size}"
                )
            }

            if (activeTrip != null) {
                val isManualVehicle = preferencesManager.isManualVehicleAssignment(activeTrip.id)
                val resolution = vehicleBluetoothMatcher.resolveConnectedVehicle()

                if (connected && matchedVehicle != null) {
                    if (activeTrip.vehicleId == null && !isManualVehicle) {
                        when (resolution) {
                            is VehicleResolutionResult.Matched -> {
                                activeTripRepository.assignVehicleIfAbsent(activeTrip.id, resolution.vehicle.id)
                                val persisted = activeTripRepository.getActiveTrip()?.takeIf { it.id == activeTrip.id }
                                if (persisted?.vehicleId == resolution.vehicle.id) {
                                    LocationTrackingService.updateVehicleDisplay(
                                        activeTrip.id,
                                        resolution.vehicle.id,
                                        resolution.vehicle.name,
                                        connected = true,
                                        ambiguousCount = 0
                                    )
                                }
                                TrackingDiagnostics.logVehicleResolution(
                                    tripId = activeTrip.id,
                                    resultCode = "MATCHED_ON_CONNECT",
                                    vehicleId = resolution.vehicle.id,
                                    candidateCount = 1,
                                    source = resolution.source
                                )
                            }
                            is VehicleResolutionResult.Ambiguous -> {
                                // Do not guess when multiple saved vehicles appear connected.
                                LocationTrackingService.updateVehicleDisplay(
                                    activeTrip.id,
                                    vehicleId = null,
                                    name = null,
                                    connected = true,
                                    ambiguousCount = resolution.candidates.size
                                )
                                TrackingDiagnostics.logVehicleResolution(
                                    tripId = activeTrip.id,
                                    resultCode = "AMBIGUOUS_ON_CONNECT",
                                    vehicleId = null,
                                    candidateCount = resolution.candidates.size,
                                    source = resolution.source
                                )
                            }
                            is VehicleResolutionResult.None -> {
                                val connectedCount = preferencesManager.getConnectedCarAddresses().size
                                if (connectedCount <= 1) {
                                    activeTripRepository.assignVehicleIfAbsent(activeTrip.id, matchedVehicle.id)
                                    val persisted = activeTripRepository.getActiveTrip()?.takeIf { it.id == activeTrip.id }
                                    if (persisted?.vehicleId == matchedVehicle.id) {
                                        LocationTrackingService.updateVehicleDisplay(
                                            activeTrip.id,
                                            matchedVehicle.id,
                                            matchedVehicle.name,
                                            connected = true,
                                            ambiguousCount = 0
                                        )
                                    }
                                    TrackingDiagnostics.logVehicleResolution(
                                        tripId = activeTrip.id,
                                        resultCode = "MATCHED_DIRECT_EVENT",
                                        vehicleId = matchedVehicle.id,
                                        candidateCount = 1,
                                        source = "ACL_EVENT"
                                    )
                                }
                            }
                        }
                    } else if (activeTrip.vehicleId == matchedVehicle.id) {
                        LocationTrackingService.updateVehicleDisplay(
                            activeTrip.id,
                            matchedVehicle.id,
                            matchedVehicle.name,
                            connected = true,
                            ambiguousCount = 0
                        )
                        TrackingDiagnostics.logVehicleResolution(
                            tripId = activeTrip.id,
                            resultCode = "RECONNECTED",
                            vehicleId = matchedVehicle.id,
                            candidateCount = 1,
                            source = "ACL_EVENT"
                        )
                    }
                } else if (!connected) {
                    if (activeTrip.vehicleId == null && !isManualVehicle) {
                        // Ambiguity recovery: if the stationary second car disconnected while driving away,
                        // resolve the remaining connected vehicle now.
                        when (resolution) {
                            is VehicleResolutionResult.Matched -> {
                                activeTripRepository.assignVehicleIfAbsent(activeTrip.id, resolution.vehicle.id)
                                val persisted = activeTripRepository.getActiveTrip()?.takeIf { it.id == activeTrip.id }
                                if (persisted?.vehicleId == resolution.vehicle.id) {
                                    LocationTrackingService.updateVehicleDisplay(
                                        activeTrip.id,
                                        resolution.vehicle.id,
                                        resolution.vehicle.name,
                                        connected = true,
                                        ambiguousCount = 0
                                    )
                                }
                                TrackingDiagnostics.logVehicleResolution(
                                    tripId = activeTrip.id,
                                    resultCode = "RESOLVED_AFTER_DISCONNECT",
                                    vehicleId = resolution.vehicle.id,
                                    candidateCount = 1,
                                    source = resolution.source
                                )
                            }
                            is VehicleResolutionResult.Ambiguous -> {
                                LocationTrackingService.updateVehicleDisplay(
                                    activeTrip.id,
                                    vehicleId = null,
                                    name = null,
                                    connected = true,
                                    ambiguousCount = resolution.candidates.size
                                )
                            }
                            is VehicleResolutionResult.None -> Unit
                        }
                    } else if (matchedVehicle != null && activeTrip.vehicleId == matchedVehicle.id) {
                        LocationTrackingService.updateVehicleDisplay(
                            activeTrip.id,
                            matchedVehicle.id,
                            matchedVehicle.name,
                            connected = false,
                            ambiguousCount = 0
                        )
                        TrackingDiagnostics.logArmedMonitor(
                            "TRIP_VEHICLE_DISCONNECTED",
                            "trip=${activeTrip.id} vehicle=${matchedVehicle.id}"
                        )
                    }
                }
            }

            if (!isAutoStopCar) return@withLock

            if (!preferencesManager.isBluetoothAutoStopActive()) return@withLock

            val currentActiveTrip = activeTripRepository.getActiveTrip() ?: return@withLock
            if (currentActiveTrip.trackingMethod != TrackingMethod.AUTO_DETECTED.value) return@withLock

            if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                val remainingConnected = preferencesManager.getConnectedCarAddresses()
                val assignedToDifferentVehicle = currentActiveTrip.vehicleId != null &&
                    matchedVehicle != null &&
                    currentActiveTrip.vehicleId != matchedVehicle.id

                if (assignedToDifferentVehicle || remainingConnected.isNotEmpty()) {
                    TrackingDiagnostics.logArmedMonitor(
                        "AUTO_STOP_SKIPPED_OTHER_CAR_ACTIVE",
                        "trip=${currentActiveTrip.id} disconnected=${matchedVehicle?.id ?: "unknown"} assigned=${currentActiveTrip.vehicleId} remaining=${remainingConnected.size}"
                    )
                    return@withLock
                }

                preferencesManager.setBluetoothDisconnectPending(true)
                preferencesManager.setInVehicleState(false)
                debounceScheduler.scheduleForBluetoothDisconnect(currentActiveTrip.id)
                TrackingDiagnostics.logStopDeadline(
                    tripId = currentActiveTrip.id,
                    action = "BT_DISCONNECT_DEBOUNCE_SCHEDULED",
                    deadlineMs = 240_000L,
                    reason = "No remaining connected vehicle Bluetooth addresses"
                )
            } else {
                preferencesManager.setBluetoothDisconnectPending(false)
                preferencesManager.setInVehicleState(true)
                debounceScheduler.cancel(currentActiveTrip.id)
                debounceScheduler.cancel()
            }
        }
    }
}
