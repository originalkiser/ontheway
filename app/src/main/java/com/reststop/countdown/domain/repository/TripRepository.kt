package com.reststop.countdown.domain.repository

import com.reststop.countdown.data.model.GeoPoint
import com.reststop.countdown.data.model.PoiCategory
import com.reststop.countdown.data.model.PointOfInterest
import com.reststop.countdown.data.model.RestStop
import com.reststop.countdown.data.model.RouteInfo

/**
 * All network access for a trip lives behind this interface. Every method is intended to be
 * invoked exactly once per trip (route + rest stops at setup, and at most once per POI category
 * the user checks) - the ViewModel never calls these again once live tracking starts.
 */
interface TripRepository {

    suspend fun fetchRoute(origin: GeoPoint, destinationQuery: String): Result<RouteInfo>

    suspend fun fetchRestStops(routePolyline: List<GeoPoint>): Result<List<RestStop>>

    suspend fun fetchPointsOfInterest(
        routePolyline: List<GeoPoint>,
        category: PoiCategory,
    ): Result<List<PointOfInterest>>
}
