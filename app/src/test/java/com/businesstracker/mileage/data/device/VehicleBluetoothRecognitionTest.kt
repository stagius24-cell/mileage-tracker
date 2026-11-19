package com.businesstracker.mileage.data.device

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.entity.VehicleEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.ClassificationSource
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.model.Waypoint
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.domain.repository.VehicleRepository
import com.businesstracker.mileage.domain.tracking.VehicleBluetoothMatchPolicy
import com.businesstracker.mileage.domain.tracking.VehicleFriendlyNameResolver
import com.businesstracker.mileage.receiver.BluetoothArmedDriveMonitor
import com.businesstracker.mileage.receiver.VehicleBluetoothEventHandler
import com.businesstracker.mileage.worker.TripDebounceScheduler
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VehicleBluetoothRecognitionTest {

    private lateinit var context: Context
    private lateinit var preferences: TrackingPreferencesManager
    private lateinit var vehicleRepository: FakeVehicleRepository
    private lateinit var activeTripRepository: FakeActiveTripRepository
    private lateinit var tripRepository: FakeTripRepository
    private lateinit var matcher: VehicleBluetoothMatcher
    private lateinit var eventHandler: VehicleBluetoothEventHandler

    private val tesla = VehicleEntity(
        id = "vehicle-tesla",
        name = "Tesla Model Y",
        bluetoothAddress = "AA:BB:CC:DD:EE:11",
        bluetoothDeviceName = "Tesla Model Y BT",
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val secondCar = VehicleEntity(
        id = "vehicle-civic",
        name = "Honda Civic",
        bluetoothAddress = "11:22:33:44:55:66",
        bluetoothDeviceName = "Honda BT",
        createdAt = 2000L,
        updatedAt = 2000L
    )

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(context.applicationContext as Application).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        preferences = TrackingPreferencesManager(context)
        vehicleRepository = FakeVehicleRepository()
        activeTripRepository = FakeActiveTripRepository()
        tripRepository = FakeTripRepository()
        matcher = VehicleBluetoothMatcher(context, vehicleRepository, preferences)

        val scheduler = RecordingScheduler(context)
        val monitor = BluetoothArmedDriveMonitor(
            context,
            preferences,
            LocationServices.getFusedLocationProviderClient(context)
        ).apply { activityUpdateUnregistrar = {} }

        eventHandler = VehicleBluetoothEventHandler(
            preferences,
            activeTripRepository,
            scheduler,
            monitor,
            matcher
        )

        vehicleRepository.save(tesla)
    }

    // 1. Saved vehicle with connected matching Bluetooth device
    @Test
    fun savedVehicleMatchesWhenConnectedBluetoothAddressMatches() = runBlocking {
        matcher.connectedDevicesProvider = { setOf("AA:BB:CC:DD:EE:11") }

        val matched = matcher.currentlyConnectedVehicle()
        assertEquals(tesla.id, matched?.id)
        assertEquals("Tesla Model Y", matched?.name)
    }

    // 2. Bluetooth connected before the app starts monitoring
    @Test
    fun bluetoothConnectedBeforeTripStartIsRecognized() = runBlocking {
        matcher.connectedDevicesProvider = { setOf("aa:bb:cc:dd:ee:11") }

        // Active trip begins with no vehicle assigned yet
        val trip = createActiveTrip("trip-preconnected", trackingMethod = TrackingMethod.MANUAL_GPS)
        activeTripRepository.activeTrip.value = trip

        val match = matcher.currentlyConnectedVehicle()
        assertTrue(match != null)
        activeTripRepository.assignVehicleIfAbsent(trip.id, match!!.id)

        assertEquals("vehicle-tesla", activeTripRepository.activeTrip.value?.vehicleId)
        val friendlyName = VehicleFriendlyNameResolver.resolve(
            activeTripRepository.activeTrip.value?.vehicleId,
            vehicleRepository.getVehicles()
        )
        assertEquals("Tesla Model Y", friendlyName)
    }

    // 3. Manual GPS trip with connected vehicle
    @Test
    fun manualGpsTripAssignsConnectedVehicle() = runBlocking {
        matcher.connectedDevicesProvider = { setOf(tesla.bluetoothAddress!!) }

        val manualTrip = createActiveTrip("manual-trip", trackingMethod = TrackingMethod.MANUAL_GPS)
        activeTripRepository.activeTrip.value = manualTrip

        val match = matcher.currentlyConnectedVehicle()
        if (manualTrip.vehicleId == null && match != null) {
            activeTripRepository.assignVehicleIfAbsent(manualTrip.id, match.id)
        }

        assertEquals("vehicle-tesla", activeTripRepository.activeTrip.value?.vehicleId)
    }

    // 4. Automatically detected trip with connected vehicle
    @Test
    fun autoDetectedTripAssignsConnectedVehicle() = runBlocking {
        matcher.connectedDevicesProvider = { setOf(tesla.bluetoothAddress!!) }

        val autoTrip = createActiveTrip("auto-trip", trackingMethod = TrackingMethod.AUTO_DETECTED)
        activeTripRepository.activeTrip.value = autoTrip

        val match = matcher.currentlyConnectedVehicle()
        if (autoTrip.vehicleId == null && match != null) {
            activeTripRepository.assignVehicleIfAbsent(autoTrip.id, match.id)
        }

        assertEquals("vehicle-tesla", activeTripRepository.activeTrip.value?.vehicleId)
    }

    // 5. Bluetooth connects after trip starts (delayed recognition via event handler)
    @Test
    fun bluetoothConnectsAfterTripStartsPopulatesVehicle() = runBlocking {
        val trip = createActiveTrip("trip-late-connect", vehicleId = null)
        activeTripRepository.activeTrip.value = trip

        // Device connects while trip is active
        eventHandler.handle(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED, tesla.bluetoothAddress!!)

        assertEquals("vehicle-tesla", activeTripRepository.activeTrip.value?.vehicleId)
    }

    // 6. Bluetooth disconnects during a trip (vehicle ID preserved)
    @Test
    fun bluetoothDisconnectDuringTripPreservesVehicleAssignment() = runBlocking {
        val trip = createActiveTrip("trip-disconnect", vehicleId = "vehicle-tesla")
        activeTripRepository.activeTrip.value = trip

        // Device disconnects while trip is active
        eventHandler.handle(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED, tesla.bluetoothAddress!!)

        assertEquals("vehicle-tesla", activeTripRepository.activeTrip.value?.vehicleId)
    }

    // 7. Saved vehicle without a Bluetooth association
    @Test
    fun savedVehicleWithoutBluetoothAssociationDoesNotMatchArbitraryDevice() = runBlocking {
        val unlinkedCar = VehicleEntity(
            id = "vehicle-unlinked",
            name = "Work Van",
            bluetoothAddress = null,
            bluetoothDeviceName = null,
            createdAt = 3000L,
            updatedAt = 3000L
        )
        vehicleRepository.save(unlinkedCar)

        matcher.connectedDevicesProvider = { setOf("99:88:77:66:55:44") }
        val match = matcher.currentlyConnectedVehicle()

        assertNull(match)
    }

    // 8. Multiple connected Bluetooth devices (unambiguous matching vs ambiguous)
    @Test
    fun multipleConnectedDevicesMatchesTargetVehicleIfOnlyOneMatchesSavedDirectory() = runBlocking {
        // Phone connected to car AND bluetooth headphones
        matcher.connectedDevicesProvider = {
            setOf("11:11:11:11:11:11", tesla.bluetoothAddress!!, "22:22:22:22:22:22")
        }

        val match = matcher.currentlyConnectedVehicle()
        assertEquals(tesla.id, match?.id)
    }

    @Test
    fun multipleConnectedDevicesAmbiguousMatchReturnsNull() = runBlocking {
        vehicleRepository.save(secondCar)

        // Both saved vehicles connected at the same time
        matcher.connectedDevicesProvider = {
            setOf(tesla.bluetoothAddress!!, secondCar.bluetoothAddress!!)
        }

        val match = matcher.currentlyConnectedVehicle()
        assertNull("Ambiguous match between multiple saved vehicles must return null", match)
    }

    // 9. Manual vehicle selection takes precedence over Bluetooth auto-attribution
    @Test
    fun manualVehicleSelectionTakesPrecedenceOverBluetoothMatch() = runBlocking {
        vehicleRepository.save(secondCar)
        matcher.connectedDevicesProvider = { setOf(tesla.bluetoothAddress!!) }

        // User explicitly picked Honda Civic for this active trip
        val trip = createActiveTrip("trip-manual-override", vehicleId = secondCar.id)
        activeTripRepository.activeTrip.value = trip

        val match = matcher.currentlyConnectedVehicle()
        // assignVehicleIfAbsent only updates if vehicleId == null
        if (trip.vehicleId == null && match != null) {
            activeTripRepository.assignVehicleIfAbsent(trip.id, match.id)
        }

        assertEquals(secondCar.id, activeTripRepository.activeTrip.value?.vehicleId)
    }

    // 10. Historical trips retain their saved vehicle association
    @Test
    fun historicalTripsRetainSavedVehicleAssociationIndependentlyOfCurrentBluetooth() = runBlocking {
        val historicTrip = createSampleTrip("historic-1", vehicleId = tesla.id)
        tripRepository.insertTrip(historicTrip)

        // Current Bluetooth is now disconnected or connected to something completely different
        matcher.connectedDevicesProvider = { setOf("99:99:99:99:99:99") }

        val persisted = tripRepository.getTripById("historic-1")
        assertEquals(tesla.id, persisted?.vehicleId)
        val displayName = VehicleFriendlyNameResolver.resolve(persisted?.vehicleId, vehicleRepository.getVehicles())
        assertEquals("Tesla Model Y", displayName)
    }

    private fun createActiveTrip(
        id: String,
        trackingMethod: TrackingMethod = TrackingMethod.MANUAL_GPS,
        vehicleId: String? = null
    ) = ActiveTripEntity(
        id = id,
        startTime = System.currentTimeMillis(),
        startLatitude = 37.25,
        startLongitude = -121.85,
        lastLatitude = 37.26,
        lastLongitude = -121.86,
        lastTimestamp = System.currentTimeMillis(),
        trackingMethod = trackingMethod.value,
        suggestedBusiness = "Test",
        totalDistanceMeters = 500.0,
        vehicleId = vehicleId
    )

    private fun createSampleTrip(
        id: String,
        vehicleId: String? = null
    ): TripEntity {
        return TripEntity(
            id = id,
            date = "2026-11-15",
            startTime = 1731670000000L,
            endTime = 1731671800000L,
            startAddress = "2392 Bailey Ave, San Jose, CA",
            endAddress = "470 North Santa Cruz Ave, Los Gatos, CA",
            startLatitude = 37.25,
            startLongitude = -121.85,
            endLatitude = 37.22,
            endLongitude = -121.98,
            totalDistanceMiles = 10.0,
            businessMiles = 10.0,
            businessEntity = "MySucculentCorner",
            purpose = "Delivery",
            trackingMethod = "MANUAL_GPS",
            classification = "BUSINESS",
            applicableRate = 0.760,
            calculatedDeduction = 7.60,
            notes = null,
            waypointsJson = "[]",
            syncStatus = "SYNCED",
            syncedAt = 1731671850000L,
            classificationSource = ClassificationSource.MANUAL.value,
            vehicleId = vehicleId
        )
    }

    private class FakeActiveTripRepository : ActiveTripRepository {
        val activeTrip = MutableStateFlow<ActiveTripEntity?>(null)

        override suspend fun getActiveTrip() = activeTrip.value
        override fun observeActiveTrip(): Flow<ActiveTripEntity?> = activeTrip
        override suspend fun createActiveTrip(
            tripId: String,
            latitude: Double,
            longitude: Double,
            trackingMethod: String,
            suggestedBusiness: String
        ): ActiveTripEntity {
            val trip = ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis(),
                startLatitude = latitude,
                startLongitude = longitude,
                lastLatitude = latitude,
                lastLongitude = longitude,
                lastTimestamp = System.currentTimeMillis(),
                trackingMethod = trackingMethod,
                suggestedBusiness = suggestedBusiness,
                totalDistanceMeters = 0.0
            )
            activeTrip.value = trip
            return trip
        }
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
        override fun observeWaypoints(tripId: String): Flow<List<Waypoint>> = flowOf(emptyList())
        override suspend fun clearActiveTrip(tripId: String) {
            if (activeTrip.value?.id == tripId) activeTrip.value = null
        }
        override suspend fun assignVehicleIfAbsent(tripId: String, vehicleId: String): Boolean {
            val current = activeTrip.value?.takeIf { it.id == tripId && it.vehicleId == null } ?: return false
            activeTrip.value = current.copy(vehicleId = vehicleId)
            return true
        }
    }

    private class FakeVehicleRepository : VehicleRepository {
        private val values = mutableListOf<VehicleEntity>()

        override fun observeVehicles(): Flow<List<VehicleEntity>> = flowOf(values.toList())
        override suspend fun getVehicles(): List<VehicleEntity> = values.toList()
        override suspend fun getVehicle(id: String): VehicleEntity? = values.firstOrNull { it.id == id }
        override suspend fun getVehicleForBluetoothAddress(address: String): VehicleEntity? =
            values.firstOrNull { it.bluetoothAddress.equals(address.trim(), ignoreCase = true) }
        override suspend fun save(vehicle: VehicleEntity) {
            values.removeAll { it.id == vehicle.id }
            values.add(vehicle)
        }
    }

    private class FakeTripRepository : TripRepository {
        private val _trips = MutableStateFlow<List<TripEntity>>(emptyList())

        override fun getAllTrips(): Flow<List<TripEntity>> = _trips.asStateFlow()
        override fun getUnclassifiedTrips(): Flow<List<TripEntity>> = flowOf(emptyList())
        override fun getUnclassifiedTripsCount(): Flow<Int> = flowOf(0)
        override fun getTripsByDateRange(startDate: String, endDate: String): Flow<List<TripEntity>> = flowOf(emptyList())
        override fun getTripsByBusinessEntity(entity: String): Flow<List<TripEntity>> = flowOf(emptyList())
        override fun getBusinessEntitySummaries() = flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
        override fun getBusinessEntitySummariesForDateRange(startDate: String, endDate: String) =
            flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
        override fun getTripCountByClassification() =
            flowOf(emptyList<com.businesstracker.mileage.data.local.model.ClassificationCount>())

        override suspend fun getTripById(tripId: String): TripEntity? =
            _trips.value.firstOrNull { it.id == tripId }

        override fun observeTripById(tripId: String): Flow<TripEntity?> =
            flowOf(_trips.value.firstOrNull { it.id == tripId })

        override suspend fun insertTrip(trip: TripEntity) {
            _trips.value = _trips.value.filterNot { it.id == trip.id } + trip
        }

        override suspend fun updateTrip(trip: TripEntity) {
            _trips.value = _trips.value.map { if (it.id == trip.id) trip else it }
        }

        override suspend fun deleteTrip(tripId: String) {
            _trips.value = _trips.value.filterNot { it.id == tripId }
        }

        override suspend fun updateTripDetails(
            tripId: String,
            classification: String,
            businessEntity: String,
            purpose: String,
            notes: String?,
            source: ClassificationSource
        ) = Unit

        override fun getTripsBySyncStatus(syncStatus: String): Flow<List<TripEntity>> = flowOf(emptyList())
        override fun getTripsByClassification(classification: String): Flow<List<TripEntity>> = flowOf(emptyList())
        override suspend fun getPendingSyncTrips(): List<TripEntity> = emptyList()
        override suspend fun markSynced(tripId: String, timestamp: Long) = Unit

        override suspend fun updateVehicleId(tripId: String, vehicleId: String?) {
            _trips.value = _trips.value.map { if (it.id == tripId) it.copy(vehicleId = vehicleId) else it }
        }
    }

    private class RecordingScheduler(context: Context) : TripDebounceScheduler(context) {
        var scheduledTripId: String? = null
        var canceled = false
        override fun scheduleForBluetoothDisconnect(tripId: String) { scheduledTripId = tripId }
        override fun cancel() { canceled = true }
    }
}
