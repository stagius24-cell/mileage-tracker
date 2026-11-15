package com.businesstracker.mileage.domain.tracking

/**
 * Validates whether GPS movement corroborates resuming an automatic drive.
 *
 * Rules:
 * - Speed must be finite, non-negative, and meet or exceed [speedThresholdMph] (default 10.0 mph).
 * - Accuracy must be finite, positive, and <= [maxAccuracyMeters] (default 50.0 m).
 * - Fix age must be strictly known (> 0), non-future (fixTimestamp <= nowMillis), and <= [windowMillis] (default 30s).
 * - Duplicate (fixTimestamp <= lastQualifyingTimestamp) and out-of-order evidence is rejected.
 * - Requires TWO qualifying fixes strictly ordered in time within [windowMillis] to corroborate motion.
 * - Corroboration state must be reset at session boundaries.
 */
class MotionResumptionValidator(
    val speedThresholdMph: Double = 10.0,
    val windowMillis: Long = 30_000L,
    val maxAccuracyMeters: Float = 50.0f
) {
    private var lastQualifyingTimestampMillis: Long = 0L

    /**
     * Evaluates a fresh GPS fix.
     * Returns [MotionValidationResult.MotionCorroborated] ONLY when a second qualifying fix arrives
     * strictly after the first qualifying fix within [windowMillis].
     */
    @Synchronized
    fun evaluateFix(
        speedMph: Double,
        accuracyMeters: Float,
        fixTimestampMillis: Long,
        nowMillis: Long
    ): MotionValidationResult {
        // 1. Non-finite or negative speed check
        if (speedMph.isNaN() || speedMph.isInfinite() || speedMph < 0.0) {
            return MotionValidationResult.Rejected("Non-finite or negative speed: $speedMph")
        }

        // 2. Non-finite or invalid accuracy check
        if (accuracyMeters.isNaN() || accuracyMeters.isInfinite() || accuracyMeters <= 0f || accuracyMeters > maxAccuracyMeters) {
            return MotionValidationResult.Rejected("Invalid or poor accuracy: $accuracyMeters")
        }

        // 3. Unknown-age check
        if (fixTimestampMillis <= 0L || nowMillis <= 0L) {
            return MotionValidationResult.Rejected("Unknown age (fixTimestamp=$fixTimestampMillis, now=$nowMillis)")
        }

        // 4. Future timestamp check
        if (fixTimestampMillis > nowMillis) {
            return MotionValidationResult.Rejected("Future timestamp ($fixTimestampMillis > $nowMillis)")
        }

        // 5. Stale evidence check
        val ageMillis = nowMillis - fixTimestampMillis
        if (ageMillis > windowMillis) {
            return MotionValidationResult.Rejected("Stale evidence (${ageMillis}ms old > ${windowMillis}ms)")
        }

        // 6. Duplicate or out-of-order timestamp check
        if (lastQualifyingTimestampMillis > 0L && fixTimestampMillis <= lastQualifyingTimestampMillis) {
            return MotionValidationResult.Rejected("Duplicate or out-of-order timestamp ($fixTimestampMillis <= $lastQualifyingTimestampMillis)")
        }

        // 7. Speed threshold check
        if (speedMph < speedThresholdMph) {
            return MotionValidationResult.Rejected("Below speed threshold (${speedMph}mph < ${speedThresholdMph}mph)")
        }

        // 8. Two-fix corroboration check
        if (lastQualifyingTimestampMillis > 0L && (fixTimestampMillis - lastQualifyingTimestampMillis) <= windowMillis) {
            val first = lastQualifyingTimestampMillis
            lastQualifyingTimestampMillis = fixTimestampMillis
            return MotionValidationResult.MotionCorroborated(
                firstFixTimestamp = first,
                secondFixTimestamp = fixTimestampMillis
            )
        } else {
            lastQualifyingTimestampMillis = fixTimestampMillis
            return MotionValidationResult.AwaitingCorroboration(
                firstFixTimestamp = fixTimestampMillis
            )
        }
    }

    @Synchronized
    fun reset() {
        lastQualifyingTimestampMillis = 0L
    }

    @Synchronized
    fun getLastQualifyingTimestamp(): Long = lastQualifyingTimestampMillis
}

sealed interface MotionValidationResult {
    data class MotionCorroborated(val firstFixTimestamp: Long, val secondFixTimestamp: Long) : MotionValidationResult
    data class AwaitingCorroboration(val firstFixTimestamp: Long) : MotionValidationResult
    data class Rejected(val reason: String) : MotionValidationResult
}
