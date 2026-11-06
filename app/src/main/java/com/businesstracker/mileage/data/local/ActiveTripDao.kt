package com.businesstracker.mileage.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveTripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActiveTrip(activeTrip: ActiveTripEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActiveTripIgnore(activeTrip: ActiveTripEntity): Long

    @Transaction
    suspend fun getOrCreateActiveTrip(activeTrip: ActiveTripEntity): ActiveTripEntity {
        val existing = getActiveTrip()
        if (existing != null) {
            return existing
        }
        insertActiveTripIgnore(activeTrip)
        return getActiveTrip() ?: activeTrip
    }

    @Update
    suspend fun updateActiveTrip(activeTrip: ActiveTripEntity)

    @Query("SELECT * FROM active_trip LIMIT 1")
    suspend fun getActiveTrip(): ActiveTripEntity?

    @Query("SELECT * FROM active_trip WHERE id = :tripId LIMIT 1")
    suspend fun getActiveTripById(tripId: String): ActiveTripEntity?

    @Query("SELECT * FROM active_trip LIMIT 1")
    fun observeActiveTrip(): Flow<ActiveTripEntity?>

    @Query("DELETE FROM active_trip WHERE id = :tripId")
    suspend fun deleteActiveTripById(tripId: String): Int

    @Transaction
    suspend fun clearActiveTripById(tripId: String): Int {
        deleteWaypointsForTrip(tripId)
        return deleteActiveTripById(tripId)
    }

    @Query("DELETE FROM active_trip")
    suspend fun clearActiveTrip()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoint(waypoint: ActiveWaypointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoints(waypoints: List<ActiveWaypointEntity>)

    @Query("SELECT * FROM active_waypoints WHERE tripId = :tripId ORDER BY timestamp ASC, id ASC")
    suspend fun getWaypointsForTrip(tripId: String): List<ActiveWaypointEntity>

    @Query("SELECT * FROM active_waypoints WHERE tripId = :tripId ORDER BY timestamp ASC, id ASC")
    fun observeWaypointsForTrip(tripId: String): Flow<List<ActiveWaypointEntity>>

    @Query("SELECT * FROM active_waypoints WHERE tripId = :tripId ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun getLastWaypointForTrip(tripId: String): ActiveWaypointEntity?

    @Query("SELECT COUNT(*) FROM active_waypoints WHERE tripId = :tripId AND sourceFixId = :fixId")
    suspend fun countWaypointsForFix(tripId: String, fixId: Long): Int

    @Query("DELETE FROM active_waypoints WHERE tripId = :tripId")
    suspend fun deleteWaypointsForTrip(tripId: String)

    @Transaction
    suspend fun clearAllActiveData() {
        clearActiveTrip()
    }

    @Query("UPDATE active_trip SET totalDistanceMeters = :distanceMeters, lastLatitude = :lat, lastLongitude = :lng, lastTimestamp = :timestamp WHERE id = :tripId")
    suspend fun updateActiveTripProgress(tripId: String, distanceMeters: Double, lat: Double, lng: Double, timestamp: Long)

    @Query("UPDATE active_trip SET isPaused = :isPaused WHERE id = :tripId")
    suspend fun setPausedState(tripId: String, isPaused: Boolean)
}
