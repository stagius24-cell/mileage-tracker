package com.businesstracker.mileage.ui.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.model.BusinessEntitySummary
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.util.Locale
import javax.inject.Inject

data class ReportsUiState(
    val summaries: List<BusinessEntitySummary> = emptyList(),
    val trips: List<TripEntity> = emptyList(),
    val totalDeduction: Double = 0.0,
    val totalBusinessMiles: Double = 0.0,
    val isExporting: Boolean = false
)

@HiltViewModel
class ReportsExportViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val uiState: StateFlow<ReportsUiState> = combine(
        tripRepository.getBusinessEntitySummaries(),
        tripRepository.getAllTrips()
    ) { summaries, trips ->
        val businessTrips = trips.filter { it.classification == "BUSINESS" }
        val totalDeduction = businessTrips.sumOf { it.calculatedDeduction }
        val totalBusinessMiles = businessTrips.sumOf { it.businessMiles }

        ReportsUiState(
            summaries = summaries,
            trips = businessTrips,
            totalDeduction = totalDeduction,
            totalBusinessMiles = totalBusinessMiles
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportsUiState()
    )

    fun exportToCsv(onFileReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val trips = uiState.value.trips
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val csvFile = File(exportDir, "Business_Mileage_Log_${System.currentTimeMillis()}.csv")

            FileWriter(csvFile).use { writer ->
                // CSV Header
                writer.append("ID,Date,BusinessEntity,Classification,Purpose,DistanceMiles,Rate,CalculatedDeduction,StartAddress,EndAddress,TrackingMethod,SyncStatus\n")

                for (trip in trips) {
                    writer.append("\"${trip.id}\",")
                    writer.append("\"${trip.date}\",")
                    writer.append("\"${trip.businessEntity}\",")
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
                putExtra(Intent.EXTRA_SUBJECT, "Business Mileage Log (MySucculentCorner & Haneelove)")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            onFileReady(Intent.createChooser(shareIntent, "Share Mileage CSV Log").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun escapeCsv(value: String): String = value.replace("\"", "\"\"")
}
