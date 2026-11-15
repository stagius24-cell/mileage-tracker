package com.businesstracker.mileage.data.repository

import com.businesstracker.mileage.data.local.RoutePatternDao
import com.businesstracker.mileage.data.local.TripLearningRecordDao
import com.businesstracker.mileage.data.local.entity.RoutePatternEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.entity.TripLearningRecordEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.ClassificationSource
import com.businesstracker.mileage.domain.model.SmartTripLearningCutoff
import com.businesstracker.mileage.domain.repository.SmartTripRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class SmartTripRepositoryImpl @Inject constructor(
    private val routePatternDao: RoutePatternDao,
    private val tripLearningRecordDao: TripLearningRecordDao,
    private val preferencesManager: TrackingPreferencesManager,
    private val ioDispatcher: CoroutineDispatcher
) : SmartTripRepository {

    companion object {
        const val DEFAULT_CLUSTER_RADIUS_METERS: Double = 250.0
        const val MIN_TRIP_DISPLACEMENT_METERS: Double = 200.0
    }

    override fun observeAllPatterns(): Flow<List<RoutePatternEntity>> =
        routePatternDao.getAllPatternsFlow().flowOn(ioDispatcher)

    override suspend fun getAllPatterns(): List<RoutePatternEntity> = withContext(ioDispatcher) {
        routePatternDao.getAllPatterns()
    }

    override suspend fun getLearningRecord(tripId: String): TripLearningRecordEntity? = withContext(ioDispatcher) {
        tripLearningRecordDao.getByTripId(tripId)
    }

    override suspend fun getPatternById(patternId: String): RoutePatternEntity? = withContext(ioDispatcher) {
        routePatternDao.getPatternById(patternId)
    }

    override suspend fun isTripEligibleForLearning(
        trip: TripEntity,
        source: ClassificationSource
    ): Boolean = withContext(ioDispatcher) {
        checkEligibilityInternal(trip, source)
    }

    private suspend fun checkEligibilityInternal(
        trip: TripEntity,
        source: ClassificationSource
    ): Boolean {
        // 1. Activation cutoff check: trip completion time (endTime) must be >= November 15, 2026, 12:00 AM America/Los_Angeles
        if (!SmartTripLearningCutoff.isEligibleByDate(trip.endTime)) {
            return false
        }

        // 2. Feature enabled check
        val isEnabled = preferencesManager.isSmartTripRecognitionEnabled.first()
        if (!isEnabled) {
            return false
        }

        // 3. Provenance check: only explicit user actions (MANUAL or SUGGESTED_CONFIRMED)
        if (source != ClassificationSource.MANUAL && source != ClassificationSource.SUGGESTED_CONFIRMED) {
            return false
        }

        // 4. Category check: must be Business or Personal, never unreviewed
        val classificationUpper = trip.classification.uppercase()
        if (classificationUpper != "BUSINESS" && classificationUpper != "PERSONAL") {
            return false
        }

        // 5. Valid GPS coordinates check
        if (trip.startLatitude == 0.0 && trip.startLongitude == 0.0) return false
        if (trip.endLatitude == 0.0 && trip.endLongitude == 0.0) return false

        // 6. Minimum trip displacement check (exclude false starts / stationary jitter)
        val displacement = calculateDistanceMeters(
            trip.startLatitude, trip.startLongitude,
            trip.endLatitude, trip.endLongitude
        )
        if (displacement < MIN_TRIP_DISPLACEMENT_METERS) {
            return false
        }

        return true
    }

    override suspend fun reconcileTripLearning(
        trip: TripEntity,
        source: ClassificationSource
    ): TripLearningRecordEntity? = withContext(ioDispatcher) {
        val isEligible = checkEligibilityInternal(trip, source)
        val existingRecord = tripLearningRecordDao.getByTripId(trip.id)

        if (!isEligible) {
            // If previously learned but now ineligible, safely remove previous evidence
            if (existingRecord != null && existingRecord.patternId != null) {
                decrementPatternCount(existingRecord.patternId)
                tripLearningRecordDao.deleteRecordByTripId(trip.id)
            }
            return@withContext null
        }

        val currentTime = System.currentTimeMillis()

        if (existingRecord != null) {
            // Subcase 1: Exactly same classification and purpose (idempotent - do not duplicate counts)
            if (existingRecord.confirmedCategory == trip.classification &&
                existingRecord.businessEntity == trip.businessEntity &&
                existingRecord.purpose == trip.purpose
            ) {
                return@withContext existingRecord
            }

            // Subcase 2: Manual correction (e.g. BUSINESS -> PERSONAL or changed purpose)
            // Safely decrement evidence from the previous route pattern
            if (existingRecord.patternId != null) {
                decrementPatternCount(existingRecord.patternId)
            }

            // Learn new pattern for the updated classification
            val newPattern = findOrCreatePatternForTrip(trip)
            val updatedRecord = existingRecord.copy(
                eligible = true,
                classificationSource = source.value,
                confirmedCategory = trip.classification,
                businessEntity = trip.businessEntity,
                purpose = trip.purpose,
                startLatitude = trip.startLatitude,
                startLongitude = trip.startLongitude,
                endLatitude = trip.endLatitude,
                endLongitude = trip.endLongitude,
                patternId = newPattern.id,
                learnedAt = currentTime
            )
            tripLearningRecordDao.updateRecord(updatedRecord)
            return@withContext updatedRecord
        } else {
            // Subcase 3: First-time learning for this trip
            val pattern = findOrCreatePatternForTrip(trip)
            val newRecord = TripLearningRecordEntity(
                tripId = trip.id,
                eligible = true,
                classificationSource = source.value,
                confirmedCategory = trip.classification,
                businessEntity = trip.businessEntity,
                purpose = trip.purpose,
                startLatitude = trip.startLatitude,
                startLongitude = trip.startLongitude,
                endLatitude = trip.endLatitude,
                endLongitude = trip.endLongitude,
                patternId = pattern.id,
                learnedAt = currentTime,
                createdAt = currentTime
            )
            tripLearningRecordDao.insertRecord(newRecord)
            return@withContext newRecord
        }
    }

    override suspend fun removeTripLearning(tripId: String) = withContext(ioDispatcher) {
        val existing = tripLearningRecordDao.getByTripId(tripId)
        if (existing != null) {
            if (existing.patternId != null) {
                decrementPatternCount(existing.patternId)
            }
            tripLearningRecordDao.deleteRecordByTripId(tripId)
        }
    }

    private suspend fun decrementPatternCount(patternId: String) {
        val pattern = routePatternDao.getPatternById(patternId) ?: return
        if (pattern.sampleCount <= 1) {
            routePatternDao.deleteRoutePatternById(patternId)
        } else {
            val updatedCount = pattern.sampleCount - 1
            val updatedConfidence = calculateConfidence(updatedCount)
            routePatternDao.updateRoutePattern(
                pattern.copy(
                    sampleCount = updatedCount,
                    confidence = updatedConfidence,
                    lastLearnedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Stable Clustering Implementation:
     * - Fixes cluster coordinates to the anchor trip without running coordinate averaging drift.
     * - Matches existing patterns within radius and with identical classification and business entity.
     * - Increments sample count and confidence without shifting geographical center points.
     */
    private suspend fun findOrCreatePatternForTrip(trip: TripEntity): RoutePatternEntity {
        val allPatterns = routePatternDao.getAllPatterns()
        val candidates = allPatterns.filter { pattern ->
            pattern.classification.equals(trip.classification, ignoreCase = true) &&
            pattern.businessEntity.equals(trip.businessEntity, ignoreCase = true)
        }

        // Find candidate patterns where both start and end fall within radius
        val matches = candidates.mapNotNull { pattern ->
            val startDist = calculateDistanceMeters(
                trip.startLatitude, trip.startLongitude,
                pattern.startLatitude, pattern.startLongitude
            )
            val endDist = calculateDistanceMeters(
                trip.endLatitude, trip.endLongitude,
                pattern.endLatitude, pattern.endLongitude
            )
            if (startDist <= pattern.startRadiusMeters && endDist <= pattern.endRadiusMeters) {
                Pair(pattern, startDist + endDist)
            } else {
                null
            }
        }.sortedBy { it.second }

        if (matches.isNotEmpty()) {
            val bestPattern = matches.first().first
            val newCount = bestPattern.sampleCount + 1
            val updatedPattern = bestPattern.copy(
                sampleCount = newCount,
                confidence = calculateConfidence(newCount),
                lastLearnedAt = System.currentTimeMillis()
                // CRITICAL: Anchor coordinates (startLatitude/startLongitude/endLatitude/endLongitude)
                // are preserved exactly to guarantee zero geographic pattern drift!
            )
            routePatternDao.updateRoutePattern(updatedPattern)
            return updatedPattern
        }

        // No matching pattern: create new stable cluster anchored at current trip's endpoints
        val now = System.currentTimeMillis()
        val newPattern = RoutePatternEntity(
            id = UUID.randomUUID().toString(),
            startLatitude = trip.startLatitude,
            startLongitude = trip.startLongitude,
            startRadiusMeters = DEFAULT_CLUSTER_RADIUS_METERS,
            endLatitude = trip.endLatitude,
            endLongitude = trip.endLongitude,
            endRadiusMeters = DEFAULT_CLUSTER_RADIUS_METERS,
            startAddress = trip.startAddress,
            endAddress = trip.endAddress,
            classification = trip.classification,
            businessEntity = trip.businessEntity,
            purpose = trip.purpose,
            sampleCount = 1,
            confidence = calculateConfidence(1),
            lastLearnedAt = now,
            createdAt = now
        )
        routePatternDao.insertRoutePattern(newPattern)
        return newPattern
    }

    override suspend fun findMatchingPattern(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): RoutePatternEntity? = withContext(ioDispatcher) {
        val allPatterns = routePatternDao.getAllPatterns()
        val matches = allPatterns.mapNotNull { pattern ->
            val startDist = calculateDistanceMeters(startLat, startLon, pattern.startLatitude, pattern.startLongitude)
            val endDist = calculateDistanceMeters(endLat, endLon, pattern.endLatitude, pattern.endLongitude)
            if (startDist <= pattern.startRadiusMeters && endDist <= pattern.endRadiusMeters) {
                Pair(pattern, startDist + endDist)
            } else {
                null
            }
        }.sortedBy { it.second }

        // If multiple conflicting patterns match closely (ambiguous), do not return an ambiguous single match
        if (matches.size > 1) {
            val first = matches[0]
            val second = matches[1]
            if (first.first.classification != second.first.classification) {
                // Conflicting classification within matching radius -> ambiguous!
                return@withContext null
            }
        }

        matches.firstOrNull()?.first
    }

    private fun calculateConfidence(sampleCount: Int): Double {
        // Formula: 1 sample = 0.60, 2 samples = 0.70, 3 samples = 0.80, 5+ samples = 1.00
        return min(1.0, max(0.5, 0.5 + 0.1 * sampleCount))
    }

    private fun calculateDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
