package com.reststop.countdown.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reststop.countdown.ui.DistanceUnit
import com.reststop.countdown.ui.formatDistance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A persistent "3:45 PM · 24.0 mi left" strip, computed locally from route progress - no new API calls. */
@Composable
fun TripSummaryBar(
    etaEpochMillis: Long?,
    remainingDistanceMeters: Double?,
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier,
) {
    if (etaEpochMillis == null || remainingDistanceMeters == null) return

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ETA ${formatEta(etaEpochMillis)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${formatDistance(remainingDistanceMeters, distanceUnit)} left",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun formatEta(epochMillis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))
