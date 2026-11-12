package com.businesstracker.mileage.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.businesstracker.mileage.data.local.entity.SavedLocationSyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLocationSyncQueueDao {

    @Query("SELECT * FROM saved_location_sync_queue ORDER BY updatedAt ASC")
    suspend fun getAllPending(): List<SavedLocationSyncQueueEntity>

    @Query("SELECT COUNT(*) FROM saved_location_sync_queue")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM saved_location_sync_queue")
    suspend fun getPendingCount(): Int

    @Query("SELECT * FROM saved_location_sync_queue WHERE locationId = :locationId LIMIT 1")
    suspend fun getPendingById(locationId: String): SavedLocationSyncQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPending(item: SavedLocationSyncQueueEntity)

    @Query("DELETE FROM saved_location_sync_queue WHERE locationId = :locationId")
    suspend fun deletePendingById(locationId: String)

    @Query("DELETE FROM saved_location_sync_queue WHERE locationId IN (:locationIds)")
    suspend fun deletePendingByIds(locationIds: List<String>)

    @Query("DELETE FROM saved_location_sync_queue")
    suspend fun clearAll()
}
