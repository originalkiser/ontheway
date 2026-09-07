package com.reststop.countdown.data.repository

import com.reststop.countdown.data.model.GeoPoint
import com.reststop.countdown.data.model.PoiCategory
import com.reststop.countdown.data.model.PointOfInterest
import com.reststop.countdown.data.model.RestStop
import com.reststop.countdown.data.model.RouteInfo
import com.reststop.countdown.data.remote.DirectionsApiService
import com.reststop.countdown.data.remote.PlacesApiService
import com.reststop.countdown.data.remote.dto.CircleDto
import com.reststop.countdown.data.remote.dto.LatLngDto
import com.reststop.countdown.data.remote.dto.LocationRestrictionDto
import com.reststop.countdown.data.remote.dto.NearbySearchRequestDto
import com.reststop.countdown.data.remote.dto.PlaceDto
import com.reststop.countdown.domain.repository.TripRepository
import com.reststop.countdown.domain.util.PolylineDecoder
import com.reststop.countdown.domain.util.RoutePolylineIndex
import com.reststop.countdown.domain.util.RouteSampler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Fetches routing + place data over HTTP. Every public function here is called exactly once per
 * trip (see [TripRepository] docs) - all repeated, real-time distance work happens purely on
 * device in [com.reststop.countdown.domain.util.RoutePolylineIndex] and the location tracker,
 * with zero further network traffic.
 */
class TripRepositoryImpl(
    private val directionsApi: DirectionsApiService,
    private val placesApi: PlacesApiService,
    private val apiKey: String,
    /** Spacing between Places API search circles used to cover the whole route corridor. */
    private val routeSampleIntervalMeters: Double = 40_000.0,
    /** Nearby Search circle radius per sample point. Google's max is 50,000m. */
    private val searchRadiusMeters: Double = 25_000.0,
) : TripRepository {

    override suspend fun fetchRoute(origin: GeoPoint, destinationQuery: String): Result<RouteInfo> = runCatching {
        val response = directionsApi.getDirections(
            origin = "${origin.latitude},${origin.longitude}",
            destination = destinationQuery,
            apiKey = apiKey,
        )
        val route = response.routes.firstOrNull()
            ?: error(response.errorMessage ?: "No route found for \"$destinationQuery\" (status ${response.status})")
        val leg = route.legs.firstOrNull() ?: error("Route had no legs")

        RouteInfo(
            polyline = PolylineDecoder.decode(route.overviewPolyline.points),
            distanceMeters = leg.distance.value,
            durationSeconds = leg.duration.value,
            destinationAddress = leg.endAddress,
        )
    }

    override suspend fun fetchRestStops(routePolyline: List<GeoPoint>): Result<List<RestStop>> = runCatching {
        val routeIndex = RoutePolylineIndex.build(routePolyline)
        val places = searchAlongRoute(routePolyline, includedTypes = listOf("rest_stop"))

        places
            .distinctBy { it.id }
            .map { place ->
                val location = GeoPoint(place.location.latitude, place.location.longitude)
                val projection = routeIndex.project(location)
                RestStop(
                    placeId = place.id,
                    name = place.displayName?.text ?: "Rest Stop",
                    location = location,
                    distanceAlongRouteMeters = projection.distanceAlongRouteMeters,
                )
            }
            // Keep only places genuinely near the highway, not just near a distant sample circle.
            .filter { restStop -> routeIndex.project(restStop.location).lateralDistanceMeters < searchRadiusMeters }
            .sortedBy { it.distanceAlongRouteMeters }
    }

    override suspend fun fetchPointsOfInterest(
        routePolyline: List<GeoPoint>,
        category: PoiCategory,
    ): Result<List<PointOfInterest>> = runCatching {
        val routeIndex = RoutePolylineIndex.build(routePolyline)
        val places = searchAlongRoute(routePolyline, includedTypes = listOf(category.placesApiType))

        places
            .distinctBy { it.id }
            .map { place ->
                val location = GeoPoint(place.location.latitude, place.location.longitude)
                val projection = routeIndex.project(location)
                PointOfInterest(
                    placeId = place.id,
                    name = place.displayName?.text ?: category.displayName,
                    category = category,
                    location = location,
                    distanceFromRouteMeters = projection.lateralDistanceMeters,
                    distanceAlongRouteMeters = projection.distanceAlongRouteMeters,
                )
            }
            .filter { it.distanceFromRouteMeters < searchRadiusMeters }
            // "sorted by distance from the route's polyline"
            .sortedBy { it.distanceFromRouteMeters }
    }

    /**
     * Issues one Nearby Search per sample point along the route (all in parallel), covering the
     * whole corridor with a bounded number of calls. This whole sweep runs once, at trip setup.
     */
    private suspend fun searchAlongRoute(routePolyline: List<GeoPoint>, includedTypes: List<String>): List<PlaceDto> = coroutineScope {
        val samplePoints = RouteSampler.sampleEvery(routePolyline, routeSampleIntervalMeters)
        samplePoints
            .map { sample ->
                async {
                    val result: Result<List<PlaceDto>> = runCatching {
                        placesApi.searchNearby(
                            apiKey = apiKey,
                            request = NearbySearchRequestDto(
                                includedTypes = includedTypes,
                                maxResultCount = 10,
                                locationRestriction = LocationRestrictionDto(
                                    circle = CircleDto(
                                        center = LatLngDto(sample.latitude, sample.longitude),
                                        radiusMeters = searchRadiusMeters,
                                    ),
                                ),
                            ),
                        ).places
                    }
                    result.getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()
    }
}
