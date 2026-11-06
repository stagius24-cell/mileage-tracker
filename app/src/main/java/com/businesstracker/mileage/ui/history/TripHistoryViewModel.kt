package com.businesstracker.mileage.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.businesstracker.mileage.data.local.entity.TripEntity
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
    val searchQuery: String = "",
    val selectedBusinessFilter: String = "ALL", // "ALL", "MySucculentCorner", "Haneelove", "Personal"
    val selectedClassificationFilter: String = "ALL", // "ALL", "BUSINESS", "PERSONAL", "UNREVIEWED"
    val selectedYearFilter: String = "ALL" // "ALL", "2026", "2025"
)

@HiltViewModel
class TripHistoryViewModel @Inject constructor(
    private val tripRepository: TripRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _businessFilter = MutableStateFlow("ALL")
    private val _classificationFilter = MutableStateFlow("ALL")
    private val _yearFilter = MutableStateFlow("ALL")

    val uiState: StateFlow<TripHistoryUiState> = combine(
        tripRepository.getAllTrips(),
        _searchQuery,
        _businessFilter,
        _classificationFilter,
        _yearFilter
    ) { allTrips, query, business, classification, year ->
        val filtered = allTrips.filter { trip ->
            val matchesQuery = query.isBlank() ||
                    (trip.purpose.contains(query, ignoreCase = true)) ||
                    (trip.startAddress?.contains(query, ignoreCase = true) == true) ||
                    (trip.endAddress?.contains(query, ignoreCase = true) == true) ||
                    (trip.date.contains(query, ignoreCase = true))

            val matchesBusiness = business == "ALL" || trip.businessEntity.equals(business, ignoreCase = true)
            val matchesClassification = classification == "ALL" || trip.classification.equals(classification, ignoreCase = true)
            val matchesYear = year == "ALL" || trip.date.startsWith(year)

            matchesQuery && matchesBusiness && matchesClassification && matchesYear
        }

        TripHistoryUiState(
            trips = filtered,
            searchQuery = query,
            selectedBusinessFilter = business,
            selectedClassificationFilter = classification,
            selectedYearFilter = year
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TripHistoryUiState()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setBusinessFilter(filter: String) {
        _businessFilter.value = filter
    }

    fun setClassificationFilter(filter: String) {
        _classificationFilter.value = filter
    }

    fun setYearFilter(year: String) {
        _yearFilter.value = year
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch {
            tripRepository.deleteTrip(tripId)
        }
    }
}
