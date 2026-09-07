package com.reststop.countdown.data.model

/**
 * The single Directions API result fetched once at trip start.
 *
 * [polyline] is the fully decoded path (every vertex of the overview polyline),
 * used both to draw the route on the map and as the reference line that all
 * rest stops / points of interest are measured against.
 */
data class RouteInfo(
    val polyline: List<GeoPoint>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val destinationAddress: String,
)
