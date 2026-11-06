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
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google OAuth 2.0 authorization lifecycle for Google Sheets integration.
 *
 * Enforces:
 * - Minimum required scope: `https://www.googleapis.com/auth/spreadsheets`.
 * - Tokens stored strictly in hardware-backed Keystore via [SecureStorageManager].
 * - Bearer tokens are never exposed in UI state or unencrypted preference storage.
 * - Invalidation and refresh capability when encountering HTTP 401.
 * - Clean disconnect/revoke flow removing cached credentials and remote authorization.
 */
@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorageManager: SecureStorageManager,
    private val preferencesManager: TrackingPreferencesManager
) {
    companion object {
        const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"
        private const val OAUTH_SCOPE_PREFIX = "oauth2:"
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
            val errorMsg = e.localizedMessage ?: "Google Sign-In failed"
            preferencesManager.setSheetsLastError(errorMsg)
            Result.failure(e)
        }
    }

    /**
     * Attempts to refresh an expired OAuth access token using Google Play Services.
     * Clears the stale token from GoogleAuthUtil cache, queries for a fresh token,
     * and updates [SecureStorageManager].
     */
    suspend fun refreshOAuthToken(): String? = withContext(Dispatchers.IO) {
        try {
            val currentToken = secureStorageManager.getOAuthToken()
            if (!currentToken.isNullOrBlank()) {
                try {
                    GoogleAuthUtil.clearToken(context, currentToken)
                } catch (_: Exception) {}
            }

            val email = secureStorageManager.getAccountEmail()
            if (email.isNullOrBlank()) return@withContext null

            val account = Account(email, "com.google")
            val newToken = GoogleAuthUtil.getToken(
                context,
                account,
                "$OAUTH_SCOPE_PREFIX$SHEETS_SCOPE"
            )

            if (!newToken.isNullOrBlank()) {
                val saved = secureStorageManager.saveOAuthToken(newToken)
                if (saved) newToken else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Disconnects Google account, revokes OAuth access, and wipes Keystore credentials.
     */
    suspend fun disconnect(customContext: Context = context) = withContext(Dispatchers.IO) {
        try {
            val client = getSignInClient(customContext)
            client.signOut()
            client.revokeAccess()
        } catch (_: Exception) {}

        secureStorageManager.clearAllCredentials()
        preferencesManager.setSheetsConnected(false)
        preferencesManager.setSheetsSpreadsheetId("")
        preferencesManager.setSheetsWebhookUrl("")
        preferencesManager.setSheetsLastError(null)
    }
}
