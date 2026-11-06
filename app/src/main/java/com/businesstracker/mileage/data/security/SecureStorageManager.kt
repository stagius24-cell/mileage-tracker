package com.businesstracker.mileage.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface defining the cryptographic key provisioning contract.
 * Separates production hardware-backed AndroidKeyStore from test-only key providers.
 */
interface SecureKeyProvider {
    fun getOrCreateKey(): SecretKey?
    fun deleteKey()
}

/**
 * Production key provider utilizing the hardware-backed Android KeyStore (AES-256 GCM).
 *
 * Enforces strict fail-closed security:
 * - If AndroidKeyStore is unavailable, or key generation/retrieval fails, it returns null.
 * - Under NO circumstances does it fall back to software keys or plaintext storage.
 */
class AndroidKeyStoreProvider(
    private val keyAlias: String = KEY_ALIAS
) : SecureKeyProvider {
    companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.businesstracker.mileage.sheets_oauth_key"
    }

    override fun getOrCreateKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.getKey(keyAlias, null) as? SecretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEY_STORE
                )
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()

                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            // Strict fail-closed: NO fallback to software keys in production
            null
        }
    }

    override fun deleteKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
        } catch (_: Exception) {}
    }
}

/**
 * Test-only key provider utilizing real AES-256 SecretKeys in a software KeyStore.
 * Strictly intended for unit tests and JVM test runners where Android hardware HSM is unavailable.
 * Cannot be instantiated by production Hilt dependency injection.
 */
@VisibleForTesting
class TestSoftwareKeyProvider(
    private var customKey: SecretKey? = null
) : SecureKeyProvider {
    companion object {
        private const val TEST_ALIAS = "com.businesstracker.mileage.test_key"
        private const val TEST_PASSWORD = "mileage_test_ks_pass"
    }

    private var inMemoryKey: SecretKey? = customKey

    override fun getOrCreateKey(): SecretKey? {
        inMemoryKey?.let { return it }
        return try {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            val generated = keyGenerator.generateKey()
            inMemoryKey = generated
            generated
        } catch (_: Exception) {
            null
        }
    }

    override fun deleteKey() {
        inMemoryKey = null
    }

    fun setKey(key: SecretKey?) {
        inMemoryKey = key
    }
}

/**
 * Hardware-backed secure credential storage utilizing Android KeyStore (AES-256 GCM).
 *
 * Implements strict fail-closed security:
 * - Credentials (OAuth access tokens, refresh tokens, and account emails) are encrypted
 *   using authenticated AES-256-GCM with a unique 96-bit (12-byte) initialization vector (IV)
 *   and 128-bit authentication tag per write.
 * - Under no circumstances is Base64 obfuscation or plaintext storage used as a fallback.
 *   If the cryptographic provider, KeyStore, or Cipher fails, the manager fails closed
 *   (returns false or null, persisting zero unencrypted data).
 * - Production builds strictly use [AndroidKeyStoreProvider]. If AndroidKeyStore is unavailable,
 *   it fails closed without falling back to software keys.
 * - Calling [clearAllCredentials] deletes the cryptographic key entry from the KeyStore
 *   and clears all stored ciphertext preferences.
 */
