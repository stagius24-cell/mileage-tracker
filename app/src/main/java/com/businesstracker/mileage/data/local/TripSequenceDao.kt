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

    /**
     * Calculates the true maximum monotonic sequence number across all durable persistence tables:
     * 1. trip_sequence (last committed sequence number)
     * 2. gps_inbox (unprocessed raw fixes pending consumption)
     * 3. active_waypoints (persisted and accepted trip waypoints)
     *
     * Ensures sequence numbers are never reused or decreased even after process crashes or restarts.
     */
    @Query("""
        SELECT COALESCE(MAX(seq), 0) FROM (
            SELECT COALESCE(MAX(lastSequenceNumber), 0) AS seq FROM trip_sequence WHERE tripId = :tripId
            UNION ALL
            SELECT COALESCE(MAX(sequenceNumber), 0) AS seq FROM gps_inbox WHERE tripId = :tripId
            UNION ALL
            SELECT COALESCE(MAX(sequenceNumber), 0) AS seq FROM active_waypoints WHERE tripId = :tripId
        )
    """)
    suspend fun getReconciledMaxSequence(tripId: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: TripSequenceEntity)

    @Transaction
    suspend fun getNextSequenceNumber(tripId: String): Long {
        val current = getReconciledMaxSequence(tripId)
        val next = current + 1L
        insertOrUpdate(TripSequenceEntity(tripId = tripId, lastSequenceNumber = next))
        return next
    }

    @Transaction
    suspend fun advanceSequence(tripId: String, lastAssigned: Long) {
        val current = getReconciledMaxSequence(tripId)
        if (lastAssigned > current) {
            insertOrUpdate(TripSequenceEntity(tripId = tripId, lastSequenceNumber = lastAssigned))
        }
    }

    @Query("DELETE FROM trip_sequence WHERE tripId = :tripId")
    suspend fun deleteSequenceForTrip(tripId: String): Int

    @Query("DELETE FROM trip_sequence")
    suspend fun clearAll()
}
