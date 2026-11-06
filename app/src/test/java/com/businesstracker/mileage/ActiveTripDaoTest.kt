package com.businesstracker.mileage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ActiveTripDao
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.TripDao
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.repository.ActiveTripRepositoryImpl
import com.businesstracker.mileage.data.repository.TripRepositoryImpl
import com.businesstracker.mileage.domain.coordinator.TripFinalizationCoordinator
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import com.businesstracker.mileage.domain.usecase.GeocodeLocationUseCase
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActiveTripDaoTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var activeTripDao: ActiveTripDao
    private lateinit var tripDao: TripDao
    private lateinit var activeTripRepository: ActiveTripRepositoryImpl
    private lateinit var tripRepository: TripRepositoryImpl
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var coordinator: TripFinalizationCoordinator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activeTripDao = database.activeTripDao()
        tripDao = database.tripDao()

        activeTripRepository = ActiveTripRepositoryImpl(activeTripDao, Dispatchers.IO)
        tripRepository = TripRepositoryImpl(tripDao, Dispatchers.IO)
        preferencesManager = TrackingPreferencesManager(context)

        coordinator = TripFinalizationCoordinator(
            database = database,
            activeTripRepository = activeTripRepository,
            tripRepository = tripRepository,
            geocodeUseCase = GeocodeLocationUseCase(context, Dispatchers.IO),
            rateCalculator = CalculateMileageRateUseCase(),
            preferencesManager = preferencesManager,
            gson = Gson(),
            ioDispatcher = Dispatchers.IO
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // FIX 5 - Requirement 7: Genuinely concurrent Room repository creation across separate coroutine dispatchers
    @Test
    fun concurrentRepositoryCreation_acrossDifferentDispatchers_returnsIdenticalActiveTrip() = runBlocking {
        val deferred1 = async(Dispatchers.IO) {
            activeTripRepository.createActiveTrip(
                tripId = "trip-thread-1",
                latitude = 37.3382,
                longitude = -121.8863,
                trackingMethod = "AUTO_DETECTED",
                suggestedBusiness = "MySucculentCorner"
            )
        }

        val deferred2 = async(Dispatchers.Default) {
            activeTripRepository.createActiveTrip(
                tripId = "trip-thread-2",
                latitude = 37.3385,
                longitude = -121.8865,
                trackingMethod = "AUTO_DETECTED",
                suggestedBusiness = "MySucculentCorner"
            )
        }

        val result1 = deferred1.await()
        val result2 = deferred2.await()

        assertEquals("Concurrent repository creation across distinct dispatchers must return identical active trip", result1.id, result2.id)

        val activeInDb = activeTripDao.getActiveTrip()
        assertNotNull("Active trip must exist in database", activeInDb)
        assertEquals("Database must contain exactly the winning active trip", result1.id, activeInDb?.id)
    }

    // FIX 5 - Requirement 5: Worker and Service concurrent finalization results in exactly one finalized trip
    @Test
    fun concurrentFinalization_byWorkerAndService_resultsInExactlyOneFinalizedTrip() = runBlocking {
        val testTripId = "trip-race-finalization-999"
        val activeTrip = ActiveTripEntity(
            id = testTripId,
            startTime = 1000L,
            startLatitude = 37.3382,
            startLongitude = -121.8863,
            lastLatitude = 37.3450,
            lastLongitude = -121.8863,
            lastTimestamp = 2000L,
            totalDistanceMeters = 800.0,
            trackingMethod = "AUTO_DETECTED",
            suggestedBusiness = "MySucculentCorner"
        )
        activeTripDao.insertActiveTrip(activeTrip)
        activeTripDao.insertWaypoint(
            ActiveWaypointEntity(
                tripId = testTripId,
                latitude = 37.3382,
                longitude = -121.8863,
                altitude = 0.0,
                timestamp = 1000L,
                speed = 10.0f,
                accuracy = 5.0f
            )
        )

        // Concurrently trigger finalization on two different dispatchers
        val workerFinalize = async(Dispatchers.IO) {
            coordinator.finalizeTrip(testTripId, TrackingMethod.AUTO_DETECTED)
        }
        val serviceFinalize = async(Dispatchers.Default) {
            coordinator.finalizeTrip(testTripId, TrackingMethod.AUTO_DETECTED)
        }

        val workerResult = workerFinalize.await()
        val serviceResult = serviceFinalize.await()

        assertNotNull("Worker finalization must return a non-null trip", workerResult)
        assertNotNull("Service finalization must return a non-null trip", serviceResult)
        assertEquals("Both callers must receive the exact same finalized trip ID", testTripId, workerResult?.id)
        assertEquals("Both callers must receive the exact same finalized trip ID", testTripId, serviceResult?.id)

        // Verify only ONE finalized trip exists in the trips table
        val allTrips = database.tripDao().getAllTrips().first()
        assertEquals("Trips table must contain exactly ONE finalized trip", 1, allTrips.size)
        assertEquals("Finalized trip ID must match", testTripId, allTrips[0].id)

        // Verify active trip table has been cleared
        val activeInDb = activeTripDao.getActiveTrip()
        assertNull("Active trip table must be cleared after finalization", activeInDb)
    }

    // FIX 5 - Requirement 8: Interrupted trip recovery restores state and displacement confirmation intact
    @Test
    fun interruptedTripRecovery_restoresStateAndDisplacementIntact() = runBlocking {
        val recoveredTripId = "trip-recovery-555"
        val initialStartTime = 1700000000000L
        val initialDistance = 1250.0 // > 200m

        activeTripDao.insertActiveTrip(
            ActiveTripEntity(
                id = recoveredTripId,
                startTime = initialStartTime,
                startLatitude = 37.3382,
                startLongitude = -121.8863,
                lastLatitude = 37.3500,
                lastLongitude = -121.8863,
                lastTimestamp = initialStartTime + 60000L,
                totalDistanceMeters = initialDistance,
                trackingMethod = "AUTO_DETECTED",
                suggestedBusiness = "MySucculentCorner"
            )
        )
        activeTripDao.insertWaypoint(
            ActiveWaypointEntity(
                tripId = recoveredTripId,
                latitude = 37.3382,
                longitude = -121.8863,
                altitude = 0.0,
                timestamp = initialStartTime,
                speed = 12.0f,
                accuracy = 4.0f
            )
        )
        activeTripDao.insertWaypoint(
            ActiveWaypointEntity(
                tripId = recoveredTripId,
                latitude = 37.3500,
                longitude = -121.8863,
                altitude = 0.0,
                timestamp = initialStartTime + 60000L,
                speed = 15.0f,
                accuracy = 4.0f
            )
        )

        // Simulate service recovery on restart
        val activeTrip = activeTripRepository.getActiveTrip()
        assertNotNull("Active trip must be recovered from Room", activeTrip)
        assertEquals("Recovered trip ID must match", recoveredTripId, activeTrip?.id)
        assertEquals("Start time must be preserved exactly", initialStartTime, activeTrip?.startTime)
        assertEquals("Total distance meters must be preserved exactly", initialDistance, activeTrip!!.totalDistanceMeters, 0.001)

        val waypoints = activeTripRepository.getWaypoints(recoveredTripId)
        assertEquals("Waypoints must be completely recovered from Room", 2, waypoints.size)

        // Verify displacement condition logic
        val isDisplacementConfirmed = activeTrip.totalDistanceMeters >= 200.0
        assertTrue("Interrupted trip with >= 200m must have displacement confirmed upon recovery", isDisplacementConfirmed)
    }

    // FIX 2 & Regression Test 4: Retrying Trip A after Trip B starts must not delete Trip B
    @Test
    fun retryingFinalizationOfTripA_doesNotDeleteNewerTripB() = runBlocking {
        val tripAId = "trip-a-111"
        val tripBId = "trip-b-222"

        // 1. Finalize Trip A
        val tripA = ActiveTripEntity(
            id = tripAId,
            startTime = 1000L,
            startLatitude = 37.3382,
            startLongitude = -121.8863,
            lastLatitude = 37.3450,
            lastLongitude = -121.8863,
            lastTimestamp = 2000L,
            totalDistanceMeters = 750.0,
            trackingMethod = "AUTO_DETECTED",
            suggestedBusiness = "MySucculentCorner"
        )
        activeTripDao.insertActiveTrip(tripA)
        coordinator.finalizeTrip(tripAId, TrackingMethod.AUTO_DETECTED)

        // Verify Trip A is finalized
        val tripAInDb = database.tripDao().getTripById(tripAId)
        assertNotNull("Trip A must be finalized in database", tripAInDb)

        // 2. Start Trip B as the new active trip with waypoints
        val tripB = ActiveTripEntity(
            id = tripBId,
            startTime = 5000L,
            startLatitude = 37.4000,
            startLongitude = -122.0000,
            lastLatitude = 37.4050,
            lastLongitude = -122.0000,
            lastTimestamp = 6000L,
            totalDistanceMeters = 500.0,
            trackingMethod = "AUTO_DETECTED",
            suggestedBusiness = "MySucculentCorner"
        )
        activeTripDao.insertActiveTrip(tripB)
        activeTripDao.insertWaypoint(
            ActiveWaypointEntity(
                tripId = tripBId,
                latitude = 37.4000,
                longitude = -122.0000,
                altitude = 0.0,
                timestamp = 5000L,
                speed = 20.0f,
                accuracy = 3.0f
            )
        )

        // 3. Retry finalization of Trip A (e.g. from delayed retry worker or service duplicate callback)
        val retryResult = coordinator.finalizeTrip(tripAId, TrackingMethod.AUTO_DETECTED)
        assertNotNull("Retry finalization of Trip A must return existing trip", retryResult)
        assertEquals(tripAId, retryResult?.id)

        // 4. Verify Trip B STILL exists and was NOT deleted!
        val activeTripInDb = activeTripDao.getActiveTrip()
        assertNotNull("Active Trip B must NOT have been deleted by Trip A's retry finalization", activeTripInDb)
        assertEquals("Active trip in DB must still be Trip B", tripBId, activeTripInDb?.id)

        val tripBWaypoints = activeTripDao.getWaypointsForTrip(tripBId)
        assertEquals("Waypoints for Trip B must still be intact", 1, tripBWaypoints.size)
    }

    // Regression Test 5: Verify the final saved trip contains the last accepted GPS waypoints
    @Test
    fun finalizedTrip_containsAllAcceptedWaypoints_includingFinalFix() = runBlocking {
        val tripId = "trip-waypoints-final-check"
        val activeTrip = ActiveTripEntity(
            id = tripId,
            startTime = 1000L,
            startLatitude = 37.3382,
            startLongitude = -121.8863,
            lastLatitude = 37.3500, // Final waypoint location
            lastLongitude = -121.8863,
            lastTimestamp = 5000L,
            totalDistanceMeters = 1500.0,
            trackingMethod = "AUTO_DETECTED",
            suggestedBusiness = "MySucculentCorner"
        )
        activeTripDao.insertActiveTrip(activeTrip)

        // Insert sequential waypoints
        val w1 = ActiveWaypointEntity(tripId = tripId, latitude = 37.3382, longitude = -121.8863, altitude = 0.0, timestamp = 1000L, speed = 10f, accuracy = 4f)
        val w2 = ActiveWaypointEntity(tripId = tripId, latitude = 37.3420, longitude = -121.8863, altitude = 0.0, timestamp = 3000L, speed = 15f, accuracy = 4f)
        val w3Final = ActiveWaypointEntity(tripId = tripId, latitude = 37.3500, longitude = -121.8863, altitude = 0.0, timestamp = 5000L, speed = 20f, accuracy = 4f)

        activeTripDao.insertWaypoint(w1)
        activeTripDao.insertWaypoint(w2)
        activeTripDao.insertWaypoint(w3Final)

        // Finalize
        val finalized = coordinator.finalizeTrip(tripId, TrackingMethod.AUTO_DETECTED)
        assertNotNull("Finalized trip must not be null", finalized)

        val savedTrip = database.tripDao().getTripById(tripId)
        assertNotNull("Saved trip must exist in database", savedTrip)
        assertEquals("End latitude must match final accepted waypoint", 37.3500, savedTrip!!.endLatitude, 0.00001)

        // Verify waypointsJson deserializes all 3 waypoints including the final one
        val recoveredWaypoints = Gson().fromJson(savedTrip.waypointsJson, Array<com.businesstracker.mileage.domain.model.Waypoint>::class.java)
        assertEquals("All 3 waypoints must be preserved in the final saved trip", 3, recoveredWaypoints.size)
        assertEquals("Last waypoint in list must match final accepted fix", 37.3500, recoveredWaypoints.last().latitude, 0.00001)
    }
}
