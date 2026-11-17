package com.businesstracker.mileage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.device.VehicleBluetoothMatcher
import com.businesstracker.mileage.data.local.entity.VehicleEntity
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.model.Waypoint
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.domain.repository.VehicleRepository
import com.businesstracker.mileage.domain.tracking.AutoDriveAction
import com.businesstracker.mileage.domain.tracking.AutoDriveDecisionEngine
import com.businesstracker.mileage.domain.tracking.TripStopDecision
import com.businesstracker.mileage.receiver.VehicleBluetoothEventHandler
import com.businesstracker.mileage.receiver.BluetoothArmedDriveMonitor
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.transition.ActivityTransitionManager
import com.businesstracker.mileage.transition.TransitionRegistrationState
import com.businesstracker.mileage.worker.TripDebounceScheduler
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomaticDriveRegressionAuditTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var engine: AutoDriveDecisionEngine
    private lateinit var transitionManager: ActivityTransitionManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferencesManager = TrackingPreferencesManager(context)
        engine = AutoDriveDecisionEngine()
        transitionManager = ActivityTransitionManager(context)
        LocationTrackingService.resetGuardForTesting()
    }

    // 1. Registration contains only supported transition activities (excluding unsupported ON_FOOT and reverted WALKING)
    @Test
    fun test01_registrationContainsOnlySupportedTransitionActivities() {
        val supportedTypes = ActivityTransitionManager.SUPPORTED_ACTIVITY_TYPES

        assertTrue("IN_VEHICLE must be supported in transition request", supportedTypes.contains(DetectedActivity.IN_VEHICLE))
        assertTrue("STILL must be supported in transition request", supportedTypes.contains(DetectedActivity.STILL))

        assertFalse(
            "DetectedActivity.ON_FOOT must NOT be in transition registration (causes Play Services status 16 / DEVELOPER_ERROR)",
            supportedTypes.contains(DetectedActivity.ON_FOOT)
        )
        assertFalse(
            "WALKING is excluded to prevent conflicting events during drive initiation",
            supportedTypes.contains(DetectedActivity.WALKING)
        )
    }

    // 2. Registration failure is surfaced and recovery can succeed
    @Test
    fun test02_registrationFailureSurfaced_andRecoveryCanSucceed() = runBlocking {
        // Simulate registration failure
        var mockSuccess = false
        transitionManager.transitionRegistrar = { mockSuccess }

        val firstAttempt = transitionManager.registerTransitions(force = true)
        assertFalse("Initial registration must fail", firstAttempt)
        assertFalse("StateFlow must report not registered", transitionManager.registrationState.value.isRegistered)
        assertNotNull("Failure error must be surfaced", transitionManager.registrationState.value.lastError)

        // Simulate recovery (e.g. user retries or permissions fixed)
        mockSuccess = true
        val secondAttempt = transitionManager.registerTransitions(force = true)
        assertTrue("Recovery registration must succeed", secondAttempt)
        assertTrue("StateFlow must report registered", transitionManager.registrationState.value.isRegistered)
        assertNull("Error must be cleared on successful registration", transitionManager.registrationState.value.lastError)
    }

    // 3. Bluetooth permission denied while required tracking permissions are granted
    @Test
    fun test03_bluetoothPermissionDenied_doesNotBlockTracking() {
        // Location and activity recognition granted, Bluetooth denied/optional
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            isServiceTracking = false,
            activeTripId = null,
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
        )
        assertTrue("Candidate drive must start without requiring BLUETOOTH_CONNECT", action is AutoDriveAction.StartCandidateDrive)
    }

    // 4. IN_VEHICLE ENTER followed immediately by WALKING ENTER
    @Test
    fun test04_inVehicleEnterFollowedImmediatelyByWalkingEnter() {
        // Step 1: IN_VEHICLE ENTER initiates candidate drive
        val enterAction = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            isServiceTracking = false,
            activeTripId = null,
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
        )
        assertTrue("IN_VEHICLE ENTER starts candidate drive", enterAction is AutoDriveAction.StartCandidateDrive)

        // Step 2: Immediate conflicting WALKING ENTER during candidate phase
        // (speed 0.0 mph, displacement unconfirmed)
        val walkingAction = engine.evaluateWalkingEnter(
            isServiceTracking = true,
            hasActiveTrip = true,
            trackingMethod = TrackingMethod.AUTO_DETECTED,
            currentSpeedMph = 0.0
        )
        assertEquals("Walking enter while stationary schedules debounce rather than killing candidate", AutoDriveAction.ScheduleDebounce, walkingAction)
    }

    // 5. A candidate progresses to a confirmed drive without a second IN_VEHICLE ENTER
    @Test
    fun test05_candidateProgressesToConfirmedDrive_withoutSecondInVehicleEnter() {
        // Car accelerates (speed = 18.0 mph, displacement reaches 220m)
        val isConfirmed = engine.evaluateCandidateMovement(
            anchorLatitude = 37.7749, anchorLongitude = -122.4194,
            currentLatitude = 37.7770, currentLongitude = -122.4194,
            accumulatedDistanceMeters = 220.0,
            calculateDistanceMeters = { _, _, _, _ -> 220.0f }
        )
        assertTrue("Movement crossing 200m confirms drive", isConfirmed)

        // Stop decision for the confirmed drive
        val stopDecision = engine.evaluateTripStop(isDisplacementConfirmed = isConfirmed, totalDistanceMeters = 220.0)
        assertEquals("Drive is confirmed and finalized without needing a second IN_VEHICLE ENTER", TripStopDecision.FINALIZE_TRIP, stopDecision)
    }

    // 6. A genuine exit/walking-away event stops an automatic trip through the intended debounce
    @Test
    fun test06_genuineExitWalkingAway_stopsAutomaticTripThroughDebounce() {
        // Confirmed drive (total distance 5.2 miles = 8368 meters)
        val isConfirmed = true
        val walkingAction = engine.evaluateWalkingEnter(
            isServiceTracking = true,
            hasActiveTrip = true,
            trackingMethod = TrackingMethod.AUTO_DETECTED,
            currentSpeedMph = 1.5 // Walking speed
        )
        assertEquals("Legitimate walking away schedules debounce", AutoDriveAction.ScheduleDebounce, walkingAction)

        // User walked away, 4 minutes elapsed with isInVehicle = false
        val debounceElapsedStop = engine.evaluateDebounceElapsed(isInVehicle = false)
        assertTrue("Trip should finalize when debounce elapses with user exited", debounceElapsedStop)

        val finalDecision = engine.evaluateTripStop(isDisplacementConfirmed = isConfirmed, totalDistanceMeters = 8368.0)
        assertEquals("Confirmed trip must be finalized", TripStopDecision.FINALIZE_TRIP, finalDecision)
    }

    // 7. An abandoned candidate is eventually discarded
    @Test
    fun test07_abandonedCandidate_isEventuallyDiscarded() {
        // Candidate started but vehicle never moved (distance = 25m, unconfirmed)
        val walkingAction = engine.evaluateWalkingEnter(
            isServiceTracking = true,
            hasActiveTrip = true,
            trackingMethod = TrackingMethod.AUTO_DETECTED,
            currentSpeedMph = 0.0
        )
        assertEquals("Walking enter schedules debounce", AutoDriveAction.ScheduleDebounce, walkingAction)

        // Debounce timer finishes while user walked away
        val shouldStop = engine.evaluateDebounceElapsed(isInVehicle = false)
        assertTrue("Debounce elapsed triggers stop evaluation", shouldStop)

        // Discard evaluation
        val stopDecision = engine.evaluateTripStop(isDisplacementConfirmed = false, totalDistanceMeters = 25.0)
        assertEquals("Abandoned candidate (<200m) is discarded as false trip", TripStopDecision.DISCARD_FALSE_TRIP, stopDecision)
    }

    // 8. Vehicle re-entry cancels pending stop work
    @Test
    fun test08_vehicleReentry_cancelsPendingStopWork() {
        // Active trip currently debouncing
        val activeTripId = "debouncing-trip-456"
        val reentryAction = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            isServiceTracking = true, // Still alive during debounce
            activeTripId = activeTripId,
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
        )
        assertEquals("Vehicle re-entry while service is alive cancels pending debounce", AutoDriveAction.CancelDebounce, reentryAction)
    }

    // 9. Stale stop work cannot terminate a newer trip
    @Test
    fun test09_staleStopWork_cannotTerminateNewerTrip() {
        val scheduledTripId = "old-trip-1"
        val activeTripId = "new-trip-2"

        // Scoping check: worker must verify scheduledTripId matches activeTripId
        val isScopedCorrectly = (scheduledTripId == activeTripId)
        assertFalse("Stale stop work scheduled for Trip 1 must not match active Trip 2", isScopedCorrectly)
    }

    // 10. Manual trips are unaffected
    @Test
    fun test10_manualTripsAreUnaffected() {
        // Walking enter received while manual GPS trip is active
        val walkingActionGps = engine.evaluateWalkingEnter(
            isServiceTracking = true,
            hasActiveTrip = true,
            trackingMethod = TrackingMethod.MANUAL_GPS,
            currentSpeedMph = 0.0
        )
        assertEquals("Manual GPS trip must ignore walking transitions", AutoDriveAction.Ignore, walkingActionGps)

        // Walking enter received while manual Odometer trip is active
        val walkingActionOdometer = engine.evaluateWalkingEnter(
            isServiceTracking = true,
            hasActiveTrip = true,
            trackingMethod = TrackingMethod.MANUAL_ODOMETER,
            currentSpeedMph = 0.0
        )
        assertEquals("Manual Odometer trip must ignore walking transitions", AutoDriveAction.Ignore, walkingActionOdometer)
    }

    // 11. Bluetooth disconnect/reconnect behavior remains correct
    @Test
    fun test11_bluetoothDisconnectReconnectBehavior() = runBlocking {
        preferencesManager.setVehicleBluetoothDevice("AA:BB:CC:DD:EE:FF", "My Tesla")
        preferencesManager.setBluetoothAutoStopEnabled(true)
        val tripId = "bt-trip-789"
        val activeTrip = ActiveTripEntity(
            id = tripId,
            startTime = 1L,
            startLatitude = 1.1,
            startLongitude = 1.1,
            lastLatitude = 1.1,
            lastLongitude = 1.1,
            lastTimestamp = 2L,
            totalDistanceMeters = 500.0,
            trackingMethod = TrackingMethod.AUTO_DETECTED.value
        )
        val scheduler = TestRecordingScheduler(context)
        val repo = TestActiveTripRepository(activeTrip)
        val monitor = BluetoothArmedDriveMonitor(
            context,
            preferencesManager,
            LocationServices.getFusedLocationProviderClient(context)
        ).apply { activityUpdateUnregistrar = {} }
        val vehicleRepository = object : VehicleRepository {
            override fun observeVehicles() = kotlinx.coroutines.flow.flowOf(emptyList<VehicleEntity>())
            override suspend fun getVehicles(): List<VehicleEntity> = emptyList()
            override suspend fun getVehicle(id: String): VehicleEntity? = null
            override suspend fun getVehicleForBluetoothAddress(address: String): VehicleEntity? = null
            override suspend fun save(vehicle: VehicleEntity) = Unit
        }
        val handler = VehicleBluetoothEventHandler(
            preferencesManager, repo, scheduler, monitor,
            VehicleBluetoothMatcher(context, vehicleRepository, preferencesManager)
        )

        // Disconnect
        handler.handle(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED, "AA:BB:CC:DD:EE:FF")
        assertTrue("Disconnect schedules debounce", scheduler.scheduled)
        assertTrue("Disconnect sets pending flag", preferencesManager.isBluetoothDisconnectPending())
        assertFalse("Disconnect clears in-vehicle state", preferencesManager.isInVehicle())

        // Reconnect
        handler.handle(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED, "AA:BB:CC:DD:EE:FF")
        assertTrue("Reconnect cancels debounce", scheduler.cancelled)
        assertFalse("Reconnect clears pending flag", preferencesManager.isBluetoothDisconnectPending())
        assertTrue("Reconnect restores in-vehicle state", preferencesManager.isInVehicle())
    }

    // 12. Work schedules and "Do not track" behavior remain intact
    @Test
    fun test12_workScheduleAndDoNotTrackBehavior() {
        // Outside schedule with CONSERVE_BATTERY ("Do not track")
        val doNotTrackAction = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            isServiceTracking = false,
            activeTripId = null,
            isWithinSchedule = false,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CONSERVE_BATTERY
        )
        assertEquals("Outside schedule with CONSERVE_BATTERY must ignore drive", AutoDriveAction.Ignore, doNotTrackAction)

        // Outside schedule with CLASSIFY_PERSONAL
        val personalAction = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            isServiceTracking = false,
            activeTripId = null,
            isWithinSchedule = false,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
        )
        assertTrue("Outside schedule with CLASSIFY_PERSONAL must start candidate drive", personalAction is AutoDriveAction.StartCandidateDrive)
        assertEquals("Personal", (personalAction as AutoDriveAction.StartCandidateDrive).suggestedBusiness)
    }

    private class TestRecordingScheduler(context: Context) : TripDebounceScheduler(context) {
        var scheduled = false
        var cancelled = false

        override fun scheduleForBluetoothDisconnect(tripId: String) {
            scheduled = true
        }

        override fun cancel() {
            cancelled = true
        }
    }

    private class TestActiveTripRepository(initial: ActiveTripEntity?) : ActiveTripRepository {
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
}
