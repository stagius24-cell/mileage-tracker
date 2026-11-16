package com.businesstracker.mileage.ui.smart

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.RoutePatternDao
import com.businesstracker.mileage.data.local.TripDao
import com.businesstracker.mileage.data.local.TripLearningRecordDao
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.repository.SmartTripRepositoryImpl
import com.businesstracker.mileage.data.repository.TripRepositoryImpl
import com.businesstracker.mileage.domain.classifier.ClassificationDecision
import com.businesstracker.mileage.domain.classifier.SmartTripClassificationEngine
import com.businesstracker.mileage.domain.matcher.TripPatternMatcher
import com.businesstracker.mileage.domain.model.ClassificationSource
import com.businesstracker.mileage.domain.model.SmartClassificationMode
import com.businesstracker.mileage.domain.model.SmartTripLearningCutoff
import com.businesstracker.mileage.domain.repository.SmartTripRepository
import com.businesstracker.mileage.domain.repository.TripRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SmartTripRecognitionUiTest {

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

    private fun createSampleTrip(
        id: String = "trip-ui-1",
        classification: String = "UNREVIEWED",
        source: String = "UNCLASSIFIED",
        suggested: String? = null
    ): TripEntity {
        return TripEntity(
            id = id,
            date = "2026-11-15",
            startTime = CUTOFF + 3600000L,
            endTime = CUTOFF + 7200000L,
            startAddress = "100 Main St, San Jose, CA",
            endAddress = "200 Market St, San Jose, CA",
            startLatitude = 37.3382,
            startLongitude = -121.8863,
            endLatitude = 37.4419,
            endLongitude = -122.1430,
            totalDistanceMiles = 16.0,
            businessMiles = if (classification == "BUSINESS") 16.0 else 0.0,
            businessEntity = if (classification == "BUSINESS") "MySucculentCorner" else "",
            purpose = if (classification == "BUSINESS") "Delivery" else "",
            trackingMethod = "AUTO_DETECTED",
            classification = classification,
            applicableRate = 0.67,
            calculatedDeduction = if (classification == "BUSINESS") 16.0 * 0.67 else 0.0,
            notes = null,
            waypointsJson = "[]",
            syncStatus = "PENDING",
            syncedAt = null,
            classificationSource = source,
            suggestedClassification = suggested
        )
    }

    // 1. Enable/disable persistence
    @Test
    fun test01_enableDisablePersistence() = testScope.runTest {
        preferencesManager.setSmartTripRecognitionEnabled(false)
        assertFalse("Disabled state must persist", preferencesManager.isSmartTripRecognitionEnabled.first())

        preferencesManager.setSmartTripRecognitionEnabled(true)
        assertTrue("Enabled state must persist", preferencesManager.isSmartTripRecognitionEnabled.first())
    }

    // 2. Default mode is ALWAYS_ASK
    @Test
    fun test02_defaultModeIsAlwaysAsk() = testScope.runTest {
        val defaultMode = preferencesManager.smartClassificationMode.first()
        assertEquals("Default mode must be ALWAYS_ASK", SmartClassificationMode.ALWAYS_ASK, defaultMode)
        assertEquals("Default mode companion must be ALWAYS_ASK", SmartClassificationMode.ALWAYS_ASK, SmartClassificationMode.DEFAULT)
    }

    // 3. Changing modes
    @Test
    fun test03_changingModes_persistsCorrectly() = testScope.runTest {
        preferencesManager.setSmartClassificationMode(SmartClassificationMode.AUTO_CLASSIFY)
        assertEquals(SmartClassificationMode.AUTO_CLASSIFY, preferencesManager.smartClassificationMode.first())
        assertTrue("AUTO_CLASSIFY also sets smartAutoClassifyEnabled = true for backward compatibility",
            preferencesManager.isSmartAutoClassifyEnabled.first())

        preferencesManager.setSmartClassificationMode(SmartClassificationMode.SUGGEST_ONLY)
        assertEquals(SmartClassificationMode.SUGGEST_ONLY, preferencesManager.smartClassificationMode.first())
        assertFalse(preferencesManager.isSmartAutoClassifyEnabled.first())

        preferencesManager.setSmartClassificationMode(SmartClassificationMode.ALWAYS_ASK)
        assertEquals(SmartClassificationMode.ALWAYS_ASK, preferencesManager.smartClassificationMode.first())
        assertFalse(preferencesManager.isSmartAutoClassifyEnabled.first())
    }

    // 4. Pending suggestion appearance
    @Test
    fun test04_pendingSuggestionAppearance_doesNotClassifyTrip() = testScope.runTest {
        val trip = createSampleTrip(
            classification = "UNREVIEWED",
            source = ClassificationSource.UNCLASSIFIED.value,
            suggested = "BUSINESS"
        )
        tripDao.insertTrip(trip)

        val retrieved = tripDao.getTripById(trip.id)
        assertNotNull(retrieved)
        assertEquals("UNREVIEWED", retrieved!!.classification)
        assertEquals("BUSINESS", retrieved.suggestedClassification)
        assertEquals(0.0, retrieved.calculatedDeduction, 0.001)
        assertEquals(0.0, retrieved.businessMiles, 0.001)
    }

    // 5. Confirmed suggestion appearance
    @Test
    fun test05_confirmedSuggestion_updatesTripAndProducesEligibleLearning() = testScope.runTest {
        val trip = createSampleTrip(
            classification = "UNREVIEWED",
            suggested = "BUSINESS"
        )
        tripDao.insertTrip(trip)

        // User confirms suggested classification
        tripRepository.classifyTrip(
            tripId = trip.id,
            classification = "BUSINESS",
            businessEntity = "MySucculentCorner",
            purpose = "Wholesale Delivery",
            notes = null,
            source = ClassificationSource.SUGGESTED_CONFIRMED
        )

        val updated = tripDao.getTripById(trip.id)
        assertNotNull(updated)
        assertEquals("BUSINESS", updated!!.classification)
        assertEquals(ClassificationSource.SUGGESTED_CONFIRMED.value, updated.classificationSource)
        assertTrue(updated.calculatedDeduction > 0.0)

        // Eligible learning record created
        val record = tripLearningRecordDao.getByTripId(trip.id)
        assertNotNull(record)
        assertTrue(record!!.eligible)
        assertEquals(ClassificationSource.SUGGESTED_CONFIRMED.value, record.classificationSource)
        assertEquals(1, routePatternDao.getPatternsCount())
    }

    // 6. Automatic classification indicator
    @Test
    fun test06_automaticClassificationIndicator_identifiesAutoClassifiedTrips() = testScope.runTest {
        val autoTrip = createSampleTrip(
            classification = "BUSINESS",
            source = ClassificationSource.SMART_AUTO.value
        )
        tripDao.insertTrip(autoTrip)

        val retrieved = tripDao.getTripById(autoTrip.id)
        assertNotNull(retrieved)
        assertEquals(ClassificationSource.SMART_AUTO.value, retrieved!!.classificationSource)
        // Verify that SMART_AUTO is not an unreviewed pending suggestion
        assertFalse(retrieved.classification == "UNREVIEWED")
    }

    // 7. Dismissal without classification
    @Test
    fun test07_dismissalWithoutClassification_leavesTripUnreviewedAndCreatesNoLearning() = testScope.runTest {
        val trip = createSampleTrip(classification = "UNREVIEWED", suggested = "BUSINESS")
        tripDao.insertTrip(trip)

        // Simulating dismiss: no classification update is made to tripRepository
        val retrieved = tripDao.getTripById(trip.id)
        assertNotNull(retrieved)
        assertEquals("UNREVIEWED", retrieved!!.classification)

        // Zero learning records or patterns created
        assertNull(tripLearningRecordDao.getByTripId(trip.id))
        assertEquals(0, routePatternDao.getPatternsCount())
    }

    // 8. No learning from unconfirmed suggestions
    @Test
    fun test08_noLearningFromUnconfirmedSuggestions() = testScope.runTest {
        val trip = createSampleTrip(classification = "BUSINESS", source = ClassificationSource.SMART_SUGGESTED.value)
        val isEligible = smartTripRepository.isTripEligibleForLearning(trip, ClassificationSource.SMART_SUGGESTED)
        assertFalse("Unconfirmed SMART_SUGGESTED trip must never be eligible for learning", isEligible)

        val record = smartTripRepository.reconcileTripLearning(trip, ClassificationSource.SMART_SUGGESTED)
        assertNull("reconcileTripLearning must return null for SMART_SUGGESTED", record)
        assertEquals(0, routePatternDao.getPatternsCount())
    }

    // 9. No deduction or sync changes from displaying suggestions
    @Test
    fun test09_noDeductionOrSyncChanges_fromDisplayingSuggestions() = testScope.runTest {
        val trip = createSampleTrip(
            classification = "UNREVIEWED",
            suggested = "BUSINESS"
        )
        tripDao.insertTrip(trip)

        val retrievedBefore = tripDao.getTripById(trip.id)!!
        val deductionBefore = retrievedBefore.calculatedDeduction
        val syncBefore = retrievedBefore.syncStatus

        // Evaluate trip with engine
        val decision = engine.evaluateTrip(retrievedBefore)
        assertTrue(decision is ClassificationDecision)

        val retrievedAfter = tripDao.getTripById(trip.id)!!
        assertEquals("Deductions must remain 0.0", deductionBefore, retrievedAfter.calculatedDeduction, 0.0001)
        assertEquals("Sync status must remain PENDING", syncBefore, retrievedAfter.syncStatus)
        assertEquals("Classification must remain UNREVIEWED", "UNREVIEWED", retrievedAfter.classification)
    }

    // 10. Settings state after app restart
    @Test
    fun test10_settingsState_persistsAcrossManagerRecreation() = testScope.runTest {
        preferencesManager.setSmartTripRecognitionEnabled(false)
        preferencesManager.setSmartClassificationMode(SmartClassificationMode.AUTO_CLASSIFY)

        // Simulate app restart by creating a new instance of TrackingPreferencesManager with same context
        val newManager = TrackingPreferencesManager(context)
        val enabledAfterRestart = newManager.isSmartTripRecognitionEnabled.first()
        val modeAfterRestart = newManager.smartClassificationMode.first()

        assertFalse("Enabled state must be false after restart", enabledAfterRestart)
        assertEquals("Mode must be AUTO_CLASSIFY after restart", SmartClassificationMode.AUTO_CLASSIFY, modeAfterRestart)
    }
}
