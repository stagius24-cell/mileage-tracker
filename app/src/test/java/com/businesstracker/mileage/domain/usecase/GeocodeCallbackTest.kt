package com.businesstracker.mileage.domain.usecase

import android.content.Context
import android.location.Address
import android.location.Geocoder
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.Locale

@Implements(Geocoder::class)
class DelayedGeocoderShadow {
    @Implementation
    protected fun getFromLocation(lat: Double, lon: Double, count: Int, callback: Geocoder.GeocodeListener) {
        listener = callback
    }
    companion object {
        var listener: Geocoder.GeocodeListener? = null
        @JvmStatic @Implementation fun isPresent() = true
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [DelayedGeocoderShadow::class])
class GeocodeCallbackTest {
    @Test fun callbackFormatsStreetAddress_andIgnoresDuplicateCompletion() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val useCase = GeocodeLocationUseCase(context, StandardTestDispatcher(testScheduler))
        val result = async { useCase.getFormattedAddress(37.77, -122.41) }
        runCurrent()
        val callback = requireNotNull(DelayedGeocoderShadow.listener)
        callback.onGeocode(mutableListOf(Address(Locale.US).apply {
            subThoroughfare = "100"; thoroughfare = "Start Street"; locality = "Example"; adminArea = "CA"
        }))
        callback.onError("Duplicate callback")
        assertEquals("100 Start Street, Example, CA", result.await())
    }

    @Test fun stalledLookupTimesOut_andLateCallbackIsSafe() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val useCase = GeocodeLocationUseCase(context, StandardTestDispatcher(testScheduler))
        val result = async { useCase.getFormattedAddress(37.77, -122.41) }
        runCurrent()
        val callback = requireNotNull(DelayedGeocoderShadow.listener)
        advanceTimeBy(8_001L)
        runCurrent()
        assertNull(result.await())
        callback.onGeocode(mutableListOf(Address(Locale.US)))
    }

    @Test fun cancelledLookup_doesNotResumeOrPersistLateAddress() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val useCase = GeocodeLocationUseCase(context, StandardTestDispatcher(testScheduler))
        val result = async { useCase.getFormattedAddress(37.77, -122.41) }
        runCurrent()
        val callback = requireNotNull(DelayedGeocoderShadow.listener)
        result.cancelAndJoin()
        callback.onError("Late callback")
        assertTrue(result.isCancelled)
    }
}
