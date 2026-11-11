package com.businesstracker.mileage.domain.repository

import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import kotlinx.coroutines.flow.Flow

interface SavedLocationRepository {
    fun getAllLocationsFlow(): Flow<List<SavedLocationEntity>>
    fun getAllSavedLocations(): Flow<List<SavedLocationEntity>> = getAllLocationsFlow()
    suspend fun getAllLocations(): List<SavedLocationEntity>
    suspend fun getLocationById(id: String): SavedLocationEntity?
    suspend fun getSavedLocationById(id: String): SavedLocationEntity? = getLocationById(id)
    suspend fun saveLocation(location: SavedLocationEntity)
    suspend fun insertSavedLocation(location: SavedLocationEntity) = saveLocation(location)
    suspend fun deleteLocation(id: String)
    suspend fun deleteSavedLocation(location: SavedLocationEntity) = deleteLocation(location.id)
}
