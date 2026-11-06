package com.businesstracker.mileage.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.businesstracker.mileage.data.local.entity.TripEntity
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
        val personalMiles = personalTrips.sumOf { it.totalDistanceMiles }

        DashboardUiState(
            unreviewedTrips = unreviewed,
            totalBusinessMilesYtd = totalBusinessMilesYtd,
            totalBusinessMilesMonth = totalBusinessMilesMonth,
            totalDeductionYtd = totalDeductionYtd,
            totalTripCount = allTrips.size,
            succulentMiles = succulentSummary?.totalBusinessMiles ?: totalBusinessMilesYtd,
            succulentDeduction = succulentSummary?.totalDeduction ?: totalDeductionYtd,
            personalMiles = personalMiles,
            currentRate = currentRate
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun classifyTripAsBusiness(tripId: String, businessEntity: String = BusinessEntity.MY_SUCCULENT_CORNER.displayName, purpose: String) {
        viewModelScope.launch {
            tripRepository.classifyTrip(
                tripId = tripId,
                classification = TripClassification.BUSINESS.value,
                businessEntity = BusinessEntity.MY_SUCCULENT_CORNER.displayName,
                purpose = purpose,
                notes = null
            )
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
