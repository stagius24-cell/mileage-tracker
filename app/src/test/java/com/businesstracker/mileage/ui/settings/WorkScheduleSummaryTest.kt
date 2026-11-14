package com.businesstracker.mileage.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkScheduleSummaryTest {
    @Test
    fun weekdaySchedule_usesSavedDaysAndTimes() {
        assertEquals(
            "Mon–Fri · 8:00 AM – 6:00 PM",
            formatScheduleSummary(
                setOf("MON", "TUE", "WED", "THU", "FRI"),
                "08:00", "18:00"
            )
        )
    }

    @Test
    fun individualDays_keepTheirOwnIdentities() {
        assertEquals(
            "Tue, Thu, Sun · 9:15 AM – 5:30 PM",
            formatScheduleSummary(setOf("TUE", "THU", "SUN"), "09:15", "17:30")
        )
    }
}
