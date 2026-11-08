package com.businesstracker.mileage.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.remote.GoogleSheetsSyncService
import com.businesstracker.mileage.data.remote.SheetsSyncResult
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.domain.model.AppThemeMode
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.transition.ActivityTransitionManager
import com.businesstracker.mileage.worker.GoogleSheetsSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: AppThemeMode = AppThemeMode.AUTO,
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
    val bluetoothAutoStopEnabled: Boolean = false,
    val vehicleBluetoothAddress: String = "",
    val vehicleBluetoothName: String = "",
    val pairedBluetoothDevices: List<VehicleBluetoothDevice> = emptyList(),
    val customPurposes: List<String> = emptyList(),
    // Google Sheets Sync State - No raw tokens exposed in UI state
    val isSheetsConnected: Boolean = false,
    val hasOAuthCredential: Boolean = false,
    val connectedAccountEmail: String? = null,
    val sheetsSpreadsheetId: String = "",
    val sheetsSpreadsheetName: String = "Business Mileage Tracker",
    val sheetsWebhookUrl: String = "",
    val sheetsLastSyncTime: Long = 0L,
    val sheetsLastError: String? = null,
    val sheetsSyncMessage: String? = null,
    val pendingTripsCount: Int = 0,
    val isSyncing: Boolean = false
)

data class VehicleBluetoothDevice(
    val address: String,
    val name: String
)

private data class GeneralPrefs(
    val autoTrackingEnabled: Boolean,
    val defaultVehicle: String,
    val themeMode: AppThemeMode,
    val customPurposes: List<String> = emptyList()
)

