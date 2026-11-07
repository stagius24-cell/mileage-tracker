package com.businesstracker.mileage.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.security.SecureKeyProvider
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.data.security.TestSoftwareKeyProvider
import kotlinx.coroutines.async
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
import javax.crypto.SecretKey

private val Context.testDataStore by preferencesDataStore(name = "tracking_prefs")

@RunWith(RobolectricTestRunner::class)
class SensitivePreferencesMigrationTest {

    private lateinit var context: Context
    private lateinit var testKeyProvider: TestSoftwareKeyProvider
    private lateinit var secureStorageManager: SecureStorageManager
    private lateinit var preferencesManager: TrackingPreferencesManager

    private val KEY_SHEETS_WEBHOOK_URL = stringPreferencesKey("sheets_webhook_url")
    private val KEY_CF_BEARER_TOKEN = stringPreferencesKey("cf_bearer_token")
    private val KEY_CF_CLIENT_SECRET = stringPreferencesKey("cf_client_secret")

    @Before
    fun setUp() = runTest {
        context = RuntimeEnvironment.getApplication()
        testKeyProvider = TestSoftwareKeyProvider()
        secureStorageManager = SecureStorageManager(context, testKeyProvider)
        secureStorageManager.clearAllCredentials()

        preferencesManager = TrackingPreferencesManager(context, secureStorageManager)

        // Clear any leftover DataStore keys
        context.testDataStore.edit { it.clear() }
    }

    @Test
    fun migrateSensitivePreferences_successfulRoundTrip_removesPlaintextFromDataStore() = runTest {
        val sampleWebhook = "https://script.google.com/macros/s/AKfycbx_webhook_secret/exec"
        val sampleBearer = "cf_bearer_token_secret_12345"
        val sampleSecret = "cf_client_secret_abcdef"

        // 1. Seed legacy plaintext values directly into DataStore
        context.testDataStore.edit { prefs ->
            prefs[KEY_SHEETS_WEBHOOK_URL] = sampleWebhook
            prefs[KEY_CF_BEARER_TOKEN] = sampleBearer
            prefs[KEY_CF_CLIENT_SECRET] = sampleSecret
        }

        // Verify DataStore initially has the plaintext secrets
        val initialData = context.testDataStore.data.first()
        assertEquals(sampleWebhook, initialData[KEY_SHEETS_WEBHOOK_URL])
        assertEquals(sampleBearer, initialData[KEY_CF_BEARER_TOKEN])
        assertEquals(sampleSecret, initialData[KEY_CF_CLIENT_SECRET])

        // 2. Execute migration
        val result = preferencesManager.migrateSensitivePreferences()

        assertTrue("Migration must report complete success", result.isCompleteSuccess)
        assertTrue(result.sheetsWebhookMigrated)
        assertTrue(result.cfBearerTokenMigrated)
        assertTrue(result.cfClientSecretMigrated)
        assertTrue(result.errors.isEmpty())

        // 3. Verify values are now in Keystore-backed SecureStorageManager
        assertEquals(sampleWebhook, secureStorageManager.getSheetsWebhookUrl())
        assertEquals(sampleBearer, secureStorageManager.getCfBearerToken())
        assertEquals(sampleSecret, secureStorageManager.getCfClientSecret())

        // 4. Verify plaintext keys have been permanently removed from DataStore
        val postMigrationData = context.testDataStore.data.first()
        assertFalse("Plaintext sheets_webhook_url must be removed", postMigrationData.contains(KEY_SHEETS_WEBHOOK_URL))
        assertFalse("Plaintext cf_bearer_token must be removed", postMigrationData.contains(KEY_CF_BEARER_TOKEN))
        assertFalse("Plaintext cf_client_secret must be removed", postMigrationData.contains(KEY_CF_CLIENT_SECRET))

        // 5. Verify transparent Flow getters on TrackingPreferencesManager return decrypted secrets
        assertEquals(sampleWebhook, preferencesManager.sheetsWebhookUrl.first())
        assertEquals(sampleBearer, preferencesManager.cfBearerToken.first())
        assertEquals(sampleSecret, preferencesManager.cfClientSecret.first())
    }

