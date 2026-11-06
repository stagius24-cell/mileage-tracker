package com.businesstracker.mileage.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.model.BusinessEntitySummary
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import com.businesstracker.mileage.worker.MileageSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class DashboardUiState(
    val unreviewedTrips: List<TripEntity> = emptyList(),
    val totalBusinessMilesYtd: Double = 0.0,
    val totalBusinessMilesMonth: Double = 0.0,
    val totalDeductionYtd: Double = 0.0,
    val totalTripCount: Int = 0,
    val succulentMiles: Double = 0.0,
    val succulentDeduction: Double = 0.0,
    val haneeloveMiles: Double = 0.0,
    val haneeloveDeduction: Double = 0.0,
    val personalMiles: Double = 0.0,
    val currentRate: Double = 0.760
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val rateUseCase: CalculateMileageRateUseCase,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val now = LocalDate.now()
    private val currentYear = now.year.toString()
    private val currentMonthPrefix = now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    private val currentRate = rateUseCase.getApplicableRate(now.format(DateTimeFormatter.ISO_DATE))

    val uiState: StateFlow<DashboardUiState> = combine(
        tripRepository.getUnclassifiedTrips(),
        tripRepository.getAllTrips(),
        tripRepository.getBusinessEntitySummaries()
    ) { unreviewed, allTrips, summaries ->
        val businessTrips = allTrips.filter { it.classification == TripClassification.BUSINESS.value }
        val personalTrips = allTrips.filter { it.classification == TripClassification.PERSONAL.value }

        val ytdBusinessTrips = businessTrips.filter { it.date.startsWith(currentYear) }
        val monthBusinessTrips = businessTrips.filter { it.date.startsWith(currentMonthPrefix) }

        val totalBusinessMilesYtd = ytdBusinessTrips.sumOf { it.businessMiles }
        val totalBusinessMilesMonth = monthBusinessTrips.sumOf { it.businessMiles }
        val totalDeductionYtd = ytdBusinessTrips.sumOf { it.calculatedDeduction }

        val succulentSummary = summaries.firstOrNull { it.businessEntity == BusinessEntity.MY_SUCCULENT_CORNER.displayName }
        val haneeloveSummary = summaries.firstOrNull { it.businessEntity == BusinessEntity.HANEELOVE.displayName }
        val personalMiles = personalTrips.sumOf { it.totalDistanceMiles }

        DashboardUiState(
            unreviewedTrips = unreviewed,
            totalBusinessMilesYtd = totalBusinessMilesYtd,
            totalBusinessMilesMonth = totalBusinessMilesMonth,
            totalDeductionYtd = totalDeductionYtd,
            totalTripCount = allTrips.size,
            succulentMiles = succulentSummary?.totalBusinessMiles ?: 0.0,
            succulentDeduction = succulentSummary?.totalDeduction ?: 0.0,
            haneeloveMiles = haneeloveSummary?.totalBusinessMiles ?: 0.0,
            haneeloveDeduction = haneeloveSummary?.totalDeduction ?: 0.0,
            personalMiles = personalMiles,
            currentRate = currentRate
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun classifyTripAsBusiness(tripId: String, businessEntity: String, purpose: String) {
        viewModelScope.launch {
            tripRepository.classifyTrip(
                tripId = tripId,
                classification = TripClassification.BUSINESS.value,
                businessEntity = businessEntity,
                purpose = purpose,
                notes = null
            )
            // Trigger resilient background sync to Cloudflare D1 / Google Sheets
            MileageSyncWorker.enqueueOneTimeSync(appContext)
        }
    }

    fun classifyTripAsPersonal(tripId: String) {
        viewModelScope.launch {
            tripRepository.classifyTrip(
                tripId = tripId,
                classification = TripClassification.PERSONAL.value,
                businessEntity = BusinessEntity.PERSONAL.displayName,
                purpose = "Personal",
                notes = null
            )
            MileageSyncWorker.enqueueOneTimeSync(appContext)
        }
    }
}