private data class BluetoothPrefs(
    val enabled: Boolean,
    val address: String,
    val name: String
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
    val spreadsheetId: String,
    val spreadsheetName: String,
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
    private val secureStorageManager: SecureStorageManager,
    private val googleAuthManager: com.businesstracker.mileage.data.security.GoogleAuthManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    private val _sheetsSyncMessage = MutableStateFlow<String?>(null)
    private val _pairedBluetoothDevices = MutableStateFlow<List<VehicleBluetoothDevice>>(emptyList())

    private val generalFlow = combine(
        preferencesManager.isAutoTrackingEnabled,
        preferencesManager.defaultVehicle,
        preferencesManager.appThemeMode,
        preferencesManager.customPurposes
    ) { autoTrack, vehicle, themeMode, customPurposes ->
        GeneralPrefs(autoTrack, vehicle, themeMode, customPurposes)
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

    private val bluetoothFlow = combine(
        preferencesManager.isBluetoothAutoStopEnabled,
        preferencesManager.vehicleBluetoothAddress,
        preferencesManager.vehicleBluetoothName
    ) { enabled, address, name ->
        BluetoothPrefs(enabled, address, name)
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
        preferencesManager.sheetsSpreadsheetId,
        preferencesManager.sheetsSpreadsheetName,
        preferencesManager.sheetsWebhookUrl,
        preferencesManager.sheetsLastSyncTime,
        preferencesManager.sheetsLastError
    ) { id, name, webhook, time, err ->
        SheetsPrefs(id, name, webhook, time, err)
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

    private data class ConfigState(
        val general: GeneralPrefs,
        val schedule: SchedulePrefs,
        val cloudflare: CloudflarePrefs,
        val bluetooth: BluetoothPrefs
    )

    private val configFlow = combine(
        generalFlow,
        scheduleFlow,
        cloudflareFlow,
        bluetoothFlow
    ) { general, schedule, cf, bluetooth ->
        ConfigState(general, schedule, cf, bluetooth)
    }

    private val pendingCountFlow = tripRepository.getAllTrips().map { allTrips ->
        allTrips.count { it.syncStatus == "PENDING" && it.classification != "UNREVIEWED" }
    }

    private data class SyncStatusState(
        val isSyncing: Boolean,
        val syncMessage: String?
    )

    private val syncStatusFlow = combine(_isSyncing, _sheetsSyncMessage) { isSyncing, syncMsg ->
        SyncStatusState(isSyncing, syncMsg)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        configFlow,
        sheetsFlow,
        pendingCountFlow,
        syncStatusFlow,
        _pairedBluetoothDevices
    ) { config, sheets, pendingCount, syncStatus, pairedDevices ->
        val hasBg = hasBackgroundLocationPermission()
        val isReady = config.general.autoTrackingEnabled && hasRequiredPermissions()

        val hasOAuth = secureStorageManager.hasOAuthToken()
        val accountEmail = secureStorageManager.getAccountEmail()
        val isConnected = (hasOAuth && sheets.spreadsheetId.isNotBlank()) || sheets.webhookUrl.isNotBlank()

        SettingsUiState(
            themeMode = config.general.themeMode,
            autoTrackingEnabled = config.general.autoTrackingEnabled,
            hasBackgroundLocationPermission = hasBg,
            isAutoTrackingReady = isReady,
            scheduleFilteringEnabled = config.schedule.filteringEnabled,
            workDays = config.schedule.workDays,
            workHoursStart = config.schedule.startTime,
            workHoursEnd = config.schedule.endTime,
            outsideScheduleBehavior = config.schedule.behavior,
            defaultVehicle = config.general.defaultVehicle,
            bluetoothAutoStopEnabled = config.bluetooth.enabled,
            vehicleBluetoothAddress = config.bluetooth.address,
            vehicleBluetoothName = config.bluetooth.name,
            pairedBluetoothDevices = pairedDevices,
            customPurposes = config.general.customPurposes,
            cfEndpoint = config.cloudflare.endpoint,
            cfClientId = config.cloudflare.clientId,
            cfClientSecret = config.cloudflare.clientSecret,
            cfBearerToken = config.cloudflare.bearerToken,
            isSheetsConnected = isConnected,
            hasOAuthCredential = hasOAuth,
            connectedAccountEmail = accountEmail,
            sheetsSpreadsheetId = sheets.spreadsheetId,
            sheetsSpreadsheetName = sheets.spreadsheetName,
            sheetsWebhookUrl = sheets.webhookUrl,
            sheetsLastSyncTime = sheets.lastSyncTime,
            sheetsLastError = sheets.lastError,
            sheetsSyncMessage = syncStatus.syncMessage,
            pendingTripsCount = pendingCount,
            isSyncing = syncStatus.isSyncing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
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

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            preferencesManager.setAppThemeMode(mode)
        }
    }

    fun setScheduleFilteringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setScheduleFilteringEnabled(enabled)
        }
    }

    fun addCustomPurpose(purpose: String) {
        viewModelScope.launch {
            preferencesManager.addCustomPurpose(purpose)
        }
    }

    fun removeCustomPurpose(purpose: String) {
        viewModelScope.launch {
            preferencesManager.removeCustomPurpose(purpose)
        }
    }

    fun updateCustomPurpose(oldPurpose: String, newPurpose: String) {
        viewModelScope.launch {
            preferencesManager.updateCustomPurpose(oldPurpose, newPurpose)
        }
    }


    fun setWorkDays(days: Set<String>) {
        viewModelScope.launch {
            preferencesManager.setWorkDays(days.joinToString(","))
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

    fun setBluetoothAutoStopEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setBluetoothAutoStopEnabled(enabled)
        }
    }

    fun selectVehicleBluetoothDevice(device: VehicleBluetoothDevice) {
        viewModelScope.launch {
            preferencesManager.setVehicleBluetoothDevice(device.address, device.name)
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedBluetoothDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            _pairedBluetoothDevices.value = emptyList()
            return
        }

        _pairedBluetoothDevices.value = try {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            adapter?.bondedDevices.orEmpty()
                .map { device ->
                    VehicleBluetoothDevice(
                        address = device.address,
                        name = device.name?.takeIf { it.isNotBlank() } ?: "Paired device"
                    )
                }
                .sortedBy { it.name.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Google Sheets Actions
    fun setSheetsSpreadsheetId(id: String) {
        viewModelScope.launch {
            preferencesManager.setSheetsSpreadsheetId(id)
            val hasOAuth = secureStorageManager.hasOAuthToken()
            val isConn = (hasOAuth && id.trim().isNotBlank()) || uiState.value.sheetsWebhookUrl.isNotBlank()
            preferencesManager.setSheetsConnected(isConn)
        }
    }

    fun setSheetsSpreadsheetName(name: String) {
        viewModelScope.launch {
            preferencesManager.setSheetsSpreadsheetName(name.trim())
        }
    }

    /**
     * Stores OAuth token securely into hardware-backed Android Keystore.
     */
    fun saveOAuthToken(token: String) {
        viewModelScope.launch {
            secureStorageManager.saveOAuthToken(token.trim())
            val id = uiState.value.sheetsSpreadsheetId
            val isConn = token.isNotBlank() && id.isNotBlank()
            preferencesManager.setSheetsConnected(isConn)
            if (isConn) {
                preferencesManager.setSheetsLastError(null)
                GoogleSheetsSyncWorker.enqueueOneTimeSync(context)
                GoogleSheetsSyncWorker.enqueuePeriodicSync(context)
            }
        }
    }

    fun setSheetsWebhookUrl(url: String) {
        viewModelScope.launch {
            val trimmed = url.trim()
            preferencesManager.setSheetsWebhookUrl(trimmed)
            val hasOAuth = secureStorageManager.hasOAuthToken()
            val isConn = (hasOAuth && uiState.value.sheetsSpreadsheetId.isNotBlank()) || trimmed.isNotBlank()
            preferencesManager.setSheetsConnected(isConn)
            if (trimmed.isNotBlank() && !trimmed.startsWith("https://", ignoreCase = true)) {
                preferencesManager.setSheetsLastError("Webhook URL must use secure HTTPS")
            } else {
                preferencesManager.setSheetsLastError(null)
            }
        }
    }

    fun getGoogleSignInClient() = googleAuthManager.getSignInClient(context)

    fun handleGoogleSignInResult(data: android.content.Intent?) {
        viewModelScope.launch {
            val result = googleAuthManager.handleSignInResult(data)
            if (result.isSuccess) {
                val id = uiState.value.sheetsSpreadsheetId
                if (id.isNotBlank()) {
                    GoogleSheetsSyncWorker.enqueueOneTimeSync(context)
                    GoogleSheetsSyncWorker.enqueuePeriodicSync(context)
                }
            }
        }
    }

    fun disconnectSheets() {
        viewModelScope.launch {
            googleAuthManager.disconnect(context)
        }
    }

    fun syncSheetsNow() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        _sheetsSyncMessage.value = null
        viewModelScope.launch {
            try {
                when (val result = sheetsSyncService.syncPendingTrips()) {
                    is SheetsSyncResult.Success -> {
                        val count = result.insertedCount + result.updatedCount
                        _sheetsSyncMessage.value = if (count > 0) {
                            "Synced $count trip${if (count > 1) "s" else ""} successfully!"
                        } else {
                            "All trips are up to date."
                        }
                    }
                    is SheetsSyncResult.PartialSuccess -> {
                        _sheetsSyncMessage.value = "Partially synced: ${result.successCount} succeeded, ${result.failedCount} failed."
                    }
                    is SheetsSyncResult.NotConfigured -> {
                        _sheetsSyncMessage.value = "Google Sheets not configured. Connect your Google account."
                    }
                    is SheetsSyncResult.TokenExpired -> {
                        _sheetsSyncMessage.value = result.message
                    }
                    is SheetsSyncResult.AccessDenied -> {
                        _sheetsSyncMessage.value = result.message
                    }
                    is SheetsSyncResult.NotFound -> {
                        _sheetsSyncMessage.value = result.message
                    }
                    is SheetsSyncResult.RateLimited -> {
                        _sheetsSyncMessage.value = result.message
                    }
                    is SheetsSyncResult.ServerError -> {
                        _sheetsSyncMessage.value = result.message
                    }
                    is SheetsSyncResult.HttpError -> {
                        _sheetsSyncMessage.value = result.message
                    }
                    is SheetsSyncResult.NetworkError -> {
                        _sheetsSyncMessage.value = result.message
                    }
                    is SheetsSyncResult.InvalidConfiguration -> {
                        _sheetsSyncMessage.value = result.message
                    }
                    is SheetsSyncResult.UnexpectedError -> {
                        _sheetsSyncMessage.value = result.exception.localizedMessage ?: "Unexpected error during sync."
                    }
                }
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
}
