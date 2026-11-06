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
import com.businesstracker.mileage.data.local.entity.TripSequenceEntity

@Database(
    entities = [
        TripEntity::class,
        ActiveTripEntity::class,
        ActiveWaypointEntity::class,
        GpsInboxEntity::class,
        TripLifecycleEntity::class,
        TripSequenceEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class ExpenseTrackerDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun activeTripDao(): ActiveTripDao
    abstract fun gpsInboxDao(): GpsInboxDao
    abstract fun tripLifecycleDao(): TripLifecycleDao
    abstract fun tripSequenceDao(): TripSequenceDao

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
                        `tripId` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `reason` TEXT,
                        PRIMARY KEY(`tripId`)
                    )
                """.trimIndent())

                // 2. Monotonic time and sequence columns for gps_inbox
                db.execSQL("DROP INDEX IF EXISTS `index_gps_inbox_tripId_timestamp`")
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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Durable per-trip sequence table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `trip_sequence` (
                        `tripId` TEXT NOT NULL,
                        `lastSequenceNumber` INTEGER NOT NULL,
                        PRIMARY KEY(`tripId`)
                    )
                """.trimIndent())

                // 2. Sequence number and monotonic elapsed time for active_waypoints
                db.execSQL("ALTER TABLE `active_waypoints` ADD COLUMN `sequenceNumber` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `active_waypoints` ADD COLUMN `elapsedRealtimeNanos` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_active_waypoints_tripId_sequenceNumber` ON `active_waypoints` (`tripId`, `sequenceNumber`)")
            }
        }

        val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
            }
        }

        val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `gps_inbox` ADD COLUMN `bootCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `active_waypoints` ADD COLUMN `bootCount` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
            }
        }

        val MIGRATION_2_5 = object : Migration(2, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
            }
        }

        val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
            }
        }
    }
}
