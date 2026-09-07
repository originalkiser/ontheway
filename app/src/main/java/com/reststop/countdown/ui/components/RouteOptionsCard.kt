package com.reststop.countdown.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reststop.countdown.data.model.RouteInfo
import com.reststop.countdown.ui.DistanceUnit
import com.reststop.countdown.ui.formatDistance
import java.util.concurrent.TimeUnit

/** Shown when the Directions API returns more than one route - the driver picks before rest-stop search runs. */
@Composable
fun RouteOptionsCard(
    routes: List<RouteInfo>,
    distanceUnit: DistanceUnit,
    onSelectRoute: (RouteInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Choose a route", style = MaterialTheme.typography.titleMedium)
            routes.forEachIndexed { index, route ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectRoute(route) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(text = route.summary, fontWeight = FontWeight.Bold)
                        Text(
                            text = formatDistance(route.distanceMeters.toDouble(), distanceUnit),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(text = formatDuration(route.durationSeconds), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val totalMinutes = TimeUnit.SECONDS.toMinutes(seconds.toLong())
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
