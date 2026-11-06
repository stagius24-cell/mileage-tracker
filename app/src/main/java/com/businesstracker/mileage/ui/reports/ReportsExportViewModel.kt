package com.businesstracker.mileage.ui.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class MonthSummary(
    val monthIso: String,          // e.g. "2026-11"
    val monthName: String,         // e.g. "November"
    val year: String,              // e.g. "2026"
    val totalDrives: Int,
    val totalMiles: Double,
    val businessDrives: Int,
    val businessMiles: Double,
    val loggedDeduction: Double,   // actual business deduction logged
    val potentialDeduction: Double,// potential deduction (business + unreviewed)
    val percentComplete: Int,      // % of total miles classified as business
    val unreviewedDrives: Int
)

data class EntityBreakdown(
    val entityName: String,
    val tripCount: Int,
    val totalMiles: Double,
    val businessMiles: Double,
    val totalDeduction: Double
)

data class PurposeBreakdown(
    val purpose: String,
    val count: Int,
    val totalMiles: Double
)

data class ReportsUiState(
    val selectedYear: String = "2026",
    val availableYears: List<String> = listOf("2026"),
    val selectedBusinessEntity: String = "ALL", // "ALL", "MySucculentCorner", "Personal"
    val selectedClassification: String = "ALL",   // "ALL", "BUSINESS", "PERSONAL", "UNREVIEWED"
    val customStartDate: String = "",
    val customEndDate: String = "",
    val monthlySummaries: List<MonthSummary> = emptyList(),
    val filteredTrips: List<TripEntity> = emptyList(),
    // Summary metrics
    val totalTrips: Int = 0,
    val totalMiles: Double = 0.0,
    val businessMiles: Double = 0.0,
    val personalMiles: Double = 0.0,
    val unreviewedMiles: Double = 0.0,
    val totalDeduction: Double = 0.0,
    val averageDistance: Double = 0.0,
    val entityBreakdown: List<EntityBreakdown> = emptyList(),
    val purposeBreakdown: List<PurposeBreakdown> = emptyList(),
    val isExporting: Boolean = false
)

