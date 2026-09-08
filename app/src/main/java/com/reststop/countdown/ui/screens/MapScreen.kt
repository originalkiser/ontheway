package com.reststop.countdown.ui.screens

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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.reststop.countdown.R
import com.reststop.countdown.ui.MainViewModel
import com.reststop.countdown.ui.components.CategoryChipsColumn
import com.reststop.countdown.ui.components.CategoryFilterMenu
import com.reststop.countdown.ui.components.DestinationInputBar
import com.reststop.countdown.ui.components.RouteOptionsCard
import com.reststop.countdown.ui.components.SecondaryDestinationBanner
import com.reststop.countdown.ui.components.TripSummaryBar
import com.reststop.countdown.ui.components.TurnByTurnBanner
import com.reststop.countdown.ui.numberedBitmapDescriptor
import com.reststop.countdown.ui.poiCategoryMarkerHue
import com.reststop.countdown.ui.routeOptionColor
import com.reststop.countdown.ui.toBounds
import com.reststop.countdown.ui.toLatLng
import com.reststop.countdown.ui.vectorBitmapDescriptor
import kotlinx.coroutines.launch

private const val OVERVIEW_ZOOM = 15f
private const val DRIVING_ZOOM = 19f
private const val DRIVING_TILT = 65f
private val ROUTE_COLOR = Color(0xFF0B3D91)

