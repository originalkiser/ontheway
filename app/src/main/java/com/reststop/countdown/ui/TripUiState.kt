package com.reststop.countdown.ui

import com.reststop.countdown.data.model.GeoPoint
import com.reststop.countdown.data.model.PlaceSuggestion
import com.reststop.countdown.data.model.PoiCategory
import com.reststop.countdown.data.model.PointOfInterest
import com.reststop.countdown.data.model.RouteInfo

enum class DistanceUnit(val label: String) {
    MILES("mi"),
    KILOMETERS("km"),
}

/**
 * Everything the Compose UI needs to render, in one immutable snapshot. Produced by
 * [MainViewModel] from the one-time API fetch + local GPS math for the POI corridor - the actual
 * turn-by-turn driving experience (route line, instruction banner, ETA, voice guidance) is drawn
 * natively by the Navigation SDK itself and isn't part of this state. Nothing here triggers
 * further network calls when it changes (destination search is the one exception, driven
 * directly by text input).
 */
data class TripUiState(
    val hasLocationPermission: Boolean = false,
    val initialCameraTarget: GeoPoint? = null,

    val destinationInput: String = "",
    val destinationSuggestions: List<PlaceSuggestion> = emptyList(),
    val destinationResultsExpanded: Boolean = true,
    val selectedDestinationPlaceId: String? = null,
    val isLoadingTrip: Boolean = false,
    val errorMessage: String? = null,

    /** A single fetched route awaiting a deliberate "Start Trip" tap on the confirm screen. */
    val pendingRoute: RouteInfo? = null,

    val tripActive: Boolean = false,
    val routePolyline: List<GeoPoint> = emptyList(),
    val currentLocation: GeoPoint? = null,
    val currentProgressMeters: Double = 0.0,

    /** A secondary stop the driver chose to route through before the real destination. */
    val selectedWaypoint: PointOfInterest? = null,
    val waypointArrivalDistanceMeters: Double? = null,
    val isReroutingToWaypoint: Boolean = false,

    val isCategoryMenuOpen: Boolean = false,
    val selectedCategories: Set<PoiCategory> = emptySet(),
    val loadingCategory: PoiCategory? = null,
    val poiByCategory: Map<PoiCategory, List<PointOfInterest>> = emptyMap(),
    /** Up to the 2 nearest not-yet-passed places per selected category. */
    val upcomingPoisByCategory: Map<PoiCategory, List<PointOfInterest>> = emptyMap(),
    /** Which category's floating chip is expanded to show its full result list right now, if any. */
    val expandedCategoryChip: PoiCategory? = null,

    val distanceUnit: DistanceUnit = DistanceUnit.MILES,
) {
    /** All checked categories' places, already sorted by distance from the route polyline. */
    val visiblePois: List<PointOfInterest>
        get() = selectedCategories.flatMap { poiByCategory[it].orEmpty() }
            .sortedBy { it.distanceFromRouteMeters }
}
