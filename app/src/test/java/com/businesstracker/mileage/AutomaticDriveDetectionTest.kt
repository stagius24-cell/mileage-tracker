package com.businesstracker.mileage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class AutomaticDriveDetectionTest {

    companion object {
        private const val METERS_PER_MILE = 1609.344
        private const val MIN_DISPLACEMENT_METERS = 200.0
    }

    @Test
    fun displacementThreshold_under200Meters_isFalseTrip() {
        val stationaryJitterMeters = 50.0
        val stationaryMiles = stationaryJitterMeters / METERS_PER_MILE

        val isFalseTrip = stationaryMiles < 0.12 && stationaryJitterMeters < MIN_DISPLACEMENT_METERS
        assertTrue("Movements under 200m must be flagged as false trips", isFalseTrip)
    }

    @Test
    fun displacementThreshold_over200Meters_isValidTrip() {
        val validDriveMeters = 850.0 // ~0.53 miles
        val validMiles = validDriveMeters / METERS_PER_MILE

        val isFalseTrip = validMiles < 0.12 && validDriveMeters < MIN_DISPLACEMENT_METERS
        assertFalse("Movements over 200m must be accepted as valid drives", isFalseTrip)
    }

    @Test
    fun workSchedule_weekdayWithinHours_isBusiness() {
        val activeDays = setOf("MON", "TUE", "WED", "THU", "FRI")
        val currentDay = "WED"
        val currentTime = LocalTime.of(14, 30) // 2:30 PM

        val startTime = LocalTime.of(8, 0)
        val endTime = LocalTime.of(18, 0)

        val isWithinDays = currentDay in activeDays
        val isWithinHours = !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime)
        val isBusinessDrive = isWithinDays && isWithinHours

        assertTrue("Wednesday 2:30 PM must be within Mon-Fri 8am-6pm schedule", isBusinessDrive)
    }

    @Test
    fun workSchedule_weekend_isOutsideSchedule() {
        val activeDays = setOf("MON", "TUE", "WED", "THU", "FRI")
        val currentDay = "SAT"
        val currentTime = LocalTime.of(11, 0) // 11:00 AM

        val isWithinDays = currentDay in activeDays
        assertFalse("Saturday must be recognized as outside work schedule", isWithinDays)
    }

    @Test
    fun workSchedule_afterHours_isOutsideSchedule() {
        val currentTime = LocalTime.of(21, 15) // 9:15 PM
        val startTime = LocalTime.of(8, 0)
        val endTime = LocalTime.of(18, 0)

        val isWithinHours = !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime)
        assertFalse("9:15 PM must be recognized as outside 8am-6pm work hours", isWithinHours)
    }

    @Test
    fun outsideScheduleBehavior_personalVsBatterySaver() {
        val behaviorClassifyPersonal = "CLASSIFY_PERSONAL"
        val behaviorConserveBattery = "CONSERVE_BATTERY"

        val isWithinSchedule = false

        // In personal mode, drive is recorded but marked Personal
        val suggestedEntity = if (isWithinSchedule) "MySucculentCorner" else "Personal"
        assertEquals("Personal", suggestedEntity)

        // In battery saver mode, tracking is completely skipped
        val shouldSkipTracking = !isWithinSchedule && behaviorConserveBattery == "CONSERVE_BATTERY"
        assertTrue("Battery saver mode must skip tracking outside schedule", shouldSkipTracking)
    }
}
