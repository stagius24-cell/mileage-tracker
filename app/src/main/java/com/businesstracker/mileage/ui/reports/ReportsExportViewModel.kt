package com.businesstracker.mileage.ui.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.entity.VehicleEntity
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.domain.repository.VehicleRepository
import com.businesstracker.mileage.domain.tracking.VehicleFriendlyNameResolver
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
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
    val personalMiles: Double,
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

data class VehicleReportSummary(
    val vehicleId: String?,
    val vehicleName: String,
    val businessMiles: Double,
    val personalMiles: Double,
    val tripCount: Int,
    val estimatedDeduction: Double
)

data class SavedReportItem(
    val file: File,
    val name: String,
    val period: String,
    val createdDate: String,
    val format: String
)

data class ReportsUiState(
    val selectedYear: String = "2026",
    val selectedMonths: Set<String> = emptySet(),
    val selectedPeriodLabel: String = "2026",
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
    val isExporting: Boolean = false,
    val isGeneratingPdf: Boolean = false,
    val exportErrorMessage: String? = null,
    val ytdLoggedDeduction: Double = 0.0,
    val ytdPotentialDeduction: Double = 0.0,
    val ytdBusinessMiles: Double = 0.0,
    val ytdTotalMiles: Double = 0.0,
    val unclassifiedCount: Int = 0,
    val incompleteCount: Int = 0,
    val missingPurposeCount: Int = 0,
    val vehicleSummaries: List<VehicleReportSummary> = emptyList(),
    val savedReports: List<SavedReportItem> = emptyList()
)

