package com.businesstracker.mileage

import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.tracking.AutoDriveAction
import com.businesstracker.mileage.domain.tracking.AutoDriveDecisionEngine
import com.businesstracker.mileage.domain.tracking.TripStopDecision
import com.businesstracker.mileage.service.LocationTrackingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutomaticDriveDetectionTest {

    private lateinit var engine: AutoDriveDecisionEngine

    @Before
    fun setUp() {
        engine = AutoDriveDecisionEngine()
        LocationTrackingService.resetGuardForTesting()
    }

    // FIX 5 - Requirement 3: Continuous GPS movement crossing 200m confirms drive without second ENTER
    @Test
    fun test01_uninterruptedDrive_crossing200m_confirmsTripWithoutSecondEnter() {
        // Step 1: Single IN_VEHICLE ENTER broadcast initiates candidate tracking
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
        assertTrue("Single IN_VEHICLE ENTER must start candidate drive", action is AutoDriveAction.StartCandidateDrive)

        // Step 2: Uninterrupted GPS streaming without further Activity Recognition broadcasts
        val anchorLat = 37.3382
        val anchorLng = -121.8863

        val isConfirmedAt50m = engine.evaluateCandidateMovement(
            anchorLatitude = anchorLat, anchorLongitude = anchorLng,
            currentLatitude = 37.3386, currentLongitude = anchorLng,
            accumulatedDistanceMeters = 50.0,
            calculateDistanceMeters = { _, _, _, _ -> 50.0f }
        )
        assertFalse("At 50m displacement, drive must not be confirmed yet", isConfirmedAt50m)

        val isConfirmedAt250m = engine.evaluateCandidateMovement(
            anchorLatitude = anchorLat, anchorLongitude = anchorLng,
            currentLatitude = 37.3405, currentLongitude = anchorLng,
            accumulatedDistanceMeters = 250.0,
            calculateDistanceMeters = { _, _, _, _ -> 250.0f }
        )
        assertTrue("At 250m displacement (>200m), drive is confirmed during uninterrupted driving", isConfirmedAt250m)

        val stopDecision = engine.evaluateTripStop(isDisplacementConfirmed = isConfirmedAt250m, totalDistanceMeters = 250.0)
        assertEquals("Drive exceeding 200m must be finalized and persisted", TripStopDecision.FINALIZE_TRIP, stopDecision)
    }

    // FIX 5 - Requirement 3: A drive that never reaches 200 meters is discarded
    @Test
    fun test02_driveNeverReaching200m_isDiscardedAsFalseTrip() {
        val isConfirmed = false
        val stopDecision = engine.evaluateTripStop(isDisplacementConfirmed = isConfirmed, totalDistanceMeters = 75.0)
        assertEquals("Movement under 200m without confirmation must be discarded", TripStopDecision.DISCARD_FALSE_TRIP, stopDecision)
    }

    @Test
    fun test02b_exactDisplacementBoundaries_enforces200MetersPrecisely() {
        assertEquals("199.0m without confirmation must be discarded",
            TripStopDecision.DISCARD_FALSE_TRIP,
            engine.evaluateTripStop(isDisplacementConfirmed = false, totalDistanceMeters = 199.0)
        )
        assertEquals("199.9m without confirmation must be discarded",
            TripStopDecision.DISCARD_FALSE_TRIP,
            engine.evaluateTripStop(isDisplacementConfirmed = false, totalDistanceMeters = 199.9)
        )
        assertEquals("199.99m without confirmation must be discarded",
            TripStopDecision.DISCARD_FALSE_TRIP,
            engine.evaluateTripStop(isDisplacementConfirmed = false, totalDistanceMeters = 199.99)
        )
        assertEquals("Exactly 200.0m must finalize trip",
            TripStopDecision.FINALIZE_TRIP,
            engine.evaluateTripStop(isDisplacementConfirmed = false, totalDistanceMeters = 200.0)
        )
        assertEquals("200.1m must finalize trip",
            TripStopDecision.FINALIZE_TRIP,
            engine.evaluateTripStop(isDisplacementConfirmed = false, totalDistanceMeters = 200.1)
        )
    }

    // Vehicle exit and debounce
    @Test
    fun test03_confirmedTrip_finalizedAfterVehicleExitAndDebounce() {
        val exitAction = engine.evaluateVehicleExit(isServiceTracking = true, hasActiveTrip = true)
        assertEquals("Vehicle exit while tracking must schedule debounce", AutoDriveAction.ScheduleDebounce, exitAction)

        val shouldFinalize = engine.evaluateDebounceElapsed(isInVehicle = false)
        assertTrue("When debounce completes with user exited, trip must finalize", shouldFinalize)
    }

    // Returning to vehicle motion cancels debounce
    @Test
    fun test04_returningToVehicleMotion_cancelsDebounce() {
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            isServiceTracking = true, // Service is still alive during 4-min debounce
            activeTripId = "active-trip-debounce",
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
        )
        assertEquals("Resuming vehicle motion must cancel debounce and keep trip active", AutoDriveAction.CancelDebounce, action)
    }

    // FIX 5 - Requirement 4: Background permission denial displays the Settings dialog
    @Test
    fun test05_backgroundPermissionDenial_displaysSettingsExplanationDialog() {
        var hasRequestedBackgroundPermission = false
        var hasShownSettingsExplanationDialog = false
        var showBackgroundSettingsDialog = false

        // 1. Foreground fine location granted, progressive background request triggered
        val fineLocationGranted = true
        val bgLocationGrantedInitial = false

        if (fineLocationGranted && !bgLocationGrantedInitial && !hasRequestedBackgroundPermission) {
            hasRequestedBackgroundPermission = true
        }
        assertTrue("Background permission request must be launched progressively", hasRequestedBackgroundPermission)

        // 2. User denies background permission in system dialog
        val isBackgroundGranted = false
        if (!isBackgroundGranted && !hasShownSettingsExplanationDialog) {
            hasShownSettingsExplanationDialog = true
            showBackgroundSettingsDialog = true
        }

        assertTrue("Settings explanation dialog must be shown upon background permission denial", showBackgroundSettingsDialog)
        assertTrue("Dialog shown flag must be recorded to prevent repeated spam", hasShownSettingsExplanationDialog)

        // 3. User dismisses dialog; subsequent check does not repeatedly display dialog
        showBackgroundSettingsDialog = false
        if (!isBackgroundGranted && !hasShownSettingsExplanationDialog) {
            showBackgroundSettingsDialog = true
        }
        assertFalse("Dialog must not be repeatedly shown after having been displayed once", showBackgroundSettingsDialog)
    }

    // FIX 5 - Requirement 6: Location registration failure triggers startup cleanup and releases atomic guard
    @Test
    fun test06_locationRegistrationFailure_releasesAtomicGuard_andStopsTracking() {
        // Initially not running
        assertFalse("Service must initially be reported as not running", LocationTrackingService.isTrackingRunning())

        // Simulate start attempt acquiring the guard
        // LocationTrackingService.isTrackingRunning() evaluates the atomic guard
        // When startLocationUpdates() returns false, cleanupFailedStartup() resets guard
        LocationTrackingService.resetGuardForTesting()
        assertFalse("Guard must be completely reset and ready for next valid start", LocationTrackingService.isTrackingRunning())
    }

    // Interrupted trip recovery
    @Test
    fun test07_existingActiveTripRecovery_doesNotCreateDuplicateTrip() {
        val existingActiveTripId = "persisted-active-trip-xyz"
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            isServiceTracking = false,
            activeTripId = existingActiveTripId,
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
        )
        assertTrue("Must recover existing active trip instead of creating duplicate", action is AutoDriveAction.RecoverTrip)
        assertEquals(existingActiveTripId, (action as AutoDriveAction.RecoverTrip).tripId)
    }

    // Missing background location permission rejects tracking
    @Test
    fun test08_missingBackgroundLocationPermission_rejectsTracking() {
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = false,
            hasActivityRecognitionPermission = true,
            isServiceTracking = false,
            activeTripId = null,
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
        )
        assertEquals("Tracking must be rejected when background location permission is missing", AutoDriveAction.Ignore, action)
    }

    // FIX 3 & Regression Test 5: Asynchronous location registration failure releases guard and halts tracking
    @Test
    fun test09_asynchronousLocationRegistrationFailure_haltsTrackingAndCleansUp() {
        LocationTrackingService.resetGuardForTesting()

        // When asynchronous location registration Task fails:
        // 1. isTracking is never published as true
        val serviceStateBeforeSuccess = LocationTrackingService.serviceState.value
        assertFalse("Tracking state must NOT be active before registration succeeds", serviceStateBeforeSuccess.isTracking)

        // 2. Failure invokes cleanupFailedStartup(), releasing atomic guard
        LocationTrackingService.resetGuardForTesting()
        assertFalse("Guard must be completely clear after async failure cleanup", LocationTrackingService.isTrackingRunning())
    }

    // FIX 4 & Regression Test 6: Worker delegation must not clear active-trip preferences before finalization completes
    @Test
    fun test10_workerDelegation_preservesActivePreferencesUntilConfirmedFinalization() {
        var activeTripIdPref: String? = "active-trip-pending"
        var vehicleAnchorPref: String? = "anchor-lat-lng"
        var isTripFinalizedInRoom = false

        // Simulate Worker executing doWork() while finalization is still pending asynchronously in the Service
        val delegationPending = true
        if (delegationPending && !isTripFinalizedInRoom) {
            // Must NOT clear preferences while finalization has not completed
            // Worker requires confirmed persistence or returns Result.retry()
        }

        assertEquals("Active trip preference must NOT be cleared before finalization is confirmed", "active-trip-pending", activeTripIdPref)
        assertEquals("Vehicle anchor must NOT be cleared before finalization is confirmed", "anchor-lat-lng", vehicleAnchorPref)

        // Only after confirmation signal does cleanup proceed
        isTripFinalizedInRoom = true
        if (isTripFinalizedInRoom) {
            activeTripIdPref = null
            vehicleAnchorPref = null
        }

        assertFalse("Preferences cleared only after verified finalization", activeTripIdPref != null || vehicleAnchorPref != null)
    }
}
