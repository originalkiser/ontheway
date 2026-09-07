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

/** Request body for Places API (New) `places:autocomplete`. */
data class AutocompleteRequestDto(
    @SerializedName("input") val input: String,
    @SerializedName("locationBias") val locationBias: LocationBiasDto? = null,
)

data class LocationBiasDto(
    @SerializedName("circle") val circle: CircleDto,
)

/** Response shape for Places API (New) `places:autocomplete`. */
data class AutocompleteResponseDto(
    @SerializedName("suggestions") val suggestions: List<SuggestionDto> = emptyList(),
)

data class SuggestionDto(
    @SerializedName("placePrediction") val placePrediction: PlacePredictionDto?,
)

data class PlacePredictionDto(
    @SerializedName("placeId") val placeId: String,
    @SerializedName("text") val text: FormattedTextDto?,
    @SerializedName("structuredFormat") val structuredFormat: StructuredFormatDto?,
)

data class StructuredFormatDto(
    @SerializedName("mainText") val mainText: FormattedTextDto?,
    @SerializedName("secondaryText") val secondaryText: FormattedTextDto?,
)

data class FormattedTextDto(
    @SerializedName("text") val text: String,
)
