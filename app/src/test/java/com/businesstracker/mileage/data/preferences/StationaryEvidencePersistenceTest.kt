package com.businesstracker.mileage.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StationaryEvidencePersistenceTest {
    @Test fun serviceRecreationRetainsTripBoundStationaryEvidence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = TrackingPreferencesManager(context)
        val evidence = StationaryEvidence("trip-a", 37.3382, -121.8863, 601_000L, 5)
        original.saveStationaryEvidence(evidence)

        val recreated = TrackingPreferencesManager(context)
        assertEquals(evidence, recreated.getStationaryEvidence())
        recreated.saveStationaryEvidence(null)
        assertNull(original.getStationaryEvidence())
    }

    @Test fun vehicleEnterMarkerSurvivesReceiverRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TrackingPreferencesManager(context).recordVehicleEnter("trip-a", 650_000L, 5)
        assertEquals(VehicleEnterEvidence("trip-a", 650_000L, 5),
            TrackingPreferencesManager(context).getVehicleEnterEvidence())
    }
}
