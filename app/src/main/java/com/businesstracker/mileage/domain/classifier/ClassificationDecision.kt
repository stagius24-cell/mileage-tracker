package com.businesstracker.mileage.domain.classifier

/**
 * Structured result produced by the Smart Trip Classification Decision Engine.
 *
 * Deterministic, offline, and UI-independent.
 */
sealed interface ClassificationDecision {
    val suggestedCategory: String?
    val suggestedBusinessEntity: String?
    val suggestedPurpose: String?
    val matchingPatternId: String?
    val confirmedTripCount: Int
    val businessCount: Int
    val personalCount: Int
    val agreementRatio: Double
    val reason: String
    val requiresExplicitConfirmation: Boolean

    /**
     * No suggestion can be made due to insufficient evidence, conflict, or exclusion.
     */
    data class NoSuggestion(
        override val reason: String,
        override val matchingPatternId: String? = null,
        override val confirmedTripCount: Int = 0,
        override val businessCount: Int = 0,
        override val personalCount: Int = 0,
        override val agreementRatio: Double = 0.0
    ) : ClassificationDecision {
        override val suggestedCategory: String? = null
        override val suggestedBusinessEntity: String? = null
        override val suggestedPurpose: String? = null
        override val requiresExplicitConfirmation: Boolean = true
    }

    /**
     * A suggested category (Business or Personal) with supporting evidence.
     * Always requires explicit user confirmation before applying.
     */
    data class SuggestedClassification(
        override val suggestedCategory: String,
        override val suggestedBusinessEntity: String? = null,
        override val suggestedPurpose: String? = null,
        override val matchingPatternId: String,
        override val confirmedTripCount: Int,
        override val businessCount: Int,
        override val personalCount: Int,
        override val agreementRatio: Double,
        override val reason: String,
        override val requiresExplicitConfirmation: Boolean = true
    ) : ClassificationDecision

    /**
     * High-confidence classification applied automatically when AUTO_CLASSIFY is enabled.
     * Does not require explicit confirmation (though can still be manually corrected).
     */
    data class AutomaticClassification(
        override val suggestedCategory: String,
        override val suggestedBusinessEntity: String? = null,
        override val suggestedPurpose: String? = null,
        override val matchingPatternId: String,
        override val confirmedTripCount: Int,
        override val businessCount: Int,
        override val personalCount: Int,
        override val agreementRatio: Double,
        override val reason: String
    ) : ClassificationDecision {
        override val requiresExplicitConfirmation: Boolean = false
    }

    /**
     * Multiple plausible or conflicting patterns detected in proximity.
     * Requires manual review.
     */
    data class AmbiguousClassification(
        val candidatePatternIds: List<String>,
        override val reason: String,
        override val confirmedTripCount: Int = 0,
        override val businessCount: Int = 0,
        override val personalCount: Int = 0,
        override val agreementRatio: Double = 0.0
    ) : ClassificationDecision {
        override val suggestedCategory: String? = null
        override val suggestedBusinessEntity: String? = null
        override val suggestedPurpose: String? = null
        override val matchingPatternId: String? = null
        override val requiresExplicitConfirmation: Boolean = true
    }
}
