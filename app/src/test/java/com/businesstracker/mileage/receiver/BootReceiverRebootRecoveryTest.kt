package com.businesstracker.mileage.receiver

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.GpsInboxEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleState
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.repository.ActiveTripRepositoryImpl
import com.businesstracker.mileage.transition.ActivityTransitionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BootReceiverRebootRecoveryTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var activeTripRepository: ActiveTripRepositoryImpl
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var bootReceiver: BootReceiver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activeTripRepository = ActiveTripRepositoryImpl(database.activeTripDao(), Dispatchers.Main)
        preferencesManager = TrackingPreferencesManager(context)

        bootReceiver = BootReceiver()
        bootReceiver.setTestDependencies(
            transitionManager = ActivityTransitionManager(context),
            prefs = preferencesManager,
            activeTripRepo = activeTripRepository,
            lifecycleDao = database.tripLifecycleDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun rebootWithActiveTrip_marksLifecycleRecoveryRequiredWithoutDiscardingData() = runBlocking {
        val tripId = "reboot-trip-123"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis() - 30 * 60 * 1000L,
                startLatitude = 37.77,
                startLongitude = -122.41,
                lastLatitude = 37.80,
                lastLongitude = -122.45,
                lastTimestamp = System.currentTimeMillis() - 25 * 60 * 1000L,
                totalDistanceMeters = 8500.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(
                tripId = tripId,
                state = TripLifecycleState.TRACKING.value
            )
        )

        // Simulate boot reconciliation
        bootReceiver.reconcileBootActiveTrip()

        // Verify active trip is completely preserved
        val tripInDb = database.activeTripDao().getActiveTripById(tripId)
        assertNotNull("Active trip must NOT be deleted on reboot", tripInDb)
        assertEquals(8500.0, tripInDb!!.totalDistanceMeters, 0.001)

        // Verify authoritative state transitioned to RECOVERY_REQUIRED
        val lifecycle = database.tripLifecycleDao().getLifecycle(tripId)
        assertNotNull(lifecycle)
        assertEquals(TripLifecycleState.RECOVERY_REQUIRED.value, lifecycle!!.state)
        assertEquals("Interrupted by device reboot; awaiting user review or resumption", lifecycle.reason)
    }

    @Test
    fun rebootWithPendingGpsInboxRecords_preservesAllPendingFixes() = runBlocking {
        val tripId = "reboot-inbox-trip"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 1000L,
                startLatitude = 37.0,
                startLongitude = -122.0,
                lastLatitude = 37.1,
                lastLongitude = -122.1,
                lastTimestamp = 2000L,
                totalDistanceMeters = 2500.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )
        // Add pending inbox fixes
        database.gpsInboxDao().insertFixes(
            listOf(
                GpsInboxEntity(id = 1, tripId = tripId, sequenceNumber = 1, latitude = 37.05, longitude = -122.05, timestamp = 1500L),
                GpsInboxEntity(id = 2, tripId = tripId, sequenceNumber = 2, latitude = 37.10, longitude = -122.10, timestamp = 2000L)
            )
        )

        bootReceiver.reconcileBootActiveTrip()

        // Verify inbox records are untouched
        assertEquals(2, database.gpsInboxDao().getPendingCount(tripId))
    }

    @Test
    fun rebootWithNoActiveTrip_succeedsSilently() = runBlocking {
        bootReceiver.reconcileBootActiveTrip()
        assertNull(database.activeTripDao().getActiveTrip())
    }

    @Test
    fun repeatedBootReconciliation_isStrictlyIdempotent() = runBlocking {
        val tripId = "idempotent-reboot-trip"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 5000L,
                startLatitude = 37.0,
                startLongitude = -122.0,
                lastLatitude = 37.1,
                lastLongitude = -122.1,
                lastTimestamp = 6000L,
                totalDistanceMeters = 4000.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(
                tripId = tripId,
                state = TripLifecycleState.TRACKING.value
            )
        )

        // First boot broadcast
        bootReceiver.reconcileBootActiveTrip()
        val firstState = database.tripLifecycleDao().getState(tripId)
        assertEquals(TripLifecycleState.RECOVERY_REQUIRED.value, firstState)

        // Second boot broadcast (e.g. PACKAGE_REPLACED after BOOT_COMPLETED)
        bootReceiver.reconcileBootActiveTrip()
        val secondState = database.tripLifecycleDao().getState(tripId)
        assertEquals(TripLifecycleState.RECOVERY_REQUIRED.value, secondState)

        // Data remains completely unharmed
        assertEquals(4000.0, database.activeTripDao().getActiveTripById(tripId)!!.totalDistanceMeters, 0.001)
    }

    @Test
    fun onReceive_bootCompleted_invokesReconciliationAndTransitionsToRecoveryRequired() = runBlocking {
        val tripId = "broadcast-reboot-trip"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis() - 30 * 60 * 1000L,
                startLatitude = 37.77,
                startLongitude = -122.41,
                lastLatitude = 37.80,
                lastLongitude = -122.45,
                lastTimestamp = System.currentTimeMillis() - 25 * 60 * 1000L,
                totalDistanceMeters = 8500.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(
                tripId = tripId,
                state = TripLifecycleState.TRACKING.value
            )
        )

        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        bootReceiver.setTestDependencies(
            transitionManager = ActivityTransitionManager(context),
            prefs = preferencesManager,
            activeTripRepo = activeTripRepository,
            lifecycleDao = database.tripLifecycleDao(),
            scope = testScope
        )

        // Invoke broadcast receiver entry point directly
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        bootReceiver.onReceive(context, bootIntent)

        // Verify active trip is completely preserved
        val tripInDb = database.activeTripDao().getActiveTripById(tripId)
        assertNotNull("Active trip must NOT be deleted on reboot", tripInDb)
        assertEquals(8500.0, tripInDb!!.totalDistanceMeters, 0.001)

        // Verify authoritative state transitioned to RECOVERY_REQUIRED
        val lifecycle = database.tripLifecycleDao().getLifecycle(tripId)
        assertNotNull(lifecycle)
        assertEquals(TripLifecycleState.RECOVERY_REQUIRED.value, lifecycle!!.state)
    }

    @Test
    fun onReceive_bootCompleted_withPendingGpsInboxFixes_preservesFixesIntact() = runBlocking {
        val tripId = "broadcast-inbox-trip"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 1000L,
                startLatitude = 37.0,
                startLongitude = -122.0,
                lastLatitude = 37.1,
                lastLongitude = -122.1,
                lastTimestamp = 2000L,
                totalDistanceMeters = 2500.0,
                trackingMethod = "AUTO_DETECTED"
            )
        )
        database.gpsInboxDao().insertFixes(
            listOf(
                GpsInboxEntity(id = 1, tripId = tripId, sequenceNumber = 1, latitude = 37.05, longitude = -122.05, timestamp = 1500L),
                GpsInboxEntity(id = 2, tripId = tripId, sequenceNumber = 2, latitude = 37.10, longitude = -122.10, timestamp = 2000L)
            )
        )

        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        bootReceiver.setTestDependencies(
            transitionManager = ActivityTransitionManager(context),
            prefs = preferencesManager,
            activeTripRepo = activeTripRepository,
            lifecycleDao = database.tripLifecycleDao(),
            scope = testScope
        )

        bootReceiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        // Verify inbox records are untouched
        assertEquals(2, database.gpsInboxDao().getPendingCount(tripId))
        assertEquals(2500.0, database.activeTripDao().getActiveTripById(tripId)!!.totalDistanceMeters, 0.001)
    }
}