@HiltViewModel
class ReportsExportViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val rateUseCase: CalculateMileageRateUseCase,
    @ApplicationContext private val context: Context,
    private val vehicleRepository: VehicleRepository? = null
) : ViewModel() {

    private val currentYear = LocalDate.now().year.toString()
    private val _selectedYear = MutableStateFlow(currentYear)
    private val _selectedMonths = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedBusinessEntity = MutableStateFlow("ALL")
    private val _selectedClassification = MutableStateFlow("ALL")
    private val _customStartDate = MutableStateFlow("")
    private val _customEndDate = MutableStateFlow("")
    private val _isExporting = MutableStateFlow(false)
    private val _isGeneratingPdf = MutableStateFlow(false)
    private val _exportErrorMessage = MutableStateFlow<String?>(null)
    private val _savedReportsRefresh = MutableStateFlow(0)

    private val vehiclesFlow: Flow<List<VehicleEntity>> = vehicleRepository?.observeVehicles() ?: flowOf(emptyList())

    private fun loadSavedReports(): List<SavedReportItem> {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) return emptyList()
        val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return exportDir.listFiles()
            ?.filter { it.isFile && (it.extension.equals("pdf", true) || it.extension.equals("csv", true)) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val format = file.extension.uppercase(Locale.US)
                val baseName = file.nameWithoutExtension
                val period = Regex("\\d{4}(-\\d{2})?").find(baseName)?.value ?: currentYear
                SavedReportItem(
                    file = file,
                    name = baseName.replace('_', ' '),
                    period = period,
                    createdDate = dateFmt.format(Date(file.lastModified())),
                    format = format
                )
            }
            ?: emptyList()
    }

    fun buildShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = if (file.extension.equals("pdf", true)) "application/pdf" else "text/csv"
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { Intent.createChooser(it, "Share ${file.name}") }
    }

    fun refreshSavedReports() {
        _savedReportsRefresh.value++
    }

    fun setSelectedPeriod(year: String, months: Set<String>) {
        _selectedYear.value = year
        _selectedMonths.value = months
    }

    val uiState: StateFlow<ReportsUiState> = combine(
        tripRepository.getAllTrips(),
        _selectedYear,
        _selectedMonths,
        _selectedBusinessEntity,
        _selectedClassification
    ) { trips, year, selectedMonths, entity, classification ->
        FilterArgs(trips, year, entity, classification, selectedMonths)
    }.combine(
        combine(_customStartDate, _customEndDate, _isExporting, _isGeneratingPdf, _exportErrorMessage) { start, end, exporting, generatingPdf, errorMsg ->
            DateArgs(start, end, exporting, generatingPdf, errorMsg)
        }
    ) { filterArgs, dateArgs ->
        val trips = filterArgs.trips
        val year = filterArgs.year
        val entity = filterArgs.entity
        val classification = filterArgs.classification
        val start = dateArgs.start
        val end = dateArgs.end

        val selectedMonths = filterArgs.selectedMonths
        val filtered = trips.filter { trip ->
            val matchesYear = if (selectedMonths.isNotEmpty()) {
                trip.date.length >= 7 && trip.date.substring(0, 7) in selectedMonths
            } else {
                year == "ALL" || trip.date.startsWith(year)
            }
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

        val unclassifiedCount = unreviewedTrips.size
        val incompleteCount = filtered.count {
            it.startAddress.isNullOrBlank() || it.endAddress.isNullOrBlank() || it.totalDistanceMiles <= 0.0
        }
        val missingPurposeCount = businessTrips.count { it.purpose.isBlank() }

        val vehicles = vehicleRepository?.let { vehiclesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList()).value } ?: emptyList()
        val vehicleSummaries = filtered.groupBy { it.vehicleId }.map { (vId, grpTrips) ->
            val bGrp = grpTrips.filter { it.classification == "BUSINESS" }
            val pGrp = grpTrips.filter { it.classification == "PERSONAL" }
            val vName = if (vId == null) "Unassigned" else VehicleFriendlyNameResolver.resolve(vId, vehicles)
            VehicleReportSummary(
                vehicleId = vId,
                vehicleName = vName,
                businessMiles = round(bGrp.sumOf { it.businessMiles }),
                personalMiles = round(pGrp.sumOf { it.totalDistanceMiles }),
                tripCount = grpTrips.size,
                estimatedDeduction = round(bGrp.sumOf { it.calculatedDeduction })
            )
        }.sortedByDescending { it.businessMiles + it.personalMiles }

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
        // Monthly rows must reflect the same year, entity, classification, and date filters
        // shown in the report header so displayed totals match exports and YTD metrics.
        val yearTrips = filtered
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
            val mPersonalMiles = mTrips.filter { it.classification == "PERSONAL" }.sumOf { it.totalDistanceMiles }
            val logged = mBusiness.sumOf { it.calculatedDeduction }
            val unreviewedPotential = mUnreviewed.sumOf { trip ->
                val rate = if (trip.applicableRate > 0.0) trip.applicableRate else rateUseCase.getApplicableRate(trip.date)
                trip.totalDistanceMiles * rate
            }
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
                    personalMiles = mPersonalMiles,
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

        val uTrips = filtered.filter { it.classification == "UNREVIEWED" }
        val unreviewedPotential = uTrips.sumOf { trip ->
            val rate = if (trip.applicableRate > 0.0) trip.applicableRate else rateUseCase.getApplicableRate(trip.date)
            trip.totalDistanceMiles * rate
        }

        val periodLabel = if (selectedMonths.isEmpty()) {
            year
        } else {
            val shortNames = selectedMonths.sorted().mapNotNull { iso ->
                runCatching {
                    LocalDate.parse("$iso-01").month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                }.getOrNull()
            }
            if (shortNames.size <= 3) {
                "${shortNames.joinToString(", ")} $year"
            } else {
                "${shortNames.size} months · $year"
            }
        }
        val effectiveSummaries = if (selectedMonths.isNotEmpty()) {
            summaries.filter { it.monthIso in selectedMonths }
        } else {
            summaries
        }

        ReportsUiState(
            selectedYear = year,
            selectedMonths = selectedMonths,
            selectedPeriodLabel = periodLabel,
            availableYears = availableYears,
            selectedBusinessEntity = entity,
            selectedClassification = classification,
            customStartDate = start,
            customEndDate = end,
            monthlySummaries = effectiveSummaries,
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
            isExporting = dateArgs.exporting,
            isGeneratingPdf = dateArgs.generatingPdf,
            exportErrorMessage = dateArgs.errorMsg,
            ytdLoggedDeduction = totalDeduction,
            ytdPotentialDeduction = round(totalDeduction + unreviewedPotential),
            ytdBusinessMiles = bMiles,
            ytdTotalMiles = totalMiles,
            unclassifiedCount = unclassifiedCount,
            incompleteCount = incompleteCount,
            missingPurposeCount = missingPurposeCount,
            vehicleSummaries = vehicleSummaries,
            savedReports = loadSavedReports()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
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

    fun clearExportError() {
        _exportErrorMessage.value = null
    }

    fun exportToCsv(monthIso: String? = null, onFileReady: (Intent) -> Unit) {
        if (_isExporting.value) return
        _isExporting.value = true
        _exportErrorMessage.value = null

        viewModelScope.launch {
            try {
                val state = uiState.value
                val tripsToExport = if (monthIso != null) {
                    state.filteredTrips.filter { it.date.startsWith(monthIso) }
                } else {
                    state.filteredTrips
                }

                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val suffix = monthIso ?: "${state.selectedYear}_${state.selectedBusinessEntity}"
                val csvFile = File(exportDir, "BusinessMileage_${suffix}_${System.currentTimeMillis()}.csv")

                FileWriter(csvFile).use { writer ->
                    writer.append("Trip ID,Date,Start Time,End Time,Start Address,End Address,Total Miles,Business Miles,Classification,Business Entity,Purpose,Mileage Rate,Estimated Deduction,Notes,Tracking Method,Sync Status\n")

                    val timeFmt = java.text.SimpleDateFormat("h:mm a", Locale.US)

                    for (trip in tripsToExport) {
                        val startStr = if (trip.startTime > 0) timeFmt.format(java.util.Date(trip.startTime)) else ""
                        val endStr = if (trip.endTime > 0) timeFmt.format(java.util.Date(trip.endTime)) else ""

                        writer.append("\"${escapeCsv(trip.id)}\",")
                        writer.append("\"${escapeCsv(trip.date)}\",")
                        writer.append("\"${escapeCsv(startStr)}\",")
                        writer.append("\"${escapeCsv(endStr)}\",")
                        writer.append("\"${escapeCsv(trip.startAddress.orEmpty())}\",")
                        writer.append("\"${escapeCsv(trip.endAddress.orEmpty())}\",")
                        writer.append("${trip.totalDistanceMiles},")
                        writer.append("${trip.businessMiles},")
                        writer.append("\"${escapeCsv(trip.classification)}\",")
                        writer.append("\"${escapeCsv(trip.businessEntity)}\",")
                        writer.append("\"${escapeCsv(trip.purpose)}\",")
                        writer.append("${trip.applicableRate},")
                        writer.append("${trip.calculatedDeduction},")
                        writer.append("\"${escapeCsv(trip.notes.orEmpty())}\",")
                        writer.append("\"${escapeCsv(trip.trackingMethod)}\",")
                        writer.append("\"${escapeCsv(trip.syncStatus)}\"\n")
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
            } catch (e: Exception) {
                android.util.Log.e("ReportsExportViewModel", "Failed to export CSV: ${e.message}", e)
                _exportErrorMessage.value = "Unable to create CSV export. Please try again."
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun generatePdfFile(onGenerated: (File) -> Unit, onError: ((String) -> Unit)? = null) {
        if (_isGeneratingPdf.value) return
        _isGeneratingPdf.value = true
        _exportErrorMessage.value = null

        viewModelScope.launch {
            try {
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
                onGenerated(pdfFile)
            } catch (e: Exception) {
                android.util.Log.e("ReportsExportViewModel", "Failed to generate PDF: ${e.message}", e)
                val friendlyMsg = "Unable to create your PDF report. Please try again."
                _exportErrorMessage.value = friendlyMsg
                onError?.invoke(friendlyMsg)
            } finally {
                _isGeneratingPdf.value = false
            }
        }
    }

    fun exportToPdf(onFileReady: (Intent) -> Unit, onError: ((String) -> Unit)? = null) {
        generatePdfFile(
            onGenerated = { pdfFile ->
                try {
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
                } catch (e: Exception) {
                    android.util.Log.e("ReportsExportViewModel", "Failed to share PDF: ${e.message}", e)
                    val friendlyMsg = "Unable to share your PDF report. Please try again."
                    _exportErrorMessage.value = friendlyMsg
                    onError?.invoke(friendlyMsg)
                }
            },
            onError = onError
        )
    }

    internal fun escapeCsv(value: String): String {
        // Protect against spreadsheet formula injection (CSV Injection / CWE-1236)
        // Values starting with formula triggers ('=', '+', '-', '@', '|', '%')
        // even after leading whitespace/control characters (' ', '\t', '\r', '\n')
        // or starting directly with whitespace/control characters must be prefixed with a single quote (')
        val trimmed = value.trimStart(' ', '\t', '\r', '\n')
        val hasFormulaTrigger = trimmed.isNotEmpty() && (
            trimmed.startsWith('=') ||
            trimmed.startsWith('+') ||
            trimmed.startsWith('-') ||
            trimmed.startsWith('@') ||
            trimmed.startsWith('|') ||
            trimmed.startsWith('%')
        )
        val hasControlPrefix = value.startsWith('\t') || value.startsWith('\r') || value.startsWith('\n')
        val sanitized = if (hasFormulaTrigger || hasControlPrefix) {
            "'$value"
        } else {
            value
        }
        return sanitized.replace("\"", "\"\"")
    }

    private fun round(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0

    private data class FilterArgs(
        val trips: List<TripEntity>,
        val year: String,
        val entity: String,
        val classification: String,
        val selectedMonths: Set<String> = emptySet()
    )

    private data class DateArgs(
        val start: String,
        val end: String,
        val exporting: Boolean,
        val generatingPdf: Boolean,
        val errorMsg: String?
    )
}
