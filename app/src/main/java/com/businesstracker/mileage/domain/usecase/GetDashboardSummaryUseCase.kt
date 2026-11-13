package com.businesstracker.mileage.domain.usecase

import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.model.DashboardSummary
import com.businesstracker.mileage.domain.model.TimeFilter
import com.businesstracker.mileage.domain.model.TripClassification
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

class GetDashboardSummaryUseCase @Inject constructor() {

    fun execute(
        trips: List<TripEntity>,
        timeFilter: TimeFilter,
        customStartDate: String? = null,
        customEndDate: String? = null,
        referenceDate: LocalDate = LocalDate.now()
    ): DashboardSummary {
        val filtered = filterTripsByTime(trips, timeFilter, customStartDate, customEndDate, referenceDate)

        val businessTrips = filtered.filter { it.classification == TripClassification.BUSINESS.value }
        val personalTrips = filtered.filter { it.classification == TripClassification.PERSONAL.value }
        val unreviewedTrips = filtered.filter { it.classification == TripClassification.UNREVIEWED.value }

        // All unreviewed trips across all time requiring user action
        val totalUnreviewedCount = trips.count { it.classification == TripClassification.UNREVIEWED.value }

        val totalMiles = round(filtered.sumOf { it.totalDistanceMiles })
        val businessMiles = round(businessTrips.sumOf { it.businessMiles })
        val personalMiles = round(personalTrips.sumOf { it.totalDistanceMiles })
        val unreviewedMiles = round(unreviewedTrips.sumOf { it.totalDistanceMiles })
        val estimatedDeduction = round(businessTrips.sumOf { it.calculatedDeduction })

        return DashboardSummary(
            totalMiles = totalMiles,
            businessMiles = businessMiles,
            personalMiles = personalMiles,
            unreviewedMiles = unreviewedMiles,
            estimatedDeduction = estimatedDeduction,
            totalTripsCount = filtered.size,
            unreviewedTripsCount = totalUnreviewedCount,
            rawBusinessMiles = businessTrips.sumOf { it.businessMiles },
            rawPersonalMiles = personalTrips.sumOf { it.totalDistanceMiles }
        )
    }

    fun filterTripsByTime(
        trips: List<TripEntity>,
        timeFilter: TimeFilter,
        customStartDate: String? = null,
        customEndDate: String? = null,
        referenceDate: LocalDate = LocalDate.now()
    ): List<TripEntity> {
        val todayStr = referenceDate.toString()
        val yearStr = referenceDate.year.toString()
        val monthPrefix = String.format(java.util.Locale.US, "%04d-%02d", referenceDate.year, referenceDate.monthValue)

        val monday = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        val sunday = referenceDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toString()

        return trips.filter { trip ->
            when (timeFilter) {
                TimeFilter.TODAY -> trip.date == todayStr
                TimeFilter.THIS_WEEK -> trip.date in monday..sunday
                TimeFilter.THIS_MONTH -> trip.date.startsWith(monthPrefix)
                TimeFilter.THIS_YEAR -> trip.date.startsWith(yearStr)
                TimeFilter.CUSTOM -> {
                    val afterStart = customStartDate.isNullOrBlank() || trip.date >= customStartDate
                    val beforeEnd = customEndDate.isNullOrBlank() || trip.date <= customEndDate
                    afterStart && beforeEnd
                }
            }
        }
    }

    private fun round(value: Double): Double {
        return kotlin.math.round(value * 100.0) / 100.0
    }
}
