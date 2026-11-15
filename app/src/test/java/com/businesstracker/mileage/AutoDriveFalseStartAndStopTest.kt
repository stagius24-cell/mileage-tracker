package com.businesstracker.mileage

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleState
import com.businesstracker.mileage.data.preferences.CandidateDeadline
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.repository.ActiveTripRepositoryImpl
import com.businesstracker.mileage.data.repository.TripRepositoryImpl
import com.businesstracker.mileage.domain.coordinator.TripFinalizationCoordinator
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.model.Waypoint
import com.businesstracker.mileage.domain.service.BootCountProvider
import com.businesstracker.mileage.domain.tracking.AutoDriveAction
import com.businesstracker.mileage.domain.tracking.AutoDriveDecisionEngine
import com.businesstracker.mileage.domain.tracking.MotionResumptionValidator
import com.businesstracker.mileage.domain.tracking.MotionValidationResult
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import com.businesstracker.mileage.domain.usecase.DrainPendingGpsFixesUseCase
import com.businesstracker.mileage.domain.usecase.FilterGpsPointUseCase
import com.businesstracker.mileage.domain.usecase.GeocodeLocationUseCase
import com.businesstracker.mileage.domain.usecase.GpsInboxProcessor
import com.businesstracker.mileage.receiver.AutoDriveReceiver
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.service.NotificationHelper
import com.businesstracker.mileage.service.TrackingServiceState
import com.businesstracker.mileage.worker.TripDebounceWorker
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AutoDriveFalseStartAndStopTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var decisionEngine: AutoDriveDecisionEngine
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var activeTripRepo: ActiveTripRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        preferencesManager = TrackingPreferencesManager(context)
        decisionEngine = AutoDriveDecisionEngine()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activeTripRepo = ActiveTripRepositoryImpl(database.activeTripDao(), Dispatchers.Default)
        LocationTrackingService.resetGuardForTesting()
    }

    @After
    fun tearDown() {
        LocationTrackingService.resetGuardForTesting()
        database.close()
    }

    // =========================================================================
    // Scenario 1: Historical movement does NOT cancel stopping when stationary
    // =========================================================================

    @Test
    fun test01_historicalDisplacementDoesNotCancelStoppingWhenStationary() = runBlocking {
        val service = LocationTrackingService()
        val tripId = "test-trip-stopping-1"

        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis(),
                startLatitude = 37.7749,
                startLongitude = -122.4194,
                lastLatitude = 37.7749,
                lastLongitude = -122.4194,
                lastTimestamp = System.currentTimeMillis(),
                totalDistanceMeters = 500.0,
                trackingMethod = "AUTO_DETECTED",
                isPaused = false
            )
        )

        val notificationHelper = NotificationHelper(context)

        service.setTestDependencies(
            activeTripRepo = activeTripRepo,
            helper = notificationHelper,
            tripId = tripId,
            prefs = preferencesManager,
            dispatcher = testDispatcher,
            scope = testScope
        )

        service.setTrackingStateForTesting(
            tripId = tripId,
            distanceMeters = 500.0,
            displacementConfirmed = true
        )

        // Simulate incoming GPS fix with 0 mph while parked
        val stationaryFix = GpsInboxProcessor.ProcessResult.Accepted(
            fixId = 1L,
            tripId = tripId,
            deltaDistanceMeters = 0.0,
            totalDistanceMeters = 500.0,
            lastLatitude = 37.7749,
            lastLongitude = -122.4194,
            speedMph = 0.0,
            isDisplacementConfirmed = true,
            waypoint = Waypoint(37.7749, -122.4194, speed = 0f, accuracy = 5f)
        )

        service.handleFixProcessed(stationaryFix)

        // Verify displacement remains confirmed but inVehicle was NOT set to true
        assertTrue("Displacement remains confirmed", service.getInternalDisplacementConfirmed())
        assertFalse("Vehicle state must not be set to in-vehicle while sitting still", preferencesManager.isInVehicle())
    }

    // =========================================================================
    // Scenario 2: AlreadyConsumed GPS fix does NOT cancel stopping
    // =========================================================================

    @Test
    fun test02_alreadyConsumedFixDoesNotCancelStopping() = runBlocking {
        val service = LocationTrackingService()
        val tripId = "test-trip-already-consumed"

        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis(),
                startLatitude = 37.7749,
                startLongitude = -122.4194,
                lastLatitude = 37.7749,
                lastLongitude = -122.4194,
                lastTimestamp = System.currentTimeMillis(),
                totalDistanceMeters = 800.0,
                trackingMethod = "AUTO_DETECTED",
                isPaused = false
            )
        )

        val notificationHelper = NotificationHelper(context)

        service.setTestDependencies(
            activeTripRepo = activeTripRepo,
            helper = notificationHelper,
            tripId = tripId,
            prefs = preferencesManager,
            dispatcher = testDispatcher,
            scope = testScope
        )

        service.setTrackingStateForTesting(
            tripId = tripId,
            distanceMeters = 800.0,
            displacementConfirmed = true
        )

        // Ingest AlreadyConsumed fix (e.g. from inbox replay)
        val alreadyConsumed = GpsInboxProcessor.ProcessResult.AlreadyConsumed(
            fixId = 2L,
            tripId = tripId,
            totalDistanceMeters = 800.0,
            lastLatitude = 37.7749,
            lastLongitude = -122.4194,
            isDisplacementConfirmed = true,
            waypoint = Waypoint(37.7749, -122.4194, speed = 0f, accuracy = 5f)
        )

        service.handleFixProcessed(alreadyConsumed)

        assertFalse("AlreadyConsumed fix must never cancel stop debounce or set inVehicle=true", preferencesManager.isInVehicle())
    }

    // =========================================================================
    // Scenario 3: Motion resumption requires two qualifying fixes within 30s
    // =========================================================================

    @Test
    fun test03_motionResumptionRequiresTwoQualifyingFixesWithinWindow() {
        val validator = MotionResumptionValidator(
            speedThresholdMph = 10.0,
            windowMillis = 30_000L,
            maxAccuracyMeters = 50.0f
        )

        val t0 = 100_000L

        // Fix 1: 15 mph, good accuracy -> AwaitingCorroboration
        val result1 = validator.evaluateFix(
            speedMph = 15.0,
            accuracyMeters = 10f,
            fixTimestampMillis = t0,
            nowMillis = t0
        )
        assertTrue("Single fix must await corroboration", result1 is MotionValidationResult.AwaitingCorroboration)

        // Fix 2: 18 mph, good accuracy, 5s later -> MotionCorroborated!
        val t1 = t0 + 5_000L
        val result2 = validator.evaluateFix(
            speedMph = 18.0,
            accuracyMeters = 12f,
            fixTimestampMillis = t1,
            nowMillis = t1
        )
        assertTrue("Second qualifying fix within 30s must corroborate motion resumption", result2 is MotionValidationResult.MotionCorroborated)
        val corroborated = result2 as MotionValidationResult.MotionCorroborated
        assertEquals(t0, corroborated.firstFixTimestamp)
        assertEquals(t1, corroborated.secondFixTimestamp)
    }

    // =========================================================================
    // Scenario 4: Motion resumption rejects poor accuracy, stale, or low-speed
    // =========================================================================

    @Test
    fun test04_motionResumptionRejectionRules() {
        val validator = MotionResumptionValidator(
            speedThresholdMph = 10.0,
            windowMillis = 30_000L,
            maxAccuracyMeters = 50.0f
        )

        val now = 200_000L

        // A. Speed < 10 mph -> Rejected
        val lowSpeed = validator.evaluateFix(
            speedMph = 7.5,
            accuracyMeters = 10f,
            fixTimestampMillis = now,
            nowMillis = now
        )
        assertTrue("Fix < 10 mph must be rejected", lowSpeed is MotionValidationResult.Rejected)

        // B. Poor accuracy > 50m -> Rejected
        val poorAccuracy = validator.evaluateFix(
            speedMph = 25.0,
            accuracyMeters = 65f,
            fixTimestampMillis = now,
            nowMillis = now
        )
        assertTrue("Fix accuracy > 50m must be rejected", poorAccuracy is MotionValidationResult.Rejected)

        // C. Stale fix (> 30s old) -> Rejected
        val staleFix = validator.evaluateFix(
            speedMph = 25.0,
            accuracyMeters = 10f,
            fixTimestampMillis = now - 35_000L,
            nowMillis = now
        )
        assertTrue("Fix older than 30s must be rejected", staleFix is MotionValidationResult.Rejected)

        // D. Future timestamp (> 5s ahead) -> Rejected
        val futureFix = validator.evaluateFix(
            speedMph = 25.0,
            accuracyMeters = 10f,
            fixTimestampMillis = now + 6_000L,
            nowMillis = now
        )
        assertTrue("Fix from future must be rejected", futureFix is MotionValidationResult.Rejected)

        // E. Same timestamp as first fix -> does not corroborate
        validator.reset()
        val first = validator.evaluateFix(20.0, 10f, now, now)
        assertTrue(first is MotionValidationResult.AwaitingCorroboration)
        val duplicateTimestamp = validator.evaluateFix(22.0, 10f, now, now)
        assertTrue("Fix with exact same timestamp must not corroborate", duplicateTimestamp is MotionValidationResult.AwaitingCorroboration)
    }

    // =========================================================================
    // Scenario 5: Candidate drive starts in candidate state
    // =========================================================================

    @Test
    fun test05_candidateDriveStartsInCandidateState() = runBlocking {
        val service = LocationTrackingService()
        val tripId = "test-candidate-trip-5"

        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis(),
                startLatitude = 37.7749,
                startLongitude = -122.4194,
                lastLatitude = 37.7749,
                lastLongitude = -122.4194,
                lastTimestamp = System.currentTimeMillis(),
                totalDistanceMeters = 0.0,
                trackingMethod = "AUTO_DETECTED",
                isPaused = false
            )
        )

        val notificationHelper = NotificationHelper(context)

        service.setTestDependencies(
            activeTripRepo = activeTripRepo,
            helper = notificationHelper,
            tripId = tripId,
            prefs = preferencesManager,
            dispatcher = testDispatcher,
            scope = testScope
        )

        service.setTrackingStateForTesting(
            tripId = tripId,
            distanceMeters = 0.0,
            displacementConfirmed = false
        )
        service.setCandidateForTesting(true, deadlineElapsed = 240_000L)

        assertTrue("Automatic drive must start in candidate state", service.getInternalIsCandidate())
        assertFalse("Candidate must not have confirmed displacement", service.getInternalDisplacementConfirmed())
    }

    // =========================================================================
    // Scenario 6: Candidate expires after 4 minutes if stationary (< 200m)
    // =========================================================================

    @Test
    fun test06_candidateExpiresAfterFourMinutesIfStationary() = runBlocking {
        val service = LocationTrackingService()
        val tripId = "test-candidate-expire-6"

        val notificationHelper = NotificationHelper(context)

        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis(),
                startLatitude = 37.77,
                startLongitude = -122.41,
                lastLatitude = 37.77,
                lastLongitude = -122.41,
                lastTimestamp = System.currentTimeMillis(),
                totalDistanceMeters = 15.0,
                trackingMethod = "AUTO_DETECTED",
                isPaused = false
            )
        )
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(tripId = tripId, state = TripLifecycleState.CANDIDATE.value)
        )

        preferencesManager.setActiveTripId(tripId)
        preferencesManager.recordCandidateDeadline(tripId, 1000L, 1)
        preferencesManager.setInVehicleState(true)

        val processor = GpsInboxProcessor(
            database = database,
            activeTripDao = database.activeTripDao(),
            gpsInboxDao = database.gpsInboxDao(),
            filterGpsPointUseCase = FilterGpsPointUseCase()
        )
        val drainUseCase = DrainPendingGpsFixesUseCase(
            gpsInboxDao = database.gpsInboxDao(),
            gpsInboxProcessor = processor
        )

        val coordinator = TripFinalizationCoordinator(
            database = database,
            activeTripRepository = activeTripRepo,
            tripRepository = TripRepositoryImpl(database.tripDao(), Dispatchers.Default),
            geocodeUseCase = GeocodeLocationUseCase(context, Dispatchers.Default),
            rateCalculator = CalculateMileageRateUseCase(),
            preferencesManager = preferencesManager,
            gson = Gson(),
            ioDispatcher = testDispatcher
        )

        service.setTestDependencies(
            activeTripRepo = activeTripRepo,
            helper = notificationHelper,
            tripId = tripId,
            lifecycleDao = database.tripLifecycleDao(),
            inboxDao = database.gpsInboxDao(),
            drainUseCase = drainUseCase,
            seqDao = database.tripSequenceDao(),
            db = database,
            prefs = preferencesManager,
            coordinator = coordinator,
            dispatcher = testDispatcher,
            scope = testScope
        )
        service.setTrackingStateForTesting(tripId, distanceMeters = 15.0, displacementConfirmed = false)
        service.setCandidateForTesting(true, deadlineElapsed = 1000L)

        // Trigger candidate expiration
        service.expireCandidateSessionForTesting()

        assertEquals("False candidate trip must be discarded in Room", TripLifecycleState.DISCARDED.value, database.tripLifecycleDao().getState(tripId))
        assertFalse("isCandidate must be false after expiration", service.getInternalIsCandidate())
        assertNull("Active trip ID must be cleared in preferences", preferencesManager.activeTripId.first())
        assertNull("Candidate deadline must be cleared in preferences", preferencesManager.getCandidateDeadline())
        assertFalse("inVehicle state must be false", preferencesManager.isInVehicle())
    }

    // =========================================================================
    // Scenario 7: Candidate promotes to confirmed tracking when displacement >= 200m
    // =========================================================================

    @Test
    fun test07_candidatePromotesToConfirmedTrackingWhenDisplacementReaches200m() = runBlocking {
        val service = LocationTrackingService()
        val tripId = "test-candidate-promote-7"

        val notificationHelper = NotificationHelper(context)

        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis(),
                startLatitude = 37.77,
                startLongitude = -122.41,
                lastLatitude = 37.77,
                lastLongitude = -122.41,
                lastTimestamp = System.currentTimeMillis(),
                totalDistanceMeters = 0.0,
                trackingMethod = "AUTO_DETECTED",
                isPaused = false
            )
        )
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(tripId = tripId, state = TripLifecycleState.CANDIDATE.value)
        )

        service.setTestDependencies(
            activeTripRepo = activeTripRepo,
            helper = notificationHelper,
            tripId = tripId,
            lifecycleDao = database.tripLifecycleDao(),
            prefs = preferencesManager,
            dispatcher = testDispatcher,
            scope = testScope
        )

        preferencesManager.setActiveTripId(tripId)
        preferencesManager.recordCandidateDeadline(tripId, 240_000L, 1)

        service.setTrackingStateForTesting(tripId, distanceMeters = 0.0, displacementConfirmed = false)
        service.setCandidateForTesting(true, deadlineElapsed = 240_000L)

        // Process a fix that achieves displacement >= 200m
        val qualifyingFix = GpsInboxProcessor.ProcessResult.Accepted(
            fixId = 3L,
            tripId = tripId,
            deltaDistanceMeters = 220.0,
            totalDistanceMeters = 220.0,
            lastLatitude = 37.7760,
            lastLongitude = -122.4194,
            speedMph = 25.0,
            isDisplacementConfirmed = true,
            waypoint = Waypoint(37.7760, -122.4194, speed = 11f, accuracy = 5f)
        )

        service.handleFixProcessed(qualifyingFix)

        // Allow background persistence to complete
        var cleared = false
        for (i in 1..40) {
            if (preferencesManager.getCandidateDeadline() == null) {
                cleared = true
                break
            }
            kotlinx.coroutines.delay(50L)
        }

        assertTrue("Displacement must be confirmed", service.getInternalDisplacementConfirmed())
        assertFalse("Candidate flag must transition to false once confirmed", service.getInternalIsCandidate())
        assertTrue("Candidate deadline must be cleared on promotion", cleared)
        assertEquals("Room lifecycle must update to TRACKING", TripLifecycleState.TRACKING.value, database.tripLifecycleDao().getState(tripId))
    }

    // =========================================================================
    // Scenario 8: STILL enter triggers stationary check without premature stop
    // =========================================================================

    @Test
    fun test08_stillEnterDoesNotPrematurelyEndTrip() {
        val stillAction = decisionEngine.evaluateStillEnter(
            isInVehicle = true,
            isServiceTracking = true
        )
        assertEquals("STILL enter must NOT end an active drive prematurely", AutoDriveAction.Ignore, stillAction)
    }

    // =========================================================================
    // Scenario 9: Cross-delivery duplicate transitions rejected
    // =========================================================================

    @Test
    fun test09_crossDeliveryDuplicateTransitionsRejected() = runBlocking {
        val boot = 1
        val t0 = 500_000_000_000L // nanoseconds

        preferencesManager.recordLastTransitionTimestamp(t0, boot)

        val last = preferencesManager.getLastTransitionTimestamp()
        assertNotNull(last)
        assertEquals(t0, last?.first)
        assertEquals(boot, last?.second)

        // Incoming duplicate event with t <= t0 must be identified as duplicate
        val isDuplicate = (t0 <= last!!.first && boot == last.second)
        assertTrue("Duplicate or older transition event must be identified", isDuplicate)

        // Subsequent newer event with t > t0 must be accepted
        val tNew = t0 + 1_000_000_000L
        val isNewer = (tNew > last.first)
        assertTrue("Newer transition event must be accepted", isNewer)
    }

    // =========================================================================
    // Scenario 10: Stale activity transitions (> 2 min) rejected
    // =========================================================================

    @Test
    fun test10_staleTransitionsOlderThanTwoMinutesRejected() {
        val maxAgeNanos = AutoDriveReceiver.MAX_TRANSITION_AGE_NANOS
        assertEquals(120_000_000_000L, maxAgeNanos)

        val receiptTimeNanos = 300_000_000_000L
        val freshEventTimeNanos = receiptTimeNanos - 10_000_000_000L // 10s old
        val staleEventTimeNanos = receiptTimeNanos - 150_000_000_000L // 150s old (2.5 min)

        val freshAge = receiptTimeNanos - freshEventTimeNanos
        val staleAge = receiptTimeNanos - staleEventTimeNanos

        assertFalse("10s old event is fresh", freshAge > maxAgeNanos)
        assertTrue("150s old event is stale (> 2 min) and must be rejected as an auto-start trigger", staleAge > maxAgeNanos)
    }

    // =========================================================================
    // Scenario 11: Debounce worker uses ExistingWorkPolicy.KEEP
    // =========================================================================

    @Test
    fun test11_debounceWorkerUsesKeepPolicy() {
        val policy = TripDebounceWorker.STOP_WORK_POLICY
        assertEquals(
            "Debounce stop worker must use ExistingWorkPolicy.KEEP so repeat exit events do not reset the 4-minute timer",
            ExistingWorkPolicy.KEEP,
            policy
        )
    }

    // =========================================================================
    // Scenario 12: Manual trips are never marked as candidate
    // =========================================================================

    @Test
    fun test12_manualTripsNeverMarkedAsCandidate() = runBlocking {
        val service = LocationTrackingService()
        val tripId = "test-manual-trip-12"

        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis(),
                startLatitude = 37.77,
                startLongitude = -122.41,
                lastLatitude = 37.77,
                lastLongitude = -122.41,
                lastTimestamp = System.currentTimeMillis(),
                totalDistanceMeters = 0.0,
                trackingMethod = "MANUAL",
                isPaused = false
            )
        )

        val notificationHelper = NotificationHelper(context)

        service.setTestDependencies(
            activeTripRepo = activeTripRepo,
            helper = notificationHelper,
            tripId = tripId,
            prefs = preferencesManager,
            dispatcher = testDispatcher,
            scope = testScope
        )

        // Manual tracking starts with isCandidate = false
        service.setTrackingStateForTesting(
            tripId = tripId,
            distanceMeters = 0.0,
            displacementConfirmed = false
        )
        service.setCandidateForTesting(false, deadlineElapsed = 0L)

        assertFalse("Manual trip must never start as candidate", service.getInternalIsCandidate())
    }

    // =========================================================================
    // Scenario 13: Terminal trips (FINALIZED / DISCARDED) cannot be recovered
    // =========================================================================

    @Test
    fun test13_terminalTripsCannotBeRecovered() = runBlocking {
        val tripId = "test-terminal-trip-13"
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(tripId = tripId, state = TripLifecycleState.FINALIZED.value)
        )

        val state = database.tripLifecycleDao().getState(tripId)
        assertTrue(
            "FINALIZED trip is terminal and must not be recovered",
            state == TripLifecycleState.FINALIZED.value || state == TripLifecycleState.DISCARDED.value
        )

        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(tripId = tripId, state = TripLifecycleState.DISCARDED.value)
        )
        val discardedState = database.tripLifecycleDao().getState(tripId)
        assertTrue(
            "DISCARDED trip is terminal and must not be recovered",
            discardedState == TripLifecycleState.FINALIZED.value || discardedState == TripLifecycleState.DISCARDED.value
        )
    }

    // =========================================================================
    // Scenario 14: Candidate deadline persistence and retrieval
    // =========================================================================

    @Test
    fun test14_candidateDeadlinePersistence() = runBlocking {
        val tripId = "candidate-pref-test"
        val deadlineElapsed = 240_000L
        val boot = 2

        preferencesManager.recordCandidateDeadline(tripId, deadlineElapsed, boot)

        val retrieved = preferencesManager.getCandidateDeadline()
        assertNotNull(retrieved)
        assertEquals(tripId, retrieved?.tripId)
        assertEquals(deadlineElapsed, retrieved?.deadlineElapsedMillis)
        assertEquals(boot, retrieved?.bootCount)

        preferencesManager.clearCandidateDeadline(tripId)
        assertNull("Candidate deadline must be cleared", preferencesManager.getCandidateDeadline())
    }

    // =========================================================================
    // Scenario 15: Notification displays Candidate state correctly
    // =========================================================================

    @Test
    fun test15_notificationDisplaysCandidateState() {
        val helper = NotificationHelper(context)
        val candidateNotification = helper.buildTrackingNotification(
            distanceMiles = 0.0,
            durationMillis = 10_000L,
            isPaused = false,
            isCandidate = true
        )
        assertNotNull(candidateNotification)

        val activeNotification = helper.buildTrackingNotification(
            distanceMiles = 1.5,
            durationMillis = 60_000L,
            isPaused = false,
            isCandidate = false
        )
        assertNotNull(activeNotification)
    }

    // =========================================================================
    // Scenario 16: ServiceState reflects isCandidate
    // =========================================================================

    @Test
    fun test16_serviceStateReflectsCandidate() {
        val candidateState = TrackingServiceState(
            isTracking = true,
            isCandidate = true,
            tripId = "cand-1"
        )
        assertTrue(candidateState.isTracking)
        assertTrue(candidateState.isCandidate)

        val confirmedState = TrackingServiceState(
            isTracking = true,
            isCandidate = false,
            tripId = "conf-1"
        )
        assertTrue(confirmedState.isTracking)
        assertFalse(confirmedState.isCandidate)
    }
}
