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

data class ReportsUiState(
    val selectedYear: String = "2026",
    val availableYears: List<String> = listOf("2026"),
    val monthlySummaries: List<MonthSummary> = emptyList(),
    val ytdLoggedDeduction: Double = 0.0,
    val ytdPotentialDeduction: Double = 0.0,
    val ytdBusinessMiles: Double = 0.0,
    val ytdTotalMiles: Double = 0.0,
    val ytdDrivesCount: Int = 0,
    val allBusinessTrips: List<TripEntity> = emptyList()
)

@HiltViewModel
class ReportsExportViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val rateUseCase: CalculateMileageRateUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val currentYear = LocalDate.now().year.toString()
    private val _selectedYear = MutableStateFlow(currentYear)

    val uiState: StateFlow<ReportsUiState> = combine(
        tripRepository.getAllTrips(),
        _selectedYear
    ) { trips, year ->
        val yearTrips = trips.filter { it.date.startsWith(year) }
        val businessTrips = yearTrips.filter { it.classification == "BUSINESS" }
        val unreviewedTrips = yearTrips.filter { it.classification == "UNREVIEWED" }

        val ytdBusinessMiles = businessTrips.sumOf { it.businessMiles }
        val ytdTotalMiles = yearTrips.sumOf { it.totalDistanceMiles }
        val ytdLoggedDeduction = businessTrips.sumOf { it.calculatedDeduction }
        val ytdUnreviewedPotential = unreviewedTrips.sumOf { it.totalDistanceMiles * it.applicableRate }
        val ytdPotentialDeduction = ytdLoggedDeduction + ytdUnreviewedPotential

        // Group trips by month
        val monthsMap = mutableMapOf<String, MutableList<TripEntity>>()
        for (i in 1..12) {
            val monthKey = String.format(Locale.US, "%s-%02d", year, i)
            monthsMap[monthKey] = mutableListOf()
        }
        for (trip in yearTrips) {
            if (trip.date.length >= 7) {
                val key = trip.date.substring(0, 7)
                monthsMap.getOrPut(key) { mutableListOf() }.add(trip)
            }
        }

        val summaries = monthsMap.keys.sortedDescending().mapNotNull { monthIso ->
            val monthTrips = monthsMap[monthIso].orEmpty()
            val mBusiness = monthTrips.filter { it.classification == "BUSINESS" }
            val mUnreviewed = monthTrips.filter { it.classification == "UNREVIEWED" }

            val totalDrives = monthTrips.size
            val totalMiles = monthTrips.sumOf { it.totalDistanceMiles }
            val bMiles = mBusiness.sumOf { it.businessMiles }
            val logged = mBusiness.sumOf { it.calculatedDeduction }
            val unreviewedPotential = mUnreviewed.sumOf { it.totalDistanceMiles * it.applicableRate }
            val potential = logged + unreviewedPotential

            val percent = if (potential > 0.0) {
                ((logged / potential) * 100).toInt().coerceIn(0, 100)
            } else if (totalMiles > 0) {
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

            // Show month if it has drives or is within the current year up to now
            val currentMonthIso = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
            if (totalDrives > 0 || monthIso <= currentMonthIso) {
                MonthSummary(
                    monthIso = monthIso,
                    monthName = monthName,
                    year = year,
                    totalDrives = totalDrives,
                    totalMiles = totalMiles,
                    businessDrives = mBusiness.size,
                    businessMiles = bMiles,
                    loggedDeduction = logged,
                    potentialDeduction = potential,
                    percentComplete = percent,
                    unreviewedDrives = mUnreviewed.size
                )
            } else {
                null
            }
        }

        val availableYears = trips.mapNotNull {
            if (it.date.length >= 4) it.date.substring(0, 4) else null
        }.distinct().sortedDescending().ifEmpty { listOf(currentYear) }

        ReportsUiState(
            selectedYear = year,
            availableYears = availableYears,
            monthlySummaries = summaries,
            ytdLoggedDeduction = ytdLoggedDeduction,
            ytdPotentialDeduction = ytdPotentialDeduction,
            ytdBusinessMiles = ytdBusinessMiles,
            ytdTotalMiles = ytdTotalMiles,
            ytdDrivesCount = businessTrips.size,
            allBusinessTrips = businessTrips
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportsUiState()
    )

    fun selectYear(year: String) {
        _selectedYear.value = year
    }

    fun exportToCsv(monthIso: String? = null, onFileReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val allTrips = uiState.value.allBusinessTrips
            val filteredTrips = if (monthIso != null) {
                allTrips.filter { it.date.startsWith(monthIso) }
            } else {
                allTrips
            }

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val suffix = monthIso ?: uiState.value.selectedYear
            val csvFile = File(exportDir, "MySucculentCorner_Mileage_${suffix}_${System.currentTimeMillis()}.csv")

            FileWriter(csvFile).use { writer ->
                writer.append("ID,Date,BusinessEntity,Classification,Purpose,DistanceMiles,Rate,CalculatedDeduction,StartAddress,EndAddress,TrackingMethod,SyncStatus\n")

                for (trip in filteredTrips) {
                    writer.append("\"${trip.id}\",")
                    writer.append("\"${trip.date}\",")
                    writer.append("\"MySucculentCorner\",")
                    writer.append("\"${trip.classification}\",")
                    writer.append("\"${escapeCsv(trip.purpose)}\",")
                    writer.append("${trip.businessMiles},")
                    writer.append("${trip.applicableRate},")
                    writer.append("${trip.calculatedDeduction},")
                    writer.append("\"${escapeCsv(trip.startAddress.orEmpty())}\",")
                    writer.append("\"${escapeCsv(trip.endAddress.orEmpty())}\",")
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
                putExtra(Intent.EXTRA_SUBJECT, "MySucculentCorner Mileage Log ($suffix)")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            onFileReady(Intent.createChooser(shareIntent, "Share Mileage Log").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun escapeCsv(value: String): String = value.replace("\"", "\"\"")
}
