package com.businesstracker.mileage.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import com.businesstracker.mileage.data.local.entity.GpsInboxEntity
import com.businesstracker.mileage.data.local.entity.TripEntity

@Database(
    entities = [
        TripEntity::class,
        ActiveTripEntity::class,
        ActiveWaypointEntity::class,
        GpsInboxEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ExpenseTrackerDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun activeTripDao(): ActiveTripDao
    abstract fun gpsInboxDao(): GpsInboxDao

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
    }
}
