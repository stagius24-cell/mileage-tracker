package com.businesstracker.mileage.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.RoutePatternDao
import com.businesstracker.mileage.data.local.TripDao
import com.businesstracker.mileage.data.local.TripLearningRecordDao
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.ClassificationSource
import com.businesstracker.mileage.domain.model.SmartTripLearningCutoff
import com.businesstracker.mileage.domain.repository.SmartTripRepository
import com.businesstracker.mileage.domain.repository.TripRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class SmartTripLearningFoundationTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var tripDao: TripDao
    private lateinit var routePatternDao: RoutePatternDao
    private lateinit var tripLearningRecordDao: TripLearningRecordDao
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var smartTripRepository: SmartTripRepository
    private lateinit var tripRepository: TripRepository

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val CUTOFF = SmartTripLearningCutoff.ACTIVATION_CUTOFF_MILLIS // 1794729600000L

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
        database.close()
    }

    private fun createSampleTrip(
        id: String,
        endTime: Long,
        classification: String = "UNREVIEWED",
        businessEntity: String = "MySucculentCorner",
        purpose: String = "Nursery Delivery",
        startLat: Double = 37.3382,
        startLon: Double = -121.8863,
        endLat: Double = 37.3521,
        endLon: Double = -121.8901,
        source: String = "UNCLASSIFIED"
    ): TripEntity {
        return TripEntity(
            id = id,
            date = "2026-11-15",
            startTime = endTime - 1800000L, // 30 minutes earlier
            endTime = endTime,
            startAddress = "100 Main St, San Jose, CA",
            endAddress = "200 Market St, San Jose, CA",
            startLatitude = startLat,
            startLongitude = startLon,
            endLatitude = endLat,
            endLongitude = endLon,
            totalDistanceMiles = 5.0,
            businessMiles = if (classification == "BUSINESS") 5.0 else 0.0,
            businessEntity = businessEntity,
            purpose = purpose,
            trackingMethod = "AUTO_DETECTED",
            classification = classification,
            applicableRate = 0.67,
            calculatedDeduction = if (classification == "BUSINESS") 3.35 else 0.0,
            notes = null,
            waypointsJson = "[]",
            syncStatus = "PENDING",
            syncedAt = null,
            classificationSource = source,
            suggestedClassification = null
        )
    }

    // 1. Existing historical trips are excluded (< cutoff)
    @Test
    fun historicalTripsBeforeCutoff_areStrictlyExcludedFromLearning() = testScope.runTest {
        val historicalTrip = createSampleTrip(
            id = "hist-trip-1",
            endTime = CUTOFF - 1000L, // 1 second before cutoff
            classification = "BUSINESS",
            source = ClassificationSource.MANUAL.value
        )
        tripDao.insertTrip(historicalTrip)

        val isEligible = smartTripRepository.isTripEligibleForLearning(historicalTrip, ClassificationSource.MANUAL)
        assertFalse("Trips completed before activation cutoff must not be eligible", isEligible)

        val record = smartTripRepository.reconcileTripLearning(historicalTrip, ClassificationSource.MANUAL)
        assertNull("No learning record should be created for historical trips", record)
        assertEquals("Route patterns must remain empty", 0, routePatternDao.getPatternsCount())
    }

    // 2. New manually classified trips are eligible (>= cutoff)
    @Test
    fun newManuallyClassifiedTrips_afterCutoff_areLearned() = testScope.runTest {
        val eligibleTrip = createSampleTrip(
            id = "new-trip-1",
            endTime = CUTOFF + 3600000L, // 1 hour after cutoff
            classification = "UNREVIEWED"
        )
        tripDao.insertTrip(eligibleTrip)

        // User manually classifies trip as Business
        tripRepository.classifyTrip(
            tripId = eligibleTrip.id,
            classification = "BUSINESS",
            businessEntity = "MySucculentCorner",
            purpose = "Client Delivery",
            notes = "Delivered succulents",
            source = ClassificationSource.MANUAL
        )

        val record = tripLearningRecordDao.getByTripId(eligibleTrip.id)
        assertNotNull("Learning record must be created for manually classified trip", record)
        assertTrue("Learning record must be marked eligible", record!!.eligible)
        assertEquals(ClassificationSource.MANUAL.value, record.classificationSource)
        assertEquals("BUSINESS", record.confirmedCategory)

        val patterns = routePatternDao.getAllPatterns()
        assertEquals("Exactly 1 route pattern must be created", 1, patterns.size)
        assertEquals(1, patterns[0].sampleCount)
        assertEquals("BUSINESS", patterns[0].classification)
        assertEquals("MySucculentCorner", patterns[0].businessEntity)
    }

    // 3. New unclassified trips are excluded
    @Test
    fun unclassifiedTrips_areExcludedFromLearning() = testScope.runTest {
        val unclassifiedTrip = createSampleTrip(
            id = "unclassified-1",
            endTime = CUTOFF + 3600000L,
            classification = "UNREVIEWED"
        )
        tripDao.insertTrip(unclassifiedTrip)

        val isEligible = smartTripRepository.isTripEligibleForLearning(unclassifiedTrip, ClassificationSource.UNCLASSIFIED)
        assertFalse("Unclassified trips must be excluded from learning", isEligible)

        val record = smartTripRepository.reconcileTripLearning(unclassifiedTrip, ClassificationSource.UNCLASSIFIED)
        assertNull("Learning record must not be generated for unclassified trip", record)
        assertEquals(0, routePatternDao.getPatternsCount())
    }

    // 4. Auto-classified trips do NOT reinforce themselves
    @Test
    fun autoClassifiedTrips_doNotReinforceLearning() = testScope.runTest {
        val autoTrip = createSampleTrip(
            id = "auto-trip-1",
            endTime = CUTOFF + 3600000L,
            classification = "PERSONAL",
            source = ClassificationSource.AUTO_CLASSIFIED.value
        )
        tripDao.insertTrip(autoTrip)

        val isEligible = smartTripRepository.isTripEligibleForLearning(autoTrip, ClassificationSource.AUTO_CLASSIFIED)
        assertFalse("Auto-classified trips must not be eligible for training until confirmed", isEligible)

        val record = smartTripRepository.reconcileTripLearning(autoTrip, ClassificationSource.AUTO_CLASSIFIED)
        assertNull("Auto-classified trips must not create learning records", record)
        assertEquals(0, routePatternDao.getPatternsCount())

        // Later, user explicitly confirms the suggestion:
        tripRepository.classifyTrip(
            tripId = autoTrip.id,
            classification = "PERSONAL",
            businessEntity = "Personal",
            purpose = "Personal Drive",
            notes = null,
            source = ClassificationSource.SUGGESTED_CONFIRMED
        )

        // Now it SHOULD be learned
        val confirmedRecord = tripLearningRecordDao.getByTripId(autoTrip.id)
        assertNotNull("Explicitly confirmed trip should now be learned", confirmedRecord)
        assertEquals(ClassificationSource.SUGGESTED_CONFIRMED.value, confirmedRecord!!.classificationSource)
        assertEquals(1, routePatternDao.getPatternsCount())
    }

    // 5. Editing an old trip does not make it eligible
    @Test
    fun editingOldTrip_doesNotMakeItEligible() = testScope.runTest {
        val oldTrip = createSampleTrip(
            id = "old-trip-edit",
            endTime = CUTOFF - 86400000L, // 1 day before cutoff
            classification = "UNREVIEWED"
        )
        tripDao.insertTrip(oldTrip)

        // User edits/classifies the old trip today
        tripRepository.updateTripDetails(
            tripId = oldTrip.id,
            classification = "BUSINESS",
            businessEntity = "MySucculentCorner",
            purpose = "Late Review",
            notes = "Reviewed post cutoff",
            source = ClassificationSource.MANUAL
        )

        // Trip was updated in trips table
        val updated = tripDao.getTripById(oldTrip.id)
        assertNotNull(updated)
        assertEquals("BUSINESS", updated!!.classification)

        // But learning foundation must strictly reject it based on original completion timestamp
        val record = tripLearningRecordDao.getByTripId(oldTrip.id)
        assertNull("Editing an old trip must never make it eligible for learning", record)
        assertEquals(0, routePatternDao.getPatternsCount())
    }

    // 6. Reopening the app does not reset the learning cutoff
    @Test
    fun reopeningApp_doesNotResetLearningCutoff() = testScope.runTest {
        preferencesManager.initializeSmartTripActivationCutoff(CUTOFF)
        assertEquals(CUTOFF, preferencesManager.smartTripActivationCutoff.first())

        // Simulate app relaunch with different/custom cutoff or repeat initialization
        preferencesManager.initializeSmartTripActivationCutoff(999999999999L)
        // Persisted cutoff MUST NOT be overwritten!
        assertEquals(CUTOFF, preferencesManager.smartTripActivationCutoff.first())
    }

    // 7. Idempotency: Repeated saves/retries do not duplicate records or sample counts
    @Test
    fun repeatedClassificationSaves_areIdempotent() = testScope.runTest {
        val trip = createSampleTrip(
            id = "idempotent-trip",
            endTime = CUTOFF + 3600000L,
            classification = "UNREVIEWED"
        )
        tripDao.insertTrip(trip)

        // Save 1
        tripRepository.classifyTrip(
            tripId = trip.id,
            classification = "BUSINESS",
            businessEntity = "MySucculentCorner",
            purpose = "Delivery",
            notes = null,
            source = ClassificationSource.MANUAL
        )

        // Save 2 (identical retry)
        tripRepository.classifyTrip(
            tripId = trip.id,
            classification = "BUSINESS",
            businessEntity = "MySucculentCorner",
            purpose = "Delivery",
            notes = null,
            source = ClassificationSource.MANUAL
        )

        // Save 3 (update details with same classification)
        tripRepository.updateTripDetails(
            tripId = trip.id,
            classification = "BUSINESS",
            businessEntity = "MySucculentCorner",
            purpose = "Delivery",
            notes = "Added note",
            source = ClassificationSource.MANUAL
        )

        // Verify exactly 1 learning record and 1 sample count
        val records = tripLearningRecordDao.getAllRecords()
        assertEquals("Must only have 1 learning record", 1, records.size)

        val patterns = routePatternDao.getAllPatterns()
        assertEquals("Must only have 1 route pattern", 1, patterns.size)
        assertEquals("Sample count must be 1, not duplicated", 1, patterns[0].sampleCount)
    }

    // 8. Manual correction: Business -> Personal properly updates route patterns
    @Test
    fun manualCorrection_decrementsOldPattern_andUpdatesNewPattern() = testScope.runTest {
        val trip = createSampleTrip(
            id = "correction-trip",
            endTime = CUTOFF + 3600000L,
            classification = "UNREVIEWED"
        )
        tripDao.insertTrip(trip)

        // Initially classified as Business
        tripRepository.classifyTrip(
            tripId = trip.id,
            classification = "BUSINESS",
            businessEntity = "MySucculentCorner",
            purpose = "Delivery",
            notes = null,
            source = ClassificationSource.MANUAL
        )

        var patterns = routePatternDao.getAllPatterns()
        assertEquals(1, patterns.size)
        assertEquals("BUSINESS", patterns[0].classification)

        // User corrects mistake: Changes to Personal
        tripRepository.updateTripDetails(
            tripId = trip.id,
            classification = "PERSONAL",
            businessEntity = "Personal",
            purpose = "Personal Drive",
            notes = "Correction",
            source = ClassificationSource.MANUAL
        )

        patterns = routePatternDao.getAllPatterns()
        assertEquals("Old business pattern had 1 sample, so it was deleted; new personal pattern created", 1, patterns.size)
        assertEquals("PERSONAL", patterns[0].classification)
        assertEquals(1, patterns[0].sampleCount)

        val record = tripLearningRecordDao.getByTripId(trip.id)
        assertNotNull(record)
        assertEquals("PERSONAL", record!!.confirmedCategory)
        assertEquals(patterns[0].id, record.patternId)
    }

    // 9. Trip deletion: Deleting trip removes learning record and decrements pattern
    @Test
    fun deletingTrip_removesLearningRecord_andDecrementsPattern() = testScope.runTest {
        val trip = createSampleTrip(
            id = "deletion-trip",
            endTime = CUTOFF + 3600000L,
            classification = "UNREVIEWED"
        )
        tripDao.insertTrip(trip)

        tripRepository.classifyTrip(
            tripId = trip.id,
            classification = "BUSINESS",
            businessEntity = "MySucculentCorner",
            purpose = "Delivery",
            notes = null,
            source = ClassificationSource.MANUAL
        )

        assertEquals(1, routePatternDao.getPatternsCount())
        assertNotNull(tripLearningRecordDao.getByTripId(trip.id))

        // Delete trip
        tripRepository.deleteTrip(trip.id)

        assertNull("Trip record must be deleted", tripDao.getTripById(trip.id))
        assertNull("Learning record must be deleted", tripLearningRecordDao.getByTripId(trip.id))
        assertEquals("Pattern with 0 samples should be removed", 0, routePatternDao.getPatternsCount())
    }

    // 10. Stable Clustering: Coordinates do NOT drift, distinct nearby destinations stay separated
    @Test
    fun stableClustering_preventsGeographicDrift() = testScope.runTest {
        val baseStartLat = 37.3382
        val baseStartLon = -121.8863
        val baseEndLat = 37.3521
        val baseEndLon = -121.8901

        // Trip 1 creates cluster anchor
        val trip1 = createSampleTrip("trip-c1", CUTOFF + 1000L, "UNREVIEWED", startLat = baseStartLat, startLon = baseStartLon, endLat = baseEndLat, endLon = baseEndLon)
        tripDao.insertTrip(trip1)
        tripRepository.classifyTrip("trip-c1", "BUSINESS", "MySucculentCorner", "Delivery", null, ClassificationSource.MANUAL)

        val initialPattern = routePatternDao.getAllPatterns()[0]
        assertEquals(baseStartLat, initialPattern.startLatitude, 0.00001)
        assertEquals(baseEndLat, initialPattern.endLatitude, 0.00001)

        // Trip 2 is 50 meters away (within 250m cluster radius)
        val trip2 = createSampleTrip("trip-c2", CUTOFF + 2000L, "UNREVIEWED", startLat = baseStartLat + 0.0003, startLon = baseStartLon, endLat = baseEndLat + 0.0003, endLon = baseEndLon)
        tripDao.insertTrip(trip2)
        tripRepository.classifyTrip("trip-c2", "BUSINESS", "MySucculentCorner", "Delivery", null, ClassificationSource.MANUAL)

        val updatedPattern = routePatternDao.getAllPatterns()[0]
        assertEquals("Sample count must increment to 2", 2, updatedPattern.sampleCount)

        // CRITICAL: Anchor coordinates MUST NOT MOVE (zero drift)!
        assertEquals("Start latitude must remain anchored", baseStartLat, updatedPattern.startLatitude, 0.0000001)
        assertEquals("End latitude must remain anchored", baseEndLat, updatedPattern.endLatitude, 0.0000001)

        // Trip 3 is 2000 meters away (outside 250m cluster radius)
        val trip3 = createSampleTrip("trip-c3", CUTOFF + 3000L, "UNREVIEWED", startLat = baseStartLat, startLon = baseStartLon, endLat = baseEndLat + 0.02, endLon = baseEndLon)
        tripDao.insertTrip(trip3)
        tripRepository.classifyTrip("trip-c3", "BUSINESS", "MySucculentCorner", "Delivery", null, ClassificationSource.MANUAL)

        val allPatterns = routePatternDao.getAllPatterns()
        assertEquals("Distinct destination must create a new distinct pattern, never merge", 2, allPatterns.size)
    }
}
