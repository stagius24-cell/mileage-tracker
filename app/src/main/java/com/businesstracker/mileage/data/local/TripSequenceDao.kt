package com.businesstracker.mileage.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.businesstracker.mileage.data.local.entity.TripSequenceEntity

@Dao
interface TripSequenceDao {

    @Query("SELECT lastSequenceNumber FROM trip_sequence WHERE tripId = :tripId LIMIT 1")
    suspend fun getLastSequenceNumber(tripId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: TripSequenceEntity)

    @Transaction
    suspend fun getNextSequenceNumber(tripId: String): Long {
        val current = getLastSequenceNumber(tripId) ?: 0L
        val next = current + 1L
        insertOrUpdate(TripSequenceEntity(tripId = tripId, lastSequenceNumber = next))
        return next
    }

    @Transaction
    suspend fun advanceSequence(tripId: String, lastAssigned: Long) {
        val current = getLastSequenceNumber(tripId) ?: 0L
        if (lastAssigned > current) {
            insertOrUpdate(TripSequenceEntity(tripId = tripId, lastSequenceNumber = lastAssigned))
        }
    }

    @Query("DELETE FROM trip_sequence WHERE tripId = :tripId")
    suspend fun deleteSequenceForTrip(tripId: String): Int

    @Query("DELETE FROM trip_sequence")
    suspend fun clearAll()
}