@Singleton
class SecureStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "mileage_secure_credentials"
        private const val KEY_ENCRYPTED_OAUTH_TOKEN = "enc_oauth_token"
        private const val KEY_ENCRYPTED_REFRESH_TOKEN = "enc_refresh_token"
        private const val KEY_ENCRYPTED_ACCOUNT_EMAIL = "enc_account_email"
        private const val KEY_OAUTH_TOKEN_IV = "oauth_token_iv"
        private const val KEY_REFRESH_TOKEN_IV = "refresh_token_iv"
        private const val KEY_ACCOUNT_EMAIL_IV = "account_email_iv"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    // In production, keyProvider is strictly AndroidKeyStoreProvider
    private var keyProvider: SecureKeyProvider = AndroidKeyStoreProvider()

    /**
     * Test-only constructor allowing explicit injection of a test key provider.
     */
    @VisibleForTesting
    constructor(context: Context, testKeyProvider: SecureKeyProvider) : this(context) {
        this.keyProvider = testKeyProvider
    }

    /**
     * Test-only setter allowing test suites to inject custom key providers.
     */
    @VisibleForTesting
    internal fun setTestKeyProvider(provider: SecureKeyProvider?) {
        this.keyProvider = provider ?: AndroidKeyStoreProvider()
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return keyProvider.getOrCreateKey()
    }

    /**
     * Encrypts plaintext using AES-256 GCM and stores ciphertext and IV.
     * Returns true on success, false on cryptographic failure (fail-closed).
     */
    private fun encryptAndStore(plaintext: String, dataKey: String, ivKey: String): Boolean {
        if (plaintext.isBlank()) {
            prefs.edit().remove(dataKey).remove(ivKey).apply()
            return true
        }

        val secretKey = getOrCreateSecretKey() ?: return false // Fail-closed

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv ?: return false
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            prefs.edit()
                .putString(dataKey, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                .putString(ivKey, Base64.encodeToString(iv, Base64.NO_WRAP))
                .commit()
            true
        } catch (_: Exception) {
            // Cryptographic failure: purge any partial or corrupt entries and fail closed
            prefs.edit().remove(dataKey).remove(ivKey).commit()
            false
        }
    }

    /**
     * Decrypts ciphertext using AES-256 GCM.
     * Returns the decrypted String, or null if missing, corrupted, or decryption fails.
     * Never falls back to unencrypted decoding.
     */
    private fun retrieveAndDecrypt(dataKey: String, ivKey: String): String? {
        val encryptedStr = prefs.getString(dataKey, null) ?: return null
        val ivStr = prefs.getString(ivKey, null) ?: return null
        val secretKey = getOrCreateSecretKey() ?: return null

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(ivStr, Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(encryptedStr, Base64.NO_WRAP)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            // AEADBadTagException, KeyPermanentlyInvalidatedException, or corrupt data
            null
        }
    }

    // --- OAuth Access Token ---

    fun saveOAuthToken(token: String): Boolean {
        return encryptAndStore(token, KEY_ENCRYPTED_OAUTH_TOKEN, KEY_OAUTH_TOKEN_IV)
    }

    fun getOAuthToken(): String? {
        return retrieveAndDecrypt(KEY_ENCRYPTED_OAUTH_TOKEN, KEY_OAUTH_TOKEN_IV)
    }

    fun hasOAuthToken(): Boolean {
        return !getOAuthToken().isNullOrBlank()
    }

    fun clearOAuthToken() {
        prefs.edit()
            .remove(KEY_ENCRYPTED_OAUTH_TOKEN)
            .remove(KEY_OAUTH_TOKEN_IV)
            .apply()
    }

    // --- OAuth Refresh Token ---

    fun saveRefreshToken(token: String): Boolean {
        return encryptAndStore(token, KEY_ENCRYPTED_REFRESH_TOKEN, KEY_REFRESH_TOKEN_IV)
    }

    fun getRefreshToken(): String? {
        return retrieveAndDecrypt(KEY_ENCRYPTED_REFRESH_TOKEN, KEY_REFRESH_TOKEN_IV)
    }

    fun clearRefreshToken() {
        prefs.edit()
            .remove(KEY_ENCRYPTED_REFRESH_TOKEN)
            .remove(KEY_REFRESH_TOKEN_IV)
            .apply()
    }

    // --- Connected Account Email ---

    fun saveAccountEmail(email: String): Boolean {
        return encryptAndStore(email, KEY_ENCRYPTED_ACCOUNT_EMAIL, KEY_ACCOUNT_EMAIL_IV)
    }

    fun getAccountEmail(): String? {
        return retrieveAndDecrypt(KEY_ENCRYPTED_ACCOUNT_EMAIL, KEY_ACCOUNT_EMAIL_IV)
    }

    fun clearAccountEmail() {
        prefs.edit()
            .remove(KEY_ENCRYPTED_ACCOUNT_EMAIL)
            .remove(KEY_ACCOUNT_EMAIL_IV)
            .apply()
    }

    // --- Complete Credential Purge ---

    /**
     * Completely purges all stored credentials, deletes the cryptographic key alias
     * from KeyStore via [keyProvider], and wipes all associated shared preferences.
     */
    fun clearAllCredentials() {
        try {
            keyProvider.deleteKey()
        } catch (_: Exception) {}

        prefs.edit().clear().commit()
    }
}
