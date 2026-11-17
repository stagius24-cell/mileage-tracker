package com.businesstracker.mileage.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import com.businesstracker.mileage.data.local.entity.GpsInboxEntity
import com.businesstracker.mileage.data.local.entity.RoutePatternEntity
import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import com.businesstracker.mileage.data.local.entity.SavedLocationSyncQueueEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.entity.TripLearningRecordEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleEntity
import com.businesstracker.mileage.data.local.entity.TripSequenceEntity
import com.businesstracker.mileage.data.local.entity.VehicleEntity

@Database(
    entities = [
        TripEntity::class,
        ActiveTripEntity::class,
        ActiveWaypointEntity::class,
        GpsInboxEntity::class,
        TripLifecycleEntity::class,
        TripSequenceEntity::class,
        SavedLocationEntity::class,
        SavedLocationSyncQueueEntity::class,
        RoutePatternEntity::class,
        TripLearningRecordEntity::class,
        VehicleEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class ExpenseTrackerDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun activeTripDao(): ActiveTripDao
    abstract fun gpsInboxDao(): GpsInboxDao
    abstract fun tripLifecycleDao(): TripLifecycleDao
    abstract fun tripSequenceDao(): TripSequenceDao
    abstract fun savedLocationDao(): SavedLocationDao
    abstract fun savedLocationSyncQueueDao(): SavedLocationSyncQueueDao
    abstract fun routePatternDao(): RoutePatternDao
    abstract fun tripLearningRecordDao(): TripLearningRecordDao
    abstract fun vehicleDao(): VehicleDao

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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `saved_locations` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `address` TEXT NOT NULL,
                        `radiusMeters` INTEGER NOT NULL,
                        `icon` TEXT NOT NULL,
                        `classificationPreference` TEXT,
                        `businessEntity` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_locations_name` ON `saved_locations` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_locations_latitude_longitude` ON `saved_locations` (`latitude`, `longitude`)")
            }
        }

        val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_3_6 = object : Migration(3, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_2_6 = object : Migration(2, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_1_6 = object : Migration(1, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `saved_location_sync_queue` (
                        `locationId` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`locationId`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_7 = object : Migration(5, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_4_7 = object : Migration(4, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5.migrate(db)
                MIGRATION_5_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_3_7 = object : Migration(3, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
                MIGRATION_5_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_2_7 = object : Migration(2, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
                MIGRATION_5_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_1_7 = object : Migration(1, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
                MIGRATION_5_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create route_patterns table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `route_patterns` (
                        `id` TEXT NOT NULL,
                        `startLatitude` REAL NOT NULL,
                        `startLongitude` REAL NOT NULL,
                        `startRadiusMeters` REAL NOT NULL,
                        `endLatitude` REAL NOT NULL,
                        `endLongitude` REAL NOT NULL,
                        `endRadiusMeters` REAL NOT NULL,
                        `startAddress` TEXT,
                        `endAddress` TEXT,
                        `classification` TEXT NOT NULL,
                        `businessEntity` TEXT NOT NULL,
                        `purpose` TEXT NOT NULL,
                        `sampleCount` INTEGER NOT NULL,
                        `confidence` REAL NOT NULL,
                        `lastLearnedAt` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_patterns_startLatitude_startLongitude` ON `route_patterns` (`startLatitude`, `startLongitude`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_patterns_endLatitude_endLongitude` ON `route_patterns` (`endLatitude`, `endLongitude`)")

                // 2. Create trip_learning_records table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `trip_learning_records` (
                        `tripId` TEXT NOT NULL,
                        `eligible` INTEGER NOT NULL,
                        `classificationSource` TEXT NOT NULL,
                        `confirmedCategory` TEXT,
                        `businessEntity` TEXT,
                        `purpose` TEXT,
                        `startLatitude` REAL NOT NULL,
                        `startLongitude` REAL NOT NULL,
                        `endLatitude` REAL NOT NULL,
                        `endLongitude` REAL NOT NULL,
                        `patternId` TEXT,
                        `learnedAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`tripId`),
                        FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`patternId`) REFERENCES `route_patterns`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_learning_records_tripId` ON `trip_learning_records` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_learning_records_patternId` ON `trip_learning_records` (`patternId`)")

                // 3. Add classification provenance columns to trips
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `classificationSource` TEXT NOT NULL DEFAULT 'UNCLASSIFIED'")
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `suggestedClassification` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `vehicleId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `active_trip` ADD COLUMN `vehicleId` TEXT DEFAULT NULL")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `vehicles` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `bluetoothAddress` TEXT,
                        `bluetoothDeviceName` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicles_bluetoothAddress` ON `vehicles` (`bluetoothAddress`)")
            }
        }

        val MIGRATION_6_8 = object : Migration(6, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_7.migrate(db)
                MIGRATION_7_8.migrate(db)
            }
        }

        val MIGRATION_5_8 = object : Migration(5, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_6.migrate(db)
                MIGRATION_6_7.migrate(db)
                MIGRATION_7_8.migrate(db)
            }
        }

        val MIGRATION_4_8 = object : Migration(4, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5.migrate(db)
                MIGRATION_5_6.migrate(db)
                MIGRATION_6_7.migrate(db)
                MIGRATION_7_8.migrate(db)
            }
        }

        val MIGRATION_3_8 = object : Migration(3, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
                MIGRATION_5_6.migrate(db)
                MIGRATION_6_7.migrate(db)
                MIGRATION_7_8.migrate(db)
            }
        }

        val MIGRATION_2_8 = object : Migration(2, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
                MIGRATION_5_6.migrate(db)
                MIGRATION_6_7.migrate(db)
                MIGRATION_7_8.migrate(db)
            }
        }

        val MIGRATION_1_8 = object : Migration(1, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
                MIGRATION_5_6.migrate(db)
                MIGRATION_6_7.migrate(db)
                MIGRATION_7_8.migrate(db)
            }
        }
    }
}
