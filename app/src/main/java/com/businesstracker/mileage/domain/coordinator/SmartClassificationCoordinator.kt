package com.businesstracker.mileage.domain.coordinator

import android.content.Context
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.classifier.ClassificationDecision
import com.businesstracker.mileage.domain.classifier.SmartTripClassificationEngine
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.model.ClassificationSource
import com.businesstracker.mileage.domain.model.SmartClassificationMode
import com.businesstracker.mileage.domain.model.SmartTripLearningCutoff
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.model.TripPurpose
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.service.NotificationHelper
import com.businesstracker.mileage.worker.GoogleSheetsSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Structured decision outcome for Smart Trip Recognition.
 */
sealed interface SmartClassificationResult {
    data class AutoClassified(
        val tripId: String,
        val category: String,
        val confidence: Double,
        val matchingTripCount: Int,
        val businessEntity: String?,
        val purpose: String?
    ) : SmartClassificationResult

    data class Suggested(
        val tripId: String,
        val category: String,
        val confidence: Double,
        val matchingTripCount: Int,
        val businessEntity: String?,
        val purpose: String?,
        val notificationShown: Boolean
    ) : SmartClassificationResult

    data class NoSuggestion(
        val tripId: String,
        val reason: String
    ) : SmartClassificationResult

    data class Ignored(
        val tripId: String,
        val reason: String
    ) : SmartClassificationResult
}

/**
 * Centralized coordinator for Smart Trip Recognition lifecycle.
 *
 * Responsibilities:
 * 1. Receiving the committed trip ID.
 * 2. Loading the completed trip from Room.
 * 3. Checking feature settings and cutoff eligibility.
 * 4. Finding route pattern matches via SmartTripClassificationEngine.
 * 5. Evaluating classification confidence.
 * 6. Applying the selected classification mode (AUTO_CLASSIFY, ALWAYS_ASK, SUGGEST_ONLY).
 * 7. Persisting the decision safely without blocking transactions.
 * 8. Triggering appropriate notifications and Google Sheets synchronization.
 */
