package com.businesstracker.mileage.data.security

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result representation for OAuth disconnect operations distinguishing
 * local credential purge from remote Google revocation status.
 */
data class DisconnectResult(
    val localCleared: Boolean,
    val remoteRevoked: Boolean,
    val error: String? = null
)

/**
 * Manages Google OAuth 2.0 authorization lifecycle for Google Sheets integration.
 *
 * Enforces:
 * - Minimum required scope: `https://www.googleapis.com/auth/spreadsheets`.
 * - Tokens stored strictly in hardware-backed Keystore via [SecureStorageManager].
 * - Bearer tokens are never exposed in UI state or unencrypted preference storage.
 * - Invalidation and refresh capability when encountering HTTP 401.
 * - If token refresh fails, local state is marked disconnected to prevent false UI claims.
 * - Awaits Google sign-out and revoke-access operations during disconnect.
 */
@Singleton
open class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorageManager: SecureStorageManager,
    private val preferencesManager: TrackingPreferencesManager
) {
    companion object {
        const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"
        private const val OAUTH_SCOPE_PREFIX = "oauth2:"
        private const val DISCONNECT_TIMEOUT_SECONDS = 5L
    }

    /**
     * Builds [GoogleSignInOptions] configured with email and the Sheets API scope.
     */
    fun getGoogleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SHEETS_SCOPE))
            .build()
    }

    /**
     * Returns a [GoogleSignInClient] configured for this application.
     */
    fun getSignInClient(customContext: Context = context): GoogleSignInClient {
        return GoogleSignIn.getClient(customContext, getGoogleSignInOptions())
    }

    /**
     * Processes Google Sign-In activity result intent.
     * Extracts account info, queries Google Play Services for the OAuth 2.0 access token,
     * and securely persists credentials in Android Keystore.
     */
    suspend fun handleSignInResult(data: Intent?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                ?: return@withContext Result.failure(IllegalStateException("No account returned from Google Sign-In"))

            val email = account.email.orEmpty()
            val androidAccount = account.account
                ?: Account(email, "com.google")

            // Request OAuth 2.0 access token with sheets scope
            val token = GoogleAuthUtil.getToken(
                context,
                androidAccount,
                "$OAUTH_SCOPE_PREFIX$SHEETS_SCOPE"
            )

            if (token.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Empty OAuth token returned"))
            }

            // Save encrypted credentials
            val savedToken = secureStorageManager.saveOAuthToken(token)
            if (!savedToken) {
                return@withContext Result.failure(SecurityException("Failed to securely store OAuth token in Keystore"))
            }

            if (email.isNotBlank()) {
                secureStorageManager.saveAccountEmail(email)
            }

            preferencesManager.setSheetsConnected(true)
            preferencesManager.setSheetsLastError(null)

            Result.success(email)
        } catch (e: Exception) {
            val errorMsg = if (e is ApiException) {
                when (e.statusCode) {
                    CommonStatusCodes.DEVELOPER_ERROR -> { // 10
                        "Google Sign-In is not configured for this build. Register the app's package name and signing SHA-1 in Google Cloud Console, or use Apps Script Webhook sync instead."
                    }
                    CommonStatusCodes.SIGN_IN_REQUIRED -> { // 4
                        "Google Sign-In required. Please choose an account to connect."
                    }
                    CommonStatusCodes.NETWORK_ERROR -> { // 7
                        "Network error during Google Sign-In. Please check your internet connection."
                    }
                    CommonStatusCodes.CANCELED -> { // 16
                        "Google Sign-In was cancelled."
                    }
                    else -> {
                        "Google Sign-In failed (${e.statusCode}). Please verify your configuration or use Apps Script Webhook sync."
                    }
                }
            } else {
                "Google Sign-In failed. Please try again or use Apps Script Webhook sync."
            }
            preferencesManager.setSheetsLastError(errorMsg)
            Result.failure(e)
        }
    }

    /**
     * Attempts to refresh an expired OAuth access token using Google Play Services.
     * Clears the stale token from GoogleAuthUtil cache, queries for a fresh token,
     * and updates [SecureStorageManager].
     *
     * If refresh fails, purges stale credentials and updates connection state to ensure
     * the UI does not falsely display an active connection.
     */
    open suspend fun refreshOAuthToken(): String? = withContext(Dispatchers.IO) {
        try {
            val currentToken = secureStorageManager.getOAuthToken()
            if (!currentToken.isNullOrBlank()) {
                try {
                    GoogleAuthUtil.clearToken(context, currentToken)
                } catch (_: Exception) {}
            }

            val email = secureStorageManager.getAccountEmail()
            if (email.isNullOrBlank()) {
                handleTokenExpiration("No account email available for refresh")
                return@withContext null
            }

            val account = Account(email, "com.google")
            val newToken = GoogleAuthUtil.getToken(
                context,
                account,
                "$OAUTH_SCOPE_PREFIX$SHEETS_SCOPE"
            )

            if (!newToken.isNullOrBlank()) {
                val saved = secureStorageManager.saveOAuthToken(newToken)
                if (saved) {
                    preferencesManager.setSheetsConnected(true)
                    preferencesManager.setSheetsLastError(null)
                    newToken
                } else {
                    handleTokenExpiration("Failed to securely store refreshed token")
                    null
                }
            } else {
                handleTokenExpiration("Empty refreshed token returned")
                null
            }
        } catch (e: Exception) {
            handleTokenExpiration(e.localizedMessage)
            null
        }
    }

    private suspend fun handleTokenExpiration(detail: String? = null) {
        val msg = "Google authorization expired. Please reconnect your account."
        preferencesManager.setSheetsLastError(if (detail != null) "$msg ($detail)" else msg)
        // Clear expired OAuth token so hasOAuthToken() returns false immediately
        secureStorageManager.clearOAuthToken()
        preferencesManager.setSheetsConnected(false)
    }

    /**
     * Disconnects Google account, awaits sign-out and remote revocation,
     * and purges local Keystore credentials.
     *
     * Awaits Google Play Services Tasks with a strict timeout so that network timeouts
     * or cancellation do not hang the application. Local credentials are guaranteed
     * to be wiped regardless of remote network outcome.
     */
    suspend fun disconnect(customContext: Context = context): DisconnectResult = withContext(Dispatchers.IO) {
        var remoteRevoked = false
        var remoteError: String? = null

        try {
            val client = getSignInClient(customContext)
            // Await sign out
            try {
                Tasks.await(client.signOut(), DISCONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (e: Exception) {
                remoteError = e.localizedMessage
            }

            // Await remote access revocation
            try {
                Tasks.await(client.revokeAccess(), DISCONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                remoteRevoked = true
            } catch (e: Exception) {
                if (remoteError == null) remoteError = e.localizedMessage
            }
        } catch (e: Exception) {
            remoteError = e.localizedMessage
        }

        // Purge OAuth credentials and reset state to disconnected
        secureStorageManager.clearOAuthCredentials()
        preferencesManager.setSheetsConnected(false)
        preferencesManager.setSheetsSpreadsheetId("")
        preferencesManager.setSheetsWebhookUrl("")
        preferencesManager.setSheetsLastError(null)

        DisconnectResult(
            localCleared = true,
            remoteRevoked = remoteRevoked,
            error = remoteError
        )
    }
}
