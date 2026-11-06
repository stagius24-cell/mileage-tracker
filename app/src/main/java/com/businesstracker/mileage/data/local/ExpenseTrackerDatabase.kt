package com.businesstracker.mileage.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import com.businesstracker.mileage.data.local.entity.TripEntity

@Database(
    entities = [
        TripEntity::class,
        ActiveTripEntity::class,
        ActiveWaypointEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ExpenseTrackerDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun activeTripDao(): ActiveTripDao

    companion object {
        const val DATABASE_NAME = "EXPENSE_TRACKER_DB"
    }
}
