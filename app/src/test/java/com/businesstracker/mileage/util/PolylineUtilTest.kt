package com.businesstracker.mileage.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PolylineUtilTest {

    @Test
    fun roundTrip_singlePoint_preservesCoordinates() {
        val original = listOf(LatLngPoint(37.77493, -122.41942))
        val encoded = PolylineUtil.encode(original)
        assertTrue(encoded.isNotEmpty())

        val decoded = PolylineUtil.decode(encoded)
        assertEquals(1, decoded.size)
        assertTrue(abs(original[0].latitude - decoded[0].latitude) < 0.00002)
        assertTrue(abs(original[0].longitude - decoded[0].longitude) < 0.00002)
    }

    @Test
    fun roundTrip_routePoints_preservesOrderAndPrecision() {
        val original = listOf(
            LatLngPoint(38.5, -120.2),
            LatLngPoint(40.7, -120.95),
            LatLngPoint(43.252, -126.453)
        )
        val encoded = PolylineUtil.encode(original)
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", encoded)

        val decoded = PolylineUtil.decode(encoded)
        assertEquals(3, decoded.size)
        for (i in original.indices) {
            assertTrue(abs(original[i].latitude - decoded[i].latitude) < 0.00002)
            assertTrue(abs(original[i].longitude - decoded[i].longitude) < 0.00002)
        }
    }

    @Test
    fun emptyOrBlank_returnsEmptyList() {
        assertEquals("", PolylineUtil.encode(emptyList()))
        assertTrue(PolylineUtil.decode("").isEmpty())
        assertTrue(PolylineUtil.decode("   ").isEmpty())
    }
}
