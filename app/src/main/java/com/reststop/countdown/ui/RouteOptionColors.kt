package com.reststop.countdown.ui

import androidx.compose.ui.graphics.Color

/** One distinct color per route alternative, shared between the map polylines/badges and the RouteOptionsCard rows so they visually match. */
private val routeOptionColors = listOf(
    Color(0xFF1A73E8), // blue
    Color(0xFF9C27B0), // purple
    Color(0xFFEF6C00), // orange
    Color(0xFF00897B), // teal
    Color(0xFFD81B60), // pink
)

fun routeOptionColor(index: Int): Color = routeOptionColors[index % routeOptionColors.size]
