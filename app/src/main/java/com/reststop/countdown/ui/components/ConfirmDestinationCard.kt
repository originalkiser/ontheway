package com.reststop.countdown.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reststop.countdown.data.model.RouteInfo
import com.reststop.countdown.ui.DistanceUnit
import com.reststop.countdown.ui.formatDistance
import java.util.concurrent.TimeUnit

/**
 * Shown after the one-time Directions fetch, before tracking starts - the driver sees exactly
 * where they're headed (and its distance/time) and gets a deliberate "Start Trip" tap, rather
 * than the app silently committing to whatever Directions (or a mistyped address) resolved to.
 */
@Composable
fun ConfirmDestinationCard(
    route: RouteInfo,
    distanceUnit: DistanceUnit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Confirm destination", style = MaterialTheme.typography.titleMedium)
            Text(
                text = route.destinationAddress,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = formatDistance(route.distanceMeters.toDouble(), distanceUnit), style = MaterialTheme.typography.bodyMedium)
                Text(text = formatDuration(route.durationSeconds), style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) { Text("Not right? Edit") }
                Button(onClick = onConfirm, modifier = Modifier.padding(start = 8.dp)) { Text("Start Trip") }
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
