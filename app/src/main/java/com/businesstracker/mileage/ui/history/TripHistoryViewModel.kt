package com.businesstracker.mileage.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.entity.VehicleEntity
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.model.ClassificationSource
import com.businesstracker.mileage.domain.model.SortOrder
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.model.TripPurpose
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import com.businesstracker.mileage.domain.repository.SavedLocationRepository
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.domain.repository.VehicleRepository
import com.businesstracker.mileage.domain.usecase.ResolveSmartTripNamesUseCase
import com.businesstracker.mileage.domain.usecase.SmartTripNames
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.businesstracker.mileage.worker.GoogleSheetsSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class TripHistoryUiState(
    val trips: List<TripEntity> = emptyList(),
    val smartNamesMap: Map<String, SmartTripNames> = emptyMap(),
    val savedLocations: List<SavedLocationEntity> = emptyList(),
    val vehicles: List<VehicleEntity> = emptyList(),
    val totalFilteredMiles: Double = 0.0,
    val totalFilteredDeduction: Double = 0.0,
    val totalRecordedTripsCount: Int = 0,
    val availableMonths: List<String> = emptyList(),
    val searchQuery: String = "",
    val selectedClassificationFilter: String = "ALL", // "ALL", "BUSINESS", "PERSONAL", "UNREVIEWED"
    val selectedBusinessEntityFilter: String = "ALL", // "ALL", "MySucculentCorner", "Personal"
    val selectedVehicleFilter: String = "ALL",        // "ALL", "UNKNOWN", or vehicle ID
    val selectedSyncStatusFilter: String = "ALL",     // "ALL", "SYNCED", "PENDING", "FAILED"
    val startDateFilter: String = "",
    val endDateFilter: String = "",
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    val selectedTripForDetails: TripEntity? = null,
    val selectedTripForConfirmationSheet: TripEntity? = null,
    val customPurposes: List<String> = emptyList(),
    val recentlyDeletedTrip: TripEntity? = null,
    val restoreFailedTrip: TripEntity? = null,
    val isRestoring: Boolean = false,
    val actionErrorMessage: String? = null
)

