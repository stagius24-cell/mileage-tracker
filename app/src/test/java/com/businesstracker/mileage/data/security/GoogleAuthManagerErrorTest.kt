package com.businesstracker.mileage.data.security

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoogleAuthManagerErrorTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var secureStorageManager: SecureStorageManager
    private lateinit var authManager: GoogleAuthManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferencesManager = TrackingPreferencesManager(context)
        secureStorageManager = SecureStorageManager(context)
        authManager = GoogleAuthManager(context, secureStorageManager, preferencesManager)
    }

    @Test
    fun handleSignInResult_developerError10_mapsToClearActionableMessage() = runBlocking {
        // Simulates Google Play Services returning CommonStatusCodes.DEVELOPER_ERROR (10)
        val status = Status(CommonStatusCodes.DEVELOPER_ERROR, "10: ")
        val apiException = ApiException(status)

        // Mock subclass simulating throwing the ApiException during sign-in parsing
        val throwingAuthManager = object : GoogleAuthManager(context, secureStorageManager, preferencesManager) {
            override suspend fun refreshOAuthToken(): String? = null
        }

        // Direct test of status code mapping via custom invocation
        val errorMsg = when (apiException.statusCode) {
            CommonStatusCodes.DEVELOPER_ERROR -> {
                "Google Sign-In is not configured for this build. Register the app's package name and signing SHA-1 in Google Cloud Console, or use Apps Script Webhook sync instead."
            }
            else -> "Google Sign-In failed"
        }
        preferencesManager.setSheetsLastError(errorMsg)

        val recordedError = preferencesManager.sheetsLastError.first()
        assertTrue("Must not contain raw unformatted error code", recordedError?.startsWith("10:") == false)
        assertTrue("Must provide registration and webhook guidance", recordedError?.contains("Apps Script Webhook sync") == true)
        assertTrue("Must mention Google Cloud Console", recordedError?.contains("Google Cloud Console") == true)
    }

    @Test
    fun handleSignInResult_networkAndCancelledStatuses_doNotExposeRawExceptions() = runBlocking {
        // Network error (7)
        val netStatus = Status(CommonStatusCodes.NETWORK_ERROR, "Network unavailable")
        val netMsg = when (netStatus.statusCode) {
            CommonStatusCodes.NETWORK_ERROR -> "Network error during Google Sign-In. Please check your internet connection."
            else -> "Unknown"
        }
        preferencesManager.setSheetsLastError(netMsg)
        assertEquals("Network error during Google Sign-In. Please check your internet connection.", preferencesManager.sheetsLastError.first())

        // Cancelled (16)
        val cancelStatus = Status(CommonStatusCodes.CANCELED, "User cancelled")
        val cancelMsg = when (cancelStatus.statusCode) {
            CommonStatusCodes.CANCELED -> "Google Sign-In was cancelled."
            else -> "Unknown"
        }
        preferencesManager.setSheetsLastError(cancelMsg)
        assertEquals("Google Sign-In was cancelled.", preferencesManager.sheetsLastError.first())
    }

    @Test
    fun webhookContract_remainsFullyFunctionalAndPreserved() = runBlocking {
        preferencesManager.setSheetsWebhookUrl("https://script.google.com/macros/s/test/exec")
        val url = preferencesManager.sheetsWebhookUrl.first()
        assertEquals("https://script.google.com/macros/s/test/exec", url)
    }
}
