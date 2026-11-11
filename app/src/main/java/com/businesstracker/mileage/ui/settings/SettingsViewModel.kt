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
import com.businesstracker.mileage.data.remote.RestoreApplyResult
import com.businesstracker.mileage.data.remote.RestorePreviewData
import com.businesstracker.mileage.data.remote.RestorePreviewResult
import com.businesstracker.mileage.data.remote.SavedLocationsSheetsService
import com.businesstracker.mileage.data.remote.SavedLocationsSyncResult
import com.businesstracker.mileage.data.remote.SheetsSyncResult
import com.businesstracker.mileage.data.remote.toMessage
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.domain.model.AppThemeMode
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.transition.ActivityTransitionManager
import com.businesstracker.mileage.worker.GoogleSheetsSyncWorker
import com.businesstracker.mileage.worker.SavedLocationBackupWorker
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
    val savedLocations: List<com.businesstracker.mileage.data.local.entity.SavedLocationEntity> = emptyList(),
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
    val isSyncing: Boolean = false,
    // Saved Locations Google Sheets Backup State
    val savedLocationsLastBackupTime: Long = 0L,
    val savedLocationsLastBackupError: String? = null,
    val savedLocationsPendingBackupCount: Int = 0,
    val isBackingUpSavedLocations: Boolean = false,
    val isLoadingRestorePreview: Boolean = false,
    val isApplyingRestore: Boolean = false,
    val savedLocationsBackupMessage: String? = null,
    val restorePreviewState: SavedLocationsRestorePreviewUi? = null
)

sealed interface SavedLocationsRestorePreviewUi {
    data class Loaded(
        val preview: RestorePreviewData,
        val selectedIds: Set<String>,
        val approvedConflictIds: Set<String>
    ) : SavedLocationsRestorePreviewUi

    data class Empty(
        val localCount: Int,
        val message: String
    ) : SavedLocationsRestorePreviewUi

    data class Failure(
        val message: String
    ) : SavedLocationsRestorePreviewUi
}

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

