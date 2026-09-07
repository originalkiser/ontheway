package com.reststop.countdown.data.model

/**
 * A generic "other place" along the route (fast food, coffee, gas, EV charging, ...).
 *
 * @property distanceFromRouteMeters perpendicular ("as the crow flies") distance from
 *   the nearest point on the route polyline - this is what the "sorted by distance from
 *   the route's polyline" requirement sorts by.
 * @property distanceAlongRouteMeters how far along the route the closest point is,
 *   used to decide whether the place is still "upcoming" and to order the upcoming list.
 */
data class PointOfInterest(
    val placeId: String,
    val name: String,
    val category: PoiCategory,
    val location: GeoPoint,
    val distanceFromRouteMeters: Double,
    val distanceAlongRouteMeters: Double,
)
