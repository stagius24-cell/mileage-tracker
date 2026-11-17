package com.businesstracker.mileage.domain.tracking

import com.businesstracker.mileage.data.local.entity.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleBluetoothMatchPolicyTest {
    private fun vehicle(id: String, name: String, address: String?) = VehicleEntity(
        id = id, name = name, bluetoothAddress = address, bluetoothDeviceName = "Duplicate Display Name",
        createdAt = 1L, updatedAt = 1L
    )

    @Test
    fun exactNormalizedAddressMatchesEvenWhenDeviceNamesDuplicate() {
        val first = vehicle("car-1", "Blue Sedan", "AA:BB:CC:DD:EE:01")
        val second = vehicle("car-2", "Red Sedan", "AA:BB:CC:DD:EE:02")

        assertEquals(first, VehicleBluetoothMatchPolicy.match(listOf(first, second), setOf("aa:bb:cc:dd:ee:01")))
    }

    @Test
    fun unknownAddressDoesNotGuessUsingDisplayName() {
        val saved = vehicle("car-1", "My Car", "AA:BB:CC:DD:EE:01")

        assertNull(VehicleBluetoothMatchPolicy.match(listOf(saved), setOf("AA:BB:CC:DD:EE:99")))
    }

    @Test
    fun multipleConnectedSavedVehiclesAreTreatedAsAmbiguous() {
        val first = vehicle("car-1", "Car One", "AA:BB:CC:DD:EE:01")
        val second = vehicle("car-2", "Car Two", "AA:BB:CC:DD:EE:02")

        assertNull(VehicleBluetoothMatchPolicy.match(listOf(first, second), setOf(first.bluetoothAddress!!, second.bluetoothAddress!!)))
    }

    @Test
    fun unpairedVehiclesCannotBeGuessed() {
        val saved = vehicle("car-1", "No Device", null)

        assertNull(VehicleBluetoothMatchPolicy.match(listOf(saved), setOf("AA:BB:CC:DD:EE:01")))
    }
}
