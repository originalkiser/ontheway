package com.reststop.countdown.ui

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.reststop.countdown.data.model.GeoPoint

fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

fun List<GeoPoint>.toBounds(): LatLngBounds? {
    if (isEmpty()) return null
    val builder = LatLngBounds.Builder()
    forEach { builder.include(it.toLatLng()) }
    return builder.build()
}
