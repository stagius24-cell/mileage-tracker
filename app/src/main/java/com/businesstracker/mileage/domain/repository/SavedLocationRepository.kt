package com.businesstracker.mileage.domain.repository

import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import kotlinx.coroutines.flow.Flow

interface SavedLocationRepository {
    fun getAllLocationsFlow(): Flow<List<SavedLocationEntity>>
    suspend fun getAllLocations(): List<SavedLocationEntity>
    suspend fun getLocationById(id: String): SavedLocationEntity?
    suspend fun saveLocation(location: SavedLocationEntity)
    suspend fun deleteLocation(id: String)
}
