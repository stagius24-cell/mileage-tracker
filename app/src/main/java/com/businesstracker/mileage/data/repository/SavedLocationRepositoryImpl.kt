package com.businesstracker.mileage.data.repository

import com.businesstracker.mileage.data.local.SavedLocationDao
import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import com.businesstracker.mileage.domain.repository.SavedLocationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedLocationRepositoryImpl @Inject constructor(
    private val savedLocationDao: SavedLocationDao,
    private val ioDispatcher: CoroutineDispatcher
) : SavedLocationRepository {

    override fun getAllLocationsFlow(): Flow<List<SavedLocationEntity>> {
        return savedLocationDao.getAllLocationsFlow()
    }

    override suspend fun getAllLocations(): List<SavedLocationEntity> = withContext(ioDispatcher) {
        savedLocationDao.getAllLocations()
    }

    override suspend fun getLocationById(id: String): SavedLocationEntity? = withContext(ioDispatcher) {
        savedLocationDao.getLocationById(id)
    }

    override suspend fun saveLocation(location: SavedLocationEntity) = withContext(ioDispatcher) {
        val existing = savedLocationDao.getLocationById(location.id)
        if (existing == null) {
            savedLocationDao.insertLocation(location)
        } else {
            savedLocationDao.updateLocation(location)
        }
    }

    override suspend fun deleteLocation(id: String) = withContext(ioDispatcher) {
        savedLocationDao.deleteLocationById(id)
    }
}