@Composable
fun MapScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val cameraPositionState = rememberCameraPositionState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    var followMode by remember { mutableStateOf(true) }
    // Measured live so the corner menu and floating chips always clear the turn-by-turn/secondary
    // destination banners above them, however tall those happen to be.
    var topBarHeight by remember { mutableStateOf(0.dp) }

    // Rasterized once and reused - BitmapDescriptorFactory.fromResource() can't decode a vector
    // drawable (it uses BitmapFactory.decodeResource under the hood, raster formats only), so the
    // arrow is drawn onto a real Bitmap ourselves first.
    val navPuckIcon = remember(context) { vectorBitmapDescriptor(context, R.drawable.ic_nav_arrow) }

    // Center on the driver's current location as soon as it's known, before any trip starts -
    // otherwise the map sits on (0,0). Runs once: initialCameraTarget is only ever set once.
    LaunchedEffect(uiState.initialCameraTarget) {
        val target = uiState.initialCameraTarget
        if (target != null && !uiState.tripActive) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target.toLatLng(), 14f))
        }
    }

    // Fit the whole route on screen exactly once, right after the one-time Directions fetch.
    LaunchedEffect(uiState.routePolyline) {
        uiState.routePolyline.toBounds()?.let { bounds ->
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 96))
        }
    }

    // Hands-free camera follow: recenters on the driver's live GPS fix without any extra network
    // calls. Driving mode tilts the camera and rotates it to the direction of travel, like a
    // turn-by-turn nav app; overview mode stays flat and north-up.
    LaunchedEffect(uiState.currentLocation, uiState.currentBearingDegrees, uiState.drivingMode, followMode) {
        if (!followMode) return@LaunchedEffect
        val location = uiState.currentLocation ?: return@LaunchedEffect
        val cameraPosition = if (uiState.drivingMode) {
            CameraPosition.Builder()
                .target(location.toLatLng())
                .zoom(DRIVING_ZOOM)
                .tilt(DRIVING_TILT)
                .bearing(uiState.currentBearingDegrees)
                .build()
        } else {
            CameraPosition.Builder()
                .target(location.toLatLng())
                .zoom(OVERVIEW_ZOOM)
                .tilt(0f)
                .bearing(0f)
                .build()
        }
        cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    // If the driver manually drags the map, stop auto-following until they tap "recenter".
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            followMode = false
        }
    }

    val showNavPuck = uiState.tripActive && uiState.drivingMode && uiState.currentLocation != null

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            // The custom nav-arrow puck below replaces the default blue dot while driving, so
            // there's only ever one location indicator on screen.
            properties = MapProperties(isMyLocationEnabled = uiState.hasLocationPermission && !showNavPuck),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
        ) {
            if (uiState.routePolyline.isNotEmpty()) {
                val points = uiState.routePolyline.map { it.toLatLng() }
                // A light outline underneath a dark, thick line on top - reads clearly against
                // any map style, the way Google Maps draws its own route.
                Polyline(points = points, color = Color.White, width = 24f, zIndex = 1f)
                Polyline(points = points, color = ROUTE_COLOR, width = 14f, zIndex = 2f)
            }

            // Route alternatives, drawn and numbered right on the map (not just the picker's
            // small preview thumbnails) so the driver can see where each one actually goes.
            if (uiState.awaitingRouteSelection) {
                uiState.routeOptions.forEachIndexed { index, route ->
                    val color = routeOptionColor(index)
                    val points = route.polyline.map { it.toLatLng() }
                    Polyline(points = points, color = Color.White, width = 12f, zIndex = 1f)
                    Polyline(points = points, color = color, width = 6f, zIndex = 2f)
                    val midpoint = route.polyline.getOrNull(route.polyline.size / 2)
                    if (midpoint != null) {
                        Marker(
                            state = MarkerState(position = midpoint.toLatLng()),
                            icon = numberedBitmapDescriptor(index + 1, color),
                            anchor = Offset(0.5f, 0.5f),
                        )
                    }
                }
            }

            // Once a stop is set, showing every other category pin too just clutters the map and
            // makes it unclear what's actually being navigated to - so only that pin renders.
            val poisToShow = uiState.selectedWaypoint?.let { waypoint ->
                uiState.visiblePois.filter { it.placeId == waypoint.placeId }
            } ?: uiState.visiblePois
            poisToShow.forEach { poi ->
                Marker(
                    state = MarkerState(position = poi.location.toLatLng()),
                    title = poi.name,
                    snippet = poi.category.displayName,
                    icon = BitmapDescriptorFactory.defaultMarker(poiCategoryMarkerHue(poi.category)),
                )
            }

            // Pre-trip destination search results - shown on the map so the driver can visually
            // confirm the right one before starting, not just pick from the text list.
            if (!uiState.tripActive) {
                uiState.destinationSuggestions.forEach { suggestion ->
                    Marker(
                        state = MarkerState(position = suggestion.location.toLatLng()),
                        title = suggestion.primaryText,
                        snippet = suggestion.secondaryText,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
                    )
                }
            }

            if (showNavPuck) {
                Marker(
                    state = MarkerState(position = uiState.currentLocation!!.toLatLng()),
                    icon = navPuckIcon,
                    rotation = uiState.currentBearingDegrees,
                    flat = true,
                    anchor = Offset(0.5f, 0.5f),
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.tripActive) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .onGloballyPositioned { coordinates ->
                            topBarHeight = with(density) { coordinates.size.height.toDp() }
                        },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TurnByTurnBanner(
                        upcomingStep = uiState.upcomingStep,
                        distanceToManeuverMeters = uiState.distanceToManeuverMeters,
                        distanceUnit = uiState.distanceUnit,
                    )
                    SecondaryDestinationBanner(
                        waypoint = uiState.selectedWaypoint,
                        isRerouting = uiState.isReroutingToWaypoint,
                        onClear = viewModel::clearSecondaryDestination,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = if (uiState.tripActive) 0.dp else 12.dp),
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

                    if (uiState.awaitingRouteSelection) {
                        RouteOptionsCard(
                            routes = uiState.routeOptions,
                            distanceUnit = uiState.distanceUnit,
                            onSelectRoute = viewModel::selectRoute,
                            onCancel = viewModel::cancelRouteSelection,
                        )
                    } else if (!uiState.tripActive) {
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

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.tripActive) {
                        TripSummaryBar(
                            etaEpochMillis = uiState.etaEpochMillis,
                            remainingDistanceMeters = uiState.remainingDistanceMeters,
                            distanceUnit = uiState.distanceUnit,
                        )
                    }
                    if (uiState.tripActive && uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (uiState.tripActive) {
            // A small corner button rather than a card in the middle of the screen, so it takes
            // no layout space when closed and never covers the map.
            CategoryFilterMenu(
                isMenuOpen = uiState.isCategoryMenuOpen,
                selectedCategories = uiState.selectedCategories,
                loadingCategory = uiState.loadingCategory,
                onToggleMenu = viewModel::toggleCategoryMenu,
                onToggleCategory = viewModel::toggleCategory,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = topBarHeight + 12.dp, start = 12.dp),
            )
        }

        if (uiState.tripActive && uiState.selectedCategories.isNotEmpty()) {
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
                    .padding(top = topBarHeight + 12.dp, end = 12.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.tripActive) {
                SmallFloatingActionButton(onClick = viewModel::endTrip) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "End trip")
                }
                SmallFloatingActionButton(onClick = viewModel::toggleDrivingMode) {
                    Icon(
                        imageVector = if (uiState.drivingMode) Icons.Filled.Map else Icons.Filled.Navigation,
                        contentDescription = if (uiState.drivingMode) "Switch to overview" else "Switch to driving view",
                    )
                }
            }

            FloatingActionButton(
                onClick = {
                    followMode = true
                    val location = uiState.currentLocation ?: uiState.initialCameraTarget
                    if (location != null) {
                        val cameraPosition = if (uiState.drivingMode && uiState.tripActive) {
                            CameraPosition.Builder()
                                .target(location.toLatLng())
                                .zoom(DRIVING_ZOOM)
                                .tilt(DRIVING_TILT)
                                .bearing(uiState.currentBearingDegrees)
                                .build()
                        } else {
                            CameraPosition.Builder()
                                .target(location.toLatLng())
                                .zoom(OVERVIEW_ZOOM)
                                .build()
                        }
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(cameraPosition))
                        }
                    }
                },
            ) {
                Icon(
                    imageVector = if (followMode) Icons.Filled.GpsFixed else Icons.Filled.GpsOff,
                    contentDescription = "Re-center on my location",
                    tint = if (followMode) MaterialTheme.colorScheme.primary else Color.Gray,
                )
            }
        }
    }
}
