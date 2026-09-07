package com.reststop.countdown.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
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
import com.reststop.countdown.data.model.RestStop
import com.reststop.countdown.ui.DistanceUnit
import com.reststop.countdown.ui.formatDistance

/**
 * The hands-free, high-visibility "Next Rest Stop: [Name] - [X.X] miles away" overlay.
 * Distance is whatever the ViewModel last computed locally from GPS - never refetched here.
 */
@Composable
fun NextRestStopCard(
    restStop: RestStop?,
    distanceMeters: Double?,
    unit: DistanceUnit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.LocalGasStation,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "Next Rest Stop",
                    style = MaterialTheme.typography.labelLarge,
                )
                if (restStop != null && distanceMeters != null) {
                    Text(
                        text = "${restStop.name} - ${formatDistance(distanceMeters, unit)} away",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Text(
                        text = "No more rest stops on this route",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
