package com.businesstracker.mileage.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.model.BusinessEntitySummary
import com.businesstracker.mileage.data.local.model.ClassificationCount
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTripIgnore(trip: TripEntity): Long

    @Query("DELETE FROM active_trip WHERE id = :tripId")
    suspend fun deleteActiveTripById(tripId: String): Int

    @Query("DELETE FROM active_waypoints WHERE tripId = :tripId")
    suspend fun deleteActiveWaypointsByTripId(tripId: String): Int

    @androidx.room.Transaction
    suspend fun persistFinalizedTripAndClearActive(trip: TripEntity): Boolean {
        val insertedRowId = insertTripIgnore(trip)
        // Delete active session atomically regardless of whether row was newly inserted or previously present
        deleteActiveTripById(trip.id)
        deleteActiveWaypointsByTripId(trip.id)
        return insertedRowId != -1L
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<TripEntity>)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Delete
    suspend fun deleteTrip(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTripById(tripId: String)

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    suspend fun getTripById(tripId: String): TripEntity?

    // Fill missing labels only. Never replace coordinates, trip totals, edits or existing addresses.
    @Query("""
        UPDATE trips SET
          startAddress = CASE WHEN startAddress IS NULL OR TRIM(startAddress) = '' OR startAddress = 'Unknown Location'
            THEN COALESCE(:startAddress, startAddress) ELSE startAddress END,
          endAddress = CASE WHEN endAddress IS NULL OR TRIM(endAddress) = '' OR endAddress = 'Unknown Location'
            THEN COALESCE(:endAddress, endAddress) ELSE endAddress END
        WHERE id = :tripId AND startLatitude = :startLat AND startLongitude = :startLon
          AND endLatitude = :endLat AND endLongitude = :endLon
    """)
    suspend fun fillMissingAddresses(tripId: String, startAddress: String?, endAddress: String?,
        startLat: Double, startLon: Double, endLat: Double, endLon: Double): Int

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    fun observeTripById(tripId: String): Flow<TripEntity?>

    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    // Unclassified trips (classification == 'UNREVIEWED')
    @Query("SELECT * FROM trips WHERE classification = 'UNREVIEWED' ORDER BY startTime DESC")
    fun getUnclassifiedTrips(): Flow<List<TripEntity>>

    @Query("SELECT COUNT(*) FROM trips WHERE classification = 'UNREVIEWED'")
    fun getUnclassifiedTripsCount(): Flow<Int>

    // Date range queries (ISO YYYY-MM-DD or timestamps)
    @Query("SELECT * FROM trips WHERE date BETWEEN :startDate AND :endDate ORDER BY startTime DESC")
    fun getTripsByDateRange(startDate: String, endDate: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE startTime >= :startMillis AND endTime <= :endMillis ORDER BY startTime DESC")
    fun getTripsByTimeRange(startMillis: Long, endMillis: Long): Flow<List<TripEntity>>

    // Filter by business entity
    @Query("SELECT * FROM trips WHERE businessEntity = :entity ORDER BY startTime DESC")
    fun getTripsByBusinessEntity(entity: String): Flow<List<TripEntity>>

    // Business entity aggregation queries
    @Query("""
        SELECT 
            businessEntity,
            COALESCE(SUM(businessMiles), 0.0) as totalBusinessMiles,
            COALESCE(SUM(totalDistanceMiles), 0.0) as totalMiles,
            COALESCE(SUM(calculatedDeduction), 0.0) as totalDeduction,
            COUNT(*) as tripCount
        FROM trips
        WHERE classification = 'BUSINESS'
        GROUP BY businessEntity
    """)
    fun getBusinessEntitySummaries(): Flow<List<BusinessEntitySummary>>

    @Query("""
        SELECT 
            businessEntity,
            COALESCE(SUM(businessMiles), 0.0) as totalBusinessMiles,
            COALESCE(SUM(totalDistanceMiles), 0.0) as totalMiles,
            COALESCE(SUM(calculatedDeduction), 0.0) as totalDeduction,
            COUNT(*) as tripCount
        FROM trips
        WHERE classification = 'BUSINESS' AND date BETWEEN :startDate AND :endDate
        GROUP BY businessEntity
    """)
    fun getBusinessEntitySummariesForDateRange(startDate: String, endDate: String): Flow<List<BusinessEntitySummary>>

    @Query("""
        SELECT 
            classification,
            COUNT(*) as count
        FROM trips
        GROUP BY classification
    """)
    fun getTripCountByClassification(): Flow<List<ClassificationCount>>

    @Query("SELECT * FROM trips WHERE syncStatus = :syncStatus ORDER BY startTime DESC")
    fun getTripsBySyncStatus(syncStatus: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE classification = :classification ORDER BY startTime DESC")
    fun getTripsByClassification(classification: String): Flow<List<TripEntity>>

    // Pending sync queries for offline-first synchronization
    @Query("SELECT * FROM trips WHERE syncStatus = 'PENDING' ORDER BY startTime ASC")
    suspend fun getPendingSyncTrips(): List<TripEntity>

    @Query("UPDATE trips SET syncStatus = :status, syncedAt = :syncedAt WHERE id = :tripId")
    suspend fun updateSyncStatus(tripId: String, status: String, syncedAt: Long?)

    // Quick classification update
    @Query("""
        UPDATE trips 
        SET classification = :classification, 
            businessEntity = :businessEntity, 
            purpose = :purpose, 
            businessMiles = CASE WHEN :classification = 'BUSINESS' THEN totalDistanceMiles ELSE 0.0 END,
            calculatedDeduction = CASE WHEN :classification = 'BUSINESS' THEN totalDistanceMiles * applicableRate ELSE 0.0 END,
            notes = :notes,
            syncStatus = 'PENDING'
        WHERE id = :tripId
    """)
    suspend fun classifyTrip(
        tripId: String,
        classification: String,
        businessEntity: String,
        purpose: String,
        notes: String?
    )

    @Query("""
        UPDATE trips 
        SET classification = :classification, 
            businessEntity = :businessEntity, 
            purpose = :purpose, 
            businessMiles = CASE WHEN :classification = 'BUSINESS' THEN totalDistanceMiles ELSE 0.0 END,
            calculatedDeduction = CASE WHEN :classification = 'BUSINESS' THEN totalDistanceMiles * applicableRate ELSE 0.0 END,
            notes = :notes,
            syncStatus = 'PENDING'
        WHERE id = :tripId
    """)
    suspend fun updateTripDetails(
        tripId: String,
        classification: String,
        businessEntity: String,
        purpose: String,
        notes: String?
    )
}
