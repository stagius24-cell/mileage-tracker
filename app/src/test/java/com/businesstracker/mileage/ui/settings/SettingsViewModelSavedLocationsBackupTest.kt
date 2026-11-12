package com.businesstracker.mileage.ui.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import com.businesstracker.mileage.data.local.model.BusinessEntitySummary
import com.businesstracker.mileage.data.local.model.ClassificationCount
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.remote.GoogleSheetsSyncService
import com.businesstracker.mileage.data.remote.RestoreApplyResult
import com.businesstracker.mileage.data.remote.RestoreCandidate
import com.businesstracker.mileage.data.remote.RestoreItemStatus
import com.businesstracker.mileage.data.remote.RestorePreviewData
import com.businesstracker.mileage.data.remote.RestorePreviewResult
import com.businesstracker.mileage.data.remote.SavedLocationsSheetsService
import com.businesstracker.mileage.data.remote.SavedLocationsSyncResult
import com.businesstracker.mileage.data.repository.SavedLocationRepositoryImpl
import com.businesstracker.mileage.data.security.GoogleAuthManager
import com.businesstracker.mileage.data.security.SecureStorageManager
import com.businesstracker.mileage.data.security.TestSoftwareKeyProvider
import com.businesstracker.mileage.domain.repository.SavedLocationBackupScheduler
import com.businesstracker.mileage.domain.repository.TripRepository
import com.businesstracker.mileage.domain.usecase.GeocodeLocationUseCase
import com.businesstracker.mileage.domain.usecase.GetCurrentLocationUseCase
import com.businesstracker.mileage.domain.usecase.SearchAddressUseCase
import com.businesstracker.mileage.transition.ActivityTransitionManager
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelSavedLocationsBackupTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var secureStorageManager: SecureStorageManager
    private lateinit var repository: SavedLocationRepositoryImpl

    private val emptyTripRepo = object : TripRepository {
        override fun getAllTrips() = flowOf(emptyList<com.businesstracker.mileage.data.local.entity.TripEntity>())
        override fun getUnclassifiedTrips() = flowOf(emptyList<com.businesstracker.mileage.data.local.entity.TripEntity>())
        override fun getUnclassifiedTripsCount() = flowOf(0)
        override fun getTripsByDateRange(startDate: String, endDate: String) = flowOf(emptyList<com.businesstracker.mileage.data.local.entity.TripEntity>())
        override fun getTripsByBusinessEntity(entity: String) = flowOf(emptyList<com.businesstracker.mileage.data.local.entity.TripEntity>())
        override fun getBusinessEntitySummaries() = flowOf(emptyList<BusinessEntitySummary>())
        override fun getBusinessEntitySummariesForDateRange(startDate: String, endDate: String) = flowOf(emptyList<BusinessEntitySummary>())
        override fun getTripCountByClassification() = flowOf(emptyList<ClassificationCount>())
        override suspend fun getTripById(tripId: String) = null
        override fun observeTripById(tripId: String) = flowOf(null)
        override suspend fun insertTrip(trip: com.businesstracker.mileage.data.local.entity.TripEntity) {}
        override suspend fun updateTrip(trip: com.businesstracker.mileage.data.local.entity.TripEntity) {}
        override suspend fun deleteTrip(tripId: String) {}
        override suspend fun classifyTrip(tripId: String, classification: String, businessEntity: String, purpose: String, notes: String?) {}
        override suspend fun updateTripDetails(tripId: String, classification: String, businessEntity: String, purpose: String, notes: String?) {}
        override fun getTripsBySyncStatus(syncStatus: String) = flowOf(emptyList<com.businesstracker.mileage.data.local.entity.TripEntity>())
        override fun getTripsByClassification(classification: String) = flowOf(emptyList<com.businesstracker.mileage.data.local.entity.TripEntity>())
        override suspend fun getPendingSyncTrips() = emptyList<com.businesstracker.mileage.data.local.entity.TripEntity>()
        override suspend fun markSynced(tripId: String, timestamp: Long) {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferencesManager = TrackingPreferencesManager(context)
        runBlocking {
            preferencesManager.setSavedLocationsLastBackupTime(0L)
            preferencesManager.setSavedLocationsLastBackupError(null)
        }
        secureStorageManager = SecureStorageManager(context, TestSoftwareKeyProvider())
        repository = SavedLocationRepositoryImpl(
            savedLocationDao = database.savedLocationDao(),
            ioDispatcher = testDispatcher,
            database = database,
            syncQueueDao = database.savedLocationSyncQueueDao(),
            backupScheduler = object : SavedLocationBackupScheduler {
                override fun scheduleAutoBackup() {}
            }
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        fakeLocationsService: SavedLocationsSheetsService,
        fakeTripsService: GoogleSheetsSyncService? = null
    ): SettingsViewModel {
        val authManager = GoogleAuthManager(context, secureStorageManager, preferencesManager)
        val tripsSyncService = fakeTripsService ?: GoogleSheetsSyncService(preferencesManager, emptyTripRepo, secureStorageManager, authManager, Gson())
        return SettingsViewModel(
            preferencesManager = preferencesManager,
            activityTransitionManager = ActivityTransitionManager(context),
            tripRepository = emptyTripRepo,
            sheetsSyncService = tripsSyncService,
            savedLocationsSheetsService = fakeLocationsService,
            secureStorageManager = secureStorageManager,
            googleAuthManager = authManager,
            savedLocationRepository = repository,
            searchAddressUseCase = SearchAddressUseCase(context, testDispatcher),
            getCurrentLocationUseCase = GetCurrentLocationUseCase(
                context = context,
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(context),
                geocodeLocationUseCase = GeocodeLocationUseCase(context, testDispatcher),
                ioDispatcher = testDispatcher
            ),
            context = context
        )
    }

    @Test
    fun backUpNow_andRestorePreviewConfirmFlow_updatesUiStateAndRequiresConflictOptIn() = runTest {
        var appliedSelectedIds: Set<String>? = null
        var appliedApprovedConflicts: Set<String>? = null

        val newCloudLoc = SavedLocationEntity(
            id = "cloud-new",
            name = "Nursery North",
            latitude = 37.35,
            longitude = -121.89,
            address = "10 North Way",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        val conflictCloudLoc = SavedLocationEntity(
            id = "local-conflict",
            name = "Cloud Version",
            latitude = 37.36,
            longitude = -121.90,
            address = "20 Cloud Way",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        val localVersion = conflictCloudLoc.copy(name = "Local Version", updatedAt = 3000L)

        val previewData = RestorePreviewData(
            candidates = listOf(
                RestoreCandidate(newCloudLoc, null, RestoreItemStatus.NEW_FROM_CLOUD, selectedByDefault = true, hasPendingLocalDelete = false),
                RestoreCandidate(conflictCloudLoc, localVersion, RestoreItemStatus.LOCAL_NEWER, selectedByDefault = false, hasPendingLocalDelete = false)
            ),
            localOnlyLocations = emptyList()
        )

        val fakeService = object : SavedLocationsSheetsService(
            preferencesManager, repository, database.savedLocationSyncQueueDao(), secureStorageManager, null, Gson(), testDispatcher
        ) {
            override suspend fun backupSavedLocations(fullSnapshot: Boolean): SavedLocationsSyncResult {
                assertTrue(fullSnapshot)
                preferencesManager.setSavedLocationsLastBackupTime(1731350000000L)
                return SavedLocationsSyncResult.Success(uploadedCount = 2, deletedCount = 0, timestamp = 1731350000000L)
            }

            override suspend fun previewRestoreFromSheets(): RestorePreviewResult {
                return RestorePreviewResult.Success(previewData)
            }

            override suspend fun applyRestoreSelection(
                preview: RestorePreviewData,
                selectedCandidateIds: Set<String>,
                approvedConflictIds: Set<String>
            ): RestoreApplyResult {
                appliedSelectedIds = selectedCandidateIds
                appliedApprovedConflicts = approvedConflictIds
                return RestoreApplyResult.Success(
                    importedCount = 1,
                    updatedCount = if ("local-conflict" in approvedConflictIds) 1 else 0,
                    preservedLocalCount = if ("local-conflict" in approvedConflictIds) 0 else 1
                )
            }
        }

        val viewModel = createViewModel(fakeService)
        advanceUntilIdle()

        // 1. Back Up Now updates timestamp & message
        viewModel.backUpSavedLocationsNow()
        withContext(Dispatchers.Default) {
            withTimeout(5000) {
                while (viewModel.uiState.value.savedLocationsLastBackupTime != 1731350000000L ||
                    viewModel.uiState.value.savedLocationsBackupMessage == null
                ) {
                    advanceUntilIdle()
                    delay(10)
                }
            }
        }
        advanceUntilIdle()
        assertEquals(1731350000000L, viewModel.uiState.value.savedLocationsLastBackupTime)
        assertTrue(viewModel.uiState.value.savedLocationsBackupMessage!!.contains("Backed up 2 locations"))

        // 2. Restore opens preview with ONLY new item checked, conflict unchecked by default
        viewModel.restoreSavedLocationsFromSheets()
        advanceUntilIdle()
        val loaded = viewModel.uiState.value.restorePreviewState as SavedLocationsRestorePreviewUi.Loaded
        assertEquals(setOf("cloud-new"), loaded.selectedIds)
        assertTrue(loaded.approvedConflictIds.isEmpty())

        // 3. Explicit user toggle approves overwriting conflict, then confirm applies selection & dismisses preview
        viewModel.toggleRestoreCandidateSelection("local-conflict")
        advanceUntilIdle()
        val updatedPreview = viewModel.uiState.value.restorePreviewState as SavedLocationsRestorePreviewUi.Loaded
        assertEquals(setOf("cloud-new", "local-conflict"), updatedPreview.selectedIds)
        assertEquals(setOf("local-conflict"), updatedPreview.approvedConflictIds)

        viewModel.confirmApplySavedLocationsRestore()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.restorePreviewState)
        assertEquals(setOf("cloud-new", "local-conflict"), appliedSelectedIds)
        assertEquals(setOf("local-conflict"), appliedApprovedConflicts)
        assertFalse(viewModel.uiState.value.isApplyingRestore)
        assertTrue(viewModel.uiState.value.savedLocationsBackupMessage!!.contains("Restored 1 new, 1 overwritten"))
    }

    @Test
    fun backupAllNow_bothSucceed_setsUnifiedSuccessMessage() = runTest {
        var tripsSynced = false
        var locationsBackedUp = false

        val fakeTripsService = object : GoogleSheetsSyncService(
            preferencesManager, emptyTripRepo, secureStorageManager, Gson()
        ) {
            override suspend fun syncPendingTrips(): com.businesstracker.mileage.data.remote.SheetsSyncResult {
                tripsSynced = true
                return com.businesstracker.mileage.data.remote.SheetsSyncResult.Success(1, 0)
            }
        }

        val fakeLocsService = object : SavedLocationsSheetsService(
            preferencesManager, repository, database.savedLocationSyncQueueDao(), secureStorageManager, null, Gson(), testDispatcher
        ) {
            override suspend fun backupSavedLocations(fullSnapshot: Boolean): SavedLocationsSyncResult {
                locationsBackedUp = true
                return SavedLocationsSyncResult.Success(1, 0, 1731350000000L)
            }
        }

        val viewModel = createViewModel(fakeLocsService, fakeTripsService)
        advanceUntilIdle()

        viewModel.backupAllNow()
        withContext(Dispatchers.Default) {
            withTimeout(5000) {
                while (viewModel.uiState.value.unifiedBackupMessage == null) {
                    advanceUntilIdle()
                    delay(10)
                }
            }
        }
        advanceUntilIdle()

        assertTrue(tripsSynced)
        assertTrue(locationsBackedUp)
        assertEquals("All data backed up", viewModel.uiState.value.unifiedBackupMessage)
        assertNull(viewModel.uiState.value.unifiedBackupError)
        assertFalse(viewModel.uiState.value.isBackingUpAll)
    }

    @Test
    fun backupAllNow_tripsFail_reportsPartialFailureAccurately() = runTest {
        val fakeTripsService = object : GoogleSheetsSyncService(
            preferencesManager, emptyTripRepo, secureStorageManager, Gson()
        ) {
            override suspend fun syncPendingTrips(): com.businesstracker.mileage.data.remote.SheetsSyncResult {
                return com.businesstracker.mileage.data.remote.SheetsSyncResult.NetworkError("No connection", java.io.IOException("No connection"))
            }
        }

        val fakeLocsService = object : SavedLocationsSheetsService(
            preferencesManager, repository, database.savedLocationSyncQueueDao(), secureStorageManager, null, Gson(), testDispatcher
        ) {
            override suspend fun backupSavedLocations(fullSnapshot: Boolean): SavedLocationsSyncResult {
                return SavedLocationsSyncResult.Success(1, 0, 1731350000000L)
            }
        }

        val viewModel = createViewModel(fakeLocsService, fakeTripsService)
        advanceUntilIdle()

        viewModel.backupAllNow()
        withContext(Dispatchers.Default) {
            withTimeout(5000) {
                while (viewModel.uiState.value.unifiedBackupError == null) {
                    advanceUntilIdle()
                    delay(10)
                }
            }
        }
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.unifiedBackupMessage)
        assertTrue(viewModel.uiState.value.unifiedBackupError!!.contains("Saved Locations backed up, but trips failed"))
    }

    @Test
    fun backupAllNow_locationsFail_reportsPartialFailureAccurately() = runTest {
        val fakeTripsService = object : GoogleSheetsSyncService(
            preferencesManager, emptyTripRepo, secureStorageManager, Gson()
        ) {
            override suspend fun syncPendingTrips(): com.businesstracker.mileage.data.remote.SheetsSyncResult {
                return com.businesstracker.mileage.data.remote.SheetsSyncResult.Success(2, 0)
            }
        }

        val fakeLocsService = object : SavedLocationsSheetsService(
            preferencesManager, repository, database.savedLocationSyncQueueDao(), secureStorageManager, null, Gson(), testDispatcher
        ) {
            override suspend fun backupSavedLocations(fullSnapshot: Boolean): SavedLocationsSyncResult {
                return SavedLocationsSyncResult.NetworkError("Failed to reach Sheets")
            }
        }

        val viewModel = createViewModel(fakeLocsService, fakeTripsService)
        advanceUntilIdle()

        viewModel.backupAllNow()
        withContext(Dispatchers.Default) {
            withTimeout(5000) {
                while (viewModel.uiState.value.unifiedBackupError == null) {
                    advanceUntilIdle()
                    delay(10)
                }
            }
        }
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.unifiedBackupMessage)
        assertTrue(viewModel.uiState.value.unifiedBackupError!!.contains("Trips up to date, but Saved Locations backup failed"))
    }
}
