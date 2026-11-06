package com.businesstracker.mileage.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.location.Location
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import com.businesstracker.mileage.data.local.entity.GpsInboxEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleState
import com.businesstracker.mileage.data.repository.ActiveTripRepositoryImpl
import com.businesstracker.mileage.domain.model.Waypoint
import com.businesstracker.mileage.domain.service.BootCountProvider
import com.businesstracker.mileage.domain.usecase.DrainPendingGpsFixesUseCase
import com.businesstracker.mileage.domain.usecase.FilterGpsPointUseCase
import com.businesstracker.mileage.domain.usecase.GpsInboxProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LocationTrackingServiceStateSyncTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var notificationManager: NotificationManager
    private lateinit var activeTripRepository: ActiveTripRepositoryImpl
    private lateinit var gpsInboxProcessor: GpsInboxProcessor
    private lateinit var drainPendingGpsFixesUseCase: DrainPendingGpsFixesUseCase
    private lateinit var service: LocationTrackingService

    private val bootCountProvider = object : BootCountProvider {
        override fun getBootCount(): Int = 1
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        notificationHelper = NotificationHelper(context)
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        activeTripRepository = ActiveTripRepositoryImpl(database.activeTripDao(), Dispatchers.Default)

        gpsInboxProcessor = GpsInboxProcessor(
            database = database,
            activeTripDao = database.activeTripDao(),
            gpsInboxDao = database.gpsInboxDao(),
            filterGpsPointUseCase = FilterGpsPointUseCase(bootCountProvider)
        )

        drainPendingGpsFixesUseCase = DrainPendingGpsFixesUseCase(
            gpsInboxDao = database.gpsInboxDao(),
            gpsInboxProcessor = gpsInboxProcessor
        )

        LocationTrackingService.resetGuardForTesting()
        service = LocationTrackingService()
        service.setTestDependencies(
            activeTripRepo = activeTripRepository,
            helper = notificationHelper,
            tripId = "test-trip"
        )
    }

    @After
    fun tearDown() {
        LocationTrackingService.resetGuardForTesting()
        database.close()
    }

    private fun createLocation(
        lat: Double,
        lng: Double,
        timeMs: Long,
        elapsedNanos: Long = timeMs * 1_000_000L,
        acc: Float = 5.0f,
        spd: Float = 10.0f
    ): Location {
        return Location("gps").apply {
            latitude = lat
            longitude = lng
            time = timeMs
            elapsedRealtimeNanos = elapsedNanos
            accuracy = acc
            speed = spd
        }
    }

    // REGRESSION TEST 1: Production queue processing updates live tracking state after an accepted fix
    @Test
    fun productionQueueProcessing_updatesLiveTrackingState_afterAcceptedFix() = runBlocking {
        val tripId = "trip-live-sync-1"
        activeTripRepository.createActiveTrip(tripId, 37.3382, -121.8863, "AUTO_DETECTED", "MySucculentCorner")

        service.setTrackingStateForTesting(
            tripId = tripId,
            distanceMeters = 0.0,
            waypoints = 0
        )

        val queue = GpsLocationQueue(
            scope = this,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = database.gpsInboxDao(),
            tripIdProvider = { tripId },
            batchFetchLimit = 10,
            drainPendingGpsFixesUseCase = drainPendingGpsFixesUseCase,
            tripSequenceDao = database.tripSequenceDao(),
            database = database,
            bootCountProvider = bootCountProvider,
            onFixProcessed = { result ->
                service.handleFixProcessed(result)
            }
        )

        // Enqueue valid fix: moved to 37.3400 (~200m north)
        val loc = createLocation(37.3400, -121.8863, 10_000L, spd = 20.0f)
        queue.enqueueBatchSync(listOf(loc))
        queue.drainAndClose()

        // 1. Assert service in-memory state updated
        val state = LocationTrackingService.serviceState.value
        assertTrue("Service state must indicate active tracking", state.isTracking)
        assertTrue("Distance meters must be greater than 0 after accepted fix", state.distanceMeters > 0.0)
        assertTrue("Distance miles must be greater than 0 after accepted fix", state.distanceMiles > 0.0)
        assertEquals("Waypoint count must be 1 after single accepted fix", 1, state.waypointCount)
        assertTrue("Speed in mph must reflect accepted speed", state.currentSpeedMph > 0.0)
        assertNotNull("Last waypoint must be populated", state.lastWaypoint)
        assertEquals(37.3400, state.lastWaypoint!!.latitude, 0.0001)

        // 2. Assert database progress matches service in-memory state
        val persistedTrip = database.activeTripDao().getActiveTripById(tripId)
        assertNotNull(persistedTrip)
        assertEquals("In-memory distance must match authoritative Room distance",
            persistedTrip!!.totalDistanceMeters, state.distanceMeters, 0.001)

        // 3. Assert notification was posted with the updated distance
        val activeNotifs = notificationManager.activeNotifications
        val trackingNotif = activeNotifs.find { it.id == NotificationHelper.NOTIFICATION_TRACKING_ID }
        assertNotNull("Tracking notification must be active", trackingNotif)
    }

    // REGRESSION TEST 2: Rejected fixes do not increase mileage or waypoint count
    @Test
    fun rejectedFixes_doNotIncreaseMileageOrWaypointCount() = runBlocking {
        val tripId = "trip-rejected-fix"
        activeTripRepository.createActiveTrip(tripId, 37.3382, -121.8863, "AUTO_DETECTED", "MySucculentCorner")

        // Baseline: trip already has 100.0m and 1 waypoint
        service.setTrackingStateForTesting(
            tripId = tripId,
            distanceMeters = 100.0,
            waypoints = 1,
            lastWaypoint = Waypoint(37.3382, -121.8863, timestamp = 1000L)
        )

        val queue = GpsLocationQueue(
            scope = this,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = database.gpsInboxDao(),
            tripIdProvider = { tripId },
            batchFetchLimit = 10,
            drainPendingGpsFixesUseCase = drainPendingGpsFixesUseCase,
            tripSequenceDao = database.tripSequenceDao(),
            database = database,
            bootCountProvider = bootCountProvider,
            onFixProcessed = { result ->
                service.handleFixProcessed(result)
            }
        )

        // Enqueue inaccurate fix (accuracy = 150m > 50m max threshold)
        val inaccurateLoc = createLocation(37.3400, -121.8863, 10_000L, acc = 150.0f)
        queue.enqueueBatchSync(listOf(inaccurateLoc))
        queue.drainAndClose()

        // Mileage and waypoint count must NOT have increased
        val state = LocationTrackingService.serviceState.value
        assertEquals("Distance must remain 100.0m after rejected fix", 100.0, state.distanceMeters, 0.001)
        assertEquals("Waypoint count must remain 1 after rejected fix", 1, state.waypointCount)

        // Fix must be acknowledged and removed from inbox
        assertEquals(0, database.gpsInboxDao().getPendingCount(tripId))
    }

    // REGRESSION TEST 3: AlreadyConsumed fixes do not double-count mileage
    @Test
    fun alreadyConsumedFixes_doNotDoubleCountMileage() = runBlocking {
        val tripId = "trip-already-consumed"
        val existingTrip = activeTripRepository.createActiveTrip(tripId, 37.3382, -121.8863, "AUTO_DETECTED", "MySucculentCorner")
        database.activeTripDao().updateActiveTripProgress(tripId, 500.0, 37.3450, -121.8863, 5000L)

        // Record waypoint with sourceFixId = 42
        database.activeTripDao().insertWaypoint(
            ActiveWaypointEntity(
                tripId = tripId,
                sourceFixId = 42L,
                sequenceNumber = 1L,
                latitude = 37.3450,
                longitude = -121.8863,
                accuracy = 5f,
                speed = 10f,
                timestamp = 5000L
            )
        )

        service.setTrackingStateForTesting(
            tripId = tripId,
            distanceMeters = 500.0,
            waypoints = 1,
            lastWaypoint = Waypoint(37.3450, -121.8863, timestamp = 5000L)
        )

        // Insert fix with ID 42 into inbox (simulating unacknowledged fix that was already committed)
        database.gpsInboxDao().insertFix(
            GpsInboxEntity(
                id = 42L,
                tripId = tripId,
                sequenceNumber = 1L,
                elapsedRealtimeNanos = 5000L * 1_000_000L,
                latitude = 37.3450,
                longitude = -121.8863,
                timestamp = 5000L
            )
        )

        // Drain using use case
        drainPendingGpsFixesUseCase.drain(tripId) { result ->
            service.handleFixProcessed(result)
        }

        // Distance must remain 500.0m (NOT double-counted to 1000m)
        val state = LocationTrackingService.serviceState.value
        assertEquals("Mileage must not be double counted on AlreadyConsumed fix", 500.0, state.distanceMeters, 0.001)
        assertEquals("Waypoint count must not increment on AlreadyConsumed fix", 1, state.waypointCount)
        assertEquals(0, database.gpsInboxDao().getPendingCount(tripId))
    }

    // REGRESSION TEST 4: Recovery does not double-count previously persisted waypoints
    @Test
    fun recovery_doesNotDoubleCountPreviouslyPersistedWaypoints() = runBlocking {
        val tripId = "trip-recovery-test"
        // 1. Populate database with 2 previously recovered waypoints totaling 800m
        activeTripRepository.createActiveTrip(tripId, 37.3382, -121.8863, "AUTO_DETECTED", "MySucculentCorner")
        database.activeTripDao().insertWaypoint(
            ActiveWaypointEntity(tripId = tripId, sequenceNumber = 1, latitude = 37.3382, longitude = -121.8863, accuracy = 5f, speed = 10f, timestamp = 1000L)
        )
        database.activeTripDao().insertWaypoint(
            ActiveWaypointEntity(tripId = tripId, sequenceNumber = 2, latitude = 37.3450, longitude = -121.8863, accuracy = 5f, speed = 10f, timestamp = 2000L)
        )
        database.activeTripDao().updateActiveTripProgress(tripId, 800.0, 37.3450, -121.8863, 2000L)

        // 2. Service recovers state on startup
        service.setTrackingStateForTesting(
            tripId = tripId,
            distanceMeters = 800.0,
            waypoints = 2,
            lastWaypoint = Waypoint(37.3450, -121.8863, timestamp = 2000L),
            anchor = Waypoint(37.3382, -121.8863, timestamp = 1000L),
            displacementConfirmed = true
        )

        val queue = GpsLocationQueue(
            scope = this,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = database.gpsInboxDao(),
            tripIdProvider = { tripId },
            batchFetchLimit = 10,
            drainPendingGpsFixesUseCase = drainPendingGpsFixesUseCase,
            tripSequenceDao = database.tripSequenceDao(),
            database = database,
            bootCountProvider = bootCountProvider,
            onFixProcessed = { result ->
                service.handleFixProcessed(result)
            }
        )

        // 3. New valid fix arrives: moved further north
        val newLoc = createLocation(37.3500, -121.8863, 15_000L, spd = 15.0f)
        queue.enqueueBatchSync(listOf(newLoc))
        queue.drainAndClose()

        // 4. Assert total distance reflects Room update without double-counting recovered 800m
        val state = LocationTrackingService.serviceState.value
        val dbTrip = database.activeTripDao().getActiveTripById(tripId)!!
        assertEquals("In-memory distance must match authoritative Room distance", dbTrip.totalDistanceMeters, state.distanceMeters, 0.001)
        assertTrue("Distance must be greater than previous 800m", state.distanceMeters > 800.0)
        assertTrue("Distance must not be double counted (must be less than 1600m)", state.distanceMeters < 1600.0)
        assertEquals("Waypoint count must be 3 (2 recovered + 1 new)", 3, state.waypointCount)
    }

    // REGRESSION TEST 5: Notification and service state reflects latest persisted distance
    @Test
    fun notificationAndServiceState_reflectsLatestPersistedDistance() = runBlocking {
        val tripId = "trip-notif-sync"
        activeTripRepository.createActiveTrip(tripId, 37.3382, -121.8863, "AUTO_DETECTED", "MySucculentCorner")

        service.setTrackingStateForTesting(
            tripId = tripId,
            distanceMeters = 0.0,
            waypoints = 0
        )

        val queue = GpsLocationQueue(
            scope = this,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = database.gpsInboxDao(),
            tripIdProvider = { tripId },
            batchFetchLimit = 10,
            drainPendingGpsFixesUseCase = drainPendingGpsFixesUseCase,
            tripSequenceDao = database.tripSequenceDao(),
            database = database,
            bootCountProvider = bootCountProvider,
            onFixProcessed = { result ->
                service.handleFixProcessed(result)
            }
        )

        // Enqueue two consecutive fixes
        val loc1 = createLocation(37.3400, -121.8863, 10_000L, spd = 20.0f)
        val loc2 = createLocation(37.3450, -121.8863, 20_000L, spd = 20.0f)
        queue.enqueueBatchSync(listOf(loc1, loc2))
        queue.drainAndClose()

        val state = LocationTrackingService.serviceState.value
        val dbTrip = database.activeTripDao().getActiveTripById(tripId)!!

        assertEquals(dbTrip.totalDistanceMeters, state.distanceMeters, 0.001)
        val expectedMiles = state.distanceMeters / 1609.344
        assertEquals(expectedMiles, state.distanceMiles, 0.001)

        val activeNotifs = notificationManager.activeNotifications
        val trackingNotif = activeNotifs.find { it.id == NotificationHelper.NOTIFICATION_TRACKING_ID }
        assertNotNull(trackingNotif)
    }

    // REGRESSION TEST 6: Stale orphan fixes do not update active trip tracking state
    @Test
    fun staleOrphanFix_doesNotUpdateActiveTripTrackingState() = runBlocking {
        val activeTripId = "trip-active-now"
        val deadTripId = "trip-dead-orphan"

        activeTripRepository.createActiveTrip(activeTripId, 37.3382, -121.8863, "AUTO_DETECTED", "MySucculentCorner")
        service.setTrackingStateForTesting(
            tripId = activeTripId,
            distanceMeters = 300.0,
            waypoints = 2
        )

        // Insert terminal lifecycle for dead trip so GpsInboxProcessor acknowledges it as StaleOrphanAcknowledged
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(tripId = deadTripId, state = TripLifecycleState.FINALIZED.value)
        )
        database.gpsInboxDao().insertFix(
            GpsInboxEntity(
                id = 88L,
                tripId = deadTripId,
                sequenceNumber = 1L,
                latitude = 37.5,
                longitude = -122.0,
                timestamp = 9999L
            )
        )

        // Drain the dead trip fixes
        drainPendingGpsFixesUseCase.drain(deadTripId) { result ->
            service.handleFixProcessed(result)
        }

        // Active trip state must remain completely unaffected
        val state = LocationTrackingService.serviceState.value
        assertEquals("Active trip distance must not change on stale orphan processing", 300.0, state.distanceMeters, 0.001)
        assertEquals("Active trip waypoint count must not change on stale orphan processing", 2, state.waypointCount)
        assertEquals(0, database.gpsInboxDao().getPendingCount(deadTripId))
    }

    // REGRESSION TEST 7: Existing transactional processing and queue shutdown behavior remain intact
    @Test
    fun queueShutdown_andTransactionalProcessing_remainIntact() = runBlocking {
        val tripId = "trip-shutdown-intact"
        activeTripRepository.createActiveTrip(tripId, 37.3382, -121.8863, "AUTO_DETECTED", "MySucculentCorner")
        service.setTrackingStateForTesting(tripId = tripId, distanceMeters = 0.0, waypoints = 0)

        val queue = GpsLocationQueue(
            scope = this,
            dispatcher = Dispatchers.Default,
            gpsInboxDao = database.gpsInboxDao(),
            tripIdProvider = { tripId },
            batchFetchLimit = 10,
            drainPendingGpsFixesUseCase = drainPendingGpsFixesUseCase,
            tripSequenceDao = database.tripSequenceDao(),
            database = database,
            bootCountProvider = bootCountProvider,
            onFixProcessed = { result ->
                service.handleFixProcessed(result)
            }
        )

        val batch = (1..5).map {
            createLocation(37.3382 + (it * 0.002), -121.8863, it * 5000L, spd = 15.0f)
        }
        queue.enqueueBatch(batch)
        queue.drainAndClose()

        // 1. All fixes deleted from inbox
        assertEquals(0, database.gpsInboxDao().getPendingCount(tripId))

        // 2. All waypoints stored in Room
        val waypoints = database.activeTripDao().getWaypointsForTrip(tripId)
        assertEquals(5, waypoints.size)

        // 3. Service state fully synchronized
        val state = LocationTrackingService.serviceState.value
        assertEquals(5, state.waypointCount)
        assertTrue(state.distanceMeters > 0.0)
    }
}
