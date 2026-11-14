package com.businesstracker.mileage.receiver

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class VehicleBluetoothManagerTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var bluetoothManager: VehicleBluetoothManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferencesManager = TrackingPreferencesManager(context)
        val initialized = CountDownLatch(1)
        bluetoothManager = object : VehicleBluetoothManager(context, preferencesManager) {
            override fun refreshConnectionState() { initialized.countDown() }
        }
        // Let the constructor load its initial persisted state before setConnected tests run.
        assertTrue(initialized.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun isCarDevice_matchesTeslaModelYTeela() {
        assertTrue(bluetoothManager.isCarDevice("Tesla model Y teela", "AA:BB:CC:11:22:33"))
        assertTrue(bluetoothManager.isCarDevice("teela", "AA:BB:CC:11:22:33"))
        assertTrue(bluetoothManager.isCarDevice("Tesla Model Y", "AA:BB:CC:11:22:33"))
        assertTrue(bluetoothManager.isCarDevice("TESLA MODEL Y", "AA:BB:CC:11:22:33"))
    }

    @Test
    fun isCarDevice_matchesConfiguredBluetoothDevice() = runBlocking {
        preferencesManager.setVehicleBluetoothDevice("00:11:22:33:44:55", "Custom Car")
        assertTrue(bluetoothManager.isCarDevice("Custom Car", "00:11:22:33:44:55"))
        assertTrue(bluetoothManager.isCarDevice("Other Name", "00:11:22:33:44:55"))
    }

    @Test
    fun isCarDevice_nonCarDevice_returnsFalse() {
        assertFalse(bluetoothManager.isCarDevice("AirPods Pro", "11:22:33:44:55:66"))
        assertFalse(bluetoothManager.isCarDevice("JBL Charge 5", "11:22:33:44:55:67"))
        assertFalse(bluetoothManager.isCarDevice("Logitech MX Master", "11:22:33:44:55:68"))
    }

    @Test
    fun setConnected_updatesStateFlowAndPreferences() = runBlocking {
        bluetoothManager.setConnected(true)
        assertTrue(bluetoothManager.isCarBluetoothConnected.value)
        assertTrue(preferencesManager.isCarBluetoothConnected.first())

        bluetoothManager.setConnected(false)
        assertFalse(bluetoothManager.isCarBluetoothConnected.value)
        assertFalse(preferencesManager.isCarBluetoothConnected.first())
    }
}
