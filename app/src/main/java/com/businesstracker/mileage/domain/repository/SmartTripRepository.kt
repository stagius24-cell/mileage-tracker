package com.businesstracker.mileage.domain.repository

import com.businesstracker.mileage.data.local.entity.RoutePatternEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.entity.TripLearningRecordEntity
import com.businesstracker.mileage.domain.model.ClassificationSource
import kotlinx.coroutines.flow.Flow

interface SmartTripRepository {

    suspend fun reconcileTripLearning(
        trip: TripEntity,
        source: ClassificationSource
    ): TripLearningRecordEntity?

    suspend fun removeTripLearning(tripId: String)

    suspend fun isTripEligibleForLearning(
        trip: TripEntity,
        source: ClassificationSource
    ): Boolean

    fun observeAllPatterns(): Flow<List<RoutePatternEntity>>

    suspend fun getAllPatterns(): List<RoutePatternEntity>

    suspend fun getLearningRecord(tripId: String): TripLearningRecordEntity?

    suspend fun getPatternById(patternId: String): RoutePatternEntity?

    suspend fun findMatchingPattern(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): RoutePatternEntity?
}
