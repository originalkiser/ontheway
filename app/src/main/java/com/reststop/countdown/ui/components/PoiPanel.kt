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

/**
 * Checkbox row for optional "other places along the route" categories, plus - for each checked
 * category - a compact summary card: category icon/name, the nearest not-yet-passed place, and
 * the second-nearest. Deliberately not a scrolling list of every match (that was overwhelming);
 * the full set still exists on the map as color-coded pins.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PoiPanel(
    selectedCategories: Set<PoiCategory>,
    loadingCategory: PoiCategory?,
    upcomingPoisByCategory: Map<PoiCategory, List<PointOfInterest>>,
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    selectedCategories.forEach { category ->
                        upcomingPoisByCategory[category]?.let { nearest ->
                            CategorySummaryRow(category, nearest, distanceUnit)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySummaryRow(category: PoiCategory, nearest: List<PointOfInterest>, distanceUnit: DistanceUnit) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = poiCategoryIcon(category),
            contentDescription = category.displayName,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = category.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            nearest.getOrNull(0)?.let { first ->
                Text(
                    text = "${first.name} - ${formatDistance(first.distanceAlongRouteMeters, distanceUnit)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            nearest.getOrNull(1)?.let { second ->
                Text(
                    text = "Then ${second.name} - ${formatDistance(second.distanceAlongRouteMeters, distanceUnit)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
