package com.businesstracker.mileage.domain.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleMotionEvidencePolicyTest {
    @Test
    fun stationaryBluetoothConnectedVehicleDoesNotQualify() {
        val first = sample(lat = 37.0, lon = -122.0, time = 10_000L)
        val second = sample(lat = 37.0, lon = -122.0, time = 130_000L)

        assertFalse(VehicleMotionEvidencePolicy.confirmsMovement(null, first))
        assertFalse(VehicleMotionEvidencePolicy.confirmsMovement(first, second))
    }

    @Test
    fun movementAfterTwoMinuteStationaryInterruptionQualifiesWithoutAnotherBluetoothEvent() {
        val beforeLeaving = sample(lat = 37.0, lon = -122.0, time = 10_000L)
        val afterReturningAndDriving = sample(lat = 37.00055, lon = -122.0, time = 130_000L)

        assertFalse(VehicleMotionEvidencePolicy.confirmsMovement(null, beforeLeaving))
        assertTrue(VehicleMotionEvidencePolicy.confirmsMovement(beforeLeaving, afterReturningAndDriving))
    }

    @Test
    fun reliableSpeedCanQualifyWithoutSecondPosition() {
        val moving = sample(
            lat = 37.0,
            lon = -122.0,
            time = 10_000L,
            speed = VehicleMotionEvidencePolicy.MIN_SPEED_METERS_PER_SECOND
        )

        assertTrue(VehicleMotionEvidencePolicy.confirmsMovement(null, moving))
    }

    @Test
    fun lowConfidencePoorAccuracyAndTooOldSamplesAreRejected() {
        val start = sample(lat = 37.0, lon = -122.0, time = 10_000L)
        assertFalse(VehicleMotionEvidencePolicy.confirmsMovement(start, start.copy(inVehicleConfidence = 40)))
        assertFalse(VehicleMotionEvidencePolicy.confirmsMovement(start, start.copy(accuracyMeters = 120f)))
        assertFalse(VehicleMotionEvidencePolicy.confirmsMovement(start, start.copy(elapsedRealtimeMillis = 250_001L)))
    }

    private fun sample(
        lat: Double,
        lon: Double,
        time: Long,
        speed: Float? = 0f
    ) = VehicleMotionEvidencePolicy.Sample(
        latitude = lat,
        longitude = lon,
        accuracyMeters = 10f,
        speedMetersPerSecond = speed,
        elapsedRealtimeMillis = time,
        inVehicleConfidence = 90
    )
}
