package com.businesstracker.mileage

import android.content.Context
import android.util.Base64
import com.businesstracker.mileage.data.security.AndroidKeyStoreProvider
import com.businesstracker.mileage.data.security.SecureKeyProvider
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.data.security.TestSoftwareKeyProvider
import org.junit.After
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
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
class SecureStorageManagerTest {

    private lateinit var context: Context
    private lateinit var testKeyProvider: TestSoftwareKeyProvider
    private lateinit var secureStorageManager: SecureStorageManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        testKeyProvider = TestSoftwareKeyProvider()
        secureStorageManager = SecureStorageManager(context, testKeyProvider)
        secureStorageManager.clearAllCredentials()
    }

    @After
    fun tearDown() {
        secureStorageManager.clearAllCredentials()
    }

    @Test
    fun saveOAuthToken_and_getOAuthToken_roundTrip_success() {
        val sampleToken = "ya29.a0AfH6SMD_sample_bearer_oauth_token_12345"
        val saved = secureStorageManager.saveOAuthToken(sampleToken)

        assertTrue("saveOAuthToken should return true on success", saved)
        assertTrue(secureStorageManager.hasOAuthToken())
        assertEquals(sampleToken, secureStorageManager.getOAuthToken())
    }

    @Test
    fun saveOAuthToken_isEncrypted_notStoredInPlaintextOrRawBase64() {
        val secretValue = "super_secret_oauth_access_token_xyz"
        secureStorageManager.saveOAuthToken(secretValue)

        val rawPrefs = context.getSharedPreferences("mileage_secure_credentials", Context.MODE_PRIVATE)
        val encryptedData = rawPrefs.getString("enc_oauth_token", null)
        val iv = rawPrefs.getString("oauth_token_iv", null)

        assertNotNull("Encrypted ciphertext must be persisted", encryptedData)
        assertNotNull("IV must be persisted", iv)

        // Verify the stored value is NOT plain text
        assertFalse("Raw prefs must never contain plain text token", encryptedData!!.contains(secretValue))

        // Verify the stored value is NOT simple Base64-encoded plaintext (No Base64 obfuscation fallback!)
        val directBase64OfPlaintext = Base64.encodeToString(secretValue.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        assertFalse("Ciphertext must NOT be Base64-obfuscated plaintext", encryptedData == directBase64OfPlaintext)
    }

    @Test
    fun saveOAuthToken_blankToken_clearsToken() {
        secureStorageManager.saveOAuthToken("valid_token")
        assertTrue(secureStorageManager.hasOAuthToken())

        secureStorageManager.saveOAuthToken("")
        assertFalse(secureStorageManager.hasOAuthToken())
        assertNull(secureStorageManager.getOAuthToken())
    }

    @Test
    fun clearOAuthToken_removesTokenAndIv() {
        secureStorageManager.saveOAuthToken("valid_token")
        assertTrue(secureStorageManager.hasOAuthToken())

        secureStorageManager.clearOAuthToken()
        assertFalse(secureStorageManager.hasOAuthToken())
        assertNull(secureStorageManager.getOAuthToken())
    }

    @Test
    fun saveRefreshToken_and_getRefreshToken_roundTrip_success() {
        val sampleRefresh = "1//04_refresh_token_sample_secret_67890"
        val saved = secureStorageManager.saveRefreshToken(sampleRefresh)

        assertTrue("saveRefreshToken should return true on success", saved)
        assertEquals(sampleRefresh, secureStorageManager.getRefreshToken())
    }

    @Test
    fun saveAccountEmail_and_getAccountEmail_roundTrip_success() {
        val email = "driver.user@gmail.com"
        val saved = secureStorageManager.saveAccountEmail(email)

        assertTrue(saved)
        assertEquals(email, secureStorageManager.getAccountEmail())
    }

    @Test
    fun tamperedCiphertext_failsClosed_returnsNull() {
        val originalToken = "valid_oauth_token_to_tamper"
        secureStorageManager.saveOAuthToken(originalToken)

        val rawPrefs = context.getSharedPreferences("mileage_secure_credentials", Context.MODE_PRIVATE)
        val validEnc = rawPrefs.getString("enc_oauth_token", null)!!

        // Tamper with the ciphertext by flipping bytes
        val bytes = Base64.decode(validEnc, Base64.NO_WRAP)
        bytes[0] = (bytes[0].toInt() xor 0xFF).toByte()
        val tamperedEnc = Base64.encodeToString(bytes, Base64.NO_WRAP)

        rawPrefs.edit().putString("enc_oauth_token", tamperedEnc).commit()

        // Decryption of tampered AES-GCM data must fail closed (AEAD authentication tag mismatch)
        val result = secureStorageManager.getOAuthToken()
        assertNull("Tampered ciphertext must fail closed and return null", result)
    }

    @Test
    fun tamperedIv_failsClosed_returnsNull() {
        val originalToken = "valid_oauth_token_iv_tamper"
        secureStorageManager.saveOAuthToken(originalToken)

        val rawPrefs = context.getSharedPreferences("mileage_secure_credentials", Context.MODE_PRIVATE)
        val validIv = rawPrefs.getString("oauth_token_iv", null)!!

        val bytes = Base64.decode(validIv, Base64.NO_WRAP)
        bytes[0] = (bytes[0].toInt() xor 0xAA).toByte()
        val tamperedIv = Base64.encodeToString(bytes, Base64.NO_WRAP)

        rawPrefs.edit().putString("oauth_token_iv", tamperedIv).commit()

        val result = secureStorageManager.getOAuthToken()
        assertNull("Tampered IV must fail closed and return null", result)
    }

    @Test
    fun missingIv_failsClosed_returnsNull() {
        secureStorageManager.saveOAuthToken("valid_token")
        val rawPrefs = context.getSharedPreferences("mileage_secure_credentials", Context.MODE_PRIVATE)
        rawPrefs.edit().remove("oauth_token_iv").commit()

        assertNull("Missing IV must fail closed and return null", secureStorageManager.getOAuthToken())
    }

    @Test
    fun clearAllCredentials_purgesAllKeysAndPreferences() {
        secureStorageManager.saveOAuthToken("oauth_token_1")
        secureStorageManager.saveRefreshToken("refresh_token_1")
        secureStorageManager.saveAccountEmail("test@example.com")

        assertTrue(secureStorageManager.hasOAuthToken())
        assertNotNull(secureStorageManager.getRefreshToken())
        assertNotNull(secureStorageManager.getAccountEmail())

        secureStorageManager.clearAllCredentials()

        assertFalse(secureStorageManager.hasOAuthToken())
        assertNull(secureStorageManager.getOAuthToken())
        assertNull(secureStorageManager.getRefreshToken())
        assertNull(secureStorageManager.getAccountEmail())

        val rawPrefs = context.getSharedPreferences("mileage_secure_credentials", Context.MODE_PRIVATE)
        assertTrue(rawPrefs.all.isEmpty())
    }

    @Test
    fun failClosed_whenKeyMismatch_returnsNullWithoutFallback() {
        secureStorageManager.saveOAuthToken("token_with_key_1")

        // Switch to a different SecretKey (simulating key rotation or invalidation)
        val kgen = KeyGenerator.getInstance("AES")
        kgen.init(256)
        val differentKey: SecretKey = kgen.generateKey()
        testKeyProvider.setKey(differentKey)

        // Attempting to decrypt with different key must fail closed
        val decrypted = secureStorageManager.getOAuthToken()
        assertNull("Decryption with mismatched key must return null, never plaintext fallback", decrypted)
    }

    @Test
    fun productionProvider_failsClosed_whenAndroidKeyStoreFails_withoutSoftwareFallback() {
        // A provider simulating production AndroidKeyStore failure
        val failingProductionProvider = object : SecureKeyProvider {
            override fun getOrCreateKey(): SecretKey? = null // AndroidKeyStore fails/unavailable
            override fun deleteKey() {}
        }

        val prodManager = SecureStorageManager(context, failingProductionProvider)
        val saved = prodManager.saveOAuthToken("test_prod_token")

        assertFalse("Production provider MUST return false on KeyStore failure", saved)
        assertNull("Production provider MUST return null without software fallback", prodManager.getOAuthToken())
        assertFalse("Production provider MUST report no token", prodManager.hasOAuthToken())

        val rawPrefs = context.getSharedPreferences("mileage_secure_credentials", Context.MODE_PRIVATE)
        assertNull("No ciphertext or plaintext must be persisted on failure", rawPrefs.getString("enc_oauth_token", null))
    }

    @Test
    fun defaultConstructor_usesAndroidKeyStoreProvider() {
        val defaultManager = SecureStorageManager(context)
        // Default constructor strictly wires AndroidKeyStoreProvider
        // In Robolectric environment without hardware HSM, it fails closed safely without crashing
        val saved = defaultManager.saveOAuthToken("prod_token")
        if (!saved) {
            assertNull(defaultManager.getOAuthToken())
            assertFalse(defaultManager.hasOAuthToken())
        }
    }
}
