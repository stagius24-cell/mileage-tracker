package com.businesstracker.mileage.worker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.remote.SavedLocationsSheetsService
import com.businesstracker.mileage.data.remote.SavedLocationsSyncResult
import com.businesstracker.mileage.data.repository.SavedLocationRepositoryImpl
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.data.security.TestSoftwareKeyProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedLocationBackupWorkerTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createWorker(result: SavedLocationsSyncResult): SavedLocationBackupWorker {
        val prefs = TrackingPreferencesManager(context)
        val secureStorage = SecureStorageManager(context, TestSoftwareKeyProvider())
        val repo = SavedLocationRepositoryImpl(database.savedLocationDao(), Dispatchers.IO)
        val fakeService = object : SavedLocationsSheetsService(
            preferencesManager = prefs,
            savedLocationRepository = repo,
            syncQueueDao = database.savedLocationSyncQueueDao(),
            secureStorage = secureStorage,
            googleAuthManager = null,
            gson = Gson()
        ) {
            override suspend fun backupSavedLocations(fullSnapshot: Boolean): SavedLocationsSyncResult = result
        }

        return TestListenableWorkerBuilder<SavedLocationBackupWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return SavedLocationBackupWorker(appContext, workerParameters, fakeService)
                }
            })
            .build()
    }

    @Test
    fun doWork_mapsResultsCorrectlyForDurableRetries() = runBlocking {
        assertEquals(
            ListenableWorker.Result.success(),
            createWorker(SavedLocationsSyncResult.Success(1, 0, 1234L)).doWork()
        )
        assertEquals(
            ListenableWorker.Result.success(),
            createWorker(SavedLocationsSyncResult.NotConfigured).doWork()
        )
        assertEquals(
            ListenableWorker.Result.retry(),
            createWorker(SavedLocationsSyncResult.NetworkError("Offline")).doWork()
        )
        assertEquals(
            ListenableWorker.Result.retry(),
            createWorker(SavedLocationsSyncResult.PartialSuccess(1, 1, "Timeout")).doWork()
        )
        assertEquals(
            ListenableWorker.Result.retry(),
            createWorker(SavedLocationsSyncResult.RateLimited("429")).doWork()
        )
        assertEquals(
            ListenableWorker.Result.retry(),
            createWorker(SavedLocationsSyncResult.ServerError(503, "Unavailable")).doWork()
        )
        assertEquals(
            ListenableWorker.Result.failure(),
            createWorker(SavedLocationsSyncResult.TokenExpired("Expired")).doWork()
        )
        assertEquals(
            ListenableWorker.Result.failure(),
            createWorker(SavedLocationsSyncResult.UnsupportedWebhookOnly("Webhook")).doWork()
        )
    }
}
