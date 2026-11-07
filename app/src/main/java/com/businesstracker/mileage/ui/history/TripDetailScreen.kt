package com.businesstracker.mileage.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.material3.OutlinedButton
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
    addressViewModel: TripAddressViewModel = hiltViewModel()
) {
    var showEditSheet by remember { mutableStateOf(false) }
    val addressState by addressViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(trip.id, trip.startAddress, trip.endAddress) { addressViewModel.resolve(trip) }
    DisposableEffect(trip.id) { onDispose { addressViewModel.cancelLookup() } }
    val startAddress = addressState.takeIf { it.tripId == trip.id }?.start ?: trip.startAddress
    val endAddress = addressState.takeIf { it.tripId == trip.id }?.end ?: trip.endAddress

    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()) }

    val dateFormatted = remember(trip.startTime) {
        if (trip.startTime > 0) dateFormatter.format(Date(trip.startTime)) else trip.date
    }
    val timeRangeStr = remember(trip.startTime, trip.endTime) {
        "${timeFormatter.format(Date(trip.startTime))} – ${timeFormatter.format(Date(trip.endTime))}"
    }

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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditSheet = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Trip")
                    }
                    IconButton(onClick = { onDeleteTrip(trip.id); onBack() }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Trip", tint = SpeedRed)
                    }
                }
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card: Date, Classification & Miles
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = classificationColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isBusiness) trip.businessEntity else trip.classification,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = classificationColor
                            )
                        }

                        // Sync Status Badge
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (trip.syncStatus == "SYNCED") Icons.Default.CloudDone else Icons.Default.CloudQueue,
                                contentDescription = trip.syncStatus,
                                tint = if (trip.syncStatus == "SYNCED") SucculentPrimary else SlateNeutral,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = trip.syncStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (trip.syncStatus == "SYNCED") SucculentPrimary else SlateNeutral,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "TOTAL DISTANCE",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                color = SlateNeutral,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = String.format(Locale.US, "%.1f", trip.totalDistanceMiles),
                                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "miles",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SlateNeutral,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }

                        if (isBusiness) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "ESTIMATED DEDUCTION",
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                    color = SucculentPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "\$${String.format(Locale.US, "%.2f", trip.calculatedDeduction)}",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        color = SucculentPrimary
                                    )
                                )
                            }
                        }
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "GPS Route Map",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TripRouteMap(trip = trip, modifier = Modifier.height(260.dp))
                }
            }

            // Locations & Times Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Time and Duration
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = SlateNeutral, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Time & Duration", style = MaterialTheme.typography.labelSmall, color = SlateNeutral)
                            Text(text = "$timeRangeStr (${durationMinutes} min)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Start Address
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(SucculentPrimary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(8.dp).background(SucculentPrimary, CircleShape))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Start Location", style = MaterialTheme.typography.labelSmall, color = SlateNeutral)
                            Text(
                                text = startAddress?.takeUnless { ResolveTripAddressesUseCase.needsAddress(it) }
                                    ?: if (addressState.tripId == trip.id && addressState.loading) "Finding start address…"
                                    else "Address unavailable (GPS: ${String.format(Locale.US, "%.4f", trip.startLatitude)}, ${String.format(Locale.US, "%.4f", trip.startLongitude)})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // End Address
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(SpeedRed.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = SpeedRed, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Ending Destination", style = MaterialTheme.typography.labelSmall, color = SlateNeutral)
                            Text(
                                text = endAddress?.takeUnless { ResolveTripAddressesUseCase.needsAddress(it) }
                                    ?: if (addressState.tripId == trip.id && addressState.loading) "Finding destination address…"
                                    else "Address unavailable (GPS: ${String.format(Locale.US, "%.4f", trip.endLatitude)}, ${String.format(Locale.US, "%.4f", trip.endLongitude)})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Classification, Purpose & Tax Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(text = "Tax & Classification Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Business Entity", style = MaterialTheme.typography.labelSmall, color = SlateNeutral)
                            Text(trip.businessEntity, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Purpose", style = MaterialTheme.typography.labelSmall, color = SlateNeutral)
                            Text(trip.purpose, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("IRS Rate Applied", style = MaterialTheme.typography.labelSmall, color = SlateNeutral)
                            Text("\$${trip.applicableRate} / mi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Tracking Method", style = MaterialTheme.typography.labelSmall, color = SlateNeutral)
                            Text(trip.trackingMethod, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (!trip.notes.isNullOrBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Column {
                            Text("Notes", style = MaterialTheme.typography.labelSmall, color = SlateNeutral)
                            Text(trip.notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Edit Action Button
            Button(
                onClick = { showEditSheet = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Classification & Details", fontWeight = FontWeight.Bold)
            }
        }
    }

    // Modal Edit Sheet
    if (showEditSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var selectedClassification by remember { mutableStateOf(trip.classification) }
        var selectedEntity by remember { mutableStateOf(trip.businessEntity) }
        var selectedPurpose by remember { mutableStateOf(trip.purpose) }
        var notesText by remember { mutableStateOf(trip.notes.orEmpty()) }
        val purposes = remember { TripPurpose.DEFAULT_SUCCULENT_PURPOSES }

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
