package com.reststop.countdown.domain.util

import android.location.Location
import com.reststop.countdown.data.model.GeoPoint
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * A precomputed index over a fixed route polyline that lets the app answer, entirely on
 * device and with no network calls:
 *
 *  - "How far along the route (from the origin) is this point?" -> used to keep rest stops
 *    in driving order and to detect that the driver has passed one.
 *  - "How far off the route (perpendicular) is this point?" -> used to rank other points of
 *    interest by closeness to the road, per the "sorted by distance from the route's
 *    polyline" requirement.
 *
 * This is built exactly once, right after the Directions API response is decoded, and is
 * then reused for every GPS update for the rest of the trip.
 */
class RoutePolylineIndex private constructor(
    private val points: List<GeoPoint>,
    private val cumulativeDistanceMeters: DoubleArray,
) {

    val totalLengthMeters: Double get() = cumulativeDistanceMeters.last()

    data class Projection(
        val distanceAlongRouteMeters: Double,
        val lateralDistanceMeters: Double,
    )

    /**
     * Projects [point] onto the closest segment of the route. O(n) in the number of route
     * vertices, run locally on-device on each GPS tick - cheap relative to a network call
     * and fine for routes with up to a few thousand polyline vertices.
     */
    fun project(point: GeoPoint): Projection {
        var best: Projection? = null
        for (i in 0 until points.size - 1) {
            val segmentStart = points[i]
            val segmentEnd = points[i + 1]
            val segmentLengthMeters = cumulativeDistanceMeters[i + 1] - cumulativeDistanceMeters[i]

            val (fraction, lateralDistanceMeters) = projectOntoSegment(point, segmentStart, segmentEnd)
            val distanceAlongRouteMeters = cumulativeDistanceMeters[i] + fraction * segmentLengthMeters

            if (best == null || lateralDistanceMeters < best.lateralDistanceMeters) {
                best = Projection(distanceAlongRouteMeters, lateralDistanceMeters)
            }
        }
        return best ?: Projection(distanceAlongRouteMeters = 0.0, lateralDistanceMeters = Double.MAX_VALUE)
    }

    /**
     * Projects [point] onto the segment [segmentStart]-[segmentEnd] using a local
     * equirectangular (flat-earth) approximation, which is accurate to a few meters over
     * the short segment lengths typical of a decoded route polyline.
     *
     * @return the clamped fraction [0, 1] along the segment, and the lateral distance in meters.
     */
    private fun projectOntoSegment(point: GeoPoint, segmentStart: GeoPoint, segmentEnd: GeoPoint): Pair<Double, Double> {
        val referenceLatRad = Math.toRadians(segmentStart.latitude)
        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLng = 111_320.0 * cos(referenceLatRad)

        fun toLocalMeters(p: GeoPoint): DoubleArray = doubleArrayOf(
            (p.longitude - segmentStart.longitude) * metersPerDegreeLng,
            (p.latitude - segmentStart.latitude) * metersPerDegreeLat,
        )

        val start = doubleArrayOf(0.0, 0.0)
        val end = toLocalMeters(segmentEnd)
        val target = toLocalMeters(point)

        val segmentDx = end[0] - start[0]
        val segmentDy = end[1] - start[1]
        val segmentLengthSquared = segmentDx * segmentDx + segmentDy * segmentDy

        val fraction = if (segmentLengthSquared == 0.0) {
            0.0
        } else {
            ((target[0] - start[0]) * segmentDx + (target[1] - start[1]) * segmentDy) / segmentLengthSquared
        }.coerceIn(0.0, 1.0)

        val closestX = start[0] + fraction * segmentDx
        val closestY = start[1] + fraction * segmentDy
        val dx = target[0] - closestX
        val dy = target[1] - closestY
        val lateralDistanceMeters = sqrt(dx * dx + dy * dy)

        return fraction to lateralDistanceMeters
    }

    companion object {
        fun build(points: List<GeoPoint>): RoutePolylineIndex {
            require(points.size >= 2) { "A route polyline needs at least 2 points" }
            val cumulative = DoubleArray(points.size)
            val results = FloatArray(1)
            for (i in 1 until points.size) {
                Location.distanceBetween(
                    points[i - 1].latitude, points[i - 1].longitude,
                    points[i].latitude, points[i].longitude,
                    results,
                )
                cumulative[i] = cumulative[i - 1] + results[0]
            }
            return RoutePolylineIndex(points, cumulative)
        }
    }
}
