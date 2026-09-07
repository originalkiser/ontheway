package com.reststop.countdown.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reststop.countdown.data.model.PoiCategory
import com.reststop.countdown.data.model.PointOfInterest
import com.reststop.countdown.ui.DistanceUnit
import com.reststop.countdown.ui.formatDistance

/**
 * Checkbox row for optional "other places along the route" categories, plus the resulting list
 * sorted by distance from the route polyline, and a compact "upcoming" summary per category -
 * a loose, non-turn-by-turn nav hint that never takes over the main map/route.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PoiPanel(
    selectedCategories: Set<PoiCategory>,
    loadingCategory: PoiCategory?,
    nearbyPois: List<PointOfInterest>,
    upcomingPoisByCategory: Map<PoiCategory, PointOfInterest>,
    distanceUnit: DistanceUnit,
    onToggleCategory: (PoiCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Also show along the route", style = MaterialTheme.typography.titleSmall)

            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PoiCategory.entries.forEach { category ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = category in selectedCategories,
                            onCheckedChange = { onToggleCategory(category) },
                        )
                        Text(text = category.displayName, style = MaterialTheme.typography.bodyMedium)
                        if (loadingCategory == category) {
                            CircularProgressIndicator(modifier = Modifier.padding(start = 4.dp).size(14.dp))
                        }
                    }
                }
            }

            if (upcomingPoisByCategory.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                upcomingPoisByCategory.forEach { (category, poi) ->
                    Text(
                        text = "Next ${category.displayName}: ${poi.name} - ${formatDistance(poi.distanceAlongRouteMeters, distanceUnit)}",
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (nearbyPois.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(text = "Closest to the route:", style = MaterialTheme.typography.labelMedium)
                // A plain Column, not LazyColumn: this sits inside a parent that's already
                // vertically scrollable (unbounded height), which LazyColumn can't measure
                // against. Capped to 20 rows, so no need for list virtualization here.
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    nearbyPois.take(20).forEach { poi ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(text = "${poi.category.displayName}: ${poi.name}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = formatDistance(poi.distanceFromRouteMeters, distanceUnit),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
