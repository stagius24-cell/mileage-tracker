package com.businesstracker.mileage.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AutoTrackingResumeTest {

    private lateinit var preferencesManager: TrackingPreferencesManager

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferencesManager = TrackingPreferencesManager(context)
        preferencesManager.setAutoTrackingEnabled(true)
        preferencesManager.setAutoTrackingResumeDate(null)
    }

    @Test
    fun checkAndApplyAutoTrackingResumeIfDue_reactivatesOnlyOnOrAfterScheduledDate() = runBlocking {
        preferencesManager.setAutoTrackingEnabled(false)
        preferencesManager.setAutoTrackingResumeDate("2026-11-25")

        assertFalse(preferencesManager.checkAndApplyAutoTrackingResumeIfDue("2026-11-24"))
        assertFalse(preferencesManager.isAutoTrackingEnabled.first())
        assertEquals("2026-11-25", preferencesManager.autoTrackingResumeDate.first())

        assertTrue(preferencesManager.checkAndApplyAutoTrackingResumeIfDue("2026-11-25"))
        assertTrue(preferencesManager.isAutoTrackingEnabled.first())
        assertNull(preferencesManager.autoTrackingResumeDate.first())
    }

    @Test
    fun enablingAutoTrackingManually_clearsScheduledResumeDate() = runBlocking {
        preferencesManager.setAutoTrackingEnabled(false)
        preferencesManager.setAutoTrackingResumeDate("2026-12-01")
        assertEquals("2026-12-01", preferencesManager.autoTrackingResumeDate.first())

        preferencesManager.setAutoTrackingEnabled(true)
        assertNull(preferencesManager.autoTrackingResumeDate.first())
    }
}
