package com.businesstracker.mileage.domain.tracking

/**
 * Validates whether GPS movement corroborates resuming an automatic drive.
 *
 * Rules:
 * - Speed must meet or exceed [speedThresholdMph] (default 10.0 mph).
 * - Accuracy must be within [maxAccuracyMeters] (default 50.0 m) and > 0.
 * - Evidence must be fresh (age <= [windowMillis], not > 5s into the future).
 * - Requires TWO qualifying fixes at distinct timestamps within [windowMillis] (default 30s)
 *   to reject isolated GPS speed spikes.
 */
class MotionResumptionValidator(
    val speedThresholdMph: Double = 10.0,
    val windowMillis: Long = 30_000L,
    val maxAccuracyMeters: Float = 50.0f
) {
    private var lastQualifyingTimestampMillis: Long = 0L

    /**
     * Evaluates a fresh GPS fix.
     * Returns true ONLY when a second qualifying fix arrives at a distinct timestamp within [windowMillis].
     */
    @Synchronized
    fun evaluateFix(
        speedMph: Double,
        accuracyMeters: Float,
        fixTimestampMillis: Long,
        nowMillis: Long
    ): MotionValidationResult {
        val ageMillis = nowMillis - fixTimestampMillis

        // 1. Sanity: Reject future timestamps (>5s) and stale fixes (>30s)
        if (ageMillis < -5_000L) {
            return MotionValidationResult.Rejected("Future timestamp (${ageMillis}ms ahead)")
        }
        if (ageMillis > windowMillis) {
            return MotionValidationResult.Rejected("Stale evidence (${ageMillis}ms old > ${windowMillis}ms)")
        }

        // 2. Accuracy check
        if (accuracyMeters <= 0f || accuracyMeters > maxAccuracyMeters) {
            return MotionValidationResult.Rejected("Poor accuracy (${accuracyMeters}m > ${maxAccuracyMeters}m)")
        }

        // 3. Speed threshold check
        if (speedMph < speedThresholdMph) {
            return MotionValidationResult.Rejected("Below speed threshold (${speedMph}mph < ${speedThresholdMph}mph)")
        }

        // 4. Two-fix corroboration check
        if (lastQualifyingTimestampMillis > 0L &&
            fixTimestampMillis != lastQualifyingTimestampMillis &&
            Math.abs(fixTimestampMillis - lastQualifyingTimestampMillis) <= windowMillis
        ) {
            // Second qualifying fix within window!
            return MotionValidationResult.MotionCorroborated(
                firstFixTimestamp = lastQualifyingTimestampMillis,
                secondFixTimestamp = fixTimestampMillis
            )
        } else {
            // First qualifying fix recorded
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
}

sealed interface MotionValidationResult {
    data class MotionCorroborated(val firstFixTimestamp: Long, val secondFixTimestamp: Long) : MotionValidationResult
    data class AwaitingCorroboration(val firstFixTimestamp: Long) : MotionValidationResult
    data class Rejected(val reason: String) : MotionValidationResult
}
