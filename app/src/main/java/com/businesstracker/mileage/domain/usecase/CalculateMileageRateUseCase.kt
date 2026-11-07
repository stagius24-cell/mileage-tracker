package com.businesstracker.mileage.domain.usecase

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class MileageRateRecord(
    val year: Int,
    val effectiveDate: String,
    val rate: Double,
    val description: String
)

class CalculateMileageRateUseCase @Inject constructor() {

    companion object {
        const val RATE_2023 = 0.655
        const val RATE_2024 = 0.670
        const val RATE_2025 = 0.700
        const val RATE_PRE_JULY_2026 = 0.725
        const val RATE_POST_JULY_2026 = 0.760

        val MID_YEAR_SPLIT_DATE_2026: LocalDate = LocalDate.of(2026, 7, 1)
        val MID_YEAR_SPLIT_DATE: LocalDate = MID_YEAR_SPLIT_DATE_2026
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        private val HISTORICAL_RATES = listOf(
            MileageRateRecord(2023, "2023-01-01", RATE_2023, "2023 Standard Business Rate ($0.655/mi)"),
            MileageRateRecord(2024, "2024-01-01", RATE_2024, "2024 Standard Business Rate ($0.670/mi)"),
            MileageRateRecord(2025, "2025-01-01", RATE_2025, "2025 Standard Business Rate ($0.700/mi)"),
            MileageRateRecord(2026, "2026-01-01", RATE_PRE_JULY_2026, "2026 H1 Rate ($0.725/mi)"),
            MileageRateRecord(2026, "2026-07-01", RATE_POST_JULY_2026, "2026 H2 Rate ($0.760/mi)")
        )
    }

    /**
     * Calculates the applicable IRS business mileage rate based on the trip date.
     * Supports historical tax years:
     * - 2023: $0.655
     * - 2024: $0.670
     * - 2025: $0.700
     * - 2026 (< 2026-07-01): $0.725
     * - 2026 (>= 2026-07-01): $0.760
     * - Post-2026: Defaults to $0.760
     *
     * @param dateString ISO formatted date: "YYYY-MM-DD"
     */
    fun getApplicableRate(dateString: String): Double {
        return try {
            val tripDate = LocalDate.parse(dateString, DATE_FORMATTER)
            when {
                tripDate.year < 2024 -> RATE_2023
                tripDate.year == 2024 -> RATE_2024
                tripDate.year == 2025 -> RATE_2025
                tripDate.year == 2026 -> {
                    if (tripDate.isBefore(MID_YEAR_SPLIT_DATE_2026)) {
                        RATE_PRE_JULY_2026
                    } else {
                        RATE_POST_JULY_2026
                    }
                }
                else -> RATE_POST_JULY_2026
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

    /**
     * Returns the list of standard IRS historical rates.
     */
    fun getHistoricalRates(): List<MileageRateRecord> = HISTORICAL_RATES

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
