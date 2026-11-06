package com.businesstracker.mileage.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.remote.GoogleSheetsSyncService
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.transition.ActivityTransitionManager
import com.businesstracker.mileage.worker.GoogleSheetsSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
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
    val defaultVehicle: String = "Tesla Model Y",
    // Google Sheets Sync State
    val isSheetsConnected: Boolean = false,
    val sheetsSpreadsheetId: String = "",
    val sheetsSpreadsheetName: String = "Business Mileage Tracker",
    val sheetsAuthToken: String = "",
    val sheetsWebhookUrl: String = "",
    val sheetsLastSyncTime: Long = 0L,
    val sheetsLastError: String? = null,
    val pendingTripsCount: Int = 0,
    val isSyncing: Boolean = false
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

private data class SheetsPrefs(
    val connected: Boolean,
    val spreadsheetId: String,
    val spreadsheetName: String,
    val authToken: String,
    val webhookUrl: String,
    val lastSyncTime: Long,
    val lastError: String?
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: TrackingPreferencesManager,
    private val activityTransitionManager: ActivityTransitionManager,
    private val tripRepository: TripRepository,
    private val sheetsSyncService: GoogleSheetsSyncService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)

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

    private val sheetsFlow = combine(
        preferencesManager.isSheetsConnected,
        preferencesManager.sheetsSpreadsheetId,
        preferencesManager.sheetsSpreadsheetName,
        preferencesManager.sheetsAuthToken,
        preferencesManager.sheetsWebhookUrl
    ) { connected, id, name, token, webhook ->
        Tuple5(connected, id, name, token, webhook)
    }.combine(
        combine(preferencesManager.sheetsLastSyncTime, preferencesManager.sheetsLastError) { time, err ->
            Pair(time, err)
        }
    ) { t5, (time, err) ->
        SheetsPrefs(t5.v1, t5.v2, t5.v3, t5.v4, t5.v5, time, err)
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
        cloudflareFlow,
        sheetsFlow,
        _isSyncing
    ) { general, schedule, cf, sheets, isSyncing ->
        val hasBg = hasBackgroundLocationPermission()
        val isReady = general.autoTrackingEnabled && hasRequiredPermissions()
        val pendingCount = tripRepository.getPendingSyncTrips().count { it.classification != "UNREVIEWED" }

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
            cfBearerToken = cf.bearerToken,
            isSheetsConnected = sheets.connected,
            sheetsSpreadsheetId = sheets.spreadsheetId,
            sheetsSpreadsheetName = sheets.spreadsheetName,
            sheetsAuthToken = sheets.authToken,
            sheetsWebhookUrl = sheets.webhookUrl,
            sheetsLastSyncTime = sheets.lastSyncTime,
            sheetsLastError = sheets.lastError,
            pendingTripsCount = pendingCount,
            isSyncing = isSyncing
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

    fun setWorkDays(days: Set<String>) {
        viewModelScope.launch {
            preferencesManager.setWorkDays(days.joinToString(","))
        }
    }

    fun setWorkHoursStart(time: String) {
        viewModelScope.launch {
            preferencesManager.setWorkHoursStart(time)
        }
    }

    fun setWorkHoursEnd(time: String) {
        viewModelScope.launch {
            preferencesManager.setWorkHoursEnd(time)
        }
    }

    fun setOutsideScheduleBehavior(behavior: String) {
        viewModelScope.launch {
            preferencesManager.setOutsideScheduleBehavior(behavior)
        }
    }

    fun setDefaultVehicle(vehicle: String) {
        viewModelScope.launch {
            preferencesManager.setDefaultVehicle(vehicle)
        }
    }

    // Google Sheets Actions
    fun setSheetsSpreadsheetId(id: String) {
        viewModelScope.launch {
            preferencesManager.setSheetsSpreadsheetId(id)
            if (id.isNotBlank()) preferencesManager.setSheetsConnected(true)
        }
    }

    fun setSheetsSpreadsheetName(name: String) {
        viewModelScope.launch {
            preferencesManager.setSheetsSpreadsheetName(name)
        }
    }

    fun setSheetsAuthToken(token: String) {
        viewModelScope.launch {
            preferencesManager.setSheetsAuthToken(token)
            if (token.isNotBlank()) preferencesManager.setSheetsConnected(true)
        }
    }

    fun setSheetsWebhookUrl(url: String) {
        viewModelScope.launch {
            preferencesManager.setSheetsWebhookUrl(url)
            if (url.isNotBlank()) preferencesManager.setSheetsConnected(true)
        }
    }

    fun connectSheets() {
        viewModelScope.launch {
            preferencesManager.setSheetsConnected(true)
            GoogleSheetsSyncWorker.enqueueOneTimeSync(context)
            GoogleSheetsSyncWorker.enqueuePeriodicSync(context)
        }
    }

    fun disconnectSheets() {
        viewModelScope.launch {
            preferencesManager.setSheetsConnected(false)
            preferencesManager.setSheetsAuthToken("")
            preferencesManager.setSheetsWebhookUrl("")
            preferencesManager.setSheetsLastError(null)
        }
    }

    fun syncSheetsNow() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        viewModelScope.launch {
            try {
                sheetsSyncService.syncPendingTrips()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // Cloudflare D1 Preferences
    fun setCfEndpoint(endpoint: String) {
        viewModelScope.launch { preferencesManager.setCfEndpoint(endpoint) }
    }

    fun setCfClientId(clientId: String) {
        viewModelScope.launch { preferencesManager.setCfClientId(clientId) }
    }

    fun setCfClientSecret(secret: String) {
        viewModelScope.launch { preferencesManager.setCfClientSecret(secret) }
    }

    fun setCfBearerToken(token: String) {
        viewModelScope.launch { preferencesManager.setCfBearerToken(token) }
    }

    fun triggerSyncNow() {
        com.businesstracker.mileage.worker.MileageSyncWorker.enqueueOneTimeSync(context)
        syncSheetsNow()
    }

    private data class Tuple5<A, B, C, D, E>(
        val v1: A, val v2: B, val v3: C, val v4: D, val v5: E
    )
}
