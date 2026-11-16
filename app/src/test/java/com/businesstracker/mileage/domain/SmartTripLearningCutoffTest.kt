package com.businesstracker.mileage.domain

import com.businesstracker.mileage.domain.model.SmartTripLearningCutoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class SmartTripLearningCutoffTest {

    @Test
    fun verifyCutoffTimestamp_matchesNovember15_2026_MidnightPacificTime() {
        // Cutoff MUST be November 15, 2026, 12:00:00 AM in America/Los_Angeles (PST)
        val zoneId = ZoneId.of("America/Los_Angeles")
        val targetDateTime = ZonedDateTime.of(2026, 11, 15, 0, 0, 0, 0, zoneId)
        val calculatedEpochMillis = targetDateTime.toInstant().toEpochMilli()

        // 1. Mandatory requirement: Cutoff must equal 1794729600000L
        assertEquals(
            "Smart Trip Recognition cutoff timestamp must match 1794729600000L",
            1794729600000L,
            SmartTripLearningCutoff.ACTIVATION_CUTOFF_MILLIS
        )

        assertEquals(
            "Calculated ZonedDateTime in America/Los_Angeles must match 1794729600000L",
            1794729600000L,
            calculatedEpochMillis
        )

        assertEquals(
            "SmartTripLearningCutoff.calculateCutoffMillis() must equal 1794729600000L",
            1794729600000L,
            SmartTripLearningCutoff.calculateCutoffMillis()
        )

        // 2. Mandatory requirement: Do NOT use 1794739200000L (discrepancy offset)
        assertNotEquals(
            "Must NOT use 1794739200000L",
            1794739200000L,
            SmartTripLearningCutoff.ACTIVATION_CUTOFF_MILLIS
        )

        // 3. Confirm UTC representation is 2026-11-15T08:00:00Z (PST is UTC-8 in November)
        val instant = Instant.ofEpochMilli(SmartTripLearningCutoff.ACTIVATION_CUTOFF_MILLIS)
        assertEquals("2026-11-15T08:00:00Z", instant.toString())
    }

    @Test
    fun isEligibleByDate_boundaryConditions() {
        val cutoff = SmartTripLearningCutoff.ACTIVATION_CUTOFF_MILLIS

        // 1 millisecond before midnight PST -> strictly ineligible
        assertFalse(
            "Trip completed 1ms before cutoff must be ineligible",
            SmartTripLearningCutoff.isEligibleByDate(cutoff - 1)
        )

        // Historical dates (e.g. November 14, 2026 or earlier) -> ineligible
        assertFalse(
            "Historical trips from Nov 14, 2026 must be ineligible",
            SmartTripLearningCutoff.isEligibleByDate(cutoff - 86400000L)
        )

        // Exactly at midnight PST -> eligible
        assertTrue(
            "Trip completed exactly at cutoff must be eligible",
            SmartTripLearningCutoff.isEligibleByDate(cutoff)
        )

        // After cutoff -> eligible
        assertTrue(
            "Trip completed after cutoff must be eligible",
            SmartTripLearningCutoff.isEligibleByDate(cutoff + 1)
        )
        assertTrue(
            "Trip completed days after cutoff must be eligible",
            SmartTripLearningCutoff.isEligibleByDate(cutoff + 86400000L)
        )
    }
}
