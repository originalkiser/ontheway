package com.reststop.countdown.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reststop.countdown.data.model.PointOfInterest

/**
 * Shown while routed through a driver-chosen secondary stop - "Via: [name], then continuing to
 * your destination". Disappears automatically once the driver reaches/passes it (handled in
 * MainViewModel), or can be cancelled here to reroute straight back to the main destination.
 */
@Composable
fun SecondaryDestinationBanner(
    waypoint: PointOfInterest?,
    isRerouting: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (waypoint == null && !isRerouting) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = "Via stop", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = if (isRerouting) "Rerouting..." else "${waypoint?.name}, then continuing to your destination",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (isRerouting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                TextButton(onClick = onClear) { Text("Cancel") }
            }
        }
    }
}
