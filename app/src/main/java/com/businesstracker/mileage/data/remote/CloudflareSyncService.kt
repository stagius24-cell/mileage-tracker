package com.businesstracker.mileage.data.remote

import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.remote.model.MileageSyncPayload
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudflareSyncService @Inject constructor(
    private val preferencesManager: TrackingPreferencesManager,
    private val gson: Gson
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun syncMileageEntry(payload: MileageSyncPayload): SyncResult {
        val endpoint = preferencesManager.cfEndpoint.first()
        val clientId = preferencesManager.cfClientId.first()
        val clientSecret = preferencesManager.cfClientSecret.first()
        val bearerToken = preferencesManager.cfBearerToken.first()

        val jsonBody = gson.toJson(payload)
        val requestBody = jsonBody.toRequestBody(jsonMediaType)

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "BusinessMileageTracker-Android/1.0")

        // Cloudflare Access Service Token Headers
        if (clientId.isNotBlank()) {
            requestBuilder.addHeader("CF-Access-Client-Id", clientId.trim())
        }
        if (clientSecret.isNotBlank()) {
            requestBuilder.addHeader("CF-Access-Client-Secret", clientSecret.trim())
        }

        // Bearer Token fallback or secondary auth
        if (bearerToken.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${bearerToken.trim()}")
        }

        val request = requestBuilder.build()

        return try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val responseBody = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    SyncResult.Success(code, responseBody)
                } else {
                    SyncResult.HttpError(code, responseBody)
                }
            }
        } catch (e: IOException) {
            SyncResult.NetworkError(e)
        } catch (e: Exception) {
            SyncResult.UnexpectedError(e)
        }
    }
}

sealed class SyncResult {
    data class Success(val statusCode: Int, val responseBody: String) : SyncResult()
    data class HttpError(val statusCode: Int, val errorBody: String) : SyncResult()
    data class NetworkError(val exception: IOException) : SyncResult()
    data class UnexpectedError(val exception: Exception) : SyncResult()
}
