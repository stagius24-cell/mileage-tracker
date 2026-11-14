package com.businesstracker.mileage.ui.reports

import org.junit.Assert.assertEquals
import org.junit.Test

class MileageChartMathTest {
    private val month = MonthSummary("2026-11", "November", "2026", 28, 261.6,
        10, 44.7, 216.9, 33.99, 33.99, 100, 0)

    @Test fun categoryVisibilityUsesExistingMonthlyMiles() {
        assertEquals(261.6, visibleMiles(month, true, true), 0.0001)
        assertEquals(44.7, visibleMiles(month, true, false), 0.0001)
        assertEquals(216.9, visibleMiles(month, false, true), 0.0001)
        assertEquals(0.0, visibleMiles(month, false, false), 0.0)
    }

    @Test fun axisAndInvalidMilesStayFinite() {
        assertEquals(300.0, mileageAxisMaximum(261.6), 0.0)
        assertEquals(1.0, mileageAxisMaximum(0.0), 0.0)
        assertEquals(0.0, safeMiles(Double.NaN), 0.0)
        assertEquals(0.0, safeMiles(Double.POSITIVE_INFINITY), 0.0)
        assertEquals(0.0, safeMiles(-2.0), 0.0)
    }
}
