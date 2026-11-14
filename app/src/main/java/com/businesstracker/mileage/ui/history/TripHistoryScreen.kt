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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
    onNavigateToSavedLocations: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.recentlyDeletedTrip) {
        val deleted = state.recentlyDeletedTrip
        if (deleted != null && state.restoreFailedTrip == null) {
            val result = snackbarHostState.showSnackbar(
                message = "Trip deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDeleteTrip()
            } else {
                viewModel.dismissRecentlyDeletedTrip(deleted.id)
            }
        }
    }

    LaunchedEffect(state.restoreFailedTrip) {
        val failed = state.restoreFailedTrip
        if (failed != null) {
            val result = snackbarHostState.showSnackbar(
                message = "Unable to restore trip. Please try again.",
                actionLabel = "Retry",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDeleteTrip()
            } else {
                viewModel.clearRestoreFailure()
            }
        }
    }

    LaunchedEffect(state.actionErrorMessage) {
        val errorMsg = state.actionErrorMessage
        if (errorMsg != null && state.restoreFailedTrip == null) {
            snackbarHostState.showSnackbar(
                message = errorMsg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearActionErrorMessage()
        }
    }

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
            customPurposes = state.customPurposes,
            smartNames = state.smartNamesMap[selectedTrip.id],
            onSaveLocation = { name, lat, lng, address, radius, icon, pref, entity, onSuccess, onError ->
                viewModel.saveLocationFromTrip(name, lat, lng, address, radius, icon, pref, entity,
                    onSuccess = onSuccess, onError = onError)
            },
            onNavigateToSavedLocations = { locationId ->
                viewModel.selectTripForDetails(null)
                onNavigateToSavedLocations?.invoke(locationId)
            }
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                onMonthChange = { month ->
                    if (month.isBlank()) viewModel.setDateRange("", "")
                    else {
                        val selected = YearMonth.parse(month)
                        viewModel.setDateRange(selected.atDay(1).toString(), selected.atEndOfMonth().toString())
                    }
                },
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
                                    smartNames = state.smartNamesMap[trip.id],
                                    showDate = false,
                                    onClick = { viewModel.selectTripForDetails(trip) }
                                )
                            }
                        }
                    } else {
                        items(state.trips, key = { it.id }) { trip ->
                            TripHistoryItem(
                                trip = trip,
                                smartNames = state.smartNamesMap[trip.id],
                                showDate = true,
                                onClick = { viewModel.selectTripForDetails(trip) }
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
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
    onMonthChange: (String) -> Unit,
    onBackToReports: (() -> Unit)?
) {
    var isSortExpanded by remember { mutableStateOf(false) }
    var openPicker by remember { mutableStateOf<String?>(null) }
    var choseAllMonths by remember { mutableStateOf(false) }
    var choseAllCategories by remember { mutableStateOf(false) }

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

        AnimatedVisibility(
            visible = isSortExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Months", "Category", "Sort").forEach { picker ->
                    val label = when (picker) {
                        "Months" -> state.startDateFilter.takeIf { it.isNotBlank() }?.let(::formatMonthFilter)
                            ?: if (choseAllMonths) "All months" else "Months"
                        "Category" -> state.selectedClassificationFilter.takeIf { it != "ALL" }
                            ?.lowercase()?.replaceFirstChar { it.uppercase() }
                            ?: if (choseAllCategories) "All" else "Category"
                        else -> when (state.sortOrder) {
                            SortOrder.HIGHEST_MILEAGE -> "High to low"
                            SortOrder.LOWEST_MILEAGE -> "Low to high"
                            SortOrder.MOST_MONEY -> "Most money"
                            SortOrder.LEAST_MONEY -> "Least money"
                            else -> "Sort"
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f).height(48.dp).clickable { openPicker = picker },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = SucculentPrimary.copy(alpha = 0.14f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold, color = SucculentPrimary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        if (openPicker != null) {
            val picker = openPicker!!
            val options: List<Pair<String, String>> = when (picker) {
                "Months" -> listOf("All months" to "") + state.availableMonths.map { formatMonthFilter("$it-01") to it }
                "Category" -> listOf("All" to "ALL", "Business" to "BUSINESS", "Personal" to "PERSONAL")
                else -> listOf(
                    "High to low" to SortOrder.HIGHEST_MILEAGE.name,
                    "Low to high" to SortOrder.LOWEST_MILEAGE.name,
                    "Most money" to SortOrder.MOST_MONEY.name,
                    "Least money" to SortOrder.LEAST_MONEY.name
                )
            }
            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { openPicker = null }) {
                Text(
                    picker,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(options.size) { index ->
                        val (label, value) = options[index]
                        val selected = when (picker) {
                            "Months" -> state.startDateFilter.startsWith(value) && (value.isNotEmpty() || state.startDateFilter.isBlank())
                            "Category" -> state.selectedClassificationFilter == value
                            else -> state.sortOrder.name == value
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                .clickable {
                                    when (picker) {
                                        "Months" -> { choseAllMonths = value.isBlank(); onMonthChange(value) }
                                        "Category" -> { choseAllCategories = value == "ALL"; onClassificationChange(value) }
                                        else -> onSortChange(SortOrder.valueOf(value))
                                    }
                                    openPicker = null
                                }
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, modifier = Modifier.weight(1f), color = if (selected) SucculentPrimary else MaterialTheme.colorScheme.onSurface)
                            if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = SucculentPrimary)
                        }
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
    smartNames: com.businesstracker.mileage.domain.usecase.SmartTripNames? = null,
    showDate: Boolean,
    onClick: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeRange = remember(trip.startTime, trip.endTime) {
        "${timeFormatter.format(Date(trip.startTime))} – ${timeFormatter.format(Date(trip.endTime))}"
    }
    val isBusiness = trip.classification == "BUSINESS"
    val badgeIcon = when (trip.classification) {
        "BUSINESS" -> Icons.Default.BusinessCenter
        "PERSONAL" -> Icons.Default.Person
        else -> Icons.Default.Info
    }
    val badgeColor = if (isBusiness) SucculentPrimary else Color(0xFF2563EB)
    val start = smartNames?.startDisplayName ?: (trip.startAddress ?: "Recorded start location")
    val end = smartNames?.endDisplayName ?: (trip.endAddress ?: "Recorded destination")

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
                    HistoryLocationLine(address = start, tint = SucculentPrimary, isStart = true)
                    HistoryLocationLine(address = end, tint = Color(0xFFCC5B77), isStart = false)
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
                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            badgeIcon,
                            contentDescription = if (isBusiness) "Business trip" else if (trip.classification == "PERSONAL") "Personal trip" else "Unreviewed trip",
                            tint = if (trip.classification == "UNREVIEWED") MaterialTheme.colorScheme.onSurfaceVariant else badgeColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryLocationLine(address: String, tint: androidx.compose.ui.graphics.Color, isStart: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isStart) {
            com.businesstracker.mileage.ui.components.StartBeaconIcon(tint = tint, size = 16.dp)
        } else {
            com.businesstracker.mileage.ui.components.EndFlagIcon(tint = tint, size = 16.dp)
        }
        Spacer(Modifier.width(6.dp))
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
