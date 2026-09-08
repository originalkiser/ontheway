package com.reststop.countdown.domain.repository

import com.reststop.countdown.data.model.GeoPoint
import com.reststop.countdown.data.model.PlaceSuggestion
import com.reststop.countdown.data.model.PoiCategory
import com.reststop.countdown.data.model.PointOfInterest
import com.reststop.countdown.data.model.RouteInfo

/**
 * All network access for a trip lives behind this interface. [fetchRoute] and
 * [fetchPointsOfInterest] are each intended to be invoked exactly once per trip setup (once per
 * POI category the user checks, plus once more if the user sets or clears a secondary
 * destination) - the ViewModel never calls these on every GPS tick. [searchDestinations] is the
 * one exception, called repeatedly (debounced) as the user types a destination.
 */
interface TripRepository {

    /** Destination-search results as the user types - matches business/landmark names too, not just addresses, and carries each result's location so it can be pinned on the map. */
    suspend fun searchDestinations(query: String, locationBias: GeoPoint?): Result<List<PlaceSuggestion>>

    /**
     * Fetches candidate routes (with alternatives) from [origin] to a destination, given either
     * as a Places [destinationPlaceId] (preferred - exact, works for names/landmarks) or a raw
     * [destinationQuery] address/text. [waypointPlaceId], when set, routes through that place
     * first (a "secondary destination") before continuing to the real destination - the response
     * then has more than one leg, which the caller must concatenate.
     */
    suspend fun fetchRoute(
        origin: GeoPoint,
        destinationQuery: String,
        destinationPlaceId: String? = null,
        waypointPlaceId: String? = null,
    ): Result<List<RouteInfo>>

    suspend fun fetchPointsOfInterest(
        routePolyline: List<GeoPoint>,
        category: PoiCategory,
    ): Result<List<PointOfInterest>>
}
