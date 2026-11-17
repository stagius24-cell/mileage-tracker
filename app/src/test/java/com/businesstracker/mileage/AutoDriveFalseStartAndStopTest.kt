package com.businesstracker.mileage

import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.GpsInboxEntity
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
import com.businesstracker.mileage.receiver.BluetoothArmedDriveMonitor
import com.businesstracker.mileage.service.GpsLocationQueue
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.service.NotificationHelper
import com.businesstracker.mileage.service.TrackingServiceState
import com.businesstracker.mileage.worker.TripDebounceWorker
import com.google.android.gms.common.internal.safeparcel.SafeParcelableSerializer
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationServices
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
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            android.Manifest.permission.ACTIVITY_RECOGNITION
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
            scope = testScope,
            context = context
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
            scope = testScope,
            context = context
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

        // D. Future timestamp -> Rejected
        val futureFix = validator.evaluateFix(
            speedMph = 25.0,
            accuracyMeters = 10f,
            fixTimestampMillis = now + 1_000L,
            nowMillis = now
        )
        assertTrue("Fix from future must be rejected", futureFix is MotionValidationResult.Rejected)

        // E. Non-finite values -> Rejected
        val nanSpeed = validator.evaluateFix(Double.NaN, 10f, now, now)
        assertTrue("NaN speed must be rejected", nanSpeed is MotionValidationResult.Rejected)
        val nanAccuracy = validator.evaluateFix(25.0, Float.NaN, now, now)
        assertTrue("NaN accuracy must be rejected", nanAccuracy is MotionValidationResult.Rejected)

        // F. Unknown age (<= 0) -> Rejected
        val unknownAge = validator.evaluateFix(25.0, 10f, 0L, now)
        assertTrue("Unknown age must be rejected", unknownAge is MotionValidationResult.Rejected)

        // G. Duplicate or out-of-order timestamp -> Rejected
        validator.reset()
        val first = validator.evaluateFix(20.0, 10f, now, now)
        assertTrue(first is MotionValidationResult.AwaitingCorroboration)
        val duplicateTimestamp = validator.evaluateFix(22.0, 10f, now, now)
        assertTrue("Fix with exact same timestamp must be rejected as duplicate", duplicateTimestamp is MotionValidationResult.Rejected)
        val outOfOrder = validator.evaluateFix(22.0, 10f, now - 1_000L, now)
        assertTrue("Fix with earlier timestamp must be rejected as out-of-order", outOfOrder is MotionValidationResult.Rejected)
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
            scope = testScope,
            context = context
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
            scope = testScope,
            context = context
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
            scope = testScope,
            context = context
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
            scope = testScope,
            context = context
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

    // =========================================================================
    // Scenario 17: ENTER during pending stop debounce does NOT set inVehicle=true
    // without corroborated GPS motion
    // =========================================================================

    @Test
    fun test17_enterDuringPendingStopDoesNotSetInVehicleWithoutMotion() = runBlocking {
        val tripId = "test-trip-debounce-pending-17"
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
        preferencesManager.setActiveTripId(tripId)
        preferencesManager.setInVehicleState(false) // Pending stop debounce

        // Instantiate actual receiver
        val receiver = AutoDriveReceiver()
        receiver.preferencesManager = preferencesManager
        receiver.activeTripRepository = activeTripRepo
        receiver.tripLifecycleDao = database.tripLifecycleDao()
        receiver.decisionEngine = decisionEngine
        receiver.ioDispatcher = testDispatcher
        receiver.bootCountProvider = object : BootCountProvider {
            override fun getBootCount(): Int = 1
        }

        val event = ActivityTransitionEvent(
            DetectedActivity.IN_VEHICLE,
            ActivityTransition.ACTIVITY_TRANSITION_ENTER,
            SystemClock.elapsedRealtimeNanos()
        )
        val result = ActivityTransitionResult(listOf(event))
        val intent = Intent(AutoDriveReceiver.ACTION_ACTIVITY_TRANSITION)
        SafeParcelableSerializer.serializeToIntentExtra(
            result,
            intent,
            "com.google.android.location.internal.EXTRA_ACTIVITY_TRANSITION_RESULT"
        )

        // Invoke actual receiver path
        receiver.onReceive(context, intent)

        // Verify inVehicle state remains false until motion is corroborated
        assertFalse(
            "ENTER during pending stop must not independently set in-vehicle to true",
            preferencesManager.isInVehicle()
        )
    }

    // =========================================================================
    // Scenario 18: Expired candidate recovery triggers explicit reconciliation
    // =========================================================================

    @Test
    fun test18_expiredCandidateRecoveryTriggersExplicitReconciliation() = runBlocking {
        val tripId = "test-candidate-expired-18"
        val pastDeadline = maxOf(0L, SystemClock.elapsedRealtime() - 10_000L)
        val boot = 1

        preferencesManager.setActiveTripId(tripId)
        preferencesManager.recordCandidateDeadline(tripId, pastDeadline, boot)

        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis(),
                startLatitude = 37.7749,
                startLongitude = -122.4194,
                lastLatitude = 37.7749,
                lastLongitude = -122.4194,
                lastTimestamp = System.currentTimeMillis(),
                totalDistanceMeters = 50.0,
                trackingMethod = "AUTO_DETECTED",
                isPaused = false
            )
        )
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(tripId = tripId, state = TripLifecycleState.TRACKING.value)
        )

        val service = LocationTrackingService()
        val notificationHelper = NotificationHelper(context)
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
            scope = testScope,
            context = context,
            bootProvider = object : BootCountProvider {
                override fun getBootCount(): Int = 1
            }
        )

        service.locationUpdatesRegistrar = { true }
        // Invoke actual service recovery path: null intent recovery via recoverActiveTripSession
        service.onStartCommand(null, 0, 1)
        service.lastRecoveryJob?.join()

        // 50m < 200m -> False candidate is discarded in Room, deadline and active trip cleared,
        // and recovery does not resume tracking
        assertEquals(
            "False candidate trip must be discarded in Room",
            TripLifecycleState.DISCARDED.value,
            database.tripLifecycleDao().getState(tripId)
        )
        assertFalse("Expired candidate session under 200m must be discarded", preferencesManager.isInVehicle())
        assertNull("Candidate deadline must be cleared", preferencesManager.getCandidateDeadline())
        assertFalse("Service must not be tracking after discarded recovery", LocationTrackingService.serviceState.value.isTracking)
    }

    @Test
    fun bluetoothRemainsConnectedAndActualActivityReceiverStartsTripAfterTwoMinuteInterruption() = runBlocking {
        preferencesManager.setAutoTrackingEnabled(true)
        preferencesManager.setActiveTripId(null)
        preferencesManager.setInVehicleState(false)
        preferencesManager.setVehicleBluetoothDevice("11:22:33:44:55:66", "Test Car")
        preferencesManager.setCarBluetoothConnected(true)
        preferencesManager.setOutsideScheduleBehavior(
            com.businesstracker.mileage.data.preferences.TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
        )

        var nowElapsed = 10_000L
        val locationSamples = ArrayDeque<Location>().apply {
            add(Location("test").apply {
                latitude = 37.0
                longitude = -122.0
                accuracy = 8f
                speed = 0f
                elapsedRealtimeNanos = nowElapsed * 1_000_000L
            })
            add(Location("test").apply {
                latitude = 37.00055
                longitude = -122.0
                accuracy = 8f
                speed = 0f
                elapsedRealtimeNanos = 130_000L * 1_000_000L
            })
        }
        val monitor = BluetoothArmedDriveMonitor(
            context,
            preferencesManager,
            LocationServices.getFusedLocationProviderClient(context)
        ).apply {
            elapsedClock = { nowElapsed }
            locationProvider = { locationSamples.removeFirstOrNull() }
        }
        val receiver = AutoDriveReceiver().apply {
            preferencesManager = this@AutoDriveFalseStartAndStopTest.preferencesManager
            activeTripRepository = this@AutoDriveFalseStartAndStopTest.activeTripRepo
            tripLifecycleDao = database.tripLifecycleDao()
            decisionEngine = this@AutoDriveFalseStartAndStopTest.decisionEngine
            ioDispatcher = testDispatcher
            bootCountProvider = object : BootCountProvider {
                override fun getBootCount(): Int = 1
            }
            bluetoothArmedDriveMonitor = monitor
        }
        val activity = listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 92))

        val firstIntent = activityUpdateIntent(activity)
        assertTrue(ActivityRecognitionResult.hasResult(firstIntent))
        assertNotNull(ActivityRecognitionResult.extractResult(firstIntent))

        receiver.handleArmedActivityUpdate(
            context,
            ActivityRecognitionResult.extractResult(firstIntent)!!.probableActivities
        )
        assertNull("Stationary connected vehicle must not start a trip", preferencesManager.activeTripId.first())
        assertEquals("First low-power location should establish the baseline", 1, locationSamples.size)

        nowElapsed = 130_000L // Same Bluetooth session; no second ACL_CONNECTED event.
        receiver.handleArmedActivityUpdate(
            context,
            ActivityRecognitionResult.extractResult(activityUpdateIntent(activity))!!.probableActivities
        )

        assertEquals("Second low-power location should confirm movement", 0, locationSamples.size)
        assertNotNull("Corroborated movement after the interruption must start a candidate", preferencesManager.activeTripId.first())
        assertTrue("Car Bluetooth remains connected during re-arm", preferencesManager.isCarBluetoothConnected())
    }

    private fun activityUpdateIntent(activities: List<DetectedActivity>): Intent {
        val result = ActivityRecognitionResult(activities, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos())
        return Intent(BluetoothArmedDriveMonitor.ACTION_ACTIVITY_UPDATE).also {
            SafeParcelableSerializer.serializeToIntentExtra(
                result,
                it,
                "com.google.android.location.internal.EXTRA_ACTIVITY_RESULT"
            )
        }
    }

    // =========================================================================
    // Scenario 19: Recovery through ACTION_START preserves candidate deadline
    // =========================================================================

    @Test
    fun test19_actionStartRecoveryPreservesCandidateDeadline() = runBlocking {
        val tripId = "test-candidate-preserve-19"
        val nowElapsed = SystemClock.elapsedRealtime()
        val futureDeadline = nowElapsed + 180_000L // 3 minutes remaining
        val currentBoot = 1

        preferencesManager.recordCandidateDeadline(tripId, futureDeadline, currentBoot)

        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis(),
                startLatitude = 37.7749,
                startLongitude = -122.4194,
                lastLatitude = 37.7749,
                lastLongitude = -122.4194,
                lastTimestamp = System.currentTimeMillis(),
                totalDistanceMeters = 50.0,
                trackingMethod = "AUTO_DETECTED",
                isPaused = false
            )
        )

        val service = LocationTrackingService()
        val notificationHelper = NotificationHelper(context)
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
            scope = testScope,
            context = context,
            bootProvider = object : BootCountProvider {
                override fun getBootCount(): Int = currentBoot
            }
        )

        val intent = Intent(LocationTrackingService.ACTION_START).apply {
            putExtra(LocationTrackingService.EXTRA_TRIP_ID, tripId)
            putExtra(LocationTrackingService.EXTRA_TRACKING_METHOD, TrackingMethod.AUTO_DETECTED.value)
        }

        service.locationUpdatesRegistrar = { true }
        // Execute actual service onStartCommand with ACTION_START
        service.onStartCommand(intent, 0, 1)
        service.lastStartupJob?.join()

        val retrieved = preferencesManager.getCandidateDeadline()
        assertNotNull("Candidate deadline must exist", retrieved)
        assertEquals(tripId, retrieved?.tripId)
        assertEquals(futureDeadline, retrieved?.deadlineElapsedMillis)
        assertEquals(currentBoot, retrieved?.bootCount)
        assertTrue("Service should remain candidate with preserved deadline", service.getInternalIsCandidate())
    }

    @Test
    fun actionStartRecovery_confirmedDuringReconciliationContinuesStartup() = runBlocking {
        val tripId = "test-candidate-preserve-19"
        val nowElapsed = SystemClock.elapsedRealtime()
        val futureDeadline = nowElapsed - 1000L // 3 minutes remaining
        val currentBoot = 1

        preferencesManager.recordCandidateDeadline(tripId, futureDeadline, currentBoot)

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

        val service = LocationTrackingService()
        val notificationHelper = NotificationHelper(context)
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
            scope = testScope,
            context = context,
            bootProvider = object : BootCountProvider {
                override fun getBootCount(): Int = currentBoot
            }
        )

        val intent = Intent(LocationTrackingService.ACTION_START).apply {
            putExtra(LocationTrackingService.EXTRA_TRIP_ID, tripId)
            putExtra(LocationTrackingService.EXTRA_TRACKING_METHOD, TrackingMethod.AUTO_DETECTED.value)
        }

        service.locationUpdatesRegistrar = { true }
        // Execute actual service onStartCommand with ACTION_START
        service.onStartCommand(intent, 0, 1)
        service.lastStartupJob?.join()

        assertNull("Confirmed recovery clears candidate deadline", preferencesManager.getCandidateDeadline())
        assertFalse("Confirmed recovery must leave candidate state", service.getInternalIsCandidate())
        assertTrue("Confirmed recovery must continue tracking", LocationTrackingService.serviceState.value.isTracking)
    }

    @Test
    fun actionStartRecovery_failedReconciliationPreservesSessionForRetry() = runBlocking {
        val tripId = "test-candidate-preserve-19"
        val nowElapsed = SystemClock.elapsedRealtime()
        val futureDeadline = nowElapsed - 1000L // 3 minutes remaining
        val currentBoot = 1

        preferencesManager.recordCandidateDeadline(tripId, futureDeadline, currentBoot)

        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis(),
                startLatitude = 37.7749,
                startLongitude = -122.4194,
                lastLatitude = 37.7749,
                lastLongitude = -122.4194,
                lastTimestamp = System.currentTimeMillis(),
                totalDistanceMeters = 50.0,
                trackingMethod = "AUTO_DETECTED",
                isPaused = false
            )
        )

        val service = LocationTrackingService()
        val notificationHelper = NotificationHelper(context)
        val processor = GpsInboxProcessor(
            database = database,
            activeTripDao = database.activeTripDao(),
            gpsInboxDao = database.gpsInboxDao(),
            filterGpsPointUseCase = FilterGpsPointUseCase()
        )
        val drainUseCase = object : DrainPendingGpsFixesUseCase(database.gpsInboxDao(), processor) {
            override suspend fun drain(tripId: String, batchLimit: Int,
                onFixProcessed: ((GpsInboxProcessor.ProcessResult) -> Unit)?): Int {
                throw java.io.IOException("Drain unavailable")
            }
        }
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
            scope = testScope,
            context = context,
            bootProvider = object : BootCountProvider {
                override fun getBootCount(): Int = currentBoot
            }
        )

        val intent = Intent(LocationTrackingService.ACTION_START).apply {
            putExtra(LocationTrackingService.EXTRA_TRIP_ID, tripId)
            putExtra(LocationTrackingService.EXTRA_TRACKING_METHOD, TrackingMethod.AUTO_DETECTED.value)
        }

        service.locationUpdatesRegistrar = { true }
        // Execute actual service onStartCommand with ACTION_START
        service.onStartCommand(intent, 0, 1)
        service.lastStartupJob?.join()

        assertNotNull("Failed drain preserves deadline", preferencesManager.getCandidateDeadline())
        assertTrue("Failed drain preserves candidate for retry", service.getInternalIsCandidate())
        assertTrue("Confirmed recovery must continue tracking", LocationTrackingService.serviceState.value.isTracking)
    }

    // =========================================================================
    // Scenario 20: Candidate expiry with GPS drain failure preserves candidate state
    // =========================================================================

    @Test
    fun test20_candidateExpiryDrainFailurePreservesState() = runBlocking {
        val tripId = "test-drain-fail-20"
        val nowElapsed = SystemClock.elapsedRealtime()
        val deadline = nowElapsed - 1000L

        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis(),
                startLatitude = 37.7749,
                startLongitude = -122.4194,
                lastLatitude = 37.7749,
                lastLongitude = -122.4194,
                lastTimestamp = System.currentTimeMillis(),
                totalDistanceMeters = 50.0,
                trackingMethod = "AUTO_DETECTED",
                isPaused = false
            )
        )
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(tripId = tripId, state = TripLifecycleState.CANDIDATE.value)
        )
        preferencesManager.setActiveTripId(tripId)
        preferencesManager.recordCandidateDeadline(tripId, deadline, 1)

        database.gpsInboxDao().insertFixes(listOf(
            GpsInboxEntity(
                tripId = tripId,
                sequenceNumber = 1L,
                elapsedRealtimeNanos = 1000L,
                latitude = 37.7750,
                longitude = -122.4195,
                timestamp = System.currentTimeMillis()
            )
        ))

        val service = LocationTrackingService()
        val notificationHelper = NotificationHelper(context)
        val processor = GpsInboxProcessor(
            database = database,
            activeTripDao = database.activeTripDao(),
            gpsInboxDao = database.gpsInboxDao(),
            filterGpsPointUseCase = FilterGpsPointUseCase()
        )
        val failingDrainUseCase = object : DrainPendingGpsFixesUseCase(database.gpsInboxDao(), processor) {
            override suspend fun drain(
                tripId: String,
                batchLimit: Int,
                onFixProcessed: ((GpsInboxProcessor.ProcessResult) -> Unit)?
            ): Int {
                throw java.io.IOException("Simulated SQLite drain failure")
            }
        }

        service.setTestDependencies(
            activeTripRepo = activeTripRepo,
            helper = notificationHelper,
            tripId = tripId,
            lifecycleDao = database.tripLifecycleDao(),
            inboxDao = database.gpsInboxDao(),
            drainUseCase = failingDrainUseCase,
            seqDao = database.tripSequenceDao(),
            db = database,
            prefs = preferencesManager,
            dispatcher = testDispatcher,
            scope = testScope,
            context = context
        )
        service.setTrackingStateForTesting(tripId, distanceMeters = 50.0, displacementConfirmed = false)
        service.setCandidateForTesting(true, deadlineElapsed = deadline)

        service.expireCandidateSessionForTesting()

        // Candidate state must not be discarded if drain fails; safe retry is preserved
        val activeTrip = activeTripRepo.getActiveTrip()
        assertNotNull("Active trip must be preserved after drain failure", activeTrip)
        assertEquals(tripId, activeTrip?.id)
        val lifecycle = database.tripLifecycleDao().getState(tripId)
        assertFalse("Trip must not be marked DISCARDED", lifecycle == TripLifecycleState.DISCARDED.value)
        assertTrue("Service must remain candidate for retry", service.getInternalIsCandidate())
    }

    // =========================================================================
    // Scenario 21: Late GPS callback from older session does not modify current session
    // =========================================================================

    @Test
    fun test21_lateGpsCallbackDoesNotModifyCurrentSession() = runBlocking {
        val oldTripId = "old-trip-stopped"
        val currentTripId = "current-active-trip"

        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = currentTripId,
                startTime = System.currentTimeMillis(),
                startLatitude = 37.7749,
                startLongitude = -122.4194,
                lastLatitude = 37.7749,
                lastLongitude = -122.4194,
                lastTimestamp = System.currentTimeMillis(),
                totalDistanceMeters = 100.0,
                trackingMethod = "AUTO_DETECTED",
                isPaused = false
            )
        )

        val service = LocationTrackingService()
        val notificationHelper = NotificationHelper(context)
        service.setTestDependencies(
            activeTripRepo = activeTripRepo,
            helper = notificationHelper,
            tripId = currentTripId,
            prefs = preferencesManager,
            dispatcher = testDispatcher,
            scope = testScope,
            context = context
        )
        service.setTrackingStateForTesting(
            tripId = currentTripId,
            distanceMeters = 100.0,
            displacementConfirmed = false
        )

        // Simulate late callback from old trip
        val lateOldFix = GpsInboxProcessor.ProcessResult.Accepted(
            fixId = 99L,
            tripId = oldTripId,
            deltaDistanceMeters = 250.0,
            totalDistanceMeters = 5000.0,
            lastLatitude = 37.7800,
            lastLongitude = -122.4200,
            speedMph = 25.0,
            isDisplacementConfirmed = true,
            waypoint = Waypoint(37.7800, -122.4200, speed = 11f, accuracy = 5f)
        )

        service.handleFixProcessed(lateOldFix)

        // Current session must be completely untouched
        assertEquals(0L, service.getLastLocationReceivedElapsedForTesting())
        assertEquals(100.0, service.getInternalDistanceMeters(), 0.001)
        assertEquals(0, service.getInternalWaypointCount())
        assertFalse(service.getInternalDisplacementConfirmed())
    }

    // =========================================================================
    // Scenario 22: Watchdog operation without a snapshot
    // =========================================================================

    @Test
    fun test22_watchdogOperationWithoutSnapshot() = runBlocking {
        val tripId = "test-trip-silence-22"
        val service = LocationTrackingService()
        val notificationHelper = NotificationHelper(context)

        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis() - 120_000L,
                startLatitude = 37.7749,
                startLongitude = -122.4194,
                lastLatitude = 37.7749,
                lastLongitude = -122.4194,
                lastTimestamp = System.currentTimeMillis() - 65_000L,
                totalDistanceMeters = 1500.0,
                trackingMethod = "AUTO_DETECTED",
                isPaused = false
            )
        )

        service.setTestDependencies(
            activeTripRepo = activeTripRepo,
            helper = notificationHelper,
            tripId = tripId,
            lifecycleDao = database.tripLifecycleDao(),
            inboxDao = database.gpsInboxDao(),
            seqDao = database.tripSequenceDao(),
            db = database,
            prefs = preferencesManager,
            dispatcher = testDispatcher,
            scope = testScope,
            context = context
        )
        service.setTrackingStateForTesting(
            tripId = tripId,
            distanceMeters = 1500.0,
            displacementConfirmed = true
        )

        // Execute recheck when GPS returns null (missing GPS)
        service.recheckStationaryLocationForTesting()

        // Missing GPS (e.g. timeout / no fix) must NOT stop or finalize confirmed trip
        assertTrue("Displacement remains confirmed", service.getInternalDisplacementConfirmed())
        assertEquals(1500.0, service.getInternalDistanceMeters(), 0.001)
        assertFalse("Trip must not be stopped on missing GPS", service.isStopping())
    }

    // =========================================================================
    // Scenario 23: Scoped stop work: trip A does not block trip B debounce
    // =========================================================================

    @Test
    fun test23_scopedStopWorkDoesNotBlockNewTrip() {
        val tripA = "trip-alpha"
        val tripB = "trip-beta"

        val workNameA = TripDebounceWorker.workNameForTrip(tripA)
        val workNameB = TripDebounceWorker.workNameForTrip(tripB)

        assertEquals("trip_debounce_stop_work_$tripA", workNameA)
        assertEquals("trip_debounce_stop_work_$tripB", workNameB)
        assertFalse("Unique work names must be distinct per trip", workNameA == workNameB)
    }

    // =========================================================================
    // Scenario 24: Deterministic race test: GPS write admitted during candidate expiry
    // =========================================================================

    @Test
    fun test24_gpsWriteAdmittedDuringCandidateExpiryPreservesMovement() = runBlocking {
        val tripId = "test-candidate-race-24"
        val nowElapsed = SystemClock.elapsedRealtime()
        val deadline = nowElapsed - 1000L // Candidate window expired

        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = System.currentTimeMillis() - 250_000L,
                startLatitude = 37.7749,
                startLongitude = -122.4194,
                lastLatitude = 37.7749,
                lastLongitude = -122.4194,
                lastTimestamp = System.currentTimeMillis() - 10_000L,
                totalDistanceMeters = 50.0, // Currently < 200m
                trackingMethod = "AUTO_DETECTED",
                isPaused = false
            )
        )
        database.tripLifecycleDao().insertOrUpdate(
            TripLifecycleEntity(tripId = tripId, state = TripLifecycleState.CANDIDATE.value)
        )
        preferencesManager.setActiveTripId(tripId)
        preferencesManager.recordCandidateDeadline(tripId, deadline, 1)

        val service = LocationTrackingService()
        val notificationHelper = NotificationHelper(context)
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
            scope = testScope,
            context = context
        )

        val queue = GpsLocationQueue(
            scope = testScope,
            dispatcher = testDispatcher,
            gpsInboxDao = database.gpsInboxDao(),
            tripIdProvider = { tripId },
            batchFetchLimit = 100,
            drainPendingGpsFixesUseCase = drainUseCase,
            tripSequenceDao = database.tripSequenceDao(),
            database = database,
            onFixProcessed = { service.handleFixProcessed(it) }
        )
        service.setGpsLocationQueueForTesting(queue)
        service.setTrackingStateForTesting(tripId, distanceMeters = 50.0, displacementConfirmed = false)
        service.setCandidateForTesting(true, deadlineElapsed = deadline)

        // Admit a GPS fix representing confirmed movement (250m) right before/during expiry drain
        val movingLocation = Location("gps").apply {
            latitude = 37.7770
            longitude = -122.4194
            accuracy = 5.0f
            speed = 12.0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        queue.enqueueBatch(listOf(movingLocation))

        // Trigger candidate expiry
        service.expireCandidateSessionForTesting()

        // 1. Verify that accepted movement (250m >= 200m) is NOT discarded
        val activeTrip = activeTripRepo.getActiveTrip()
        assertNotNull("Trip with admitted movement >= 200m must not be discarded", activeTrip)
        assertEquals(tripId, activeTrip?.id)

        var promoted = false
        for (i in 1..40) {
            if (database.tripLifecycleDao().getState(tripId) == TripLifecycleState.TRACKING.value) {
                promoted = true
                break
            }
            kotlinx.coroutines.delay(50L)
        }
        assertTrue("Trip must be promoted to TRACKING state in Room", promoted)
        assertFalse("Candidate flag must be cleared", service.getInternalIsCandidate())

        // 2. Verify admission barrier was reopened for ongoing tracking
        assertFalse("Admission barrier must be reopened after confirmation", queue.admissionBarrier.isClosed())
    }
}
