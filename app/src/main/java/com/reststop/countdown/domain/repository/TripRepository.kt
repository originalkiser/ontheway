package com.reststop.countdown.domain.repository

import com.reststop.countdown.data.model.GeoPoint
import com.reststop.countdown.data.model.PlaceSuggestion
import com.reststop.countdown.data.model.PoiCategory
import com.reststop.countdown.data.model.PointOfInterest
import com.reststop.countdown.data.model.RestStop
import com.reststop.countdown.data.model.RouteInfo

/**
 * All network access for a trip lives behind this interface. [fetchRoute], [fetchRestStops],
 * and [fetchPointsOfInterest] are each intended to be invoked exactly once per trip (route +
 * rest stops at setup, and at most once per POI category the user checks) - the ViewModel never
 * calls these again once live tracking starts. [autocomplete] is the one exception, called
 * repeatedly (debounced) as the user types a destination.
 */
interface TripRepository {

    /** Destination-search suggestions as the user types - matches business/landmark names too, not just addresses. */
    suspend fun autocomplete(query: String, locationBias: GeoPoint?): Result<List<PlaceSuggestion>>

    /**
     * Fetches candidate routes (with alternatives) from [origin] to a destination, given either
     * as a Places [destinationPlaceId] (preferred - exact, works for names/landmarks) or a raw
     * [destinationQuery] address/text.
     */
    suspend fun fetchRoute(
        origin: GeoPoint,
        destinationQuery: String,
        destinationPlaceId: String? = null,
    ): Result<List<RouteInfo>>

    suspend fun fetchRestStops(routePolyline: List<GeoPoint>): Result<List<RestStop>>

    suspend fun fetchPointsOfInterest(
        routePolyline: List<GeoPoint>,
        category: PoiCategory,
    ): Result<List<PointOfInterest>>
}
