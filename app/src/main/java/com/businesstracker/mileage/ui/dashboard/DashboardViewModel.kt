package com.businesstracker.mileage.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.model.DashboardSummary
import com.businesstracker.mileage.domain.model.TimeFilter
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.repository.ActiveTripRepository
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import com.businesstracker.mileage.domain.usecase.GetDashboardSummaryUseCase
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.service.TrackingServiceState
import com.businesstracker.mileage.worker.MileageSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class DashboardUiState(
    val unreviewedTrips: List<TripEntity> = emptyList(),
    val timeFilter: TimeFilter = TimeFilter.THIS_MONTH,
    val customStartDate: String = "",
    val customEndDate: String = "",
    val selectedMonthName: String = "",
    val availableMonths: List<String> = emptyList(),
    val summary: DashboardSummary = DashboardSummary(),
    val currentRate: Double = 0.760,
    val lastClassifiedTrip: TripEntity? = null,
    val isUndoAvailable: Boolean = false,
    val processingTripIds: Set<String> = emptySet()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val activeTripRepository: ActiveTripRepository,
    private val rateUseCase: CalculateMileageRateUseCase,
    private val summaryUseCase: GetDashboardSummaryUseCase,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val now = LocalDate.now()
    private val currentRate = rateUseCase.getApplicableRate(now.format(DateTimeFormatter.ISO_DATE))
    private val currentMonthIso = now.format(DateTimeFormatter.ofPattern("yyyy-MM"))

    private val _timeFilter = MutableStateFlow(TimeFilter.THIS_MONTH)
    private val _customStartDate = MutableStateFlow("")
    private val _customEndDate = MutableStateFlow("")
    private val _selectedMonthPrefix = MutableStateFlow(currentMonthIso)
    private val _processingTripIds = MutableStateFlow<Set<String>>(emptySet())
    private val _lastClassifiedTrip = MutableStateFlow<TripEntity?>(null)

    val serviceState: StateFlow<TrackingServiceState> = combine(
        LocationTrackingService.serviceState,
        activeTripRepository.observeActiveTrip()
    ) { liveState, activeTrip ->
        if (liveState.isTracking) {
            liveState
        } else if (activeTrip != null) {
            TrackingServiceState(
                isTracking = true,
                tripId = activeTrip.id,
                distanceMeters = activeTrip.totalDistanceMeters,
                distanceMiles = activeTrip.totalDistanceMeters * 0.000621371,
                isPaused = activeTrip.isPaused,
                durationMillis = maxOf(0L, System.currentTimeMillis() - activeTrip.startTime)
            )
        } else {
            liveState
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LocationTrackingService.serviceState.value
    )

    private data class TripFilterState(
        val unreviewed: List<TripEntity>,
        val allTrips: List<TripEntity>,
        val timeFilter: TimeFilter,
        val monthPrefix: String
    )

    private data class CustomRangeAndActionState(
        val customStart: String,
        val customEnd: String,
        val processingIds: Set<String>,
        val lastClassified: TripEntity?
    )

    private val tripFilterFlow = combine(
        tripRepository.getUnclassifiedTrips(),
        tripRepository.getAllTrips(),
        _timeFilter,
        _selectedMonthPrefix
    ) { unreviewed, allTrips, filter, monthPrefix ->
        TripFilterState(unreviewed, allTrips, filter, monthPrefix)
    }

    private val actionFlow = combine(
        _customStartDate,
        _customEndDate,
        _processingTripIds,
        _lastClassifiedTrip
    ) { customStart, customEnd, processingIds, lastClassified ->
        CustomRangeAndActionState(customStart, customEnd, processingIds, lastClassified)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        tripFilterFlow,
        actionFlow
    ) { tripState, actionState ->

        val effectiveFilter = if (tripState.timeFilter == TimeFilter.CUSTOM && actionState.customStart.isBlank() && actionState.customEnd.isBlank()) {
            TimeFilter.THIS_MONTH
        } else {
            tripState.timeFilter
        }

        val summary = summaryUseCase.execute(
            trips = tripState.allTrips,
            timeFilter = effectiveFilter,
            customStartDate = if (actionState.customStart.isNotBlank()) actionState.customStart else null,
            customEndDate = if (actionState.customEnd.isNotBlank()) actionState.customEnd else null,
            referenceDate = now
        )

        val monthDate = try {
            LocalDate.parse("${tripState.monthPrefix}-01")
        } catch (_: Exception) {
            now
        }
        val monthDisplayName = monthDate.month.getDisplayName(TextStyle.FULL, Locale.getDefault())

        val availableMonths = tripState.allTrips.mapNotNull {
            if (it.date.length >= 7) it.date.substring(0, 7) else null
        }.distinct().sortedDescending().ifEmpty { listOf(currentMonthIso) }

        DashboardUiState(
            unreviewedTrips = tripState.unreviewed,
            timeFilter = tripState.timeFilter,
            customStartDate = actionState.customStart,
            customEndDate = actionState.customEnd,
            selectedMonthName = monthDisplayName,
            availableMonths = availableMonths,
            summary = summary,
            currentRate = currentRate,
            lastClassifiedTrip = actionState.lastClassified,
            isUndoAvailable = actionState.lastClassified != null,
            processingTripIds = actionState.processingIds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DashboardUiState(
            selectedMonthName = now.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        )
    )

    fun setTimeFilter(filter: TimeFilter) {
        _timeFilter.value = filter
    }

    fun setCustomDateRange(startDate: String, endDate: String) {
        _customStartDate.value = startDate
        _customEndDate.value = endDate
        _timeFilter.value = TimeFilter.CUSTOM
    }

    fun selectMonth(monthPrefix: String) {
        _selectedMonthPrefix.value = monthPrefix
        val parts = monthPrefix.split("-")
        if (parts.size == 2) {
            val year = parts[0].toIntOrNull() ?: now.year
            val month = parts[1].toIntOrNull() ?: now.monthValue
            val firstDay = LocalDate.of(year, month, 1)
            val lastDay = firstDay.plusMonths(1).minusDays(1)
            _customStartDate.value = firstDay.toString()
            _customEndDate.value = lastDay.toString()
            _timeFilter.value = TimeFilter.CUSTOM
        }
    }

    fun startManualTracking(suggestedBusiness: String = BusinessEntity.MY_SUCCULENT_CORNER.displayName) {
        val intent = Intent(appContext, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_TRIP_ID, UUID.randomUUID().toString())
            putExtra(LocationTrackingService.EXTRA_TRACKING_METHOD, TrackingMethod.MANUAL_GPS.value)
            putExtra(LocationTrackingService.EXTRA_SUGGESTED_BUSINESS, suggestedBusiness)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun pauseTracking() {
        val intent = Intent(appContext, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_PAUSE
        }
        appContext.startService(intent)
    }

    fun resumeTracking() {
        val intent = Intent(appContext, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_RESUME
        }
        appContext.startService(intent)
    }

    fun endTracking() {
        val intent = Intent(appContext, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        appContext.startService(intent)
    }

    fun classifyTripAsBusiness(
        tripId: String,
        businessEntity: String = BusinessEntity.MY_SUCCULENT_CORNER.displayName,
        purpose: String
    ) {
        if (_processingTripIds.value.contains(tripId)) return
        _processingTripIds.value = _processingTripIds.value + tripId

        viewModelScope.launch {
            try {
                val originalTrip = tripRepository.getTripById(tripId)
                tripRepository.classifyTrip(
                    tripId = tripId,
                    classification = TripClassification.BUSINESS.value,
                    businessEntity = businessEntity,
                    purpose = purpose,
                    notes = originalTrip?.notes
                )
                if (originalTrip != null) {
                    _lastClassifiedTrip.value = originalTrip
                }
                MileageSyncWorker.enqueueOneTimeSync(appContext)
            } finally {
                _processingTripIds.value = _processingTripIds.value - tripId
            }
        }
    }

    fun classifyTripAsPersonal(tripId: String) {
        if (_processingTripIds.value.contains(tripId)) return
        _processingTripIds.value = _processingTripIds.value + tripId

        viewModelScope.launch {
            try {
                val originalTrip = tripRepository.getTripById(tripId)
                tripRepository.classifyTrip(
                    tripId = tripId,
                    classification = TripClassification.PERSONAL.value,
                    businessEntity = BusinessEntity.PERSONAL.displayName,
                    purpose = "Personal",
                    notes = originalTrip?.notes
                )
                if (originalTrip != null) {
                    _lastClassifiedTrip.value = originalTrip
                }
                MileageSyncWorker.enqueueOneTimeSync(appContext)
            } finally {
                _processingTripIds.value = _processingTripIds.value - tripId
            }
        }
    }

    fun undoLastClassification() {
        val previousTrip = _lastClassifiedTrip.value ?: return
        _lastClassifiedTrip.value = null

        viewModelScope.launch {
            tripRepository.classifyTrip(
                tripId = previousTrip.id,
                classification = previousTrip.classification,
                businessEntity = previousTrip.businessEntity,
                purpose = previousTrip.purpose,
                notes = previousTrip.notes
            )
            MileageSyncWorker.enqueueOneTimeSync(appContext)
        }
    }
}
