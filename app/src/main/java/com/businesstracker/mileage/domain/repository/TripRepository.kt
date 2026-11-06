package com.businesstracker.mileage.domain.repository

import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.model.BusinessEntitySummary
import com.businesstracker.mileage.data.local.model.ClassificationCount
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun getAllTrips(): Flow<List<TripEntity>>
    fun getUnclassifiedTrips(): Flow<List<TripEntity>>
    fun getUnclassifiedTripsCount(): Flow<Int>
    fun getTripsByDateRange(startDate: String, endDate: String): Flow<List<TripEntity>>
    fun getTripsByBusinessEntity(entity: String): Flow<List<TripEntity>>
    fun getBusinessEntitySummaries(): Flow<List<BusinessEntitySummary>>
    fun getBusinessEntitySummariesForDateRange(startDate: String, endDate: String): Flow<List<BusinessEntitySummary>>
    fun getTripCountByClassification(): Flow<List<ClassificationCount>>
    suspend fun getTripById(tripId: String): TripEntity?
    fun observeTripById(tripId: String): Flow<TripEntity?>
    suspend fun insertTrip(trip: TripEntity)
    suspend fun updateTrip(trip: TripEntity)
    suspend fun deleteTrip(tripId: String)
    suspend fun classifyTrip(
        tripId: String,
        classification: String,
        businessEntity: String,
        purpose: String,
        notes: String?
    )
    suspend fun getPendingSyncTrips(): List<TripEntity>
    suspend fun markSynced(tripId: String, timestamp: Long)
}
