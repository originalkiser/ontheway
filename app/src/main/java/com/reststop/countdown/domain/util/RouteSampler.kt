package com.reststop.countdown.domain.util

import android.location.Location
import com.reststop.countdown.data.model.GeoPoint

/**
 * Picks a small number of evenly spaced points along a route polyline to use as search-circle
 * centers for the Places API. A single Nearby Search call only covers a limited radius, so a
 * long route needs several search circles to cover its full length - but this sampling, and the
 * resulting Places calls, all happen exactly once during trip setup, never during live tracking.
 */
object RouteSampler {

    /**
     * @param intervalMeters target spacing between sample points.
     * @param maxSamples hard cap on the number of Places API calls issued per category, so a
     *   very long route can't blow up the request count.
     */
    fun sampleEvery(
        polyline: List<GeoPoint>,
        intervalMeters: Double,
        maxSamples: Int = 15,
    ): List<GeoPoint> {
        if (polyline.size < 2) return polyline

        val samples = mutableListOf(polyline.first())
        var distanceSinceLastSample = 0.0
        val segmentResult = FloatArray(1)

        for (i in 1 until polyline.size) {
            val previous = polyline[i - 1]
            val current = polyline[i]
            Location.distanceBetween(previous.latitude, previous.longitude, current.latitude, current.longitude, segmentResult)
            distanceSinceLastSample += segmentResult[0]
            if (distanceSinceLastSample >= intervalMeters) {
                samples += current
                distanceSinceLastSample = 0.0
                if (samples.size >= maxSamples) break
            }
        }

        if (samples.last() != polyline.last() && samples.size < maxSamples) {
            samples += polyline.last()
        }
        return samples
    }
}
