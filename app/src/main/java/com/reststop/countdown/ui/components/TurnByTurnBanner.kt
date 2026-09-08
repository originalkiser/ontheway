package com.reststop.countdown.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reststop.countdown.data.model.RouteStep
import com.reststop.countdown.ui.DistanceUnit
import com.reststop.countdown.ui.formatDistance
import com.reststop.countdown.ui.maneuverIcon

/**
 * The driving-mode instruction banner - "in [X.X mi], [turn icon] [instruction]" - computed
 * purely from local GPS progress against the route's cached steps, never a new Directions call.
 */
@Composable
fun TurnByTurnBanner(
    upcomingStep: RouteStep?,
    distanceToManeuverMeters: Double?,
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier,
) {
    if (upcomingStep == null) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = maneuverIcon(upcomingStep.maneuver),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
            Row(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = buildString {
                        distanceToManeuverMeters?.let { append(formatDistance(it, distanceUnit)) }
                        if (distanceToManeuverMeters != null) append("  ")
                        append(upcomingStep.instruction)
                    },
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        }
    }
}
