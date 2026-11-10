package com.businesstracker.mileage.ui.history
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

private data class HistoryFilterOption(val value: String, val label: String)

@Composable
fun TripHistoryScreen(
    viewModel: TripHistoryViewModel = hiltViewModel(),
    initialMonthIso: String = "",
    onBackToReports: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tripPendingDeletion by remember { mutableStateOf<TripEntity?>(null) }

    LaunchedEffect(initialMonthIso) {
        if (initialMonthIso.matches(Regex("\\d{4}-\\d{2}"))) {
            val month = YearMonth.parse(initialMonthIso)
            viewModel.setDateRange(month.atDay(1).toString(), month.atEndOfMonth().toString())
        }
    }

    if (state.selectedTripForDetails != null) {
        val selectedTrip = state.selectedTripForDetails!!
        val currentTripIndex = state.trips.indexOfFirst { it.id == selectedTrip.id }
        val onPreviousTrip: (() -> Unit)? = if (currentTripIndex > 0) {
            { viewModel.selectTripForDetails(state.trips[currentTripIndex - 1]) }
        } else null
        val onNextTrip: (() -> Unit)? = if (currentTripIndex >= 0 && currentTripIndex < state.trips.size - 1) {
            { viewModel.selectTripForDetails(state.trips[currentTripIndex + 1]) }
        } else null

        TripDetailScreen(
            trip = selectedTrip,
            onBack = { viewModel.selectTripForDetails(null) },
            onPreviousTrip = onPreviousTrip,
            onNextTrip = onNextTrip,
            onSaveTrip = { id, classification, entity, purpose, notes, onError, onSuccess ->
                viewModel.updateTripDetails(id, classification, entity, purpose, notes, onError, onSuccess)
            },
            onDeleteTrip = { id -> viewModel.deleteTrip(id) },
            customPurposes = state.customPurposes
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (tripPendingDeletion != null) Modifier.blur(8.dp) else Modifier)
            .background(MaterialTheme.colorScheme.background)
    ) {
        HistoryControls(
            state = state,
            onSearchChange = viewModel::setSearchQuery,
            onSortChange = viewModel::setSortOrder,
            onClassificationChange = viewModel::setClassificationFilter,
            onEntityChange = viewModel::setBusinessEntityFilter,
            onSyncChange = viewModel::setSyncStatusFilter,
            onClearDateFilter = { viewModel.setDateRange("", "") },
            onBackToReports = onBackToReports
        )

        if (state.trips.isEmpty()) {
            EmptyHistory(
                state = state,
                onClearSearch = { viewModel.setSearchQuery("") },
                onClearFilters = { viewModel.clearAllFilters() }
            )
        } else {
            val groupByDate = state.sortOrder == SortOrder.NEWEST_FIRST ||
                state.sortOrder == SortOrder.OLDEST_FIRST

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
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
                                onDelete = { tripPendingDeletion = trip }
                            )
                        }
                    }
                } else {
                    items(state.trips, key = { it.id }) { trip ->
                        TripHistoryItem(
                            trip = trip,
                            showDate = true,
                            onClick = { viewModel.selectTripForDetails(trip) },
                            onDelete = { tripPendingDeletion = trip }
                        )
                    }
                }
            }
        }
    }

    if (tripPendingDeletion != null) {
        val targetTrip = tripPendingDeletion!!
        Dialog(
            onDismissRequest = { tripPendingDeletion = null },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { tripPendingDeletion = null },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {},
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                    tonalElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Delete Trip",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Delete this ${formatMiles(targetTrip.totalDistanceMiles)} mi trip? This action cannot be undone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { tripPendingDeletion = null }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val idToDelete = targetTrip.id
                                    tripPendingDeletion = null
                                    viewModel.deleteTrip(idToDelete)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SpeedRed)
                            ) {
                                Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
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
    onSyncChange: (String) -> Unit,
    onClearDateFilter: () -> Unit,
    onBackToReports: (() -> Unit)?
) {
    var isSortExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trip History",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (onBackToReports != null) {
                TextButton(onClick = onBackToReports) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Back to Reports")
                }
            } else {
                Text(
                    text = "${state.trips.size} drives  •  ${formatMiles(state.totalFilteredMiles)} mi",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Search Bar & Sort Toggle Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (state.searchQuery.isEmpty()) {
                            Text(
                                text = "Search trips",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = state.searchQuery,
                            onValueChange = onSearchChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(SucculentPrimary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (state.searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { onSearchChange("") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Collapsible sort toggle button
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { isSortExpanded = !isSortExpanded },
                shape = RoundedCornerShape(12.dp),
                color = if (isSortExpanded) SucculentPrimary else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (isSortExpanded) SucculentPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort and filter options",
                        tint = if (isSortExpanded) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Active Month Filter Badge (if present)
        if (state.startDateFilter.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onClearDateFilter),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Month: ${formatMonthFilter(state.startDateFilter)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear month filter",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Collapsible 2-Row Sort & Filter Section
        AnimatedVisibility(
            visible = isSortExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: Classification filters (All, Business, Personal, Unreviewed)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    item {
                        SortFilterPill(
                            label = "All",
                            icon = Icons.Default.DirectionsCar,
                            isSelected = state.selectedClassificationFilter == "ALL",
                            onClick = { onClassificationChange("ALL") }
                        )
                    }
                    item {
                        SortFilterPill(
                            label = "Business",
                            icon = Icons.Default.BusinessCenter,
                            isSelected = state.selectedClassificationFilter == "BUSINESS",
                            onClick = {
                                onClassificationChange(if (state.selectedClassificationFilter == "BUSINESS") "ALL" else "BUSINESS")
                            }
                        )
                    }
                    item {
                        SortFilterPill(
                            label = "Personal",
                            icon = Icons.Default.Person,
                            isSelected = state.selectedClassificationFilter == "PERSONAL",
                            onClick = {
                                onClassificationChange(if (state.selectedClassificationFilter == "PERSONAL") "ALL" else "PERSONAL")
                            }
                        )
                    }
                    item {
                        SortFilterPill(
                            label = "Unreviewed",
                            icon = Icons.Default.Info,
                            isSelected = state.selectedClassificationFilter == "UNREVIEWED",
                            onClick = {
                                onClassificationChange(if (state.selectedClassificationFilter == "UNREVIEWED") "ALL" else "UNREVIEWED")
                            }
                        )
                    }
                }

                // Row 2: Sort options (Newest, Oldest, Highest, Lowest)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    item {
                        SortFilterPill(
                            label = "Newest",
                            icon = Icons.Default.DateRange,
                            isSelected = state.sortOrder == SortOrder.NEWEST_FIRST,
                            onClick = { onSortChange(SortOrder.NEWEST_FIRST) }
                        )
                    }
                    item {
                        SortFilterPill(
                            label = "Oldest",
                            icon = Icons.Default.History,
                            isSelected = state.sortOrder == SortOrder.OLDEST_FIRST,
                            onClick = { onSortChange(SortOrder.OLDEST_FIRST) }
                        )
                    }
                    item {
                        SortFilterPill(
                            label = "Highest",
                            icon = Icons.Default.KeyboardArrowUp,
                            isSelected = state.sortOrder == SortOrder.HIGHEST_MILEAGE,
                            onClick = { onSortChange(SortOrder.HIGHEST_MILEAGE) }
                        )
                    }
                    item {
                        SortFilterPill(
                            label = "Lowest",
                            icon = Icons.Default.KeyboardArrowDown,
                            isSelected = state.sortOrder == SortOrder.LOWEST_MILEAGE,
                            onClick = { onSortChange(SortOrder.LOWEST_MILEAGE) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortFilterPill(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) SucculentPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
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
private fun EmptyHistory(
    state: TripHistoryUiState,
    onClearSearch: () -> Unit,
    onClearFilters: () -> Unit
) {
    val isFilterActive = state.selectedClassificationFilter != "ALL" ||
        state.selectedBusinessEntityFilter != "ALL" ||
        state.selectedSyncStatusFilter != "ALL" ||
        state.startDateFilter.isNotBlank() ||
        state.endDateFilter.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            state.totalRecordedTripsCount == 0 -> {
                Text(
                    "No trips recorded yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your drives will appear here automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            state.searchQuery.isNotBlank() -> {
                Text(
                    "No matching results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "No trips match your search \"${state.searchQuery}\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onClearSearch,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Clear Search")
                }
            }
            isFilterActive -> {
                Text(
                    "No matching trips",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "No trips match the selected filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onClearFilters,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Clear Filters")
                }
            }
            else -> {
                Text(
                    "No trips found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Try changing the search or filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
    val badgeIcon = if (isBusiness) Icons.Default.BusinessCenter else Icons.Default.Person
    val badgeColor = if (isBusiness) SucculentPrimary else Color(0xFF2563EB)
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
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = if (isBusiness) "Business" else "Personal",
                            tint = badgeColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
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

            if (isBusiness && trip.purpose.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = trip.purpose,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
                    )
                    Text(
                        text = formatCurrency(trip.calculatedDeduction),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SucculentPrimary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    HistoryLocationLine(address = start, tint = SucculentPrimary)
                    HistoryLocationLine(address = end, tint = SpeedRed)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (isBusiness && trip.purpose.isBlank()) {
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
}

@Composable
private fun HistoryLocationLine(address: String, tint: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = address,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

private fun formatMonthFilter(startDate: String): String {
    return runCatching {
        val month = YearMonth.parse(startDate.take(7), DateTimeFormatter.ofPattern("yyyy-MM"))
        "${month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${month.year}"
    }.getOrDefault(startDate.take(7))
}
