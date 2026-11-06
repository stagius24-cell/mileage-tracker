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

        // Cloudflare D1 & Sync Configuration
        private val KEY_CF_ENDPOINT = stringPreferencesKey("cf_endpoint")
        private val KEY_CF_CLIENT_ID = stringPreferencesKey("cf_client_id")
        private val KEY_CF_CLIENT_SECRET = stringPreferencesKey("cf_client_secret")
        private val KEY_CF_BEARER_TOKEN = stringPreferencesKey("cf_bearer_token")
        private val KEY_DEFAULT_VEHICLE = stringPreferencesKey("default_vehicle")
        private val KEY_CUSTOM_CATEGORIES = stringPreferencesKey("custom_categories")

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

    suspend fun setAutoTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_TRACKING_ENABLED] = enabled
        }
    }

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
}
