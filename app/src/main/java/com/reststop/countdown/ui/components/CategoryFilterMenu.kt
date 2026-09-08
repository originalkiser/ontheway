package com.reststop.countdown.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reststop.countdown.data.model.PoiCategory

/**
 * A small corner button that opens a dropdown of "show along the route" category checkboxes,
 * instead of a card sitting inline over the map - it takes essentially no layout space when
 * closed, and the dropdown itself floats as a popup rather than pushing other content around.
 */
@Composable
fun CategoryFilterMenu(
    isMenuOpen: Boolean,
    selectedCategories: Set<PoiCategory>,
    loadingCategory: PoiCategory?,
    onToggleMenu: () -> Unit,
    onToggleCategory: (PoiCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        SmallFloatingActionButton(
            onClick = onToggleMenu,
            containerColor = if (selectedCategories.isEmpty()) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ) {
            Icon(imageVector = Icons.Filled.Tune, contentDescription = "Show places along the route")
        }

        DropdownMenu(expanded = isMenuOpen, onDismissRequest = onToggleMenu) {
            Text(
                text = "Show along the route",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PoiCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = category in selectedCategories, onCheckedChange = null)
                            Text(text = category.displayName)
                            if (loadingCategory == category) {
                                CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp).size(14.dp))
                            }
                        }
                    },
                    onClick = { onToggleCategory(category) },
                )
            }
        }
    }
}
