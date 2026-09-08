package com.reststop.countdown.data.model

/**
 * One turn-by-turn maneuver along a selected route. [distanceAlongRouteMeters] is where this
 * step *begins* (cumulative distance from the origin), matched against live GPS progress the
 * same way rest stops are, to drive the turn-by-turn banner locally with no extra network calls.
 */
data class RouteStep(
    val instruction: String,
    val maneuver: String?,
    val distanceMeters: Int,
    val location: GeoPoint,
    val distanceAlongRouteMeters: Double,
)
