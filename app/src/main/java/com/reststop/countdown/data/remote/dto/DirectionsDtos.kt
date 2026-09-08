package com.reststop.countdown.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Response shape for the legacy Directions API (`maps.googleapis.com/maps/api/directions/json`). */
data class DirectionsResponseDto(
    @SerializedName("routes") val routes: List<RouteDto> = emptyList(),
    @SerializedName("status") val status: String = "",
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class RouteDto(
    @SerializedName("summary") val summary: String = "",
    @SerializedName("overview_polyline") val overviewPolyline: OverviewPolylineDto,
    @SerializedName("legs") val legs: List<LegDto> = emptyList(),
)

data class OverviewPolylineDto(
    @SerializedName("points") val points: String,
)

data class LegDto(
    @SerializedName("distance") val distance: ValueTextDto,
    @SerializedName("duration") val duration: ValueTextDto,
    @SerializedName("end_address") val endAddress: String = "",
    @SerializedName("steps") val steps: List<StepDto> = emptyList(),
)

data class ValueTextDto(
    @SerializedName("value") val value: Int,
    @SerializedName("text") val text: String,
)

/** One turn-by-turn maneuver within a leg. */
data class StepDto(
    @SerializedName("html_instructions") val htmlInstructions: String = "",
    @SerializedName("maneuver") val maneuver: String? = null,
    @SerializedName("distance") val distance: ValueTextDto,
    @SerializedName("start_location") val startLocation: DirectionsLatLngDto,
    // Each step carries its own detailed polyline (follows the actual road geometry for just
    // that segment) - much higher fidelity than the route's overall overview_polyline, which is
    // deliberately simplified and visibly cuts corners at high zoom.
    @SerializedName("polyline") val polyline: OverviewPolylineDto,
)

/** The legacy Directions API uses {"lat", "lng"} - distinct from Places (New)'s {"latitude", "longitude"}. */
data class DirectionsLatLngDto(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lng") val lng: Double,
)
