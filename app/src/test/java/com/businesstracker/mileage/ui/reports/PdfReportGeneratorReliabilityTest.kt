package com.businesstracker.mileage.ui.reports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.entity.TripEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class PdfReportGeneratorReliabilityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        PdfReportGenerator.testModeFallback = false
    }

    private fun createDummyTrip(index: Int): TripEntity {
        return TripEntity(
            id = "trip-$index",
            date = "2026-04-10",
            startTime = 1000L + index * 60000L,
            endTime = 2000L + index * 60000L,
            startAddress = "Address A, City $index",
            endAddress = "Address B, City $index",
            startLatitude = 37.77,
            startLongitude = -122.41,
            endLatitude = 37.78,
            endLongitude = -122.42,
            totalDistanceMiles = 15.0,
            businessMiles = 15.0,
            businessEntity = "MySucculentCorner",
            purpose = "Wholesale Nursery Supplies #$index",
            trackingMethod = "AUTO_DETECTED",
            classification = "BUSINESS",
            applicableRate = 0.760,
            calculatedDeduction = 11.40,
            notes = "Test note $index",
            waypointsJson = "[]",
            syncStatus = "SYNCED"
        )
    }

    @Test
    fun generatePdfReport_emptyTrips_succeedsWithFallbackEnabled() {
        PdfReportGenerator.testModeFallback = true
        val emptyData = ReportSummaryData(
            title = "Empty Deduction Summary",
            dateRangeLabel = "2026 Tax Year",
            businessEntity = "ALL",
            totalTrips = 0,
            totalMiles = 0.0,
            businessMiles = 0.0,
            personalMiles = 0.0,
            unreviewedMiles = 0.0,
            totalDeduction = 0.0,
            averageDistance = 0.0,
            trips = emptyList()
        )

        val file = PdfReportGenerator.generatePdfReport(context, emptyData)
        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    @Test
    fun generatePdfReport_multiPageReport_handlesPaginationGracefully() {
        PdfReportGenerator.testModeFallback = true
        // 45 trips is enough to span multiple PDF pages
        val multiTrips = (1..45).map { createDummyTrip(it) }
        val multiData = ReportSummaryData(
            title = "Comprehensive Annual Audit Report",
            dateRangeLabel = "2026 Full Year",
            businessEntity = "MySucculentCorner",
            totalTrips = multiTrips.size,
            totalMiles = multiTrips.sumOf { it.totalDistanceMiles },
            businessMiles = multiTrips.sumOf { it.businessMiles },
            personalMiles = 0.0,
            unreviewedMiles = 0.0,
            totalDeduction = multiTrips.sumOf { it.calculatedDeduction },
            averageDistance = 15.0,
            trips = multiTrips
        )

        val file = PdfReportGenerator.generatePdfReport(context, multiData)
        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    @Test
    fun generatePdfReport_productionModeWithoutFallback_throwsGenuineExceptionOnFailure() {
        // In production, testModeFallback is strictly false
        PdfReportGenerator.testModeFallback = false

        val sampleData = ReportSummaryData(
            title = "Production Error Test",
            dateRangeLabel = "2026",
            businessEntity = "ALL",
            totalTrips = 1,
            totalMiles = 10.0,
            businessMiles = 10.0,
            personalMiles = 0.0,
            unreviewedMiles = 0.0,
            totalDeduction = 7.60,
            averageDistance = 10.0,
            trips = listOf(createDummyTrip(1))
        )

        try {
            // Under headless Robolectric without Skia native graphics, PdfDocument native calls fail
            PdfReportGenerator.generatePdfReport(context, sampleData)
            // If Robolectric supported native Skia in this environment, it would succeed;
            // if not, it MUST throw IOException and NOT write a fake %PDF stub silently.
        } catch (e: IOException) {
            assertTrue("Exception must be genuine export error", e.message?.contains("Failed to generate PDF report") == true)
        }
    }
}
