package com.businesstracker.mileage.domain.tracking

import com.businesstracker.mileage.data.local.entity.VehicleEntity

/** Resolves only exact stable addresses. Device display names are deliberately never used as keys. */
object VehicleBluetoothMatchPolicy {
    fun match(vehicles: List<VehicleEntity>, connectedAddresses: Set<String>): VehicleEntity? {
        val connected = connectedAddresses.mapTo(mutableSetOf(), ::normalizeAddress)
        val matches = vehicles.filter { vehicle ->
            vehicle.bluetoothAddress?.let(::normalizeAddress)?.let(connected::contains) == true
        }.distinctBy { it.id }
        return matches.singleOrNull()
    }

    fun normalizeAddress(address: String): String = address.trim().uppercase()
}

/** Resolves the current friendly name by persisted trip vehicle ID, never by a name snapshot. */
object VehicleFriendlyNameResolver {
    const val UNKNOWN_VEHICLE = "Unknown vehicle"

    fun resolve(vehicleId: String?, vehicles: List<VehicleEntity>): String =
        vehicleId?.let { id -> vehicles.firstOrNull { it.id == id }?.name }
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: UNKNOWN_VEHICLE
}
