package com.businesstracker.mileage.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.businesstracker.mileage.domain.usecase.ResolveTripAddressesUseCase
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.model.TripClassification
import com.businesstracker.mileage.domain.model.TripPurpose
import com.businesstracker.mileage.ui.components.TripRouteMap
import com.businesstracker.mileage.ui.theme.SlateNeutral
import com.businesstracker.mileage.ui.theme.SpeedRed
import com.businesstracker.mileage.ui.theme.SucculentPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripDetailScreen(
    trip: TripEntity,
    onBack: () -> Unit,
    onSaveTrip: (tripId: String, classification: String, businessEntity: String, purpose: String, notes: String?) -> Unit,
    onDeleteTrip: (tripId: String) -> Unit,
    modifier: Modifier = Modifier,
    addressViewModel: TripAddressViewModel = hiltViewModel(),
    customPurposes: List<String> = emptyList()
) {
    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var selectedDetailTab by remember(trip.id) { mutableIntStateOf(0) }
    val addressState by addressViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(trip.id, trip.startAddress, trip.endAddress) { addressViewModel.resolve(trip) }
    DisposableEffect(trip.id) { onDispose { addressViewModel.cancelLookup() } }
    val startAddress = addressState.takeIf { it.tripId == trip.id }?.start ?: trip.startAddress
    val endAddress = addressState.takeIf { it.tripId == trip.id }?.end ?: trip.endAddress

    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    val dateFormatted = remember(trip.startTime) {
        if (trip.startTime > 0) dateFormatter.format(Date(trip.startTime)) else trip.date
    }
    val startTimeStr = remember(trip.startTime) { timeFormatter.format(Date(trip.startTime)) }
    val endTimeStr = remember(trip.endTime) { timeFormatter.format(Date(trip.endTime)) }

    val durationMinutes = remember(trip.startTime, trip.endTime) {
        val diff = trip.endTime - trip.startTime
        if (diff > 0) TimeUnit.MILLISECONDS.toMinutes(diff) else 0L
    }

    val isBusiness = trip.classification == TripClassification.BUSINESS.value
    val classificationColor = if (isBusiness) SucculentPrimary else SlateNeutral

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Details", fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // One-line summary inspired by the compact trip receipt layout.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = dateFormatted.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (trip.startTime > 0) SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(trip.startTime)) else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateNeutral,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f", trip.totalDistanceMiles),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "MILES",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateNeutral,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "\$${String.format(Locale.US, "%.2f", trip.calculatedDeduction)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = SucculentPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "POTENTIAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateNeutral,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Route Map Card: Only renders actual coordinates, no fabricated curves
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp)
                ) {
                    TripRouteMap(trip = trip, modifier = Modifier.height(200.dp))
                }
            }

            // Route/status tabs with edit and delete actions on the far right.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TripDetailTabButton(
                        label = "Route",
                        icon = Icons.Default.LocationOn,
                        selected = selectedDetailTab == 0,
                        onClick = { selectedDetailTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    TripDetailTabButton(
                        label = "More info",
                        icon = Icons.Default.Info,
                        selected = selectedDetailTab == 1,
                        onClick = { selectedDetailTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    IconButton(onClick = { showEditSheet = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit trip")
                    }
                    IconButton(onClick = { showDeleteConfirmation = true }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete trip", tint = SpeedRed)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                if (selectedDetailTab == 0) {
                    RouteDetailsTab(
                        startAddress = startAddress?.takeUnless { ResolveTripAddressesUseCase.needsAddress(it) }
                            ?: if (addressState.tripId == trip.id && addressState.loading) "Finding start address…"
                            else "Approximate start location unavailable",
                        endAddress = endAddress?.takeUnless { ResolveTripAddressesUseCase.needsAddress(it) }
                            ?: if (addressState.tripId == trip.id && addressState.loading) "Finding destination address…"
                            else "Approximate destination unavailable",
                        startTime = startTimeStr,
                        endTime = endTimeStr
                    )
                } else {
                    TripStatusTab(
                        trip = trip,
                        durationMinutes = durationMinutes,
                        classificationColor = classificationColor
                    )
                }
            }

        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete this trip?") },
            text = { Text("This removes the trip from local history. It does not silently delete a Google Sheets row.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteTrip(trip.id)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpeedRed)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal Edit Sheet
    if (showEditSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var selectedClassification by remember { mutableStateOf(trip.classification) }
        var selectedEntity by remember { mutableStateOf(trip.businessEntity) }
        var selectedPurpose by remember { mutableStateOf(trip.purpose) }
        var notesText by remember { mutableStateOf(trip.notes.orEmpty()) }
        val purposes = remember(customPurposes, trip.purpose) {
            val list = (TripPurpose.DEFAULT_SUCCULENT_PURPOSES + customPurposes).toMutableList()
            if (trip.purpose.isNotBlank() && trip.purpose != "Personal" && !list.contains(trip.purpose)) {
                list.add(trip.purpose)
            }
            list.distinct()
        }

        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Edit Trip",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Classification Picker
                Text("Classification", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("BUSINESS", "PERSONAL").forEach { type ->
                        FilterChip(
                            selected = selectedClassification == type,
                            onClick = {
                                selectedClassification = type
                                if (type == "PERSONAL") {
                                    selectedEntity = BusinessEntity.PERSONAL.displayName
                                    selectedPurpose = "Personal"
                                } else {
                                    selectedEntity = BusinessEntity.MY_SUCCULENT_CORNER.displayName
                                    if (selectedPurpose == "Personal") selectedPurpose = TripPurpose.NURSERY_PICKUP
                                }
                            },
                            label = { Text(type) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (type == "BUSINESS") SucculentPrimary else SlateNeutral,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                if (selectedClassification == "BUSINESS") {
                    Text("Business Entity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedEntity == BusinessEntity.MY_SUCCULENT_CORNER.displayName,
                            onClick = { selectedEntity = BusinessEntity.MY_SUCCULENT_CORNER.displayName },
                            label = { Text(BusinessEntity.MY_SUCCULENT_CORNER.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SucculentPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    Text("Purpose", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        purposes.forEach { p ->
                            FilterChip(
                                selected = selectedPurpose == p,
                                onClick = { selectedPurpose = p },
                                label = { Text(p) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SucculentPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                Text("Notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add trip notes...") },
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showEditSheet = false }) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            showEditSheet = false
                            onSaveTrip(
                                trip.id,
                                selectedClassification,
                                selectedEntity,
                                selectedPurpose,
                                notesText.ifBlank { null }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
                    ) {
                        Text("Save Changes", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun TripDetailTabButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(
                color = if (selected) SucculentPrimary.copy(alpha = 0.12f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (selected) SucculentPrimary else SlateNeutral
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) SucculentPrimary else SlateNeutral
        )
    }
}

@Composable
private fun RouteDetailsTab(
    startAddress: String,
    endAddress: String,
    startTime: String,
    endTime: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DestinationRow(
            label = "Start",
            address = startAddress,
            time = startTime,
            color = SucculentPrimary,
            isStart = true
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        DestinationRow(
            label = "Destination",
            address = endAddress,
            time = endTime,
            color = SpeedRed,
            isStart = false
        )
    }
}

@Composable
private fun DestinationRow(
    label: String,
    address: String,
    time: String,
    color: Color,
    isStart: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(color.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isStart) {
                Box(modifier = Modifier.size(9.dp).background(color, CircleShape))
            } else {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = SlateNeutral)
            Text(
                address,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            time,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TripStatusTab(
    trip: TripEntity,
    durationMinutes: Long,
    classificationColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatusValue("Trip type", trip.classification, Modifier.weight(1f), classificationColor)
            StatusValue("Sync status", trip.syncStatus, Modifier.weight(1f), if (trip.syncStatus == "SYNCED") SucculentPrimary else SlateNeutral)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatusValue("Duration", "$durationMinutes min", Modifier.weight(1f))
            StatusValue("Tracking", trip.trackingMethod, Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatusValue("Purpose", trip.purpose, Modifier.weight(1f))
            StatusValue("IRS rate", "\$${trip.applicableRate} / mi", Modifier.weight(1f))
        }
        if (!trip.notes.isNullOrBlank()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            StatusValue("Notes", trip.notes, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatusValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier.padding(end = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SlateNeutral)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
