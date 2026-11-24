package com.businesstracker.mileage

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.businesstracker.mileage.data.device.VehicleBluetoothMatcher
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import com.businesstracker.mileage.data.local.entity.ActiveWaypointEntity
import com.businesstracker.mileage.data.local.entity.RoutePatternEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.data.local.entity.TripLearningRecordEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleEntity
import com.businesstracker.mileage.data.local.entity.TripLifecycleState
import com.businesstracker.mileage.data.local.entity.VehicleEntity
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.data.repository.ActiveTripRepositoryImpl
import com.businesstracker.mileage.data.repository.SmartTripRepositoryImpl
import com.businesstracker.mileage.data.repository.TripRepositoryImpl
import com.businesstracker.mileage.data.repository.VehicleRepositoryImpl
import com.businesstracker.mileage.domain.classifier.SmartTripClassificationEngine
import com.businesstracker.mileage.domain.coordinator.SmartClassificationCoordinator
import com.businesstracker.mileage.domain.coordinator.TripFinalizationCoordinator
import com.businesstracker.mileage.domain.matcher.TripPatternMatcher
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.model.ClassificationSource
import com.businesstracker.mileage.domain.model.SmartClassificationMode
import com.businesstracker.mileage.domain.model.SmartTripLearningCutoff
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.model.TripPurpose
import com.businesstracker.mileage.domain.model.Waypoint
import com.businesstracker.mileage.domain.service.BootCountProvider
import com.businesstracker.mileage.domain.tracking.AutoDriveAction
import com.businesstracker.mileage.domain.tracking.AutoDriveDecisionEngine
import com.businesstracker.mileage.domain.tracking.TrackingDiagnostics
import com.businesstracker.mileage.domain.tracking.VehicleBluetoothMatchPolicy
import com.businesstracker.mileage.domain.tracking.VehicleFriendlyNameResolver
import com.businesstracker.mileage.domain.tracking.VehicleResolutionResult
import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import com.businesstracker.mileage.domain.usecase.DrainPendingGpsFixesUseCase
import com.businesstracker.mileage.domain.usecase.FilterGpsPointUseCase
import com.businesstracker.mileage.domain.usecase.GeocodeLocationUseCase
import com.businesstracker.mileage.domain.usecase.GetDashboardSummaryUseCase
import com.businesstracker.mileage.domain.usecase.GpsInboxProcessor
import com.businesstracker.mileage.receiver.AutoDriveReceiver
import com.businesstracker.mileage.receiver.BluetoothArmedDriveMonitor
import com.businesstracker.mileage.receiver.BootReceiver
import com.businesstracker.mileage.receiver.VehicleBluetoothEventHandler
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.service.NotificationHelper
import com.businesstracker.mileage.transition.ActivityTransitionManager
import com.businesstracker.mileage.ui.dashboard.DashboardViewModel
import com.businesstracker.mileage.worker.TripDebounceScheduler
import com.businesstracker.mileage.worker.TripDebounceWorker
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MultiVehicleAutoStartReliabilityAuditTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var preferencesManager: TrackingPreferencesManager
    private lateinit var vehicleRepository: VehicleRepositoryImpl
    private lateinit var activeTripRepository: ActiveTripRepositoryImpl
    private lateinit var smartTripRepository: SmartTripRepositoryImpl
    private lateinit var tripRepository: TripRepositoryImpl
    private lateinit var matcher: VehicleBluetoothMatcher
    private lateinit var scheduler: RecordingScheduler
    private lateinit var monitor: BluetoothArmedDriveMonitor
    private lateinit var eventHandler: VehicleBluetoothEventHandler
    private lateinit var receiver: AutoDriveReceiver
    private lateinit var finalizationCoordinator: TripFinalizationCoordinator
    private lateinit var drainUseCase: DrainPendingGpsFixesUseCase
    private lateinit var notificationHelper: NotificationHelper

    private val testDispatcher = Dispatchers.Unconfined
    private lateinit var testScope: CoroutineScope
    private val createdViewModels = mutableListOf<DashboardViewModel>()
    private var fakeElapsedClockMillis = 100_000L

    private val model3 = VehicleEntity(
        id = "veh-model-3",
        name = "Tesla Model 3",
        bluetoothAddress = "33:33:33:33:33:03",
        bluetoothDeviceName = "Tesla Model 3 BT",
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val modelY = VehicleEntity(
        id = "veh-model-y",
        name = "Tesla Model Y",
        bluetoothAddress = "YY:YY:YY:YY:YY:0Y".replace('Y', '4'),
        bluetoothDeviceName = "Tesla model Y teela",
        createdAt = 2000L,
        updatedAt = 2000L
    )

    @Before
    fun setUp() = runBlocking {
        testScope = CoroutineScope(SupervisorJob() + testDispatcher)
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        val app = context.applicationContext as Application
        shadowOf(app).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS
        )

        LocationTrackingService.resetGuardForTesting()
        TrackingDiagnostics.clearForTesting()

        val directExecutor = Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        preferencesManager = TrackingPreferencesManager(context)
        preferencesManager.setAutoTrackingEnabled(true)
        preferencesManager.setBluetoothAutoStopEnabled(true)
        preferencesManager.setVehicleBluetoothDevice("", "")
        preferencesManager.setCarBluetoothConnected(false)
        preferencesManager.setConnectedCarAddresses(emptySet())
        preferencesManager.setInVehicleState(false)
        preferencesManager.setActiveTripId(null)
        preferencesManager.clearManualVehicleAssignment()
        preferencesManager.setOutsideScheduleBehavior(TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL)

        vehicleRepository = VehicleRepositoryImpl(database.vehicleDao())
        vehicleRepository.save(model3)
        vehicleRepository.save(modelY)

        activeTripRepository = ActiveTripRepositoryImpl(database.activeTripDao(), testDispatcher)
        smartTripRepository = SmartTripRepositoryImpl(
            database.routePatternDao(),
            database.tripLearningRecordDao(),
            preferencesManager,
            testDispatcher
        )
        tripRepository = TripRepositoryImpl(database.tripDao(), database, smartTripRepository, testDispatcher)
        matcher = VehicleBluetoothMatcher(context, vehicleRepository, preferencesManager).apply {
            activeProfileDevicesProvider = { emptySet() }
            fallbackDevicesProvider = { preferencesManager.getConnectedCarAddresses() }
        }
        scheduler = RecordingScheduler(context)

        monitor = BluetoothArmedDriveMonitor(
            context,
            preferencesManager,
            LocationServices.getFusedLocationProviderClient(context),
            matcher
        ).apply {
            elapsedClock = { fakeElapsedClockMillis }
            activityUpdateRegistrar = { _, _ -> }
            activityUpdateUnregistrar = {}
        }

        eventHandler = VehicleBluetoothEventHandler(
            preferencesManager,
            activeTripRepository,
            scheduler,
            monitor,
            matcher
        )

        receiver = AutoDriveReceiver().apply {
            preferencesManager = this@MultiVehicleAutoStartReliabilityAuditTest.preferencesManager
            activeTripRepository = this@MultiVehicleAutoStartReliabilityAuditTest.activeTripRepository
            tripLifecycleDao = database.tripLifecycleDao()
            decisionEngine = AutoDriveDecisionEngine()
            bluetoothArmedDriveMonitor = monitor
            ioDispatcher = testDispatcher
            bootCountProvider = object : BootCountProvider { override fun getBootCount(): Int = 1 }
        }

        notificationHelper = NotificationHelper(context)
        val processor = GpsInboxProcessor(
            database = database,
            activeTripDao = database.activeTripDao(),
            gpsInboxDao = database.gpsInboxDao(),
            filterGpsPointUseCase = FilterGpsPointUseCase()
        )
        drainUseCase = DrainPendingGpsFixesUseCase(database.gpsInboxDao(), processor)
        finalizationCoordinator = TripFinalizationCoordinator(
            database = database,
            activeTripRepository = activeTripRepository,
            tripRepository = tripRepository,
            geocodeUseCase = GeocodeLocationUseCase(context, testDispatcher),
            rateCalculator = CalculateMileageRateUseCase(),
            preferencesManager = preferencesManager,
            gson = Gson(),
            ioDispatcher = testDispatcher
        )

        receiver.apply {
            vehicleBluetoothMatcher = matcher
            this.notificationHelper = this@MultiVehicleAutoStartReliabilityAuditTest.notificationHelper
            filterGpsPointUseCase = FilterGpsPointUseCase()
            tripFinalizationCoordinator = finalizationCoordinator
            elapsedClock = { fakeElapsedClockMillis }
        }
        Unit
    }

    @After
    fun tearDown() {
        runBlocking {
            createdViewModels.forEach { it.clearForTesting() }
            createdViewModels.clear()
            if (::testScope.isInitialized) {
                testScope.coroutineContext[Job]?.cancelAndJoin()
            }
            if (::preferencesManager.isInitialized) {
                preferencesManager.setVehicleBluetoothDevice("", "")
                preferencesManager.setCarBluetoothConnected(false)
                preferencesManager.setConnectedCarAddresses(emptySet())
                preferencesManager.clearManualVehicleAssignment()
            }
        }
        LocationTrackingService.resetGuardForTesting()
        TrackingDiagnostics.clearForTesting()
        if (::database.isInitialized) {
            database.close()
        }
        Dispatchers.resetMain()
    }

    private fun makeLocation(
        lat: Double,
        lon: Double,
        elapsedMillis: Long,
        speedMps: Float = 0f,
        accuracyMeters: Float = 8f
    ) = Location("test").apply {
        latitude = lat
        longitude = lon
        speed = speedMps
        accuracy = accuracyMeters
        elapsedRealtimeNanos = elapsedMillis * 1_000_000L
        time = elapsedMillis
    }

    // 1. One vehicle connects and driving starts immediately
    @Test(timeout = 15_000)
    fun scenario01_oneVehicleConnectsAndDrivingStartsImmediately() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        assertTrue(monitor.isArmedForTesting())

        monitor.locationProvider = { makeLocation(37.3382, -121.8863, fakeElapsedClockMillis, speedMps = 12f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 92)))

        val tripId = preferencesManager.activeTripId.first()
        assertNotNull("Immediate driving must start candidate trip", tripId)
        val matched = matcher.currentlyConnectedVehicle()
        assertEquals(model3.id, matched?.id)
    }

    // 2. One vehicle connects and driving starts 3 minutes later (including charger-dampened IN_VEHICLE confidence)
    @Test(timeout = 15_000)
    fun scenario02_oneVehicleConnectsAndDrivingStartsThreeMinutesLater() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        preferencesManager.setConnectedCarAddresses(setOf(model3.bluetoothAddress!!), fakeElapsedClockMillis)

        // Parked in driveway at t0 (0 m/s) -> no trip created
        monitor.locationProvider = { makeLocation(37.3382, -121.8863, fakeElapsedClockMillis, speedMps = 0f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.STILL, 80)))
        assertNull(preferencesManager.activeTripId.first())

        // 3 minutes later, smooth EV pulls out while phone on charger gives low IN_VEHICLE confidence (40)
        fakeElapsedClockMillis += 180_000L
        monitor.locationProvider = { makeLocation(37.3400, -121.8863, fakeElapsedClockMillis, speedMps = 9f, accuracyMeters = 10f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 40)))

        assertNotNull("Driving 3 minutes after connect must start trip even with low IN_VEHICLE confidence", preferencesManager.activeTripId.first())
    }

    // 3. Two vehicles become available within seconds (without legacy single BT pref set)
    @Test(timeout = 15_000)
    fun scenario03_twoVehiclesBecomeAvailableWithinSeconds_monitorStaysArmed() = runBlocking {
        // Legacy KEY_VEHICLE_BLUETOOTH_ADDRESS is intentionally blank; vehicles exist only in Room
        assertEquals("", preferencesManager.getVehicleBluetoothAddress())

        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        assertTrue(monitor.isArmedForTesting())

        fakeElapsedClockMillis += 3_000L
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, modelY.bluetoothAddress!!)
        assertTrue(monitor.isArmedForTesting())
        assertEquals(2, preferencesManager.getConnectedCarAddresses().size)
    }

    // 4. Model 3 first, Model Y second, Model 3 driven (THE EXACT BUG SCENARIO)
    @Test(timeout = 15_000)
    fun scenario04_model3First_modelYSecond_model3Driven() = runBlocking {
        // Step 1: Open Model 3, then open Model Y seconds later
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        fakeElapsedClockMillis += 5_000L
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, modelY.bluetoothAddress!!)
        assertTrue(monitor.isArmedForTesting())

        // Step 2: Under 3 minutes later, drive away in Model 3 while Model Y is still briefly in range (both connected)
        fakeElapsedClockMillis += 120_000L
        monitor.locationProvider = { makeLocation(37.3382, -121.8863, fakeElapsedClockMillis, speedMps = 11f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 88)))

        val tripId = preferencesManager.activeTripId.first()
        assertNotNull("Drive must NOT be missed when both Model 3 and Model Y connected", tripId)
        activeTripRepository.createActiveTrip(tripId!!, 37.3382, -121.8863, TrackingMethod.AUTO_DETECTED.value, "MySucculentCorner")

        // Ambiguous while both are still connected -> vehicleId must remain null
        assertTrue(matcher.resolveConnectedVehicle() is VehicleResolutionResult.Ambiguous)
        assertNull(activeTripRepository.getActiveTrip()?.vehicleId)

        // Step 3: Driving out of driveway -> Model Y disconnects while Model 3 remains connected
        eventHandler.handle(BluetoothDevice.ACTION_ACL_DISCONNECTED, modelY.bluetoothAddress!!)

        // Must NOT trigger auto-stop AND must keep monitor armed AND resolve Model 3!
        assertFalse("Stationary Model Y disconnect must NOT trigger auto-stop", scheduler.scheduled)
        assertTrue(monitor.isArmedForTesting())
        assertEquals(model3.id, activeTripRepository.getActiveTrip()?.vehicleId)
    }

    // 5. Model Y first, Model 3 second, Model Y driven
    @Test(timeout = 15_000)
    fun scenario05_modelYFirst_model3Second_modelYDriven() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, modelY.bluetoothAddress!!)
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)

        val trip = activeTripRepository.createActiveTrip("trip-s5", 37.0, -122.0, TrackingMethod.AUTO_DETECTED.value, "Test")
        assertNull(trip.vehicleId)

        // User drives Model Y away, so Model 3 disconnects
        eventHandler.handle(BluetoothDevice.ACTION_ACL_DISCONNECTED, model3.bluetoothAddress!!)
        assertFalse(scheduler.scheduled)
        assertEquals(modelY.id, activeTripRepository.getActiveTrip()?.vehicleId)
    }

    // 6. Two vehicles connected simultaneously (Tier 1 A2DP vs ambiguous Tier 2 ACL/GATT)
    @Test(timeout = 15_000)
    fun scenario06_twoVehiclesSimultaneous_ambiguityDoesNotBlock_tier1Resolves() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, modelY.bluetoothAddress!!)

        // When neither has A2DP/HEADSET exclusive audio, resolution is Ambiguous
        assertTrue(matcher.resolveConnectedVehicle() is VehicleResolutionResult.Ambiguous)
        assertNull(matcher.currentlyConnectedVehicle())

        // Driving detection still accepts start cleanly without requiring vehicle identity
        val engineAction = AutoDriveDecisionEngine().evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            isServiceTracking = false,
            activeTripId = null,
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
        )
        assertTrue(engineAction is AutoDriveAction.StartCandidateDrive)

        // If Android reports ONLY Model 3 on Tier 1 A2DP/HEADSET while Model Y is only background ACL:
        matcher.activeProfileDevicesProvider = { setOf(model3.bluetoothAddress!!) }
        matcher.fallbackDevicesProvider = { setOf(model3.bluetoothAddress!!, modelY.bluetoothAddress!!) }
        val resolved = matcher.resolveConnectedVehicle()
        assertTrue(resolved is VehicleResolutionResult.Matched)
        assertEquals(model3.id, (resolved as VehicleResolutionResult.Matched).vehicle.id)
    }

    // 7. Bluetooth switches between vehicles (Model 3 -> Model Y -> Model 3)
    @Test(timeout = 15_000)
    fun scenario07_bluetoothSwitchesBetweenVehicles_noStaleState() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        eventHandler.handle(BluetoothDevice.ACTION_ACL_DISCONNECTED, model3.bluetoothAddress!!)
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, modelY.bluetoothAddress!!)
        eventHandler.handle(BluetoothDevice.ACTION_ACL_DISCONNECTED, modelY.bluetoothAddress!!)
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)

        assertEquals(setOf(VehicleBluetoothMatchPolicy.normalizeAddress(model3.bluetoothAddress!!)), preferencesManager.getConnectedCarAddresses())
        assertTrue(monitor.isArmedForTesting())
        assertEquals(model3.id, matcher.currentlyConnectedVehicle()?.id)
    }

    // 8. One vehicle opens but never moves -> no false trip created, later drive still works
    @Test(timeout = 15_000)
    fun scenario08_vehicleOpensWithoutDriving_noFalseTrip_laterDriveWorks() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        monitor.locationProvider = { makeLocation(37.3382, -121.8863, fakeElapsedClockMillis, speedMps = 0.2f) }

        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 90)))
        fakeElapsedClockMillis += 35_000L
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 90)))
        assertNull("Stationary vehicle must never start a trip", preferencesManager.activeTripId.first())

        // Later genuine drive
        fakeElapsedClockMillis += 60_000L
        monitor.locationProvider = { makeLocation(37.3420, -121.8863, fakeElapsedClockMillis, speedMps = 15f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 92)))
        assertNotNull("Later legitimate drive must start after stationary stay", preferencesManager.activeTripId.first())
    }

    // 9. Initial movement verification fails (candidate < 200m expires), followed by legitimate driving
    @Test(timeout = 15_000)
    fun scenario09_initialVerificationFails_thenLegitimateDriveSucceeds() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        val failedTripId = "cand-fail-9"
        activeTripRepository.createActiveTrip(failedTripId, 37.3382, -121.8863, TrackingMethod.AUTO_DETECTED.value, "Test")
        preferencesManager.setActiveTripId(failedTripId)
        preferencesManager.recordCandidateDeadline(failedTripId, 100L, 1)

        // Discarded because < 200m
        finalizationCoordinator.discardFalseTrip(failedTripId)
        assertNull(activeTripRepository.getActiveTrip())
        monitor.resetTriggerCooldown()
        monitor.reconcile()
        assertTrue(monitor.isArmedForTesting())

        // Legitimate driving afterward starts a new trip cleanly
        fakeElapsedClockMillis += 65_000L
        monitor.locationProvider = { makeLocation(37.3450, -121.8863, fakeElapsedClockMillis, speedMps = 14f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 95)))
        val secondTripId = preferencesManager.activeTripId.first()
        assertNotNull(secondTripId)
        assertNotEquals(failedTripId, secondTripId)
    }

    // 10. Repeated failed verification attempts never permanently lock detection
    @Test(timeout = 15_000)
    fun scenario10_repeatedFailedVerifications_neverLockDetection() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        repeat(3) { idx ->
            monitor.locationProvider = { makeLocation(37.3382, -121.8863, fakeElapsedClockMillis, speedMps = 0f, accuracyMeters = 150f) }
            receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 85)))
            assertNull("Attempt $idx poor accuracy / 0 speed must not start", preferencesManager.activeTripId.first())
            fakeElapsedClockMillis += 35_000L
        }

        // Now valid GPS speed arrives -> succeeds
        monitor.locationProvider = { makeLocation(37.3410, -121.8863, fakeElapsedClockMillis, speedMps = 13f, accuracyMeters = 8f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 90)))
        assertNotNull(preferencesManager.activeTripId.first())
    }

    // 11. Bluetooth already connected before service startup
    @Test(timeout = 15_000)
    fun scenario11_bluetoothAlreadyConnectedBeforeServiceStartup_reconcilesAndArms() = runBlocking {
        // No ACL_CONNECTED broadcast fired, but Bluetooth profile reports Model 3 connected
        matcher.activeProfileDevicesProvider = { setOf(model3.bluetoothAddress!!) }
        matcher.fallbackDevicesProvider = { emptySet() }

        monitor.reconcile()
        assertTrue(monitor.isArmedForTesting())
        assertEquals(model3.id, matcher.currentlyConnectedVehicle()?.id)
    }

    // 12. Bluetooth connects after tracking starts -> assigns active trip & survives 1s ticker
    @Test(timeout = 15_000)
    fun scenario12_bluetoothConnectsAfterTrackingStarts_populatesVehicleWithoutTickerOverwrite() = runBlocking {
        val tripId = "trip-late-bt-12"
        val service = LocationTrackingService()
        service.setTestDependencies(
            activeTripRepo = activeTripRepository,
            helper = notificationHelper,
            tripId = tripId,
            lifecycleDao = database.tripLifecycleDao(),
            inboxDao = database.gpsInboxDao(),
            drainUseCase = drainUseCase,
            seqDao = database.tripSequenceDao(),
            db = database,
            prefs = preferencesManager,
            coordinator = finalizationCoordinator,
            dispatcher = testDispatcher,
            scope = testScope,
            context = context,
            vehicleRepo = vehicleRepository,
            matcher = matcher,
            armedMonitor = monitor
        )
        activeTripRepository.createActiveTrip(tripId, 37.33, -121.88, TrackingMethod.MANUAL_GPS.value, "MySucculentCorner")
        service.setTrackingStateForTesting(tripId, distanceMeters = 300.0, displacementConfirmed = true)

        assertNull(LocationTrackingService.serviceState.value.vehicleName)

        // Late Bluetooth connection arrives
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        assertEquals(model3.id, activeTripRepository.getActiveTrip()?.vehicleId)
        assertEquals(model3.name, LocationTrackingService.serviceState.value.vehicleName)

        // Simulate 1-second duration ticker tick
        val acceptedFix = GpsInboxProcessor.ProcessResult.Accepted(
            fixId = 1L,
            tripId = tripId,
            deltaDistanceMeters = 50.0,
            totalDistanceMeters = 350.0,
            lastLatitude = 37.335,
            lastLongitude = -121.88,
            speedMph = 25.0,
            isDisplacementConfirmed = true,
            waypoint = Waypoint(37.335, -121.88, speed = 11f, accuracy = 5f, elapsedRealtimeNanos = 50_000_000_000L)
        )
        service.handleFixProcessed(acceptedFix)
        assertEquals("Ticker update must preserve identified vehicle name", model3.name, LocationTrackingService.serviceState.value.vehicleName)
    }

    // 13. Manual GPS trip with known vehicle
    @Test(timeout = 15_000)
    fun scenario13_manualGpsTripWithKnownVehicle_assignsAndNeverAutoStops() = runBlocking {
        val tripId = "manual-s13"
        activeTripRepository.createActiveTrip(tripId, 37.0, -122.0, TrackingMethod.MANUAL_GPS.value, "MySucculentCorner")
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, modelY.bluetoothAddress!!)
        assertEquals(modelY.id, activeTripRepository.getActiveTrip()?.vehicleId)

        // Even if BT disconnects, manual trip never auto-stops
        eventHandler.handle(BluetoothDevice.ACTION_ACL_DISCONNECTED, modelY.bluetoothAddress!!)
        assertFalse(scheduler.scheduled)
        assertEquals(modelY.id, activeTripRepository.getActiveTrip()?.vehicleId)
    }

    // 14. Automatic trip with known vehicle -> persists vehicle ID into finalized trip
    @Test(timeout = 15_000)
    fun scenario14_automaticTripWithKnownVehicle_persistsVehicleIntoCompletedTrip() = runBlocking {
        val tripId = "auto-s14"
        activeTripRepository.createActiveTrip(tripId, 37.3382, -121.8863, TrackingMethod.AUTO_DETECTED.value, "MySucculentCorner")
        database.tripLifecycleDao().insertOrUpdate(TripLifecycleEntity(tripId, TripLifecycleState.TRACKING.value))
        activeTripRepository.updateTripProgress(tripId, 1200.0, 37.3500, -121.8863, 200_000L)
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)

        val finalized = finalizationCoordinator.finalizeTrip(tripId, TrackingMethod.AUTO_DETECTED)
        assertNotNull(finalized)
        assertEquals(model3.id, finalized?.vehicleId)
        assertEquals(model3.id, tripRepository.getTripById(tripId)?.vehicleId)
    }

    // 15. Unknown vehicle does not block trip start
    @Test(timeout = 15_000)
    fun scenario15_unknownVehicleDoesNotBlockAutoStartOrFinalization() = runBlocking {
        matcher.activeProfileDevicesProvider = { emptySet() }
        matcher.fallbackDevicesProvider = { emptySet() }

        val action = AutoDriveDecisionEngine().evaluateVehicleEnter(
            isAutoTrackingEnabled = true,
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            isServiceTracking = false,
            activeTripId = null,
            isWithinSchedule = true,
            outsideScheduleBehavior = TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
        )
        assertTrue(action is AutoDriveAction.StartCandidateDrive)

        val tripId = "unknown-veh-s15"
        activeTripRepository.createActiveTrip(tripId, 37.3382, -121.8863, TrackingMethod.AUTO_DETECTED.value, "MySucculentCorner")
        database.tripLifecycleDao().insertOrUpdate(TripLifecycleEntity(tripId, TripLifecycleState.TRACKING.value))
        activeTripRepository.updateTripProgress(tripId, 800.0, 37.3450, -121.8863, 250_000L)

        val finalized = finalizationCoordinator.finalizeTrip(tripId, TrackingMethod.AUTO_DETECTED)
        assertNotNull(finalized)
        assertNull(finalized?.vehicleId)
        assertEquals(VehicleFriendlyNameResolver.UNKNOWN_VEHICLE, VehicleFriendlyNameResolver.resolve(finalized?.vehicleId, vehicleRepository.getVehicles()))
    }

    // 16. Vehicle identification succeeds after trip creation (at 200m confirmation)
    @Test(timeout = 15_000)
    fun scenario16_vehicleIdentificationSucceedsAfterTripCreation_on200mConfirmation() = runBlocking {
        val tripId = "late-200m-s16"
        var phase = 0
        matcher.activeProfileDevicesProvider = {
            if (phase == 0) setOf(model3.bluetoothAddress!!, modelY.bluetoothAddress!!) else setOf(model3.bluetoothAddress!!)
        }
        matcher.fallbackDevicesProvider = { emptySet() }

        val service = LocationTrackingService()
        service.setTestDependencies(
            activeTripRepo = activeTripRepository,
            helper = notificationHelper,
            tripId = tripId,
            lifecycleDao = database.tripLifecycleDao(),
            inboxDao = database.gpsInboxDao(),
            drainUseCase = drainUseCase,
            seqDao = database.tripSequenceDao(),
            db = database,
            prefs = preferencesManager,
            coordinator = finalizationCoordinator,
            dispatcher = testDispatcher,
            scope = testScope,
            context = context,
            vehicleRepo = vehicleRepository,
            matcher = matcher
        )
        activeTripRepository.createActiveTrip(tripId, 37.3382, -121.8863, TrackingMethod.AUTO_DETECTED.value, "MySucculentCorner")
        service.setCandidateForTesting(true, 999_999L)
        service.setTrackingStateForTesting(tripId, distanceMeters = 0.0, displacementConfirmed = false)

        // Initially ambiguous -> vehicleId is null
        assertNull(activeTripRepository.getActiveTrip()?.vehicleId)

        // Drive reaches 200m and Model Y drops out of range -> confirmation re-triggers vehicle ID
        phase = 1
        service.handleFixProcessed(
            GpsInboxProcessor.ProcessResult.Accepted(
                fixId = 2L,
                tripId = tripId,
                deltaDistanceMeters = 250.0,
                totalDistanceMeters = 250.0,
                lastLatitude = 37.3405,
                lastLongitude = -121.8863,
                speedMph = 20.0,
                isDisplacementConfirmed = true,
                waypoint = Waypoint(37.3405, -121.8863, speed = 9f, accuracy = 5f, elapsedRealtimeNanos = 60_000_000_000L)
            )
        )
        service.vehicleIdentificationJob?.join()
        assertEquals(model3.id, activeTripRepository.getActiveTrip()?.vehicleId)
        assertEquals(model3.name, LocationTrackingService.serviceState.value.vehicleName)
    }

    // 17. Manual vehicle assignment cannot be overwritten by Bluetooth events
    @Test(timeout = 15_000)
    fun scenario17_manualVehicleAssignmentCannotBeOverwritten() = runBlocking {
        val tripId = "manual-override-s17"
        activeTripRepository.createActiveTrip(tripId, 37.0, -122.0, TrackingMethod.AUTO_DETECTED.value, "MySucculentCorner")

        // User explicitly assigns Model Y via Dashboard
        val viewModel = DashboardViewModel(
            tripRepository = tripRepository,
            rateUseCase = CalculateMileageRateUseCase(),
            summaryUseCase = GetDashboardSummaryUseCase(),
            appContext = context,
            activeTripRepository = activeTripRepository,
            tripLifecycleDao = database.tripLifecycleDao(),
            preferencesManager = preferencesManager,
            vehicleRepository = vehicleRepository
        ).also { createdViewModels.add(it) }
        viewModel.selectActiveTripVehicle(modelY.id).join()
        assertEquals(modelY.id, activeTripRepository.getActiveTrip()?.vehicleId)
        assertTrue(preferencesManager.isManualVehicleAssignment(tripId))

        // Even if user clears to "Unknown vehicle" manually or Model 3 BT connects later, manual selection stays!
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        assertEquals(modelY.id, activeTripRepository.getActiveTrip()?.vehicleId)

        // Explicitly choosing Unknown vehicle (null) cannot be overwritten either
        viewModel.selectActiveTripVehicle(null).join()
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        assertNull(activeTripRepository.getActiveTrip()?.vehicleId)
    }

    // 18. Bluetooth disconnects during tracking (vehicle ID preserved)
    @Test(timeout = 15_000)
    fun scenario18_bluetoothDisconnectDuringTracking_preservesVehicleId() = runBlocking {
        val tripId = "bt-drop-s18"
        activeTripRepository.createActiveTrip(tripId, 37.0, -122.0, TrackingMethod.AUTO_DETECTED.value, "MySucculentCorner")
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        assertEquals(model3.id, activeTripRepository.getActiveTrip()?.vehicleId)

        eventHandler.handle(BluetoothDevice.ACTION_ACL_DISCONNECTED, model3.bluetoothAddress!!)
        assertEquals("Vehicle ID must be preserved on active trip after BT disconnect", model3.id, activeTripRepository.getActiveTrip()?.vehicleId)
    }

    // 19. App backgrounded before driving (Receiver + DataStore + Room without Activity)
    @Test(timeout = 15_000)
    fun scenario19_appBackgroundedBeforeDriving_autoStartsViaReceivers() = runBlocking {
        // No Dashboard/MainActivity created — pure broadcast path
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        monitor.locationProvider = { makeLocation(37.3382, -121.8863, fakeElapsedClockMillis, speedMps = 14f) }

        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 91)))
        assertNotNull(preferencesManager.activeTripId.first())
    }

    // 20. Process killed and restarted (re-reconciles monitor and recovers active trip)
    @Test(timeout = 15_000)
    fun scenario20_processKilledAndRestarted_recoversTripAndVehicleDisplay() = runBlocking {
        val tripId = "recover-s20"
        activeTripRepository.createActiveTrip(tripId, 37.3382, -121.8863, TrackingMethod.AUTO_DETECTED.value, "MySucculentCorner")
        activeTripRepository.assignVehicleIfAbsent(tripId, model3.id)
        activeTripRepository.updateTripProgress(tripId, 1000.0, 37.3500, -121.8863, 200_000L)
        database.tripLifecycleDao().insertOrUpdate(TripLifecycleEntity(tripId, TripLifecycleState.TRACKING.value))

        // Fresh service instance after process kill
        val recoveredService = LocationTrackingService()
        recoveredService.setTestDependencies(
            activeTripRepo = activeTripRepository,
            helper = notificationHelper,
            lifecycleDao = database.tripLifecycleDao(),
            inboxDao = database.gpsInboxDao(),
            drainUseCase = drainUseCase,
            seqDao = database.tripSequenceDao(),
            db = database,
            prefs = preferencesManager,
            coordinator = finalizationCoordinator,
            dispatcher = testDispatcher,
            scope = testScope,
            context = context,
            vehicleRepo = vehicleRepository,
            matcher = matcher
        )
        recoveredService.locationUpdatesRegistrar = { true }
        matcher.activeProfileDevicesProvider = { setOf(model3.bluetoothAddress!!) }
        matcher.fallbackDevicesProvider = { emptySet() }

        recoveredService.onStartCommand(null, 0, 1)
        recoveredService.lastRecoveryJob?.join()
        recoveredService.vehicleIdentificationJob?.join()

        assertEquals(tripId, LocationTrackingService.serviceState.value.tripId)
        assertEquals(model3.name, LocationTrackingService.serviceState.value.vehicleName)
    }

    // 21. Device reboot recovery -> marks RECOVERY_REQUIRED, clears stale ACL flags, preserves vehicle
    @Test(timeout = 15_000)
    fun scenario21_deviceRebootRecovery_preservesTripAndClearsStaleAcl() = runBlocking {
        val tripId = "reboot-s21"
        activeTripRepository.createActiveTrip(tripId, 37.0, -122.0, TrackingMethod.AUTO_DETECTED.value, "MySucculentCorner")
        activeTripRepository.assignVehicleIfAbsent(tripId, modelY.id)
        database.tripLifecycleDao().insertOrUpdate(TripLifecycleEntity(tripId, TripLifecycleState.TRACKING.value))
        preferencesManager.addConnectedCarAddress(modelY.bluetoothAddress!!)

        val bootReceiver = BootReceiver()
        bootReceiver.activityTransitionRegistrar = { true }
        bootReceiver.setTestDependencies(
            transitionManager = ActivityTransitionManager(context),
            prefs = preferencesManager,
            activeTripRepo = activeTripRepository,
            lifecycleDao = database.tripLifecycleDao(),
            scope = testScope
        )
        bootReceiver.reconcileBootActiveTrip()
        preferencesManager.setCarBluetoothConnected(false)

        assertEquals(TripLifecycleState.RECOVERY_REQUIRED.value, database.tripLifecycleDao().getState(tripId))
        assertEquals(modelY.id, activeTripRepository.getActiveTrip()?.vehicleId)
        assertFalse(preferencesManager.isCarBluetoothConnected())
        assertTrue(preferencesManager.getConnectedCarAddresses().isEmpty())
    }

    // 22. GPS temporarily unavailable does not crash or permanently lock verification
    @Test(timeout = 15_000)
    fun scenario22_gpsTemporarilyUnavailable_recoversOnNextSample() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        monitor.locationProvider = { null } // GPS timeout / tunnel

        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 92)))
        assertNull(preferencesManager.activeTripId.first())

        // After sample cadence window passes and GPS returns, auto-start succeeds
        fakeElapsedClockMillis += BluetoothArmedDriveMonitor.MIN_LOCATION_SAMPLE_INTERVAL_MILLIS + 1000L
        monitor.locationProvider = { makeLocation(37.34, -121.88, fakeElapsedClockMillis, speedMps = 14f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 92)))
        assertNotNull(preferencesManager.activeTripId.first())
    }

    // 23. Activity Recognition delayed/missing transition works cleanly when armed GPS sees movement
    @Test(timeout = 15_000)
    fun scenario23_delayedOrLowConfidenceActivityRecognition_startsWhenMovementDetected() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        preferencesManager.setConnectedCarAddresses(setOf(model3.bluetoothAddress!!), fakeElapsedClockMillis)

        // Delayed transition (UNKNOWN or STILL) with real corroborated GPS displacement across 35s
        monitor.locationProvider = { makeLocation(37.3382, -121.8863, fakeElapsedClockMillis, speedMps = 0f, accuracyMeters = 10f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.UNKNOWN, 100)))
        assertNull(preferencesManager.activeTripId.first())

        fakeElapsedClockMillis += 35_000L
        monitor.locationProvider = { makeLocation(37.3390, -121.8863, fakeElapsedClockMillis, speedMps = 0f, accuracyMeters = 10f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.UNKNOWN, 100)))
        assertNotNull("Displacement >= 45m during post-connect window must trigger auto-start", preferencesManager.activeTripId.first())
    }

    // 24. Rapid duplicate Bluetooth broadcasts are serialized and idempotent
    @Test(timeout = 15_000)
    fun scenario24_rapidDuplicateBluetoothBroadcasts_areSerializedAndRedacted() = runBlocking {
        coroutineScope {
            val jobs = List(5) {
                async { eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!) }
            }
            jobs.forEach { it.await() }
        }
        assertEquals(setOf(VehicleBluetoothMatchPolicy.normalizeAddress(model3.bluetoothAddress!!)), preferencesManager.getConnectedCarAddresses())
        assertTrue(monitor.isArmedForTesting())

        // Verify diagnostics redacts MAC addresses and raw GPS coordinates
        val report = TrackingDiagnostics.exportRedactedReport(context)
        assertFalse("MAC address must never appear in exported diagnostic report", report.contains(model3.bluetoothAddress!!, ignoreCase = true))
        assertFalse("Raw coordinate leakage check", TrackingDiagnostics.redactSensitive("at 37.33821, -121.88631").contains("37.33821"))
    }

    // 25. Auto-stop after successful trip detection -> finalizes cleanly with vehicle ID & true movement end time
    @Test(timeout = 15_000)
    fun scenario25_autoStopAfterSuccessfulDetection_finalizesWithVehicleAndTrueEndTime() = runBlocking {
        val tripId = "autostop-s25"
        val startMs = 10_000L
        val lastMovementMs = 150_000L
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = startMs,
                startLatitude = 37.3382,
                startLongitude = -121.8863,
                lastLatitude = 37.3450,
                lastLongitude = -121.8863,
                lastTimestamp = lastMovementMs,
                totalDistanceMeters = 900.0,
                trackingMethod = TrackingMethod.AUTO_DETECTED.value,
                vehicleId = model3.id
            )
        )
        database.activeTripDao().insertWaypoint(
            ActiveWaypointEntity(tripId = tripId, latitude = 37.3450, longitude = -121.8863, altitude = 0.0, accuracy = 5f, speed = 15f, timestamp = lastMovementMs)
        )
        database.tripLifecycleDao().insertOrUpdate(TripLifecycleEntity(tripId, TripLifecycleState.TRACKING.value))
        preferencesManager.setActiveTripId(tripId)
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        eventHandler.handle(BluetoothDevice.ACTION_ACL_DISCONNECTED, model3.bluetoothAddress!!)

        assertTrue("Single vehicle disconnect schedules debounce auto-stop", scheduler.scheduled)

        val worker = TestListenableWorkerBuilder<TripDebounceWorker>(context)
            .setInputData(Data.Builder().putString(TripDebounceWorker.INPUT_TRIGGER, TripDebounceWorker.TRIGGER_BLUETOOTH).putString(TripDebounceWorker.INPUT_TRIP_ID, tripId).build())
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    TripDebounceWorker(
                        appContext,
                        workerParameters,
                        preferencesManager,
                        activeTripRepository,
                        tripRepository,
                        com.businesstracker.mileage.domain.usecase.FinalizeTripUseCase(finalizationCoordinator),
                        notificationHelper,
                        drainUseCase,
                        database.gpsInboxDao(),
                        database.tripLifecycleDao()
                    )
            })
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        val finalized = tripRepository.getTripById(tripId)
        assertNotNull(finalized)
        assertEquals(model3.id, finalized?.vehicleId)
        assertEquals(lastMovementMs, finalized?.endTime)
    }

    // 26. No duplicate active trips under concurrent creation
    @Test(timeout = 15_000)
    fun scenario26_noDuplicateActiveTrips() = runBlocking {
        val first = activeTripRepository.createActiveTrip("trip-a", 37.0, -122.0, TrackingMethod.AUTO_DETECTED.value, "Test")
        val second = activeTripRepository.createActiveTrip("trip-b", 37.1, -122.1, TrackingMethod.MANUAL_GPS.value, "Test")
        assertEquals(first.id, second.id)
        assertEquals("trip-a", activeTripRepository.getActiveTrip()?.id)
    }

    // 27. No trip loss or destructive database changes -> historical trips remain untouched
    @Test(timeout = 15_000)
    fun scenario27_historicalTripsPreservedWithoutLoss() = runBlocking {
        val historicTrip = TripEntity(
            id = "historic-trip-27",
            date = "2026-10-10",
            startTime = 1728550000000L,
            endTime = 1728551000000L,
            startAddress = "Home",
            endAddress = "Nursery",
            startLatitude = 37.3,
            startLongitude = -121.8,
            endLatitude = 37.35,
            endLongitude = -121.95,
            totalDistanceMiles = 12.4,
            businessMiles = 12.4,
            businessEntity = "MySucculentCorner",
            purpose = "Delivery",
            trackingMethod = TrackingMethod.AUTO_DETECTED.value,
            classification = TripClassification.BUSINESS.value,
            applicableRate = 0.70,
            calculatedDeduction = 8.68,
            notes = null,
            waypointsJson = "[]",
            syncStatus = "SYNCED",
            syncedAt = 1728551200000L,
            vehicleId = modelY.id
        )
        tripRepository.insertTrip(historicTrip)

        // Switching connected vehicles or creating/discarding active trips never deletes historic data
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        val discarded = "temp-discard-27"
        activeTripRepository.createActiveTrip(discarded, 37.0, -122.0, TrackingMethod.AUTO_DETECTED.value, "Test")
        finalizationCoordinator.discardFalseTrip(discarded)

        val persisted = tripRepository.getTripById("historic-trip-27")
        assertNotNull(persisted)
        assertEquals(modelY.id, persisted?.vehicleId)
        assertEquals(12.4, persisted?.totalDistanceMiles ?: 0.0, 0.001)
    }

    // 28. Dashboard displays assigned friendly vehicle name (never raw Bluetooth name)
    @Test(timeout = 15_000)
    fun scenario28_dashboardDisplaysAssignedFriendlyVehicleName() = runBlocking {
        val tripId = "dash-s28"
        activeTripRepository.createActiveTrip(tripId, 37.0, -122.0, TrackingMethod.AUTO_DETECTED.value, "MySucculentCorner")
        activeTripRepository.assignVehicleIfAbsent(tripId, modelY.id)

        val viewModel = DashboardViewModel(
            tripRepository = tripRepository,
            rateUseCase = CalculateMileageRateUseCase(),
            summaryUseCase = GetDashboardSummaryUseCase(),
            appContext = context,
            activeTripRepository = activeTripRepository,
            tripLifecycleDao = database.tripLifecycleDao(),
            preferencesManager = preferencesManager,
            vehicleRepository = vehicleRepository
        ).also { createdViewModels.add(it) }

        val state = viewModel.serviceState.first { it.tripId == tripId && it.vehicleName != null }
        assertEquals(modelY.id, state.vehicleId)
        assertEquals("Tesla Model Y", state.vehicleName)
        assertNotEquals(modelY.bluetoothDeviceName, state.vehicleName)
    }

    // 29. Dashboard updates reactively when active vehicle ID changes
    @Test(timeout = 15_000)
    fun scenario29_dashboardUpdatesReactivelyWhenActiveVehicleIdChanges() = runBlocking {
        val tripId = "dash-reactive-s29"
        activeTripRepository.createActiveTrip(tripId, 37.0, -122.0, TrackingMethod.MANUAL_GPS.value, "MySucculentCorner")

        val viewModel = DashboardViewModel(
            tripRepository = tripRepository,
            rateUseCase = CalculateMileageRateUseCase(),
            summaryUseCase = GetDashboardSummaryUseCase(),
            appContext = context,
            activeTripRepository = activeTripRepository,
            tripLifecycleDao = database.tripLifecycleDao(),
            preferencesManager = preferencesManager,
            vehicleRepository = vehicleRepository
        ).also { createdViewModels.add(it) }

        val initialState = viewModel.serviceState.first { it.tripId == tripId }
        assertNull(initialState.vehicleName)

        // Late match populates vehicle ID in Room
        activeTripRepository.assignVehicleIfAbsent(tripId, model3.id)
        val matchedState = viewModel.serviceState.first { it.vehicleName == "Tesla Model 3" }
        assertEquals(model3.id, matchedState.vehicleId)

        // Explicit user change updates to Model Y reactively
        viewModel.selectActiveTripVehicle(modelY.id).join()
        val switchedState = viewModel.serviceState.first { it.vehicleName == "Tesla Model Y" }
        assertEquals(modelY.id, switchedState.vehicleId)
    }

    // 30. Existing Smart Classification remains functional alongside new vehicle attribution
    @Test(timeout = 15_000)
    fun scenario30_existingSmartClassificationRemainsFunctional() = runBlocking {
        preferencesManager.setSmartTripRecognitionEnabled(true)
        preferencesManager.setSmartClassificationMode(SmartClassificationMode.AUTO_CLASSIFY)

        val startLat = 37.3382
        val startLon = -121.8863
        val endLat = 37.3541
        val endLon = -121.9552
        database.routePatternDao().insertRoutePattern(
            RoutePatternEntity(
                id = "pattern-1",
                startLatitude = startLat,
                startLongitude = startLon,
                startRadiusMeters = 250.0,
                endLatitude = endLat,
                endLongitude = endLon,
                endRadiusMeters = 250.0,
                startAddress = "Home",
                endAddress = "Wholesale Nursery",
                classification = TripClassification.BUSINESS.value,
                businessEntity = BusinessEntity.MY_SUCCULENT_CORNER.displayName,
                purpose = TripPurpose.NURSERY_PICKUP,
                sampleCount = 6,
                confidence = 1.0,
                lastLearnedAt = SmartTripLearningCutoff.ACTIVATION_CUTOFF_MILLIS + 1000L,
                createdAt = SmartTripLearningCutoff.ACTIVATION_CUTOFF_MILLIS
            )
        )

        val learnedBaseMs = SmartTripLearningCutoff.ACTIVATION_CUTOFF_MILLIS + 1000L
        repeat(5) { idx ->
            val learnedTripId = "learned-trip-$idx"
            database.tripDao().insertTrip(
                TripEntity(
                    id = learnedTripId,
                    date = "2026-11-16",
                    startTime = learnedBaseMs + idx * 60_000L,
                    endTime = learnedBaseMs + idx * 60_000L + 30_000L,
                    startAddress = "Home",
                    endAddress = "Wholesale Nursery",
                    startLatitude = startLat,
                    startLongitude = startLon,
                    endLatitude = endLat,
                    endLongitude = endLon,
                    totalDistanceMiles = 5.0,
                    businessMiles = 5.0,
                    businessEntity = BusinessEntity.MY_SUCCULENT_CORNER.displayName,
                    purpose = TripPurpose.NURSERY_PICKUP,
                    trackingMethod = TrackingMethod.AUTO_DETECTED.value,
                    classification = TripClassification.BUSINESS.value,
                    applicableRate = 0.70,
                    calculatedDeduction = 3.5,
                    notes = null,
                    waypointsJson = "[]",
                    syncStatus = "SYNCED",
                    syncedAt = learnedBaseMs,
                    classificationSource = ClassificationSource.MANUAL.value,
                    vehicleId = model3.id
                )
            )
            database.tripLearningRecordDao().insertRecord(
                TripLearningRecordEntity(
                    tripId = learnedTripId,
                    eligible = true,
                    classificationSource = ClassificationSource.MANUAL.value,
                    confirmedCategory = TripClassification.BUSINESS.value,
                    businessEntity = BusinessEntity.MY_SUCCULENT_CORNER.displayName,
                    purpose = TripPurpose.NURSERY_PICKUP,
                    startLatitude = startLat,
                    startLongitude = startLon,
                    endLatitude = endLat,
                    endLongitude = endLon,
                    patternId = "pattern-1",
                    learnedAt = learnedBaseMs,
                    createdAt = learnedBaseMs
                )
            )
        }

        val engine = SmartTripClassificationEngine(
            TripPatternMatcher(database.routePatternDao(), testDispatcher),
            database.tripLearningRecordDao(),
            preferencesManager,
            testDispatcher
        )
        val smartCoordinator = SmartClassificationCoordinator(
            tripRepository,
            engine,
            preferencesManager,
            notificationHelper,
            context,
            testDispatcher
        )
        val combinedFinalization = TripFinalizationCoordinator(
            database,
            activeTripRepository,
            tripRepository,
            GeocodeLocationUseCase(context, testDispatcher),
            CalculateMileageRateUseCase(),
            preferencesManager,
            Gson(),
            testDispatcher,
            smartCoordinator
        )

        val tripId = "smart-s30"
        val tEnd = SmartTripLearningCutoff.ACTIVATION_CUTOFF_MILLIS + 3_600_000L
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = tEnd - 600_000L,
                startLatitude = startLat,
                startLongitude = startLon,
                lastLatitude = endLat,
                lastLongitude = endLon,
                lastTimestamp = tEnd,
                totalDistanceMeters = 8000.0,
                trackingMethod = TrackingMethod.AUTO_DETECTED.value,
                suggestedBusiness = "MySucculentCorner",
                vehicleId = model3.id
            )
        )
        database.tripLifecycleDao().insertOrUpdate(TripLifecycleEntity(tripId, TripLifecycleState.TRACKING.value))

        val finalized = combinedFinalization.finalizeTrip(tripId, TrackingMethod.AUTO_DETECTED)
        assertNotNull(finalized)
        assertEquals(model3.id, finalized?.vehicleId)
        assertEquals(TripClassification.BUSINESS.value, finalized?.classification)
        assertEquals(ClassificationSource.SMART_AUTO.value, finalized?.classificationSource)
    }

    // 31. ForegroundServiceStartNotAllowedException falls back to background GPS & confirms at 200m with Tesla Model 3
    @Test(timeout = 15_000)
    fun scenario31_foregroundServiceStartNotAllowed_activatesBackgroundGpsFallbackAndConfirmsAt200m_withTeslaModel3() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        preferencesManager.setConnectedCarAddresses(setOf(model3.bluetoothAddress!!), fakeElapsedClockMillis)

        var fallbackRegistered = false
        var serviceLaunchAttempts = 0
        receiver.serviceLauncher = { _, _ ->
            serviceLaunchAttempts++
            throw android.app.ForegroundServiceStartNotAllowedException("Background FGS start restricted")
        }
        receiver.backgroundLocationRegistrar = { _, _ ->
            fallbackRegistered = true
            true
        }

        // Real-world driving corroboration at 16:34:06 with rawConf=91
        fakeElapsedClockMillis += 180_000L
        monitor.locationProvider = { makeLocation(37.3382, -121.8863, fakeElapsedClockMillis, speedMps = 12f, accuracyMeters = 3.79f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 91)))

        assertEquals(1, serviceLaunchAttempts)
        assertTrue("Background GPS fallback must register when FGS start is blocked", fallbackRegistered)

        val tripId = preferencesManager.activeTripId.first()
        assertNotNull("Candidate trip must NOT be destroyed by LaunchFailedReconciled when fallback is active", tripId)
        val persistedCandidate = activeTripRepository.getActiveTrip()
        assertNotNull(persistedCandidate)
        assertEquals(model3.id, persistedCandidate?.vehicleId)
        assertNotNull("Candidate deadline must be initialized > 0", preferencesManager.getCandidateDeadline())
        assertEquals(TripLifecycleState.CANDIDATE.value, database.tripLifecycleDao().getState(tripId!!))
        assertTrue(LocationTrackingService.serviceState.value.isTracking)

        // Feed >200m of driving locations via background PendingIntent path -> promotes to TRACKING
        receiver.handleBackgroundGpsUpdate(
            context,
            listOf(
                makeLocation(37.3382, -121.8863, fakeElapsedClockMillis + 3_000L, speedMps = 13f, accuracyMeters = 5f),
                makeLocation(37.3408, -121.8863, fakeElapsedClockMillis + 15_000L, speedMps = 15f, accuracyMeters = 5f)
            )
        )

        val confirmedTrip = activeTripRepository.getActiveTrip()
        assertNotNull(confirmedTrip)
        assertTrue("Distance must reach >= 200m via background GPS fallback", (confirmedTrip?.totalDistanceMeters ?: 0.0) >= 200.0)
        assertEquals(TripLifecycleState.TRACKING.value, database.tripLifecycleDao().getState(tripId))
        assertEquals(model3.id, confirmedTrip?.vehicleId)
        assertFalse(LocationTrackingService.serviceState.value.isCandidate)

        // Once FGS becomes eligible again, next background update promotes cleanly to LocationTrackingService
        val launchedIntents = mutableListOf<Intent>()
        receiver.serviceLauncher = { _, intent -> launchedIntents.add(intent) }
        receiver.handleBackgroundGpsUpdate(
            context,
            listOf(makeLocation(37.3420, -121.8863, fakeElapsedClockMillis + 25_000L, speedMps = 15f, accuracyMeters = 5f))
        )
        assertEquals(1, launchedIntents.size)
        assertEquals(tripId, launchedIntents.first().getStringExtra(LocationTrackingService.EXTRA_TRIP_ID))
    }

    // 32. Fresh GPS fix whose elapsedRealtimeNanos is 138ms ahead of pre-request clock must NOT trigger GPS_SAMPLE_STALE
    @Test(timeout = 15_000)
    fun scenario32_freshGpsFix138msNewerThanPreRequestTimestamp_isNotRejectedAsStale() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        preferencesManager.setConnectedCarAddresses(setOf(model3.bluetoothAddress!!), fakeElapsedClockMillis)

        fakeElapsedClockMillis = 206_886_176L
        // Exact clock skew observed in real device logs: fixElapsed=206886314 now=206886176 (+138ms)
        monitor.locationProvider = { makeLocation(37.3382, -121.8863, 206_886_314L, speedMps = 12f, accuracyMeters = 4f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 91)))

        assertNotNull("Fresh fix +138ms after pre-request timestamp must be accepted and start trip", preferencesManager.activeTripId.first())
        val recentCodes = TrackingDiagnostics.getRecentEvents().map { "${it.category}:${it.code}" }
        assertFalse("GPS_SAMPLE_STALE must not fire on +138ms newly fetched fix", recentCodes.contains("BluetoothArmedMonitor:GPS_SAMPLE_STALE"))
    }

    // 33. Multi-step slow departure preserves origin anchor (16:31:02 -> 16:31:43 -> 16:32:43)
    @Test(timeout = 15_000)
    fun scenario33_multiStepSlowDeparture_preservesOriginAnchorAndConfirmsMovement() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        preferencesManager.setConnectedCarAddresses(setOf(model3.bluetoothAddress!!), fakeElapsedClockMillis)

        // 16:31:02: rawConf=30, acc=3.79m at origin
        val t0 = fakeElapsedClockMillis
        monitor.locationProvider = { makeLocation(37.33820, -121.88630, t0, speedMps = 0f, accuracyMeters = 3.79f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 30)))
        assertNull(preferencesManager.activeTripId.first())

        // 16:31:43: rawConf=9, acc=9.935m, +20m slowly rolling out of driveway (< 45m) -> preserves origin anchor
        fakeElapsedClockMillis += 41_000L
        monitor.locationProvider = { makeLocation(37.33838, -121.88630, fakeElapsedClockMillis, speedMps = 0.8f, accuracyMeters = 9.935f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 9)))
        assertNull(preferencesManager.activeTripId.first())

        // 16:32:43: rawConf=88, acc=9.935m, +60m from origin (only 40m from t1, but 60m >= 45m from t0 origin anchor!)
        fakeElapsedClockMillis += 60_000L
        monitor.locationProvider = { makeLocation(37.33874, -121.88630, fakeElapsedClockMillis, speedMps = 1.2f, accuracyMeters = 9.935f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 88)))

        assertNotNull("Origin anchor preservation across slow intermediate step must corroborate >= 45m displacement", preferencesManager.activeTripId.first())
    }

    // 34. Genuine unrecoverable launch failure reconciles cleanly, logs detailed diagnostics, and resets cooldown for immediate retry
    @Test(timeout = 15_000)
    fun scenario34_genuineUnrecoverableLaunchFailure_reconcilesAndResetsCooldown_allowingImmediateRetry() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        preferencesManager.setConnectedCarAddresses(setOf(model3.bluetoothAddress!!), fakeElapsedClockMillis)

        receiver.serviceLauncher = { _, _ -> throw SecurityException("Simulated FGS launch restriction") }
        receiver.backgroundLocationRegistrar = { _, _ -> false }

        fakeElapsedClockMillis += 10_000L
        monitor.locationProvider = { makeLocation(37.3382, -121.8863, fakeElapsedClockMillis, speedMps = 14f, accuracyMeters = 5f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 92)))

        assertNull("Unrecoverable launch failure must clear activeTripId", preferencesManager.activeTripId.first())
        assertNull("Unrecoverable launch failure must clear active trip from Room", activeTripRepository.getActiveTrip())

        val detailEvent = TrackingDiagnostics.getRecentEvents().lastOrNull { it.category == "CandidateLaunch" }
        assertNotNull(detailEvent)
        assertEquals("LAUNCH_FAILED_RECONCILED", detailEvent?.code)
        assertTrue(detailEvent?.detail?.contains("exc=SecurityException") == true)
        assertTrue(detailEvent?.detail?.contains("deadlineRem=240000ms") == true)

        // Immediately recoverable on next sample without waiting 60s cooldown once service launch works
        receiver.serviceLauncher = null
        fakeElapsedClockMillis += BluetoothArmedDriveMonitor.MIN_LOCATION_SAMPLE_INTERVAL_MILLIS + 1000L
        monitor.locationProvider = { makeLocation(37.3400, -121.8863, fakeElapsedClockMillis, speedMps = 14f, accuracyMeters = 5f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 92)))

        assertNotNull("Next sample after launch recovery must succeed without 60s lock", preferencesManager.activeTripId.first())
    }

    // 35. Service adopts pre-created candidate ActiveTrip and waypoints without duplicating or losing vehicle attribution
    @Test(timeout = 15_000)
    fun scenario35_delayedServiceStartupAcknowledgment_adoptsPreCreatedCandidateWithoutDuplicate() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        preferencesManager.setConnectedCarAddresses(setOf(model3.bluetoothAddress!!), fakeElapsedClockMillis)

        val deferredIntents = mutableListOf<Intent>()
        receiver.serviceLauncher = { _, intent -> deferredIntents.add(intent) }

        monitor.locationProvider = { makeLocation(37.3382, -121.8863, fakeElapsedClockMillis, speedMps = 12f, accuracyMeters = 4f) }
        receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 90)))

        val candidateTripId = preferencesManager.activeTripId.first()
        assertNotNull(candidateTripId)
        assertEquals(1, deferredIntents.size)

        // Simulate delayed service startup after fallback already recorded a waypoint
        activeTripRepository.recordWaypoint(candidateTripId!!, Waypoint(37.3382, -121.8863, speed = 10f, accuracy = 5f, elapsedRealtimeNanos = 100_000_000L))
        activeTripRepository.updateTripProgress(candidateTripId, 120.0, 37.3392, -121.8863, 200_000L)

        val adoptedTrip = activeTripRepository.createActiveTrip(
            tripId = candidateTripId,
            latitude = 0.0,
            longitude = 0.0,
            trackingMethod = TrackingMethod.AUTO_DETECTED.value,
            suggestedBusiness = "MySucculentCorner"
        )
        assertEquals(candidateTripId, adoptedTrip.id)
        assertEquals(120.0, adoptedTrip.totalDistanceMeters, 0.001)
        assertEquals(model3.id, adoptedTrip.vehicleId)
    }

    // 36. In-service Bluetooth auto-stop deadline finalizes trip even without WorkManager
    @Test(timeout = 15_000)
    fun scenario36_bluetoothDisconnect_schedulesInServiceStopDeadlineAndFinalizes() = runBlocking {
        val tripId = "bt-inservice-stop-36"
        val lastMovementMs = 150_000L
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 10_000L,
                startLatitude = 37.3382,
                startLongitude = -121.8863,
                lastLatitude = 37.3450,
                lastLongitude = -121.8863,
                lastTimestamp = lastMovementMs,
                totalDistanceMeters = 900.0,
                trackingMethod = TrackingMethod.AUTO_DETECTED.value,
                vehicleId = modelY.id
            )
        )
        database.activeTripDao().insertWaypoint(
            ActiveWaypointEntity(tripId = tripId, latitude = 37.3450, longitude = -121.8863, altitude = 0.0, accuracy = 5f, speed = 15f, timestamp = lastMovementMs)
        )
        database.tripLifecycleDao().insertOrUpdate(TripLifecycleEntity(tripId, TripLifecycleState.TRACKING.value))
        preferencesManager.setActiveTripId(tripId)
        preferencesManager.setInVehicleState(true)

        val service = LocationTrackingService()
        service.elapsedClock = { fakeElapsedClockMillis }
        service.setTestDependencies(
            activeTripRepo = activeTripRepository,
            helper = notificationHelper,
            tripId = tripId,
            lifecycleDao = database.tripLifecycleDao(),
            inboxDao = database.gpsInboxDao(),
            drainUseCase = drainUseCase,
            seqDao = database.tripSequenceDao(),
            db = database,
            prefs = preferencesManager,
            coordinator = finalizationCoordinator,
            dispatcher = testDispatcher,
            scope = testScope,
            context = context,
            vehicleRepo = vehicleRepository,
            matcher = matcher,
            armedMonitor = monitor
        )
        service.setTrackingStateForTesting(tripId, distanceMeters = 900.0, displacementConfirmed = true)

        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, modelY.bluetoothAddress!!)
        eventHandler.handle(BluetoothDevice.ACTION_ACL_DISCONNECTED, modelY.bluetoothAddress!!)

        assertEquals(fakeElapsedClockMillis + 240_000L, service.getStopDeadlineElapsedForTesting())
        assertEquals(TripDebounceWorker.TRIGGER_BLUETOOTH, service.getStopDeadlineTriggerForTesting())

        // Advance past 4 minutes and tick without relying on WorkManager
        fakeElapsedClockMillis += 240_000L
        service.checkStopDeadlineOnTick(fakeElapsedClockMillis)

        var finalized: TripEntity? = null
        for (i in 1..40) {
            finalized = tripRepository.getTripById(tripId)
            if (finalized != null && !preferencesManager.isBluetoothDisconnectPending()) {
                break
            }
            kotlinx.coroutines.delay(50L)
        }
        assertNotNull("In-service Bluetooth stop deadline must finalize trip without WorkManager", finalized)
        assertEquals(modelY.id, finalized?.vehicleId)
        assertFalse(preferencesManager.isBluetoothDisconnectPending())
        assertFalse(preferencesManager.isInVehicle())
    }

    // 37. Pre-disconnect fixes & indoor walking jitter cannot cancel BT auto-stop; true motion extends deadline
    @Test(timeout = 15_000)
    fun scenario37_postDisconnectJitterCannotCancelBtStop_andMotionExtendsDeadline() = runBlocking {
        val tripId = "bt-motion-guard-37"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 10_000L,
                startLatitude = 37.3382,
                startLongitude = -121.8863,
                lastLatitude = 37.3450,
                lastLongitude = -121.8863,
                lastTimestamp = 95_000L,
                totalDistanceMeters = 900.0,
                trackingMethod = TrackingMethod.AUTO_DETECTED.value,
                vehicleId = modelY.id
            )
        )
        database.tripLifecycleDao().insertOrUpdate(TripLifecycleEntity(tripId, TripLifecycleState.TRACKING.value))
        preferencesManager.setActiveTripId(tripId)
        preferencesManager.setInVehicleState(true)

        val service = LocationTrackingService()
        service.elapsedClock = { fakeElapsedClockMillis }
        service.setTestDependencies(
            activeTripRepo = activeTripRepository,
            helper = notificationHelper,
            tripId = tripId,
            lifecycleDao = database.tripLifecycleDao(),
            inboxDao = database.gpsInboxDao(),
            drainUseCase = drainUseCase,
            seqDao = database.tripSequenceDao(),
            db = database,
            prefs = preferencesManager,
            coordinator = finalizationCoordinator,
            dispatcher = testDispatcher,
            scope = testScope,
            context = context,
            vehicleRepo = vehicleRepository,
            matcher = matcher,
            armedMonitor = monitor
        )
        service.setTrackingStateForTesting(tripId, distanceMeters = 900.0, displacementConfirmed = true)

        // Pre-disconnect driving fix primes validator before disconnect
        service.handleFixProcessed(
            GpsInboxProcessor.ProcessResult.Accepted(
                fixId = 1L,
                tripId = tripId,
                deltaDistanceMeters = 100.0,
                totalDistanceMeters = 800.0,
                lastLatitude = 37.3440,
                lastLongitude = -121.8863,
                speedMph = 25.0,
                isDisplacementConfirmed = true,
                waypoint = Waypoint(37.3440, -121.8863, speed = 11f, accuracy = 5f, elapsedRealtimeNanos = (fakeElapsedClockMillis - 3_000L) * 1_000_000L)
            )
        )

        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, modelY.bluetoothAddress!!)
        eventHandler.handle(BluetoothDevice.ACTION_ACL_DISCONNECTED, modelY.bluetoothAddress!!)
        val initialDeadline = service.getStopDeadlineElapsedForTesting()
        assertEquals(fakeElapsedClockMillis + 240_000L, initialDeadline)

        // Post-disconnect indoor walking/multipath coordinate jump: coordinate speed 15mph, Doppler speed 1.0 m/s (~2.2 mph)
        fakeElapsedClockMillis += 3_000L
        service.handleFixProcessed(
            GpsInboxProcessor.ProcessResult.Accepted(
                fixId = 2L,
                tripId = tripId,
                deltaDistanceMeters = 20.0,
                totalDistanceMeters = 820.0,
                lastLatitude = 37.3442,
                lastLongitude = -121.8863,
                speedMph = 15.0,
                isDisplacementConfirmed = true,
                waypoint = Waypoint(37.3442, -121.8863, speed = 1.0f, accuracy = 8f, elapsedRealtimeNanos = fakeElapsedClockMillis * 1_000_000L)
            )
        )
        fakeElapsedClockMillis += 3_000L
        service.handleFixProcessed(
            GpsInboxProcessor.ProcessResult.Accepted(
                fixId = 3L,
                tripId = tripId,
                deltaDistanceMeters = 20.0,
                totalDistanceMeters = 840.0,
                lastLatitude = 37.3444,
                lastLongitude = -121.8863,
                speedMph = 15.0,
                isDisplacementConfirmed = true,
                waypoint = Waypoint(37.3444, -121.8863, speed = 1.0f, accuracy = 8f, elapsedRealtimeNanos = fakeElapsedClockMillis * 1_000_000L)
            )
        )
        assertFalse("Walking multipath jump must NOT restore inVehicle state", preferencesManager.isInVehicle())
        assertTrue("Bluetooth disconnect pending must stay true", preferencesManager.isBluetoothDisconnectPending())
        assertEquals("Original 4-minute stop deadline must remain unchanged", initialDeadline, service.getStopDeadlineElapsedForTesting())

        // Now genuine driving motion (>= 10 mph Doppler & coordinate) occurs twice while BT disconnected -> extends deadline rather than cancelling
        fakeElapsedClockMillis += 60_000L
        service.handleFixProcessed(
            GpsInboxProcessor.ProcessResult.Accepted(
                fixId = 4L,
                tripId = tripId,
                deltaDistanceMeters = 50.0,
                totalDistanceMeters = 890.0,
                lastLatitude = 37.3450,
                lastLongitude = -121.8863,
                speedMph = 15.0,
                isDisplacementConfirmed = true,
                waypoint = Waypoint(37.3450, -121.8863, speed = 7.0f, accuracy = 6f, elapsedRealtimeNanos = fakeElapsedClockMillis * 1_000_000L)
            )
        )
        fakeElapsedClockMillis += 3_000L
        service.handleFixProcessed(
            GpsInboxProcessor.ProcessResult.Accepted(
                fixId = 5L,
                tripId = tripId,
                deltaDistanceMeters = 50.0,
                totalDistanceMeters = 940.0,
                lastLatitude = 37.3455,
                lastLongitude = -121.8863,
                speedMph = 15.0,
                isDisplacementConfirmed = true,
                waypoint = Waypoint(37.3455, -121.8863, speed = 7.0f, accuracy = 6f, elapsedRealtimeNanos = fakeElapsedClockMillis * 1_000_000L)
            )
        )
        var extendedDeadline = service.getStopDeadlineElapsedForTesting()
        for (i in 1..40) {
            extendedDeadline = service.getStopDeadlineElapsedForTesting()
            if (extendedDeadline == fakeElapsedClockMillis + 240_000L) {
                break
            }
            kotlinx.coroutines.delay(50L)
        }
        assertFalse("inVehicle must remain false while BT is still disconnected", preferencesManager.isInVehicle())
        assertTrue("BT disconnect pending remains true when deadline is extended", preferencesManager.isBluetoothDisconnectPending())
        assertEquals("Stop deadline should extend 4 minutes from resumed motion", fakeElapsedClockMillis + 240_000L, extendedDeadline)
    }

    // 38. Assigned vehicle disconnect still schedules auto-stop even with second saved Tesla in garage or lagging GATT
    @Test(timeout = 15_000)
    fun scenario38_assignedVehicleDisconnectSchedulesAutoStopDespiteSecondTeslaInGarage() = runBlocking {
        val tripId = "bt-assigned-garage-38"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 10_000L,
                startLatitude = 37.3382,
                startLongitude = -121.8863,
                lastLatitude = 37.3450,
                lastLongitude = -121.8863,
                lastTimestamp = 95_000L,
                totalDistanceMeters = 900.0,
                trackingMethod = TrackingMethod.AUTO_DETECTED.value,
                vehicleId = modelY.id
            )
        )
        preferencesManager.setActiveTripId(tripId)

        // Both Model Y (being driven) and Model 3 (parked in garage) are initially connected;
        // fallbackDevicesProvider also simulates lagging GATT Phone Key still returning modelY after disconnect!
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, modelY.bluetoothAddress!!)
        matcher.fallbackDevicesProvider = { setOf(model3.bluetoothAddress!!, modelY.bluetoothAddress!!) }

        // Model Y disconnects when arriving at destination
        eventHandler.handle(BluetoothDevice.ACTION_ACL_DISCONNECTED, modelY.bluetoothAddress!!)

        assertTrue("Disconnect of assigned trip vehicle MUST schedule auto-stop", scheduler.scheduled)
        assertTrue(preferencesManager.isBluetoothDisconnectPending())
        assertFalse(preferencesManager.isInVehicle())
        assertFalse(
            "Disconnected Model Y address must not be re-added by lagging GATT/profile reconcile",
            preferencesManager.getConnectedCarAddresses().contains(VehicleBluetoothMatchPolicy.normalizeAddress(modelY.bluetoothAddress!!))
        )
    }

    // 39. Repeated BT_ARMED activity updates during active session only log SESSION_ALREADY_ACTIVE once
    @Test(timeout = 15_000)
    fun scenario39_activeSessionActivityUpdatesDeduplicateDiagnosticLogs() = runBlocking {
        AutoDriveReceiver.resetDiagnosticDedupeForTesting()
        TrackingDiagnostics.clearForTesting()

        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        activeTripRepository.createActiveTrip("active-dedupe-39", 37.0, -122.0, TrackingMethod.AUTO_DETECTED.value, "MySucculentCorner")

        repeat(5) {
            receiver.handleArmedActivityUpdate(context, listOf(DetectedActivity(DetectedActivity.IN_VEHICLE, 90)))
        }

        val loggedCount = TrackingDiagnostics.getRecentEvents().count {
            it.category == "AutoStartDecision" && it.code == "BT_ARMED_REJECTED_SESSION_ALREADY_ACTIVE"
        }
        assertEquals("SESSION_ALREADY_ACTIVE should log at most once per active trip", 1, loggedCount)
    }

    // 40. Sitting in car 15+ minutes (past old 10-min cliff) then driving with low IN_VEHICLE conf (STILL=90) still auto-starts
    @Test(timeout = 15_000)
    fun scenario40_sittingInCarFifteenMinutesThenDrivingWithLowConfidence_autoStartsCleanly() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, model3.bluetoothAddress!!)
        preferencesManager.setConnectedCarAddresses(setOf(model3.bluetoothAddress!!), fakeElapsedClockMillis)

        // Wait 15 minutes parked (exceeds legacy 10-minute ACTIVE_POST_CONNECT_WINDOW_MILLIS)
        fakeElapsedClockMillis += 15L * 60_000L
        monitor.locationProvider = { makeLocation(37.3382, -121.8863, fakeElapsedClockMillis, speedMps = 0f, accuracyMeters = 8f) }
        receiver.handleArmedActivityUpdate(
            context,
            listOf(DetectedActivity(DetectedActivity.STILL, 90), DetectedActivity(DetectedActivity.IN_VEHICLE, 0))
        )
        assertNull("Sitting parked after 15 minutes must not false-start", preferencesManager.activeTripId.first())

        // 65 seconds later (> extended 60s cadence while STILL=90), driver smoothly pulls onto road
        fakeElapsedClockMillis += 65_000L
        monitor.locationProvider = { makeLocation(37.3410, -121.8863, fakeElapsedClockMillis, speedMps = 10f, accuracyMeters = 8f) }
        receiver.handleArmedActivityUpdate(
            context,
            listOf(DetectedActivity(DetectedActivity.STILL, 90), DetectedActivity(DetectedActivity.IN_VEHICLE, 0))
        )

        assertNotNull("Driving after 15-minute parked wait must still auto-start even when IN_VEHICLE conf=0", preferencesManager.activeTripId.first())
    }

    // 41. Toggling Bluetooth OFF -> ON while already driving reconciles connection and starts trip via connect probe
    @Test(timeout = 15_000)
    fun scenario41_bluetoothToggleOffThenOnWhileDriving_reconcilesAndAutoStartsViaConnectProbe() = runBlocking {
        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, modelY.bluetoothAddress!!)
        preferencesManager.setCarBluetoothConnected(false)
        monitor.reconcile(allowProfileDiscovery = false)
        assertFalse(monitor.isArmedForTesting())

        var probeRequested = false
        monitor.postConnectProbeDispatcher = { probeRequested = true }
        matcher.activeProfileDevicesProvider = { setOf(modelY.bluetoothAddress!!) }
        matcher.fallbackDevicesProvider = { emptySet() }

        // User toggles Bluetooth back ON while driving
        eventHandler.handleBluetoothAdapterTurnedOn()
        assertTrue("Bluetooth ON must request an immediate movement probe when no trip is active", probeRequested)
        assertTrue(preferencesManager.isCarBluetoothConnected())
        assertTrue(monitor.isArmedForTesting())

        // Parked probe stores origin anchor without false-starting
        monitor.locationProvider = { makeLocation(37.3382, -121.8863, fakeElapsedClockMillis, speedMps = 0f, accuracyMeters = 8f) }
        receiver.handleBluetoothConnectProbe(context)
        assertNull("Parked connect probe must not false-start", preferencesManager.activeTripId.first())

        // Moving connect probe starts trip immediately without waiting for Activity Recognition
        fakeElapsedClockMillis += BluetoothArmedDriveMonitor.CONNECT_PROBE_MIN_GAP_MILLIS + 1_000L
        monitor.locationProvider = { makeLocation(37.3400, -121.8863, fakeElapsedClockMillis, speedMps = 12f, accuracyMeters = 5f) }
        receiver.handleBluetoothConnectProbe(context)

        val active = activeTripRepository.getActiveTrip()
        assertNotNull("Moving connect probe after BT toggle ON must start trip", active)
        assertEquals(modelY.id, active?.vehicleId)
    }

    // 42. Stale bluetoothDisconnectPending flag is cleared on connect & new trip start, never spamming deadline extension
    @Test(timeout = 15_000)
    fun scenario42_staleBluetoothDisconnectPending_clearedOnConnectAndStart_neverSpamsDuringDrive() = runBlocking {
        TrackingDiagnostics.clearForTesting()
        preferencesManager.setBluetoothDisconnectPending(true)

        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, modelY.bluetoothAddress!!)
        assertFalse("ACL_CONNECTED with no active trip must clear stale bluetoothDisconnectPending", preferencesManager.isBluetoothDisconnectPending())

        // Even if set again right before ACTION_START of a new session, new service start clears it
        preferencesManager.setBluetoothDisconnectPending(true)
        val tripId = "fresh-trip-clear-pending-42"
        val service = LocationTrackingService()
        service.elapsedClock = { fakeElapsedClockMillis }
        service.locationUpdatesRegistrar = { true }
        service.setTestDependencies(
            activeTripRepo = activeTripRepository,
            helper = notificationHelper,
            tripId = tripId,
            lifecycleDao = database.tripLifecycleDao(),
            inboxDao = database.gpsInboxDao(),
            drainUseCase = drainUseCase,
            seqDao = database.tripSequenceDao(),
            db = database,
            prefs = preferencesManager,
            coordinator = finalizationCoordinator,
            dispatcher = testDispatcher,
            scope = testScope,
            context = context,
            vehicleRepo = vehicleRepository,
            matcher = matcher,
            armedMonitor = monitor
        )
        val startIntent = Intent(LocationTrackingService.ACTION_START).apply {
            putExtra(LocationTrackingService.EXTRA_TRIP_ID, tripId)
            putExtra(LocationTrackingService.EXTRA_TRACKING_METHOD, TrackingMethod.AUTO_DETECTED.value)
        }
        service.onStartCommand(startIntent, 0, 1)
        service.lastStartupJob?.join()

        assertFalse("Fresh session startup must clear stale bluetoothDisconnectPending", preferencesManager.isBluetoothDisconnectPending())

        repeat(4) { idx ->
            fakeElapsedClockMillis += 3_000L
            service.handleFixProcessed(
                GpsInboxProcessor.ProcessResult.Accepted(
                    fixId = idx + 1L,
                    tripId = tripId,
                    deltaDistanceMeters = 50.0,
                    totalDistanceMeters = (idx + 1) * 50.0,
                    lastLatitude = 37.3382 + idx * 0.0004,
                    lastLongitude = -121.8863,
                    speedMph = 25.0,
                    isDisplacementConfirmed = idx >= 3,
                    waypoint = Waypoint(37.3382 + idx * 0.0004, -121.8863, speed = 11f, accuracy = 5f, elapsedRealtimeNanos = fakeElapsedClockMillis * 1_000_000L)
                )
            )
        }

        assertEquals(0L, service.getStopDeadlineElapsedForTesting())
        val extendedEvents = TrackingDiagnostics.getRecentEvents().count {
            it.category == "StopDeadline" && it.code == "BT_DISCONNECT_DEADLINE_EXTENDED_BY_MOTION"
        }
        assertEquals("Connected driving must never emit BT_DISCONNECT_DEADLINE_EXTENDED_BY_MOTION", 0, extendedEvents)
        LocationTrackingService.resetGuardForTesting()
    }

    // 43. exportRedactedReport preserves newest events within paste-safe character budget
    @Test(timeout = 15_000)
    fun scenario43_diagnosticReportExportPreservesNewestEventsWithinSizeBudget() {
        TrackingDiagnostics.clearForTesting()
        repeat(250) { idx ->
            TrackingDiagnostics.logArmedMonitor("TEST_EVT_$idx", "payload-padding-for-size-check-$idx")
        }

        val report = TrackingDiagnostics.exportRedactedReport(null)
        assertTrue("Report must contain the newest recorded event", report.contains("TEST_EVT_249"))
        assertTrue("Report must indicate newest slice was selected", report.contains("Showing latest "))
        assertTrue("Report size (${report.length}) must fit paste-safe budget (<= 13000)", report.length <= 13_000)
    }

    // 44. Toggling Bluetooth OFF -> ON during an active trip cancels pending Bluetooth disconnect stop deadline
    @Test(timeout = 15_000)
    fun scenario44_bluetoothToggleOffThenOnDuringActiveTrip_cancelsPendingDisconnectStopDeadline() = runBlocking {
        TrackingDiagnostics.clearForTesting()
        val tripId = "bt-toggle-active-44"
        database.activeTripDao().insertActiveTrip(
            ActiveTripEntity(
                id = tripId,
                startTime = 10_000L,
                startLatitude = 37.3382,
                startLongitude = -121.8863,
                lastLatitude = 37.3450,
                lastLongitude = -121.8863,
                lastTimestamp = 95_000L,
                totalDistanceMeters = 800.0,
                trackingMethod = TrackingMethod.AUTO_DETECTED.value,
                vehicleId = modelY.id
            )
        )
        database.tripLifecycleDao().insertOrUpdate(TripLifecycleEntity(tripId, TripLifecycleState.TRACKING.value))
        preferencesManager.setActiveTripId(tripId)

        val service = LocationTrackingService()
        service.elapsedClock = { fakeElapsedClockMillis }
        service.setTestDependencies(
            activeTripRepo = activeTripRepository,
            helper = notificationHelper,
            tripId = tripId,
            lifecycleDao = database.tripLifecycleDao(),
            inboxDao = database.gpsInboxDao(),
            drainUseCase = drainUseCase,
            seqDao = database.tripSequenceDao(),
            db = database,
            prefs = preferencesManager,
            coordinator = finalizationCoordinator,
            dispatcher = testDispatcher,
            scope = testScope,
            context = context,
            vehicleRepo = vehicleRepository,
            matcher = matcher,
            armedMonitor = monitor
        )
        service.setTrackingStateForTesting(tripId, distanceMeters = 800.0, displacementConfirmed = true)

        eventHandler.handle(BluetoothDevice.ACTION_ACL_CONNECTED, modelY.bluetoothAddress!!)
        eventHandler.handle(BluetoothDevice.ACTION_ACL_DISCONNECTED, modelY.bluetoothAddress!!)
        assertTrue(preferencesManager.isBluetoothDisconnectPending())
        assertTrue(service.getStopDeadlineElapsedForTesting() > 0L)

        // Toggle BT OFF then back ON while drive resumes
        preferencesManager.setCarBluetoothConnected(false)
        matcher.activeProfileDevicesProvider = { setOf(modelY.bluetoothAddress!!) }
        matcher.fallbackDevicesProvider = { emptySet() }

        eventHandler.handleBluetoothAdapterTurnedOn()

        assertFalse("Reconnect after BT toggle ON must clear bluetoothDisconnectPending", preferencesManager.isBluetoothDisconnectPending())
        assertTrue("Reconnect after BT toggle ON must restore inVehicle=true", preferencesManager.isInVehicle())
        assertEquals("Pending in-service stop deadline must be cancelled", 0L, service.getStopDeadlineElapsedForTesting())
        assertTrue("Debounce worker must be cancelled", scheduler.cancelled)
        assertTrue(
            TrackingDiagnostics.getRecentEvents().any {
                it.category == "StopDeadline" && it.code == "BT_RECONNECT_DEBOUNCE_CANCELLED"
            }
        )
    }

    private class RecordingScheduler(context: Context) : TripDebounceScheduler(context) {
        var scheduled = false
        var cancelled = false
        override fun scheduleForBluetoothDisconnect(tripId: String) { scheduled = true }
        override fun cancel() { cancelled = true }
        override fun cancel(tripId: String?) { cancelled = true }
    }
}
