package com.reststop.countdown.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reststop.countdown.data.model.PoiCategory

/**
 * A collapsed-by-default menu for the "other places along the route" checkboxes, so it doesn't
 * sit on screen the whole drive. Results themselves show as floating [CategoryChip]s over the
 * map, not here - this panel is purely for turning categories on/off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PoiPanel(
    isMenuOpen: Boolean,
    selectedCategories: Set<PoiCategory>,
    loadingCategory: PoiCategory?,
    onToggleMenu: () -> Unit,
    onToggleCategory: (PoiCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleMenu),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = if (selectedCategories.isEmpty()) "Show places along the route" else "${selectedCategories.size} shown along the route",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Icon(
                    imageVector = if (isMenuOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isMenuOpen) "Collapse" else "Expand",
                )
            }

            if (isMenuOpen) {
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
            }
        }
    }
}
