package com.reststop.countdown.data.model

/**
 * "Other places along the route" categories the user can toggle on with a checkbox - rest areas
 * included, on equal footing with the rest. Each maps to a Places API (New) `includedType` used
 * for a one-time Nearby Search sweep of the route corridor.
 *
 * @property maxLateralMeters how far off the route's polyline a place can sit and still count
 *   as "on the way" for this category. Rest areas are kept tight (1.5 mi) since a rest area a
 *   few miles off the highway isn't really "on the route"; other categories allow a bit more
 *   since a gas station or restaurant just off an exit is still a reasonable stop.
 */
enum class PoiCategory(val displayName: String, val placesApiType: String, val maxLateralMeters: Double) {
    REST_AREA("Rest Areas", "rest_stop", 2_414.0),
    FAST_FOOD("Fast Food", "fast_food_restaurant", 1_609.0),
    COFFEE("Coffee", "coffee_shop", 1_609.0),
    GAS_STATION("Gas Station", "gas_station", 1_609.0),
    EV_CHARGING("EV Charging", "electric_vehicle_charging_station", 3_219.0),
}
