package com.reststop.countdown.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reststop.countdown.data.model.GeoPoint
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
                    RoutePreviewShape(
                        polyline = route.polyline,
                        modifier = Modifier
                            .size(width = 64.dp, height = 48.dp)
                            .padding(end = 12.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
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

/**
 * A small, abstract "shape of the route" thumbnail - not a live map tile (embedding several real
 * GoogleMap instances in a list is expensive and unreliable), just the polyline's geometry
 * normalized to fit the box, the same way ride-share apps preview a route choice.
 */
@Composable
private fun RoutePreviewShape(polyline: List<GeoPoint>, modifier: Modifier = Modifier) {
    val routeColor = MaterialTheme.colorScheme.primary
    val startColor = Color(0xFF34A853)
    val endColor = Color(0xFFEA4335)

    Canvas(modifier = modifier) {
        if (polyline.size < 2) return@Canvas

        val minLat = polyline.minOf { it.latitude }
        val maxLat = polyline.maxOf { it.latitude }
        val minLng = polyline.minOf { it.longitude }
        val maxLng = polyline.maxOf { it.longitude }
        val latSpan = (maxLat - minLat).coerceAtLeast(1e-6)
        val lngSpan = (maxLng - minLng).coerceAtLeast(1e-6)

        val padding = 4.dp.toPx()
        val availableWidth = size.width - padding * 2
        val availableHeight = size.height - padding * 2
        val scale = minOf(availableWidth / lngSpan, availableHeight / latSpan)
        val drawnWidth = lngSpan * scale
        val drawnHeight = latSpan * scale
        val offsetX = padding + (availableWidth - drawnWidth) / 2
        val offsetY = padding + (availableHeight - drawnHeight) / 2

        fun toOffset(point: GeoPoint): Offset = Offset(
            x = (offsetX + (point.longitude - minLng) * scale).toFloat(),
            y = (offsetY + (maxLat - point.latitude) * scale).toFloat(),
        )

        val path = Path().apply {
            val start = toOffset(polyline.first())
            moveTo(start.x, start.y)
            polyline.drop(1).forEach { lineTo(toOffset(it).x, toOffset(it).y) }
        }
        drawPath(path, color = routeColor, style = Stroke(width = 3.dp.toPx()))
        drawCircle(color = startColor, radius = 3.dp.toPx(), center = toOffset(polyline.first()))
        drawCircle(color = endColor, radius = 3.dp.toPx(), center = toOffset(polyline.last()))
    }
}

private fun formatDuration(seconds: Int): String {
    val totalMinutes = TimeUnit.SECONDS.toMinutes(seconds.toLong())
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
