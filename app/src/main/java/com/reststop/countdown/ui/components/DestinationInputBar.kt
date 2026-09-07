package com.reststop.countdown.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reststop.countdown.data.model.PlaceSuggestion

/**
 * Pre-trip overlay: destination entry (with Places autocomplete, so a business/landmark name
 * works just as well as a street address) + "Start Trip", which triggers the one-time route
 * fetch.
 */
@Composable
fun DestinationInputBar(
    destination: String,
    suggestions: List<PlaceSuggestion>,
    isLoading: Boolean,
    errorMessage: String?,
    onDestinationChanged: (String) -> Unit,
    onSuggestionSelected: (PlaceSuggestion) -> Unit,
    onStartTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Where are you headed?", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = destination,
                    onValueChange = onDestinationChanged,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Destination name or address") },
                    enabled = !isLoading,
                )
                Button(
                    onClick = onStartTrip,
                    modifier = Modifier.padding(start = 8.dp),
                    enabled = !isLoading && destination.isNotBlank(),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text("Start Trip")
                    }
                }
            }

            if (suggestions.isNotEmpty() && !isLoading) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                // A plain Column, not LazyColumn: this sits inside a parent that's already
                // vertically scrollable (unbounded height), which LazyColumn can't measure
                // against. Autocomplete only ever returns a handful of rows, so no need for
                // list virtualization here.
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    suggestions.take(5).forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuggestionSelected(suggestion) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(text = suggestion.primaryText, style = MaterialTheme.typography.bodyMedium)
                                if (suggestion.secondaryText.isNotBlank()) {
                                    Text(text = suggestion.secondaryText, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
