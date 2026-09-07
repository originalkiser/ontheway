package com.reststop.countdown.data.model

/** One destination-search suggestion from Places Autocomplete - a business/landmark name or an address. */
data class PlaceSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
)
