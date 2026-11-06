package com.businesstracker.mileage.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.businesstracker.mileage.data.remote.GoogleSheetsSyncService
import com.businesstracker.mileage.data.remote.SheetsSyncResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class GoogleSheetsSyncWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun createWorker(mockResult: SheetsSyncResult): GoogleSheetsSyncWorker {
        val mockService = object : GoogleSheetsSyncService(
            com.businesstracker.mileage.data.preferences.TrackingPreferencesManager(context),
            object : com.businesstracker.mileage.domain.repository.TripRepository {
                override fun getAllTrips() = kotlinx.coroutines.flow.flowOf(emptyList<com.businesstracker.mileage.data.local.entity.TripEntity>())
                override fun getUnclassifiedTrips() = kotlinx.coroutines.flow.flowOf(emptyList<com.businesstracker.mileage.data.local.entity.TripEntity>())
                override fun getUnclassifiedTripsCount() = kotlinx.coroutines.flow.flowOf(0)
                override fun getTripsByDateRange(startDate: String, endDate: String) = kotlinx.coroutines.flow.flowOf(emptyList<com.businesstracker.mileage.data.local.entity.TripEntity>())
                override fun getTripsByBusinessEntity(entity: String) = kotlinx.coroutines.flow.flowOf(emptyList<com.businesstracker.mileage.data.local.entity.TripEntity>())
                override fun getBusinessEntitySummaries() = kotlinx.coroutines.flow.flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
                override fun getBusinessEntitySummariesForDateRange(startDate: String, endDate: String) = kotlinx.coroutines.flow.flowOf(emptyList<com.businesstracker.mileage.data.local.model.BusinessEntitySummary>())
                override fun getTripCountByClassification() = kotlinx.coroutines.flow.flowOf(emptyList<com.businesstracker.mileage.data.local.model.ClassificationCount>())
                override suspend fun getTripById(tripId: String) = null
                override fun observeTripById(tripId: String) = kotlinx.coroutines.flow.flowOf(null)
                override suspend fun insertTrip(trip: com.businesstracker.mileage.data.local.entity.TripEntity) {}
                override suspend fun updateTrip(trip: com.businesstracker.mileage.data.local.entity.TripEntity) {}
                override suspend fun deleteTrip(tripId: String) {}
                override suspend fun classifyTrip(tripId: String, classification: String, businessEntity: String, purpose: String, notes: String?) {}
                override suspend fun updateTripDetails(tripId: String, classification: String, businessEntity: String, purpose: String, notes: String?) {}
                override fun getTripsBySyncStatus(syncStatus: String) = kotlinx.coroutines.flow.flowOf(emptyList<com.businesstracker.mileage.data.local.entity.TripEntity>())
                override fun getTripsByClassification(classification: String) = kotlinx.coroutines.flow.flowOf(emptyList<com.businesstracker.mileage.data.local.entity.TripEntity>())
                override suspend fun getPendingSyncTrips() = emptyList<com.businesstracker.mileage.data.local.entity.TripEntity>()
                override suspend fun markSynced(tripId: String, timestamp: Long) {}
            },
            com.businesstracker.mileage.data.security.SecureStorageManager(context, com.businesstracker.mileage.data.security.TestSoftwareKeyProvider()),
            com.google.gson.Gson()
        ) {
            override suspend fun syncPendingTrips(): SheetsSyncResult = mockResult
        }

        return TestListenableWorkerBuilder<GoogleSheetsSyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return GoogleSheetsSyncWorker(appContext, workerParameters, mockService)
                }
            })
            .build()
    }

    @Test
    fun doWork_success_returnsSuccess() = runTest {
        val worker = createWorker(SheetsSyncResult.Success(2, 1))
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun doWork_notConfigured_returnsSuccess() = runTest {
        val worker = createWorker(SheetsSyncResult.NotConfigured)
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun doWork_partialSuccess_returnsRetry() = runTest {
        val worker = createWorker(SheetsSyncResult.PartialSuccess(1, 1, "Partial"))
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun doWork_rateLimited_returnsRetry() = runTest {
        val worker = createWorker(SheetsSyncResult.RateLimited("429 Too Many Requests"))
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun doWork_serverError_returnsRetry() = runTest {
        val worker = createWorker(SheetsSyncResult.ServerError(503, "Service Unavailable"))
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun doWork_networkError_returnsRetry() = runTest {
        val worker = createWorker(SheetsSyncResult.NetworkError("Timeout", IOException("Socket timeout")))
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun doWork_http5xx_returnsRetry() = runTest {
        val worker = createWorker(SheetsSyncResult.HttpError(502, "Bad Gateway"))
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun doWork_http4xx_returnsFailure() = runTest {
        val worker = createWorker(SheetsSyncResult.HttpError(400, "Bad Request"))
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun doWork_tokenExpired_returnsFailure() = runTest {
        val worker = createWorker(SheetsSyncResult.TokenExpired("401 Expired"))
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun doWork_accessDenied_returnsFailure() = runTest {
        val worker = createWorker(SheetsSyncResult.AccessDenied("403 Forbidden"))
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun doWork_notFound_returnsFailure() = runTest {
        val worker = createWorker(SheetsSyncResult.NotFound("404 Not Found"))
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun doWork_invalidConfig_returnsFailure() = runTest {
        val worker = createWorker(SheetsSyncResult.InvalidConfiguration("Insecure URL"))
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun doWork_unexpectedError_returnsFailure() = runTest {
        val worker = createWorker(SheetsSyncResult.UnexpectedError(IllegalArgumentException("Malformed")))
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }
}
