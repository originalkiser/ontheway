package com.reststop.countdown.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reststop.countdown.data.model.PoiCategory
import com.reststop.countdown.data.model.PointOfInterest
import com.reststop.countdown.ui.DistanceUnit
import com.reststop.countdown.ui.formatDistance
import com.reststop.countdown.ui.poiCategoryIcon

/** One small floating chip per selected category - the map-side counterpart to the category menu. */
@Composable
fun CategoryChipsColumn(
    selectedCategories: Set<PoiCategory>,
    upcomingPoisByCategory: Map<PoiCategory, List<PointOfInterest>>,
    expandedCategory: PoiCategory?,
    currentProgressMeters: Double,
    distanceUnit: DistanceUnit,
    onToggleExpanded: (PoiCategory) -> Unit,
    onSelectWaypoint: (PointOfInterest) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // PoiCategory.entries order, not selectedCategories' (a Set) iteration order, so chips
        // don't jump around as categories are toggled on and off.
        PoiCategory.entries.forEach { category ->
            if (category !in selectedCategories) return@forEach
            val nearest = upcomingPoisByCategory[category]
            if (!nearest.isNullOrEmpty()) {
                CategoryChip(
                    category = category,
                    nearest = nearest,
                    expanded = expandedCategory == category,
                    currentProgressMeters = currentProgressMeters,
                    distanceUnit = distanceUnit,
                    onToggleExpanded = { onToggleExpanded(category) },
                    onSelectWaypoint = onSelectWaypoint,
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: PoiCategory,
    nearest: List<PointOfInterest>,
    expanded: Boolean,
    currentProgressMeters: Double,
    distanceUnit: DistanceUnit,
    onToggleExpanded: () -> Unit,
    onSelectWaypoint: (PointOfInterest) -> Unit,
) {
    // Distance AHEAD of the driver right now, not the place's absolute position along the route.
    fun distanceAhead(poi: PointOfInterest) = (poi.distanceAlongRouteMeters - currentProgressMeters).coerceAtLeast(0.0)

    Card(elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
        Column(modifier = Modifier.width(220.dp)) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onToggleExpanded)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = poiCategoryIcon(category),
                    contentDescription = category.displayName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(text = category.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${nearest.first().name} - ${formatDistance(distanceAhead(nearest.first()), distanceUnit)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }

            if (expanded) {
                HorizontalDivider()
                nearest.forEach { poi ->
                    Row(
                        modifier = Modifier
                            .clickable { onSelectWaypoint(poi) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = poi.name, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = formatDistance(distanceAhead(poi), distanceUnit),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Text(
                            text = "Set as stop",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
