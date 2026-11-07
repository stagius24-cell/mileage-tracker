package com.businesstracker.mileage.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.model.SortOrder
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripHistoryUiState(
    val trips: List<TripEntity> = emptyList(),
    val totalFilteredMiles: Double = 0.0,
    val totalFilteredDeduction: Double = 0.0,
    val searchQuery: String = "",
    val selectedClassificationFilter: String = "ALL", // "ALL", "BUSINESS", "PERSONAL", "UNREVIEWED"
    val selectedBusinessEntityFilter: String = "ALL", // "ALL", "MySucculentCorner", "Personal"
    val selectedSyncStatusFilter: String = "ALL",     // "ALL", "SYNCED", "PENDING", "FAILED"
    val startDateFilter: String = "",
    val endDateFilter: String = "",
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    val selectedTripForDetails: TripEntity? = null
)

@HiltViewModel
class TripHistoryViewModel @Inject constructor(
    private val tripRepository: TripRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _classificationFilter = MutableStateFlow("ALL")
    private val _businessEntityFilter = MutableStateFlow("ALL")
    private val _syncStatusFilter = MutableStateFlow("ALL")
    private val _startDateFilter = MutableStateFlow("")
    private val _endDateFilter = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST_FIRST)
    private val _selectedTripForDetails = MutableStateFlow<TripEntity?>(null)

    val uiState: StateFlow<TripHistoryUiState> = combine(
        tripRepository.getAllTrips(),
        _searchQuery,
        _classificationFilter,
        _businessEntityFilter,
        _syncStatusFilter
    ) { allTrips, query, classification, entity, syncStatus ->
        FilterStatePart1(allTrips, query, classification, entity, syncStatus)
    }.combine(
        combine(_startDateFilter, _endDateFilter, _sortOrder, _selectedTripForDetails) { start, end, sort, selectedTrip ->
            FilterStatePart2(start, end, sort, selectedTrip)
        }
    ) { part1, part2 ->
        val filtered = part1.allTrips.filter { trip ->
            // Search filter: address, business entity, purpose, notes, date
            val matchesQuery = part1.query.isBlank() ||
                    trip.purpose.contains(part1.query, ignoreCase = true) ||
                    (trip.startAddress?.contains(part1.query, ignoreCase = true) == true) ||
                    (trip.endAddress?.contains(part1.query, ignoreCase = true) == true) ||
                    trip.businessEntity.contains(part1.query, ignoreCase = true) ||
                    (trip.notes?.contains(part1.query, ignoreCase = true) == true) ||
                    trip.date.contains(part1.query, ignoreCase = true)

            // Classification filter
            val matchesClassification = part1.classification == "ALL" ||
                    trip.classification.equals(part1.classification, ignoreCase = true)

            // Business entity filter
            val matchesEntity = part1.entity == "ALL" ||
                    trip.businessEntity.equals(part1.entity, ignoreCase = true)

            // Sync status filter
            val matchesSync = part1.syncStatus == "ALL" ||
                    trip.syncStatus.equals(part1.syncStatus, ignoreCase = true)

            // Date range filter
            val matchesStartDate = part2.startDate.isBlank() || trip.date >= part2.startDate
            val matchesEndDate = part2.endDate.isBlank() || trip.date <= part2.endDate

            matchesQuery && matchesClassification && matchesEntity && matchesSync && matchesStartDate && matchesEndDate
        }

        // Sorting
        val sorted = when (part2.sortOrder) {
            SortOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.startTime }
            SortOrder.OLDEST_FIRST -> filtered.sortedBy { it.startTime }
            SortOrder.HIGHEST_MILEAGE -> filtered.sortedByDescending { it.totalDistanceMiles }
            SortOrder.LOWEST_MILEAGE -> filtered.sortedBy { it.totalDistanceMiles }
        }

        val totalMiles = sorted.sumOf { it.totalDistanceMiles }
        val totalDeduction = sorted.filter { it.classification == TripClassification.BUSINESS.value }
            .sumOf { it.calculatedDeduction }

        TripHistoryUiState(
            trips = sorted,
            totalFilteredMiles = kotlin.math.round(totalMiles * 100.0) / 100.0,
            totalFilteredDeduction = kotlin.math.round(totalDeduction * 100.0) / 100.0,
            searchQuery = part1.query,
            selectedClassificationFilter = part1.classification,
            selectedBusinessEntityFilter = part1.entity,
            selectedSyncStatusFilter = part1.syncStatus,
            startDateFilter = part2.startDate,
            endDateFilter = part2.endDate,
            sortOrder = part2.sortOrder,
            selectedTripForDetails = part2.selectedTrip
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TripHistoryUiState()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setClassificationFilter(filter: String) {
        _classificationFilter.value = filter
    }

    fun setBusinessEntityFilter(entity: String) {
        _businessEntityFilter.value = entity
    }

    fun setSyncStatusFilter(status: String) {
        _syncStatusFilter.value = status
    }

    fun setDateRange(startDate: String, endDate: String) {
        _startDateFilter.value = startDate
        _endDateFilter.value = endDate
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun selectTripForDetails(trip: TripEntity?) {
        _selectedTripForDetails.value = trip
    }

    fun updateTripDetails(
        tripId: String,
        classification: String,
        businessEntity: String,
        purpose: String,
        notes: String?
    ) {
        viewModelScope.launch {
            tripRepository.updateTripDetails(
                tripId = tripId,
                classification = classification,
                businessEntity = businessEntity,
                purpose = purpose,
                notes = notes
            )
            // Update selected trip in details view if open
            val updated = tripRepository.getTripById(tripId)
            if (_selectedTripForDetails.value?.id == tripId) {
                _selectedTripForDetails.value = updated
            }
        }
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch {
            if (_selectedTripForDetails.value?.id == tripId) {
                _selectedTripForDetails.value = null
            }
            tripRepository.deleteTrip(tripId)
        }
    }

    private data class FilterStatePart1(
        val allTrips: List<TripEntity>,
        val query: String,
        val classification: String,
        val entity: String,
        val syncStatus: String
    )

    private data class FilterStatePart2(
        val startDate: String,
        val endDate: String,
        val sortOrder: SortOrder,
        val selectedTrip: TripEntity?
    )
}
