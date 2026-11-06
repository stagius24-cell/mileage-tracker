package com.businesstracker.mileage.domain.usecase

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class CalculateMileageRateUseCase @Inject constructor() {

    companion object {
        const val RATE_PRE_JULY_2026 = 0.725
        const val RATE_POST_JULY_2026 = 0.760
        val MID_YEAR_SPLIT_DATE: LocalDate = LocalDate.of(2026, 7, 1)
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    /**
     * Calculates the applicable IRS business mileage rate based on the trip date.
     * Rate: $0.725 for dates < 2026-07-01; $0.760 for dates >= 2026-07-01.
     *
     * @param dateString ISO formatted date: "YYYY-MM-DD"
     */
    fun getApplicableRate(dateString: String): Double {
        return try {
            val tripDate = LocalDate.parse(dateString, DATE_FORMATTER)
            if (tripDate.isBefore(MID_YEAR_SPLIT_DATE)) {
                RATE_PRE_JULY_2026
            } else {
                RATE_POST_JULY_2026
            }
        } catch (_: Exception) {
            // Default to pre-July 2026 rate if parsing fails
            RATE_PRE_JULY_2026
        }
    }

    /**
     * Calculates the applicable rate from epoch millisecond timestamp.
     */
    fun getApplicableRate(timestamp: Long): Double {
        val dateString = getFormattedDate(timestamp)
        return getApplicableRate(dateString)
    }

    /**
     * Calculates tax deduction for given business miles and rate.
     */
    fun calculateDeduction(businessMiles: Double, rate: Double): Double {
        return (businessMiles * rate).roundToDecimals(2)
    }

    fun getFormattedDate(timestamp: Long): String {
        val instant = java.time.Instant.ofEpochMilli(timestamp)
        val zoneId = java.time.ZoneId.systemDefault()
        return instant.atZone(zoneId).toLocalDate().format(DATE_FORMATTER)
    }

    private fun Double.roundToDecimals(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }
}
