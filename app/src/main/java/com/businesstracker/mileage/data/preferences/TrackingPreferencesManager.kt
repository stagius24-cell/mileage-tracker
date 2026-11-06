package com.businesstracker.mileage.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tracking_prefs")

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

        // Cloudflare D1 & Sync Configuration
        private val KEY_CF_ENDPOINT = stringPreferencesKey("cf_endpoint")
        private val KEY_CF_CLIENT_ID = stringPreferencesKey("cf_client_id")
        private val KEY_CF_CLIENT_SECRET = stringPreferencesKey("cf_client_secret")
        private val KEY_CF_BEARER_TOKEN = stringPreferencesKey("cf_bearer_token")
        private val KEY_DEFAULT_VEHICLE = stringPreferencesKey("default_vehicle")
        private val KEY_CUSTOM_CATEGORIES = stringPreferencesKey("custom_categories")

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

    suspend fun setVehicleAnchor(latitude: Double, longitude: Double, timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VEHICLE_ANCHOR_LAT] = latitude
            prefs[KEY_VEHICLE_ANCHOR_LNG] = longitude
            prefs[KEY_VEHICLE_ANCHOR_TIME] = timestamp
            prefs[KEY_IN_VEHICLE_STATE] = true
        }
    }

    suspend fun clearVehicleAnchor() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_VEHICLE_ANCHOR_LAT)
            prefs.remove(KEY_VEHICLE_ANCHOR_LNG)
            prefs.remove(KEY_VEHICLE_ANCHOR_TIME)
            prefs[KEY_IN_VEHICLE_STATE] = false
        }
    }

    suspend fun isInVehicle(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_IN_VEHICLE_STATE] ?: false
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
