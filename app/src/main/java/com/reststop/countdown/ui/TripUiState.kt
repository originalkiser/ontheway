package com.reststop.countdown.ui

import com.reststop.countdown.data.model.GeoPoint
import com.reststop.countdown.data.model.PlaceSuggestion
import com.reststop.countdown.data.model.PoiCategory
import com.reststop.countdown.data.model.PointOfInterest
import com.reststop.countdown.data.model.RestStop
import com.reststop.countdown.data.model.RouteInfo

enum class DistanceUnit(val label: String) {
    MILES("mi"),
    KILOMETERS("km"),
}

/**
 * Everything the Compose UI needs to render, in one immutable snapshot. Produced by
 * [MainViewModel] purely from the one-time API fetch + local GPS math - nothing here triggers
 * further network calls when it changes (destination autocomplete is the one exception, driven
 * directly by text input).
 */
data class TripUiState(
    val hasLocationPermission: Boolean = false,
    val initialCameraTarget: GeoPoint? = null,

    val destinationInput: String = "",
    val destinationSuggestions: List<PlaceSuggestion> = emptyList(),
    val selectedDestinationPlaceId: String? = null,
    val isLoadingTrip: Boolean = false,
    val errorMessage: String? = null,

    val routeOptions: List<RouteInfo> = emptyList(),
    val awaitingRouteSelection: Boolean = false,

    val tripActive: Boolean = false,
    val routePolyline: List<GeoPoint> = emptyList(),
    val currentLocation: GeoPoint? = null,
    val currentProgressMeters: Double = 0.0,

    val upcomingRestStops: List<RestStop> = emptyList(),
    val passedRestStops: List<RestStop> = emptyList(),
    val nextRestStop: RestStop? = null,
    val distanceToNextRestStopMeters: Double? = null,

    val selectedCategories: Set<PoiCategory> = emptySet(),
    val loadingCategory: PoiCategory? = null,
    val poiByCategory: Map<PoiCategory, List<PointOfInterest>> = emptyMap(),
    val upcomingPoisByCategory: Map<PoiCategory, PointOfInterest> = emptyMap(),

    val distanceUnit: DistanceUnit = DistanceUnit.MILES,
) {
    /** All checked categories' places, already sorted by distance from the route polyline. */
    val visiblePois: List<PointOfInterest>
        get() = selectedCategories.flatMap { poiByCategory[it].orEmpty() }
            .sortedBy { it.distanceFromRouteMeters }
}
