package com.businesstracker.mileage.domain.classifier

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.RoutePatternDao
import com.businesstracker.mileage.data.local.TripDao
import com.businesstracker.mileage.data.local.TripLearningRecordDao
import com.businesstracker.mileage.data.local.entity.RoutePatternEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.entity.TripLearningRecordEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.repository.SmartTripRepositoryImpl
import com.businesstracker.mileage.data.repository.TripRepositoryImpl
import com.businesstracker.mileage.domain.matcher.TripPatternMatcher
import com.businesstracker.mileage.domain.model.ClassificationSource
import com.businesstracker.mileage.domain.model.SmartClassificationMode
import com.businesstracker.mileage.domain.model.SmartTripLearningCutoff
import com.businesstracker.mileage.domain.repository.SmartTripRepository
import com.businesstracker.mileage.domain.repository.TripRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SmartTripClassificationEngineTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var tripDao: TripDao
    private lateinit var routePatternDao: RoutePatternDao
    private lateinit var tripLearningRecordDao: TripLearningRecordDao
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var matcher: TripPatternMatcher
    private lateinit var engine: SmartTripClassificationEngine
    private lateinit var smartTripRepository: SmartTripRepository
    private lateinit var tripRepository: TripRepository

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val HOME_LAT = 37.3382
    private val HOME_LON = -121.8863
    private val NURSERY_LAT = 37.4419
    private val NURSERY_LON = -122.1430
    private val CUTOFF = SmartTripLearningCutoff.ACTIVATION_CUTOFF_MILLIS

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        tripDao = database.tripDao()
        routePatternDao = database.routePatternDao()
        tripLearningRecordDao = database.tripLearningRecordDao()
        preferencesManager = TrackingPreferencesManager(context)

        runBlocking {
            preferencesManager.setSmartTripRecognitionEnabled(true)
            preferencesManager.setSmartClassificationMode(SmartClassificationMode.ALWAYS_ASK)
        }

        matcher = TripPatternMatcher(routePatternDao, testDispatcher)
        engine = SmartTripClassificationEngine(matcher, tripLearningRecordDao, preferencesManager, testDispatcher)

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
    }

    @After
    fun tearDown() {
        runBlocking {
            preferencesManager.setSmartTripRecognitionEnabled(true)
            preferencesManager.setSmartClassificationMode(SmartClassificationMode.ALWAYS_ASK)
        }
        database.close()
    }

    private fun createRoutePattern(
        id: String = "pattern-1",
        startLat: Double = HOME_LAT,
        startLon: Double = HOME_LON,
        endLat: Double = NURSERY_LAT,
        endLon: Double = NURSERY_LON,
        classification: String = "BUSINESS",
        businessEntity: String = "MySucculentCorner",
        purpose: String = "Nursery Delivery",
        sampleCount: Int = 5
    ): RoutePatternEntity {
        return RoutePatternEntity(
            id = id,
            startLatitude = startLat,
            startLongitude = startLon,
            startRadiusMeters = 200.0,
            endLatitude = endLat,
            endLongitude = endLon,
            endRadiusMeters = 200.0,
            startAddress = "Home",
            endAddress = "Nursery",
            classification = classification,
            businessEntity = businessEntity,
            purpose = purpose,
            sampleCount = sampleCount,
            confidence = 0.9,
            lastLearnedAt = CUTOFF + 100000L,
            createdAt = CUTOFF
        )
    }

    private suspend fun insertLearningRecord(
        tripId: String,
        patternId: String = "pattern-1",
        category: String = "BUSINESS",
        entity: String? = "MySucculentCorner",
        purpose: String? = "Nursery Delivery",
        source: String = ClassificationSource.MANUAL.value,
        eligible: Boolean = true
    ): TripLearningRecordEntity {
        val parentTrip = createTestTrip(id = tripId, classification = category, source = source)
        tripDao.insertTrip(parentTrip)

        val record = TripLearningRecordEntity(
            tripId = tripId,
            eligible = eligible,
            classificationSource = source,
            confirmedCategory = category,
            businessEntity = entity,
            purpose = purpose,
            startLatitude = HOME_LAT,
            startLongitude = HOME_LON,
            endLatitude = NURSERY_LAT,
            endLongitude = NURSERY_LON,
            patternId = patternId,
            learnedAt = CUTOFF + 10000L,
            createdAt = CUTOFF + 10000L
        )
        tripLearningRecordDao.insertRecord(record)
        return record
    }

    private fun createTestTrip(
        id: String = "test-trip-eval",
        startLat: Double = HOME_LAT,
        startLon: Double = HOME_LON,
        endLat: Double = NURSERY_LAT,
        endLon: Double = NURSERY_LON,
        distanceMiles: Double = 16.0,
        classification: String = "UNREVIEWED",
        source: String = "UNCLASSIFIED"
    ): TripEntity {
        return TripEntity(
            id = id,
            date = "2026-11-15",
            startTime = CUTOFF + 3600000L,
            endTime = CUTOFF + 7200000L,
            startAddress = "Home",
            endAddress = "Nursery",
            startLatitude = startLat,
            startLongitude = startLon,
            endLatitude = endLat,
            endLongitude = endLon,
            totalDistanceMiles = distanceMiles,
            businessMiles = 0.0,
            businessEntity = "",
            purpose = "",
            trackingMethod = "AUTO_DETECTED",
            classification = classification,
            applicableRate = 0.67,
            calculatedDeduction = 0.0,
            notes = null,
            waypointsJson = "[]",
            syncStatus = "PENDING",
            syncedAt = null,
            classificationSource = source,
            suggestedClassification = null
        )
    }

    // 1. Zero matching trips -> No suggestion
    @Test
    fun test01_zeroMatchingTrips_returnsNoSuggestion() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)
        // No records inserted in tripLearningRecordDao

        val trip = createTestTrip()
        val decision = engine.evaluateTrip(trip)

        assertTrue(decision is ClassificationDecision.NoSuggestion)
        assertEquals(0, decision.confirmedTripCount)
        assertTrue(decision.reason.contains("minimum 3 required", ignoreCase = true))
    }

    // 2. One or two matching trips -> No suggestion
    @Test
    fun test02_oneOrTwoMatchingTrips_returnsNoSuggestion() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)

        insertLearningRecord("trip-1", pattern.id, "BUSINESS")
        insertLearningRecord("trip-2", pattern.id, "BUSINESS")

        val trip = createTestTrip()
        val decision = engine.evaluateTrip(trip)

        assertTrue(decision is ClassificationDecision.NoSuggestion)
        assertEquals(2, decision.confirmedTripCount)
        assertEquals(2, decision.businessCount)
        assertTrue(decision.reason.contains("minimum 3 required", ignoreCase = true))
    }

    // 3. Three consistent Business trips -> SuggestedClassification with explicit confirmation
    @Test
    fun test03_threeConsistentBusinessTrips_returnsSuggestedClassification() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)

        for (i in 1..3) {
            insertLearningRecord("trip-$i", pattern.id, "BUSINESS")
        }

        val trip = createTestTrip()
        val decision = engine.evaluateTrip(trip)

        assertTrue(decision is ClassificationDecision.SuggestedClassification)
        val suggestion = decision as ClassificationDecision.SuggestedClassification
        assertEquals("BUSINESS", suggestion.suggestedCategory)
        assertEquals(3, suggestion.confirmedTripCount)
        assertEquals(3, suggestion.businessCount)
        assertEquals(0, suggestion.personalCount)
        assertEquals(1.0, suggestion.agreementRatio, 0.0001)
        assertTrue("Must require explicit confirmation", suggestion.requiresExplicitConfirmation)
    }

    // 4. Three conflicting trips (2 Business, 1 Personal) -> No suggestion (100% agreement required for < 5)
    @Test
    fun test04_threeConflictingTrips_returnsNoSuggestion() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)

        insertLearningRecord("trip-1", pattern.id, "BUSINESS")
        insertLearningRecord("trip-2", pattern.id, "BUSINESS")
        insertLearningRecord("trip-3", pattern.id, "PERSONAL")

        val trip = createTestTrip()
        val decision = engine.evaluateTrip(trip)

        assertTrue(decision is ClassificationDecision.NoSuggestion)
        assertEquals(3, decision.confirmedTripCount)
        assertEquals(2, decision.businessCount)
        assertEquals(1, decision.personalCount)
        assertTrue(decision.reason.contains("100% agreement required", ignoreCase = true))
    }

    // 5. Four consistent Personal trips -> SuggestedClassification (PERSONAL)
    @Test
    fun test05_fourConsistentPersonalTrips_returnsSuggestedClassification() = testScope.runTest {
        val pattern = createRoutePattern(classification = "PERSONAL")
        routePatternDao.insertRoutePattern(pattern)

        for (i in 1..4) {
            insertLearningRecord("trip-$i", pattern.id, "PERSONAL", entity = null, purpose = null)
        }

        val trip = createTestTrip()
        val decision = engine.evaluateTrip(trip)

        assertTrue(decision is ClassificationDecision.SuggestedClassification)
        val suggestion = decision as ClassificationDecision.SuggestedClassification
        assertEquals("PERSONAL", suggestion.suggestedCategory)
        assertEquals(4, suggestion.confirmedTripCount)
        assertEquals(4, suggestion.personalCount)
        assertEquals(0, suggestion.businessCount)
        assertEquals(1.0, suggestion.agreementRatio, 0.0001)
        assertTrue(suggestion.requiresExplicitConfirmation)
    }

    // 6. Five consistent Business trips (100%) -> High confidence
    @Test
    fun test06_fiveConsistentBusinessTrips_returnsHighConfidence() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)

        for (i in 1..5) {
            insertLearningRecord("trip-$i", pattern.id, "BUSINESS")
        }

        // In ALWAYS_ASK mode (default), high confidence returns SuggestedClassification requiring confirmation
        val trip = createTestTrip()
        val decision = engine.evaluateTrip(trip)

        assertTrue(decision is ClassificationDecision.SuggestedClassification)
        val suggestion = decision as ClassificationDecision.SuggestedClassification
        assertEquals("BUSINESS", suggestion.suggestedCategory)
        assertEquals(5, suggestion.confirmedTripCount)
        assertEquals(1.0, suggestion.agreementRatio, 0.0001)
        assertTrue(suggestion.requiresExplicitConfirmation)
    }

    // 7. Four Business and one Personal (80% agreement) -> Medium-confidence rule (suggest only, confirmation required)
    @Test
    fun test07_fourBusinessAndOnePersonal_mediumConfidenceSuggestion() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)

        for (i in 1..4) {
            insertLearningRecord("trip-b-$i", pattern.id, "BUSINESS")
        }
        insertLearningRecord("trip-p-1", pattern.id, "PERSONAL")

        val trip = createTestTrip()
        // Evaluate in AUTO_CLASSIFY mode to verify medium-confidence does NOT auto-classify
        val decision = engine.evaluateTripWithMode(trip, SmartClassificationMode.AUTO_CLASSIFY)

        assertTrue("80% agreement (4/5) must produce SuggestedClassification, NEVER AutomaticClassification",
            decision is ClassificationDecision.SuggestedClassification
        )
        val suggestion = decision as ClassificationDecision.SuggestedClassification
        assertEquals("BUSINESS", suggestion.suggestedCategory)
        assertEquals(5, suggestion.confirmedTripCount)
        assertEquals(4, suggestion.businessCount)
        assertEquals(1, suggestion.personalCount)
        assertEquals(0.8, suggestion.agreementRatio, 0.0001)
        assertTrue("Medium confidence always requires explicit confirmation", suggestion.requiresExplicitConfirmation)
    }

    // 8. Nine Business and one Personal (90% agreement) -> High-confidence classification
    @Test
    fun test08_nineBusinessAndOnePersonal_highConfidenceClassification() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)

        for (i in 1..9) {
            insertLearningRecord("trip-b-$i", pattern.id, "BUSINESS")
        }
        insertLearningRecord("trip-p-1", pattern.id, "PERSONAL")

        val trip = createTestTrip()
        val autoDecision = engine.evaluateTripWithMode(trip, SmartClassificationMode.AUTO_CLASSIFY)

        assertTrue("90% agreement with 10 trips in AUTO_CLASSIFY mode must produce AutomaticClassification",
            autoDecision is ClassificationDecision.AutomaticClassification
        )
        val autoClass = autoDecision as ClassificationDecision.AutomaticClassification
        assertEquals("BUSINESS", autoClass.suggestedCategory)
        assertEquals(10, autoClass.confirmedTripCount)
        assertEquals(0.9, autoClass.agreementRatio, 0.0001)
        assertFalse("AutomaticClassification does not require confirmation", autoClass.requiresExplicitConfirmation)
    }

    // 9. Conflicting route patterns -> AmbiguousClassification
    @Test
    fun test09_conflictingRoutePatterns_returnsAmbiguousClassification() = testScope.runTest {
        // Pattern 1: Business delivery
        val pattern1 = createRoutePattern(id = "pat-conflict-1", classification = "BUSINESS", purpose = "Wholesale")
        // Pattern 2: Personal commute
        val pattern2 = createRoutePattern(id = "pat-conflict-2", classification = "PERSONAL", purpose = "Personal Gym")
        routePatternDao.insertRoutePattern(pattern1)
        routePatternDao.insertRoutePattern(pattern2)

        val trip = createTestTrip()
        val decision = engine.evaluateTrip(trip)

        assertTrue(decision is ClassificationDecision.AmbiguousClassification)
        val ambiguous = decision as ClassificationDecision.AmbiguousClassification
        assertEquals(2, ambiguous.candidatePatternIds.size)
        assertTrue(ambiguous.candidatePatternIds.contains("pat-conflict-1"))
        assertTrue(ambiguous.candidatePatternIds.contains("pat-conflict-2"))
    }

    // 10. Ambiguous nearby destinations (strip mall) -> AmbiguousClassification
    @Test
    fun test10_ambiguousNearbyDestinations_returnsAmbiguousClassification() = testScope.runTest {
        val storeA = createRoutePattern(id = "store-a", endLat = NURSERY_LAT, endLon = NURSERY_LON, classification = "BUSINESS")
        val storeB = createRoutePattern(id = "store-b", endLat = NURSERY_LAT + 0.0005, endLon = NURSERY_LON + 0.0003, classification = "PERSONAL")
        routePatternDao.insertRoutePattern(storeA)
        routePatternDao.insertRoutePattern(storeB)

        // Trip parking midway between Store A and Store B
        val midpointTrip = createTestTrip(
            endLat = (storeA.endLatitude + storeB.endLatitude) / 2.0,
            endLon = (storeA.endLongitude + storeB.endLongitude) / 2.0
        )
        val decision = engine.evaluateTrip(midpointTrip)

        assertTrue(decision is ClassificationDecision.AmbiguousClassification)
    }

    // 11. Manual classification priority -> Preserved, NoSuggestion
    @Test
    fun test11_manualClassificationPriority_neverOverridden() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)
        for (i in 1..5) {
            insertLearningRecord("trip-$i", pattern.id, "BUSINESS")
        }

        // Trip was already manually classified as PERSONAL by user
        val manualTrip = createTestTrip(
            classification = "PERSONAL",
            source = ClassificationSource.MANUAL.value
        )
        val decision = engine.evaluateTrip(manualTrip)

        assertTrue(decision is ClassificationDecision.NoSuggestion)
        assertTrue(decision.reason.contains("already been explicitly manually classified", ignoreCase = true))
    }

    // 12. Schedule classification priority -> Preserved, NoSuggestion
    @Test
    fun test12_scheduleClassificationPriority_neverOverridden() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)
        for (i in 1..5) {
            insertLearningRecord("trip-$i", pattern.id, "BUSINESS")
        }

        // Trip was classified by Schedule rule
        val scheduleTrip = createTestTrip(
            classification = "PERSONAL",
            source = ClassificationSource.SCHEDULE.value
        )
        val decision = engine.evaluateTrip(scheduleTrip)

        assertTrue(decision is ClassificationDecision.NoSuggestion)
        assertTrue(decision.reason.contains("Schedule rule takes precedence", ignoreCase = true))
    }

    // 13. Smart Classification disabled -> NoSuggestion
    @Test
    fun test13_smartClassificationDisabled_returnsNoSuggestion() = testScope.runTest {
        preferencesManager.setSmartTripRecognitionEnabled(false)

        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)
        for (i in 1..5) {
            insertLearningRecord("trip-$i", pattern.id, "BUSINESS")
        }

        val trip = createTestTrip()
        val decision = engine.evaluateTrip(trip)

        assertTrue(decision is ClassificationDecision.NoSuggestion)
        assertTrue(decision.reason.contains("Smart Trip Recognition is disabled", ignoreCase = true))
    }

    // 14. AUTO_CLASSIFY mode -> AutomaticClassification on high confidence
    @Test
    fun test14_autoClassifyMode_appliesAutomaticClassification() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)
        for (i in 1..5) {
            insertLearningRecord("trip-$i", pattern.id, "BUSINESS")
        }

        val trip = createTestTrip()
        val decision = engine.evaluateTripWithMode(trip, SmartClassificationMode.AUTO_CLASSIFY)

        assertTrue(decision is ClassificationDecision.AutomaticClassification)
        assertFalse(decision.requiresExplicitConfirmation)
        assertEquals("BUSINESS", decision.suggestedCategory)
    }

    // 15. ALWAYS_ASK mode -> SuggestedClassification (explicit confirmation required)
    @Test
    fun test15_alwaysAskMode_requiresExplicitConfirmation() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)
        for (i in 1..5) {
            insertLearningRecord("trip-$i", pattern.id, "BUSINESS")
        }

        val trip = createTestTrip()
        val decision = engine.evaluateTripWithMode(trip, SmartClassificationMode.ALWAYS_ASK)

        assertTrue(decision is ClassificationDecision.SuggestedClassification)
        assertTrue(decision.requiresExplicitConfirmation)
        assertEquals("BUSINESS", decision.suggestedCategory)
    }

    // 16. SUGGEST_ONLY mode -> SuggestedClassification (explicit confirmation required, no notifications)
    @Test
    fun test16_suggestOnlyMode_requiresExplicitConfirmation() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)
        for (i in 1..5) {
            insertLearningRecord("trip-$i", pattern.id, "BUSINESS")
        }

        val trip = createTestTrip()
        val decision = engine.evaluateTripWithMode(trip, SmartClassificationMode.SUGGEST_ONLY)

        assertTrue(decision is ClassificationDecision.SuggestedClassification)
        assertTrue(decision.requiresExplicitConfirmation)
    }

    // 17. Manual correction -> Updates evidence, changes future decision
    @Test
    fun test17_manualCorrection_updatesEvidenceAndFutureDecision() = testScope.runTest {
        // Initial setup: Trip was auto-classified as Personal
        val trip = createTestTrip(id = "trip-correct-1", classification = "PERSONAL", source = ClassificationSource.SMART_AUTO.value)
        tripDao.insertTrip(trip)

        // User manually corrects trip to Business
        tripRepository.classifyTrip(
            tripId = trip.id,
            classification = "BUSINESS",
            businessEntity = "MySucculentCorner",
            purpose = "Wholesale Garden Supply",
            notes = "Corrected by user",
            source = ClassificationSource.MANUAL
        )

        // Verify learning record is now eligible and confirmed as BUSINESS
        val record = tripLearningRecordDao.getByTripId(trip.id)
        assertNotNull(record)
        assertTrue(record!!.eligible)
        assertEquals("BUSINESS", record.confirmedCategory)
        assertEquals(ClassificationSource.MANUAL.value, record.classificationSource)
    }

    // 18. Duplicate evidence prevention -> Idempotent
    @Test
    fun test18_duplicateEvidencePrevention_isIdempotent() = testScope.runTest {
        val trip = createTestTrip(id = "trip-idem-1", classification = "UNREVIEWED")
        tripDao.insertTrip(trip)

        // First manual classification
        tripRepository.classifyTrip(trip.id, "BUSINESS", "Entity1", "Purpose1", null, ClassificationSource.MANUAL)
        val recordCount1 = tripLearningRecordDao.getAllRecords().size
        val pattern1 = routePatternDao.getAllPatterns().first()
        val count1 = pattern1.sampleCount

        // Repeated classification with identical values
        tripRepository.classifyTrip(trip.id, "BUSINESS", "Entity1", "Purpose1", null, ClassificationSource.MANUAL)
        val recordCount2 = tripLearningRecordDao.getAllRecords().size
        val pattern2 = routePatternDao.getAllPatterns().first()
        val count2 = pattern2.sampleCount

        assertEquals("Record count must remain 1 (no duplicate evidence)", 1, recordCount1)
        assertEquals("Record count must remain 1 after repeated save", 1, recordCount2)
        assertEquals("Sample count must not increment on idempotent re-save", count1, count2)
    }

    // 19. Business purpose disagreement -> Category recognized, but purpose is null
    @Test
    fun test19_businessPurposeDisagreement_suggestsCategoryWithoutPurpose() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)

        // 5 confirmed trips: all Business, but split between 3 "Inventory pickup" and 2 "Supplier visit"
        for (i in 1..3) {
            insertLearningRecord("trip-pur-$i", pattern.id, "BUSINESS", "MySucculentCorner", "Inventory pickup")
        }
        for (i in 4..5) {
            insertLearningRecord("trip-pur-$i", pattern.id, "BUSINESS", "MySucculentCorner", "Supplier visit")
        }

        val trip = createTestTrip()
        val decision = engine.evaluateTripWithMode(trip, SmartClassificationMode.ALWAYS_ASK)

        assertTrue(decision is ClassificationDecision.SuggestedClassification)
        val suggestion = decision as ClassificationDecision.SuggestedClassification
        assertEquals("Category must be recognized as BUSINESS", "BUSINESS", suggestion.suggestedCategory)
        assertEquals("Entity is consistent (5/5 = 100%)", "MySucculentCorner", suggestion.suggestedBusinessEntity)
        assertNull("Purpose had disagreement (3 vs 2, 60% < 90%); must NOT be assumed", suggestion.suggestedPurpose)
    }

    // 20. Repeated evaluations producing identical results -> Deterministic
    @Test
    fun test20_repeatedEvaluations_produceIdenticalResults() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)
        for (i in 1..5) {
            insertLearningRecord("trip-$i", pattern.id, "BUSINESS")
        }

        val trip = createTestTrip()
        val firstDecision = engine.evaluateTrip(trip)

        for (i in 1..50) {
            val repeatDecision = engine.evaluateTrip(trip)
            assertEquals(firstDecision.suggestedCategory, repeatDecision.suggestedCategory)
            assertEquals(firstDecision.suggestedBusinessEntity, repeatDecision.suggestedBusinessEntity)
            assertEquals(firstDecision.suggestedPurpose, repeatDecision.suggestedPurpose)
            assertEquals(firstDecision.matchingPatternId, repeatDecision.matchingPatternId)
            assertEquals(firstDecision.confirmedTripCount, repeatDecision.confirmedTripCount)
            assertEquals(firstDecision.businessCount, repeatDecision.businessCount)
            assertEquals(firstDecision.personalCount, repeatDecision.personalCount)
            assertEquals(firstDecision.agreementRatio, repeatDecision.agreementRatio, 0.000001)
            assertEquals(firstDecision.requiresExplicitConfirmation, repeatDecision.requiresExplicitConfirmation)
            assertEquals(firstDecision.reason, repeatDecision.reason)
        }
    }

    // 21. SMART_SUGGESTED represents pending suggestion and is NEVER treated as confirmed learning evidence
    @Test
    fun test21_smartSuggested_isNeverTreatedAsConfirmedLearningEvidence() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)

        // Add 5 pending SMART_SUGGESTED trips (not explicitly confirmed)
        for (i in 1..5) {
            insertLearningRecord(
                tripId = "sug-trip-$i",
                patternId = pattern.id,
                category = "BUSINESS",
                source = ClassificationSource.SMART_SUGGESTED.value,
                eligible = false // Pending suggestions are marked ineligible
            )
        }

        // Verify that checkEligibilityInternal rejects SMART_SUGGESTED
        val sampleSuggestedTrip = createTestTrip(id = "check-sug", classification = "BUSINESS", source = ClassificationSource.SMART_SUGGESTED.value)
        val isEligible = smartTripRepository.isTripEligibleForLearning(sampleSuggestedTrip, ClassificationSource.SMART_SUGGESTED)
        assertFalse("SMART_SUGGESTED must never be eligible for learning", isEligible)

        // Evaluate a new trip: should find 0 confirmed trips -> NoSuggestion
        val newTrip = createTestTrip()
        val decision = engine.evaluateTrip(newTrip)

        assertTrue("Pending suggestions must never contribute to decision evidence", decision is ClassificationDecision.NoSuggestion)
        assertEquals(0, decision.confirmedTripCount)
        assertEquals(0, decision.businessCount)
    }

    // 22. SCHEDULE and SMART_AUTO are excluded from learning until user explicitly confirms
    @Test
    fun test22_scheduleAndSmartAuto_areExcludedFromLearningUntilConfirmed() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)

        // Insert SCHEDULE and SMART_AUTO records
        insertLearningRecord("sched-trip-1", pattern.id, "PERSONAL", source = ClassificationSource.SCHEDULE.value, eligible = false)
        insertLearningRecord("sched-trip-2", pattern.id, "PERSONAL", source = ClassificationSource.SCHEDULE.value, eligible = false)
        insertLearningRecord("auto-trip-1", pattern.id, "BUSINESS", source = ClassificationSource.SMART_AUTO.value, eligible = false)
        insertLearningRecord("auto-trip-2", pattern.id, "BUSINESS", source = ClassificationSource.SMART_AUTO.value, eligible = false)

        val schedTrip = createTestTrip(id = "sched-check", classification = "PERSONAL", source = ClassificationSource.SCHEDULE.value)
        val autoTrip = createTestTrip(id = "auto-check", classification = "BUSINESS", source = ClassificationSource.SMART_AUTO.value)

        assertFalse("SCHEDULE trips must not be eligible without explicit confirmation",
            smartTripRepository.isTripEligibleForLearning(schedTrip, ClassificationSource.SCHEDULE))
        assertFalse("SMART_AUTO trips must not be eligible without explicit confirmation",
            smartTripRepository.isTripEligibleForLearning(autoTrip, ClassificationSource.SMART_AUTO))

        val newTrip = createTestTrip()
        val decision = engine.evaluateTrip(newTrip)

        assertTrue(decision is ClassificationDecision.NoSuggestion)
        assertEquals("Neither SCHEDULE nor SMART_AUTO may count towards confirmed trips", 0, decision.confirmedTripCount)

        // Now user explicitly confirms sched-trip-1 as PERSONAL
        tripRepository.classifyTrip(
            tripId = "sched-trip-1",
            classification = "PERSONAL",
            businessEntity = "",
            purpose = "",
            notes = "Confirmed by user",
            source = ClassificationSource.MANUAL
        )

        // Verify sched-trip-1 is now eligible in learning records
        val record = tripLearningRecordDao.getByTripId("sched-trip-1")
        assertNotNull(record)
        assertTrue("User confirmed trip must now be eligible", record!!.eligible)
        assertEquals(ClassificationSource.MANUAL.value, record.classificationSource)
    }

    // 23. SUGGESTED_CONFIRMED contributes to training
    @Test
    fun test23_suggestedConfirmed_contributesToTraining() = testScope.runTest {
        val pattern = createRoutePattern()
        routePatternDao.insertRoutePattern(pattern)

        // 3 trips confirmed via SUGGESTED_CONFIRMED
        for (i in 1..3) {
            insertLearningRecord(
                tripId = "conf-trip-$i",
                patternId = pattern.id,
                category = "BUSINESS",
                source = ClassificationSource.SUGGESTED_CONFIRMED.value,
                eligible = true
            )
        }

        val testTrip = createTestTrip(id = "conf-check", classification = "BUSINESS", source = ClassificationSource.SUGGESTED_CONFIRMED.value)
        assertTrue("SUGGESTED_CONFIRMED must be eligible for learning",
            smartTripRepository.isTripEligibleForLearning(testTrip, ClassificationSource.SUGGESTED_CONFIRMED))

        val newTrip = createTestTrip()
        val decision = engine.evaluateTrip(newTrip)

        assertTrue(decision is ClassificationDecision.SuggestedClassification)
        assertEquals(3, decision.confirmedTripCount)
        assertEquals(3, decision.businessCount)
        assertEquals("BUSINESS", (decision as ClassificationDecision.SuggestedClassification).suggestedCategory)
    }

    // 24. All ClassificationSource values persist and load correctly in Room without breaking
    @Test
    fun test24_allClassificationSources_persistCorrectlyInRoom() = testScope.runTest {
        val allSources = ClassificationSource.values()

        for (source in allSources) {
            val tripId = "pers-trip-${source.name}"
            val trip = createTestTrip(
                id = tripId,
                classification = "BUSINESS",
                source = source.value
            )
            tripDao.insertTrip(trip)

            val loadedTrip = tripDao.getTripById(tripId)
            assertNotNull("Trip with source ${source.name} must persist and load", loadedTrip)
            assertEquals(source.value, loadedTrip!!.classificationSource)

            val record = TripLearningRecordEntity(
                tripId = tripId,
                eligible = source == ClassificationSource.MANUAL || source == ClassificationSource.SUGGESTED_CONFIRMED,
                classificationSource = source.value,
                confirmedCategory = "BUSINESS",
                businessEntity = "MySucculentCorner",
                purpose = "Delivery",
                startLatitude = HOME_LAT,
                startLongitude = HOME_LON,
                endLatitude = NURSERY_LAT,
                endLongitude = NURSERY_LON,
                patternId = null,
                learnedAt = CUTOFF,
                createdAt = CUTOFF
            )
            tripLearningRecordDao.insertRecord(record)

            val loadedRecord = tripLearningRecordDao.getByTripId(tripId)
            assertNotNull("Learning record with source ${source.name} must persist and load", loadedRecord)
            assertEquals(source.value, loadedRecord!!.classificationSource)
        }
    }
}
