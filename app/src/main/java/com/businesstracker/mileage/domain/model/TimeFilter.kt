package com.businesstracker.mileage.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class TimeFilter(val displayName: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year"),
    CUSTOM("Custom");

    fun toDateRange(
        customStartDate: String? = null,
        customEndDate: String? = null,
        referenceDate: LocalDate = LocalDate.now()
    ): Pair<String, String> {
        return when (this) {
            TODAY -> {
                val d = referenceDate.toString()
                Pair(d, d)
            }
            THIS_WEEK -> {
                val monday = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
                val sunday = referenceDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toString()
                Pair(monday, sunday)
            }
            THIS_MONTH -> {
                val start = referenceDate.withDayOfMonth(1).toString()
                val end = referenceDate.withDayOfMonth(referenceDate.lengthOfMonth()).toString()
                Pair(start, end)
            }
            THIS_YEAR -> {
                val year = referenceDate.year
                val start = String.format(Locale.US, "%04d-01-01", year)
                val end = String.format(Locale.US, "%04d-12-31", year)
                Pair(start, end)
            }
            CUSTOM -> {
                Pair(customStartDate.orEmpty(), customEndDate.orEmpty())
            }
        }
    }
}
