package com.businesstracker.mileage.ui.history

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.usecase.GeocodeLocationUseCase
import com.businesstracker.mileage.domain.usecase.ResolveTripAddressesUseCase
import com.businesstracker.mileage.ui.components.PrivateMapTileDownloader
import com.businesstracker.mileage.ui.components.PrivateMapTileCache
import com.businesstracker.mileage.ui.components.createRouteMap
import com.businesstracker.mileage.ui.components.routeMapData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class TripMapAndAddressTest {
    private lateinit var context: Context
    private lateinit var db: ExpenseTrackerDatabase

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }

    private fun trip() = TripEntity(
        id = "map-address-test", date = "2026-11-06", startTime = 1000L, endTime = 2000L,
        startAddress = null, endAddress = "200 Destination Avenue, Example, CA",
        startLatitude = 37.77, startLongitude = -122.41, endLatitude = 37.78, endLongitude = -122.42,
        totalDistanceMiles = 8.4, businessMiles = 0.0, businessEntity = "Personal", purpose = "Personal Drive",
        trackingMethod = "AUTO_DETECTED", classification = "PERSONAL", applicableRate = 0.760,
        calculatedDeduction = 0.0, notes = "Synthetic test trip", waypointsJson = "[]", syncStatus = "PENDING", syncedAt = null
    )

    private fun resolver(lookup: suspend (Double, Double) -> String?) = ResolveTripAddressesUseCase(
        db.tripDao(), object : GeocodeLocationUseCase(context, Dispatchers.IO) {
            override suspend fun getFormattedAddress(latitude: Double, longitude: Double) = lookup(latitude, longitude)
        }
    )

    @Test fun savedTrip_missingStartAddress_isResolvedWithoutChangingAnyOtherFields() = runBlocking {
        val before = trip()
        db.tripDao().insertTrip(before)
        var lookups = 0
        val result = resolver { lat, lon ->
            assertEquals(before.startLatitude, lat, 0.0)
            assertEquals(before.startLongitude, lon, 0.0)
            lookups++
            "100 Start Street, Example, CA"
        }.execute(before.id)
        assertEquals(1, lookups)
        assertEquals(before.copy(startAddress = "100 Start Street, Example, CA"), result)
        assertEquals(result, db.tripDao().getTripById(before.id))
    }

    @Test fun addressLookup_doesNotOverwriteConcurrentAddressOrNoteEdits() = runBlocking {
        val before = trip()
        db.tripDao().insertTrip(before)
        val edited = before.copy(startAddress = "Edited address", notes = "Edited note")
        val result = resolver { _, _ -> db.tripDao().updateTrip(edited); "Stale geocoder result" }.execute(before.id)
        assertEquals(edited, result)
    }

    @Test fun addressLookup_doesNotApplyAnAddressToChangedCoordinates() = runBlocking {
        val before = trip()
        db.tripDao().insertTrip(before)
        val edited = before.copy(startLatitude = 38.0)
        val result = resolver { _, _ -> db.tripDao().updateTrip(edited); "Stale address" }.execute(before.id)
        assertEquals(edited, result)
    }

    @Test fun unavailableGeocoder_preservesCompleteTrip() = runBlocking {
        val before = trip()
        db.tripDao().insertTrip(before)
        assertEquals(before, resolver { _, _ -> null }.execute(before.id))
    }

    @Test fun deletedTrip_isNeverRecreatedByLateAddressLookup() = runBlocking {
        val before = trip()
        db.tripDao().insertTrip(before)
        assertNull(resolver { _, _ -> db.tripDao().deleteTripById(before.id); "Late address" }.execute(before.id))
        assertNull(db.tripDao().getTripById(before.id))
    }

    @Test fun alreadyResolvedAddresses_areNotLookedUpAgain() = runBlocking {
        val before = trip().copy(startAddress = "Existing start address")
        db.tripDao().insertTrip(before)
        assertEquals(before, resolver { _, _ -> error("Unexpected address request") }.execute(before.id))
    }

    @Test fun map_usesRecordedRouteOverStreetTileSource_withEndpointMarkers() {
        val data = routeMapData(trip().copy(waypointsJson = """[
          {"latitude":37.77,"longitude":-122.41},
          {"latitude":37.775,"longitude":-122.415},
          {"latitude":37.78,"longitude":-122.42}]
        """))
        val map = createRouteMap(context, data, allowNetwork = false)
        try {
            assertEquals(TileSourceFactory.MAPNIK.name(), map.tileProvider.tileSource.name())
            assertTrue(map.tileProvider.tileSource.copyrightNotice.contains("OpenStreetMap"))
            assertEquals(3, map.overlays.filterIsInstance<Polyline>().single().actualPoints.size)
            val markers = map.overlays.filterIsInstance<Marker>()
            assertEquals(listOf("Start", "End"), markers.map { it.title })
            assertEquals(data.recordedPoints.first().latitude, markers.first().position.latitude, 0.0)
            assertTrue(Configuration.getInstance().osmdroidTileCache.canonicalPath.startsWith(context.cacheDir.canonicalPath))
            map.onResume()
            map.onPause()
        } finally { map.onDetach() }
    }

    @Test fun mapWithoutWaypoints_showsOnlyEndpointMarkers_notAnInventedRoute() {
        val data = routeMapData(trip().copy(waypointsJson = "not JSON"))
        val map = createRouteMap(context, data, allowNetwork = false)
        try {
            assertTrue(map.overlays.filterIsInstance<Polyline>().isEmpty())
            assertEquals(2, map.overlays.filterIsInstance<Marker>().size)
        } finally { map.onDetach() }
    }

    @Test fun malformedRouteEntries_areRejectedWithoutDiscardingValidPoints() {
        val data = routeMapData(trip().copy(waypointsJson = """[
          {"latitude":37.77,"longitude":-122.41},
          {"latitude":999,"longitude":-122.41},{"longitude":-122.41},null,
          {"latitude":37.78,"longitude":-122.42}]
        """))
        assertEquals(2, data.recordedPoints.size)
    }

    @Test fun tileDownloadFailure_doesNotLogTileCoordinatesOrRequestUrl() {
        Configuration.getInstance().userAgentValue = "com.example.test/1.0"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(503).message("Unavailable").body("unavailable".toResponseBody()).build()
        }.build()
        ShadowLog.clear()
        assertNull(PrivateMapTileDownloader(client).downloadTile(
            0L, 0, "https://tile.openstreetmap.org/0/0/0.png", null, TileSourceFactory.MAPNIK
        ))
        val messages = ShadowLog.getLogs().joinToString { it.msg }
        assertFalse(messages.contains("tile.openstreetmap.org"))
        assertFalse(messages.contains("/0/0/0"))
    }

    @Test fun tileCache_rejectsLateWritesWithoutLocationDiagnostics() {
        val map = createRouteMap(context, routeMapData(trip()), allowNetwork = false)
        map.onDetach()
        val cache = PrivateMapTileCache()
        cache.onDetach()
        ShadowLog.clear()
        assertFalse(cache.saveFile(TileSourceFactory.MAPNIK, 0L, byteArrayOf(1, 2).inputStream(), 1000L))
        assertTrue(ShadowLog.getLogs().isEmpty())
    }

    @Test fun tileCache_roundTripsImageAndServerExpirationForTheMapReader() {
        val map = createRouteMap(context, routeMapData(trip()), allowNetwork = false)
        map.onDetach()
        val cache = PrivateMapTileCache()
        try {
            val image = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val bytes = ByteArrayOutputStream().apply { image.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
            val expiration = System.currentTimeMillis() + 600_000L
            assertTrue(cache.saveFile(TileSourceFactory.MAPNIK, 0L, bytes.inputStream(), expiration))
            assertEquals(expiration, cache.getExpirationTimestamp(TileSourceFactory.MAPNIK, 0L))
            assertNotNull(cache.loadTile(TileSourceFactory.MAPNIK, 0L))
        } finally {
            cache.remove(TileSourceFactory.MAPNIK, 0L)
            cache.onDetach()
        }
    }
}
