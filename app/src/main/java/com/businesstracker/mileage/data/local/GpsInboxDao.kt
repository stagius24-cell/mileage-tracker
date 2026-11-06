package com.businesstracker.mileage.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.businesstracker.mileage.data.local.entity.GpsInboxEntity

@Dao
interface GpsInboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFix(fix: GpsInboxEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFixes(fixes: List<GpsInboxEntity>): List<Long>

    @Query("SELECT * FROM gps_inbox WHERE id = :id")
    suspend fun getFixById(id: Long): GpsInboxEntity?

    @Query("SELECT * FROM gps_inbox WHERE tripId = :tripId ORDER BY sequenceNumber ASC, elapsedRealtimeNanos ASC, timestamp ASC, id ASC LIMIT :limit")
    suspend fun getPendingFixes(tripId: String, limit: Int): List<GpsInboxEntity>

    @Query("SELECT COUNT(*) FROM gps_inbox WHERE tripId = :tripId")
    suspend fun getPendingCount(tripId: String): Int

    @Query("DELETE FROM gps_inbox WHERE id = :id")
    suspend fun deleteFixById(id: Long): Int

    @Query("DELETE FROM gps_inbox WHERE id IN (:ids)")
    suspend fun deleteFixesByIds(ids: List<Long>): Int

    @Query("DELETE FROM gps_inbox WHERE tripId = :tripId")
    suspend fun deleteFixesForTrip(tripId: String): Int

    @Query("DELETE FROM gps_inbox")
    suspend fun clearAll(): Int
}
