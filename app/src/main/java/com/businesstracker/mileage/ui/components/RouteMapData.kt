package com.businesstracker.mileage.ui.components

import com.businesstracker.mileage.data.local.entity.TripEntity
import org.json.JSONArray

internal data class RouteMapPoint(val latitude: Double, val longitude: Double)

internal data class RouteMapData(
    val recordedPoints: List<RouteMapPoint>,
    val endpoints: List<RouteMapPoint>
)

internal fun routeMapData(trip: TripEntity): RouteMapData {
    fun point(lat: Double, lon: Double): RouteMapPoint? =
        if (lat.isFinite() && lon.isFinite() && lat in -85.05112878..85.05112878 && lon in -180.0..180.0)
            RouteMapPoint(lat, lon) else null

    val recorded = mutableListOf<RouteMapPoint>()
    try {
        val array = JSONArray(trip.waypointsJson)
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            point(item.optDouble("latitude", Double.NaN), item.optDouble("longitude", Double.NaN))
                ?.let { recorded.add(it) }
        }
    } catch (_: Exception) {
        // Malformed historical route data must not crash Trip Details.
    }
    val endpoints = if (recorded.isNotEmpty()) {
        listOf(recorded.first(), recorded.last()).distinct()
    } else {
        listOfNotNull(
            point(trip.startLatitude, trip.startLongitude)?.takeUnless { it.latitude == 0.0 && it.longitude == 0.0 },
            point(trip.endLatitude, trip.endLongitude)?.takeUnless { it.latitude == 0.0 && it.longitude == 0.0 }
        ).distinct()
    }
    // Endpoints alone are markers, never a fabricated straight-line route.
    return RouteMapData(recorded, endpoints)
}
