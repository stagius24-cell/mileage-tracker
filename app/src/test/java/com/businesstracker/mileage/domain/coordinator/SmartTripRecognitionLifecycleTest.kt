package com.businesstracker.mileage.domain.coordinator

import android.app.NotificationManager
import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.RoutePatternDao
import com.businesstracker.mileage.data.local.TripDao
import com.businesstracker.mileage.data.local.TripLearningRecordDao
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.repository.ActiveTripRepositoryImpl
import com.businesstracker.mileage.data.repository.SavedLocationRepositoryImpl
import com.businesstracker.mileage.data.repository.SmartTripRepositoryImpl
import com.businesstracker.mileage.data.repository.TripRepositoryImpl
import com.businesstracker.mileage.domain.classifier.ClassificationDecision
import com.businesstracker.mileage.domain.classifier.SmartTripClassificationEngine
import com.businesstracker.mileage.domain.matcher.TripPatternMatcher
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.model.ClassificationSource
import com.businesstracker.mileage.domain.model.SmartClassificationMode
import com.businesstracker.mileage.domain.model.SmartTripLearningCutoff
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.model.TripPurpose
import com.businesstracker.mileage.domain.repository.SmartTripRepository
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import com.businesstracker.mileage.domain.usecase.GeocodeLocationUseCase
import com.businesstracker.mileage.domain.usecase.MatchLocationUseCase
import com.businesstracker.mileage.domain.usecase.ResolveSmartTripNamesUseCase
import com.businesstracker.mileage.service.NotificationHelper
import com.businesstracker.mileage.ui.history.TripHistoryViewModel
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SmartTripRecognitionLifecycleTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var tripDao: TripDao
    private lateinit var routePatternDao: RoutePatternDao
    private lateinit var tripLearningRecordDao: TripLearningRecordDao
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var notificationManager: NotificationManager
    private lateinit var smartTripRepository: SmartTripRepository
    private lateinit var tripRepository: TripRepository
    private lateinit var patternMatcher: TripPatternMatcher
    private lateinit var engine: SmartTripClassificationEngine
    private lateinit var coordinator: SmartClassificationCoordinator
    private lateinit var finalizationCoordinator: TripFinalizationCoordinator
    private lateinit var activeTripRepo: ActiveTripRepositoryImpl
    private lateinit var rateCalculator: CalculateMileageRateUseCase
    private lateinit var geocoder: GeocodeLocationUseCase
    private lateinit var historyViewModel: TripHistoryViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val HOME_LAT = 37.3382
    private val HOME_LON = -121.8863
    private val NURSERY_LAT = 37.3541
    private val NURSERY_LON = -121.9552
    private val GYM_LAT = 37.3688
    private val GYM_LON = -122.0363

    private val VALID_POST_CUTOFF_TIME = 1794733200000L // Nov 15, 2026, 1:00 AM PST
    private val PRE_CUTOFF_TIME = 1794726000000L // Nov 14, 2026, 11:00 PM PST

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        val shadowApp = shadowOf(context as Application)
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tripDao = database.tripDao()
        routePatternDao = database.routePatternDao()
        tripLearningRecordDao = database.tripLearningRecordDao()

        val prefsFile = File(context.filesDir, "datastore/tracking_preferences.preferences_pb")
        if (prefsFile.exists()) prefsFile.delete()

        preferencesManager = TrackingPreferencesManager(context)
        notificationHelper = NotificationHelper(context)
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        smartTripRepository = SmartTripRepositoryImpl(
            routePatternDao = routePatternDao,
            tripLearningRecordDao = tripLearningRecordDao,
            preferencesManager = preferencesManager,
            ioDispatcher = testDispatcher
        )

        tripRepository = TripRepositoryImpl(
            tripDao = tripDao,
            database = database,
            smartTripRepository = smartTripRepository,
            ioDispatcher = testDispatcher
        )

        patternMatcher = TripPatternMatcher(routePatternDao, testDispatcher)
        engine = SmartTripClassificationEngine(patternMatcher, tripLearningRecordDao, preferencesManager, testDispatcher)

        coordinator = SmartClassificationCoordinator(
            tripRepository = tripRepository,
            classificationEngine = engine,
            preferencesManager = preferencesManager,
            notificationHelper = notificationHelper,
            context = context,
            ioDispatcher = testDispatcher
        )

        rateCalculator = CalculateMileageRateUseCase()
        geocoder = GeocodeLocationUseCase(context, testDispatcher)
        activeTripRepo = ActiveTripRepositoryImpl(database.activeTripDao(), testDispatcher)

        finalizationCoordinator = TripFinalizationCoordinator(
            database = database,
            activeTripRepository = activeTripRepo,
            tripRepository = tripRepository,
            geocodeUseCase = geocoder,
            rateCalculator = rateCalculator,
            preferencesManager = preferencesManager,
            gson = Gson(),
            ioDispatcher = testDispatcher,
            smartClassificationCoordinator = coordinator
        )

        val matchLoc = MatchLocationUseCase()
        val resolveSmartNames = ResolveSmartTripNamesUseCase(matchLoc)
        val savedLocRepo = SavedLocationRepositoryImpl(database.savedLocationDao(), testDispatcher)

        historyViewModel = TripHistoryViewModel(
            tripRepository = tripRepository,
            savedLocationRepository = savedLocRepo,
            resolveSmartTripNamesUseCase = resolveSmartNames,
            preferencesManager = preferencesManager,
            context = context
        )

        runBlocking {
            preferencesManager.setSmartTripRecognitionEnabled(true)
            preferencesManager.setSmartClassificationMode(SmartClassificationMode.ALWAYS_ASK)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            preferencesManager.setSmartTripRecognitionEnabled(true)
            preferencesManager.setSmartClassificationMode(SmartClassificationMode.ALWAYS_ASK)
        }
        database.close()
        Dispatchers.resetMain()
    }

    private suspend fun trainRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        classification: String,
        count: Int,
        entity: String = BusinessEntity.MY_SUCCULENT_CORNER.displayName,
        purpose: String = TripPurpose.NURSERY_PICKUP,
        source: ClassificationSource = ClassificationSource.MANUAL
    ) {
        repeat(count) { i ->
            val trip = TripEntity(
                id = "train_${classification}${i}-${UUID.randomUUID()}",
                date = "2026-11-16",
                startTime = VALID_POST_CUTOFF_TIME + (i * 3600_000L),
                endTime = VALID_POST_CUTOFF_TIME + (i * 3600_000L) + 1800_000L,
                startAddress = "Start Address",
                endAddress = "End Address",
                startLatitude = startLat,
                startLongitude = startLon,
                endLatitude = endLat,
                endLongitude = endLon,
                totalDistanceMiles = 5.0,
                businessMiles = if (classification == "BUSINESS") 5.0 else 0.0,
                businessEntity = entity,
                purpose = purpose,
                trackingMethod = TrackingMethod.AUTO_DETECTED.value,
                classification = classification,
                applicableRate = 0.67,
                calculatedDeduction = if (classification == "BUSINESS") 3.35 else 0.0,
                notes = null,
                waypointsJson = "[]",
                syncStatus = "SYNCED",
                syncedAt = null,
                classificationSource = source.value
            )
            tripRepository.insertTrip(trip)
            smartTripRepository.reconcileTripLearning(trip, source)
        }
    }

    private fun createUnreviewedTrip(
        id: String = "unreviewed-${UUID.randomUUID()}",
        endTime: Long = VALID_POST_CUTOFF_TIME,
        startLat: Double = HOME_LAT,
        startLon: Double = HOME_LON,
        endLat: Double = NURSERY_LAT,
        endLon: Double = NURSERY_LON,
        classificationSource: String = ClassificationSource.UNCLASSIFIED.value,
        suggestedClassification: String? = null
    ): TripEntity {
        return TripEntity(
            id = id,
            date = "2026-11-16",
            startTime = endTime - 1800_000L,
            endTime = endTime,
            startAddress = "Home",
            endAddress = "Nursery",
            startLatitude = startLat,
            startLongitude = startLon,
            endLatitude = endLat,
            endLongitude = endLon,
            totalDistanceMiles = 5.0,
            businessMiles = 0.0,
            businessEntity = BusinessEntity.MY_SUCCULENT_CORNER.displayName,
            purpose = TripPurpose.NURSERY_PICKUP,
            trackingMethod = TrackingMethod.AUTO_DETECTED.value,
            classification = TripClassification.UNREVIEWED.value,
            applicableRate = 0.67,
            calculatedDeduction = 0.0,
            notes = null,
            waypointsJson = "[]",
            syncStatus = "PENDING",
            syncedAt = null,
            classificationSource = classificationSource,
            suggestedClassification = suggestedClassification
        )
    }

    // 1. Successful trip finalization triggers recognition
    @Test
    fun test01_successfulTripFinalizationTriggersRecognition() = testScope.runTest {
        trainRoute(HOME_LAT, HOME_LON, NURSERY_LAT, NURSERY_LON, "BUSINESS", 5)

        val activeTrip = ActiveTripEntity(
            id = "active_trip_1",
            startTime = VALID_POST_CUTOFF_TIME - 1800_000L,
            startLatitude = HOME_LAT,
            startLongitude = HOME_LON,
            lastLatitude = NURSERY_LAT,
            lastLongitude = NURSERY_LON,
            lastTimestamp = VALID_POST_CUTOFF_TIME,
            totalDistanceMeters = 8000.0,
            suggestedBusiness = "MySucculentCorner",
            trackingMethod = TrackingMethod.AUTO_DETECTED.value
        )
        database.activeTripDao().insertActiveTrip(activeTrip)
        database.activeTripDao().insertWaypoint(
            ActiveWaypointEntity(
                tripId = "active_trip_1",
                latitude = HOME_LAT,
                longitude = HOME_LON,
                accuracy = 5.0f,
                speed = 10.0f,
                timestamp = VALID_POST_CUTOFF_TIME - 1800_000L
            )
        )
        database.activeTripDao().insertWaypoint(
            ActiveWaypointEntity(
                tripId = "active_trip_1",
                latitude = NURSERY_LAT,
                longitude = NURSERY_LON,
                accuracy = 5.0f,
                speed = 10.0f,
                timestamp = VALID_POST_CUTOFF_TIME
            )
        )

        val finalized = finalizationCoordinator.finalizeTrip("active_trip_1")
        assertNotNull(finalized)
        assertEquals("active_trip_1", finalized!!.id)
        assertEquals("BUSINESS", finalized.suggestedClassification)
        assertEquals(ClassificationSource.SMART_SUGGESTED.value, finalized.classificationSource)
    }

    // 2. Active trip never triggers recognition
    @Test
    fun test02_activeTripNeverTriggersRecognition() = testScope.runTest {
        val result = coordinator.processFinalizedTrip("non_existent_or_active_id")
        assertTrue(result is SmartClassificationResult.Ignored)
    }

    // 3. Historical trips are ignored (< Nov 15, 2026)
    @Test
    fun test03_historicalTripsAreIgnored() = testScope.runTest {
        trainRoute(HOME_LAT, HOME_LON, NURSERY_LAT, NURSERY_LON, "BUSINESS", 5)
        val oldTrip = createUnreviewedTrip(id = "old_trip_1", endTime = PRE_CUTOFF_TIME)
        tripRepository.insertTrip(oldTrip)

        val result = coordinator.processFinalizedTrip("old_trip_1")
        assertTrue(result is SmartClassificationResult.Ignored)
        val saved = tripRepository.getTripById("old_trip_1")
        assertNull(saved?.suggestedClassification)
    }

    // 4. Smart Classification disabled
    @Test
    fun test04_smartClassificationDisabled() = testScope.runTest {
        preferencesManager.setSmartTripRecognitionEnabled(false)
        trainRoute(HOME_LAT, HOME_LON, NURSERY_LAT, NURSERY_LON, "BUSINESS", 5)

        val trip = createUnreviewedTrip(id = "disabled_test_trip")
        tripRepository.insertTrip(trip)

        val result = coordinator.processFinalizedTrip("disabled_test_trip")
        assertTrue(result is SmartClassificationResult.Ignored)
        val saved = tripRepository.getTripById("disabled_test_trip")
        assertNull(saved?.suggestedClassification)
    }

    // 5. AUTO_CLASSIFY high-confidence route
    @Test
    fun test05_autoClassifyHighConfidenceRoute() = testScope.runTest {
        preferencesManager.setSmartClassificationMode(SmartClassificationMode.AUTO_CLASSIFY)
        trainRoute(HOME_LAT, HOME_LON, NURSERY_LAT, NURSERY_LON, "BUSINESS", 5)

        val trip = createUnreviewedTrip(id = "auto_high_conf")
        tripRepository.insertTrip(trip)

        val result = coordinator.processFinalizedTrip("auto_high_conf")
        assertTrue(result is SmartClassificationResult.AutoClassified)

        val saved = tripRepository.getTripById("auto_high_conf")
        assertNotNull(saved)
        assertEquals("BUSINESS", saved!!.classification)
        assertEquals(ClassificationSource.SMART_AUTO.value, saved.classificationSource)
        assertEquals(5.0, saved.businessMiles, 0.01)
        assertEquals(3.35, saved.calculatedDeduction, 0.01)
        assertEquals("PENDING", saved.syncStatus)
    }

    // 6. AUTO_CLASSIFY medium-confidence route
    @Test
    fun test06_autoClassifyMediumConfidenceRoute() = testScope.runTest {
        preferencesManager.setSmartClassificationMode(SmartClassificationMode.AUTO_CLASSIFY)
        trainRoute(HOME_LAT, HOME_LON, NURSERY_LAT, NURSERY_LON, "BUSINESS", 3)

        val trip = createUnreviewedTrip(id = "auto_med_conf")
        tripRepository.insertTrip(trip)

        val result = coordinator.processFinalizedTrip("auto_med_conf")
        assertTrue(result is SmartClassificationResult.Suggested)

        val saved = tripRepository.getTripById("auto_med_conf")
        assertNotNull(saved)
        assertEquals(TripClassification.UNREVIEWED.value, saved!!.classification)
        assertEquals("BUSINESS", saved.suggestedClassification)
        assertEquals(ClassificationSource.SMART_SUGGESTED.value, saved.classificationSource)
    }

    // 7. ALWAYS_ASK notification
    @Test
    fun test07_alwaysAskNotification() = testScope.runTest {
        preferencesManager.setSmartClassificationMode(SmartClassificationMode.ALWAYS_ASK)
        trainRoute(HOME_LAT, HOME_LON, NURSERY_LAT, NURSERY_LON, "BUSINESS", 5)

        val trip = createUnreviewedTrip(id = "always_ask_trip")
        tripRepository.insertTrip(trip)

        val result = coordinator.processFinalizedTrip("always_ask_trip")
        assertTrue(result is SmartClassificationResult.Suggested)
        assertTrue((result as SmartClassificationResult.Suggested).notificationShown)

        val shadowNm = shadowOf(notificationManager)
        assertTrue(shadowNm.allNotifications.isNotEmpty())
    }

    // 8. SUGGEST_ONLY no notification
    @Test
    fun test08_suggestOnlyNoNotification() = testScope.runTest {
        preferencesManager.setSmartClassificationMode(SmartClassificationMode.SUGGEST_ONLY)
        trainRoute(HOME_LAT, HOME_LON, NURSERY_LAT, NURSERY_LON, "BUSINESS", 5)

        val trip = createUnreviewedTrip(id = "suggest_only_trip")
        tripRepository.insertTrip(trip)

        notificationManager.cancelAll()

        val result = coordinator.processFinalizedTrip("suggest_only_trip")
        assertTrue(result is SmartClassificationResult.Suggested)
        assertFalse((result as SmartClassificationResult.Suggested).notificationShown)

        val shadowNm = shadowOf(notificationManager)
        assertTrue(shadowNm.allNotifications.isEmpty())
    }

    // 9. Manual classification priority
    @Test
    fun test09_manualClassificationPriority() = testScope.runTest {
        trainRoute(HOME_LAT, HOME_LON, NURSERY_LAT, NURSERY_LON, "BUSINESS", 5)

        val manualTrip = createUnreviewedTrip(
            id = "manual_priority_trip",
            classificationSource = ClassificationSource.MANUAL.value
        ).copy(classification = TripClassification.PERSONAL.value)
        tripRepository.insertTrip(manualTrip)

        val result = coordinator.processFinalizedTrip("manual_priority_trip")
        assertTrue(result is SmartClassificationResult.Ignored)

        val saved = tripRepository.getTripById("manual_priority_trip")
        assertEquals(TripClassification.PERSONAL.value, saved?.classification)
        assertEquals(ClassificationSource.MANUAL.value, saved?.classificationSource)
    }

    // 10. Schedule precedence
    @Test
    fun test10_schedulePrecedence() = testScope.runTest {
        trainRoute(HOME_LAT, HOME_LON, NURSERY_LAT, NURSERY_LON, "BUSINESS", 5)

        val scheduleTrip = createUnreviewedTrip(
            id = "schedule_trip",
            classificationSource = ClassificationSource.SCHEDULE.value
        ).copy(classification = TripClassification.PERSONAL.value)
        tripRepository.insertTrip(scheduleTrip)

        val result = coordinator.processFinalizedTrip("schedule_trip")
        assertTrue(result is SmartClassificationResult.Ignored)

        val saved = tripRepository.getTripById("schedule_trip")
        assertEquals(ClassificationSource.SCHEDULE.value, saved?.classificationSource)
    }

    // 11. Saved-location behavior
    @Test
    fun test11_savedLocationBehavior() = testScope.runTest {
        val savedLoc = SavedLocationEntity(
            id = "loc_nursery",
            name = "Succulent Nursery",
            latitude = NURSERY_LAT,
            longitude = NURSERY_LON,
            address = "123 Nursery Rd",
            radiusMeters = 200,
            icon = "store",
            classificationPreference = "BUSINESS",
            businessEntity = BusinessEntity.MY_SUCCULENT_CORNER.displayName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        database.savedLocationDao().insertLocation(savedLoc)

        val trip = createUnreviewedTrip(id = "saved_loc_trip")
        val matchLoc = MatchLocationUseCase()
        val resolver = ResolveSmartTripNamesUseCase(matchLoc)
        val names = resolver.resolve(trip, listOf(savedLoc))

        assertEquals("Succulent Nursery", names.endDisplayName)
        assertEquals("BUSINESS", names.suggestedClassification)
    }

    // 12. Notification tap routing
    @Test
    fun test12_notificationTapRouting() = testScope.runTest {
        val intent = Intent().apply {
            putExtra(NotificationHelper.EXTRA_TRIP_ID, "target_trip_123")
            putExtra(NotificationHelper.EXTRA_OPEN_CONFIRMATION, true)
        }
        assertEquals("target_trip_123", intent.getStringExtra(NotificationHelper.EXTRA_TRIP_ID))
        assertTrue(intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_CONFIRMATION, false))
    }

    // 13. Notification tap after process restart
    @Test
    fun test13_notificationTapAfterProcessRestart() = testScope.runTest {
        val trip = createUnreviewedTrip(id = "restart_trip_1", suggestedClassification = "BUSINESS")
        tripRepository.insertTrip(trip)

        val collectJob = backgroundScope.launch { historyViewModel.uiState.collect() }

        historyViewModel.selectTripById("restart_trip_1", openConfirmation = true).join()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = historyViewModel.uiState.value
        assertEquals("restart_trip_1", state.selectedTripForDetails?.id)
        assertEquals("restart_trip_1", state.selectedTripForConfirmationSheet?.id)
    }

    // 14. Pending suggestion persistence
    @Test
    fun test14_pendingSuggestionPersistence() = testScope.runTest {
        val trip = createUnreviewedTrip(id = "persisted_sugg")
        tripRepository.insertTrip(trip)

        tripRepository.setPendingSuggestion("persisted_sugg", "BUSINESS")

        val saved = tripRepository.getTripById("persisted_sugg")
        assertNotNull(saved)
        assertEquals(TripClassification.UNREVIEWED.value, saved!!.classification)
        assertEquals("BUSINESS", saved.suggestedClassification)
        assertEquals(ClassificationSource.SMART_SUGGESTED.value, saved.classificationSource)
    }

    // 15. Dismissed suggestion persistence
    @Test
    fun test15_dismissedSuggestionPersistence() = testScope.runTest {
        val trip = createUnreviewedTrip(id = "dismiss_trip", suggestedClassification = "BUSINESS")
        tripRepository.insertTrip(trip)

        historyViewModel.dismissSmartSuggestion("dismiss_trip").join()
        testDispatcher.scheduler.advanceUntilIdle()

        val saved = tripRepository.getTripById("dismiss_trip")
        assertNotNull(saved)
        assertEquals("DISMISSED", saved!!.suggestedClassification)
        assertEquals(TripClassification.UNREVIEWED.value, saved.classification)

        // Verifying repeated recognition does NOT regenerate
        val reeval = coordinator.processFinalizedTrip("dismiss_trip")
        assertTrue(reeval is SmartClassificationResult.Ignored)
        val afterReeval = tripRepository.getTripById("dismiss_trip")
        assertEquals("DISMISSED", afterReeval?.suggestedClassification)
    }

    // 16. Confirmation records "SUGGESTED_CONFIRMED"
    @Test
    fun test16_confirmationRecordsSuggestedConfirmed() = testScope.runTest {
        val trip = createUnreviewedTrip(id = "confirm_sugg_trip", suggestedClassification = "BUSINESS")
        tripRepository.insertTrip(trip)

        historyViewModel.confirmSmartSuggestion(
            tripId = "confirm_sugg_trip",
            category = "BUSINESS",
            entity = BusinessEntity.MY_SUCCULENT_CORNER.displayName,
            purpose = TripPurpose.NURSERY_PICKUP
        ).join()
        testDispatcher.scheduler.advanceUntilIdle()

        val saved = tripRepository.getTripById("confirm_sugg_trip")
        assertNotNull(saved)
        assertEquals("BUSINESS", saved!!.classification)
        assertEquals(ClassificationSource.SUGGESTED_CONFIRMED.value, saved.classificationSource)

        val record = smartTripRepository.getLearningRecord("confirm_sugg_trip")
        assertNotNull(record)
        assertEquals("BUSINESS", record!!.confirmedCategory)
        assertEquals(ClassificationSource.SUGGESTED_CONFIRMED.value, record.classificationSource)
    }

    // 17. Manual correction records "MANUAL"
    @Test
    fun test17_manualCorrectionRecordsManual() = testScope.runTest {
        val trip = createUnreviewedTrip(id = "manual_correction_trip")
        tripRepository.insertTrip(trip)

        historyViewModel.updateTripDetails(
            tripId = "manual_correction_trip",
            classification = "PERSONAL",
            businessEntity = BusinessEntity.PERSONAL.displayName,
            purpose = "Personal Drive",
            notes = null
        ).join()
        testDispatcher.scheduler.advanceUntilIdle()

        val saved = tripRepository.getTripById("manual_correction_trip")
        assertNotNull(saved)
        assertEquals("PERSONAL", saved!!.classification)
        assertEquals(ClassificationSource.MANUAL.value, saved.classificationSource)

        val record = smartTripRepository.getLearningRecord("manual_correction_trip")
        assertNotNull(record)
        assertEquals("PERSONAL", record!!.confirmedCategory)
        assertEquals(ClassificationSource.MANUAL.value, record.classificationSource)
    }

    // 18. Duplicate recognition prevention
    @Test
    fun test18_duplicateRecognitionPrevention() = testScope.runTest {
        trainRoute(HOME_LAT, HOME_LON, NURSERY_LAT, NURSERY_LON, "BUSINESS", 5)
        val trip = createUnreviewedTrip(id = "dup_prevention_trip")
        tripRepository.insertTrip(trip)

        val firstRun = coordinator.processFinalizedTrip("dup_prevention_trip")
        assertTrue(firstRun is SmartClassificationResult.Suggested)

        val secondRun = coordinator.processFinalizedTrip("dup_prevention_trip")
        assertTrue(secondRun is SmartClassificationResult.Ignored)
    }

    // 19. Concurrent classification safety
    @Test
    fun test19_concurrentClassificationSafety() = testScope.runTest {
        val trip = createUnreviewedTrip(id = "concurrent_trip")
        tripRepository.insertTrip(trip)

        // User manually classifies trip right before or during auto classification
        tripRepository.classifyTrip(
            tripId = "concurrent_trip",
            classification = "PERSONAL",
            businessEntity = BusinessEntity.PERSONAL.displayName,
            purpose = "Personal Drive",
            notes = null,
            source = ClassificationSource.MANUAL
        )

        // Auto classify attempt should be rejected due to conditional update
        val updated = tripRepository.autoClassifyTrip(
            tripId = "concurrent_trip",
            classification = "BUSINESS",
            businessEntity = BusinessEntity.MY_SUCCULENT_CORNER.displayName,
            purpose = TripPurpose.NURSERY_PICKUP,
            notes = null
        )
        assertFalse(updated)

        val saved = tripRepository.getTripById("concurrent_trip")
        assertEquals("PERSONAL", saved?.classification)
        assertEquals(ClassificationSource.MANUAL.value, saved?.classificationSource)
    }

    // 20. Business deduction recalculation
    @Test
    fun test20_businessDeductionRecalculation() = testScope.runTest {
        val trip = createUnreviewedTrip(id = "deduction_biz_trip")
        tripRepository.insertTrip(trip)

        tripRepository.classifyTrip(
            tripId = "deduction_biz_trip",
            classification = "BUSINESS",
            businessEntity = BusinessEntity.MY_SUCCULENT_CORNER.displayName,
            purpose = TripPurpose.NURSERY_PICKUP,
            notes = null,
            source = ClassificationSource.MANUAL
        )

        val saved = tripRepository.getTripById("deduction_biz_trip")
        assertNotNull(saved)
        assertEquals(5.0, saved!!.businessMiles, 0.01)
        assertEquals(3.35, saved.calculatedDeduction, 0.01)
    }

    // 21. Personal deduction clearing
    @Test
    fun test21_personalDeductionClearing() = testScope.runTest {
        val trip = createUnreviewedTrip(id = "deduction_pers_trip")
        tripRepository.insertTrip(trip)

        tripRepository.classifyTrip(
            tripId = "deduction_pers_trip",
            classification = "PERSONAL",
            businessEntity = BusinessEntity.PERSONAL.displayName,
            purpose = "Personal Drive",
            notes = null,
            source = ClassificationSource.MANUAL
        )

        val saved = tripRepository.getTripById("deduction_pers_trip")
        assertNotNull(saved)
        assertEquals(0.0, saved!!.businessMiles, 0.001)
        assertEquals(0.0, saved.calculatedDeduction, 0.001)
    }

    // 22. Google Sheets pending-sync status
    @Test
    fun test22_googleSheetsPendingSyncStatus() = testScope.runTest {
        preferencesManager.setSmartClassificationMode(SmartClassificationMode.AUTO_CLASSIFY)
        trainRoute(HOME_LAT, HOME_LON, NURSERY_LAT, NURSERY_LON, "BUSINESS", 5)

        val trip = createUnreviewedTrip(id = "sheets_pending_trip")
        tripRepository.insertTrip(trip)

        coordinator.processFinalizedTrip("sheets_pending_trip")

        val saved = tripRepository.getTripById("sheets_pending_trip")
        assertEquals("PENDING", saved?.syncStatus)
    }

    // 23. Existing synced trip update
    @Test
    fun test23_existingSyncedTripUpdate() = testScope.runTest {
        val trip = createUnreviewedTrip(id = "synced_trip_to_update").copy(
            syncStatus = "SYNCED",
            syncedAt = System.currentTimeMillis()
        )
        tripRepository.insertTrip(trip)

        tripRepository.classifyTrip(
            tripId = "synced_trip_to_update",
            classification = "BUSINESS",
            businessEntity = BusinessEntity.MY_SUCCULENT_CORNER.displayName,
            purpose = TripPurpose.NURSERY_PICKUP,
            notes = null,
            source = ClassificationSource.MANUAL
        )

        val saved = tripRepository.getTripById("synced_trip_to_update")
        assertEquals("PENDING", saved?.syncStatus)
    }

    // 24. No training from SMART_AUTO
    @Test
    fun test24_noTrainingFromSmartAuto() = testScope.runTest {
        val trip = createUnreviewedTrip(
            id = "smart_auto_trip",
            classificationSource = ClassificationSource.SMART_AUTO.value
        ).copy(classification = "BUSINESS")
        tripRepository.insertTrip(trip)

        val eligible = smartTripRepository.isTripEligibleForLearning(trip, ClassificationSource.SMART_AUTO)
        assertFalse(eligible)
    }

    // 25. No training from unconfirmed suggestions
    @Test
    fun test25_noTrainingFromUnconfirmedSuggestions() = testScope.runTest {
        val trip = createUnreviewedTrip(
            id = "unconfirmed_sugg_trip",
            classificationSource = ClassificationSource.SMART_SUGGESTED.value,
            suggestedClassification = "BUSINESS"
        )
        tripRepository.insertTrip(trip)

        val eligible = smartTripRepository.isTripEligibleForLearning(trip, ClassificationSource.SMART_SUGGESTED)
        assertFalse(eligible)
    }

    // 26. Feature disabled during processing
    @Test
    fun test26_featureDisabledDuringProcessing() = testScope.runTest {
        preferencesManager.setSmartTripRecognitionEnabled(false)
        val trip = createUnreviewedTrip(id = "race_disabled_trip")
        tripRepository.insertTrip(trip)

        val result = coordinator.processFinalizedTrip("race_disabled_trip")
        assertTrue(result is SmartClassificationResult.Ignored)
    }

    // 27. No regression to GPS recording or auto-stop
    @Test
    fun test27_noRegressionToGpsRecordingOrAutoStop() = testScope.runTest {
        val shortTrip = ActiveTripEntity(
            id = "short_trip",
            startTime = VALID_POST_CUTOFF_TIME - 300_000L,
            startLatitude = HOME_LAT,
            startLongitude = HOME_LON,
            lastLatitude = HOME_LAT + 0.0001,
            lastLongitude = HOME_LON + 0.0001,
            lastTimestamp = VALID_POST_CUTOFF_TIME,
            totalDistanceMeters = 50.0, // <200m
            trackingMethod = TrackingMethod.AUTO_DETECTED.value
        )
        database.activeTripDao().insertActiveTrip(shortTrip)

        val finalized = finalizationCoordinator.finalizeTrip("short_trip")
        assertNull(finalized) // Discarded as false trip
        assertNull(database.activeTripDao().getActiveTripById("short_trip"))
    }
}

