package com.reststop.countdown.ui

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reststop.countdown.data.model.GeoPoint
import com.reststop.countdown.data.model.PlaceSuggestion
import com.reststop.countdown.data.model.PoiCategory
import com.reststop.countdown.data.model.PointOfInterest
import com.reststop.countdown.data.model.RouteInfo
import com.reststop.countdown.data.model.RouteStep
import com.reststop.countdown.domain.repository.TripRepository
import com.reststop.countdown.domain.util.RoutePolylineIndex
import com.reststop.countdown.location.LocationTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the whole trip lifecycle:
 *  1. [onDestinationChanged] debounces destination search as the user types.
 *  2. [startTrip] fetches route alternatives; [selectRoute] (or auto-selection when there's only
 *     one) then enters driving mode. [setSecondaryDestination] later reroutes through a chosen
 *     POI as a waypoint - the one other network call allowed mid-trip, since it's a deliberate
 *     driver action, not a background poll.
 *  3. [beginLocationUpdates] then drives everything else purely off local GPS fixes - the
 *     turn-by-turn banner, ETA, camera bearing, and POI proximity - with no further network calls.
 */
class MainViewModel(
    private val tripRepository: TripRepository,
    private val locationTracker: LocationTracker,
) : ViewModel() {

    companion object {
        /** Small buffer so GPS jitter right at a waypoint's route position doesn't flip-flop it. */
        private const val PASS_TOLERANCE_METERS = 50.0

        /** Only surface a POI as "upcoming" once it's within this many meters of the drive. */
        private const val UPCOMING_POI_MAX_LATERAL_METERS = 3_000.0

        /** How many nearest not-yet-passed places to surface per checked category. */
        private const val UPCOMING_POIS_PER_CATEGORY = 2

        /** Debounce so destination search doesn't fire a request on every single keystroke. */
        private const val SEARCH_DEBOUNCE_MILLIS = 300L

        /** Below this speed, a GPS fix's reported bearing is too noisy to trust for the camera. */
        private const val MIN_SPEED_FOR_BEARING_MPS = 1.0f
    }

    private val _uiState = MutableStateFlow(TripUiState())
    val uiState: StateFlow<TripUiState> = _uiState.asStateFlow()

    private var routeIndex: RoutePolylineIndex? = null
    private var routeSteps: List<RouteStep> = emptyList()
    private var routeTotalDistanceMeters: Int = 0
    private var routeTotalDurationSeconds: Int = 0
    private var locationJob: Job? = null
    private var suggestionsJob: Job? = null
    private var pendingOrigin: GeoPoint? = null
    private var previousLocation: Location? = null
    private var originalDestinationQuery: String = ""
    private var originalDestinationPlaceId: String? = null

    fun onPermissionResult(granted: Boolean) {
        val wasGranted = _uiState.value.hasLocationPermission
        _uiState.update { it.copy(hasLocationPermission = granted) }
        if (granted && !wasGranted) {
            centerMapOnUserLocationOnce()
        }
    }

    /** One-shot fix so the map isn't sitting on (0,0) before a trip starts. Never repeated. */
    private fun centerMapOnUserLocationOnce() {
        viewModelScope.launch {
            val location = locationTracker.awaitCurrentLocation() ?: return@launch
            _uiState.update { it.copy(initialCameraTarget = GeoPoint(location.latitude, location.longitude)) }
        }
    }

    fun onDestinationChanged(text: String) {
        _uiState.update {
            it.copy(destinationInput = text, selectedDestinationPlaceId = null, errorMessage = null)
        }

        suggestionsJob?.cancel()
        if (text.isBlank()) {
            _uiState.update { it.copy(destinationSuggestions = emptyList()) }
            return
        }

        suggestionsJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            val bias = _uiState.value.currentLocation ?: _uiState.value.initialCameraTarget
            tripRepository.searchDestinations(text, bias)
                .onSuccess { results ->
                    _uiState.update { it.copy(destinationSuggestions = results, destinationResultsExpanded = true) }
                }
        }
    }

    fun toggleDestinationResultsExpanded() {
        _uiState.update { it.copy(destinationResultsExpanded = !it.destinationResultsExpanded) }
    }

    /** User tapped a result instead of typing a raw address - searches by name work too. */
    fun selectSuggestion(suggestion: PlaceSuggestion) {
        suggestionsJob?.cancel()
        val displayText = if (suggestion.secondaryText.isNotBlank()) {
            "${suggestion.primaryText}, ${suggestion.secondaryText}"
        } else {
            suggestion.primaryText
        }
        _uiState.update {
            it.copy(
                destinationInput = displayText,
                selectedDestinationPlaceId = suggestion.placeId,
                destinationSuggestions = emptyList(),
            )
        }
    }

    fun toggleDistanceUnit() {
        _uiState.update {
            it.copy(distanceUnit = if (it.distanceUnit == DistanceUnit.MILES) DistanceUnit.KILOMETERS else DistanceUnit.MILES)
        }
    }

    /** Flips between the tilted, bearing-following driving view and a flat north-up overview. */
    fun toggleDrivingMode() {
        _uiState.update { it.copy(drivingMode = !it.drivingMode) }
    }

    fun toggleCategoryMenu() {
        _uiState.update { it.copy(isCategoryMenuOpen = !it.isCategoryMenuOpen) }
    }

    /** Expands/collapses a category's floating chip to show its individual results. */
    fun toggleCategoryChipExpanded(category: PoiCategory) {
        _uiState.update { it.copy(expandedCategoryChip = if (it.expandedCategoryChip == category) null else category) }
    }

    /** Fetches route alternatives. If there's more than one, waits for [selectRoute]; otherwise proceeds immediately. */
    fun startTrip() {
        val destination = _uiState.value.destinationInput.trim()
        if (destination.isEmpty() || _uiState.value.isLoadingTrip) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTrip = true, errorMessage = null) }

            val currentLocation = locationTracker.awaitCurrentLocation()
            if (currentLocation == null) {
                _uiState.update { it.copy(isLoadingTrip = false, errorMessage = "Couldn't determine your current location.") }
                return@launch
            }
            val origin = GeoPoint(currentLocation.latitude, currentLocation.longitude)
            pendingOrigin = origin
            originalDestinationQuery = destination
            originalDestinationPlaceId = _uiState.value.selectedDestinationPlaceId

            val routes = tripRepository.fetchRoute(origin, destination, originalDestinationPlaceId)
                .getOrElse { throwable ->
                    _uiState.update { it.copy(isLoadingTrip = false, errorMessage = throwable.message ?: "Failed to fetch route.") }
                    return@launch
                }

            if (routes.size == 1) {
                proceedWithRoute(routes.first(), origin, waypoint = null)
            } else {
                _uiState.update {
                    it.copy(isLoadingTrip = false, routeOptions = routes, awaitingRouteSelection = true)
                }
            }
        }
    }

    /** User picked one of several route alternatives. */
    fun selectRoute(route: RouteInfo) {
        val origin = pendingOrigin ?: _uiState.value.currentLocation ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTrip = true, awaitingRouteSelection = false, routeOptions = emptyList()) }
            proceedWithRoute(route, origin, waypoint = null)
        }
    }

    /**
     * A driver-initiated detour: reroutes through [poi] before continuing to the original
     * destination. One extra Directions call (deliberate, not a background poll) - after this,
     * tracking goes right back to zero further network calls until the driver changes their mind.
     */
    fun setSecondaryDestination(poi: PointOfInterest) {
        val origin = _uiState.value.currentLocation ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isReroutingToWaypoint = true, expandedCategoryChip = null, errorMessage = null) }
            val routes = tripRepository.fetchRoute(origin, originalDestinationQuery, originalDestinationPlaceId, poi.placeId)
                .getOrElse { throwable ->
                    _uiState.update { it.copy(isReroutingToWaypoint = false, errorMessage = throwable.message ?: "Couldn't route via ${poi.name}.") }
                    return@launch
                }
            proceedWithRoute(routes.first(), origin, waypoint = poi)
        }
    }

    /** Drops the secondary stop and reroutes straight back to the original destination. */
    fun clearSecondaryDestination() {
        val origin = _uiState.value.currentLocation ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isReroutingToWaypoint = true, errorMessage = null) }
            val routes = tripRepository.fetchRoute(origin, originalDestinationQuery, originalDestinationPlaceId)
                .getOrElse { throwable ->
                    _uiState.update { it.copy(isReroutingToWaypoint = false, errorMessage = throwable.message ?: "Couldn't reroute.") }
                    return@launch
                }
            proceedWithRoute(routes.first(), origin, waypoint = null)
        }
    }

    private suspend fun proceedWithRoute(route: RouteInfo, origin: GeoPoint, waypoint: PointOfInterest?) {
        val wasAlreadyActive = _uiState.value.tripActive
        val newIndex = RoutePolylineIndex.build(route.polyline)

        // Re-project any already-fetched POIs onto the new polyline (e.g. after a reroute) -
        // purely local geometry, no new Places calls.
        val reprojectedPoiByCategory = _uiState.value.poiByCategory.mapValues { (_, pois) ->
            pois.map { poi ->
                val projection = newIndex.project(poi.location)
                poi.copy(distanceAlongRouteMeters = projection.distanceAlongRouteMeters, distanceFromRouteMeters = projection.lateralDistanceMeters)
            }
        }

        routeIndex = newIndex
        routeSteps = route.steps
        routeTotalDistanceMeters = route.distanceMeters
        routeTotalDurationSeconds = route.durationSeconds

        _uiState.update {
            it.copy(
                isLoadingTrip = false,
                isReroutingToWaypoint = false,
                tripActive = true,
                drivingMode = if (wasAlreadyActive) it.drivingMode else true,
                routeOptions = emptyList(),
                awaitingRouteSelection = false,
                routePolyline = route.polyline,
                currentLocation = origin,
                selectedWaypoint = waypoint,
                waypointArrivalDistanceMeters = route.waypointArrivalDistanceMeters,
                upcomingStep = route.steps.getOrNull(1),
                distanceToManeuverMeters = route.steps.getOrNull(1)?.distanceAlongRouteMeters,
                poiByCategory = reprojectedPoiByCategory,
            )
        }

        if (locationJob == null) beginLocationUpdates()
    }

    /** Toggles a POI category checkbox, fetching it once (then caching) if newly checked. */
    fun toggleCategory(category: PoiCategory) {
        val alreadySelected = category in _uiState.value.selectedCategories
        if (alreadySelected) {
            _uiState.update { it.copy(selectedCategories = it.selectedCategories - category) }
            return
        }
        _uiState.update { it.copy(selectedCategories = it.selectedCategories + category) }

        val alreadyCached = _uiState.value.poiByCategory.containsKey(category)
        val polyline = _uiState.value.routePolyline
        if (alreadyCached || polyline.isEmpty()) {
            recomputeUpcomingPois()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loadingCategory = category) }
            tripRepository.fetchPointsOfInterest(polyline, category)
                .onSuccess { pois ->
                    _uiState.update { it.copy(poiByCategory = it.poiByCategory + (category to pois), loadingCategory = null) }
                    recomputeUpcomingPois()
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(loadingCategory = null, errorMessage = throwable.message ?: "Couldn't load ${category.displayName}.")
                    }
                }
        }
    }

    private fun beginLocationUpdates() {
        locationJob?.cancel()
        locationJob = locationTracker.observeLocationUpdates()
            .onEach(::handleLocationUpdate)
            .launchIn(viewModelScope)
    }

    /**
     * The real-time countdown + turn-by-turn loop. Runs entirely on-device against the cached
     * route/POI data fetched once in [proceedWithRoute] - no Directions/Places calls happen here.
     */
    private fun handleLocationUpdate(location: Location) {
        val index = routeIndex ?: return
        val currentPoint = GeoPoint(location.latitude, location.longitude)
        val projection = index.project(currentPoint)

        val bearing = resolveBearing(location)
        previousLocation = location

        val currentStepIndex = routeSteps.indexOfLast { it.distanceAlongRouteMeters <= projection.distanceAlongRouteMeters }
            .coerceAtLeast(0)
        val upcomingStep = routeSteps.getOrNull(currentStepIndex + 1)
        val distanceToManeuverMeters = when {
            upcomingStep != null -> (upcomingStep.distanceAlongRouteMeters - projection.distanceAlongRouteMeters).coerceAtLeast(0.0)
            routeSteps.isNotEmpty() -> (index.totalLengthMeters - projection.distanceAlongRouteMeters).coerceAtLeast(0.0)
            else -> null
        }
        // On the final step there's no "next" maneuver - synthesize an arrival instruction so the
        // banner has something consistent to render instead of special-casing null in the UI.
        val displayedStep = upcomingStep ?: if (routeSteps.isNotEmpty()) {
            RouteStep(
                instruction = "Arrive at your destination",
                maneuver = null,
                distanceMeters = 0,
                location = currentPoint,
                distanceAlongRouteMeters = index.totalLengthMeters,
            )
        } else {
            null
        }

        val remainingDistanceMeters = (routeTotalDistanceMeters - projection.distanceAlongRouteMeters).coerceAtLeast(0.0)
        val remainingFraction = if (routeTotalDistanceMeters > 0) remainingDistanceMeters / routeTotalDistanceMeters else 0.0
        val remainingDurationSeconds = routeTotalDurationSeconds * remainingFraction
        val etaEpochMillis = System.currentTimeMillis() + (remainingDurationSeconds * 1000).toLong()

        _uiState.update { current ->
            // Once the driver has reached (or driven past) the secondary stop, drop it and go
            // back to normal main-destination guidance - the route itself already continues on
            // through the same cached steps, this just clears the "via" banner.
            val waypointPassed = current.waypointArrivalDistanceMeters != null &&
                projection.distanceAlongRouteMeters > current.waypointArrivalDistanceMeters + PASS_TOLERANCE_METERS

            current.copy(
                currentLocation = currentPoint,
                currentBearingDegrees = bearing,
                currentProgressMeters = projection.distanceAlongRouteMeters,
                remainingDistanceMeters = remainingDistanceMeters,
                etaEpochMillis = etaEpochMillis,
                upcomingStep = displayedStep,
                distanceToManeuverMeters = distanceToManeuverMeters,
                selectedWaypoint = if (waypointPassed) null else current.selectedWaypoint,
                waypointArrivalDistanceMeters = if (waypointPassed) null else current.waypointArrivalDistanceMeters,
                upcomingPoisByCategory = nearestUpcomingPoisPerCategory(current, projection.distanceAlongRouteMeters),
            )
        }
    }

    /** GPS-reported bearing when moving fast enough to trust it; falls back to fix-to-fix bearing, then holds the last known heading. */
    private fun resolveBearing(location: Location): Float {
        if (location.hasBearing() && location.speed >= MIN_SPEED_FOR_BEARING_MPS) {
            return location.bearing
        }
        val previous = previousLocation
        if (previous != null && previous.distanceTo(location) > 5f) {
            return previous.bearingTo(location)
        }
        return _uiState.value.currentBearingDegrees
    }

    /** Picks, per selected category, the nearest not-yet-passed places - a lightweight "loose" nav hint. */
    private fun nearestUpcomingPoisPerCategory(state: TripUiState, currentProgressMeters: Double): Map<PoiCategory, List<PointOfInterest>> =
        state.selectedCategories.mapNotNull { category ->
            val nearest = state.poiByCategory[category].orEmpty()
                .filter { it.distanceAlongRouteMeters >= currentProgressMeters && it.distanceFromRouteMeters <= UPCOMING_POI_MAX_LATERAL_METERS }
                .sortedBy { it.distanceAlongRouteMeters }
                .take(UPCOMING_POIS_PER_CATEGORY)
            if (nearest.isEmpty()) null else category to nearest
        }.toMap()

    private fun recomputeUpcomingPois() {
        val current = _uiState.value
        _uiState.update { it.copy(upcomingPoisByCategory = nearestUpcomingPoisPerCategory(current, current.currentProgressMeters)) }
    }

    override fun onCleared() {
        locationJob?.cancel()
        suggestionsJob?.cancel()
    }

    class Factory(
        private val tripRepository: TripRepository,
        private val locationTracker: LocationTracker,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(tripRepository, locationTracker) as T
    }
}
