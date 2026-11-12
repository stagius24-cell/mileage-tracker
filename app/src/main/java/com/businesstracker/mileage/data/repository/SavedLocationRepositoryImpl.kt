package com.businesstracker.mileage.data.repository

import androidx.room.withTransaction
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.SavedLocationDao
import com.businesstracker.mileage.data.local.SavedLocationSyncQueueDao
import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import com.businesstracker.mileage.data.local.entity.SavedLocationSyncQueueEntity
import com.businesstracker.mileage.domain.repository.SavedLocationBackupScheduler
import com.businesstracker.mileage.domain.repository.SavedLocationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedLocationRepositoryImpl(
    private val savedLocationDao: SavedLocationDao,
    private val ioDispatcher: CoroutineDispatcher
) : SavedLocationRepository {

    private var database: ExpenseTrackerDatabase? = null
    private var syncQueueDao: SavedLocationSyncQueueDao? = null
    private var backupScheduler: SavedLocationBackupScheduler? = null

    @Inject
    constructor(
        savedLocationDao: SavedLocationDao,
        ioDispatcher: CoroutineDispatcher,
        database: ExpenseTrackerDatabase,
        syncQueueDao: SavedLocationSyncQueueDao,
        backupScheduler: SavedLocationBackupScheduler
    ) : this(savedLocationDao, ioDispatcher) {
        this.database = database
        this.syncQueueDao = syncQueueDao
        this.backupScheduler = backupScheduler
    }

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
        runInTransactionIfAvailable {
            val existing = savedLocationDao.getLocationById(location.id)
            if (existing == null) {
                savedLocationDao.insertLocation(location)
            } else {
                savedLocationDao.updateLocation(location)
            }
            syncQueueDao?.upsertPending(
                SavedLocationSyncQueueEntity(
                    locationId = location.id,
                    operation = SavedLocationSyncQueueEntity.OP_UPSERT,
                    updatedAt = location.updatedAt
                )
            )
        }
        backupScheduler?.scheduleAutoBackup()
        Unit
    }

    override suspend fun deleteLocation(id: String) = withContext(ioDispatcher) {
        runInTransactionIfAvailable {
            savedLocationDao.deleteLocationById(id)
            syncQueueDao?.upsertPending(
                SavedLocationSyncQueueEntity(
                    locationId = id,
                    operation = SavedLocationSyncQueueEntity.OP_DELETE,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        backupScheduler?.scheduleAutoBackup()
        Unit
    }

    override suspend fun applyRestoredLocations(
        locationsToSave: List<SavedLocationEntity>,
        pendingDeleteIdsToClear: Collection<String>
    ) = withContext(ioDispatcher) {
        if (locationsToSave.isEmpty() && pendingDeleteIdsToClear.isEmpty()) {
            return@withContext
        }
        runInTransactionIfAvailable {
            for (location in locationsToSave) {
                val existing = savedLocationDao.getLocationById(location.id)
                if (existing == null) {
                    savedLocationDao.insertLocation(location)
                } else {
                    savedLocationDao.updateLocation(location)
                }
                syncQueueDao?.deletePendingById(location.id)
            }
            if (pendingDeleteIdsToClear.isNotEmpty()) {
                syncQueueDao?.deletePendingByIds(pendingDeleteIdsToClear.toList())
            }
        }
    }

    override suspend fun getPendingSyncItems(): List<SavedLocationSyncQueueEntity> = withContext(ioDispatcher) {
        syncQueueDao?.getAllPending() ?: emptyList()
    }

    override fun getPendingSyncCountFlow(): Flow<Int> {
        return syncQueueDao?.getPendingCountFlow() ?: flowOf(0)
    }

    private suspend fun <T> runInTransactionIfAvailable(block: suspend () -> T): T {
        val db = database
        return if (db != null) {
            db.withTransaction { block() }
        } else {
            block()
        }
    }
}
