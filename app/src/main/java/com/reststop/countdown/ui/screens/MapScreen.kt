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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.reststop.countdown.data.model.RestStop
import com.reststop.countdown.ui.MainViewModel
import com.reststop.countdown.ui.components.DestinationInputBar
import com.reststop.countdown.ui.components.NextRestStopCard
import com.reststop.countdown.ui.components.PoiPanel
import com.reststop.countdown.ui.components.RouteOptionsCard
import com.reststop.countdown.ui.components.TurnByTurnBanner
import com.reststop.countdown.ui.poiCategoryMarkerHue
import com.reststop.countdown.ui.toBounds
import com.reststop.countdown.ui.toLatLng
import kotlinx.coroutines.launch

private const val OVERVIEW_ZOOM = 15f
private const val DRIVING_ZOOM = 18.5f
private const val DRIVING_TILT = 60f

@Composable
fun MapScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val cameraPositionState = rememberCameraPositionState()
    val coroutineScope = rememberCoroutineScope()
    var followMode by remember { mutableStateOf(true) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = uiState.hasLocationPermission),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
        ) {
            if (uiState.routePolyline.isNotEmpty()) {
                val points = uiState.routePolyline.map { it.toLatLng() }
                // A light outline underneath a dark, thick line on top - reads clearly against
                // any map style, the way Google Maps draws its own route.
                Polyline(points = points, color = Color.White, width = 24f, zIndex = 1f)
                Polyline(points = points, color = ROUTE_COLOR, width = 14f, zIndex = 2f)
            }

            uiState.upcomingRestStops.forEach { restStop ->
                RestStopMarker(restStop, isNext = restStop == uiState.nextRestStop)
            }

            uiState.visiblePois.forEach { poi ->
                Marker(
                    state = MarkerState(position = poi.location.toLatLng()),
                    title = poi.name,
                    snippet = poi.category.displayName,
                    icon = BitmapDescriptorFactory.defaultMarker(poiCategoryMarkerHue(poi.category)),
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.tripActive) {
                TurnByTurnBanner(
                    upcomingStep = uiState.upcomingStep,
                    distanceToManeuverMeters = uiState.distanceToManeuverMeters,
                    distanceUnit = uiState.distanceUnit,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
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
                        )
                    } else if (!uiState.tripActive) {
                        DestinationInputBar(
                            destination = uiState.destinationInput,
                            suggestions = uiState.destinationSuggestions,
                            isLoading = uiState.isLoadingTrip,
                            errorMessage = uiState.errorMessage,
                            onDestinationChanged = viewModel::onDestinationChanged,
                            onSuggestionSelected = viewModel::selectSuggestion,
                            onStartTrip = viewModel::startTrip,
                        )
                    } else {
                        PoiPanel(
                            selectedCategories = uiState.selectedCategories,
                            loadingCategory = uiState.loadingCategory,
                            upcomingPoisByCategory = uiState.upcomingPoisByCategory,
                            distanceUnit = uiState.distanceUnit,
                            onToggleCategory = viewModel::toggleCategory,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.tripActive) {
                        NextRestStopCard(
                            restStop = uiState.nextRestStop,
                            distanceMeters = uiState.distanceToNextRestStopMeters,
                            unit = uiState.distanceUnit,
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.tripActive) {
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

private val ROUTE_COLOR = Color(0xFF0B3D91)

@Composable
@GoogleMapComposable
private fun RestStopMarker(restStop: RestStop, isNext: Boolean) {
    Marker(
        state = MarkerState(position = restStop.location.toLatLng()),
        title = restStop.name,
        snippet = if (isNext) "Next stop" else "Rest stop",
    )
}