@Singleton
class SmartClassificationCoordinator @Inject constructor(
    private val tripRepository: TripRepository,
    private val classificationEngine: SmartTripClassificationEngine,
    private val preferencesManager: TrackingPreferencesManager,
    private val notificationHelper: NotificationHelper,
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun processFinalizedTrip(
        tripId: String,
        modeOverride: SmartClassificationMode? = null
    ): SmartClassificationResult = withContext(ioDispatcher) {
        if (tripId.isBlank()) {
            return@withContext SmartClassificationResult.Ignored(tripId, "Blank trip ID")
        }

        // 1. Load the completed trip strictly from Room
        val trip = tripRepository.getTripById(tripId)
            ?: return@withContext SmartClassificationResult.Ignored(tripId, "Trip not found in Room")

        // 2. Activation cutoff check: must be >= November 15, 2026
        if (!SmartTripLearningCutoff.isEligibleByDate(trip.endTime)) {
            return@withContext SmartClassificationResult.Ignored(
                tripId,
                "Trip completed before activation cutoff (Nov 15, 2026)"
            )
        }

        // 3. Feature enabled check
        val isEnabled = preferencesManager.isSmartTripRecognitionEnabled.first()
        if (!isEnabled) {
            return@withContext SmartClassificationResult.Ignored(tripId, "Smart Trip Recognition is disabled")
        }

        // 4. Precedence: Never overwrite manual classifications or work schedule rules
        if (trip.classificationSource == ClassificationSource.MANUAL.value ||
            (trip.classification != TripClassification.UNREVIEWED.value &&
             trip.classificationSource != ClassificationSource.SMART_AUTO.value &&
             trip.classificationSource != ClassificationSource.SMART_SUGGESTED.value &&
             trip.classificationSource != ClassificationSource.SCHEDULE.value)
        ) {
            return@withContext SmartClassificationResult.Ignored(tripId, "Trip is already manually classified")
        }

        if (trip.classificationSource == ClassificationSource.SCHEDULE.value) {
            return@withContext SmartClassificationResult.Ignored(tripId, "Work Schedule rule takes precedence")
        }

        // 5. Avoid duplicate processing if already suggested or dismissed
        if (trip.suggestedClassification != null || trip.classificationSource == ClassificationSource.SMART_AUTO.value) {
            return@withContext SmartClassificationResult.Ignored(tripId, "Trip has already been evaluated or suggested")
        }

        // 6. Check active mode
        val mode = modeOverride ?: preferencesManager.smartClassificationMode.first()

        // 7. Evaluate route pattern and confidence
        val decision = classificationEngine.evaluateTripWithMode(trip, mode)

        // 8. Re-check enabled state to protect against concurrent preference toggle
        if (!preferencesManager.isSmartTripRecognitionEnabled.first()) {
            return@withContext SmartClassificationResult.Ignored(tripId, "Smart Trip Recognition disabled during processing")
        }

        when (decision) {
            is ClassificationDecision.AutomaticClassification -> {
                if (mode == SmartClassificationMode.AUTO_CLASSIFY) {
                    val targetEntity = decision.suggestedBusinessEntity
                        ?: if (decision.suggestedCategory.equals("BUSINESS", ignoreCase = true)) {
                            BusinessEntity.MY_SUCCULENT_CORNER.displayName
                        } else {
                            BusinessEntity.PERSONAL.displayName
                        }
                    val targetPurpose = decision.suggestedPurpose
                        ?: if (decision.suggestedCategory.equals("BUSINESS", ignoreCase = true)) {
                            TripPurpose.NURSERY_PICKUP
                        } else {
                            "Personal Drive"
                        }

                    val updated = tripRepository.autoClassifyTrip(
                        tripId = tripId,
                        classification = decision.suggestedCategory,
                        businessEntity = targetEntity,
                        purpose = targetPurpose,
                        notes = trip.notes
                    )

                    if (updated) {
                        GoogleSheetsSyncWorker.enqueueOneTimeSync(context)
                        notificationHelper.showAutoClassifiedNotification(
                            tripId = trip.id,
                            distanceMiles = trip.totalDistanceMiles,
                            category = decision.suggestedCategory
                        )
                        SmartClassificationResult.AutoClassified(
                            tripId = trip.id,
                            category = decision.suggestedCategory,
                            confidence = decision.agreementRatio,
                            matchingTripCount = decision.confirmedTripCount,
                            businessEntity = targetEntity,
                            purpose = targetPurpose
                        )
                    } else {
                        SmartClassificationResult.Ignored(tripId, "Trip classification updated concurrently")
                    }
                } else {
                    // ALWAYS_ASK or SUGGEST_ONLY mode: record as suggestion
                    val updated = tripRepository.setPendingSuggestion(tripId, decision.suggestedCategory)
                    val notify = (mode == SmartClassificationMode.ALWAYS_ASK)
                    if (updated && notify) {
                        notificationHelper.showTripSuggestionNotification(
                            tripId = trip.id,
                            distanceMiles = trip.totalDistanceMiles,
                            suggestedCategory = decision.suggestedCategory,
                            matchCount = decision.confirmedTripCount
                        )
                    }
                    SmartClassificationResult.Suggested(
                        tripId = trip.id,
                        category = decision.suggestedCategory,
                        confidence = decision.agreementRatio,
                        matchingTripCount = decision.confirmedTripCount,
                        businessEntity = decision.suggestedBusinessEntity,
                        purpose = decision.suggestedPurpose,
                        notificationShown = updated && notify
                    )
                }
            }

            is ClassificationDecision.SuggestedClassification -> {
                val updated = tripRepository.setPendingSuggestion(tripId, decision.suggestedCategory)
                val notify = (mode == SmartClassificationMode.ALWAYS_ASK || mode == SmartClassificationMode.AUTO_CLASSIFY)
                if (updated && notify) {
                    notificationHelper.showTripSuggestionNotification(
                        tripId = trip.id,
                        distanceMiles = trip.totalDistanceMiles,
                        suggestedCategory = decision.suggestedCategory,
                        matchCount = decision.confirmedTripCount
                    )
                }
                SmartClassificationResult.Suggested(
                    tripId = trip.id,
                    category = decision.suggestedCategory,
                    confidence = decision.agreementRatio,
                    matchingTripCount = decision.confirmedTripCount,
                    businessEntity = decision.suggestedBusinessEntity,
                    purpose = decision.suggestedPurpose,
                    notificationShown = updated && notify
                )
            }

            is ClassificationDecision.AmbiguousClassification, is ClassificationDecision.NoSuggestion -> {
                if (mode != SmartClassificationMode.SUGGEST_ONLY &&
                    trip.classification == TripClassification.UNREVIEWED.value
                ) {
                    notificationHelper.showTripDetectedNotification(trip.id, trip.totalDistanceMiles)
                }
                SmartClassificationResult.NoSuggestion(
                    tripId = trip.id,
                    reason = (decision as? ClassificationDecision.NoSuggestion)?.reason ?: "Pattern ambiguous"
                )
            }
        }
    }
}
