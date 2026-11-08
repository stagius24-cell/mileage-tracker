package com.businesstracker.mileage.domain.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationaryWatchdogPolicyTest {
    private val evidence = StationaryAutoStopMonitor.Snapshot(37.3382, -121.8863, 1_000L)

    @Test fun missingCallbacksRequireFreshFixAtDeadline() {
        val deadline = 601_000L
        assertTrue(StationaryWatchdogPolicy.shouldRecheck(evidence, deadline, 0L, true, false))
        assertFalse(StationaryWatchdogPolicy.isFreshDeadlineFix(evidence, 590_000L, deadline))
        assertTrue(StationaryWatchdogPolicy.isFreshDeadlineFix(evidence, deadline, deadline))
    }

    @Test fun delayedBackgroundCheckCannotTrustCachedLocation() {
        val late = 900_000L
        assertTrue(StationaryWatchdogPolicy.shouldRecheck(evidence, late, 0L, true, false))
        assertFalse(StationaryWatchdogPolicy.isFreshDeadlineFix(evidence, 601_000L, late))
        assertTrue(StationaryWatchdogPolicy.isFreshDeadlineFix(evidence, late, late))
        assertFalse(StationaryWatchdogPolicy.shouldRecheck(evidence, late + 1L, late, true, false))
    }

    @Test fun manualPausedOrUnconfirmedTripNeverStartsRecheck() {
        assertFalse(StationaryWatchdogPolicy.shouldRecheck(evidence, 601_000L, 0L, false, false))
        assertFalse(StationaryWatchdogPolicy.shouldRecheck(evidence, 601_000L, 0L, true, true))
        assertFalse(StationaryWatchdogPolicy.shouldRecheck(null, 601_000L, 0L, true, false))
    }

    @Test fun monotonicResetCannotReusePreviousDeadline() {
        assertFalse(StationaryWatchdogPolicy.shouldRecheck(evidence, 100L, 0L, true, false))
        assertFalse(StationaryWatchdogPolicy.isFreshDeadlineFix(evidence, 100L, 100L))
    }

    @Test fun recoveryRequiresSameTripAndKnownSameBoot() {
        assertTrue(StationaryWatchdogPolicy.canRestore("trip-a", "trip-a", 5, 5, 1_000L, 601_000L))
        assertFalse(StationaryWatchdogPolicy.canRestore("trip-a", "trip-b", 5, 5, 1_000L, 601_000L))
        assertFalse(StationaryWatchdogPolicy.canRestore("trip-a", "trip-a", 5, 6, 1_000L, 601_000L))
        assertFalse(StationaryWatchdogPolicy.canRestore("trip-a", "trip-a", 0, 0, 1_000L, 601_000L))
        assertFalse(StationaryWatchdogPolicy.canRestore("trip-a", "trip-a", 5, 5, 700_000L, 601_000L))
    }
}
