package com.businesstracker.mileage.domain.model

import java.time.ZoneId
import java.time.ZonedDateTime

object SmartTripLearningCutoff {
    /**
     * Exact activation cutoff: November 15, 2026, 12:00:00 AM America/Los_Angeles (PST, UTC-08:00).
     * Corresponds to epoch millis 1794729600000L (2026-11-15T08:00:00Z UTC).
     */
    const val ACTIVATION_CUTOFF_MILLIS: Long = 1794729600000L
    const val TIMEZONE_ID: String = "America/Los_Angeles"

    fun calculateCutoffMillis(): Long {
        return ZonedDateTime.of(2026, 11, 15, 0, 0, 0, 0, ZoneId.of(TIMEZONE_ID))
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Checks if a trip is eligible for learning based on its completion timestamp (endTime).
     * Old trips edited after the cutoff must never be learned; only trips completed on or after
     * the cutoff are eligible.
     */
    fun isEligibleByDate(tripEndTime: Long): Boolean {
        return tripEndTime >= ACTIVATION_CUTOFF_MILLIS
    }
}
