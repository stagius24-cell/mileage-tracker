package com.businesstracker.mileage.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @Rule
    @JvmField
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ExpenseTrackerDatabase::class.java
    )

    // Tests 28, 29, 30, 31: Migrating from v1 to v2, v2 to v3, and v1 to v3 preserves existing trips & waypoints
    @Test
    fun migrate1To2_preservesExistingData_andCreatesGpsInbox() {
        var db = helper.createDatabase(TEST_DB, 1).apply {
            execSQL("""
                INSERT INTO trips (id, date, startTime, endTime, startLatitude, startLongitude, endLatitude, endLongitude, totalDistanceMiles, businessMiles, businessEntity, purpose, trackingMethod, classification, applicableRate, calculatedDeduction, waypointsJson, syncStatus)
                VALUES ('trip-v1', '2026-11-06', 1000, 2000, 37.33, -121.88, 37.35, -121.89, 5.2, 5.2, 'MySucculentCorner', 'Nursery Pickup', 'AUTO_DETECTED', 'BUSINESS', 0.67, 3.484, '[]', 'PENDING')
            """.trimIndent())
            execSQL("""
                INSERT INTO active_trip (id, startTime, startLatitude, startLongitude, lastLatitude, lastLongitude, lastTimestamp, totalDistanceMeters, trackingMethod, isPaused, pausedDurationMillis, suggestedBusiness)
                VALUES ('active-v1', 1000, 37.33, -121.88, 37.35, -121.89, 2000, 1500.0, 'AUTO_DETECTED', 0, 0, 'MySucculentCorner')
            """.trimIndent())
            execSQL("""
                INSERT INTO active_waypoints (id, tripId, latitude, longitude, altitude, accuracy, speed, timestamp)
                VALUES (1, 'active-v1', 37.33, -121.88, 10.0, 5.0, 15.0, 1000)
            """.trimIndent())
            close()
        }

        // Migrate to v2
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, ExpenseTrackerDatabase.MIGRATION_1_2)

        // Verify v1 user records survived migration intact
        val tripCursor = db.query("SELECT id, totalDistanceMiles FROM trips WHERE id = 'trip-v1'")
        assertTrue(tripCursor.moveToFirst())
        assertEquals("trip-v1", tripCursor.getString(0))
        assertEquals(5.2, tripCursor.getDouble(1), 0.001)
        tripCursor.close()

        val activeCursor = db.query("SELECT id, totalDistanceMeters FROM active_trip WHERE id = 'active-v1'")
        assertTrue(activeCursor.moveToFirst())
        assertEquals("active-v1", activeCursor.getString(0))
        assertEquals(1500.0, activeCursor.getDouble(1), 0.001)
        activeCursor.close()

        val waypointCursor = db.query("SELECT id, tripId FROM active_waypoints WHERE id = 1")
        assertTrue(waypointCursor.moveToFirst())
        assertEquals("active-v1", waypointCursor.getString(1))
        waypointCursor.close()

        // Verify new gps_inbox table works in v2
        db.execSQL("""
            INSERT INTO gps_inbox (id, tripId, latitude, longitude, altitude, accuracy, speed, timestamp)
            VALUES (100, 'active-v1', 37.33, -121.88, 10.0, 5.0, 15.0, 1000)
        """.trimIndent())
        val inboxCursor = db.query("SELECT COUNT(*) FROM gps_inbox")
        assertTrue(inboxCursor.moveToFirst())
        assertEquals(1, inboxCursor.getInt(0))
        inboxCursor.close()
        db.close()
    }

    @Test
    fun migrate2To3_preservesExistingData_andAddsLifecycleAndMonotonicColumns() {
        var db = helper.createDatabase(TEST_DB, 2).apply {
            execSQL("""
                INSERT INTO trips (id, date, startTime, endTime, startLatitude, startLongitude, endLatitude, endLongitude, totalDistanceMiles, businessMiles, businessEntity, purpose, trackingMethod, classification, applicableRate, calculatedDeduction, waypointsJson, syncStatus)
                VALUES ('trip-v2', '2026-11-06', 1000, 2000, 37.33, -121.88, 37.35, -121.89, 8.5, 8.5, 'MySucculentCorner', 'Client Visit', 'AUTO_DETECTED', 'BUSINESS', 0.67, 5.695, '[]', 'PENDING')
            """.trimIndent())
            execSQL("""
                INSERT INTO gps_inbox (id, tripId, latitude, longitude, altitude, accuracy, speed, timestamp)
                VALUES (200, 'trip-v2', 37.33, -121.88, 10.0, 5.0, 15.0, 1000)
            """.trimIndent())
            close()
        }

        // Migrate to v3
        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, ExpenseTrackerDatabase.MIGRATION_2_3)

        // Verify v2 records preserved
        val tripCursor = db.query("SELECT id, totalDistanceMiles FROM trips WHERE id = 'trip-v2'")
        assertTrue(tripCursor.moveToFirst())
        assertEquals("trip-v2", tripCursor.getString(0))
        assertEquals(8.5, tripCursor.getDouble(1), 0.001)
        tripCursor.close()

        // Verify new columns in gps_inbox exist and defaults applied
        val inboxCursor = db.query("SELECT id, sequenceNumber, elapsedRealtimeNanos FROM gps_inbox WHERE id = 200")
        assertTrue(inboxCursor.moveToFirst())
        assertEquals(0L, inboxCursor.getLong(1))
        assertEquals(0L, inboxCursor.getLong(2))
        inboxCursor.close()

        // Verify trip_lifecycle table works in v3
        db.execSQL("""
            INSERT INTO trip_lifecycle (tripId, state, updatedAt, reason)
            VALUES ('trip-v2', 'FINALIZED', 2500, 'Test migration')
        """.trimIndent())
        val lifecycleCursor = db.query("SELECT state FROM trip_lifecycle WHERE tripId = 'trip-v2'")
        assertTrue(lifecycleCursor.moveToFirst())
        assertEquals("FINALIZED", lifecycleCursor.getString(0))
        lifecycleCursor.close()
        db.close()
    }

    @Test
    fun migrate1To3_compositeMigration_preservesAllData() {
        var db = helper.createDatabase(TEST_DB, 1).apply {
            execSQL("""
                INSERT INTO trips (id, date, startTime, endTime, startLatitude, startLongitude, endLatitude, endLongitude, totalDistanceMiles, businessMiles, businessEntity, purpose, trackingMethod, classification, applicableRate, calculatedDeduction, waypointsJson, syncStatus)
                VALUES ('trip-legacy', '2026-11-06', 1000, 2000, 37.33, -121.88, 37.35, -121.89, 12.0, 12.0, 'MySucculentCorner', 'Delivery', 'AUTO_DETECTED', 'BUSINESS', 0.67, 8.04, '[]', 'PENDING')
            """.trimIndent())
            close()
        }

        // Migrate directly 1 -> 3
        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, ExpenseTrackerDatabase.MIGRATION_1_3)

        val cursor = db.query("SELECT id, totalDistanceMiles FROM trips WHERE id = 'trip-legacy'")
        assertTrue(cursor.moveToFirst())
        assertEquals("trip-legacy", cursor.getString(0))
        assertEquals(12.0, cursor.getDouble(1), 0.001)
        cursor.close()
        db.close()
    }
}
