package com.reststop.countdown.data.model

/**
 * One candidate route from a single Directions API call fetched once at trip start (the API is
 * asked for alternatives, so more than one of these can come back - the user picks one before
 * rest-stop/POI search proceeds against its polyline).
 *
 * [polyline] is the fully decoded path (every vertex of the overview polyline),
 * used both to draw the route on the map and as the reference line that all
 * rest stops / points of interest are measured against.
 */
data class RouteInfo(
    val summary: String,
    val polyline: List<GeoPoint>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val destinationAddress: String,
    /** Turn-by-turn maneuvers, in route order - drives the driving-mode instruction banner. */
    val steps: List<RouteStep> = emptyList(),
)
