package com.reststop.countdown.data.model

/**
 * A rest area / service plaza found once during trip setup and then tracked
 * locally for the rest of the drive - no further network calls needed.
 *
 * @property distanceAlongRouteMeters how far this stop sits along the route,
 *   measured from the origin. Used to keep the queue in driving order and to
 *   detect when the driver has physically passed the stop.
 */
data class RestStop(
    val placeId: String,
    val name: String,
    val location: GeoPoint,
    val distanceAlongRouteMeters: Double,
)
