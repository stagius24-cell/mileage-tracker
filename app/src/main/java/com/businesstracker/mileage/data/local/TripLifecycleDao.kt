package com.businesstracker.mileage.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.businesstracker.mileage.data.local.entity.TripLifecycleEntity

@Dao
interface TripLifecycleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(lifecycle: TripLifecycleEntity)

    @Query("SELECT * FROM trip_lifecycle WHERE tripId = :tripId")
    suspend fun getLifecycle(tripId: String): TripLifecycleEntity?

    @Query("SELECT state FROM trip_lifecycle WHERE tripId = :tripId")
    suspend fun getState(tripId: String): String?

    @Query("UPDATE trip_lifecycle SET state = :newState, updatedAt = :timestamp WHERE tripId = :tripId")
    suspend fun updateState(tripId: String, newState: String, timestamp: Long = System.currentTimeMillis()): Int

    /**
     * Conditional atomic state transition:
     * Only transitions if current state matches expectedState.
     * Prevents two callers from independently claiming or conflicting over finalization.
     */
    @Query("UPDATE trip_lifecycle SET state = :newState, updatedAt = :timestamp WHERE tripId = :tripId AND state = :expectedState")
    suspend fun transitionState(tripId: String, expectedState: String, newState: String, timestamp: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM trip_lifecycle WHERE tripId = :tripId")
    suspend fun deleteLifecycle(tripId: String): Int

    @Query("DELETE FROM trip_lifecycle")
    suspend fun clearAll(): Int
}
