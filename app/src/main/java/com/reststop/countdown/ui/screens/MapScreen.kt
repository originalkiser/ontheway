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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
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
import com.reststop.countdown.ui.toBounds
import com.reststop.countdown.ui.toLatLng

@Composable
fun MapScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val cameraPositionState = rememberCameraPositionState()
    var followMode by remember { mutableStateOf(true) }

    // Fit the whole route on screen exactly once, right after the one-time Directions fetch.
    LaunchedEffect(uiState.routePolyline) {
        uiState.routePolyline.toBounds()?.let { bounds ->
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 96))
        }
    }

    // Hands-free camera follow: recenters on the driver's live GPS fix without any extra
    // network calls. Toggle off via the FAB to freely pan/inspect the map.
    LaunchedEffect(uiState.currentLocation, followMode) {
        if (followMode) {
            uiState.currentLocation?.let { location ->
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(location.toLatLng(), 15f))
            }
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
                Polyline(
                    points = uiState.routePolyline.map { it.toLatLng() },
                    color = MaterialTheme.colorScheme.primary,
                    width = 10f,
                )
            }

            uiState.upcomingRestStops.forEach { restStop ->
                RestStopMarker(restStop, isNext = restStop == uiState.nextRestStop)
            }

            uiState.visiblePois.forEach { poi ->
                Marker(
                    state = MarkerState(position = poi.location.toLatLng()),
                    title = poi.name,
                    snippet = poi.category.displayName,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
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

                if (!uiState.tripActive) {
                    DestinationInputBar(
                        destination = uiState.destinationInput,
                        isLoading = uiState.isLoadingTrip,
                        errorMessage = uiState.errorMessage,
                        onDestinationChanged = viewModel::onDestinationChanged,
                        onStartTrip = viewModel::startTrip,
                    )
                } else {
                    PoiPanel(
                        selectedCategories = uiState.selectedCategories,
                        loadingCategory = uiState.loadingCategory,
                        nearbyPois = uiState.visiblePois,
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

        FloatingActionButton(
            onClick = { followMode = !followMode },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = if (followMode) Icons.Filled.GpsFixed else Icons.Filled.GpsOff,
                contentDescription = if (followMode) "Following your location" else "Follow mode off",
                tint = if (followMode) MaterialTheme.colorScheme.primary else Color.Gray,
            )
        }
    }
}

@Composable
@GoogleMapComposable
private fun RestStopMarker(restStop: RestStop, isNext: Boolean) {
    Marker(
        state = MarkerState(position = restStop.location.toLatLng()),
        title = restStop.name,
        snippet = if (isNext) "Next stop" else "Rest stop",
    )
}