@HiltViewModel
class ReportsExportViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val rateUseCase: CalculateMileageRateUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val currentYear = LocalDate.now().year.toString()
    private val _selectedYear = MutableStateFlow(currentYear)
    private val _selectedBusinessEntity = MutableStateFlow("ALL")
    private val _selectedClassification = MutableStateFlow("ALL")
    private val _customStartDate = MutableStateFlow("")
    private val _customEndDate = MutableStateFlow("")
    private val _isExporting = MutableStateFlow(false)

    val uiState: StateFlow<ReportsUiState> = combine(
        tripRepository.getAllTrips(),
        _selectedYear,
        _selectedBusinessEntity,
        _selectedClassification
    ) { trips, year, entity, classification ->
        FilterArgs(trips, year, entity, classification)
    }.combine(
        combine(_customStartDate, _customEndDate, _isExporting) { start, end, exporting ->
            DateArgs(start, end, exporting)
        }
    ) { filterArgs, dateArgs ->
        val trips = filterArgs.trips
        val year = filterArgs.year
        val entity = filterArgs.entity
        val classification = filterArgs.classification
        val start = dateArgs.start
        val end = dateArgs.end

        // Apply filters
        val filtered = trips.filter { trip ->
            val matchesYear = year == "ALL" || trip.date.startsWith(year)
            val matchesEntity = entity == "ALL" || trip.businessEntity.equals(entity, ignoreCase = true)
            val matchesClassification = classification == "ALL" || trip.classification.equals(classification, ignoreCase = true)
            val matchesStartDate = start.isBlank() || trip.date >= start
            val matchesEndDate = end.isBlank() || trip.date <= end

            matchesYear && matchesEntity && matchesClassification && matchesStartDate && matchesEndDate
        }

        val businessTrips = filtered.filter { it.classification == "BUSINESS" }
        val personalTrips = filtered.filter { it.classification == "PERSONAL" }
        val unreviewedTrips = filtered.filter { it.classification == "UNREVIEWED" }

        val totalMiles = round(filtered.sumOf { it.totalDistanceMiles })
        val bMiles = round(businessTrips.sumOf { it.businessMiles })
        val pMiles = round(personalTrips.sumOf { it.totalDistanceMiles })
        val uMiles = round(unreviewedTrips.sumOf { it.totalDistanceMiles })
        val totalDeduction = round(businessTrips.sumOf { it.calculatedDeduction })
        val averageDist = if (filtered.isNotEmpty()) round(totalMiles / filtered.size) else 0.0

        // Entity Breakdown
        val entityGroups = filtered.groupBy { it.businessEntity }
        val entityBreakdowns = entityGroups.map { (name, grpTrips) ->
            val bGrp = grpTrips.filter { it.classification == "BUSINESS" }
            EntityBreakdown(
                entityName = name,
                tripCount = grpTrips.size,
                totalMiles = round(grpTrips.sumOf { it.totalDistanceMiles }),
                businessMiles = round(bGrp.sumOf { it.businessMiles }),
                totalDeduction = round(bGrp.sumOf { it.calculatedDeduction })
            )
        }.sortedByDescending { it.businessMiles }

        // Purpose Breakdown
        val purposeGroups = filtered.groupBy { it.purpose }
        val purposeBreakdowns = purposeGroups.map { (p, grpTrips) ->
            PurposeBreakdown(
                purpose = p,
                count = grpTrips.size,
                totalMiles = round(grpTrips.sumOf { it.totalDistanceMiles })
            )
        }.sortedByDescending { it.totalMiles }

        // Monthly Summaries (for the selected year)
        val yearTrips = trips.filter { year == "ALL" || it.date.startsWith(year) }
        val monthsMap = mutableMapOf<String, MutableList<TripEntity>>()
        for (i in 1..12) {
            val yrPrefix = if (year == "ALL") currentYear else year
            val monthKey = String.format(Locale.US, "%s-%02d", yrPrefix, i)
            monthsMap[monthKey] = mutableListOf()
        }
        for (trip in yearTrips) {
            if (trip.date.length >= 7) {
                val key = trip.date.substring(0, 7)
                monthsMap.getOrPut(key) { mutableListOf() }.add(trip)
            }
        }

        val summaries = monthsMap.keys.sortedDescending().mapNotNull { monthIso ->
            val mTrips = monthsMap[monthIso].orEmpty()
            val mBusiness = mTrips.filter { it.classification == "BUSINESS" }
            val mUnreviewed = mTrips.filter { it.classification == "UNREVIEWED" }

            val totalDrives = mTrips.size
            val mTotalMiles = mTrips.sumOf { it.totalDistanceMiles }
            val mBMiles = mBusiness.sumOf { it.businessMiles }
            val logged = mBusiness.sumOf { it.calculatedDeduction }
            val unreviewedPotential = mUnreviewed.sumOf { it.totalDistanceMiles * it.applicableRate }
            val potential = logged + unreviewedPotential

            val percent = if (potential > 0.0) {
                ((logged / potential) * 100).toInt().coerceIn(0, 100)
            } else if (mTotalMiles > 0) {
                0
            } else {
                100
            }

            val monthDate = try {
                LocalDate.parse("$monthIso-01")
            } catch (_: Exception) {
                LocalDate.now()
            }
            val monthName = monthDate.month.getDisplayName(TextStyle.FULL, Locale.getDefault())

            val curMonthIso = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
            if (totalDrives > 0 || monthIso <= curMonthIso) {
                MonthSummary(
                    monthIso = monthIso,
                    monthName = monthName,
                    year = year,
                    totalDrives = totalDrives,
                    totalMiles = mTotalMiles,
                    businessDrives = mBusiness.size,
                    businessMiles = mBMiles,
                    loggedDeduction = logged,
                    potentialDeduction = potential,
                    percentComplete = percent,
                    unreviewedDrives = mUnreviewed.size
                )
            } else null
        }

        val availableYears = trips.mapNotNull {
            if (it.date.length >= 4) it.date.substring(0, 4) else null
        }.distinct().sortedDescending().ifEmpty { listOf(currentYear) }

        ReportsUiState(
            selectedYear = year,
            availableYears = availableYears,
            selectedBusinessEntity = entity,
            selectedClassification = classification,
            customStartDate = start,
            customEndDate = end,
            monthlySummaries = summaries,
            filteredTrips = filtered,
            totalTrips = filtered.size,
            totalMiles = totalMiles,
            businessMiles = bMiles,
            personalMiles = pMiles,
            unreviewedMiles = uMiles,
            totalDeduction = totalDeduction,
            averageDistance = averageDist,
            entityBreakdown = entityBreakdowns,
            purposeBreakdown = purposeBreakdowns,
            isExporting = dateArgs.exporting
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportsUiState()
    )

    fun selectYear(year: String) {
        _selectedYear.value = year
    }

    fun selectBusinessEntity(entity: String) {
        _selectedBusinessEntity.value = entity
    }

    fun selectClassification(classification: String) {
        _selectedClassification.value = classification
    }

    fun setCustomDateRange(start: String, end: String) {
        _customStartDate.value = start
        _customEndDate.value = end
    }

    fun exportToCsv(onFileReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val state = uiState.value
            val tripsToExport = state.filteredTrips

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val suffix = "${state.selectedYear}_${state.selectedBusinessEntity}"
            val csvFile = File(exportDir, "BusinessMileage_${suffix}_${System.currentTimeMillis()}.csv")

            FileWriter(csvFile).use { writer ->
                writer.append("Trip ID,Date,Start Time,End Time,Start Address,End Address,Total Miles,Business Miles,Classification,Business Entity,Purpose,Mileage Rate,Estimated Deduction,Notes,Tracking Method,Sync Status\n")

                val timeFmt = java.text.SimpleDateFormat("h:mm a", Locale.US)

                for (trip in tripsToExport) {
                    val startStr = if (trip.startTime > 0) timeFmt.format(java.util.Date(trip.startTime)) else ""
                    val endStr = if (trip.endTime > 0) timeFmt.format(java.util.Date(trip.endTime)) else ""

                    writer.append("\"${trip.id}\",")
                    writer.append("\"${trip.date}\",")
                    writer.append("\"$startStr\",")
                    writer.append("\"$endStr\",")
                    writer.append("\"${escapeCsv(trip.startAddress.orEmpty())}\",")
                    writer.append("\"${escapeCsv(trip.endAddress.orEmpty())}\",")
                    writer.append("${trip.totalDistanceMiles},")
                    writer.append("${trip.businessMiles},")
                    writer.append("\"${trip.classification}\",")
                    writer.append("\"${trip.businessEntity}\",")
                    writer.append("\"${escapeCsv(trip.purpose)}\",")
                    writer.append("${trip.applicableRate},")
                    writer.append("${trip.calculatedDeduction},")
                    writer.append("\"${escapeCsv(trip.notes.orEmpty())}\",")
                    writer.append("\"${trip.trackingMethod}\",")
                    writer.append("\"${trip.syncStatus}\"\n")
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Business Mileage Report ($suffix)")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            onFileReady(Intent.createChooser(shareIntent, "Share Mileage CSV").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun exportToPdf(onFileReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val state = uiState.value
            val summaryData = ReportSummaryData(
                title = "Mileage Tax Deduction Report",
                dateRangeLabel = if (state.customStartDate.isNotBlank() || state.customEndDate.isNotBlank()) {
                    "${state.customStartDate} to ${state.customEndDate}"
                } else {
                    "${state.selectedYear} Tax Year"
                },
                businessEntity = state.selectedBusinessEntity,
                totalTrips = state.totalTrips,
                totalMiles = state.totalMiles,
                businessMiles = state.businessMiles,
                personalMiles = state.personalMiles,
                unreviewedMiles = state.unreviewedMiles,
                totalDeduction = state.totalDeduction,
                averageDistance = state.averageDistance,
                trips = state.filteredTrips
            )

            val pdfFile = PdfReportGenerator.generatePdfReport(context, summaryData)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_SUBJECT, "Business Mileage PDF Report")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            onFileReady(Intent.createChooser(shareIntent, "Share Mileage PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun escapeCsv(value: String): String = value.replace("\"", "\"\"")

    private fun round(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0

    private data class FilterArgs(
        val trips: List<TripEntity>,
        val year: String,
        val entity: String,
        val classification: String
    )

    private data class DateArgs(
        val start: String,
        val end: String,
        val exporting: Boolean
    )
}
