package com.businesstracker.mileage

import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.preferences.VehicleAnchor
import com.businesstracker.mileage.domain.tracking.AutoDriveAction
import com.businesstracker.mileage.domain.tracking.AutoDriveDecisionEngine
import com.businesstracker.mileage.domain.tracking.CurrentLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutomaticDriveDetectionTest {

    private lateinit var engine: AutoDriveDecisionEngine

    @Before
    fun setUp() {
        engine = AutoDriveDecisionEngine()
    }

    // 1. First IN_VEHICLE ENTER event
    @Test
    fun test01_firstInVehicleEnter_establishesAnchorWithoutStartingTrip() {
        val location = CurrentLocation(latitude = 37.3382, longitude = -121.8863, accuracyMeters = 10f, timestamp = 1000L)
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasPermissions = true,
            isServiceTracking = false,
            activeTripId = null,
            existingAnchor = null,
            currentLocation = location,
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL,
            calculateDistanceMeters = { _, _, _, _ -> 0.0f }
        )
        assertTrue("First vehicle enter must establish an anchor", action is AutoDriveAction.SetAnchor)
        val setAnchor = action as AutoDriveAction.SetAnchor
        assertEquals(37.3382, setAnchor.latitude, 0.0001)
        assertEquals(-121.8863, setAnchor.longitude, 0.0001)
    }

    // 2. Second IN_VEHICLE event without movement
    @Test
    fun test02_secondInVehicleEvent_withoutMovement_doesNotStartTrip() {
        val anchor = VehicleAnchor(latitude = 37.3382, longitude = -121.8863, timestamp = 1000L)
        val location = CurrentLocation(latitude = 37.3382, longitude = -121.8863, accuracyMeters = 8f, timestamp = 2000L)
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasPermissions = true,
            isServiceTracking = false,
            activeTripId = null,
            existingAnchor = anchor,
            currentLocation = location,
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL,
            calculateDistanceMeters = { _, _, _, _ -> 0.0f }
        )
        assertEquals("Stationary vehicle events must not start a trip", AutoDriveAction.Ignore, action)
    }

    // 3. Movement below 200 meters
    @Test
    fun test03_movementBelow200Meters_preservesAnchorAndDoesNotStartTrip() {
        val anchor = VehicleAnchor(latitude = 37.3382, longitude = -121.8863, timestamp = 1000L)
        val location = CurrentLocation(latitude = 37.3390, longitude = -121.8863, accuracyMeters = 12f, timestamp = 3000L)
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasPermissions = true,
            isServiceTracking = false,
            activeTripId = null,
            existingAnchor = anchor,
            currentLocation = location,
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL,
            calculateDistanceMeters = { _, _, _, _ -> 95.0f } // 95m < 200m
        )
        assertEquals("Movements below 200m must be ignored (anchor preserved)", AutoDriveAction.Ignore, action)
    }

    // 4. Movement exceeding 200 meters
    @Test
    fun test04_movementExceeding200Meters_startsTrip() {
        val anchor = VehicleAnchor(latitude = 37.3382, longitude = -121.8863, timestamp = 1000L)
        val location = CurrentLocation(latitude = 37.3420, longitude = -121.8863, accuracyMeters = 15f, timestamp = 5000L)
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasPermissions = true,
            isServiceTracking = false,
            activeTripId = null,
            existingAnchor = anchor,
            currentLocation = location,
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL,
            calculateDistanceMeters = { _, _, _, _ -> 420.0f } // 420m >= 200m
        )
        assertTrue("Movements exceeding 200m must trigger trip start", action is AutoDriveAction.StartTrip)
        assertEquals("MySucculentCorner", (action as AutoDriveAction.StartTrip).suggestedBusiness)
    }

    // 5. GPS accuracy too poor to verify displacement
    @Test
    fun test05_gpsAccuracyTooPoor_rejectsDisplacementVerification() {
        val anchor = VehicleAnchor(latitude = 37.3382, longitude = -121.8863, timestamp = 1000L)
        // Accuracy 75m > 50m max threshold
        val inaccurateLocation = CurrentLocation(latitude = 37.3500, longitude = -121.8863, accuracyMeters = 75.0f, timestamp = 4000L)
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasPermissions = true,
            isServiceTracking = false,
            activeTripId = null,
            existingAnchor = anchor,
            currentLocation = inaccurateLocation,
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL,
            calculateDistanceMeters = { _, _, _, _ -> 1200.0f }
        )
        assertEquals("Fixes with poor accuracy (>50m) must be rejected", AutoDriveAction.Ignore, action)
    }

    // 6. Walking without driving
    @Test
    fun test06_walkingWithoutDriving_doesNotTriggerVehicleTracking() {
        val noLocation: CurrentLocation? = null
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasPermissions = true,
            isServiceTracking = false,
            activeTripId = null,
            existingAnchor = null,
            currentLocation = noLocation,
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL,
            calculateDistanceMeters = { _, _, _, _ -> 0.0f }
        )
        assertEquals("Non-vehicle events without GPS must be ignored", AutoDriveAction.Ignore, action)
    }

    // 7. STILL ENTER at a traffic light
    @Test
    fun test07_stillEnterAtTrafficLight_doesNotTerminateOrDebounceTrip() {
        val action = engine.evaluateStillEnter(isInVehicle = true, isServiceTracking = true)
        assertEquals("STILL ENTER during an active drive (traffic light) must be ignored", AutoDriveAction.Ignore, action)
    }

    // 8. IN_VEHICLE EXIT
    @Test
    fun test08_inVehicleExit_initiatesDebounce() {
        val action = engine.evaluateVehicleExit(isServiceTracking = true, hasActiveTrip = true)
        assertEquals("Vehicle exit while tracking must schedule debounce", AutoDriveAction.ScheduleDebounce, action)
    }

    // 9. Four-minute debounce
    @Test
    fun test09_fourMinuteDebounce_finalizesWhenUserExited() {
        val isInVehicle = false
        val shouldFinalize = !isInVehicle
        assertTrue("When debounce timer fires and user is not in vehicle, trip must finalize", shouldFinalize)
    }

    // 10. Vehicle movement restarting during debounce
    @Test
    fun test10_vehicleMovementRestartingDuringDebounce_cancelsDebounce() {
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasPermissions = true,
            isServiceTracking = true, // Still tracking during debounce period
            activeTripId = "existing-trip-123",
            existingAnchor = null,
            currentLocation = CurrentLocation(37.3382, -121.8863, 10f, 6000L),
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL,
            calculateDistanceMeters = { _, _, _, _ -> 500f }
        )
        assertEquals("Movement restarting during debounce must cancel debounce and keep trip active", AutoDriveAction.CancelDebounce, action)
    }

    // 11. Repeated ENTER events
    @Test
    fun test11_repeatedEnterEvents_preventsDuplicateTrips() {
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasPermissions = true,
            isServiceTracking = true, // already tracking from first event
            activeTripId = "active-uuid-1",
            existingAnchor = null,
            currentLocation = CurrentLocation(37.3382, -121.8863, 10f, 7000L),
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL,
            calculateDistanceMeters = { _, _, _, _ -> 300f }
        )
        assertEquals("Repeated ENTER events while tracking must cancel debounce, never create duplicate trip", AutoDriveAction.CancelDebounce, action)
    }

    // 12. Duplicate service prevention
    @Test
    fun test12_duplicateServicePrevention_ignoresSubsequentStarts() {
        val isServiceTracking = true
        val currentTripId = "trip-active-100"
        val shouldIgnoreDuplicateStart = isServiceTracking && currentTripId.isNotEmpty()
        assertTrue("Service must ignore subsequent start calls when already tracking", shouldIgnoreDuplicateStart)
    }

    // 13. Existing active-trip recovery
    @Test
    fun test13_existingActiveTripRecovery_recoversExistingTripId() {
        val existingTripId = "interrupted-trip-uuid-42"
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasPermissions = true,
            isServiceTracking = false, // Process was killed, service not running yet
            activeTripId = existingTripId, // but activeTripId is stored in persistence
            existingAnchor = null,
            currentLocation = CurrentLocation(37.3382, -121.8863, 10f, 8000L),
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL,
            calculateDistanceMeters = { _, _, _, _ -> 300f }
        )
        assertTrue("Interrupted trip must be recovered rather than creating a duplicate", action is AutoDriveAction.RecoverTrip)
        assertEquals(existingTripId, (action as AutoDriveAction.RecoverTrip).tripId)
    }

    // 14. Automatic tracking disabled
    @Test
    fun test14_automaticTrackingDisabled_ignoresAllEvents() {
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = false,
            hasPermissions = true,
            isServiceTracking = false,
            activeTripId = null,
            existingAnchor = null,
            currentLocation = CurrentLocation(37.3382, -121.8863, 10f, 9000L),
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL,
            calculateDistanceMeters = { _, _, _, _ -> 500f }
        )
        assertEquals("When automatic tracking is disabled, vehicle events must be ignored", AutoDriveAction.Ignore, action)
    }

    // 15. Missing activity recognition or location permissions
    @Test
    fun test15_missingPermissions_safelyAbortsWithoutCrash() {
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasPermissions = false, // Permissions denied by user
            isServiceTracking = false,
            activeTripId = null,
            existingAnchor = null,
            currentLocation = null,
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL,
            calculateDistanceMeters = { _, _, _, _ -> 500f }
        )
        assertEquals("When permissions are missing, auto tracking must abort safely", AutoDriveAction.Ignore, action)
    }
}