@HiltViewModel
class TripHistoryViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val savedLocationRepository: SavedLocationRepository? = null,
    private val vehicleRepository: VehicleRepository? = null,
    private val resolveSmartTripNamesUseCase: ResolveSmartTripNamesUseCase? = null,
    private val preferencesManager: TrackingPreferencesManager? = null,
    @ApplicationContext private val context: Context? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _classificationFilter = MutableStateFlow("ALL")
    private val _businessEntityFilter = MutableStateFlow("ALL")
    private val _vehicleFilter = MutableStateFlow("ALL")
    private val _syncStatusFilter = MutableStateFlow("ALL")
    private val _startDateFilter = MutableStateFlow("")
    private val _endDateFilter = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST_FIRST)
    private val _selectedTripForDetails = MutableStateFlow<TripEntity?>(null)
    private val _selectedTripForConfirmationSheet = MutableStateFlow<TripEntity?>(null)
    private val _recentlyDeletedTrips = MutableStateFlow<List<TripEntity>>(emptyList())
    private val _restoreFailedTrip = MutableStateFlow<TripEntity?>(null)
    private val _isRestoring = MutableStateFlow(false)
    private val _actionErrorMessage = MutableStateFlow<String?>(null)

    private val deleteMutex = Mutex()
    private val restoreMutex = Mutex()

    val uiState: StateFlow<TripHistoryUiState> = combine(
        tripRepository.getAllTrips(),
        (savedLocationRepository?.getAllLocationsFlow() ?: flowOf(emptyList())).catch { emit(emptyList()) },
        (vehicleRepository?.observeVehicles() ?: flowOf(emptyList())).catch { emit(emptyList()) },
        _searchQuery,
        _classificationFilter
    ) { allTrips, savedLocations, vehicles, query, classification ->
        FilterStatePart1(allTrips, savedLocations, vehicles, query, classification)
    }.combine(
        combine(
            _businessEntityFilter,
            _vehicleFilter,
            _syncStatusFilter,
            _startDateFilter,
            _endDateFilter
        ) { entity, vehicle, syncStatus, start, end ->
            FilterStatePart2(
                entity = entity,
                vehicle = vehicle,
                syncStatus = syncStatus,
                startDate = start,
                endDate = end
            )
        }
    ) { part1, part2 ->
        Pair(part1, part2)
    }.combine(
        combine(
            _sortOrder,
            combine(_selectedTripForDetails, _selectedTripForConfirmationSheet) { details, sheet ->
                Pair(details, sheet)
            },
            preferencesManager?.customPurposes ?: flowOf(emptyList())
        ) { sort, (selectedTrip, sheetTrip), customPurposes ->
            FilterStatePart3(
                sortOrder = sort,
                selectedTrip = selectedTrip,
                selectedTripForConfirmationSheet = sheetTrip,
                customPurposes = customPurposes
            )
        }
    ) { (part1, part2), part3 ->
        Triple(part1, part2, part3)
    }.combine(
        combine(
            _recentlyDeletedTrips,
            _restoreFailedTrip,
            _isRestoring,
            _actionErrorMessage
        ) { deletedTrips, restoreFailed, isRestoring, actionError ->
            UndoState(deletedTrips.lastOrNull(), restoreFailed, isRestoring, actionError)
        }
    ) { (part1, part2, part3), undoState ->
        // Compute smart names map
        val namesMap = if (resolveSmartTripNamesUseCase != null && part1.savedLocations.isNotEmpty()) {
            part1.allTrips.associate { trip ->
                trip.id to resolveSmartTripNamesUseCase.resolve(trip, part1.savedLocations)
            }
        } else {
            emptyMap()
        }

        val filtered = part1.allTrips.filter { trip ->
            val smartNames = namesMap[trip.id]
            val startDisplay = smartNames?.startDisplayName ?: trip.startAddress.orEmpty()
            val endDisplay = smartNames?.endDisplayName ?: trip.endAddress.orEmpty()

            // Search filter: address, smart name, business entity, purpose, notes, date
            val matchesQuery = part1.query.isBlank() ||
                    trip.purpose.contains(part1.query, ignoreCase = true) ||
                    startDisplay.contains(part1.query, ignoreCase = true) ||
                    endDisplay.contains(part1.query, ignoreCase = true) ||
                    (trip.startAddress?.contains(part1.query, ignoreCase = true) == true) ||
                    (trip.endAddress?.contains(part1.query, ignoreCase = true) == true) ||
                    trip.businessEntity.contains(part1.query, ignoreCase = true) ||
                    (trip.notes?.contains(part1.query, ignoreCase = true) == true) ||
                    trip.date.contains(part1.query, ignoreCase = true)

            // Classification filter
            val matchesClassification = part1.classification == "ALL" ||
                    trip.classification.equals(part1.classification, ignoreCase = true)

            // Business entity filter
            val matchesEntity = part2.entity == "ALL" ||
                    trip.businessEntity.equals(part2.entity, ignoreCase = true)

            // Vehicle filter
            val matchesVehicle = when (part2.vehicle) {
                "ALL" -> true
                "UNKNOWN" -> trip.vehicleId.isNullOrBlank()
                else -> trip.vehicleId == part2.vehicle
            }

            // Sync status filter
            val matchesSync = part2.syncStatus == "ALL" ||
                    trip.syncStatus.equals(part2.syncStatus, ignoreCase = true)

            // Date range filter
            val matchesStartDate = part2.startDate.isBlank() || trip.date >= part2.startDate
            val matchesEndDate = part2.endDate.isBlank() || trip.date <= part2.endDate

            matchesQuery && matchesClassification && matchesEntity && matchesVehicle && matchesSync && matchesStartDate && matchesEndDate
        }

        // Sorting
        val sorted = when (part3.sortOrder) {
            SortOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.startTime }
            SortOrder.OLDEST_FIRST -> filtered.sortedBy { it.startTime }
            SortOrder.HIGHEST_MILEAGE -> filtered.sortedByDescending { it.totalDistanceMiles }
            SortOrder.LOWEST_MILEAGE -> filtered.sortedBy { it.totalDistanceMiles }
            SortOrder.MOST_MONEY -> filtered.sortedByDescending { it.calculatedDeduction }
            SortOrder.LEAST_MONEY -> filtered.sortedBy { it.calculatedDeduction }
        }

        val totalMiles = sorted.sumOf { it.totalDistanceMiles }
        val totalDeduction = sorted.filter { it.classification == TripClassification.BUSINESS.value }
            .sumOf { it.calculatedDeduction }

        TripHistoryUiState(
            trips = sorted,
            smartNamesMap = namesMap,
            savedLocations = part1.savedLocations,
            vehicles = part1.vehicles,
            totalFilteredMiles = kotlin.math.round(totalMiles * 100.0) / 100.0,
            totalFilteredDeduction = kotlin.math.round(totalDeduction * 100.0) / 100.0,
            totalRecordedTripsCount = part1.allTrips.size,
            availableMonths = part1.allTrips.map { it.date.take(7) }.filter { it.matches(Regex("\\d{4}-\\d{2}")) }.distinct().sortedDescending(),
            searchQuery = part1.query,
            selectedClassificationFilter = part1.classification,
            selectedBusinessEntityFilter = part2.entity,
            selectedVehicleFilter = part2.vehicle,
            selectedSyncStatusFilter = part2.syncStatus,
            startDateFilter = part2.startDate,
            endDateFilter = part2.endDate,
            sortOrder = part3.sortOrder,
            selectedTripForDetails = part3.selectedTrip,
            selectedTripForConfirmationSheet = part3.selectedTripForConfirmationSheet,
            customPurposes = part3.customPurposes,
            recentlyDeletedTrip = undoState.recentlyDeleted,
            restoreFailedTrip = undoState.restoreFailed,
            isRestoring = undoState.isRestoring,
            actionErrorMessage = undoState.actionError
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

    fun setVehicleFilter(vehicleId: String) {
        _vehicleFilter.value = vehicleId
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

    fun selectTripForConfirmationSheet(trip: TripEntity?) {
        _selectedTripForConfirmationSheet.value = trip
    }

    fun dismissConfirmationSheet() {
        _selectedTripForConfirmationSheet.value = null
    }

    fun selectTripById(tripId: String, openConfirmation: Boolean = false): Job =
        viewModelScope.launch {
            val trip = tripRepository.getTripById(tripId)
            if (trip != null) {
                _selectedTripForDetails.value = trip
                if (openConfirmation && (trip.suggestedClassification == "BUSINESS" || trip.suggestedClassification == "PERSONAL")) {
                    _selectedTripForConfirmationSheet.value = trip
                }
            }
        }

    fun confirmSmartSuggestion(
        tripId: String,
        category: String,
        entity: String? = null,
        purpose: String? = null
    ): Job = viewModelScope.launch {
        try {
            val currentTrip = tripRepository.getTripById(tripId)
            if (currentTrip == null) return@launch
            val isBiz = category.equals("BUSINESS", ignoreCase = true)
            val targetEntity = entity?.takeIf { it.isNotBlank() }
                ?: if (isBiz) BusinessEntity.MY_SUCCULENT_CORNER.displayName else BusinessEntity.PERSONAL.displayName
            val targetPurpose = purpose?.takeIf { it.isNotBlank() }
                ?: if (isBiz) TripPurpose.NURSERY_PICKUP else "Personal Drive"

            tripRepository.classifyTrip(
                tripId = tripId,
                classification = if (isBiz) TripClassification.BUSINESS.value else TripClassification.PERSONAL.value,
                businessEntity = targetEntity,
                purpose = targetPurpose,
                notes = currentTrip.notes,
                source = ClassificationSource.SUGGESTED_CONFIRMED
            )

            val updated = tripRepository.getTripById(tripId)
            if (_selectedTripForDetails.value?.id == tripId) {
                _selectedTripForDetails.value = updated
            }
            if (_selectedTripForConfirmationSheet.value?.id == tripId) {
                _selectedTripForConfirmationSheet.value = null
            }
            context?.let { GoogleSheetsSyncWorker.enqueueOneTimeSync(it) }
        } catch (e: Exception) {
            android.util.Log.e("TripHistoryViewModel", "Failed to confirm suggestion: ${e.message}", e)
            _actionErrorMessage.value = "Failed to confirm trip classification."
        }
    }

    fun dismissSmartSuggestion(tripId: String): Job = viewModelScope.launch {
        try {
            tripRepository.dismissSuggestion(tripId)
            val updated = tripRepository.getTripById(tripId)
            if (_selectedTripForDetails.value?.id == tripId) {
                _selectedTripForDetails.value = updated
            }
            if (_selectedTripForConfirmationSheet.value?.id == tripId) {
                _selectedTripForConfirmationSheet.value = null
            }
        } catch (e: Exception) {
            android.util.Log.e("TripHistoryViewModel", "Failed to dismiss suggestion: ${e.message}", e)
        }
    }

    fun updateTripDetails(
        tripId: String,
        classification: String,
        businessEntity: String,
        purpose: String,
        notes: String?,
        onError: ((String) -> Unit)? = null,
        onSuccess: (() -> Unit)? = null
    ): Job = viewModelScope.launch {
            try {
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
                context?.let { GoogleSheetsSyncWorker.enqueueOneTimeSync(it) }
                onSuccess?.invoke()
            } catch (e: Exception) {
                android.util.Log.e("TripHistoryViewModel", "Failed to update trip details: ${e.message}", e)
                val msg = "Unable to save trip changes. Please try again."
                _actionErrorMessage.value = msg
                onError?.invoke(msg)
            }
        }

    fun updateTripVehicle(tripId: String, vehicleId: String?) = viewModelScope.launch {
        try {
            tripRepository.updateVehicleId(tripId, vehicleId)
            val updated = tripRepository.getTripById(tripId)
            if (_selectedTripForDetails.value?.id == tripId) _selectedTripForDetails.value = updated
        } catch (error: Exception) {
            _actionErrorMessage.value = "Unable to update the trip vehicle. Please try again."
        }
    }

    fun clearAllFilters() {
        _searchQuery.value = ""
        _classificationFilter.value = "ALL"
        _businessEntityFilter.value = "ALL"
        _vehicleFilter.value = "ALL"
        _syncStatusFilter.value = "ALL"
        _startDateFilter.value = ""
        _endDateFilter.value = ""
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch {
            deleteMutex.withLock {
                try {
                    val tripToDelete = tripRepository.getTripById(tripId)
                    if (tripToDelete == null) {
                        _actionErrorMessage.value = "Unable to delete trip. Trip not found."
                        return@withLock
                    }

                    // Perform deletion in repository FIRST
                    tripRepository.deleteTrip(tripId)

                    // On verified repository deletion success:
                    if (_selectedTripForDetails.value?.id == tripId) {
                        _selectedTripForDetails.value = null
                    }
                    _recentlyDeletedTrips.update { current ->
                        current.filterNot { it.id == tripId } + tripToDelete
                    }
                    _restoreFailedTrip.value = null
                } catch (e: Exception) {
                    android.util.Log.e("TripHistoryViewModel", "Failed to delete trip: ${e.message}", e)
                    // On failure, retain trip in repository, never set recentlyDeletedTrip or show false success
                    _actionErrorMessage.value = "Unable to delete trip. Please try again."
                }
            }
        }
    }

    fun undoDeleteTrip() {
        viewModelScope.launch {
            if (!restoreMutex.tryLock()) {
                // Prevent duplicate restore requests while one is active
                return@launch
            }
            _isRestoring.value = true
            try {
                val tripToRestore = _recentlyDeletedTrips.value.lastOrNull() ?: return@launch
                try {
                    tripRepository.insertTrip(tripToRestore)
                    // Confirmed restoration: remove restored trip from the undo stack
                    _recentlyDeletedTrips.update { current ->
                        current.filterNot { it.id == tripToRestore.id }
                    }
                    _restoreFailedTrip.value = null
                } catch (e: Exception) {
                    android.util.Log.e("TripHistoryViewModel", "Failed to restore deleted trip: ${e.message}", e)
                    // Preserve the deleted record in the stack and expose failure for Retry action
                    _restoreFailedTrip.value = tripToRestore
                    _actionErrorMessage.value = "Unable to restore trip. Please try again."
                }
            } finally {
                _isRestoring.value = false
                restoreMutex.unlock()
            }
        }
    }

    fun dismissRecentlyDeletedTrip(tripId: String? = null) {
        _recentlyDeletedTrips.update { list ->
            if (tripId != null) {
                list.filterNot { it.id == tripId }
            } else if (list.isNotEmpty()) {
                list.dropLast(1)
            } else {
                emptyList()
            }
        }
    }

    fun clearRecentlyDeletedTrip() {
        dismissRecentlyDeletedTrip()
    }

    fun clearAllRecentlyDeletedTrips() {
        _recentlyDeletedTrips.value = emptyList()
        _restoreFailedTrip.value = null
    }

    fun clearRestoreFailure() {
        _restoreFailedTrip.value = null
    }

    fun clearActionErrorMessage() {
        _actionErrorMessage.value = null
    }

    fun saveLocationFromTrip(
        name: String,
        latitude: Double,
        longitude: Double,
        address: String,
        radiusMeters: Int = 150,
        icon: String = "location",
        classificationPreference: String? = null,
        businessEntity: String? = null,
        onSuccess: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                if (savedLocationRepository == null) {
                    onError?.invoke("Saved location repository unavailable")
                    return@launch
                }
                val now = System.currentTimeMillis()
                val entity = SavedLocationEntity(
                    name = name.trim(),
                    latitude = latitude,
                    longitude = longitude,
                    address = address.trim(),
                    radiusMeters = radiusMeters.coerceIn(50, 500),
                    icon = icon,
                    classificationPreference = classificationPreference,
                    businessEntity = if (classificationPreference == "BUSINESS") businessEntity else null,
                    createdAt = now,
                    updatedAt = now
                )
                savedLocationRepository.insertSavedLocation(entity)
                onSuccess?.invoke(entity.id)
            } catch (e: Exception) {
                onError?.invoke(e.localizedMessage ?: "Failed to save location")
            }
        }
    }

    private data class UndoState(
        val recentlyDeleted: TripEntity?,
        val restoreFailed: TripEntity?,
        val isRestoring: Boolean,
        val actionError: String?
    )

    private data class FilterStatePart1(
        val allTrips: List<TripEntity>,
        val savedLocations: List<SavedLocationEntity>,
        val vehicles: List<VehicleEntity>,
        val query: String,
        val classification: String
    )

    private data class FilterStatePart2(
        val entity: String,
        val vehicle: String,
        val syncStatus: String,
        val startDate: String,
        val endDate: String
    )

    private data class FilterStatePart3(
        val sortOrder: SortOrder,
        val selectedTrip: TripEntity?,
        val selectedTripForConfirmationSheet: TripEntity? = null,
        val customPurposes: List<String> = emptyList()
    )
}
