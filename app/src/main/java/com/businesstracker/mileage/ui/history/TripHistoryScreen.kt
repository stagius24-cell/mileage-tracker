package com.businesstracker.mileage.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.model.SortOrder
import com.businesstracker.mileage.ui.theme.SlateNeutral
import com.businesstracker.mileage.ui.theme.SpeedRed
import com.businesstracker.mileage.ui.theme.SucculentPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripHistoryScreen(
    viewModel: TripHistoryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // If a trip is selected for details, render TripDetailScreen
    if (state.selectedTripForDetails != null) {
        TripDetailScreen(
            trip = state.selectedTripForDetails!!,
            onBack = { viewModel.selectTripForDetails(null) },
            onSaveTrip = { id, classification, entity, purpose, notes ->
                viewModel.updateTripDetails(id, classification, entity, purpose, notes)
            },
            onDeleteTrip = { id ->
                viewModel.deleteTrip(id)
            }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header, Search & Filter Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trip History",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                // Sort Dropdown Button
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { sortMenuExpanded = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = state.sortOrder.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.displayName) },
                                onClick = {
                                    viewModel.setSortOrder(order)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search address, purpose, entity, notes...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            // Classification Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                val filters = listOf(
                    "ALL" to "All Drives",
                    "BUSINESS" to "Business",
                    "PERSONAL" to "Personal",
                    "UNREVIEWED" to "Unreviewed"
                )

                items(filters) { (key, label) ->
                    val isSelected = state.selectedClassificationFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setClassificationFilter(key) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (key == "BUSINESS") SucculentPrimary else MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Sync Status & Business Entity Sub-filters
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val syncOptions = listOf("ALL" to "All Sync", "SYNCED" to "Synced", "PENDING" to "Pending", "FAILED" to "Failed")
                items(syncOptions) { (key, label) ->
                    val isSelected = state.selectedSyncStatusFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setSyncStatusFilter(key) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            // Summary metrics bar for filtered results
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${state.trips.size} drives • ${state.totalFilteredMiles} mi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
                if (state.totalFilteredDeduction > 0) {
                    Text(
                        text = "Deduction: \$${String.format(Locale.US, "%.2f", state.totalFilteredDeduction)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SucculentPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Trips List
        if (state.trips.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No trips found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Try adjusting your search query or filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.trips, key = { it.id }) { trip ->
                    TripHistoryItem(
                        trip = trip,
                        onClick = { viewModel.selectTripForDetails(trip) },
                        onDelete = { viewModel.deleteTrip(trip.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TripHistoryItem(
    trip: TripEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeRangeStr = remember(trip.startTime, trip.endTime) {
        "${timeFormatter.format(Date(trip.startTime))} – ${timeFormatter.format(Date(trip.endTime))}"
    }

    val isBusiness = trip.classification == "BUSINESS"
    val classificationColor = if (isBusiness) SucculentPrimary else SlateNeutral

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = classificationColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isBusiness) trip.businessEntity else trip.classification,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = classificationColor
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Cloud Sync Badge
                    Icon(
                        imageVector = if (trip.syncStatus == "SYNCED") Icons.Default.CloudDone else Icons.Default.CloudQueue,
                        contentDescription = trip.syncStatus,
                        tint = if (trip.syncStatus == "SYNCED") SucculentPrimary else SlateNeutral,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", trip.totalDistanceMiles)} mi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (isBusiness) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "\$${String.format(Locale.US, "%.2f", trip.calculatedDeduction)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = SucculentPrimary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = "${trip.date} • $timeRangeStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Text(
                text = trip.purpose,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            // Start & End addresses
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = SlateNeutral,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "From: ${trip.startAddress ?: "Recorded GPS (${String.format(Locale.US, "%.4f", trip.startLatitude)}, ${String.format(Locale.US, "%.4f", trip.startLongitude)})"}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                    Text(
                        text = "To: ${trip.endAddress ?: "Recorded GPS (${String.format(Locale.US, "%.4f", trip.endLatitude)}, ${String.format(Locale.US, "%.4f", trip.endLongitude)})"}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap to view route & details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Trip",
                        tint = SpeedRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
