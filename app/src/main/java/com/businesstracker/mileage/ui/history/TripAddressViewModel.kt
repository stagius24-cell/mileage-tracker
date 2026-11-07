package com.businesstracker.mileage.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.usecase.ResolveTripAddressesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripAddressState(val tripId: String = "", val start: String? = null, val end: String? = null, val loading: Boolean = false)

@HiltViewModel
class TripAddressViewModel @Inject constructor(
    private val resolveAddresses: ResolveTripAddressesUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(TripAddressState())
    val state = _state.asStateFlow()
    private var lookup: Job? = null

    fun resolve(trip: TripEntity) {
        lookup?.cancel()
        _state.value = TripAddressState(trip.id, trip.startAddress, trip.endAddress, true)
        lookup = viewModelScope.launch {
            try {
                val resolved = resolveAddresses.execute(trip.id)
                _state.value = TripAddressState(trip.id, resolved?.startAddress ?: trip.startAddress, resolved?.endAddress ?: trip.endAddress)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = TripAddressState(trip.id, trip.startAddress, trip.endAddress)
            }
        }
    }

    fun cancelLookup() { lookup?.cancel() }
}
