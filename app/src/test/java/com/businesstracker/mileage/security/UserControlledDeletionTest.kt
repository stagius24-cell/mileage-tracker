package com.businesstracker.mileage.security

import android.content.Context
import androidx.room.Room
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import com.businesstracker.mileage.data.local.entity.GpsInboxEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleEntity
import com.businesstracker.mileage.data.local.entity.TripSequenceEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.repository.TripRepositoryImpl
import com.businesstracker.mileage.data.security.GoogleAuthManager
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.data.security.TestSoftwareKeyProvider
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.service.LocationTrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Validates user-controlled data deletion (GAP-04) and local trip preservation rules:
 * - Atomic purge of trips and all related lifecycle session tables.
 * - Cache cleanup of exported PDF/CSV reports.
 * - Credential preservation (OAuth, webhook, Cloudflare secrets are NOT deleted by trip purge).
 * - Settings preservation.
 * - Disconnecting Google OAuth does NOT delete local trip records.
 */
@RunWith(RobolectricTestRunner::class)
class UserControlledDeletionTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var tripRepository: TripRepository
    private lateinit var secureStorageManager: SecureStorageManager
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var googleAuthManager: GoogleAuthManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        tripRepository = TripRepositoryImpl(database.tripDao(), Dispatchers.IO, context)

        val keyProvider = TestSoftwareKeyProvider()
        secureStorageManager = SecureStorageManager(context, keyProvider)
        secureStorageManager.clearAllCredentials()

        preferencesManager = TrackingPreferencesManager(context, secureStorageManager)
        googleAuthManager = GoogleAuthManager(context, secureStorageManager, preferencesManager)
    }

    @After
    fun tearDown() {
        database.close()
        secureStorageManager.clearAllCredentials()
    }

    @Test
    fun deleteAllTripData_atomicallyPurgesAllTripAndSessionTables() = runTest {
        val tripId = "test_trip_123"

        // Seed finalized trip
        database.tripDao().insertTrip(
            TripEntity(
                id = tripId,
                date = "2026-11-06",
                startTime = 1000L,
                endTime = 2000L,
                startAddress = "100 Start St",
                endAddress = "200 End Ave",
                startLatitude = 37.7749,
                startLongitude = -122.4194,
                endLatitude = 37.7750,
                endLongitude = -122.4195,
                totalDistanceMiles = 15.5,
                businessMiles = 15.5,
                classification = "BUSINESS",
                businessEntity = "MySucculentCorner",
                purpose = "Client Delivery",
                applicableRate = 0.760,
                calculatedDeduction = 11.78,
                trackingMethod = TrackingMethod.AUTO_DETECTED.value,
                syncStatus = "PENDING",
                syncedAt = null,
                notes = "Confidential business trip note",
                waypointsJson = "[]"
            )
        )

        // Seed active trip, waypoints, gps inbox, lifecycle, sequence
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 1000L,
                startAddress = "100 Start St",
                suggestedBusiness = "MySucculentCorner",
                trackingMethod = TrackingMethod.AUTO_DETECTED.value,
                totalDistanceMeters = 5000f,
                lastMovementTimestamp = 1500L,
                isStationary = false,
                isCandidate = false,
                isConfirmed = true
            )
        )
        database.activeTripDao().insertWaypoint(
            ActiveWaypointEntity(
                tripId = tripId,
                latitude = 37.7749,
                longitude = -122.4194,
                altitude = 10.0,
                timestamp = 1050L,
                accuracy = 5f,
                speed = 12f
            )
        )
        database.gpsInboxDao().insertFix(
            GpsInboxEntity(
                tripId = tripId,
                latitude = 37.7750,
                longitude = -122.4195,
                altitude = 10.0,
                accuracy = 5f,
                speed = 12f,
                timestamp = 1060L
            )
        )
        database.tripLifecycleDao().upsertLifecycle(
            TripLifecycleEntity(
                tripId = tripId,
                state = "RUNNING",
                updatedAt = 1060L,
                reason = "AUTO_DRIVE_STARTED"
            )
        )
        database.tripSequenceDao().upsertSequence(
            TripSequenceEntity(
                tripId = tripId,
                lastSequenceNumber = 5
            )
        )

        // Verify entities exist prior to deletion
        assertNotNull(database.tripDao().getTripById(tripId))
        assertNotNull(database.activeTripDao().getActiveTripById(tripId))
        assertEquals(1, database.activeTripDao().getWaypointsForTrip(tripId).size)
        assertEquals(1, database.gpsInboxDao().getPendingFixesCount(tripId))
        assertNotNull(database.tripLifecycleDao().getLifecycle(tripId))
        assertEquals(5, database.tripSequenceDao().getLastSequenceNumber(tripId))

        // Create export cache files
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val testPdf = File(exportDir, "Mileage_Report_test.pdf").apply { writeText("dummy pdf data") }
        val testCsv = File(exportDir, "Mileage_Report_test.csv").apply { writeText("dummy csv data") }
        assertTrue(testPdf.exists())
        assertTrue(testCsv.exists())

        // Execute user-controlled deletion
        val deletedCount = tripRepository.deleteAllTripData()

        assertEquals("Should report 1 trip deleted", 1, deletedCount)

        // Verify ALL tables are completely empty
        assertNull(database.tripDao().getTripById(tripId))
        assertEquals(0, database.tripDao().getAllTrips().first().size)
        assertNull(database.activeTripDao().getActiveTripById(tripId))
        assertEquals(0, database.activeTripDao().getWaypointsForTrip(tripId).size)
        assertEquals(0, database.gpsInboxDao().getPendingFixesCount(tripId))
        assertNull(database.tripLifecycleDao().getLifecycle(tripId))
        assertEquals(0, database.tripSequenceDao().getLastSequenceNumber(tripId))

        // Verify export cache was purged
        assertFalse("Test PDF must be deleted", testPdf.exists())
        assertFalse("Test CSV must be deleted", testCsv.exists())
    }

    @Test
    fun deleteAllTripData_preservesCredentialsAndSettings() = runTest {
        // Set up credentials and settings
        secureStorageManager.saveOAuthToken("oauth_token_preserve_me")
        secureStorageManager.saveSheetsWebhookUrl("https://example.com/webhook_preserve_me")
        secureStorageManager.saveCfBearerToken("cf_bearer_preserve_me")
        preferencesManager.setDefaultVehicle("Rivian R1S")
        preferencesManager.setAutoTrackingEnabled(true)

        // Seed and delete trip data
        database.tripDao().insertTrip(
            TripEntity(
                id = "trip_to_delete",
                date = "2026-11-06",
                startTime = 1000L,
                endTime = 2000L,
                startAddress = "A",
                endAddress = "B",
                startLatitude = 37.7749,
                startLongitude = -122.4194,
                endLatitude = 37.7750,
                endLongitude = -122.4195,
                totalDistanceMiles = 5.0,
                businessMiles = 5.0,
                classification = "BUSINESS",
                businessEntity = "MySucculentCorner",
                purpose = "Delivery",
                applicableRate = 0.760,
                calculatedDeduction = 3.80,
                trackingMethod = TrackingMethod.AUTO_DETECTED.value,
                syncStatus = "SYNCED",
                syncedAt = 2500L,
                notes = null,
                waypointsJson = "[]"
            )
        )

        tripRepository.deleteAllTripData()

        // Verify trips are gone
        assertEquals(0, database.tripDao().getAllTrips().first().size)

        // Verify credentials and settings are 100% PRESERVED (GAP-04)
        assertEquals("oauth_token_preserve_me", secureStorageManager.getOAuthToken())
        assertEquals("https://example.com/webhook_preserve_me", secureStorageManager.getSheetsWebhookUrl())
        assertEquals("cf_bearer_preserve_me", secureStorageManager.getCfBearerToken())
        assertEquals("Rivian R1S", preferencesManager.defaultVehicle.first())
        assertTrue(preferencesManager.isAutoTrackingEnabled.first())
    }

    @Test
    fun disconnectingGoogle_doesNotDeleteLocalTripRecords() = runTest {
        val tripId = "trip_preserved_after_disconnect"
        database.tripDao().insertTrip(
            TripEntity(
                id = tripId,
                date = "2026-11-06",
                startTime = 5000L,
                endTime = 6000L,
                startAddress = "Warehouse",
                endAddress = "Store",
                startLatitude = 37.7749,
                startLongitude = -122.4194,
                endLatitude = 37.7750,
                endLongitude = -122.4195,
                totalDistanceMiles = 10.0,
                businessMiles = 10.0,
                classification = "BUSINESS",
                businessEntity = "MySucculentCorner",
                purpose = "Restock",
                applicableRate = 0.760,
                calculatedDeduction = 7.60,
                trackingMethod = TrackingMethod.AUTO_DETECTED.value,
                syncStatus = "PENDING",
                syncedAt = null,
                notes = null,
                waypointsJson = "[]"
            )
        )

        secureStorageManager.saveOAuthToken("token_to_disconnect")
        preferencesManager.setSheetsConnected(true)

        // Disconnect Google account
        val disconnectResult = googleAuthManager.disconnect(context)
        assertTrue(disconnectResult.localCleared)

        // OAuth credentials must be wiped
        assertFalse(secureStorageManager.hasOAuthToken())
        assertFalse(preferencesManager.isSheetsConnected.first())

        // NON-NEGOTIABLE RULE: Local trip records MUST NOT be deleted by disconnect
        val preservedTrip = database.tripDao().getTripById(tripId)
        assertNotNull("Local trip records must survive Google disconnect", preservedTrip)
        assertEquals(tripId, preservedTrip!!.id)
        assertEquals(10.0, preservedTrip.totalDistanceMiles, 0.001)
    }
}
