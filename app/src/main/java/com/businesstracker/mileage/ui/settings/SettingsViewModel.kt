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

private data class GeneralPrefs(
    val autoTrackingEnabled: Boolean,
    val defaultBusinessEntity: String,
    val defaultVehicle: String,
    val customCategories: String
)

private data class CloudflarePrefs(
    val endpoint: String,
    val clientId: String,
    val clientSecret: String,
    val bearerToken: String
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: TrackingPreferencesManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val generalFlow = combine(
        preferencesManager.isAutoTrackingEnabled,
        preferencesManager.defaultBusinessEntity,
        preferencesManager.defaultVehicle,
        preferencesManager.customCategories
    ) { autoTrack, entity, vehicle, categories ->
        GeneralPrefs(autoTrack, entity, vehicle, categories)
    }

    private val cloudflareFlow = combine(
        preferencesManager.cfEndpoint,
        preferencesManager.cfClientId,
        preferencesManager.cfClientSecret,
        preferencesManager.cfBearerToken
    ) { endpoint, clientId, secret, token ->
        CloudflarePrefs(endpoint, clientId, secret, token)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        generalFlow,
        cloudflareFlow
    ) { general, cf ->
        SettingsUiState(
            autoTrackingEnabled = general.autoTrackingEnabled,
            defaultBusinessEntity = general.defaultBusinessEntity,
            defaultVehicle = general.defaultVehicle,
            customCategories = general.customCategories,
            cfEndpoint = cf.endpoint,
            cfClientId = cf.clientId,
            cfClientSecret = cf.clientSecret,
            cfBearerToken = cf.bearerToken
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
