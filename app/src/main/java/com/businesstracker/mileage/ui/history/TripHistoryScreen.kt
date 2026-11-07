package com.businesstracker.mileage.ui.history

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.model.SortOrder
import com.businesstracker.mileage.ui.theme.SlateNeutral
import com.businesstracker.mileage.ui.theme.SpeedRed
import com.businesstracker.mileage.ui.theme.SucculentPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class HistoryFilterOption(val value: String, val label: String)

@Composable
fun TripHistoryScreen(
    viewModel: TripHistoryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.selectedTripForDetails != null) {
        TripDetailScreen(
            trip = state.selectedTripForDetails!!,
            onBack = { viewModel.selectTripForDetails(null) },
            onSaveTrip = { id, classification, entity, purpose, notes ->
                viewModel.updateTripDetails(id, classification, entity, purpose, notes)
            },
            onDeleteTrip = { id -> viewModel.deleteTrip(id) }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HistoryControls(
            state = state,
            onSearchChange = viewModel::setSearchQuery,
            onSortChange = viewModel::setSortOrder,
            onClassificationChange = viewModel::setClassificationFilter,
            onEntityChange = viewModel::setBusinessEntityFilter,
            onSyncChange = viewModel::setSyncStatusFilter
        )

        if (state.trips.isEmpty()) {
            EmptyHistory()
        } else {
            val groupByDate = state.sortOrder == SortOrder.NEWEST_FIRST ||
                state.sortOrder == SortOrder.OLDEST_FIRST

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (groupByDate) {
                    state.trips.groupBy { it.date }.forEach { (date, trips) ->
                        item(key = "date-$date") {
                            DateSectionHeader(date = date, trips = trips)
                        }
                        items(trips, key = { it.id }) { trip ->
                            TripHistoryItem(
                                trip = trip,
                                showDate = false,
                                onClick = { viewModel.selectTripForDetails(trip) },
                                onDelete = { viewModel.deleteTrip(trip.id) }
                            )
                        }
                    }
                } else {
                    items(state.trips, key = { it.id }) { trip ->
                        TripHistoryItem(
                            trip = trip,
                            showDate = true,
                            onClick = { viewModel.selectTripForDetails(trip) },
                            onDelete = { viewModel.deleteTrip(trip.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryControls(
    state: TripHistoryUiState,
    onSearchChange: (String) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onClassificationChange: (String) -> Unit,
    onEntityChange: (String) -> Unit,
    onSyncChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
            Text(
                text = "${state.trips.size} drives  •  ${formatMiles(state.totalFilteredMiles)} mi",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                placeholder = { Text("Search trips", maxLines = 1) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(19.dp))
                },
                trailingIcon = if (state.searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
            SortMenu(selected = state.sortOrder, onSelected = onSortChange)
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            item {
                CompactFilterMenu(
                    label = "Type",
                    selectedValue = state.selectedClassificationFilter,
                    options = listOf(
                        HistoryFilterOption("ALL", "All drives"),
                        HistoryFilterOption("BUSINESS", "Business"),
                        HistoryFilterOption("PERSONAL", "Personal"),
                        HistoryFilterOption("UNREVIEWED", "Unreviewed")
                    ),
                    onSelected = onClassificationChange
                )
            }
            item {
                CompactFilterMenu(
                    label = "Entity",
                    selectedValue = state.selectedBusinessEntityFilter,
                    options = listOf(HistoryFilterOption("ALL", "All entities")) +
                        BusinessEntity.entries.map { HistoryFilterOption(it.displayName, it.displayName) },
                    onSelected = onEntityChange
                )
            }
            item {
                CompactFilterMenu(
                    label = "Sync",
                    selectedValue = state.selectedSyncStatusFilter,
                    options = listOf(
                        HistoryFilterOption("ALL", "All status"),
                        HistoryFilterOption("SYNCED", "Synced"),
                        HistoryFilterOption("PENDING", "Pending"),
                        HistoryFilterOption("FAILED", "Failed")
                    ),
                    onSelected = onSyncChange
                )
            }
        }

        if (state.totalFilteredDeduction > 0) {
            Text(
                text = "Business deduction  ${formatCurrency(state.totalFilteredDeduction)}",
                modifier = Modifier.align(Alignment.End),
                style = MaterialTheme.typography.labelMedium,
                color = SucculentPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SortMenu(selected: SortOrder, onSelected: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Sort, contentDescription = "Sort trips", modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = order.displayName,
                            fontWeight = if (order == selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelected(order)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CompactFilterMenu(
    label: String,
    selectedValue: String,
    options: List<HistoryFilterOption>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.value == selectedValue } ?: options.first()
    Box {
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = if (selectedValue == "ALL") {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$label: ${selected.label}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(17.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            fontWeight = if (option.value == selectedValue) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelected(option.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DateSectionHeader(date: String, trips: List<TripEntity>) {
    val miles = trips.sumOf { it.totalDistanceMiles }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatHistoryDate(date),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${trips.size} ${if (trips.size == 1) "drive" else "drives"}  •  ${formatMiles(miles)} mi",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No trips found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Try changing the search or filters.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TripHistoryItem(
    trip: TripEntity,
    showDate: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeRange = remember(trip.startTime, trip.endTime) {
        "${timeFormatter.format(Date(trip.startTime))} – ${timeFormatter.format(Date(trip.endTime))}"
    }
    val isBusiness = trip.classification == "BUSINESS"
    val classificationColor = if (isBusiness) SucculentPrimary else SlateNeutral
    val start = trip.startAddress ?: "Recorded start location"
    val end = trip.endAddress ?: "Recorded destination"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = classificationColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isBusiness) trip.businessEntity else trip.classification.lowercase().replaceFirstChar { it.titlecase() },
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = classificationColor
                    )
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = if (trip.syncStatus == "SYNCED") Icons.Default.CloudDone else Icons.Default.CloudQueue,
                    contentDescription = trip.syncStatus,
                    tint = if (trip.syncStatus == "SYNCED") SucculentPrimary else SlateNeutral,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (showDate) "${formatHistoryDate(trip.date)}  •  $timeRange" else timeRange,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatMiles(trip.totalDistanceMiles)} mi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (trip.purpose.isNotBlank()) {
                Text(
                    text = trip.purpose,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = SlateNeutral,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "$start  →  $end",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isBusiness) {
                    Text(
                        text = formatCurrency(trip.calculatedDeduction),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SucculentPrimary
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete trip",
                        tint = SpeedRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatMiles(miles: Double): String = String.format(Locale.US, "%.1f", miles)

private fun formatCurrency(amount: Double): String = String.format(Locale.US, "$%.2f", amount)

private fun formatHistoryDate(date: String): String {
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val formatter = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        formatter.format(requireNotNull(parser.parse(date)))
    }.getOrDefault(date)
}
