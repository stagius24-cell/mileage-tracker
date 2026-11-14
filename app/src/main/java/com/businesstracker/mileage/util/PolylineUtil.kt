package com.businesstracker.mileage.util

import kotlin.math.roundToInt

data class LatLngPoint(val latitude: Double, val longitude: Double)

object PolylineUtil {

    /**
     * Encodes a list of [LatLngPoint] coordinates into a Google Encoded Polyline string.
     */
    fun encode(points: List<LatLngPoint>): String {
        if (points.isEmpty()) return ""
        val result = StringBuilder()
        var prevLat = 0
        var prevLng = 0

        for (point in points) {
            val lat = (point.latitude * 1e5).roundToInt()
            val lng = (point.longitude * 1e5).roundToInt()

            val dLat = lat - prevLat
            val dLng = lng - prevLng

            encodeValue(dLat, result)
            encodeValue(dLng, result)

            prevLat = lat
            prevLng = lng
        }

        return result.toString()
    }

    private fun encodeValue(value: Int, result: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else (value shl 1)
        while (v >= 0x20) {
            result.append((((v and 0x1f) or 0x20) + 63).toChar())
            v = v ushr 5
        }
        result.append((v + 63).toChar())
    }

    /**
     * Decodes a Google Encoded Polyline string into a list of [LatLngPoint] coordinates.
     */
    fun decode(encoded: String): List<LatLngPoint> {
        if (encoded.isBlank()) return emptyList()
        val points = mutableListOf<LatLngPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        try {
            while (index < len) {
                var b: Int
                var shift = 0
                var result = 0
                do {
                    if (index >= len) break
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                val dLat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
                lat += dLat

                shift = 0
                result = 0
                do {
                    if (index >= len) break
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                val dLng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
                lng += dLng

                points.add(LatLngPoint(lat / 1e5, lng / 1e5))
            }
        } catch (_: Exception) {
            // Malformed polyline gracefully returns points decoded so far
        }

        return points
    }
}
