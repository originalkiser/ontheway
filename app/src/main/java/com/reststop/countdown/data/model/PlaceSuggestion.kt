package com.reststop.countdown.data.model

/**
 * One destination-search result from Places Text Search - a business/landmark name or an
 * address. Carries [location] (unlike plain Autocomplete predictions) so results can be shown
 * as pins on the map, letting the driver visually confirm which one they mean before starting.
 */
data class PlaceSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
    val location: GeoPoint,
)
