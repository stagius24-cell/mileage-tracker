package com.businesstracker.mileage

import android.content.Context
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.security.GoogleAuthManager
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.data.security.TestSoftwareKeyProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GoogleAuthManagerTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var secureStorageManager: SecureStorageManager
    private lateinit var googleAuthManager: GoogleAuthManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        preferencesManager = TrackingPreferencesManager(context)
        val keyProvider = TestSoftwareKeyProvider()
        secureStorageManager = SecureStorageManager(context, keyProvider)
        secureStorageManager.clearAllCredentials()
        googleAuthManager = GoogleAuthManager(context, secureStorageManager, preferencesManager)
    }

    @Test
    fun disconnect_clearsLocalCredentials_andResetsConnectionState() = runTest {
        // Setup initial connected state
        secureStorageManager.saveOAuthToken("test_token_123")
        secureStorageManager.saveAccountEmail("driver@gmail.com")
        preferencesManager.setSheetsConnected(true)
        preferencesManager.setSheetsSpreadsheetId("test_spreadsheet_id")
        preferencesManager.setSheetsWebhookUrl("https://example.com")

        assertTrue(secureStorageManager.hasOAuthToken())
        assertTrue(preferencesManager.isSheetsConnected.first())

        val result = googleAuthManager.disconnect(context)

        assertTrue("Local credentials must be cleared", result.localCleared)
        assertFalse("OAuth token must be wiped", secureStorageManager.hasOAuthToken())
        assertNull("OAuth token must be null", secureStorageManager.getOAuthToken())
        assertNull("Account email must be null", secureStorageManager.getAccountEmail())
        assertFalse("Connection state must be reset", preferencesManager.isSheetsConnected.first())
        assertTrue("Spreadsheet ID must be cleared", preferencesManager.sheetsSpreadsheetId.first().isBlank())
        assertTrue("Webhook URL must be cleared", preferencesManager.sheetsWebhookUrl.first().isBlank())
    }

    @Test
    fun refreshOAuthToken_whenEmailMissing_failsClosed_andMarksDisconnected() = runTest {
        secureStorageManager.saveOAuthToken("expired_stale_token")
        // Deliberately omit account email
        secureStorageManager.clearAccountEmail()
        preferencesManager.setSheetsConnected(true)

        val refreshed = googleAuthManager.refreshOAuthToken()

        assertNull("Refresh must return null when account is missing", refreshed)
        assertFalse("Expired token must be purged so UI does not show false connection", secureStorageManager.hasOAuthToken())
        assertFalse("Connection state must be set to false", preferencesManager.isSheetsConnected.first())
        val error = preferencesManager.sheetsLastError.first()
        assertNotNull("Error message must prompt reconnect", error)
        assertTrue("Error must mention expiration/reconnect", error!!.contains("reconnect", ignoreCase = true))
    }

    @Test
    fun getGoogleSignInOptions_hasRequiredSheetsScope() {
        val options = googleAuthManager.getGoogleSignInOptions()
        val scopes = options.scopes
        assertTrue(
            "GoogleSignInOptions must include sheets scope",
            scopes.any { it.scopeUri == GoogleAuthManager.SHEETS_SCOPE }
        )
    }
}
