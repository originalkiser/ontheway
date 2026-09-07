package com.reststop.countdown.data.model

/**
 * Plain lat/lng pair used throughout the domain and data layers so that
 * business logic never has to depend directly on the Google Maps SDK's
 * `com.google.android.gms.maps.model.LatLng` type.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)
