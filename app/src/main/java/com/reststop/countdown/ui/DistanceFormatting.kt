package com.reststop.countdown.ui

import kotlin.math.roundToInt

private const val METERS_PER_MILE = 1609.344
private const val METERS_PER_KM = 1000.0

/** Formats a distance in meters as e.g. "3.4 mi" / "5.5 km", per the user's chosen unit. */
fun formatDistance(meters: Double, unit: DistanceUnit): String {
    val value = when (unit) {
        DistanceUnit.MILES -> meters / METERS_PER_MILE
        DistanceUnit.KILOMETERS -> meters / METERS_PER_KM
    }
    val rounded = (value * 10).roundToInt() / 10.0
    return "$rounded ${unit.label}"
}
