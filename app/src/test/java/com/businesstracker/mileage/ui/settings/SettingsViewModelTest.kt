package com.businesstracker.mileage.ui.settings

import android.content.Context
import androidx.room.Room
import androidx.lifecycle.viewModelScope
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.remote.GoogleSheetsSyncService
import com.businesstracker.mileage.data.repository.TripRepositoryImpl
import com.businesstracker.mileage.data.security.GoogleAuthManager
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.data.security.TestSoftwareKeyProvider
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.service.TrackingServiceState
import com.businesstracker.mileage.transition.ActivityTransitionManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var tripRepository: TripRepository
    private lateinit var secureStorageManager: SecureStorageManager
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var activityTransitionManager: ActivityTransitionManager
    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var sheetsSyncService: GoogleSheetsSyncService
    private var currentViewModel: SettingsViewModel? = null
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tripRepository = TripRepositoryImpl(database.tripDao(), testDispatcher, context)

        val keyProvider = TestSoftwareKeyProvider()
        secureStorageManager = SecureStorageManager(context, keyProvider)
        secureStorageManager.clearAllCredentials()

        preferencesManager = TrackingPreferencesManager(context, secureStorageManager)
        activityTransitionManager = ActivityTransitionManager(context)
        googleAuthManager = GoogleAuthManager(context, secureStorageManager, preferencesManager)
        sheetsSyncService = GoogleSheetsSyncService(
            preferencesManager,
            tripRepository,
            secureStorageManager,
            googleAuthManager,
            Gson()
        )

        LocationTrackingService.resetGuardForTesting()

        // Prime Room and DataStore flows
        preferencesManager.isAutoTrackingEnabled.first()
        database.tripDao().getAllTrips().first()
    }

    @After
    fun tearDown() {
        currentViewModel?.let { vm ->
            vm.viewModelScope.coroutineContext[Job]?.cancelChildren()
        }
        currentViewModel = null
        LocationTrackingService.resetGuardForTesting()
        Dispatchers.resetMain()
        database.close()
        secureStorageManager.clearAllCredentials()
    }

    private fun createViewModel(customRepo: TripRepository = tripRepository): SettingsViewModel {
        return SettingsViewModel(
            preferencesManager = preferencesManager,
            activityTransitionManager = activityTransitionManager,
            tripRepository = customRepo,
            sheetsSyncService = sheetsSyncService,
            secureStorageManager = secureStorageManager,
            googleAuthManager = googleAuthManager,
            context = context
        ).also { currentViewModel = it }
    }

    @Test
    fun initialDeletionState_isNotLoading_andMessageIsNull() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Initial deletion state must not be loading", state.isDeletingAllTrips)
        assertNull("Initial deletion message must be null", state.deletionMessage)
    }

    @Test
    fun successfulDeletion_updatesState_andDeletesLocalData() = runTest(testDispatcher) {
        // Seed trip record
        val tripId = "test_trip_delete_me"
        database.tripDao().insertTrip(
            TripEntity(
                id = tripId,
                date = "2026-11-06",
                startTime = 1000L,
                endTime = 2000L,
                startAddress = "Home",
                endAddress = "Office",
                startLatitude = 37.7749,
                startLongitude = -122.4194,
                endLatitude = 37.7750,
                endLongitude = -122.4195,
                totalDistanceMiles = 8.5,
                businessMiles = 8.5,
                classification = "BUSINESS",
                businessEntity = "MySucculentCorner",
                purpose = "Consulting",
                applicableRate = 0.760,
                calculatedDeduction = 6.46,
                trackingMethod = TrackingMethod.AUTO_DETECTED.value,
                syncStatus = "PENDING",
                syncedAt = null,
                notes = null,
                waypointsJson = "[]"
            )
        )
        assertEquals(1, database.tripDao().getAllTrips().first().size)

        val viewModel = createViewModel()
        advanceUntilIdle()

        var callbackResult: Result<Int>? = null
        viewModel.deleteAllTripData { result ->
            callbackResult = result
        }
        advanceUntilIdle()

        assertNotNull(callbackResult)
        assertTrue("Callback should be success", callbackResult!!.isSuccess)
        assertEquals(1, callbackResult!!.getOrNull())

        val state = viewModel.uiState.first { it.deletionMessage != null }
        assertFalse("Loading state must be reset to false after deletion", state.isDeletingAllTrips)
        assertNotNull("Success message must be populated", state.deletionMessage)
        assertTrue("Message must mention deletion count", state.deletionMessage!!.contains("Successfully deleted 1 local trip records"))

        // Verify database is completely empty
        assertEquals(0, database.tripDao().getAllTrips().first().size)
    }

    @Test
    fun failedDeletion_updatesErrorMessage_andResetsLoadingState() = runTest(testDispatcher) {
        // Failing repository simulation
        val failingRepo = object : TripRepository by tripRepository {
            override suspend fun deleteAllTripData(): Int {
                throw IllegalStateException("Simulated SQLite write lock failure")
            }
        }

        val viewModel = createViewModel(customRepo = failingRepo)
        advanceUntilIdle()

        var callbackResult: Result<Int>? = null
        viewModel.deleteAllTripData { result ->
            callbackResult = result
        }
        advanceUntilIdle()

        assertNotNull(callbackResult)
        assertTrue("Callback should report failure", callbackResult!!.isFailure)

        val state = viewModel.uiState.first { it.deletionMessage != null }
        assertFalse("Loading state must reset to false after error", state.isDeletingAllTrips)
        assertNotNull("Error message must be set", state.deletionMessage)
        assertTrue("Message must describe error", state.deletionMessage!!.contains("Failed to delete local trip data"))
    }

    @Test
    fun activeTracking_preventsDeletion_andSetsWarningMessage() = runTest(testDispatcher) {
        // Simulate active tracking in progress
        LocationTrackingService.setServiceStateForTesting(
            TrackingServiceState(
                isTracking = true,
                isPaused = false,
                tripId = "active_trip_in_progress",
                distanceMeters = 500.0,
                distanceMiles = 0.31,
                durationMillis = 30000L,
                statusMessage = "Tracking active"
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        var callbackResult: Result<Int>? = null
        viewModel.deleteAllTripData { result ->
            callbackResult = result
        }
        advanceUntilIdle()

        assertNotNull(callbackResult)
        assertTrue("Deletion must fail when tracking is active", callbackResult!!.isFailure)

        val state = viewModel.uiState.first { it.deletionMessage != null }
        assertFalse("Must not enter loading state", state.isDeletingAllTrips)
        assertNotNull("Warning message must be set", state.deletionMessage)
        assertTrue("Message must warn about active tracking", state.deletionMessage!!.contains("Cannot delete while tracking is active"))
    }

    @Test
    fun clearDeletionMessage_resetsMessageToNull() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        var callbackResult: Result<Int>? = null
        viewModel.deleteAllTripData { result ->
            callbackResult = result
        }
        advanceUntilIdle()

        val stateWithMessage = viewModel.uiState.first { it.deletionMessage != null }
        assertNotNull(stateWithMessage.deletionMessage)

        viewModel.clearDeletionMessage()
        advanceUntilIdle()

        val clearedState = viewModel.uiState.first { it.deletionMessage == null }
        assertNull("Deletion message must be cleared", clearedState.deletionMessage)
    }

    @Test
    fun deletion_preservesCredentialsAndSettings() = runTest(testDispatcher) {
        secureStorageManager.saveOAuthToken("keep_this_oauth_token")
        secureStorageManager.saveSheetsWebhookUrl("https://example.com/keep_webhook")
        secureStorageManager.saveCfBearerToken("keep_cf_token")
        preferencesManager.setDefaultVehicle("Rivian R1T")
        preferencesManager.setAutoTrackingEnabled(true)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteAllTripData()
        advanceUntilIdle()

        // Verify Settings flows still reflect accurate values
        val state = viewModel.uiState.value
        assertEquals("Rivian R1T", state.defaultVehicle)
        assertTrue(state.autoTrackingEnabled)
        assertEquals("https://example.com/keep_webhook", state.sheetsWebhookUrl)
        assertTrue(state.hasOAuthCredential)
    }
}
