package com.businesstracker.mileage.receiver

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class VehicleBluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: TrackingPreferencesManager
) {

    private val _isCarBluetoothConnected = MutableStateFlow(false)
    val isCarBluetoothConnected: StateFlow<Boolean> = _isCarBluetoothConnected.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            try {
                val savedState = preferencesManager.isCarBluetoothConnected.first()
                _isCarBluetoothConnected.value = savedState
            } catch (_: Exception) {}
            refreshConnectionState()
        }
    }

    open fun isCarDevice(deviceName: String?, deviceAddress: String?): Boolean {
        val name = deviceName?.trim().orEmpty()
        val addr = deviceAddress?.trim().orEmpty()

        val lowerName = name.lowercase()
        // Specifically matches user's car Bluetooth "Tesla model Y teela" or Tesla Model Y
        if (lowerName.contains("teela")) return true
        if (lowerName.contains("tesla") && lowerName.contains("model y")) return true
        if (lowerName.contains("tesla")) return true

        return try {
            kotlinx.coroutines.runBlocking {
                val configuredAddr = preferencesManager.getVehicleBluetoothAddress()
                val configuredName = preferencesManager.vehicleBluetoothName.first()
                (configuredAddr.isNotBlank() && addr.equals(configuredAddr.trim(), ignoreCase = true)) ||
                    (configuredName.isNotBlank() && name.equals(configuredName.trim(), ignoreCase = true))
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun onDeviceConnectionChanged(action: String, device: BluetoothDevice?) {
        val name = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) null else device?.name
        } catch (_: SecurityException) { null }
        val address = device?.address

        if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            if (isCarDevice(name, address)) {
                setConnected(true)
            }
        } else if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            if (isCarDevice(name, address)) {
                setConnected(false)
            }
        } else {
            refreshConnectionState()
        }
    }

    suspend fun setConnected(connected: Boolean) {
        _isCarBluetoothConnected.value = connected
        try {
            preferencesManager.setCarBluetoothConnected(connected)
        } catch (_: Exception) {}
    }

    open fun refreshConnectionState() {
        scope.launch {
            val connected = checkLiveBluetoothConnection()
            setConnected(connected)
        }
    }

    open fun checkLiveBluetoothConnection(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                ?: return false
            val adapter = bluetoothManager.adapter ?: return false
            if (!adapter.isEnabled) return false

            val bondedDevices = adapter.bondedDevices.orEmpty()
            for (device in bondedDevices) {
                val name = try { device.name } catch (_: SecurityException) { null }
                val address = device.address.orEmpty()

                if (isCarDevice(name, address)) {
                    // Method 1: Check reflection isConnected()
                    val isConnected = try {
                        val method = device.javaClass.getMethod("isConnected")
                        method.invoke(device) as? Boolean ?: false
                    } catch (_: Exception) {
                        false
                    }
                    if (isConnected) return true

                    // Method 2: Check GATT connection state
                    val gattState = try {
                        bluetoothManager.getConnectionState(device, BluetoothProfile.GATT)
                    } catch (_: Exception) {
                        BluetoothProfile.STATE_DISCONNECTED
                    }
                    if (gattState == BluetoothProfile.STATE_CONNECTED) return true
                }
            }

            // Method 3: Check A2DP and Headset profile connection state
            val a2dpState = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
            val headsetState = adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
            if (a2dpState == BluetoothProfile.STATE_CONNECTED || headsetState == BluetoothProfile.STATE_CONNECTED) {
                val hasCarBonded = bondedDevices.any { dev ->
                    val name = try { dev.name } catch (_: SecurityException) { null }
                    isCarDevice(name, dev.address)
                }
                if (hasCarBonded) {
                    checkProfileProxy(adapter)
                }
            }

            false
        } catch (e: Exception) {
            Log.w("VehicleBluetoothManager", "Live Bluetooth check error", e)
            false
        }
    }

    private fun checkProfileProxy(adapter: BluetoothAdapter) {
        try {
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    try {
                        val devices = proxy.connectedDevices
                        val isCar = devices.any { device ->
                            val name = try { device.name } catch (_: SecurityException) { null }
                            isCarDevice(name, device.address)
                        }
                        if (isCar) {
                            scope.launch { setConnected(true) }
                        }
                    } catch (_: Exception) {
                    } finally {
                        try { adapter.closeProfileProxy(profile, proxy) } catch (_: Exception) {}
                    }
                }
                override fun onServiceDisconnected(profile: Int) {}
            }, BluetoothProfile.A2DP)
        } catch (_: Exception) {}
    }
}
