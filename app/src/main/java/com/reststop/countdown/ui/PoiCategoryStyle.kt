package com.reststop.countdown.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.reststop.countdown.data.model.PoiCategory

/** A distinct icon per POI category, used in both the summary cards and the map legend. */
fun poiCategoryIcon(category: PoiCategory): ImageVector = when (category) {
    PoiCategory.REST_AREA -> Icons.Filled.Weekend
    PoiCategory.FAST_FOOD -> Icons.Filled.Fastfood
    PoiCategory.COFFEE -> Icons.Filled.LocalCafe
    PoiCategory.GAS_STATION -> Icons.Filled.LocalGasStation
    PoiCategory.EV_CHARGING -> Icons.Filled.EvStation
}

/** A distinct marker hue per category, so pins on the map are visually distinguishable at a glance. */
fun poiCategoryMarkerHue(category: PoiCategory): Float = when (category) {
    PoiCategory.REST_AREA -> BitmapDescriptorFactory.HUE_AZURE
    PoiCategory.FAST_FOOD -> BitmapDescriptorFactory.HUE_ORANGE
    PoiCategory.COFFEE -> BitmapDescriptorFactory.HUE_YELLOW
    PoiCategory.GAS_STATION -> BitmapDescriptorFactory.HUE_ROSE
    PoiCategory.EV_CHARGING -> BitmapDescriptorFactory.HUE_GREEN
}
