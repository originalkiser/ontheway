package com.reststop.countdown.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reststop.countdown.data.model.GeoPoint
import com.reststop.countdown.data.model.RouteInfo
import com.reststop.countdown.ui.DistanceUnit
import com.reststop.countdown.ui.formatDistance
import com.reststop.countdown.ui.routeOptionColor
import java.util.concurrent.TimeUnit

/**
 * Shown when the Directions API returns more than one route - the driver picks before rest-area
 * search runs. Each route is numbered and colored to match the same route drawn on the map
 * itself (see MapScreen), not just the small preview thumbnail here.
 */
@Composable
fun RouteOptionsCard(
    routes: List<RouteInfo>,
    distanceUnit: DistanceUnit,
    onSelectRoute: (RouteInfo) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Confirm destination", style = MaterialTheme.typography.titleMedium)
                    routes.firstOrNull()?.let { route ->
                        Text(
                            text = route.destinationAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                TextButton(onClick = onCancel) { Text("Not right? Edit") }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            routes.forEachIndexed { index, route ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                val color = routeOptionColor(index)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectRoute(route) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(color = color, shape = CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "${index + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    RoutePreviewShape(
                        polyline = route.polyline,
                        routeColor = color,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 12.dp)
                            .size(width = 64.dp, height = 48.dp),
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
private fun RoutePreviewShape(polyline: List<GeoPoint>, routeColor: Color, modifier: Modifier = Modifier) {
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