private data class LocationBackupStatusState(
    val lastBackupTime: Long,
    val lastBackupError: String?,
    val pendingCount: Int,
    val isBackingUp: Boolean,
    val isLoadingRestorePreview: Boolean,
    val isApplyingRestore: Boolean,
    val backupMessage: String?,
    val restorePreview: SavedLocationsRestorePreviewUi?
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: TrackingPreferencesManager,
    private val activityTransitionManager: ActivityTransitionManager,
    private val tripRepository: TripRepository,
    private val sheetsSyncService: GoogleSheetsSyncService,
    private val savedLocationsSheetsService: SavedLocationsSheetsService,
    private val secureStorageManager: SecureStorageManager,
    private val googleAuthManager: com.businesstracker.mileage.data.security.GoogleAuthManager,
    private val savedLocationRepository: com.businesstracker.mileage.domain.repository.SavedLocationRepository,
    private val searchAddressUseCase: com.businesstracker.mileage.domain.usecase.SearchAddressUseCase,
    private val getCurrentLocationUseCase: com.businesstracker.mileage.domain.usecase.GetCurrentLocationUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    private val _sheetsSyncMessage = MutableStateFlow<String?>(null)
    private val _pairedBluetoothDevices = MutableStateFlow<List<VehicleBluetoothDevice>>(emptyList())
    private val _isBackingUpSavedLocations = MutableStateFlow(false)
    private val _isLoadingRestorePreview = MutableStateFlow(false)
    private val _isApplyingRestore = MutableStateFlow(false)
    private val _savedLocationsBackupMessage = MutableStateFlow<String?>(null)
    private val _restorePreviewState = MutableStateFlow<SavedLocationsRestorePreviewUi?>(null)

    private val generalFlow = combine(
        preferencesManager.isAutoTrackingEnabled,
        preferencesManager.defaultVehicle,
        preferencesManager.appThemeMode,
        preferencesManager.customPurposes
    ) { autoTrack, vehicle, themeMode, customPurposes ->
        GeneralPrefs(autoTrack, vehicle, themeMode, customPurposes)
    }

    private val _workHoursStartInput = MutableStateFlow<String?>(null)
    private val _workHoursEndInput = MutableStateFlow<String?>(null)

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
    }.combine(
        combine(_workHoursStartInput, _workHoursEndInput) { startInput, endInput ->
            Pair(startInput, endInput)
        }
    ) { schedule, (startInput, endInput) ->
        schedule.copy(
            startTime = startInput ?: schedule.startTime,
            endTime = endInput ?: schedule.endTime
        )
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

    private val locationBackupPrefsFlow = combine(
        preferencesManager.savedLocationsLastBackupTime,
        preferencesManager.savedLocationsLastBackupError,
        savedLocationRepository.getPendingSyncCountFlow()
    ) { time, err, pending ->
        Triple(time, err, pending)
    }

    private val locationBackupActionsFlow = combine(
        _isBackingUpSavedLocations,
        _isLoadingRestorePreview,
        _isApplyingRestore,
        _savedLocationsBackupMessage,
        _restorePreviewState
    ) { backingUp, loadingPreview, applyingRestore, msg, preview ->
        listOf<Any?>(backingUp, loadingPreview, applyingRestore, msg, preview)
    }

    private val locationBackupStatusFlow = combine(
        locationBackupPrefsFlow,
        locationBackupActionsFlow
    ) { (time, err, pending), actions ->
        LocationBackupStatusState(
            lastBackupTime = time,
            lastBackupError = err,
            pendingCount = pending,
            isBackingUp = actions[0] as Boolean,
            isLoadingRestorePreview = actions[1] as Boolean,
            isApplyingRestore = actions[2] as Boolean,
            backupMessage = actions[3] as String?,
            restorePreview = actions[4] as SavedLocationsRestorePreviewUi?
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
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
        },
        savedLocationRepository.getAllSavedLocations(),
        locationBackupStatusFlow
    ) { baseState, savedLocations, backupStatus ->
        baseState.copy(
            savedLocations = savedLocations,
            savedLocationsLastBackupTime = backupStatus.lastBackupTime,
            savedLocationsLastBackupError = backupStatus.lastBackupError,
            savedLocationsPendingBackupCount = backupStatus.pendingCount,
            isBackingUpSavedLocations = backupStatus.isBackingUp,
            isLoadingRestorePreview = backupStatus.isLoadingRestorePreview,
            isApplyingRestore = backupStatus.isApplyingRestore,
            savedLocationsBackupMessage = backupStatus.backupMessage,
            restorePreviewState = backupStatus.restorePreview
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
        _workHoursStartInput.value = time
        viewModelScope.launch {
            preferencesManager.setWorkHoursStart(time)
        }
    }

    fun setWorkHoursEnd(time: String) {
        _workHoursEndInput.value = time
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
                SavedLocationBackupWorker.enqueueOneTimeBackup(context)
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
                    SavedLocationBackupWorker.enqueueOneTimeBackup(context)
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

    // Saved Locations
    suspend fun searchAddress(query: String) = searchAddressUseCase(query)

    suspend fun getCurrentLocation() = getCurrentLocationUseCase()

    private val locationSaveGate = SavedLocationSaveGate()

    fun saveLocation(
        id: String? = null,
        name: String,
        latitude: Double,
        longitude: Double,
        address: String,
        radiusMeters: Int = 150,
        icon: String = "location",
        classificationPreference: String? = null,
        businessEntity: String? = null,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (!locationSaveGate.tryBegin()) return
        viewModelScope.launch {
            try {
                if (name.isBlank()) {
                    onError?.invoke("Name cannot be empty")
                    return@launch
                }
                val now = System.currentTimeMillis()
                val entity = if (id != null) {
                    val existing = savedLocationRepository.getSavedLocationById(id)
                    com.businesstracker.mileage.data.local.entity.SavedLocationEntity(
                        id = id,
                        name = name.trim(),
                        latitude = latitude,
                        longitude = longitude,
                        address = address.trim(),
                        radiusMeters = radiusMeters.coerceIn(50, 500),
                        icon = icon,
                        classificationPreference = classificationPreference,
                        businessEntity = if (classificationPreference == "BUSINESS") businessEntity else null,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now
                    )
                } else {
                    com.businesstracker.mileage.data.local.entity.SavedLocationEntity(
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
                }
                savedLocationRepository.insertSavedLocation(entity)
                onSuccess?.invoke()
            } catch (e: Exception) {
                onError?.invoke("Could not save location. Please try again.")
            } finally {
                locationSaveGate.end()
            }
        }
    }

    fun deleteLocation(
        location: com.businesstracker.mileage.data.local.entity.SavedLocationEntity,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                savedLocationRepository.deleteSavedLocation(location)
                onSuccess?.invoke()
            } catch (e: Exception) {
                onError?.invoke(e.localizedMessage ?: "Failed to delete location")
            }
        }
    }

    fun backUpSavedLocationsNow() {
        if (_isBackingUpSavedLocations.value) return
        _isBackingUpSavedLocations.value = true
        _savedLocationsBackupMessage.value = null
        viewModelScope.launch {
            try {
                when (val result = savedLocationsSheetsService.backupSavedLocations(fullSnapshot = true)) {
                    is SavedLocationsSyncResult.Success -> {
                        val total = result.uploadedCount + result.deletedCount
                        _savedLocationsBackupMessage.value = if (total > 0) {
                            "Backed up ${result.uploadedCount} location${if (result.uploadedCount == 1) "" else "s"}" +
                                if (result.deletedCount > 0) " and removed ${result.deletedCount} deleted" else ""
                        } else {
                            "Saved Locations backup is up to date."
                        }
                    }
                    is SavedLocationsSyncResult.NotConfigured -> {
                        _savedLocationsBackupMessage.value = "Connect Google Sheets first to back up Saved Locations."
                    }
                    else -> {
                        _savedLocationsBackupMessage.value = result.toMessage()
                    }
                }
            } finally {
                _isBackingUpSavedLocations.value = false
            }
        }
    }

    fun restoreSavedLocationsFromSheets() {
        if (_isLoadingRestorePreview.value || _isApplyingRestore.value) return
        _isLoadingRestorePreview.value = true
        _savedLocationsBackupMessage.value = null
        viewModelScope.launch {
            try {
                when (val result = savedLocationsSheetsService.previewRestoreFromSheets()) {
                    is RestorePreviewResult.Success -> {
                        val defaultSelected = result.preview.candidates
                            .filter { it.selectedByDefault }
                            .map { it.cloudLocation.id }
                            .toSet()
                        _restorePreviewState.value = SavedLocationsRestorePreviewUi.Loaded(
                            preview = result.preview,
                            selectedIds = defaultSelected,
                            approvedConflictIds = emptySet()
                        )
                    }
                    is RestorePreviewResult.EmptyCloudSheet -> {
                        _restorePreviewState.value = SavedLocationsRestorePreviewUi.Empty(
                            localCount = result.localCount,
                            message = result.message
                        )
                    }
                    is RestorePreviewResult.Error -> {
                        _restorePreviewState.value = SavedLocationsRestorePreviewUi.Failure(
                            message = result.failure.toMessage()
                        )
                    }
                }
            } finally {
                _isLoadingRestorePreview.value = false
            }
        }
    }

    fun toggleRestoreCandidateSelection(locationId: String) {
        val current = _restorePreviewState.value as? SavedLocationsRestorePreviewUi.Loaded ?: return
        val candidate = current.preview.candidates.firstOrNull { it.cloudLocation.id == locationId } ?: return
        val nextSelected = current.selectedIds.toMutableSet()
        val nextApproved = current.approvedConflictIds.toMutableSet()

        if (locationId in nextSelected) {
            nextSelected.remove(locationId)
            nextApproved.remove(locationId)
        } else {
            nextSelected.add(locationId)
            if (candidate.requiresOverwriteApproval) {
                nextApproved.add(locationId)
            }
        }

        _restorePreviewState.value = current.copy(
            selectedIds = nextSelected,
            approvedConflictIds = nextApproved
        )
    }

    fun setOverwriteConflictsApproved(approveAll: Boolean) {
        val current = _restorePreviewState.value as? SavedLocationsRestorePreviewUi.Loaded ?: return
        val conflictIds = current.preview.candidates
            .filter { it.requiresOverwriteApproval }
            .map { it.cloudLocation.id }
            .toSet()

        val nextSelected = current.selectedIds.toMutableSet()
        val nextApproved = current.approvedConflictIds.toMutableSet()
        if (approveAll) {
            nextSelected.addAll(conflictIds)
            nextApproved.addAll(conflictIds)
        } else {
            nextSelected.removeAll(conflictIds)
            nextApproved.removeAll(conflictIds)
        }
        _restorePreviewState.value = current.copy(
            selectedIds = nextSelected,
            approvedConflictIds = nextApproved
        )
    }

    fun dismissRestorePreview() {
        _restorePreviewState.value = null
    }

    fun clearSavedLocationsBackupMessage() {
        _savedLocationsBackupMessage.value = null
    }

    fun confirmApplySavedLocationsRestore() {
        val current = _restorePreviewState.value as? SavedLocationsRestorePreviewUi.Loaded ?: return
        if (_isApplyingRestore.value) return
        _isApplyingRestore.value = true
        viewModelScope.launch {
            try {
                when (val result = savedLocationsSheetsService.applyRestoreSelection(
                    preview = current.preview,
                    selectedCandidateIds = current.selectedIds,
                    approvedConflictIds = current.approvedConflictIds
                )) {
                    is RestoreApplyResult.Success -> {
                        _restorePreviewState.value = null
                        _savedLocationsBackupMessage.value =
                            "Restored ${result.importedCount} new, ${result.updatedCount} overwritten" +
                                if (result.preservedLocalCount > 0) " (${result.preservedLocalCount} local kept)" else ""
                    }
                    is RestoreApplyResult.NothingSelected -> {
                        _restorePreviewState.value = null
                        _savedLocationsBackupMessage.value = "No locations selected to restore. Local records preserved."
                    }
                    is RestoreApplyResult.Failed -> {
                        _restorePreviewState.value = SavedLocationsRestorePreviewUi.Failure(result.message)
                    }
                }
            } finally {
                _isApplyingRestore.value = false
            }
        }
    }
}
