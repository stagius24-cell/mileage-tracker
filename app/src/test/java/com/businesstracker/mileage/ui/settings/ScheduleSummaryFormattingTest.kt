package com.businesstracker.mileage.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleSummaryFormattingTest {

    @Test
    fun testFormatScheduleSummary_singleDay_isInlineWithDot() {
        val result = formatScheduleSummary(setOf("MON"), "08:00", "17:00")
        assertEquals("Mon · 8:00 AM – 5:00 PM", result)
    }

    @Test
    fun testFormatScheduleSummary_twoDays_putsTimeOnSecondLine() {
        val result = formatScheduleSummary(setOf("FRI", "SAT"), "08:00", "19:00")
        assertEquals("Fri, Sat\n8:00 AM – 7:00 PM", result)
    }

    @Test
    fun testFormatScheduleSummary_weekdays_putsTimeOnSecondLine() {
        val result = formatScheduleSummary(setOf("MON", "TUE", "WED", "THU", "FRI"), "09:00", "17:00")
        assertEquals("Mon–Fri\n9:00 AM – 5:00 PM", result)
    }

    @Test
    fun testFormatScheduleSummary_allDays_putsTimeOnSecondLine() {
        val result = formatScheduleSummary(
            setOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"),
            "08:00",
            "18:00"
        )
        assertEquals("Every day\n8:00 AM – 6:00 PM", result)
    }

    @Test
    fun testFormatScheduleSummary_noDays_isInline() {
        val result = formatScheduleSummary(emptySet(), "08:00", "17:00")
        assertEquals("No work days · 8:00 AM – 5:00 PM", result)
    }
}
