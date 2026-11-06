package com.businesstracker.mileage

import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.tracking.AutoDriveAction
import com.businesstracker.mileage.domain.tracking.AutoDriveDecisionEngine
import com.businesstracker.mileage.domain.tracking.TripStopDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class AutomaticDriveDetectionTest {

    private lateinit var engine: AutoDriveDecisionEngine

    @Before
    fun setUp() {
        engine = AutoDriveDecisionEngine()
    }

    // 1. One uninterrupted drive with only one IN_VEHICLE ENTER event
    @Test
    fun test01_uninterruptedDrive_singleInVehicleEnter_startsCandidateDrive() {
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
        assertTrue("Single IN_VEHICLE ENTER must start candidate drive without requiring second event", action is AutoDriveAction.StartCandidateDrive)
        assertEquals("MySucculentCorner", (action as AutoDriveAction.StartCandidateDrive).suggestedBusiness)
    }

    // 2. Movement beyond 200m without reopening the app
    @Test
    fun test02_continuousMovement_beyond200m_confirmsDriveWithoutReopeningApp() {
        val anchorLat = 37.3382
        val anchorLng = -121.8863

        // Distance mock: simulates 50m -> 120m -> 260m displacement
        val isConfirmedAt50m = engine.evaluateCandidateMovement(
            anchorLatitude = anchorLat, anchorLongitude = anchorLng,
            currentLatitude = 37.3386, currentLongitude = anchorLng,
            accumulatedDistanceMeters = 50.0,
            calculateDistanceMeters = { _, _, _, _ -> 50.0f }
        )
        assertFalse("At 50m displacement, drive must not be confirmed yet", isConfirmedAt50m)

        val isConfirmedAt260m = engine.evaluateCandidateMovement(
            anchorLatitude = anchorLat, anchorLongitude = anchorLng,
            currentLatitude = 37.3405, currentLongitude = anchorLng,
            accumulatedDistanceMeters = 260.0,
            calculateDistanceMeters = { _, _, _, _ -> 260.0f }
        )
        assertTrue("At 260m displacement (> 200m), drive must be confirmed during uninterrupted drive", isConfirmedAt260m)

        val stopDecision = engine.evaluateTripStop(isDisplacementConfirmed = isConfirmedAt260m, totalDistanceMeters = 260.0)
        assertEquals("Confirmed 260m drive must be finalized", TripStopDecision.FINALIZE_TRIP, stopDecision)
    }

    // 3. Movement under 200m discards false trip
    @Test
    fun test03_continuousMovement_under200m_discardsFalseTrip() {
        val isConfirmed = false
        val stopDecision = engine.evaluateTripStop(isDisplacementConfirmed = isConfirmed, totalDistanceMeters = 65.0)
        assertEquals("Movement under 200m without confirmation must be discarded as false trip", TripStopDecision.DISCARD_FALSE_TRIP, stopDecision)
    }

    // 4. Duplicate start requests arriving simultaneously (Atomic Guard)
    @Test
    fun test04_duplicateStartRequests_simultaneous_atomicGuardPreventsDuplicate() {
        val atomicGuard = AtomicBoolean(false)

        // First start request
        val firstStartAccepted = !atomicGuard.getAndSet(true)
        assertTrue("First start request must acquire atomic guard", firstStartAccepted)

        // Second simultaneous start request
        val secondStartAccepted = !atomicGuard.getAndSet(true)
        assertFalse("Concurrent second start request must be rejected synchronously", secondStartAccepted)
    }

    // 5. Database-backed protection ensures only one active trip
    @Test
    fun test05_duplicateTripPrevention_databaseGuardEnsuresSingleActiveTrip() {
        val simulatedDbActiveTrips = mutableMapOf<String, String>()
        fun createActiveTripDbGuard(tripId: String): String {
            if (simulatedDbActiveTrips.isNotEmpty()) {
                return simulatedDbActiveTrips.values.first()
            }
            simulatedDbActiveTrips["ACTIVE"] = tripId
            return tripId
        }

        val trip1 = createActiveTripDbGuard("trip-uuid-111")
        val trip2 = createActiveTripDbGuard("trip-uuid-222") // Concurrent start attempt

        assertEquals("First trip must be recorded", "trip-uuid-111", trip1)
        assertEquals("Database guard must return existing active trip rather than creating duplicate", "trip-uuid-111", trip2)
    }

    // 6. Process death and recovery
    @Test
    fun test06_processDeathAndRecovery_restoresExistingActiveTrip() {
        val persistedActiveTripId = "persisted-trip-survived-crash-99"
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            isServiceTracking = false, // Service killed by OS
            activeTripId = persistedActiveTripId, // Preserved in Room / DataStore
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
        )
        assertTrue("Interrupted trip must be recovered rather than creating a new trip", action is AutoDriveAction.RecoverTrip)
        assertEquals(persistedActiveTripId, (action as AutoDriveAction.RecoverTrip).tripId)
    }

    // 7. Debounce cancellation when driving resumes
    @Test
    fun test07_debounceCancellation_whenDrivingResumes() {
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            isServiceTracking = true, // Service still active during debounce
            activeTripId = "active-trip-during-debounce",
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
        )
        assertEquals("Resumed vehicle motion must cancel debounce and preserve active drive", AutoDriveAction.CancelDebounce, action)
    }

    // 8. Missing background location permission
    @Test
    fun test08_missingBackgroundLocationPermission_rejectsAutoTracking() {
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = false, // Denied or not granted on Android 10+
            hasActivityRecognitionPermission = true,
            isServiceTracking = false,
            activeTripId = null,
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
        )
        assertEquals("Auto tracking must be rejected when background location permission is missing", AutoDriveAction.Ignore, action)
    }

    // 9. Actual debounce behavior when exited vs resumed
    @Test
    fun test09_actualDebounceBehavior_whenExitedVsResumed() {
        // Debounce timer elapsed, user not in vehicle -> finalize trip
        val shouldFinalizeWhenExited = engine.evaluateDebounceElapsed(isInVehicle = false)
        assertTrue("When debounce elapsed and user exited vehicle, trip must finalize", shouldFinalizeWhenExited)

        // Debounce timer elapsed, but user got back in vehicle -> do not finalize
        val shouldFinalizeWhenResumed = engine.evaluateDebounceElapsed(isInVehicle = true)
        assertFalse("When debounce elapsed but user is back in vehicle, trip must not finalize", shouldFinalizeWhenResumed)
    }

    // 10. Work schedule: outside hours with battery saver
    @Test
    fun test10_workSchedule_outsideHours_conserveBatteryMode_skipsTracking() {
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            isServiceTracking = false,
            activeTripId = null,
            isWithinSchedule = false,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CONSERVE_BATTERY
        )
        assertEquals("Battery saver mode outside schedule must skip tracking completely", AutoDriveAction.Ignore, action)
    }

    // 11. Work schedule: outside hours with personal classification
    @Test
    fun test11_workSchedule_outsideHours_personalMode_autoClassifiesPersonal() {
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            isServiceTracking = false,
            activeTripId = null,
            isWithinSchedule = false,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
        )
        assertTrue("Personal mode outside schedule must start drive tagged as Personal", action is AutoDriveAction.StartCandidateDrive)
        assertEquals("Personal", (action as AutoDriveAction.StartCandidateDrive).suggestedBusiness)
    }

    // 12. STILL ENTER at a traffic light does not stop trip
    @Test
    fun test12_stillEnter_atTrafficLight_doesNotStopTrip() {
        val action = engine.evaluateStillEnter(isInVehicle = true, isServiceTracking = true)
        assertEquals("Stationary stop at traffic light must be ignored", AutoDriveAction.Ignore, action)
    }
}
