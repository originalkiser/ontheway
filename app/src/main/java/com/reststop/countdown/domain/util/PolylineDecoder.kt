package com.reststop.countdown.domain.util

import com.reststop.countdown.data.model.GeoPoint

/**
 * Decodes Google's encoded polyline algorithm format (used by the Directions API's
 * `overview_polyline.points` field) into a list of lat/lng points.
 *
 * Reference: https://developers.google.com/maps/documentation/utilities/polylinealgorithm
 */
object PolylineDecoder {

    fun decode(encoded: String): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var byte: Int
            do {
                byte = encoded[index].code - 63
                index++
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else (result shr 1)

            result = 0
            shift = 0
            do {
                byte = encoded[index].code - 63
                index++
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else (result shr 1)

            points += GeoPoint(latitude = lat / 1e5, longitude = lng / 1e5)
        }
        return points
    }
}
