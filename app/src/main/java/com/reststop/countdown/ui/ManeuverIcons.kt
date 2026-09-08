package com.reststop.countdown.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.RoundaboutLeft
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.UTurnRight
import androidx.compose.ui.graphics.vector.ImageVector

/** Maps a Directions API `maneuver` string to a Material icon for the turn-by-turn banner. */
fun maneuverIcon(maneuver: String?): ImageVector = when (maneuver) {
    "turn-left" -> Icons.Filled.TurnLeft
    "turn-right" -> Icons.Filled.TurnRight
    "turn-slight-left" -> Icons.Filled.TurnSlightLeft
    "turn-slight-right" -> Icons.Filled.TurnSlightRight
    "turn-sharp-left" -> Icons.Filled.TurnSharpLeft
    "turn-sharp-right" -> Icons.Filled.TurnSharpRight
    "uturn-left" -> Icons.Filled.UTurnLeft
    "uturn-right" -> Icons.Filled.UTurnRight
    "roundabout-left" -> Icons.Filled.RoundaboutLeft
    "roundabout-right" -> Icons.Filled.RoundaboutRight
    "merge", "fork-left", "fork-right", "ramp-left", "ramp-right" -> Icons.Filled.Merge
    "straight" -> Icons.Filled.Straight
    else -> Icons.Filled.ArrowUpward
}
