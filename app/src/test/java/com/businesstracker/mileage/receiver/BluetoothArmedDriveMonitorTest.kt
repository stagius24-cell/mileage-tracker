package com.businesstracker.mileage.receiver

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BluetoothArmedDriveMonitorTest {
    private lateinit var context: Context
    private lateinit var preferences: TrackingPreferencesManager
    private lateinit var monitor: BluetoothArmedDriveMonitor

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).grantPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            android.Manifest.permission.ACTIVITY_RECOGNITION
        )
        preferences = TrackingPreferencesManager(context)
        preferences.setAutoTrackingEnabled(true)
        preferences.setVehicleBluetoothDevice("11:22:33:44:55:66", "Test Car")
        preferences.setCarBluetoothConnected(true)
        monitor = BluetoothArmedDriveMonitor(
            context,
            preferences,
            LocationServices.getFusedLocationProviderClient(context)
        )
    }

    @Test
    fun monitorArmsOnlyWhileTrackingEnabledAndVehicleConnectionPersists() = runBlocking {
        var registrations = 0
        var removals = 0
        monitor.activityUpdateRegistrar = { interval, _ ->
            assertEquals(BluetoothArmedDriveMonitor.ACTIVITY_UPDATE_INTERVAL_MILLIS, interval)
            registrations++
        }
        monitor.activityUpdateUnregistrar = { removals++ }

        monitor.reconcile()
        monitor.reconcile()
        assertEquals(1, registrations)
        assertTrue(preferences.isCarBluetoothConnected())

        preferences.setAutoTrackingEnabled(false)
        monitor.reconcile()
        assertEquals(1, removals)
    }

    @Test
    fun continuedBluetoothConnectionRearmsAfterStationaryTwoMinutePause() = runBlocking {
        var now = 10_000L
        val locations = ArrayDeque<Location>().apply {
            add(location(37.0, -122.0, now))
            add(location(37.00055, -122.0, 130_000L))
        }
        monitor.elapsedClock = { now }
        monitor.locationProvider = { locations.removeFirstOrNull() }
        val inVehicle = listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 92))

        assertFalse(monitor.observeActivityUpdate(inVehicle))
        now = 130_000L // Car Bluetooth stayed connected while the user was away for two minutes.
        assertTrue(monitor.observeActivityUpdate(inVehicle))
        assertTrue(preferences.isCarBluetoothConnected())
    }

    @Test
    fun stationaryVehicleClassificationDoesNotConfirmMovement() = runBlocking {
        var now = 10_000L
        monitor.elapsedClock = { now }
        monitor.locationProvider = {
            location(37.0, -122.0, now)
        }
        val inVehicle = listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 90))

        assertFalse(monitor.observeActivityUpdate(inVehicle))
        now += BluetoothArmedDriveMonitor.MIN_LOCATION_SAMPLE_INTERVAL_MILLIS
        assertFalse(monitor.observeActivityUpdate(inVehicle))
    }

    private fun location(latitude: Double, longitude: Double, elapsedMillis: Long) =
        Location("test").apply {
            this.latitude = latitude
            this.longitude = longitude
            accuracy = 8f
            speed = 0f
            setElapsedRealtimeNanos(elapsedMillis * 1_000_000L)
        }
}
