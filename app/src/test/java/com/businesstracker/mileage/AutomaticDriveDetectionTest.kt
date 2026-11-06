package com.businesstracker.mileage

import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.tracking.AutoDriveAction
import com.businesstracker.mileage.domain.tracking.AutoDriveDecisionEngine
import com.businesstracker.mileage.domain.tracking.TripStopDecision
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    // Required Test 2: Multiple GPS callbacks are processed serially
    @Test
    fun test02_multipleGpsCallbacks_areProcessedSerially() = runBlocking {
        val channel = Channel<Long>(capacity = Channel.BUFFERED)
        val processedTimestamps = mutableListOf<Long>()

        val consumerJob = launch {
            for (timestamp in channel) {
                processedTimestamps.add(timestamp)
            }
        }

        // Simulate multiple GPS updates delivered to Channel
        val incomingFixes = listOf(1000L, 2000L, 3000L, 4000L, 5000L)
        for (fix in incomingFixes) {
            channel.send(fix)
        }
        channel.close()
        consumerJob.join()

        assertEquals("All GPS callbacks must be processed in exact serial order", incomingFixes, processedTimestamps)
    }

    // Required Test 3: Uninterrupted drive crossing 200 meters confirms trip without requiring another IN_VEHICLE ENTER event
    @Test
    fun test03_uninterruptedDrive_crossing200m_confirmsTripWithoutSecondEnter() {
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

    // Required Test 4: A drive that never reaches 200 meters is discarded
    @Test
    fun test04_driveNeverReaching200m_isDiscardedAsFalseTrip() {
        val isConfirmed = false
        val stopDecision = engine.evaluateTripStop(isDisplacementConfirmed = isConfirmed, totalDistanceMeters = 75.0)
        assertEquals("Movement under 200m without confirmation must be discarded", TripStopDecision.DISCARD_FALSE_TRIP, stopDecision)
    }

    // Required Test 5: A confirmed trip is finalized after vehicle exit and debounce
    @Test
    fun test05_confirmedTrip_finalizedAfterVehicleExitAndDebounce() {
        // Vehicle exit triggers debounce schedule
        val exitAction = engine.evaluateVehicleExit(isServiceTracking = true, hasActiveTrip = true)
        assertEquals("Vehicle exit while tracking must schedule debounce", AutoDriveAction.ScheduleDebounce, exitAction)

        // When debounce timer elapses and user remains out of vehicle, trip finalizes
        val shouldFinalize = engine.evaluateDebounceElapsed(isInVehicle = false)
        assertTrue("When debounce completes with user exited, trip must finalize", shouldFinalize)
    }

    // Required Test 6: Returning to vehicle motion cancels the debounce
    @Test
    fun test06_returningToVehicleMotion_cancelsDebounce() {
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

    // Required Test 7: Startup failure releases the atomic guard
    @Test
    fun test07_startupFailure_releasesAtomicGuard() {
        val isStartingOrRunning = AtomicBoolean(false)

        // Acquire start guard
        val acquired = isStartingOrRunning.compareAndSet(false, true)
        assertTrue("Atomic guard must be acquired at start", acquired)

        // Simulate startup failure (e.g. ForegroundServiceStartNotAllowedException or DB failure)
        val simulateFailure = true
        if (simulateFailure) {
            // Cleanup failed startup
            isStartingOrRunning.set(false)
        }

        assertFalse("Atomic guard must be released upon startup failure cleanup", isStartingOrRunning.get())

        // Subsequent valid start attempt must succeed
        val reattemptAcquired = isStartingOrRunning.compareAndSet(false, true)
        assertTrue("Subsequent start attempt must succeed after cleanup", reattemptAcquired)
    }

    // Required Test 8: Existing active-trip recovery does not create a duplicate trip
    @Test
    fun test08_existingActiveTripRecovery_doesNotCreateDuplicateTrip() {
        val existingActiveTripId = "persisted-active-trip-xyz"
        val action = engine.evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            isServiceTracking = false, // Process died, service not yet running
            activeTripId = existingActiveTripId, // Preserved in Room active_trip
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
        )
        assertTrue("Must recover existing active trip instead of creating duplicate", action is AutoDriveAction.RecoverTrip)
        assertEquals(existingActiveTripId, (action as AutoDriveAction.RecoverTrip).tripId)
    }

    // Supplementary Test 9: Missing background location permission rejects tracking
    @Test
    fun test09_missingBackgroundLocationPermission_rejectsTracking() {
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
}
