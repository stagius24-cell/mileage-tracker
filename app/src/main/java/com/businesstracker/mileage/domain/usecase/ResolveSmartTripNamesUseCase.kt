package com.businesstracker.mileage.domain.usecase

import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import javax.inject.Inject
import javax.inject.Singleton

data class SmartTripNames(
    val startDisplayName: String,
    val endDisplayName: String,
    val startSavedLocation: SavedLocationEntity? = null,
    val endSavedLocation: SavedLocationEntity? = null
)

data class ClassificationSuggestion(
    val suggestedClassification: String?, // "BUSINESS", "PERSONAL", or null
    val suggestedBusinessEntity: String? = null // "MySucculentCorner", etc.
)

@Singleton
class ResolveSmartTripNamesUseCase @Inject constructor(
    private val matchLocationUseCase: MatchLocationUseCase
) {

    fun resolve(
        trip: TripEntity,
        savedLocations: List<SavedLocationEntity>
    ): SmartTripNames {
        val startMatch = matchLocationUseCase.findMatch(
            trip.startLatitude,
            trip.startLongitude,
            savedLocations
        )

        val endMatch = matchLocationUseCase.findMatch(
            trip.endLatitude,
            trip.endLongitude,
            savedLocations
        )

        val defaultStart = trip.startAddress ?: "Recorded start location"
        val defaultEnd = trip.endAddress ?: "Recorded destination"

        return SmartTripNames(
            startDisplayName = startMatch?.location?.name ?: defaultStart,
            endDisplayName = endMatch?.location?.name ?: defaultEnd,
            startSavedLocation = startMatch?.location,
            endSavedLocation = endMatch?.location
        )
    }

    /**
     * Section 6 classification suggestions for unreviewed trips:
     * - If only one endpoint provides a suggestion, suggest it.
     * - If both endpoints provide conflicting suggestions (e.g. Business vs Personal), no automatic suggestion (null).
     * - If both agree, suggest that classification.
     */
    fun evaluateClassificationSuggestion(
        startMatch: SavedLocationEntity?,
        endMatch: SavedLocationEntity?
    ): ClassificationSuggestion {
        val startPref = startMatch?.classificationPreference?.takeIf { it.isNotBlank() }
        val endPref = endMatch?.classificationPreference?.takeIf { it.isNotBlank() }

        if (startPref == null && endPref == null) {
            return ClassificationSuggestion(null, null)
        }

        if (startPref != null && endPref == null) {
            val entity = if (startPref == "BUSINESS") startMatch.businessEntity else null
            return ClassificationSuggestion(startPref, entity)
        }

        if (startPref == null && endPref != null) {
            val entity = if (endPref == "BUSINESS") endMatch.businessEntity else null
            return ClassificationSuggestion(endPref, entity)
        }

        // Both endpoints have preferences
        return if (startPref.equals(endPref, ignoreCase = true)) {
            val entity = if (startPref == "BUSINESS") {
                startMatch?.businessEntity ?: endMatch?.businessEntity
            } else null
            ClassificationSuggestion(startPref, entity)
        } else {
            // Conflicting preferences -> leave unreviewed with no suggested classification
            ClassificationSuggestion(null, null)
        }
    }
}
