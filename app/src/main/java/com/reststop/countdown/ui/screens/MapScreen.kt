package com.reststop.countdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.reststop.countdown.ui.MainViewModel
import com.reststop.countdown.ui.TripUiState
import com.reststop.countdown.ui.components.CategoryChipsColumn
import com.reststop.countdown.ui.components.CategoryFilterMenu
import com.reststop.countdown.ui.components.ConfirmDestinationCard
import com.reststop.countdown.ui.components.DestinationInputBar
import com.reststop.countdown.ui.components.DrivingMapView
import com.reststop.countdown.ui.components.SecondaryDestinationBanner
import com.reststop.countdown.ui.toBounds
import com.reststop.countdown.ui.toLatLng
import kotlinx.coroutines.launch

private const val OVERVIEW_ZOOM = 15f
private val ROUTE_COLOR = Color(0xFF0B3D91)

@Composable
fun MapScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.tripActive) {
        DrivingScreen(viewModel, uiState)
    } else {
        PlanningScreen(viewModel, uiState)
    }
}

/**
 * Destination search + the one-time route preview/confirmation, before any tracking starts.
 * Still a plain Compose GoogleMap - the Navigation SDK only takes over once a trip is confirmed.
 */
@Composable
private fun PlanningScreen(viewModel: MainViewModel, uiState: TripUiState) {
    val cameraPositionState = rememberCameraPositionState()
    val coroutineScope = rememberCoroutineScope()
    var followMode by remember { mutableStateOf(true) }

    // Center on the driver's current location as soon as it's known - otherwise the map sits on
    // (0,0). Runs once: initialCameraTarget is only ever set once.
    LaunchedEffect(uiState.initialCameraTarget) {
        val target = uiState.initialCameraTarget
        if (target != null) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target.toLatLng(), 14f))
        }
    }

    // Fit the whole route on screen exactly once, right after the one-time Directions fetch.
    LaunchedEffect(uiState.pendingRoute) {
        uiState.pendingRoute?.polyline?.toBounds()?.let { bounds ->
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 96))
        }
    }

    // If the driver manually drags the map, stop auto-following until they tap "recenter".
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            followMode = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = uiState.hasLocationPermission),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
        ) {
            uiState.pendingRoute?.let { route ->
                val points = route.polyline.map { it.toLatLng() }
                // A light outline underneath a dark, thick line on top - reads clearly against
                // any map style, the way Google Maps draws its own route.
                Polyline(points = points, color = Color.White, width = 24f, zIndex = 1f)
                Polyline(points = points, color = ROUTE_COLOR, width = 14f, zIndex = 2f)
            }

            // Pre-trip destination search results - shown on the map so the driver can visually
            // confirm the right one before starting, not just pick from the text list.
            uiState.destinationSuggestions.forEach { suggestion ->
                Marker(
                    state = MarkerState(position = suggestion.location.toLatLng()),
                    title = suggestion.primaryText,
                    snippet = suggestion.secondaryText,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AssistChip(
                        onClick = viewModel::toggleDistanceUnit,
                        label = { Text("Units: ${uiState.distanceUnit.label}") },
                    )
                }

                val pendingRoute = uiState.pendingRoute
                if (pendingRoute != null) {
                    ConfirmDestinationCard(
                        route = pendingRoute,
                        distanceUnit = uiState.distanceUnit,
                        onConfirm = viewModel::confirmRoute,
                        onCancel = viewModel::cancelRouteSelection,
                    )
                } else {
                    DestinationInputBar(
                        destination = uiState.destinationInput,
                        suggestions = uiState.destinationSuggestions,
                        resultsExpanded = uiState.destinationResultsExpanded,
                        isLoading = uiState.isLoadingTrip,
                        errorMessage = uiState.errorMessage,
                        onDestinationChanged = viewModel::onDestinationChanged,
                        onSuggestionSelected = viewModel::selectSuggestion,
                        onToggleResultsExpanded = viewModel::toggleDestinationResultsExpanded,
                        onStartTrip = viewModel::startTrip,
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                followMode = true
                val location = uiState.currentLocation ?: uiState.initialCameraTarget
                if (location != null) {
                    coroutineScope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(location.toLatLng(), OVERVIEW_ZOOM))
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(
                imageVector = if (followMode) Icons.Filled.GpsFixed else Icons.Filled.GpsOff,
                contentDescription = "Re-center on my location",
                tint = if (followMode) MaterialTheme.colorScheme.primary else Color.Gray,
            )
        }
    }
}

/**
 * The active-trip screen: Google's native turn-by-turn driving UI (via [DrivingMapView]), with
 * our own POI category menu/chips and secondary-destination banner floating on top of it.
 */
@Composable
private fun DrivingScreen(viewModel: MainViewModel, uiState: TripUiState) {
    // Approximate clearance for the Navigation SDK's own native instruction banner, which is
    // drawn inside the view itself and can't be measured from Compose the way our own banners
    // could - a reasonable estimate, easy to tune after seeing it live on a device.
    val nativeBannerClearance = 120.dp

    Box(modifier = Modifier.fillMaxSize()) {
        val waypoint = uiState.selectedWaypoint
        val poisToShow = if (waypoint != null) {
            uiState.visiblePois.filter { it.placeId == waypoint.placeId }
        } else {
            uiState.visiblePois
        }
        DrivingMapView(pois = poisToShow, modifier = Modifier.fillMaxSize())

        SecondaryDestinationBanner(
            waypoint = uiState.selectedWaypoint,
            isRerouting = uiState.isReroutingToWaypoint,
            onClear = viewModel::clearSecondaryDestination,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = nativeBannerClearance, start = 12.dp, end = 12.dp),
        )

        CategoryFilterMenu(
            isMenuOpen = uiState.isCategoryMenuOpen,
            selectedCategories = uiState.selectedCategories,
            loadingCategory = uiState.loadingCategory,
            onToggleMenu = viewModel::toggleCategoryMenu,
            onToggleCategory = viewModel::toggleCategory,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = nativeBannerClearance, start = 12.dp),
        )

        SmallFloatingActionButton(
            onClick = viewModel::endTrip,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp),
        ) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "End trip")
        }

        if (uiState.selectedCategories.isNotEmpty()) {
            CategoryChipsColumn(
                selectedCategories = uiState.selectedCategories,
                upcomingPoisByCategory = uiState.upcomingPoisByCategory,
                expandedCategory = uiState.expandedCategoryChip,
                currentProgressMeters = uiState.currentProgressMeters,
                distanceUnit = uiState.distanceUnit,
                onToggleExpanded = viewModel::toggleCategoryChipExpanded,
                onSelectWaypoint = viewModel::setSecondaryDestination,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = nativeBannerClearance, end = 12.dp),
            )
        }

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .background(color = MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
            )
        }
    }
}
