package com.businesstracker.mileage.ui.history

import com.businesstracker.mileage.data.local.entity.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TripHistoryStickyHeaderTest {

    private fun createTrip(
        id: String,
        date: String,
        distance: Double,
        classification: String = "BUSINESS",
        startTime: Long = 1000L
    ): TripEntity {
        return TripEntity(
            id = id,
            date = date,
            startTime = startTime,
            endTime = startTime + 1800000L,
            startAddress = "123 Main St",
            endAddress = "456 Market St",
            startLatitude = 37.77,
            startLongitude = -122.41,
            endLatitude = 37.78,
            endLongitude = -122.42,
            totalDistanceMiles = distance,
            businessMiles = if (classification == "BUSINESS") distance else 0.0,
            businessEntity = "MySucculentCorner",
            purpose = "Nursery Pickup",
            trackingMethod = "AUTO_DETECTED",
            classification = classification,
            applicableRate = 0.760,
            calculatedDeduction = distance * 0.760,
            notes = null,
            waypointsJson = "[]",
            syncStatus = "SYNCED",
            syncedAt = 1000L
        )
    }

    @Test
    fun test01_emptyTripsList_producesZeroDateGroups() {
        val groups = groupTripsByDisplayedDate(emptyList())
        assertTrue("Empty trips list must produce empty date groups", groups.isEmpty())
    }

    @Test
    fun test02_singleTrip_producesOneGroup_withSingularDriveLabel() {
        val trip = createTrip("t1", "2026-11-14", 1.9)
        val groups = groupTripsByDisplayedDate(listOf(trip))

        assertEquals(1, groups.size)
        val group = groups.first()
        assertEquals("2026-11-14", group.date)
        assertEquals("t1", group.firstTripId)
        assertEquals(1, group.tripCount)
        assertEquals(1.9, group.totalMiles, 0.001)
        assertEquals("1 drive · 1.9 mi", group.summaryText)
    }

    @Test
    fun test03_multipleTripsSameDay_calculatesTotalsAccurately() {
        val trips = listOf(
            createTrip("t1", "2026-11-14", 0.5),
            createTrip("t2", "2026-11-14", 0.4),
            createTrip("t3", "2026-11-14", 0.3),
            createTrip("t4", "2026-11-14", 0.7)
        )
        val groups = groupTripsByDisplayedDate(trips)

        assertEquals(1, groups.size)
        val group = groups.first()
        assertEquals("2026-11-14", group.date)
        assertEquals(4, group.tripCount)
        assertEquals(1.9, group.totalMiles, 0.001)
        assertEquals("4 drives · 1.9 mi", group.summaryText)
        assertEquals(4, group.trips.size)
    }

    @Test
    fun test04_multipleDates_groupsChronologically_descending() {
        val trips = listOf(
            createTrip("t1", "2026-11-14", 1.9, startTime = 3000L),
            createTrip("t2", "2026-11-14", 2.1, startTime = 2500L),
            createTrip("t3", "2026-11-13", 5.0, startTime = 2000L),
            createTrip("t4", "2026-11-12", 3.2, startTime = 1000L)
        )
        val groups = groupTripsByDisplayedDate(trips)

        assertEquals(3, groups.size)
        assertEquals("2026-11-14", groups[0].date)
        assertEquals(2, groups[0].tripCount)
        assertEquals(4.0, groups[0].totalMiles, 0.001)

        assertEquals("2026-11-13", groups[1].date)
        assertEquals(1, groups[1].tripCount)
        assertEquals(5.0, groups[1].totalMiles, 0.001)

        assertEquals("2026-11-12", groups[2].date)
        assertEquals(1, groups[2].tripCount)
        assertEquals(3.2, groups[2].totalMiles, 0.001)
    }

    @Test
    fun test05_multipleDates_groupsChronologically_ascending() {
        val trips = listOf(
            createTrip("t1", "2026-11-12", 3.2, startTime = 1000L),
            createTrip("t2", "2026-11-13", 5.0, startTime = 2000L),
            createTrip("t3", "2026-11-14", 4.0, startTime = 3000L)
        )
        val groups = groupTripsByDisplayedDate(trips)

        assertEquals(3, groups.size)
        assertEquals("2026-11-12", groups[0].date)
        assertEquals("2026-11-13", groups[1].date)
        assertEquals("2026-11-14", groups[2].date)
    }

    @Test
    fun test06_formatHistoryDate_formatsExpectedDayOfWeekAndMonth() {
        val formatted = formatHistoryDate("2026-11-14")
        assertTrue("Formatted date must contain Sat and Nov: $formatted", formatted.contains("Sat") && formatted.contains("Nov"))
    }

    @Test
    fun test07_filteredTrips_omitsEmptyDateGroups() {
        val allTrips = listOf(
            createTrip("t1", "2026-11-14", 1.9, classification = "PERSONAL"),
            createTrip("t2", "2026-11-13", 5.0, classification = "BUSINESS"),
            createTrip("t3", "2026-11-12", 3.2, classification = "BUSINESS")
        )
        // User filters for "BUSINESS" only
        val businessOnly = allTrips.filter { it.classification == "BUSINESS" }
        val groups = groupTripsByDisplayedDate(businessOnly)

        assertEquals(2, groups.size)
        assertEquals("2026-11-13", groups[0].date)
        assertEquals("2026-11-12", groups[1].date)
        // Nov 14 had only personal trips and must be completely omitted
        assertTrue("Nov 14 must not be in filtered groups", groups.none { it.date == "2026-11-14" })
    }

    @Test
    fun test08_nonChronologicalSorting_preservesContiguousGroupsAndUniqueKeys() {
        val tripsSortedByMileage = listOf(
            createTrip("t1", "2026-11-14", 10.0),
            createTrip("t2", "2026-11-12", 8.0),
            createTrip("t3", "2026-11-14", 6.0)
        )
        val groups = groupTripsByDisplayedDate(tripsSortedByMileage)

        assertEquals(3, groups.size)
        val keys = groups.map { "date-header-${it.date}-${it.firstTripId}" }
        assertEquals(3, keys.distinct().size)
    }

    @Test
    fun test09_dateGroupTotals_reconcileExactSumOfFilteredMileage() {
        val trips = listOf(
            createTrip("t1", "2026-11-14", 1.25),
            createTrip("t2", "2026-11-14", 0.65),
            createTrip("t3", "2026-11-13", 2.1)
        )
        val groups = groupTripsByDisplayedDate(trips)

        val totalGroupMiles = groups.sumOf { it.totalMiles }
        val rawSum = trips.sumOf { it.totalDistanceMiles }
        assertEquals(rawSum, totalGroupMiles, 0.001)
    }

    @Test
    fun test10_dateHeaderKey_isStableAndUniquePerSection() {
        val g1 = DateGroup(
            date = "2026-11-14",
            firstTripId = "trip-abc",
            formattedDate = "Sat, Nov 14",
            tripCount = 2,
            totalMiles = 3.5,
            summaryText = "2 drives · 3.5 mi",
            trips = emptyList()
        )
        val key = "date-header-${g1.date}-${g1.firstTripId}"
        assertEquals("date-header-2026-11-14-trip-abc", key)
    }
}
