package com.reststop.countdown.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Request body for Places API (New) `places:searchNearby`. */
data class NearbySearchRequestDto(
    @SerializedName("includedTypes") val includedTypes: List<String>,
    @SerializedName("maxResultCount") val maxResultCount: Int = 10,
    @SerializedName("locationRestriction") val locationRestriction: LocationRestrictionDto,
)

data class LocationRestrictionDto(
    @SerializedName("circle") val circle: CircleDto,
)

data class CircleDto(
    @SerializedName("center") val center: LatLngDto,
    @SerializedName("radius") val radiusMeters: Double,
)

data class LatLngDto(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
)

/** Response shape for Places API (New) `places:searchNearby`. */
data class NearbySearchResponseDto(
    @SerializedName("places") val places: List<PlaceDto> = emptyList(),
)

data class PlaceDto(
    @SerializedName("id") val id: String,
    @SerializedName("displayName") val displayName: DisplayNameDto?,
    @SerializedName("location") val location: LatLngDto,
)

data class DisplayNameDto(
    @SerializedName("text") val text: String,
)
