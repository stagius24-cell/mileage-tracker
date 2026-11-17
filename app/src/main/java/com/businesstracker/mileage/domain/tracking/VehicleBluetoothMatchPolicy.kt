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
