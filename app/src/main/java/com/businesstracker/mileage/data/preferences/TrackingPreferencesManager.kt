package com.businesstracker.mileage.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import com.businesstracker.mileage.domain.model.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tracking_prefs")

data class VehicleAnchor(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

@Singleton
class TrackingPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_AUTO_TRACKING_ENABLED = booleanPreferencesKey("auto_tracking_enabled")
        private val KEY_DEFAULT_BUSINESS_ENTITY = stringPreferencesKey("default_business_entity")
        private val KEY_VEHICLE_ANCHOR_LAT = doublePreferencesKey("vehicle_anchor_lat")
        private val KEY_VEHICLE_ANCHOR_LNG = doublePreferencesKey("vehicle_anchor_lng")
        private val KEY_VEHICLE_ANCHOR_TIME = longPreferencesKey("vehicle_anchor_time")
        private val KEY_IN_VEHICLE_STATE = booleanPreferencesKey("in_vehicle_state")
        private val KEY_ACTIVE_TRIP_ID = stringPreferencesKey("active_trip_id")
        private val KEY_DISCARDED_TRIP_IDS = stringSetPreferencesKey("discarded_trip_ids")
        private val KEY_BLUETOOTH_AUTO_STOP_ENABLED = booleanPreferencesKey("bluetooth_auto_stop_enabled")
        private val KEY_VEHICLE_BLUETOOTH_ADDRESS = stringPreferencesKey("vehicle_bluetooth_address")
        private val KEY_VEHICLE_BLUETOOTH_NAME = stringPreferencesKey("vehicle_bluetooth_name")
        private val KEY_BLUETOOTH_DISCONNECT_PENDING = booleanPreferencesKey("bluetooth_disconnect_pending")
        private val KEY_APP_THEME_MODE = stringPreferencesKey("app_theme_mode")

        // Cloudflare D1 & Sync Configuration
        private val KEY_CF_ENDPOINT = stringPreferencesKey("cf_endpoint")
        private val KEY_CF_CLIENT_ID = stringPreferencesKey("cf_client_id")
        private val KEY_CF_CLIENT_SECRET = stringPreferencesKey("cf_client_secret")
        private val KEY_CF_BEARER_TOKEN = stringPreferencesKey("cf_bearer_token")
        private val KEY_DEFAULT_VEHICLE = stringPreferencesKey("default_vehicle")
        private val KEY_CUSTOM_CATEGORIES = stringPreferencesKey("custom_categories")

        // Google Sheets Sync Configuration
        private val KEY_SHEETS_CONNECTED = booleanPreferencesKey("sheets_connected")
        private val KEY_SHEETS_SPREADSHEET_ID = stringPreferencesKey("sheets_spreadsheet_id")
        private val KEY_SHEETS_SPREADSHEET_NAME = stringPreferencesKey("sheets_spreadsheet_name")
        private val KEY_SHEETS_AUTH_TOKEN = stringPreferencesKey("sheets_auth_token")
        private val KEY_SHEETS_WEBHOOK_URL = stringPreferencesKey("sheets_webhook_url")
        private val KEY_SHEETS_LAST_SYNC_TIME = longPreferencesKey("sheets_last_sync_time")
        private val KEY_SHEETS_LAST_ERROR = stringPreferencesKey("sheets_last_error")
        private val KEY_SHEETS_AUTO_SYNC_ENABLED = booleanPreferencesKey("sheets_auto_sync_enabled")

        // Work Schedule & Auto-Classification Keys
        private val KEY_SCHEDULE_FILTERING_ENABLED = booleanPreferencesKey("schedule_filtering_enabled")
        private val KEY_WORK_DAYS = stringPreferencesKey("work_days")
        private val KEY_WORK_HOURS_START = stringPreferencesKey("work_hours_start")
        private val KEY_WORK_HOURS_END = stringPreferencesKey("work_hours_end")
        private val KEY_OUTSIDE_SCHEDULE_BEHAVIOR = stringPreferencesKey("outside_schedule_behavior")

        const val BEHAVIOR_CLASSIFY_PERSONAL = "CLASSIFY_PERSONAL"
        const val BEHAVIOR_CONSERVE_BATTERY = "CONSERVE_BATTERY"
        const val DEFAULT_WORK_DAYS = "MON,TUE,WED,THU,FRI"
        const val DEFAULT_WORK_START = "08:00"
        const val DEFAULT_WORK_END = "18:00"

        const val DEFAULT_CF_ENDPOINT = "https://app.mysucculentcorner.com/api/expense/mileage"
    }

    val isAutoTrackingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_TRACKING_ENABLED] ?: true
    }

    val appThemeMode: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        AppThemeMode.fromStoredValue(prefs[KEY_APP_THEME_MODE])
    }

    suspend fun setAppThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_THEME_MODE] = mode.storedValue
        }
    }

    suspend fun setAutoTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_TRACKING_ENABLED] = enabled
        }
    }

    val isBluetoothAutoStopEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLUETOOTH_AUTO_STOP_ENABLED] ?: false
    }

    suspend fun setBluetoothAutoStopEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BLUETOOTH_AUTO_STOP_ENABLED] = enabled
            if (!enabled) prefs[KEY_BLUETOOTH_DISCONNECT_PENDING] = false
        }
    }

    val vehicleBluetoothAddress: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_VEHICLE_BLUETOOTH_ADDRESS] ?: ""
    }

    val vehicleBluetoothName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_VEHICLE_BLUETOOTH_NAME] ?: ""
    }

    suspend fun setVehicleBluetoothDevice(address: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VEHICLE_BLUETOOTH_ADDRESS] = address.trim().uppercase()
            prefs[KEY_VEHICLE_BLUETOOTH_NAME] = name.trim()
            prefs[KEY_BLUETOOTH_DISCONNECT_PENDING] = false
        }
    }

    suspend fun getVehicleBluetoothAddress(): String =
        context.dataStore.data.first()[KEY_VEHICLE_BLUETOOTH_ADDRESS].orEmpty()

    suspend fun isBluetoothAutoStopActive(): Boolean =
        context.dataStore.data.first()[KEY_BLUETOOTH_AUTO_STOP_ENABLED] ?: false

    suspend fun setBluetoothDisconnectPending(pending: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BLUETOOTH_DISCONNECT_PENDING] = pending
        }
    }

    suspend fun isBluetoothDisconnectPending(): Boolean =
        context.dataStore.data.first()[KEY_BLUETOOTH_DISCONNECT_PENDING] ?: false

    // Work Schedule & Drive Filtering
    val isScheduleFilteringEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SCHEDULE_FILTERING_ENABLED] ?: true
    }

    suspend fun setScheduleFilteringEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SCHEDULE_FILTERING_ENABLED] = enabled
        }
    }

    val workDays: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_WORK_DAYS] ?: DEFAULT_WORK_DAYS
    }

    suspend fun setWorkDays(days: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WORK_DAYS] = days
        }
    }

    val workHoursStart: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_WORK_HOURS_START] ?: DEFAULT_WORK_START
    }

    suspend fun setWorkHoursStart(time: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WORK_HOURS_START] = time
        }
    }

    val workHoursEnd: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_WORK_HOURS_END] ?: DEFAULT_WORK_END
    }

    suspend fun setWorkHoursEnd(time: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WORK_HOURS_END] = time
        }
    }

    val outsideScheduleBehavior: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_OUTSIDE_SCHEDULE_BEHAVIOR] ?: BEHAVIOR_CLASSIFY_PERSONAL
    }

    suspend fun setOutsideScheduleBehavior(behavior: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_OUTSIDE_SCHEDULE_BEHAVIOR] = behavior
        }
    }

    suspend fun isWithinWorkSchedule(): Boolean {
        val prefs = context.dataStore.data.first()
        val filteringEnabled = prefs[KEY_SCHEDULE_FILTERING_ENABLED] ?: true
        if (!filteringEnabled) return true

        val now = java.time.LocalDateTime.now()
        val dayCode = when (now.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "MON"
            java.time.DayOfWeek.TUESDAY -> "TUE"
            java.time.DayOfWeek.WEDNESDAY -> "WED"
            java.time.DayOfWeek.THURSDAY -> "THU"
            java.time.DayOfWeek.FRIDAY -> "FRI"
            java.time.DayOfWeek.SATURDAY -> "SAT"
            java.time.DayOfWeek.SUNDAY -> "SUN"
        }

        val activeDays = (prefs[KEY_WORK_DAYS] ?: DEFAULT_WORK_DAYS)
            .split(",")
            .map { it.trim().uppercase() }

        if (dayCode !in activeDays) return false

        val startStr = prefs[KEY_WORK_HOURS_START] ?: DEFAULT_WORK_START
        val endStr = prefs[KEY_WORK_HOURS_END] ?: DEFAULT_WORK_END

        return try {
            val startTime = java.time.LocalTime.parse(startStr)
            val endTime = java.time.LocalTime.parse(endStr)
            val curTime = now.toLocalTime()
            !curTime.isBefore(startTime) && !curTime.isAfter(endTime)
        } catch (_: Exception) {
            true
        }
    }

    suspend fun getOutsideScheduleBehavior(): String {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_OUTSIDE_SCHEDULE_BEHAVIOR] ?: BEHAVIOR_CLASSIFY_PERSONAL
    }

    val defaultBusinessEntity: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_BUSINESS_ENTITY] ?: "MySucculentCorner"
    }

    suspend fun setDefaultBusinessEntity(entity: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_BUSINESS_ENTITY] = entity
        }
    }

    val activeTripId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_TRIP_ID]
    }

    suspend fun setActiveTripId(tripId: String?) {
        context.dataStore.edit { prefs ->
            if (tripId != null) {
                prefs[KEY_ACTIVE_TRIP_ID] = tripId
            } else {
                prefs.remove(KEY_ACTIVE_TRIP_ID)
            }
        }
    }

    suspend fun clearActiveTripId(tripId: String) {
        context.dataStore.edit { prefs ->
            if (prefs[KEY_ACTIVE_TRIP_ID] == tripId) {
                prefs.remove(KEY_ACTIVE_TRIP_ID)
            }
        }
    }

    suspend fun recordDiscardedTrip(tripId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_DISCARDED_TRIP_IDS] ?: emptySet()
            prefs[KEY_DISCARDED_TRIP_IDS] = current + tripId
        }
    }

    suspend fun isTripDiscarded(tripId: String): Boolean {
        val prefs = context.dataStore.data.first()
        return (prefs[KEY_DISCARDED_TRIP_IDS] ?: emptySet()).contains(tripId)
    }

    suspend fun setVehicleAnchor(latitude: Double, longitude: Double, timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VEHICLE_ANCHOR_LAT] = latitude
            prefs[KEY_VEHICLE_ANCHOR_LNG] = longitude
            prefs[KEY_VEHICLE_ANCHOR_TIME] = timestamp
        }
    }

    suspend fun clearVehicleAnchor() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_VEHICLE_ANCHOR_LAT)
            prefs.remove(KEY_VEHICLE_ANCHOR_LNG)
            prefs.remove(KEY_VEHICLE_ANCHOR_TIME)
        }
    }

    suspend fun getVehicleAnchor(): VehicleAnchor? {
        val prefs = context.dataStore.data.first()
        val lat = prefs[KEY_VEHICLE_ANCHOR_LAT] ?: return null
        val lng = prefs[KEY_VEHICLE_ANCHOR_LNG] ?: return null
        val time = prefs[KEY_VEHICLE_ANCHOR_TIME] ?: 0L
        return VehicleAnchor(lat, lng, time)
    }

    suspend fun isInVehicle(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_IN_VEHICLE_STATE] ?: false
    }

    suspend fun setInVehicleState(isInVehicle: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IN_VEHICLE_STATE] = isInVehicle
        }
    }

    // Cloudflare D1 & Sync Preferences
    val cfEndpoint: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CF_ENDPOINT] ?: DEFAULT_CF_ENDPOINT
    }

    suspend fun setCfEndpoint(endpoint: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CF_ENDPOINT] = endpoint
        }
    }

    val cfClientId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CF_CLIENT_ID] ?: ""
    }

    suspend fun setCfClientId(clientId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CF_CLIENT_ID] = clientId
        }
    }

    val cfClientSecret: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CF_CLIENT_SECRET] ?: ""
    }

    suspend fun setCfClientSecret(secret: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CF_CLIENT_SECRET] = secret
        }
    }

    val cfBearerToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CF_BEARER_TOKEN] ?: ""
    }

    suspend fun setCfBearerToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CF_BEARER_TOKEN] = token
        }
    }

    val defaultVehicle: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_VEHICLE] ?: "Tesla Model Y"
    }

    suspend fun setDefaultVehicle(vehicle: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_VEHICLE] = vehicle
        }
    }

    val customCategories: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_CATEGORIES] ?: ""
    }

    suspend fun setCustomCategories(categories: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_CATEGORIES] = categories
        }
    }

    // Google Sheets Sync Flow & Functions
    val isSheetsConnected: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHEETS_CONNECTED] ?: false
    }

    suspend fun setSheetsConnected(connected: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHEETS_CONNECTED] = connected
        }
    }

    val sheetsSpreadsheetId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHEETS_SPREADSHEET_ID] ?: ""
    }

    suspend fun setSheetsSpreadsheetId(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHEETS_SPREADSHEET_ID] = id
        }
    }

    val sheetsSpreadsheetName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHEETS_SPREADSHEET_NAME] ?: "Business Mileage Tracker"
    }

    suspend fun setSheetsSpreadsheetName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHEETS_SPREADSHEET_NAME] = name
        }
    }

    /**
     * Legacy token flow - marked internal/deprecated. Plaintext tokens should not be stored.
     * Use [migrateLegacyAuthToken] to transition existing installations to SecureStorageManager.
     */
    @Deprecated("Use SecureStorageManager for secure credential handling", ReplaceWith("secureStorageManager.getOAuthToken()"))
    val sheetsAuthToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHEETS_AUTH_TOKEN] ?: ""
    }

    @Deprecated("Use SecureStorageManager for secure credential handling", ReplaceWith("secureStorageManager.saveOAuthToken(token)"))
    suspend fun setSheetsAuthToken(token: String) {
        context.dataStore.edit { prefs ->
            if (token.isBlank()) {
                prefs.remove(KEY_SHEETS_AUTH_TOKEN)
            } else {
                prefs[KEY_SHEETS_AUTH_TOKEN] = token
            }
        }
    }

    /**
     * Safely migrates legacy plaintext OAuth token from unencrypted DataStore into [com.businesstracker.mileage.data.security.SecureStorageManager].
     *
     * In accordance with security audit requirements:
     * 1. Reads plaintext token from DataStore (if any).
     * 2. If present and non-blank, attempts to save into Keystore-backed SecureStorageManager.
     * 3. Verifies retrieval: secureStorage.getOAuthToken() == plainToken.
     * 4. Permanently removes the plaintext preference key from DataStore.
     * 5. If verification fails, flags reconnect required and does not leave plaintext token.
     */
    suspend fun migrateLegacyAuthToken(secureStorage: com.businesstracker.mileage.data.security.SecureStorageManager): Boolean {
        val prefs = context.dataStore.data.first()
        val legacyToken = prefs[KEY_SHEETS_AUTH_TOKEN]?.trim().orEmpty()
        if (legacyToken.isBlank()) {
            return true
        }

        val saved = secureStorage.saveOAuthToken(legacyToken)
        val verified = saved && (secureStorage.getOAuthToken() == legacyToken)

        context.dataStore.edit { editPrefs ->
            editPrefs.remove(KEY_SHEETS_AUTH_TOKEN)
            if (!verified) {
                editPrefs[KEY_SHEETS_LAST_ERROR] = "Failed to securely migrate credentials. Please reconnect your account."
                editPrefs[KEY_SHEETS_CONNECTED] = false
            }
        }
        return verified
    }

    val sheetsWebhookUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHEETS_WEBHOOK_URL] ?: ""
    }

    suspend fun setSheetsWebhookUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHEETS_WEBHOOK_URL] = url
        }
    }

    val sheetsLastSyncTime: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHEETS_LAST_SYNC_TIME] ?: 0L
    }

    suspend fun setSheetsLastSyncTime(time: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHEETS_LAST_SYNC_TIME] = time
        }
    }

    val sheetsLastError: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHEETS_LAST_ERROR]
    }

    suspend fun setSheetsLastError(error: String?) {
        context.dataStore.edit { prefs ->
            if (error != null) {
                prefs[KEY_SHEETS_LAST_ERROR] = error
            } else {
                prefs.remove(KEY_SHEETS_LAST_ERROR)
            }
        }
    }

    val isSheetsAutoSyncEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHEETS_AUTO_SYNC_ENABLED] ?: true
    }

    suspend fun setSheetsAutoSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHEETS_AUTO_SYNC_ENABLED] = enabled
        }
    }
}
