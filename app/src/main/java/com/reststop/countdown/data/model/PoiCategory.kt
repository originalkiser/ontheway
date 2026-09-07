package com.reststop.countdown.data.model

/**
 * Optional "other places along the route" categories the user can toggle on
 * with a checkbox. Each maps to a Places API (New) `includedType` used for a
 * one-time Nearby Search sweep of the route corridor.
 */
enum class PoiCategory(val displayName: String, val placesApiType: String) {
    FAST_FOOD("Fast Food", "fast_food_restaurant"),
    COFFEE("Coffee", "coffee_shop"),
    GAS_STATION("Gas Station", "gas_station"),
    EV_CHARGING("EV Charging", "electric_vehicle_charging_station"),
}
