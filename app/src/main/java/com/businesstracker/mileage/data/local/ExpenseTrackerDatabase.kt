package com.businesstracker.mileage.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import com.businesstracker.mileage.data.local.entity.GpsInboxEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleEntity

@Database(
    entities = [
        TripEntity::class,
        ActiveTripEntity::class,
        ActiveWaypointEntity::class,
        GpsInboxEntity::class,
        TripLifecycleEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class ExpenseTrackerDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun activeTripDao(): ActiveTripDao
    abstract fun gpsInboxDao(): GpsInboxDao
    abstract fun tripLifecycleDao(): TripLifecycleDao

    companion object {
        const val DATABASE_NAME = "EXPENSE_TRACKER_DB"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `gps_inbox` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `tripId` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `altitude` REAL NOT NULL,
                        `accuracy` REAL NOT NULL,
                        `speed` REAL NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_inbox_tripId_timestamp` ON `gps_inbox` (`tripId`, `timestamp`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Persistent trip lifecycle table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `trip_lifecycle` (
                        `tripId` TEXT PRIMARY KEY NOT NULL,
                        `state` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `reason` TEXT
                    )
                """.trimIndent())

                // 2. Monotonic time and sequence columns for gps_inbox
                db.execSQL("ALTER TABLE `gps_inbox` ADD COLUMN `sequenceNumber` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `gps_inbox` ADD COLUMN `elapsedRealtimeNanos` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_inbox_tripId_sequenceNumber` ON `gps_inbox` (`tripId`, `sequenceNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_inbox_tripId_elapsedRealtimeNanos_timestamp_id` ON `gps_inbox` (`tripId`, `elapsedRealtimeNanos`, `timestamp`, `id`)")

                // 3. Stable source identifier for active_waypoints idempotency
                db.execSQL("ALTER TABLE `active_waypoints` ADD COLUMN `sourceFixId` INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_active_waypoints_tripId_sourceFixId` ON `active_waypoints` (`tripId`, `sourceFixId`)")
            }
        }

        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
            }
        }
    }
}
