package com.reststop.countdown.ui

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reststop.countdown.data.model.GeoPoint
import com.reststop.countdown.data.model.PoiCategory
import com.reststop.countdown.data.model.PointOfInterest
import com.reststop.countdown.data.model.RestStop
import com.reststop.countdown.domain.repository.TripRepository
import com.reststop.countdown.domain.util.RoutePolylineIndex
import com.reststop.countdown.location.LocationTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the whole trip lifecycle:
 *  1. [startTrip] makes the one-and-only Directions + Places API calls for the trip.
 *  2. [beginLocationUpdates] then drives everything else purely off local GPS fixes -
 *     no further network calls happen until (if ever) a brand new trip is started.
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
    }

    private val _uiState = MutableStateFlow(TripUiState())
    val uiState: StateFlow<TripUiState> = _uiState.asStateFlow()

    private var routeIndex: RoutePolylineIndex? = null
    private val restStopQueue = ArrayDeque<RestStop>()
    private var locationJob: Job? = null

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
    }

    fun onDestinationChanged(text: String) {
        _uiState.update { it.copy(destinationInput = text, errorMessage = null) }
    }

    fun toggleDistanceUnit() {
        _uiState.update {
            it.copy(distanceUnit = if (it.distanceUnit == DistanceUnit.MILES) DistanceUnit.KILOMETERS else DistanceUnit.MILES)
        }
    }

    /** The one-time trip setup: a single Directions call and a single Places sweep. */
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

            val route = tripRepository.fetchRoute(origin, destination).getOrElse { throwable ->
                _uiState.update { it.copy(isLoadingTrip = false, errorMessage = throwable.message ?: "Failed to fetch route.") }
                return@launch
            }

            routeIndex = RoutePolylineIndex.build(route.polyline)

            val restStops = tripRepository.fetchRestStops(route.polyline).getOrElse { emptyList() }
            restStopQueue.clear()
            restStopQueue.addAll(restStops)

            _uiState.update {
                it.copy(
                    isLoadingTrip = false,
                    tripActive = true,
                    routePolyline = route.polyline,
                    currentLocation = origin,
                    upcomingRestStops = restStopQueue.toList(),
                    nextRestStop = restStopQueue.firstOrNull(),
                )
            }

            beginLocationUpdates()
        }
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
     * The real-time countdown loop. Runs entirely on-device against the cached route/rest-stop
     * data fetched once in [startTrip] - no Directions/Places calls happen here.
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

        _uiState.update { current ->
            current.copy(
                currentLocation = currentPoint,
                currentProgressMeters = projection.distanceAlongRouteMeters,
                upcomingRestStops = restStopQueue.toList(),
                passedRestStops = current.passedRestStops + justPassed,
                nextRestStop = nextStop,
                distanceToNextRestStopMeters = distanceToNextMeters,
                upcomingPoisByCategory = nearestUpcomingPoiPerCategory(current, projection.distanceAlongRouteMeters),
            )
        }
    }

    /** Picks, per selected category, the closest not-yet-passed place - a lightweight "loose" nav hint. */
    private fun nearestUpcomingPoiPerCategory(state: TripUiState, currentProgressMeters: Double): Map<PoiCategory, PointOfInterest> =
        state.selectedCategories.mapNotNull { category ->
            val next = state.poiByCategory[category].orEmpty()
                .filter { it.distanceAlongRouteMeters >= currentProgressMeters && it.distanceFromRouteMeters <= UPCOMING_POI_MAX_LATERAL_METERS }
                .minByOrNull { it.distanceAlongRouteMeters }
            next?.let { category to it }
        }.toMap()

    private fun recomputeUpcomingPois() {
        val current = _uiState.value
        _uiState.update { it.copy(upcomingPoisByCategory = nearestUpcomingPoiPerCategory(current, current.currentProgressMeters)) }
    }

    override fun onCleared() {
        locationJob?.cancel()
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
