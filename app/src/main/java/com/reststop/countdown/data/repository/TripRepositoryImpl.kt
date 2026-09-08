package com.reststop.countdown.data.repository

import com.reststop.countdown.data.model.GeoPoint
import com.reststop.countdown.data.model.PlaceSuggestion
import com.reststop.countdown.data.model.PoiCategory
import com.reststop.countdown.data.model.PointOfInterest
import com.reststop.countdown.data.model.RouteInfo
import com.reststop.countdown.data.model.RouteStep
import com.reststop.countdown.data.remote.DirectionsApiService
import com.reststop.countdown.data.remote.PlacesApiService
import com.reststop.countdown.data.remote.dto.CircleDto
import com.reststop.countdown.data.remote.dto.LatLngDto
import com.reststop.countdown.data.remote.dto.LocationBiasDto
import com.reststop.countdown.data.remote.dto.LocationRestrictionDto
import com.reststop.countdown.data.remote.dto.NearbySearchRequestDto
import com.reststop.countdown.data.remote.dto.PlaceDto
import com.reststop.countdown.data.remote.dto.TextSearchRequestDto
import com.reststop.countdown.domain.repository.TripRepository
import com.reststop.countdown.domain.util.HtmlText
import com.reststop.countdown.domain.util.PolylineDecoder
import com.reststop.countdown.domain.util.RoutePolylineIndex
import com.reststop.countdown.domain.util.RouteSampler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Fetches routing + place data over HTTP. Every public function here is called exactly once per
 * trip setup (see [TripRepository] docs) - all repeated, real-time distance work happens purely
 * on device in [com.reststop.countdown.domain.util.RoutePolylineIndex] and the location tracker,
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

    override suspend fun searchDestinations(query: String, locationBias: GeoPoint?): Result<List<PlaceSuggestion>> = runCatching {
        if (query.isBlank()) return@runCatching emptyList()

        val bias = locationBias?.let {
            LocationBiasDto(circle = CircleDto(center = LatLngDto(it.latitude, it.longitude), radiusMeters = 50_000.0))
        }
        placesApi.searchText(apiKey = apiKey, request = TextSearchRequestDto(textQuery = query, locationBias = bias))
            .places
            .map { place ->
                PlaceSuggestion(
                    placeId = place.id,
                    primaryText = place.displayName?.text ?: place.formattedAddress ?: "Unnamed place",
                    secondaryText = place.formattedAddress.orEmpty(),
                    location = GeoPoint(place.location.latitude, place.location.longitude),
                )
            }
    }

    override suspend fun fetchRoute(
        origin: GeoPoint,
        destinationQuery: String,
        destinationPlaceId: String?,
        waypointPlaceId: String?,
    ): Result<List<RouteInfo>> = runCatching {
        // A place_id destination is exact (works for business/landmark names); falls back to
        // geocoding the raw text when the user just typed and hit "Start Trip" without picking
        // a suggestion.
        val destination = destinationPlaceId?.let { "place_id:$it" } ?: destinationQuery
        val waypoints = waypointPlaceId?.let { "place_id:$it" }

        val response = directionsApi.getDirections(
            origin = "${origin.latitude},${origin.longitude}",
            destination = destination,
            apiKey = apiKey,
            // Google doesn't support route alternatives together with waypoints.
            alternatives = waypoints == null,
            waypoints = waypoints,
        )
        if (response.routes.isEmpty()) {
            error(response.errorMessage ?: "No route found for \"$destinationQuery\" (status ${response.status})")
        }

        response.routes.map { route ->
            if (route.legs.isEmpty()) error("Route had no legs")

            var cumulativeMeters = 0.0
            val steps = mutableListOf<RouteStep>()
            var waypointArrivalDistanceMeters: Double? = null

            route.legs.forEachIndexed { legIndex, leg ->
                leg.steps.forEach { step ->
                    steps += RouteStep(
                        instruction = HtmlText.strip(step.htmlInstructions),
                        maneuver = step.maneuver,
                        distanceMeters = step.distance.value,
                        location = GeoPoint(step.startLocation.lat, step.startLocation.lng),
                        distanceAlongRouteMeters = cumulativeMeters,
                    )
                    cumulativeMeters += step.distance.value
                }
                // A waypoint splits the route into 2+ legs; the end of the first leg is where
                // the secondary destination is reached.
                if (waypointPlaceId != null && legIndex == 0 && route.legs.size > 1) {
                    waypointArrivalDistanceMeters = cumulativeMeters
                }
            }

            val finalLeg = route.legs.last()
            RouteInfo(
                summary = route.summary.ifBlank { finalLeg.endAddress },
                polyline = PolylineDecoder.decode(route.overviewPolyline.points),
                distanceMeters = route.legs.sumOf { it.distance.value },
                durationSeconds = route.legs.sumOf { it.duration.value },
                destinationAddress = finalLeg.endAddress,
                steps = steps,
                waypointArrivalDistanceMeters = waypointArrivalDistanceMeters,
            )
        }
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
            // Keep only places genuinely on the way, not just near a distant sample circle -
            // each category has its own cutoff (rest areas are kept tight; see PoiCategory).
            .filter { it.distanceFromRouteMeters < category.maxLateralMeters }
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
