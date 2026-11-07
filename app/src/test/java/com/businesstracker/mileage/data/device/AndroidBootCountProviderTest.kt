package com.businesstracker.mileage.data.device

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidBootCountProviderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Settings.Global.putString(context.contentResolver, Settings.Global.BOOT_COUNT, null)
    }

    // TEST 1: A valid system boot count
    @Test
    fun getBootCount_returnsValidSystemBootCount() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, 42)
        val provider = AndroidBootCountProvider(context)
        assertEquals(42, provider.getBootCount())
    }

    // TEST 2: A missing setting returns 0 (unknown/unavailable)
    @Test
    fun getBootCount_missingSetting_returnsZero() {
        Settings.Global.putString(context.contentResolver, Settings.Global.BOOT_COUNT, null)
        val provider = AndroidBootCountProvider(context)
        assertEquals(0, provider.getBootCount())
    }

    // TEST 3: An exception while reading setting returns 0
    @Test
    fun getBootCount_exceptionReadingSetting_returnsZero() {
        val throwingContext = object : ContextWrapper(context) {
            override fun getContentResolver(): ContentResolver {
                throw SecurityException("Restricted access to Settings.Global")
            }
        }
        val provider = AndroidBootCountProvider(throwingContext)
        assertEquals(0, provider.getBootCount())
    }
}
