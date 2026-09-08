package com.reststop.countdown.ui

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.Waypoint
import com.reststop.countdown.data.model.GeoPoint
import com.reststop.countdown.data.model.PlaceSuggestion
import com.reststop.countdown.data.model.PoiCategory
import com.reststop.countdown.data.model.PointOfInterest
import com.reststop.countdown.data.model.RouteInfo
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
 *  2. [startTrip] fetches a route once, for the "confirm destination" screen. [confirmRoute] then
 *     hands off to the Navigation SDK for the actual driving - native route line, turn-by-turn
 *     banner, voice guidance and rerouting all happen inside the SDK itself, driven by
 *     [startGuidance] below. [setSecondaryDestination] reroutes both the SDK and our own trip
 *     state through a chosen POI as a waypoint.
 *  3. [beginLocationUpdates] runs entirely on-device, independent of the SDK's own tracking - it
 *     exists purely to know how far along the route the driver is, for ranking "upcoming" POIs
 *     and for showing the single selected-stop pin. No further network calls happen here.
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
    }

    private val _uiState = MutableStateFlow(TripUiState())
    val uiState: StateFlow<TripUiState> = _uiState.asStateFlow()

    private var routeIndex: RoutePolylineIndex? = null
    private var locationJob: Job? = null
    private var suggestionsJob: Job? = null
    private var pendingOrigin: GeoPoint? = null
    private var originalDestinationQuery: String = ""
    private var originalDestinationPlaceId: String? = null
    private var navigator: Navigator? = null

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

    /** The app-wide Navigator session became available (first launch may show Google's Nav ToS dialog first). */
    fun onNavigatorReady(navigator: Navigator) {
        this.navigator = navigator
    }

    /** Navigator setup failed - the app still works, just without native turn-by-turn this session. */
    fun onNavigatorUnavailable() {
        navigator = null
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

    fun toggleCategoryMenu() {
        _uiState.update { it.copy(isCategoryMenuOpen = !it.isCategoryMenuOpen) }
    }

    /** Expands/collapses a category's floating chip to show its individual results. */
    fun toggleCategoryChipExpanded(category: PoiCategory) {
        _uiState.update { it.copy(expandedCategoryChip = if (it.expandedCategoryChip == category) null else category) }
    }

    /** Fetches a single route and surfaces it on the "confirm destination" screen. */
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

            val route = tripRepository.fetchRoute(origin, destination, originalDestinationPlaceId)
                .getOrElse { throwable ->
                    _uiState.update { it.copy(isLoadingTrip = false, errorMessage = throwable.message ?: "Failed to fetch route.") }
                    return@launch
                }
                .firstOrNull()

            if (route == null) {
                _uiState.update { it.copy(isLoadingTrip = false, errorMessage = "No route found for \"$destination\".") }
                return@launch
            }

            // Always confirm before tracking starts - the driver should see exactly where they're
            // headed (and its distance/time) and get a deliberate "Start" tap, not have the app
            // silently commit to whatever Directions (or a mistyped/misheard address) resolved to.
            _uiState.update { it.copy(isLoadingTrip = false, pendingRoute = route) }
        }
    }

    /** Driver tapped "Start Trip" on the confirm screen. */
    fun confirmRoute() {
        val route = _uiState.value.pendingRoute ?: return
        val origin = pendingOrigin ?: _uiState.value.currentLocation ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTrip = true, pendingRoute = null) }
            proceedWithRoute(route, origin, waypoint = null)
        }
    }

    /** "Not right? Edit" on the confirm screen - back to the destination field, nothing else disturbed. */
    fun cancelRouteSelection() {
        _uiState.update { it.copy(pendingRoute = null) }
    }

    /**
     * A driver-initiated detour: reroutes through [poi] before continuing to the original
     * destination. One extra Directions call (deliberate, not a background poll) for our own
     * POI-corridor bookkeeping - the Navigation SDK reroutes itself the same moment.
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

    private fun proceedWithRoute(route: RouteInfo, origin: GeoPoint, waypoint: PointOfInterest?) {
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

        _uiState.update {
            it.copy(
                isLoadingTrip = false,
                isReroutingToWaypoint = false,
                tripActive = true,
                pendingRoute = null,
                routePolyline = route.polyline,
                currentLocation = origin,
                selectedWaypoint = waypoint,
                waypointArrivalDistanceMeters = route.waypointArrivalDistanceMeters,
                poiByCategory = reprojectedPoiByCategory,
            )
        }

        startGuidance(route, waypoint)

        if (locationJob == null) beginLocationUpdates()
    }

    /** Hands the same destination (and waypoint, if any) to the Navigation SDK's own routing/guidance. */
    private fun startGuidance(route: RouteInfo, waypoint: PointOfInterest?) {
        val nav = navigator
        if (nav == null) {
            _uiState.update { it.copy(errorMessage = "Turn-by-turn isn't ready yet - the app will still track your progress.") }
            return
        }

        val waypoints = try {
            val list = mutableListOf<Waypoint>()
            if (waypoint != null) {
                list += Waypoint.builder().setPlaceIdString(waypoint.placeId).setTitle(waypoint.name).build()
            }
            val destinationBuilder = Waypoint.builder().setTitle(route.destinationAddress)
            val placeId = originalDestinationPlaceId
            if (placeId != null) {
                destinationBuilder.setPlaceIdString(placeId)
            } else {
                val destinationPoint = route.polyline.last()
                destinationBuilder.setLatLng(destinationPoint.latitude, destinationPoint.longitude)
            }
            list += destinationBuilder.build()
            list
        } catch (throwable: Exception) {
            _uiState.update { it.copy(errorMessage = "Couldn't start turn-by-turn guidance for this destination.") }
            return
        }

        nav.setDestinations(waypoints).setOnResultListener { status ->
            if (status == Navigator.RouteStatus.OK) {
                nav.startGuidance()
            } else {
                _uiState.update { it.copy(errorMessage = "Turn-by-turn guidance couldn't start (status: $status).") }
            }
        }
    }

    /** Abandons the active trip and returns to the destination search screen. */
    fun endTrip() {
        navigator?.stopGuidance()
        navigator?.clearDestinations()
        locationJob?.cancel()
        locationJob = null
        routeIndex = null
        pendingOrigin = null
        originalDestinationQuery = ""
        originalDestinationPlaceId = null
        _uiState.update { TripUiState(hasLocationPermission = it.hasLocationPermission, initialCameraTarget = it.currentLocation ?: it.initialCameraTarget) }
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
     * Tracks progress along the route purely for our own POI-corridor bookkeeping (ranking
     * "upcoming" places, clearing a passed waypoint) - independent of the Navigation SDK's own
     * tracking, which drives the native turn-by-turn UI separately. No network calls happen here.
     */
    private fun handleLocationUpdate(location: Location) {
        val index = routeIndex ?: return
        val currentPoint = GeoPoint(location.latitude, location.longitude)
        val projection = index.project(currentPoint)

        _uiState.update { current ->
            // Once the driver has reached (or driven past) the secondary stop, drop it and go
            // back to normal main-destination guidance - this just clears the "via" banner and
            // the single-pin filter; the Navigation SDK's own route already continues on through.
            val waypointPassed = current.waypointArrivalDistanceMeters != null &&
                projection.distanceAlongRouteMeters > current.waypointArrivalDistanceMeters + PASS_TOLERANCE_METERS

            current.copy(
                currentLocation = currentPoint,
                currentProgressMeters = projection.distanceAlongRouteMeters,
                selectedWaypoint = if (waypointPassed) null else current.selectedWaypoint,
                waypointArrivalDistanceMeters = if (waypointPassed) null else current.waypointArrivalDistanceMeters,
                upcomingPoisByCategory = nearestUpcomingPoisPerCategory(current, projection.distanceAlongRouteMeters),
            )
        }
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
