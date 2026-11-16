package com.businesstracker.mileage.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.businesstracker.mileage.data.local.entity.TripLearningRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripLearningRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: TripLearningRecordEntity)

    @Update
    suspend fun updateRecord(record: TripLearningRecordEntity)

    @Delete
    suspend fun deleteRecord(record: TripLearningRecordEntity)

    @Query("DELETE FROM trip_learning_records WHERE tripId = :tripId")
    suspend fun deleteRecordByTripId(tripId: String): Int

    @Query("SELECT * FROM trip_learning_records WHERE tripId = :tripId LIMIT 1")
    suspend fun getByTripId(tripId: String): TripLearningRecordEntity?

    @Query("SELECT * FROM trip_learning_records WHERE tripId = :tripId LIMIT 1")
    fun observeByTripId(tripId: String): Flow<TripLearningRecordEntity?>

    @Query("SELECT * FROM trip_learning_records ORDER BY createdAt DESC")
    suspend fun getAllRecords(): List<TripLearningRecordEntity>

    @Query("SELECT * FROM trip_learning_records WHERE patternId = :patternId")
    suspend fun getRecordsByPatternId(patternId: String): List<TripLearningRecordEntity>

    @Query("SELECT COUNT(*) FROM trip_learning_records WHERE patternId = :patternId")
    suspend fun countRecordsForPattern(patternId: String): Int

    @Query("SELECT COUNT(*) FROM trip_learning_records WHERE eligible = 1")
    suspend fun countEligibleRecords(): Int
}
