package com.businesstracker.mileage.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLocationDao {

    @Query("SELECT * FROM saved_locations ORDER BY name ASC")
    fun getAllLocationsFlow(): Flow<List<SavedLocationEntity>>

    @Query("SELECT * FROM saved_locations ORDER BY name ASC")
    suspend fun getAllLocations(): List<SavedLocationEntity>

    @Query("SELECT * FROM saved_locations WHERE id = :id")
    suspend fun getLocationById(id: String): SavedLocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: SavedLocationEntity)

    @Update
    suspend fun updateLocation(location: SavedLocationEntity)

    @Query("DELETE FROM saved_locations WHERE id = :id")
    suspend fun deleteLocationById(id: String)

    @Query("SELECT COUNT(*) FROM saved_locations")
    suspend fun getCount(): Int
}
