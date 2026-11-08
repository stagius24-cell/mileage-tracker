package com.businesstracker.mileage.domain.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationaryAutoStopMonitorTest {
    private val originLatitude = 37.3382
    private val originLongitude = -121.8863

    @Test
    fun tenMinutesStationary_requestsStop() {
        val monitor = StationaryAutoStopMonitor()

        assertFalse(monitor.record(originLatitude, originLongitude, 0f, 5f, 1_000L))
        assertFalse(monitor.record(originLatitude + 0.00005, originLongitude, 0.2f, 5f, 300_000L))
        assertTrue(monitor.record(originLatitude, originLongitude + 0.00005, 0f, 5f, 601_000L))
    }

    @Test
    fun meaningfulMovement_resetsTenMinuteTimer() {
        val monitor = StationaryAutoStopMonitor()

        assertFalse(monitor.record(originLatitude, originLongitude, 0f, 5f, 0L))
        assertFalse(monitor.record(originLatitude + 0.001, originLongitude, 8f, 5f, 540_000L))
        assertFalse(monitor.record(originLatitude + 0.001, originLongitude, 0f, 5f, 600_001L))
        assertTrue(monitor.record(originLatitude + 0.001, originLongitude, 0f, 5f, 1_140_000L))
    }

    @Test
    fun inaccurateLocations_doNotAdvanceOrTriggerTimer() {
        val monitor = StationaryAutoStopMonitor()

        assertFalse(monitor.record(originLatitude, originLongitude, 0f, 5f, 0L))
        assertFalse(monitor.record(originLatitude, originLongitude, 0f, 100f, 700_000L))
        assertTrue(monitor.record(originLatitude, originLongitude, 0f, 5f, 700_001L))
    }

    @Test
    fun stopIsRequestedOnlyOnceUntilReset() {
        val monitor = StationaryAutoStopMonitor(timeoutMillis = 1_000L)

        assertFalse(monitor.record(originLatitude, originLongitude, 0f, 5f, 0L))
        assertTrue(monitor.record(originLatitude, originLongitude, 0f, 5f, 1_000L))
        assertFalse(monitor.record(originLatitude, originLongitude, 0f, 5f, 2_000L))

        monitor.reset()
        assertFalse(monitor.record(originLatitude, originLongitude, 0f, 5f, 3_000L))
        assertTrue(monitor.record(originLatitude, originLongitude, 0f, 5f, 4_000L))
    }
    @Test
    fun invalidFixesCannotCreateStationaryEvidence() {
        val monitor = StationaryAutoStopMonitor()
        assertFalse(monitor.record(Double.NaN, originLongitude, 0f, 5f, 0L))
        assertFalse(monitor.record(originLatitude, originLongitude, 0f, Float.NaN, 600_000L))
        assertFalse(monitor.record(originLatitude, originLongitude, Float.NaN, 5f, 600_000L))
        assertFalse(monitor.record(originLatitude, originLongitude, 0f, 5f, 600_001L))
        assertTrue(monitor.record(originLatitude, originLongitude, 0f, 5f, 1_200_001L))
    }

}
