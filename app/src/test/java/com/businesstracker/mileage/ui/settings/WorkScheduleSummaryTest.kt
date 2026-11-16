package com.businesstracker.mileage.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkScheduleSummaryTest {
    @Test
    fun weekdaySchedule_usesSavedDaysAndTimes() {
        assertEquals(
            "Mon–Fri\n8:00 AM – 6:00 PM",
            formatScheduleSummary(
                setOf("MON", "TUE", "WED", "THU", "FRI"),
                "08:00", "18:00"
            )
        )
    }

    @Test
    fun individualDays_keepTheirOwnIdentities() {
        assertEquals(
            "Tue, Thu, Sun\n9:15 AM – 5:30 PM",
            formatScheduleSummary(setOf("TUE", "THU", "SUN"), "09:15", "17:30")
        )
    }

    @Test
    fun fourDays_putsTimeOnSecondLine() {
        assertEquals(
            "Mon, Tue, Fri, Sat\n8:00 AM – 7:00 PM",
            formatScheduleSummary(setOf("MON", "TUE", "FRI", "SAT"), "08:00", "19:00")
        )
    }

    @Test
    fun moreThanFourDays_timePeriodOnSecondLine() {
        assertEquals(
            "Mon, Tue, Wed, Fri, Sat\n8:00 AM – 7:00 PM",
            formatScheduleSummary(setOf("MON", "TUE", "WED", "FRI", "SAT"), "08:00", "19:00")
        )
    }

    @Test
    fun sixDays_timePeriodOnSecondLine() {
        assertEquals(
            "Mon, Tue, Wed, Thu, Fri, Sat\n8:00 AM – 7:00 PM",
            formatScheduleSummary(setOf("MON", "TUE", "WED", "THU", "FRI", "SAT"), "08:00", "19:00")
        )
    }

    @Test
    fun allSevenDays_timePeriodOnSecondLine() {
        assertEquals(
            "Every day\n8:00 AM – 7:00 PM",
            formatScheduleSummary(setOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"), "08:00", "19:00")
        )
    }

    @Test
    fun noWorkDays_singleLine() {
        assertEquals(
            "No work days · 8:00 AM – 7:00 PM",
            formatScheduleSummary(emptySet(), "08:00", "19:00")
        )
    }
}
