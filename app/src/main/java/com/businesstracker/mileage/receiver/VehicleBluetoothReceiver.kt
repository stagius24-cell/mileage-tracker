package com.businesstracker.mileage.receiver

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager

@AndroidEntryPoint
class VehicleBluetoothReceiver : BroadcastReceiver() {
    @Inject lateinit var eventHandler: VehicleBluetoothEventHandler
    @Inject lateinit var preferencesManager: TrackingPreferencesManager
    @Inject lateinit var bluetoothArmedDriveMonitor: BluetoothArmedDriveMonitor
    @Inject lateinit var ioDispatcher: CoroutineDispatcher

    @VisibleForTesting
    internal var lastJob: Job? = null

    override fun onReceive(context: Context, intent: Intent?) {
        processBroadcastIntent(context, intent)
    }

    internal fun processBroadcastIntent(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val isProfileAction = action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED ||
            action == BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED &&
            action != BluetoothDevice.ACTION_ACL_DISCONNECTED &&
            action != BluetoothAdapter.ACTION_STATE_CHANGED &&
            !isProfileAction
        ) return

        if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val adapterState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (adapterState == BluetoothAdapter.STATE_OFF) {
                val pendingResult = goAsync()
                lastJob = CoroutineScope(SupervisorJob() + ioDispatcher).launch {
                    try {
                        preferencesManager.setCarBluetoothConnected(false)
                        bluetoothArmedDriveMonitor.reconcile(allowProfileDiscovery = false)
                    } finally {
                        pendingResult?.finish()
                    }
                }
            } else if (adapterState == BluetoothAdapter.STATE_ON) {
                val pendingResult = goAsync()
                lastJob = CoroutineScope(SupervisorJob() + ioDispatcher).launch {
                    try {
                        kotlinx.coroutines.delay(2_500L)
                        eventHandler.handleBluetoothAdapterTurnedOn()
                    } finally {
                        pendingResult?.finish()
                    }
                }
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val effectiveAction = if (isProfileAction) {
            val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
            // Only forward profile connects; never treat individual profile drops as vehicle disconnect.
            if (state != BluetoothProfile.STATE_CONNECTED) return
            BluetoothDevice.ACTION_ACL_CONNECTED
        } else {
            action
        }

        @Suppress("DEPRECATION")
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        val pendingResult = goAsync()
        lastJob = CoroutineScope(SupervisorJob() + ioDispatcher).launch {
            try {
                if (device != null) {
                    eventHandler.handle(effectiveAction, device.address)
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
