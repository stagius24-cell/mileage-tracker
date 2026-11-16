package com.businesstracker.mileage.domain.model

/**
 * Operating mode for Smart Trip Recognition classification.
 *
 * AUTO_CLASSIFY:
 * - Automatically classifies high-confidence trips (>= 5 matches, >= 90% agreement).
 * - Requests confirmation for medium-confidence matches (3-4 matches 100% agreement, or 5+ matches 80-89% agreement).
 * - Leaves uncertain trips unreviewed.
 *
 * ALWAYS_ASK:
 * - Default mode.
 * - Never automatically classifies.
 * - Suggests classification when evidence threshold is met, requiring explicit confirmation.
 *
 * SUGGEST_ONLY:
 * - Displays classification suggestions in trip history/details.
 * - Never automatically classifies and suppresses prompts/notifications.
 */
enum class SmartClassificationMode {
    AUTO_CLASSIFY,
    ALWAYS_ASK,
    SUGGEST_ONLY;

    companion object {
        val DEFAULT = ALWAYS_ASK

        fun fromString(value: String?): SmartClassificationMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
        }
    }
}
