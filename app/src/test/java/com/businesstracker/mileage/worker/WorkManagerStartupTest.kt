package com.businesstracker.mileage.worker

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.startup.InitializationProvider
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.businesstracker.mileage.MileageTrackerApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Covers production startup wiring rather than injecting a replacement worker factory. */
@RunWith(RobolectricTestRunner::class)
@Config(application = MileageTrackerApp::class)
class WorkManagerStartupTest {
    @Test
    fun mergedManifest_doesNotEagerlyInitializeWorkManagerWithDefaultFactory() {
        val app = ApplicationProvider.getApplicationContext<MileageTrackerApp>()
        val provider = app.packageManager.getProviderInfo(
            ComponentName(app, InitializationProvider::class.java),
            PackageManager.GET_META_DATA
        )
        assertFalse(
            "Default startup would bypass the application's HiltWorkerFactory",
            provider.metaData?.containsKey("androidx.work.WorkManagerInitializer") == true
        )
    }

    @Test
    fun onDemandWorkManager_usesProductionHiltFactory_andConstructsExitWorker() {
        val app = ApplicationProvider.getApplicationContext<MileageTrackerApp>()
        val factory = WorkManager.getInstance(app).configuration.workerFactory
        assertTrue(factory is HiltWorkerFactory)

        // The default reflection factory cannot construct this injected worker.
        // Use the factory selected by real WorkManager initialization, with actual
        // Hilt-generated bindings, rather than the fake used by worker logic tests.
        val worker = TestListenableWorkerBuilder<TripDebounceWorker>(app)
            .setWorkerFactory(factory)
            .build()
        assertNotNull(worker)
    }
}
