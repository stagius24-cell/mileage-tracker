package com.businesstracker.mileage.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.transition.ActivityTransitionManager
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
    val hasBackgroundLocationPermission: Boolean = true,
    val isAutoTrackingReady: Boolean = true,
    val scheduleFilteringEnabled: Boolean = true,
    val workDays: Set<String> = setOf("MON", "TUE", "WED", "THU", "FRI"),
    val workHoursStart: String = "08:00",
    val workHoursEnd: String = "18:00",
    val outsideScheduleBehavior: String = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL,
    val cfEndpoint: String = TrackingPreferencesManager.DEFAULT_CF_ENDPOINT,
    val cfClientId: String = "",
    val cfClientSecret: String = "",
    val cfBearerToken: String = "",
    val defaultVehicle: String = "Tesla Model Y"
)

private data class GeneralPrefs(
    val autoTrackingEnabled: Boolean,
    val defaultVehicle: String
)

private data class SchedulePrefs(
    val filteringEnabled: Boolean,
    val workDays: Set<String>,
    val startTime: String,
    val endTime: String,
    val behavior: String
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
    private val activityTransitionManager: ActivityTransitionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val generalFlow = combine(
        preferencesManager.isAutoTrackingEnabled,
        preferencesManager.defaultVehicle
    ) { autoTrack, vehicle ->
        GeneralPrefs(autoTrack, vehicle)
    }

    private val scheduleFlow = combine(
        preferencesManager.isScheduleFilteringEnabled,
        preferencesManager.workDays,
        preferencesManager.workHoursStart,
        preferencesManager.workHoursEnd,
        preferencesManager.outsideScheduleBehavior
    ) { enabled, daysStr, start, end, behavior ->
        val daysSet = daysStr.split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        SchedulePrefs(enabled, daysSet, start, end, behavior)
    }

    private val cloudflareFlow = combine(
        preferencesManager.cfEndpoint,
        preferencesManager.cfClientId,
        preferencesManager.cfClientSecret,
        preferencesManager.cfBearerToken
    ) { endpoint, clientId, secret, token ->
        CloudflarePrefs(endpoint, clientId, secret, token)
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasActivity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        return hasFine && hasActivity && hasBackgroundLocationPermission()
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        generalFlow,
        scheduleFlow,
        cloudflareFlow
    ) { general, schedule, cf ->
        val hasBg = hasBackgroundLocationPermission()
        val isReady = general.autoTrackingEnabled && hasRequiredPermissions()
        SettingsUiState(
            autoTrackingEnabled = general.autoTrackingEnabled,
            hasBackgroundLocationPermission = hasBg,
            isAutoTrackingReady = isReady,
            scheduleFilteringEnabled = schedule.filteringEnabled,
            workDays = schedule.workDays,
            workHoursStart = schedule.startTime,
            workHoursEnd = schedule.endTime,
            outsideScheduleBehavior = schedule.behavior,
            defaultVehicle = general.defaultVehicle,
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
            try {
                if (enabled) {
                    activityTransitionManager.registerTransitions()
                } else {
                    activityTransitionManager.unregisterTransitions()
                }
            } catch (_: Exception) {}
        }
    }

    fun setScheduleFilteringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setScheduleFilteringEnabled(enabled)
        }
    }

    fun toggleWorkDay(day: String) {
        viewModelScope.launch {
            val current = uiState.value.workDays.toMutableSet()
            if (current.contains(day)) {
                if (current.size > 1) { // keep at least 1 day
                    current.remove(day)
                }
            } else {
                current.add(day)
            }
            // Preserve standard order: MON, TUE, WED, THU, FRI, SAT, SUN
            val order = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
            val ordered = order.filter { current.contains(it) }
            preferencesManager.setWorkDays(ordered.joinToString(","))
        }
    }

    fun setWorkHoursStart(start: String) {
        viewModelScope.launch {
            preferencesManager.setWorkHoursStart(start)
        }
    }

    fun setWorkHoursEnd(end: String) {
        viewModelScope.launch {
            preferencesManager.setWorkHoursEnd(end)
        }
    }

    fun setOutsideScheduleBehavior(behavior: String) {
        viewModelScope.launch {
            preferencesManager.setOutsideScheduleBehavior(behavior)
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

    fun triggerSyncNow() {
        MileageSyncWorker.enqueueOneTimeSync(context)
    }
}
