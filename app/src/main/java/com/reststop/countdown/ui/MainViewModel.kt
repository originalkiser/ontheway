package com.reststop.countdown.ui

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reststop.countdown.data.model.GeoPoint
import com.reststop.countdown.data.model.PlaceSuggestion
import com.reststop.countdown.data.model.PoiCategory
import com.reststop.countdown.data.model.PointOfInterest
import com.reststop.countdown.data.model.RestStop
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
 *  1. [onDestinationChanged] debounces destination-search autocomplete as the user types.
 *  2. [startTrip] fetches route alternatives; [selectRoute] (or auto-selection when there's only
 *     one) then makes the one-and-only Places sweep for rest stops and enters driving mode.
 *  3. [beginLocationUpdates] then drives everything else purely off local GPS fixes - the
 *     turn-by-turn banner, the camera bearing, rest-stop countdown/auto-advance, and POI
 *     proximity - with no further network calls.
 */
class MainViewModel(
    private val tripRepository: TripRepository,
    private val locationTracker: LocationTracker,
) : ViewModel() {

    companion object {
        /** Auto-advance when within this radius of the targeted rest stop (spec: 200-300m). */
        private const val ARRIVAL_RADIUS_METERS = 250.0

        /** Small buffer so GPS jitter right at a stop's route position doesn't flip-flop the queue. */
        private const val PASS_TOLERANCE_METERS = 50.0

        /** Only surface a POI as "upcoming" once it's within this many meters of the drive. */
        private const val UPCOMING_POI_MAX_LATERAL_METERS = 3_000.0

        /** How many nearest not-yet-passed places to surface per checked category. */
        private const val UPCOMING_POIS_PER_CATEGORY = 2

        /** Debounce so autocomplete doesn't fire a request on every single keystroke. */
        private const val AUTOCOMPLETE_DEBOUNCE_MILLIS = 300L

        /** Below this speed, a GPS fix's reported bearing is too noisy to trust for the camera. */
        private const val MIN_SPEED_FOR_BEARING_MPS = 1.0f
    }

    private val _uiState = MutableStateFlow(TripUiState())
    val uiState: StateFlow<TripUiState> = _uiState.asStateFlow()

    private var routeIndex: RoutePolylineIndex? = null
    private var routeSteps: List<RouteStep> = emptyList()
    private val restStopQueue = ArrayDeque<RestStop>()
    private var locationJob: Job? = null
    private var suggestionsJob: Job? = null
    private var pendingOrigin: GeoPoint? = null
    private var previousLocation: Location? = null

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
            delay(AUTOCOMPLETE_DEBOUNCE_MILLIS)
            val bias = _uiState.value.currentLocation ?: _uiState.value.initialCameraTarget
            tripRepository.autocomplete(text, bias)
                .onSuccess { suggestions -> _uiState.update { it.copy(destinationSuggestions = suggestions) } }
        }
    }

    /** User tapped a suggestion instead of typing a raw address - searches by name work too. */
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

            val routes = tripRepository.fetchRoute(origin, destination, _uiState.value.selectedDestinationPlaceId)
                .getOrElse { throwable ->
                    _uiState.update { it.copy(isLoadingTrip = false, errorMessage = throwable.message ?: "Failed to fetch route.") }
                    return@launch
                }

            if (routes.size == 1) {
                proceedWithRoute(routes.first(), origin)
            } else {
                _uiState.update {
                    it.copy(isLoadingTrip = false, routeOptions = routes, awaitingRouteSelection = true)
                }
            }
        }
    }

    /** User picked one of several route alternatives - now do the one-time rest-stop Places sweep and go live. */
    fun selectRoute(route: RouteInfo) {
        val origin = pendingOrigin ?: _uiState.value.currentLocation ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTrip = true, awaitingRouteSelection = false, routeOptions = emptyList()) }
            proceedWithRoute(route, origin)
        }
    }

    private suspend fun proceedWithRoute(route: RouteInfo, origin: GeoPoint) {
        routeIndex = RoutePolylineIndex.build(route.polyline)
        routeSteps = route.steps

        val restStops = tripRepository.fetchRestStops(route.polyline).getOrElse { emptyList() }
        restStopQueue.clear()
        restStopQueue.addAll(restStops)

        _uiState.update {
            it.copy(
                isLoadingTrip = false,
                tripActive = true,
                drivingMode = true,
                routeOptions = emptyList(),
                awaitingRouteSelection = false,
                routePolyline = route.polyline,
                currentLocation = origin,
                upcomingRestStops = restStopQueue.toList(),
                nextRestStop = restStopQueue.firstOrNull(),
                upcomingStep = route.steps.getOrNull(1),
                distanceToManeuverMeters = route.steps.getOrNull(1)?.distanceAlongRouteMeters,
            )
        }

        beginLocationUpdates()
    }

    /** Toggles a "other places" category checkbox, fetching it once (then caching) if newly checked. */
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
     * route/rest-stop data fetched once in [proceedWithRoute] - no Directions/Places calls
     * happen here.
     */
    private fun handleLocationUpdate(location: Location) {
        val index = routeIndex ?: return
        val currentPoint = GeoPoint(location.latitude, location.longitude)
        val projection = index.project(currentPoint)

        val justPassed = mutableListOf<RestStop>()
        while (restStopQueue.isNotEmpty()) {
            val candidate = restStopQueue.first()
            val straightLineDistanceMeters = location.distanceTo(candidate.location.toAndroidLocation())
            val hasArrived = straightLineDistanceMeters <= ARRIVAL_RADIUS_METERS
            val hasDrivenPast = projection.distanceAlongRouteMeters > candidate.distanceAlongRouteMeters + PASS_TOLERANCE_METERS
            if (hasArrived || hasDrivenPast) {
                justPassed += restStopQueue.removeFirst()
            } else {
                break
            }
        }

        val nextStop = restStopQueue.firstOrNull()
        val distanceToNextMeters = nextStop?.let { location.distanceTo(it.location.toAndroidLocation()).toDouble() }

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

        _uiState.update { current ->
            current.copy(
                currentLocation = currentPoint,
                currentBearingDegrees = bearing,
                currentProgressMeters = projection.distanceAlongRouteMeters,
                upcomingStep = displayedStep,
                distanceToManeuverMeters = distanceToManeuverMeters,
                upcomingRestStops = restStopQueue.toList(),
                passedRestStops = current.passedRestStops + justPassed,
                nextRestStop = nextStop,
                distanceToNextRestStopMeters = distanceToNextMeters,
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

private fun GeoPoint.toAndroidLocation(): Location = Location("").apply {
    latitude = this@toAndroidLocation.latitude
    longitude = this@toAndroidLocation.longitude
}
