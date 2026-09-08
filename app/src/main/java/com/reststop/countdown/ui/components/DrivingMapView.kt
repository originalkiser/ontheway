package com.reststop.countdown.ui.components

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.navigation.NavigationView
import com.reststop.countdown.data.model.PointOfInterest
import com.reststop.countdown.ui.poiCategoryMarkerHue
import com.reststop.countdown.ui.toLatLng

/**
 * Hosts Google's own turn-by-turn driving UI - road-snapped route line, native instruction
 * banner, ETA card, voice guidance, lane info - instead of a hand-rolled one. What's actually
 * being navigated (destination, waypoints, guidance) is driven separately by MainViewModel via
 * the shared Navigator session; this composable just displays it and overlays our own POI pins.
 *
 * [NavigationView] is a plain Android View, not part of maps-compose, so its lifecycle is
 * forwarded manually here - the same recipe commonly used to embed a classic MapView in Compose.
 */
@Composable
fun DrivingMapView(
    pois: List<PointOfInterest>,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var navigationView by remember { mutableStateOf<NavigationView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            NavigationView(context).apply {
                onCreate(Bundle())
                onStart()
                onResume()
                navigationView = this
            }
        },
    )

    DisposableEffect(lifecycleOwner, navigationView) {
        val view = navigationView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view?.onResume()
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                Lifecycle.Event.ON_STOP -> view?.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(navigationView) {
        onDispose { navigationView?.onDestroy() }
    }

    DisposableEffect(pois, navigationView) {
        var addedMarkers: List<Marker> = emptyList()
        navigationView?.getMapAsync { map ->
            addedMarkers = pois.mapNotNull { poi ->
                map.addMarker(
                    MarkerOptions()
                        .position(poi.location.toLatLng())
                        .title(poi.name)
                        .snippet(poi.category.displayName)
                        .icon(BitmapDescriptorFactory.defaultMarker(poiCategoryMarkerHue(poi.category))),
                )
            }
        }
        onDispose { addedMarkers.forEach { it.remove() } }
    }
}
