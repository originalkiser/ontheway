package com.reststop.countdown.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Response shape for the legacy Directions API (`maps.googleapis.com/maps/api/directions/json`). */
data class DirectionsResponseDto(
    @SerializedName("routes") val routes: List<RouteDto> = emptyList(),
    @SerializedName("status") val status: String = "",
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class RouteDto(
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
)

data class ValueTextDto(
    @SerializedName("value") val value: Int,
    @SerializedName("text") val text: String,
)
