package com.businesstracker.mileage.data.device

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidBootCountProviderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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
        // Reset or use non-existent setting key on mock or unpopulated resolver
        val mockContext = mock(Context::class.java)
        val mockResolver = mock(ContentResolver::class.java)
        `when`(mockContext.contentResolver).thenReturn(mockResolver)
        `when`(Settings.Global.getInt(mockResolver, Settings.Global.BOOT_COUNT, 0)).thenReturn(0)

        val provider = AndroidBootCountProvider(mockContext)
        assertEquals(0, provider.getBootCount())
    }

    // TEST 3: An exception while reading setting returns 0
    @Test
    fun getBootCount_exceptionReadingSetting_returnsZero() {
        val mockContext = mock(Context::class.java)
        `when`(mockContext.contentResolver).thenThrow(SecurityException("Restricted access to Settings.Global"))

        val provider = AndroidBootCountProvider(mockContext)
        assertEquals(0, provider.getBootCount())
    }
}
