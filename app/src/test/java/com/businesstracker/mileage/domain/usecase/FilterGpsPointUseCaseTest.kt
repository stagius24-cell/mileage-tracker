package com.businesstracker.mileage.domain.usecase

import android.location.Location
import com.businesstracker.mileage.domain.model.FilterResult
import com.businesstracker.mileage.domain.model.Waypoint
import com.businesstracker.mileage.domain.service.BootCountProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilterGpsPointUseCaseTest {

    private class TestBootCountProvider(var currentBootCount: Int = 1) : BootCountProvider {
        override fun getBootCount(): Int = currentBootCount
    }

    private lateinit var bootCountProvider: TestBootCountProvider
    private lateinit var filterGpsPointUseCase: FilterGpsPointUseCase

    @Before
    fun setUp() {
        bootCountProvider = TestBootCountProvider(currentBootCount = 1)
        filterGpsPointUseCase = FilterGpsPointUseCase(bootCountProvider)
    }

    private fun createLocation(
        lat: Double,
        lng: Double,
        timeMillis: Long,
        elapsedRealtimeNanos: Long = 0L,
        accuracy: Float = 5.0f,
        speedMps: Float = 15.0f
    ): Location {
        return Location("gps").apply {
            latitude = lat
            longitude = lng
            time = timeMillis
            this.elapsedRealtimeNanos = elapsedRealtimeNanos
            this.accuracy = accuracy
            speed = speedMps
        }
    }

    // REGRESSION TEST 1: Same boot identifier and increasing elapsed time: normal elapsed-time calculation
    @Test
    fun sameBootIdentifier_increasingElapsedTime_calculatesNormalElapsedTimeDelta() {
        val lastWaypoint = Waypoint(
            latitude = 37.3382,
            longitude = -121.8863,
            timestamp = 1000L,
            elapsedRealtimeNanos = 10_000_000_000L, // 10s
            bootCount = 1
        )

        // 5 seconds later in same boot session (bootCount = 1)
        bootCountProvider.currentBootCount = 1
        val newLocation = createLocation(
            lat = 37.3391, // ~100m displacement
            lng = -121.8863,
            timeMillis = 6000L,
            elapsedRealtimeNanos = 15_000_000_000L, // 15s (+5s)
            speedMps = 20.0f
        )

        val result = filterGpsPointUseCase.filter(newLocation, lastWaypoint)
        assertTrue("Point with reasonable speed in same boot session must be accepted", result is FilterResult.Accepted)
        val accepted = result as FilterResult.Accepted
        assertEquals(1, accepted.waypoint.bootCount)
        assertEquals(15_000_000_000L, accepted.waypoint.elapsedRealtimeNanos)
        assertTrue("Displacement should be approximately 100 meters", accepted.deltaDistanceMeters in 90.0..110.0)
    }

    // REGRESSION TEST 2: Different boot identifier and increasing elapsed time: re-baseline safely, no cross-boot delta (50h vs 60h prompt scenario)
    @Test
    fun differentBootIdentifier_increasingElapsedTime_rebaselinesSafely_doesNotCalculateCrossBootDelta() {
        // Previous boot (boot 1): phone was running for 50 hours
        val lastWaypoint = Waypoint(
            latitude = 37.3382,
            longitude = -121.8863,
            timestamp = 100_000L,
            elapsedRealtimeNanos = 50L * 3600L * 1_000_000_000L, // 50 hours
            bootCount = 1
        )

        // Phone rebooted! Current boot is boot 2, and new boot has already been running for 60 hours
        bootCountProvider.currentBootCount = 2
        val newLocation = createLocation(
            lat = 37.3391, // ~100m displacement
            lng = -121.8863,
            timeMillis = 200_000L,
            elapsedRealtimeNanos = 60L * 3600L * 1_000_000_000L, // 60 hours (greater than 50 hours!)
            speedMps = 15.0f // Doppler sensor speed = 15 m/s (~33.5 mph)
        )

        // Must detect reboot despite new elapsed time (60h) > old elapsed time (50h).
        // Must NOT calculate cross-boot delta (which would be 10 hours) or jump.
        // Re-baselines safely with Doppler sensor speed.
        val result = filterGpsPointUseCase.filter(newLocation, lastWaypoint, currentBootCount = 2)
        assertTrue("Must accept fix across reboot with safe Doppler re-baselining", result is FilterResult.Accepted)
        val accepted = result as FilterResult.Accepted
        assertEquals("New waypoint must record current boot count 2", 2, accepted.waypoint.bootCount)
        assertEquals(60L * 3600L * 1_000_000_000L, accepted.waypoint.elapsedRealtimeNanos)
        assertEquals("Speed must match sensor speed in mph (~33.55 mph)", 15.0 * 2.23694, accepted.speedMph, 0.5)
    }

    // REGRESSION TEST 3: Different boot identifier and decreasing elapsed time: re-baseline safely
    @Test
    fun differentBootIdentifier_decreasingElapsedTime_rebaselinesSafely() {
        // Previous boot (boot 1): 50 hours
        val lastWaypoint = Waypoint(
            latitude = 37.3382,
            longitude = -121.8863,
            timestamp = 100_000L,
            elapsedRealtimeNanos = 50L * 3600L * 1_000_000_000L,
            bootCount = 1
        )

        // Boot 2: 30 seconds after reboot
        bootCountProvider.currentBootCount = 2
        val newLocation = createLocation(
            lat = 37.3391,
            lng = -121.8863,
            timeMillis = 110_000L,
            elapsedRealtimeNanos = 30_000_000_000L, // 30s (< 50h)
            speedMps = 12.0f
        )

        val result = filterGpsPointUseCase.filter(newLocation, lastWaypoint, currentBootCount = 2)
        assertTrue("Must accept fix after reboot with decreasing elapsed time", result is FilterResult.Accepted)
        val accepted = result as FilterResult.Accepted
        assertEquals(2, accepted.waypoint.bootCount)
    }

    // REGRESSION TEST 4: Legacy persisted record with no boot identifier: safe fallback without crashing or inventing cross-boot delta
    @Test
    fun legacyPersistedRecord_noBootIdentifier_safeFallbackWithoutCrossBootDelta() {
        // Legacy waypoint with bootCount = 0 (from schema v1-v4)
        val legacyWaypoint = Waypoint(
            latitude = 37.3382,
            longitude = -121.8863,
            timestamp = 100_000L,
            elapsedRealtimeNanos = 50L * 3600L * 1_000_000_000L, // 50 hours
            bootCount = 0 // Legacy: unpopulated
        )

        // New fix arrives with valid bootCount = 3, but monotonic clock reset to 10s
        bootCountProvider.currentBootCount = 3
        val newLocation = createLocation(
            lat = 37.3391,
            lng = -121.8863,
            timeMillis = 110_000L,
            elapsedRealtimeNanos = 10_000_000_000L, // Reset to 10s
            speedMps = 14.0f
        )

        val result = filterGpsPointUseCase.filter(newLocation, legacyWaypoint, currentBootCount = 3)
        assertTrue("Legacy waypoint without bootCount must safely fall back and detect reboot via monotonic reset", result is FilterResult.Accepted)
        val accepted = result as FilterResult.Accepted
        assertEquals(3, accepted.waypoint.bootCount)
    }

    // REGRESSION TEST 5: Missing or unavailable boot identifier: safe, documented fallback
    @Test
    fun missingOrUnavailableBootIdentifier_safeFallback() {
        // Both records have bootCount = 0 (unavailable / unsupported hardware)
        val lastWaypoint = Waypoint(
            latitude = 37.3382,
            longitude = -121.8863,
            timestamp = 10_000L,
            elapsedRealtimeNanos = 10_000_000_000L,
            bootCount = 0
        )

        // Normal sequential fix with increasing monotonic nanos
        val newLocation = createLocation(
            lat = 37.3391,
            lng = -121.8863,
            timeMillis = 15_000L,
            elapsedRealtimeNanos = 15_000_000_000L,
            speedMps = 20.0f
        )

        val result = filterGpsPointUseCase.filter(newLocation, lastWaypoint, currentBootCount = 0)
        assertTrue("Must safely process fix using monotonic elapsed time when bootCount is unavailable", result is FilterResult.Accepted)
    }

    // REGRESSION TEST 6: Existing reboot regression test continues passing
    @Test
    fun existingMonotonicReset_rebootScenario_continuesPassing() {
        val lastWaypoint = Waypoint(
            latitude = 37.3382,
            longitude = -121.8863,
            timestamp = 100_000L,
            elapsedRealtimeNanos = 100_000_000_000L, // 100s
            bootCount = 0
        )

        // Monotonic nanos decreases (< 100s), simulating reboot without boot identifier
        val newLocation = createLocation(
            lat = 37.3391,
            lng = -121.8863,
            timeMillis = 110_000L,
            elapsedRealtimeNanos = 5_000_000_000L, // 5s
            speedMps = 15.0f
        )

        val result = filterGpsPointUseCase.filter(newLocation, lastWaypoint, currentBootCount = 0)
        assertTrue("Must detect reboot when elapsedRealtimeNanos decreases", result is FilterResult.Accepted)
    }

    // REGRESSION TEST 7: Existing speed and GPS filtering constraints continue passing
    @Test
    fun existingSpeedAndGpsFilteringConstraints_continuePassing() {
        // 1. Poor accuracy rejected
        val poorAccuracyLoc = createLocation(37.3382, -121.8863, 1000L, accuracy = 45.0f)
        val r1 = filterGpsPointUseCase.filter(poorAccuracyLoc, null)
        assertTrue("Accuracy > 35m must be rejected", r1 is FilterResult.Rejected)
        assertEquals(FilterResult.RejectionReason.POOR_ACCURACY, (r1 as FilterResult.Rejected).reason)

        // 2. Stationary jitter (< 15m) rejected
        val anchor = Waypoint(37.3382, -121.8863, timestamp = 1000L, elapsedRealtimeNanos = 1000L, bootCount = 1)
        val jitterLoc = createLocation(37.3382001, -121.8863001, 2000L, elapsedRealtimeNanos = 2000L) // < 1m
        val r2 = filterGpsPointUseCase.filter(jitterLoc, anchor, currentBootCount = 1)
        assertTrue("Movement < 15m must be rejected as stationary jitter", r2 is FilterResult.Rejected)
        assertEquals(FilterResult.RejectionReason.STATIONARY_JITTER, (r2 as FilterResult.Rejected).reason)

        // 3. Impossible speed spike (> 110 mph) rejected
        val highSpeedLoc = createLocation(
            lat = 37.5000, // ~18 km in 1 second = ~40,000 mph!
            lng = -121.8863,
            timeMillis = 2000L,
            elapsedRealtimeNanos = 2_000_000_000L, // +1s
            speedMps = 60.0f
        )
        val r3 = filterGpsPointUseCase.filter(highSpeedLoc, anchor, currentBootCount = 1)
        assertTrue("Speed > 110 mph must be rejected", r3 is FilterResult.Rejected)
        assertEquals(FilterResult.RejectionReason.IMPOSSIBLE_SPEED_SPIKE, (r3 as FilterResult.Rejected).reason)
    }
}
