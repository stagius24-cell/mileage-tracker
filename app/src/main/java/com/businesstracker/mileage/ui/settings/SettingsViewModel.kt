package com.businesstracker.mileage.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.worker.MileageSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val autoTrackingEnabled: Boolean = true,
    val defaultBusinessEntity: String = "MySucculentCorner",
    val cfEndpoint: String = TrackingPreferencesManager.DEFAULT_CF_ENDPOINT,
    val cfClientId: String = "",
    val cfClientSecret: String = "",
    val cfBearerToken: String = "",
    val defaultVehicle: String = "",
    val customCategories: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: TrackingPreferencesManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesManager.isAutoTrackingEnabled,
        preferencesManager.defaultBusinessEntity,
        preferencesManager.cfEndpoint,
        preferencesManager.cfClientId,
        preferencesManager.cfClientSecret,
        preferencesManager.cfBearerToken,
        preferencesManager.defaultVehicle,
        preferencesManager.customCategories
    ) { autoTrack, entity, endpoint, clientId, secret, token, vehicle, categories ->
        SettingsUiState(
            autoTrackingEnabled = autoTrack,
            defaultBusinessEntity = entity,
            cfEndpoint = endpoint,
            cfClientId = clientId,
            cfClientSecret = secret,
            cfBearerToken = token,
            defaultVehicle = vehicle,
            customCategories = categories
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setAutoTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoTrackingEnabled(enabled)
        }
    }

    fun setDefaultBusinessEntity(entity: String) {
        viewModelScope.launch {
            preferencesManager.setDefaultBusinessEntity(entity)
        }
    }

    fun setCfEndpoint(endpoint: String) {
        viewModelScope.launch {
            preferencesManager.setCfEndpoint(endpoint)
        }
    }

    fun setCfClientId(clientId: String) {
        viewModelScope.launch {
            preferencesManager.setCfClientId(clientId)
        }
    }

    fun setCfClientSecret(secret: String) {
        viewModelScope.launch {
            preferencesManager.setCfClientSecret(secret)
        }
    }

    fun setCfBearerToken(token: String) {
        viewModelScope.launch {
            preferencesManager.setCfBearerToken(token)
        }
    }

    fun setDefaultVehicle(vehicle: String) {
        viewModelScope.launch {
            preferencesManager.setDefaultVehicle(vehicle)
        }
    }

    fun setCustomCategories(categories: String) {
        viewModelScope.launch {
            preferencesManager.setCustomCategories(categories)
        }
    }

    fun triggerSyncNow() {
        MileageSyncWorker.enqueueOneTimeSync(context)
    }
}
