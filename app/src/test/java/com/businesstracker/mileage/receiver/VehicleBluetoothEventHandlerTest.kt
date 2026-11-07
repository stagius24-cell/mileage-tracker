package com.businesstracker.mileage.receiver

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.model.Waypoint
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.worker.TripDebounceScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VehicleBluetoothEventHandlerTest {
    private lateinit var preferences: TrackingPreferencesManager
    private lateinit var repository: FakeActiveTripRepository
    private lateinit var scheduler: RecordingScheduler
    private lateinit var handler: VehicleBluetoothEventHandler

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = TrackingPreferencesManager(context)
        preferences.setBluetoothAutoStopEnabled(false)
        preferences.setBluetoothDisconnectPending(false)
        preferences.setInVehicleState(true)
        preferences.setVehicleBluetoothDevice(CAR_ADDRESS, "My Car")
        repository = FakeActiveTripRepository(autoTrip())
        scheduler = RecordingScheduler(context)
        handler = VehicleBluetoothEventHandler(preferences, repository, scheduler)
    }

    @Test
    fun selectedCarDisconnect_startsSafeStopSignal() = runBlocking {
        preferences.setBluetoothAutoStopEnabled(true)

        handler.handle(BluetoothDevice.ACTION_ACL_DISCONNECTED, CAR_ADDRESS.lowercase())

        assertTrue(scheduler.scheduled)
        assertFalse(preferences.isInVehicle())
        assertTrue(preferences.isBluetoothDisconnectPending())
    }

    @Test
    fun selectedCarReconnect_cancelsPendingStop() = runBlocking {
        preferences.setBluetoothAutoStopEnabled(true)
        preferences.setBluetoothDisconnectPending(true)
        preferences.setInVehicleState(false)

        handler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, CAR_ADDRESS)

        assertTrue(scheduler.cancelled)
        assertTrue(preferences.isInVehicle())
        assertFalse(preferences.isBluetoothDisconnectPending())
    }

    @Test
    fun otherDeviceDisconnect_isIgnored() = runBlocking {
        preferences.setBluetoothAutoStopEnabled(true)

        handler.handle(BluetoothDevice.ACTION_ACL_DISCONNECTED, "AA:BB:CC:DD:EE:FF")

        assertFalse(scheduler.scheduled)
        assertTrue(preferences.isInVehicle())
    }

    @Test
    fun manualTripDisconnect_isIgnored() = runBlocking {
        preferences.setBluetoothAutoStopEnabled(true)
        repository.activeTrip.value = autoTrip().copy(trackingMethod = TrackingMethod.MANUAL.value)

        handler.handle(BluetoothDevice.ACTION_ACL_DISCONNECTED, CAR_ADDRESS)

        assertFalse(scheduler.scheduled)
        assertTrue(preferences.isInVehicle())
    }

    @Test
    fun disabledOptionDisconnect_isIgnored() = runBlocking {
        handler.handle(BluetoothDevice.ACTION_ACL_DISCONNECTED, CAR_ADDRESS)

        assertFalse(scheduler.scheduled)
        assertTrue(preferences.isInVehicle())
    }

    private fun autoTrip() = ActiveTripEntity(
        id = "active-trip",
        startTime = 1L,
        startLatitude = 1.0,
        startLongitude = 1.0,
        lastLatitude = 1.1,
        lastLongitude = 1.1,
        lastTimestamp = 2L,
        totalDistanceMeters = 500.0,
        trackingMethod = TrackingMethod.AUTO_DETECTED.value
    )

    private class RecordingScheduler(context: Context) : TripDebounceScheduler(context) {
        var scheduled = false
        var cancelled = false

        override fun scheduleForBluetoothDisconnect() {
            scheduled = true
        }

        override fun cancel() {
            cancelled = true
        }
    }

    private class FakeActiveTripRepository(initial: ActiveTripEntity?) : ActiveTripRepository {
        val activeTrip = MutableStateFlow(initial)
        override suspend fun getActiveTrip() = activeTrip.value
        override fun observeActiveTrip(): Flow<ActiveTripEntity?> = activeTrip
        override suspend fun createActiveTrip(
            tripId: String,
            latitude: Double,
            longitude: Double,
            trackingMethod: String,
            suggestedBusiness: String
        ): ActiveTripEntity = error("Not used")
        override suspend fun recordWaypoint(tripId: String, waypoint: Waypoint) = Unit
        override suspend fun updateTripProgress(
            tripId: String,
            distanceMeters: Double,
            latitude: Double,
            longitude: Double,
            timestamp: Long
        ) = Unit
        override suspend fun setPaused(tripId: String, isPaused: Boolean) = Unit
        override suspend fun getWaypoints(tripId: String): List<Waypoint> = emptyList()
        override fun observeWaypoints(tripId: String): Flow<List<Waypoint>> = MutableStateFlow(emptyList())
        override suspend fun clearActiveTrip(tripId: String) {
            if (activeTrip.value?.id == tripId) activeTrip.value = null
        }
    }

    companion object {
        private const val CAR_ADDRESS = "11:22:33:44:55:66"
    }
}
