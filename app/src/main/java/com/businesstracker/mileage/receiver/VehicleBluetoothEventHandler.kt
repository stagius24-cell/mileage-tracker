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
                    if (activeTrip == null) {
                        preferencesManager.setBluetoothDisconnectPending(false)
                    }
                    preferencesManager.addConnectedCarAddress(normalizedEventAddress)
                } else {
                    preferencesManager.removeConnectedCarAddress(normalizedEventAddress)
                }
                bluetoothArmedDriveMonitor.reconcile(allowProfileDiscovery = connected)
                TrackingDiagnostics.logBluetoothEvent(
                    action = if (connected) "ACL_CONNECTED" else "ACL_DISCONNECTED",
                    redactedDevice = TrackingDiagnostics.redactAddress(normalizedEventAddress),
                    matchedVehicleId = matchedVehicle?.id,
                    candidateCount = updatedConnectedAddresses.size,
                    detail = "vehicle=${matchedVehicle?.name ?: "legacy-car"} remainingConnected=${updatedConnectedAddresses.size}"
                )
                if (connected && activeTrip == null && !LocationTrackingService.serviceState.value.isTracking) {
                    bluetoothArmedDriveMonitor.requestPostConnectMovementProbe()
                }
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
                val assignedToDisconnectingVehicle = currentActiveTrip.vehicleId != null &&
                    matchedVehicle != null &&
                    currentActiveTrip.vehicleId == matchedVehicle.id

                if (assignedToDifferentVehicle || (!assignedToDisconnectingVehicle && remainingConnected.isNotEmpty())) {
                    TrackingDiagnostics.logArmedMonitor(
                        "AUTO_STOP_SKIPPED_OTHER_CAR_ACTIVE",
                        "trip=${currentActiveTrip.id} disconnected=${matchedVehicle?.id ?: "unknown"} assigned=${currentActiveTrip.vehicleId} remaining=${remainingConnected.size}"
                    )
                    return@withLock
                }

                preferencesManager.setBluetoothDisconnectPending(true)
                preferencesManager.setInVehicleState(false)
                LocationTrackingService.scheduleStopDeadline(
                    tripId = currentActiveTrip.id,
                    trigger = com.businesstracker.mileage.worker.TripDebounceWorker.TRIGGER_BLUETOOTH,
                    delayMillis = 240_000L
                )
                debounceScheduler.scheduleForBluetoothDisconnect(currentActiveTrip.id)
                TrackingDiagnostics.logStopDeadline(
                    tripId = currentActiveTrip.id,
                    action = "BT_DISCONNECT_DEBOUNCE_SCHEDULED",
                    deadlineMs = 240_000L,
                    reason = "Vehicle Bluetooth disconnected (assigned=$assignedToDisconnectingVehicle remaining=${remainingConnected.size})"
                )
            } else {
                if (currentActiveTrip.vehicleId == null ||
                    matchedVehicle == null ||
                    currentActiveTrip.vehicleId == matchedVehicle.id
                ) {
                    preferencesManager.setBluetoothDisconnectPending(false)
                    preferencesManager.setInVehicleState(true)
                    LocationTrackingService.cancelStopDeadline(currentActiveTrip.id)
                    debounceScheduler.cancel(currentActiveTrip.id)
                    debounceScheduler.cancel()
                    TrackingDiagnostics.logStopDeadline(
                        tripId = currentActiveTrip.id,
                        action = "BT_RECONNECT_DEBOUNCE_CANCELLED",
                        deadlineMs = 0L,
                        reason = "Vehicle Bluetooth reconnected"
                    )
                }
            }
        }
    }

    suspend fun handleBluetoothAdapterTurnedOn() = eventMutex.withLock {
        val connectedAddresses = vehicleBluetoothMatcher.queryCurrentlyConnectedCarAddresses()
        if (connectedAddresses.isNotEmpty()) {
            preferencesManager.setConnectedCarAddresses(connectedAddresses)
            bluetoothArmedDriveMonitor.reconcile(allowProfileDiscovery = true)
            TrackingDiagnostics.logBluetoothEvent(
                action = "ADAPTER_ON_RECONCILED",
                redactedDevice = TrackingDiagnostics.redactAddress(connectedAddresses.firstOrNull()),
                matchedVehicleId = null,
                candidateCount = connectedAddresses.size,
                detail = "reconnectedAfterBluetoothToggle=true"
            )
            val activeTrip = activeTripRepository.getActiveTrip()
            if (activeTrip == null) {
                preferencesManager.setBluetoothDisconnectPending(false)
                if (!LocationTrackingService.serviceState.value.isTracking) {
                    bluetoothArmedDriveMonitor.requestPostConnectMovementProbe()
                }
            } else {
                val isManualVehicle = preferencesManager.isManualVehicleAssignment(activeTrip.id)
                val resolution = vehicleBluetoothMatcher.resolveConnectedVehicle()
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
                } else if (resolution is VehicleResolutionResult.Matched && activeTrip.vehicleId == resolution.vehicle.id) {
                    LocationTrackingService.updateVehicleDisplay(
                        activeTrip.id,
                        resolution.vehicle.id,
                        resolution.vehicle.name,
                        connected = true,
                        ambiguousCount = 0
                    )
                }

                if (activeTrip.trackingMethod == TrackingMethod.AUTO_DETECTED.value &&
                    preferencesManager.isBluetoothAutoStopActive() &&
                    (activeTrip.vehicleId == null ||
                        (resolution as? VehicleResolutionResult.Matched)?.vehicle?.id == activeTrip.vehicleId ||
                        resolution !is VehicleResolutionResult.Matched)
                ) {
                    preferencesManager.setBluetoothDisconnectPending(false)
                    preferencesManager.setInVehicleState(true)
                    LocationTrackingService.cancelStopDeadline(activeTrip.id)
                    debounceScheduler.cancel(activeTrip.id)
                    debounceScheduler.cancel()
                    TrackingDiagnostics.logStopDeadline(
                        tripId = activeTrip.id,
                        action = "BT_RECONNECT_DEBOUNCE_CANCELLED",
                        deadlineMs = 0L,
                        reason = "Vehicle Bluetooth reconnected after adapter ON"
                    )
                }
            }
        } else {
            bluetoothArmedDriveMonitor.reconcile(allowProfileDiscovery = true)
        }
    }
}
