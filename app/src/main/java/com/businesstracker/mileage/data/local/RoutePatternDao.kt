package com.businesstracker.mileage.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.businesstracker.mileage.data.local.entity.RoutePatternEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutePatternDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutePattern(pattern: RoutePatternEntity)

    @Update
    suspend fun updateRoutePattern(pattern: RoutePatternEntity)

    @Delete
    suspend fun deleteRoutePattern(pattern: RoutePatternEntity)

    @Query("DELETE FROM route_patterns WHERE id = :id")
    suspend fun deleteRoutePatternById(id: String)

    @Query("SELECT * FROM route_patterns WHERE id = :id LIMIT 1")
    suspend fun getPatternById(id: String): RoutePatternEntity?

    @Query("SELECT * FROM route_patterns ORDER BY sampleCount DESC, lastLearnedAt DESC")
    fun getAllPatternsFlow(): Flow<List<RoutePatternEntity>>

    @Query("SELECT * FROM route_patterns ORDER BY sampleCount DESC, lastLearnedAt DESC")
    suspend fun getAllPatterns(): List<RoutePatternEntity>

    @Query("SELECT * FROM route_patterns WHERE classification = :classification ORDER BY sampleCount DESC")
    suspend fun getPatternsByClassification(classification: String): List<RoutePatternEntity>

    @Query("SELECT COUNT(*) FROM route_patterns")
    suspend fun getPatternsCount(): Int
}
