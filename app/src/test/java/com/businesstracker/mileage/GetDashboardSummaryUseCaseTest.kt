package com.businesstracker.mileage

import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.model.TimeFilter
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.usecase.GetDashboardSummaryUseCase
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class GetDashboardSummaryUseCaseTest {

    private lateinit var useCase: GetDashboardSummaryUseCase
    private val fixedReferenceDate = LocalDate.of(2026, 11, 6) // Friday

    @Test
    fun rawClassifiedMiles_preservePrecisionWithoutChangingExistingRoundedTotals() {
        val summary = GetDashboardSummaryUseCase().execute(
            listOf(
                createTrip("b", "2026-11-06", 0.004, TripClassification.BUSINESS),
                createTrip("p", "2026-11-06", 0.006, TripClassification.PERSONAL),
                createTrip("u", "2026-11-06", 10.0, TripClassification.UNREVIEWED)
            ), TimeFilter.TODAY, referenceDate = fixedReferenceDate
        )
        assertEquals(0.004, summary.rawBusinessMiles, 0.000001)
        assertEquals(0.006, summary.rawPersonalMiles, 0.000001)
        assertEquals(0.01, summary.rawBusinessMiles + summary.rawPersonalMiles, 0.000001)
        assertEquals(40.0, summary.rawBusinessMiles / (summary.rawBusinessMiles + summary.rawPersonalMiles) * 100, 0.000001)
        assertEquals(0.0, summary.businessMiles, 0.000001)
        assertEquals(0.01, summary.personalMiles, 0.000001)
        assertEquals(0.0, summary.estimatedDeduction, 0.000001)
        assertEquals(3, summary.totalTripsCount)
    }

    @Before
    fun setUp() {
        useCase = GetDashboardSummaryUseCase()
    }

    private fun createTrip(
        id: String,
        date: String,
        distance: Double,
        classification: TripClassification,
        businessMiles: Double = if (classification == TripClassification.BUSINESS) distance else 0.0,
        rate: Double = 0.760
    ): TripEntity {
        return TripEntity(
            id = id,
            date = date,
            startTime = 1000L,
            endTime = 2000L,
            startAddress = "123 Start St",
            endAddress = "456 End St",
            startLatitude = 37.7749,
            startLongitude = -122.4194,
            endLatitude = 37.7849,
            endLongitude = -122.4094,
            totalDistanceMiles = distance,
            businessMiles = businessMiles,
            businessEntity = if (classification == TripClassification.BUSINESS) "MySucculentCorner" else "Personal",
            purpose = if (classification == TripClassification.BUSINESS) "Nursery Pickup" else "Personal",
            trackingMethod = "AUTO_DETECTED",
            classification = classification.value,
            applicableRate = rate,
            calculatedDeduction = if (classification == TripClassification.BUSINESS) distance * rate else 0.0,
            notes = null,
            waypointsJson = "[]",
            syncStatus = "PENDING",
            syncedAt = null
        )
    }

    @Test
    fun execute_todayFilter_aggregatesOnlyTodayTrips() {
        val trips = listOf(
            createTrip("1", "2026-11-06", 10.0, TripClassification.BUSINESS), // today
            createTrip("2", "2026-11-06", 5.0, TripClassification.PERSONAL),   // today
            createTrip("3", "2026-11-05", 20.0, TripClassification.BUSINESS)  // yesterday
        )

        val summary = useCase.execute(
            trips = trips,
            timeFilter = TimeFilter.TODAY,
            referenceDate = fixedReferenceDate
        )

        assertEquals(15.0, summary.totalMiles, 0.01)
        assertEquals(10.0, summary.businessMiles, 0.01)
        assertEquals(5.0, summary.personalMiles, 0.01)
        assertEquals(0.0, summary.unreviewedMiles, 0.01)
        assertEquals(7.60, summary.estimatedDeduction, 0.01) // 10 * 0.76
        assertEquals(2, summary.totalTripsCount)
        assertEquals(0, summary.unreviewedTripsCount)
    }

    @Test
    fun execute_unreviewedTrips_excludedFromBusinessMilesAndDeductions() {
        val trips = listOf(
            createTrip("1", "2026-11-06", 10.0, TripClassification.BUSINESS),
            createTrip("2", "2026-11-06", 15.0, TripClassification.UNREVIEWED),
            createTrip("3", "2026-11-06", 8.0, TripClassification.PERSONAL)
        )

        val summary = useCase.execute(
            trips = trips,
            timeFilter = TimeFilter.TODAY,
            referenceDate = fixedReferenceDate
        )

        assertEquals(33.0, summary.totalMiles, 0.01)
        assertEquals(10.0, summary.businessMiles, 0.01)
        assertEquals(8.0, summary.personalMiles, 0.01)
        assertEquals(15.0, summary.unreviewedMiles, 0.01)
        assertEquals(7.60, summary.estimatedDeduction, 0.01)
        assertEquals(3, summary.totalTripsCount)
        assertEquals(1, summary.unreviewedTripsCount)
    }

    @Test
    fun execute_thisWeekFilter_aggregatesMondayThroughSunday() {
        // Reference is 2026-11-06 (Friday). Week is Monday 2026-11-02 to Sunday 2026-11-08.
        val trips = listOf(
            createTrip("1", "2026-11-02", 12.0, TripClassification.BUSINESS), // Monday (in)
            createTrip("2", "2026-11-04", 8.0, TripClassification.BUSINESS),  // Wednesday (in)
            createTrip("3", "2026-11-06", 10.0, TripClassification.PERSONAL),  // Friday (in)
            createTrip("4", "2026-11-01", 30.0, TripClassification.BUSINESS)  // Previous Sunday (out)
        )

        val summary = useCase.execute(
            trips = trips,
            timeFilter = TimeFilter.THIS_WEEK,
            referenceDate = fixedReferenceDate
        )

        assertEquals(30.0, summary.totalMiles, 0.01)
        assertEquals(20.0, summary.businessMiles, 0.01)
        assertEquals(10.0, summary.personalMiles, 0.01)
        assertEquals(3, summary.totalTripsCount)
    }

    @Test
    fun execute_customRangeFilter_correctlyBounded() {
        val trips = listOf(
            createTrip("1", "2026-10-15", 50.0, TripClassification.BUSINESS),
            createTrip("2", "2026-11-01", 25.0, TripClassification.BUSINESS),
            createTrip("3", "2026-11-10", 30.0, TripClassification.BUSINESS),
            createTrip("4", "2026-12-01", 40.0, TripClassification.BUSINESS)
        )

        val summary = useCase.execute(
            trips = trips,
            timeFilter = TimeFilter.CUSTOM,
            customStartDate = "2026-11-01",
            customEndDate = "2026-11-30",
            referenceDate = fixedReferenceDate
        )

        assertEquals(55.0, summary.totalMiles, 0.01)
        assertEquals(55.0, summary.businessMiles, 0.01)
        assertEquals(2, summary.totalTripsCount)
    }

    @Test
    fun execute_thisMonthAndThisYear_aggregatesCorrectly() {
        val trips = listOf(
            createTrip("1", "2026-11-02", 15.0, TripClassification.BUSINESS), // Nov 2026 (this month + this year)
            createTrip("2", "2026-10-15", 25.0, TripClassification.BUSINESS), // Oct 2026 (this year, not this month)
            createTrip("3", "2025-11-02", 50.0, TripClassification.BUSINESS)  // Nov 2025 (different year)
        )

        val monthSummary = useCase.execute(
            trips = trips,
            timeFilter = TimeFilter.THIS_MONTH,
            referenceDate = fixedReferenceDate
        )
        assertEquals(15.0, monthSummary.totalMiles, 0.01)
        assertEquals(1, monthSummary.totalTripsCount)

        val yearSummary = useCase.execute(
            trips = trips,
            timeFilter = TimeFilter.THIS_YEAR,
            referenceDate = fixedReferenceDate
        )
        assertEquals(40.0, yearSummary.totalMiles, 0.01)
        assertEquals(2, yearSummary.totalTripsCount)
    }

    @Test
    fun execute_customRangeSingleDay_aggregatesExactDay() {
        val trips = listOf(
            createTrip("1", "2026-11-05", 10.0, TripClassification.BUSINESS),
            createTrip("2", "2026-11-06", 20.0, TripClassification.BUSINESS),
            createTrip("3", "2026-11-07", 30.0, TripClassification.BUSINESS)
        )

        val summary = useCase.execute(
            trips = trips,
            timeFilter = TimeFilter.CUSTOM,
            customStartDate = "2026-11-06",
            customEndDate = "2026-11-06",
            referenceDate = fixedReferenceDate
        )

        assertEquals(20.0, summary.totalMiles, 0.01)
        assertEquals(1, summary.totalTripsCount)
    }

    @Test
    fun execute_emptyList_returnsZeroesWithoutCrashing() {
        val summary = useCase.execute(
            trips = emptyList(),
            timeFilter = TimeFilter.THIS_MONTH,
            referenceDate = fixedReferenceDate
        )

        assertEquals(0.0, summary.totalMiles, 0.001)
        assertEquals(0.0, summary.businessMiles, 0.001)
        assertEquals(0.0, summary.personalMiles, 0.001)
        assertEquals(0.0, summary.unreviewedMiles, 0.001)
        assertEquals(0.0, summary.estimatedDeduction, 0.001)
        assertEquals(0, summary.totalTripsCount)
        assertEquals(0, summary.unreviewedTripsCount)
    }

    @Test
    fun timeFilter_toDateRange_matchesFilterTripsByTime() {
        val refDate = LocalDate.of(2026, 11, 13) // Friday
        val trips = listOf(
            createTrip("1", "2026-11-13", 10.0, TripClassification.BUSINESS), // Today & This Week
            createTrip("2", "2026-11-09", 15.0, TripClassification.BUSINESS), // Monday this week
            createTrip("3", "2026-11-01", 20.0, TripClassification.PERSONAL), // This month
            createTrip("4", "2026-05-15", 30.0, TripClassification.BUSINESS), // This year
            createTrip("5", "2025-12-31", 50.0, TripClassification.BUSINESS)  // Prior year
        )

        // TODAY
        val (todayStart, todayEnd) = TimeFilter.TODAY.toDateRange(referenceDate = refDate)
        assertEquals("2026-11-13", todayStart)
        assertEquals("2026-11-13", todayEnd)
        val todayTrips = useCase.filterTripsByTime(trips, TimeFilter.TODAY, referenceDate = refDate)
        assertEquals(listOf("1"), todayTrips.map { it.id })

        // THIS_WEEK (Mon Nov 9 to Sun Nov 15)
        val (weekStart, weekEnd) = TimeFilter.THIS_WEEK.toDateRange(referenceDate = refDate)
        assertEquals("2026-11-09", weekStart)
        assertEquals("2026-11-15", weekEnd)
        val weekTrips = useCase.filterTripsByTime(trips, TimeFilter.THIS_WEEK, referenceDate = refDate)
        assertEquals(listOf("1", "2"), weekTrips.map { it.id })

        // THIS_MONTH (Nov 1 to Nov 30)
        val (monthStart, monthEnd) = TimeFilter.THIS_MONTH.toDateRange(referenceDate = refDate)
        assertEquals("2026-11-01", monthStart)
        assertEquals("2026-11-30", monthEnd)
        val monthTrips = useCase.filterTripsByTime(trips, TimeFilter.THIS_MONTH, referenceDate = refDate)
        assertEquals(listOf("1", "2", "3"), monthTrips.map { it.id })

        // THIS_YEAR (Jan 1 to Dec 31)
        val (yearStart, yearEnd) = TimeFilter.THIS_YEAR.toDateRange(referenceDate = refDate)
        assertEquals("2026-01-01", yearStart)
        assertEquals("2026-12-31", yearEnd)
        val yearTrips = useCase.filterTripsByTime(trips, TimeFilter.THIS_YEAR, referenceDate = refDate)
        assertEquals(listOf("1", "2", "3", "4"), yearTrips.map { it.id })
    }
}