    @Test
    fun migrateSensitivePreferences_idempotent_repeatedCallsPreserveData() = runTest {
        val sampleWebhook = "https://script.google.com/macros/s/AKfycbx_idempotent/exec"
        val sampleBearer = "cf_bearer_idempotent"
        val sampleSecret = "cf_secret_idempotent"

        context.testDataStore.edit { prefs ->
            prefs[KEY_SHEETS_WEBHOOK_URL] = sampleWebhook
            prefs[KEY_CF_BEARER_TOKEN] = sampleBearer
            prefs[KEY_CF_CLIENT_SECRET] = sampleSecret
        }

        // First migration
        val firstResult = preferencesManager.migrateSensitivePreferences()
        assertTrue(firstResult.isCompleteSuccess)

        // Second migration on already migrated store
        val secondResult = preferencesManager.migrateSensitivePreferences()
        assertTrue("Second migration must succeed idempotently", secondResult.isCompleteSuccess)

        // Credentials must remain fully intact and decryptable
        assertEquals(sampleWebhook, secureStorageManager.getSheetsWebhookUrl())
        assertEquals(sampleBearer, secureStorageManager.getCfBearerToken())
        assertEquals(sampleSecret, secureStorageManager.getCfClientSecret())
    }

    @Test
    fun migrateSensitivePreferences_whenKeystoreFails_preservesPlaintextInStore_andReportsError() = runTest {
        val sampleWebhook = "https://script.google.com/macros/s/AKfycbx_unmigrated/exec"
        val sampleBearer = "cf_bearer_unmigrated"
        val sampleSecret = "cf_secret_unmigrated"

        context.testDataStore.edit { prefs ->
            prefs[KEY_SHEETS_WEBHOOK_URL] = sampleWebhook
            prefs[KEY_CF_BEARER_TOKEN] = sampleBearer
            prefs[KEY_CF_CLIENT_SECRET] = sampleSecret
        }

        // Simulate failing Keystore provider
        val failingKeyProvider = object : SecureKeyProvider {
            override fun getOrCreateKey(): SecretKey? = null // Fails closed
            override fun deleteKey() {}
        }
        val failingSecureStorage = SecureStorageManager(context, failingKeyProvider)
        preferencesManager.setSecureStorageManagerForTesting(failingSecureStorage)

        // Run migration with failing Keystore
        val result = preferencesManager.migrateSensitivePreferences()

        assertFalse("Migration must report failure when Keystore is unavailable", result.isCompleteSuccess)
        assertFalse(result.sheetsWebhookMigrated)
        assertFalse(result.cfBearerTokenMigrated)
        assertFalse(result.cfClientSecretMigrated)
        assertEquals(3, result.errors.size)

        // CRITICAL DATA PRESERVATION RULE:
        // Plaintext must NOT be deleted from DataStore if encryption/verification failed!
        val preservedData = context.testDataStore.data.first()
        assertEquals(sampleWebhook, preservedData[KEY_SHEETS_WEBHOOK_URL])
        assertEquals(sampleBearer, preservedData[KEY_CF_BEARER_TOKEN])
        assertEquals(sampleSecret, preservedData[KEY_CF_CLIENT_SECRET])
    }

    @Test
    fun migrateSensitivePreferences_emptyOrAbsentValues_handledGracefully() = runTest {
        // DataStore has zero secrets
        val result = preferencesManager.migrateSensitivePreferences()

        assertTrue("Empty store must report success without errors", result.isCompleteSuccess)
        assertTrue(result.errors.isEmpty())
        assertNull(secureStorageManager.getSheetsWebhookUrl())
        assertNull(secureStorageManager.getCfBearerToken())
        assertNull(secureStorageManager.getCfClientSecret())
    }

    @Test
    fun migrateSensitivePreferences_concurrentCalls_doNotCorruptState() = runTest {
        val sampleWebhook = "https://script.google.com/macros/s/AKfycbx_concurrent/exec"
        context.testDataStore.edit { prefs ->
            prefs[KEY_SHEETS_WEBHOOK_URL] = sampleWebhook
        }

        // Launch concurrent migrations
        val job1 = async { preferencesManager.migrateSensitivePreferences() }
        val job2 = async { preferencesManager.migrateSensitivePreferences() }

        val res1 = job1.await()
        val res2 = job2.await()

        assertTrue(res1.isCompleteSuccess)
        assertTrue(res2.isCompleteSuccess)
        assertEquals(sampleWebhook, secureStorageManager.getSheetsWebhookUrl())

        val postData = context.testDataStore.data.first()
        assertFalse(postData.contains(KEY_SHEETS_WEBHOOK_URL))
    }
}
