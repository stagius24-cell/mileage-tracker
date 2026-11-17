package com.businesstracker.mileage.data.repository

import androidx.room.withTransaction
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.TripDao
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.model.BusinessEntitySummary
import com.businesstracker.mileage.data.local.model.ClassificationCount
import com.businesstracker.mileage.domain.model.ClassificationSource
import com.businesstracker.mileage.domain.repository.SmartTripRepository
import com.businesstracker.mileage.domain.repository.TripRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepositoryImpl @Inject constructor(
    private val tripDao: TripDao,
    private val database: ExpenseTrackerDatabase?,
    private val smartTripRepository: SmartTripRepository?,
    private val ioDispatcher: CoroutineDispatcher
) : TripRepository {

    constructor(
        tripDao: TripDao,
        ioDispatcher: CoroutineDispatcher
    ) : this(
        tripDao = tripDao,
        database = null,
        smartTripRepository = null,
        ioDispatcher = ioDispatcher
    )

    override fun getAllTrips(): Flow<List<TripEntity>> =
        tripDao.getAllTrips().flowOn(ioDispatcher)

    override fun getUnclassifiedTrips(): Flow<List<TripEntity>> =
        tripDao.getUnclassifiedTrips().flowOn(ioDispatcher)

    override fun getUnclassifiedTripsCount(): Flow<Int> =
        tripDao.getUnclassifiedTripsCount().flowOn(ioDispatcher)

    override fun getTripsByDateRange(startDate: String, endDate: String): Flow<List<TripEntity>> =
        tripDao.getTripsByDateRange(startDate, endDate).flowOn(ioDispatcher)

    override fun getTripsByBusinessEntity(entity: String): Flow<List<TripEntity>> =
        tripDao.getTripsByBusinessEntity(entity).flowOn(ioDispatcher)

    override fun getBusinessEntitySummaries(): Flow<List<BusinessEntitySummary>> =
        tripDao.getBusinessEntitySummaries().flowOn(ioDispatcher)

    override fun getBusinessEntitySummariesForDateRange(startDate: String, endDate: String): Flow<List<BusinessEntitySummary>> =
        tripDao.getBusinessEntitySummariesForDateRange(startDate, endDate).flowOn(ioDispatcher)

    override fun getTripCountByClassification(): Flow<List<ClassificationCount>> =
        tripDao.getTripCountByClassification().flowOn(ioDispatcher)

    override suspend fun getTripById(tripId: String): TripEntity? = withContext(ioDispatcher) {
        tripDao.getTripById(tripId)
    }

    override fun observeTripById(tripId: String): Flow<TripEntity?> =
        tripDao.observeTripById(tripId).flowOn(ioDispatcher)

    override suspend fun insertTrip(trip: TripEntity) = withContext(ioDispatcher) {
        tripDao.insertTrip(trip)
    }

    override suspend fun updateTrip(trip: TripEntity) = withContext(ioDispatcher) {
        tripDao.updateTrip(trip)
    }

    override suspend fun updateVehicleId(tripId: String, vehicleId: String?) {
        withContext(ioDispatcher) {
        tripDao.updateVehicleId(tripId, vehicleId)
        }
    }

    override suspend fun deleteTrip(tripId: String) = withContext(ioDispatcher) {
        if (database != null) {
            database.withTransaction {
                smartTripRepository?.removeTripLearning(tripId)
                tripDao.deleteTripById(tripId)
            }
        } else {
            smartTripRepository?.removeTripLearning(tripId)
            tripDao.deleteTripById(tripId)
        }
    }

    override suspend fun classifyTrip(
        tripId: String,
        classification: String,
        businessEntity: String,
        purpose: String,
        notes: String?
    ) {
        classifyTrip(tripId, classification, businessEntity, purpose, notes, ClassificationSource.MANUAL)
    }

    override suspend fun classifyTrip(
        tripId: String,
        classification: String,
        businessEntity: String,
        purpose: String,
        notes: String?,
        source: ClassificationSource
    ) = withContext(ioDispatcher) {
        if (database != null) {
            database.withTransaction {
                tripDao.classifyTrip(tripId, classification, businessEntity, purpose, notes, source.value)
                val updatedTrip = tripDao.getTripById(tripId)
                if (updatedTrip != null && smartTripRepository != null) {
                    smartTripRepository.reconcileTripLearning(updatedTrip, source)
                }
            }
        } else {
            tripDao.classifyTrip(tripId, classification, businessEntity, purpose, notes, source.value)
            val updatedTrip = tripDao.getTripById(tripId)
            if (updatedTrip != null && smartTripRepository != null) {
                smartTripRepository.reconcileTripLearning(updatedTrip, source)
            }
        }
    }

    override suspend fun updateTripDetails(
        tripId: String,
        classification: String,
        businessEntity: String,
        purpose: String,
        notes: String?
    ) {
        updateTripDetails(tripId, classification, businessEntity, purpose, notes, ClassificationSource.MANUAL)
    }

    override suspend fun updateTripDetails(
        tripId: String,
        classification: String,
        businessEntity: String,
        purpose: String,
        notes: String?,
        source: ClassificationSource
    ) = withContext(ioDispatcher) {
        if (database != null) {
            database.withTransaction {
                tripDao.updateTripDetails(tripId, classification, businessEntity, purpose, notes, source.value)
                val updatedTrip = tripDao.getTripById(tripId)
                if (updatedTrip != null && smartTripRepository != null) {
                    smartTripRepository.reconcileTripLearning(updatedTrip, source)
                }
            }
        } else {
            tripDao.updateTripDetails(tripId, classification, businessEntity, purpose, notes, source.value)
            val updatedTrip = tripDao.getTripById(tripId)
            if (updatedTrip != null && smartTripRepository != null) {
                smartTripRepository.reconcileTripLearning(updatedTrip, source)
            }
        }
    }

    override fun getTripsBySyncStatus(syncStatus: String): Flow<List<TripEntity>> =
        tripDao.getTripsBySyncStatus(syncStatus).flowOn(ioDispatcher)

    override fun getTripsByClassification(classification: String): Flow<List<TripEntity>> =
        tripDao.getTripsByClassification(classification).flowOn(ioDispatcher)

    override suspend fun getPendingSyncTrips(): List<TripEntity> = withContext(ioDispatcher) {
        tripDao.getPendingSyncTrips()
    }

    override suspend fun markSynced(tripId: String, timestamp: Long) = withContext(ioDispatcher) {
        tripDao.updateSyncStatus(tripId, "SYNCED", timestamp)
    }

    override suspend fun markAllClassifiedTripsPending() = withContext(ioDispatcher) {
        tripDao.markAllClassifiedTripsPending()
    }

    override suspend fun setPendingSuggestion(
        tripId: String,
        suggestedClassification: String
    ): Boolean = withContext(ioDispatcher) {
        tripDao.setPendingSuggestion(
            tripId = tripId,
            suggestedClassification = suggestedClassification,
            source = ClassificationSource.SMART_SUGGESTED.value
        ) > 0
    }

    override suspend fun dismissSuggestion(tripId: String): Boolean = withContext(ioDispatcher) {
        tripDao.dismissSuggestion(tripId) > 0
    }

    override suspend fun autoClassifyTrip(
        tripId: String,
        classification: String,
        businessEntity: String,
        purpose: String,
        notes: String?
    ): Boolean = withContext(ioDispatcher) {
        val rows = if (database != null) {
            database.withTransaction {
                tripDao.autoClassifyTrip(tripId, classification, businessEntity, purpose, notes)
            }
        } else {
            tripDao.autoClassifyTrip(tripId, classification, businessEntity, purpose, notes)
        }
        rows > 0
    }
}
