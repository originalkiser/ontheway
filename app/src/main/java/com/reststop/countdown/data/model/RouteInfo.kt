package com.reststop.countdown.data.model

/**
 * A route from a single Directions API call, fetched once at trip start purely for the "confirm
 * destination" preview and as the reference line POIs are measured against - the Navigation SDK
 * computes its own route separately for actual turn-by-turn guidance.
 *
 * [polyline] is the fully decoded path (every vertex of each step's own detailed polyline),
 * used both to draw the route preview on the map and as the reference line that all
 * points of interest are measured against.
 */
data class RouteInfo(
    val summary: String,
    val polyline: List<GeoPoint>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val destinationAddress: String,
    /**
     * Set only when this route was fetched with a secondary-destination waypoint: the
     * distance-along-route at which that stop is reached, i.e. where the first leg ends and the
     * route continues on to the real destination.
     */
    val waypointArrivalDistanceMeters: Double? = null,
)
