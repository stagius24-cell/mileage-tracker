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

    @Test
    fun migrate3To4_preservesExistingData_andAddsTripSequenceAndWaypointColumns() {
        var db = helper.createDatabase(TEST_DB, 3).apply {
            execSQL("""
                INSERT INTO active_trip (id, startTime, startLatitude, startLongitude, lastLatitude, lastLongitude, lastTimestamp, totalDistanceMeters, trackingMethod, isPaused, pausedDurationMillis, suggestedBusiness)
                VALUES ('active-v3', 1000, 37.33, -121.88, 37.35, -121.89, 2000, 2500.0, 'AUTO_DETECTED', 0, 0, 'MySucculentCorner')
            """.trimIndent())
            execSQL("""
                INSERT INTO active_waypoints (id, tripId, sourceFixId, latitude, longitude, altitude, accuracy, speed, timestamp)
                VALUES (1, 'active-v3', 10, 37.33, -121.88, 10.0, 5.0, 15.0, 1000)
            """.trimIndent())
            close()
        }

        // Migrate to v4
        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, ExpenseTrackerDatabase.MIGRATION_3_4)

        // Verify existing active trip and waypoint preserved
        val tripCursor = db.query("SELECT id, totalDistanceMeters FROM active_trip WHERE id = 'active-v3'")
        assertTrue(tripCursor.moveToFirst())
        assertEquals("active-v3", tripCursor.getString(0))
        assertEquals(2500.0, tripCursor.getDouble(1), 0.001)
        tripCursor.close()

        val wpCursor = db.query("SELECT id, sequenceNumber, elapsedRealtimeNanos FROM active_waypoints WHERE id = 1")
        assertTrue(wpCursor.moveToFirst())
        assertEquals(1L, wpCursor.getLong(0))
        assertEquals(0L, wpCursor.getLong(1))
        assertEquals(0L, wpCursor.getLong(2))
        wpCursor.close()

        // Verify trip_sequence table works in v4
        db.execSQL("""
            INSERT INTO trip_sequence (tripId, lastSequenceNumber)
            VALUES ('active-v3', 42)
        """.trimIndent())
        val seqCursor = db.query("SELECT lastSequenceNumber FROM trip_sequence WHERE tripId = 'active-v3'")
        assertTrue(seqCursor.moveToFirst())
        assertEquals(42L, seqCursor.getLong(0))
        seqCursor.close()
        db.close()
    }

    @Test
    fun migrate2To4_preservesExistingData() {
        var db = helper.createDatabase(TEST_DB, 2).apply {
            execSQL("""
                INSERT INTO trips (id, date, startTime, endTime, startLatitude, startLongitude, endLatitude, endLongitude, totalDistanceMiles, businessMiles, businessEntity, purpose, trackingMethod, classification, applicableRate, calculatedDeduction, waypointsJson, syncStatus)
                VALUES ('trip-v2-to-v4', '2026-11-06', 1000, 2000, 37.33, -121.88, 37.35, -121.89, 7.5, 7.5, 'MySucculentCorner', 'Delivery', 'AUTO_DETECTED', 'BUSINESS', 0.67, 5.025, '[]', 'PENDING')
            """.trimIndent())
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, ExpenseTrackerDatabase.MIGRATION_2_4)

        val cursor = db.query("SELECT id, totalDistanceMiles FROM trips WHERE id = 'trip-v2-to-v4'")
        assertTrue(cursor.moveToFirst())
        assertEquals("trip-v2-to-v4", cursor.getString(0))
        assertEquals(7.5, cursor.getDouble(1), 0.001)
        cursor.close()
        db.close()
    }

    @Test
    fun migrate1To4_compositeMigration_preservesAllData() {
        var db = helper.createDatabase(TEST_DB, 1).apply {
            execSQL("""
                INSERT INTO trips (id, date, startTime, endTime, startLatitude, startLongitude, endLatitude, endLongitude, totalDistanceMiles, businessMiles, businessEntity, purpose, trackingMethod, classification, applicableRate, calculatedDeduction, waypointsJson, syncStatus)
                VALUES ('trip-v1-to-v4', '2026-11-06', 1000, 2000, 37.33, -121.88, 37.35, -121.89, 15.0, 15.0, 'MySucculentCorner', 'Delivery', 'AUTO_DETECTED', 'BUSINESS', 0.67, 10.05, '[]', 'PENDING')
            """.trimIndent())
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, ExpenseTrackerDatabase.MIGRATION_1_4)

        val cursor = db.query("SELECT id, totalDistanceMiles FROM trips WHERE id = 'trip-v1-to-v4'")
        assertTrue(cursor.moveToFirst())
        assertEquals("trip-v1-to-v4", cursor.getString(0))
        assertEquals(15.0, cursor.getDouble(1), 0.001)
        cursor.close()
        db.close()
    }

    @Test
    fun migrate4To5_preservesExistingData_andAddsBootCountColumns() {
        var db = helper.createDatabase(TEST_DB, 4).apply {
            execSQL("""
                INSERT INTO active_trip (id, startTime, startLatitude, startLongitude, lastLatitude, lastLongitude, lastTimestamp, totalDistanceMeters, trackingMethod, isPaused, pausedDurationMillis, suggestedBusiness)
                VALUES ('active-v4', 1000, 37.33, -121.88, 37.35, -121.89, 2000, 3200.0, 'AUTO_DETECTED', 0, 0, 'MySucculentCorner')
            """.trimIndent())
            execSQL("""
                INSERT INTO active_waypoints (id, tripId, sourceFixId, sequenceNumber, elapsedRealtimeNanos, latitude, longitude, altitude, accuracy, speed, timestamp)
                VALUES (1, 'active-v4', 10, 5, 5000000000, 37.33, -121.88, 10.0, 5.0, 15.0, 1000)
            """.trimIndent())
            execSQL("""
                INSERT INTO gps_inbox (id, tripId, sequenceNumber, elapsedRealtimeNanos, latitude, longitude, altitude, accuracy, speed, timestamp)
                VALUES (101, 'active-v4', 6, 6000000000, 37.34, -121.89, 10.0, 5.0, 15.0, 2000)
            """.trimIndent())
            close()
        }

        // Migrate to v5
        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, ExpenseTrackerDatabase.MIGRATION_4_5)

        // Verify existing active trip, waypoint, and inbox preserved
        val tripCursor = db.query("SELECT id, totalDistanceMeters FROM active_trip WHERE id = 'active-v4'")
        assertTrue(tripCursor.moveToFirst())
        assertEquals("active-v4", tripCursor.getString(0))
        assertEquals(3200.0, tripCursor.getDouble(1), 0.001)
        tripCursor.close()

        val wpCursor = db.query("SELECT id, sequenceNumber, elapsedRealtimeNanos, bootCount FROM active_waypoints WHERE id = 1")
        assertTrue(wpCursor.moveToFirst())
        assertEquals(1L, wpCursor.getLong(0))
        assertEquals(5L, wpCursor.getLong(1))
        assertEquals(5000000000L, wpCursor.getLong(2))
        assertEquals(0, wpCursor.getInt(3)) // Default bootCount is 0 for legacy records
        wpCursor.close()

        val inboxCursor = db.query("SELECT id, sequenceNumber, bootCount FROM gps_inbox WHERE id = 101")
        assertTrue(inboxCursor.moveToFirst())
        assertEquals(101L, inboxCursor.getLong(0))
        assertEquals(6L, inboxCursor.getLong(1))
        assertEquals(0, inboxCursor.getInt(2))
        inboxCursor.close()

        db.close()
    }

    @Test
    fun migrate3To5_compositeMigration_preservesAllData() {
        var db = helper.createDatabase(TEST_DB, 3).apply {
            execSQL("""
                INSERT INTO trips (id, date, startTime, endTime, startLatitude, startLongitude, endLatitude, endLongitude, totalDistanceMiles, businessMiles, businessEntity, purpose, trackingMethod, classification, applicableRate, calculatedDeduction, waypointsJson, syncStatus)
                VALUES ('trip-v3-to-v5', '2026-11-06', 1000, 2000, 37.33, -121.88, 37.35, -121.89, 9.0, 9.0, 'MySucculentCorner', 'Delivery', 'AUTO_DETECTED', 'BUSINESS', 0.67, 6.03, '[]', 'PENDING')
            """.trimIndent())
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, ExpenseTrackerDatabase.MIGRATION_3_5)

        val cursor = db.query("SELECT id, totalDistanceMiles FROM trips WHERE id = 'trip-v3-to-v5'")
        assertTrue(cursor.moveToFirst())
        assertEquals("trip-v3-to-v5", cursor.getString(0))
        assertEquals(9.0, cursor.getDouble(1), 0.001)
        cursor.close()
        db.close()
    }

    @Test
    fun migrate2To5_compositeMigration_preservesAllData() {
        var db = helper.createDatabase(TEST_DB, 2).apply {
            execSQL("""
                INSERT INTO trips (id, date, startTime, endTime, startLatitude, startLongitude, endLatitude, endLongitude, totalDistanceMiles, businessMiles, businessEntity, purpose, trackingMethod, classification, applicableRate, calculatedDeduction, waypointsJson, syncStatus)
                VALUES ('trip-v2-to-v5', '2026-11-06', 1000, 2000, 37.33, -121.88, 37.35, -121.89, 11.5, 11.5, 'MySucculentCorner', 'Client Visit', 'AUTO_DETECTED', 'BUSINESS', 0.67, 7.705, '[]', 'PENDING')
            """.trimIndent())
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, ExpenseTrackerDatabase.MIGRATION_2_5)

        val cursor = db.query("SELECT id, totalDistanceMiles FROM trips WHERE id = 'trip-v2-to-v5'")
        assertTrue(cursor.moveToFirst())
        assertEquals("trip-v2-to-v5", cursor.getString(0))
        assertEquals(11.5, cursor.getDouble(1), 0.001)
        cursor.close()
        db.close()
    }

    @Test
    fun migrate1To5_compositeMigration_preservesAllData() {
        var db = helper.createDatabase(TEST_DB, 1).apply {
            execSQL("""
                INSERT INTO trips (id, date, startTime, endTime, startLatitude, startLongitude, endLatitude, endLongitude, totalDistanceMiles, businessMiles, businessEntity, purpose, trackingMethod, classification, applicableRate, calculatedDeduction, waypointsJson, syncStatus)
                VALUES ('trip-v1-to-v5', '2026-11-06', 1000, 2000, 37.33, -121.88, 37.35, -121.89, 18.0, 18.0, 'MySucculentCorner', 'Delivery', 'AUTO_DETECTED', 'BUSINESS', 0.67, 12.06, '[]', 'PENDING')
            """.trimIndent())
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, ExpenseTrackerDatabase.MIGRATION_1_5)

        val cursor = db.query("SELECT id, totalDistanceMiles FROM trips WHERE id = 'trip-v1-to-v5'")
        assertTrue(cursor.moveToFirst())
        assertEquals("trip-v1-to-v5", cursor.getString(0))
        assertEquals(18.0, cursor.getDouble(1), 0.001)
        cursor.close()
        db.close()
    }
    }
}
