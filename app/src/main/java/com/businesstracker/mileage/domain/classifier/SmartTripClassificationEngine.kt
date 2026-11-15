package com.businesstracker.mileage.domain.classifier

import com.businesstracker.mileage.data.local.TripLearningRecordDao
import com.businesstracker.mileage.data.local.entity.RoutePatternEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.entity.TripLearningRecordEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.matcher.RouteMatchResult
import com.businesstracker.mileage.domain.matcher.TripPatternMatcher
import com.businesstracker.mileage.domain.model.ClassificationSource
import com.businesstracker.mileage.domain.model.SmartClassificationMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart Trip Classification Decision Engine.
 *
 * Evaluates completed trips against historical learning evidence to determine whether
 * a trip should receive:
 * - No suggestion
 * - A Business or Personal suggestion
 * - An automatic classification (when AUTO_CLASSIFY mode is active)
 * - An ambiguity warning requiring manual review
 *
 * Implements strict rule precedence:
 * 1. Explicit manual classification
 * 2. Schedule rules and exclusions
 * 3. Saved-location preferences
 * 4. Smart Trip Recognition
 * 5. Default UNREVIEWED
 */
@Singleton
class SmartTripClassificationEngine @Inject constructor(
    private val matcher: TripPatternMatcher,
    private val tripLearningRecordDao: TripLearningRecordDao,
    private val preferencesManager: TrackingPreferencesManager,
    private val ioDispatcher: CoroutineDispatcher
) {

    companion object {
        const val MIN_SAMPLES_FOR_SUGGESTION = 3
        const val MIN_SAMPLES_FOR_HIGH_CONFIDENCE = 5
        const val HIGH_CONFIDENCE_AGREEMENT_THRESHOLD = 0.90 // >= 90%
        const val MEDIUM_CONFIDENCE_AGREEMENT_THRESHOLD = 0.80 // >= 80%
        const val BUSINESS_PURPOSE_CONSISTENCY_THRESHOLD = 0.90 // >= 90%
    }

    /**
     * Evaluates a completed trip using currently stored preferences.
     */
    suspend fun evaluateTrip(trip: TripEntity): ClassificationDecision = withContext(ioDispatcher) {
        val isEnabled = preferencesManager.isSmartTripRecognitionEnabled.first()
        val mode = preferencesManager.smartClassificationMode.first()
        evaluateTripInternal(trip, isEnabled, mode)
    }

    /**
     * Evaluates a completed trip overriding the classification mode.
     */
    suspend fun evaluateTripWithMode(
        trip: TripEntity,
        mode: SmartClassificationMode
    ): ClassificationDecision = withContext(ioDispatcher) {
        val isEnabled = preferencesManager.isSmartTripRecognitionEnabled.first()
        evaluateTripInternal(trip, isEnabled, mode)
    }

    private suspend fun evaluateTripInternal(
        trip: TripEntity,
        isEnabled: Boolean,
        mode: SmartClassificationMode
    ): ClassificationDecision {
        // Step 1: Feature toggle check
        if (!isEnabled) {
            return ClassificationDecision.NoSuggestion("Smart Trip Recognition is disabled.")
        }

        // Step 2: Precedence 1 — Explicit manual classification
        if (trip.classificationSource == ClassificationSource.MANUAL.value ||
            (trip.classification != "UNREVIEWED" &&
             trip.classificationSource != ClassificationSource.SMART_AUTO.value &&
             trip.classificationSource != ClassificationSource.SMART_SUGGESTED.value &&
             trip.classificationSource != ClassificationSource.SCHEDULE.value)
        ) {
            return ClassificationDecision.NoSuggestion(
                reason = "Trip has already been explicitly manually classified as ${trip.classification}."
            )
        }

        // Step 3: Precedence 2 — Existing Schedule rules
        if (trip.classificationSource == ClassificationSource.SCHEDULE.value) {
            return ClassificationDecision.NoSuggestion(
                reason = "Existing Work Schedule rule takes precedence over Smart Trip Recognition."
            )
        }

        // Step 4: Geographic route pattern matching via TripPatternMatcher
        val matchResult = matcher.matchTrip(trip)
        return when (matchResult) {
            is RouteMatchResult.NoMatch -> {
                ClassificationDecision.NoSuggestion(
                    reason = "No matching route pattern: ${matchResult.reason}"
                )
            }
            is RouteMatchResult.InvalidCoordinates -> {
                ClassificationDecision.NoSuggestion(
                    reason = "Invalid coordinates: ${matchResult.reason}"
                )
            }
            is RouteMatchResult.AmbiguousMatch -> {
                ClassificationDecision.AmbiguousClassification(
                    candidatePatternIds = matchResult.candidatePatterns.map { it.id },
                    reason = "Ambiguous route pattern match: ${matchResult.reason}"
                )
            }
            is RouteMatchResult.ReliableMatch -> {
                // Step 5: Query historical learning evidence for this pattern
                val records = tripLearningRecordDao.getRecordsByPatternId(matchResult.pattern.id)
                evaluateEvidence(
                    matchingPattern = matchResult.pattern,
                    learningRecords = records,
                    mode = mode
                )
            }
        }
    }

    /**
     * Pure, deterministic evidence evaluation logic.
     * Evaluates confirmed matching historical trips against threshold rules.
     */
    fun evaluateEvidence(
        matchingPattern: RoutePatternEntity,
        learningRecords: List<TripLearningRecordEntity>,
        mode: SmartClassificationMode
    ): ClassificationDecision {
        // Consider only explicitly confirmed, eligible historical learning records
        val confirmedTrips = learningRecords.filter { record ->
            record.eligible && (
                record.classificationSource == ClassificationSource.MANUAL.value ||
                record.classificationSource == ClassificationSource.SUGGESTED_CONFIRMED.value ||
                record.classificationSource == "USER_CONFIRMED"
            )
        }

        val totalConfirmed = confirmedTrips.size
        val businessCount = confirmedTrips.count { it.confirmedCategory.equals("BUSINESS", ignoreCase = true) }
        val personalCount = confirmedTrips.count { it.confirmedCategory.equals("PERSONAL", ignoreCase = true) }

        // Threshold Rule 1: 0–2 confirmed matching trips -> No suggestion
        if (totalConfirmed < MIN_SAMPLES_FOR_SUGGESTION) {
            return ClassificationDecision.NoSuggestion(
                reason = "Insufficient historical evidence ($totalConfirmed confirmed matching trips; minimum 3 required).",
                matchingPatternId = matchingPattern.id,
                confirmedTripCount = totalConfirmed,
                businessCount = businessCount,
                personalCount = personalCount,
                agreementRatio = 0.0
            )
        }

        val majorityCategory = if (businessCount >= personalCount) "BUSINESS" else "PERSONAL"
        val majorityCount = maxOf(businessCount, personalCount)
        val agreementRatio = majorityCount.toDouble() / totalConfirmed

        // Threshold Rule 2: 3–4 confirmed matching trips -> Suggest only when ALL agree (100%)
        if (totalConfirmed < MIN_SAMPLES_FOR_HIGH_CONFIDENCE) {
            return if (majorityCount == totalConfirmed) {
                // 100% agreement on 3 or 4 trips: Suggest only (never auto-classify, requires confirmation)
                val (suggestedEntity, suggestedPurpose) = resolveEntityAndPurpose(confirmedTrips, majorityCategory)
                ClassificationDecision.SuggestedClassification(
                    suggestedCategory = majorityCategory,
                    suggestedBusinessEntity = suggestedEntity,
                    suggestedPurpose = suggestedPurpose,
                    matchingPatternId = matchingPattern.id,
                    confirmedTripCount = totalConfirmed,
                    businessCount = businessCount,
                    personalCount = personalCount,
                    agreementRatio = 1.0,
                    reason = "$totalConfirmed of $totalConfirmed matching trips consistently classified as $majorityCategory (100% agreement).",
                    requiresExplicitConfirmation = true
                )
            } else {
                // Disagreement in small sample size (< 5) -> No suggestion
                ClassificationDecision.NoSuggestion(
                    reason = "Conflicting classifications among small sample ($businessCount Business, $personalCount Personal; 100% agreement required for < 5 trips).",
                    matchingPatternId = matchingPattern.id,
                    confirmedTripCount = totalConfirmed,
                    businessCount = businessCount,
                    personalCount = personalCount,
                    agreementRatio = agreementRatio
                )
            }
        }

        // Threshold Rule 3: 5+ confirmed matching trips
        val (suggestedEntity, suggestedPurpose) = resolveEntityAndPurpose(confirmedTrips, majorityCategory)

        return when {
            // High confidence: 5+ with >= 90% agreement
            agreementRatio >= HIGH_CONFIDENCE_AGREEMENT_THRESHOLD -> {
                val percentage = (agreementRatio * 100).toInt()
                if (mode == SmartClassificationMode.AUTO_CLASSIFY) {
                    ClassificationDecision.AutomaticClassification(
                        suggestedCategory = majorityCategory,
                        suggestedBusinessEntity = suggestedEntity,
                        suggestedPurpose = suggestedPurpose,
                        matchingPatternId = matchingPattern.id,
                        confirmedTripCount = totalConfirmed,
                        businessCount = businessCount,
                        personalCount = personalCount,
                        agreementRatio = agreementRatio,
                        reason = "High-confidence route pattern ($majorityCount of $totalConfirmed trips, $percentage% agreement)."
                    )
                } else {
                    ClassificationDecision.SuggestedClassification(
                        suggestedCategory = majorityCategory,
                        suggestedBusinessEntity = suggestedEntity,
                        suggestedPurpose = suggestedPurpose,
                        matchingPatternId = matchingPattern.id,
                        confirmedTripCount = totalConfirmed,
                        businessCount = businessCount,
                        personalCount = personalCount,
                        agreementRatio = agreementRatio,
                        reason = "High-confidence route pattern ($majorityCount of $totalConfirmed trips, $percentage% agreement). Mode requires explicit confirmation.",
                        requiresExplicitConfirmation = true
                    )
                }
            }

            // Medium confidence: 5+ with 80%..89% agreement -> Suggest only (requires confirmation in all modes)
            agreementRatio >= MEDIUM_CONFIDENCE_AGREEMENT_THRESHOLD -> {
                val percentage = (agreementRatio * 100).toInt()
                ClassificationDecision.SuggestedClassification(
                    suggestedCategory = majorityCategory,
                    suggestedBusinessEntity = suggestedEntity,
                    suggestedPurpose = suggestedPurpose,
                    matchingPatternId = matchingPattern.id,
                    confirmedTripCount = totalConfirmed,
                    businessCount = businessCount,
                    personalCount = personalCount,
                    agreementRatio = agreementRatio,
                    reason = "Consistent route pattern ($majorityCount of $totalConfirmed trips, $percentage% agreement; meets >=80% suggestion threshold).",
                    requiresExplicitConfirmation = true
                )
            }

            // Low consistency: 5+ with < 80% agreement -> No suggestion
            else -> {
                val percentage = (agreementRatio * 100).toInt()
                ClassificationDecision.NoSuggestion(
                    reason = "Insufficient consistency ($majorityCount of $totalConfirmed trips, $percentage% agreement; minimum 80% required for 5+ trips).",
                    matchingPatternId = matchingPattern.id,
                    confirmedTripCount = totalConfirmed,
                    businessCount = businessCount,
                    personalCount = personalCount,
                    agreementRatio = agreementRatio
                )
            }
        }
    }

    /**
     * Resolves Business Entity and Purpose independently from Category.
     * Only suggests entity or purpose if there is >= 90% consistent agreement among confirmed business trips.
     */
    private fun resolveEntityAndPurpose(
        confirmedTrips: List<TripLearningRecordEntity>,
        majorityCategory: String
    ): Pair<String?, String?> {
        if (!majorityCategory.equals("BUSINESS", ignoreCase = true)) {
            return Pair(null, null)
        }

        val businessTrips = confirmedTrips.filter {
            it.confirmedCategory.equals("BUSINESS", ignoreCase = true)
        }
        if (businessTrips.isEmpty()) {
            return Pair(null, null)
        }

        // Resolve Business Entity: requires >= 90% agreement
        val entityCounts = businessTrips.mapNotNull { it.businessEntity?.trim()?.takeIf { s -> s.isNotEmpty() } }
            .groupingBy { it }
            .eachCount()
        val topEntityEntry = entityCounts.maxByOrNull { it.value }
        val resolvedEntity = if (topEntityEntry != null &&
            (topEntityEntry.value.toDouble() / businessTrips.size) >= BUSINESS_PURPOSE_CONSISTENCY_THRESHOLD
        ) {
            topEntityEntry.key
        } else {
            null
        }

        // Resolve Purpose: requires >= 90% agreement
        val purposeCounts = businessTrips.mapNotNull { it.purpose?.trim()?.takeIf { s -> s.isNotEmpty() } }
            .groupingBy { it }
            .eachCount()
        val topPurposeEntry = purposeCounts.maxByOrNull { it.value }
        val resolvedPurpose = if (topPurposeEntry != null &&
            (topPurposeEntry.value.toDouble() / businessTrips.size) >= BUSINESS_PURPOSE_CONSISTENCY_THRESHOLD
        ) {
            topPurposeEntry.key
        } else {
            null
        }

        return Pair(resolvedEntity, resolvedPurpose)
    }
}
