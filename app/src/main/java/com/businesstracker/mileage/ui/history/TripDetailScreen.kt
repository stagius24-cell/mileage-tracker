package com.businesstracker.mileage.ui.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.BusinessCenter
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
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
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripDetailScreen(
    trip: TripEntity,
    onBack: () -> Unit,
    onSaveTrip: (
        tripId: String,
        classification: String,
        businessEntity: String,
        purpose: String,
        notes: String?,
        onError: ((String) -> Unit)?,
        onSuccess: (() -> Unit)?
    ) -> Unit,
    onDeleteTrip: (tripId: String) -> Unit,
    modifier: Modifier = Modifier,
    onPreviousTrip: (() -> Unit)? = null,
    onNextTrip: (() -> Unit)? = null,
    addressViewModel: TripAddressViewModel = hiltViewModel(),
    customPurposes: List<String> = emptyList(),
    smartNames: com.businesstracker.mileage.domain.usecase.SmartTripNames? = null,
    onSaveLocation: ((name: String, lat: Double, lng: Double, address: String, radius: Int, icon: String, pref: String?, entity: String?, onSuccess: (String) -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    onNavigateToSavedLocations: ((String) -> Unit)? = null
) {
    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var locationSaveError by remember { mutableStateOf<String?>(null) }
    var locationSaving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
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

    var outerDragOffset by remember(trip.id) { mutableFloatStateOf(0f) }
    var suggestionDismissed by remember(trip.id) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(trip.id, onPreviousTrip, onNextTrip) {
                detectHorizontalDragGestures(
                    onDragStart = { outerDragOffset = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        outerDragOffset += dragAmount
                    },
                    onDragEnd = {
                        val threshold = 90f
                        if (outerDragOffset > threshold && onPreviousTrip != null) {
                            onPreviousTrip()
                        } else if (outerDragOffset < -threshold && onNextTrip != null) {
                            onNextTrip()
                        }
                        outerDragOffset = 0f
                    },
                    onDragCancel = { outerDragOffset = 0f }
                )
            }
            .verticalScroll(rememberScrollState())
    ) {
        // The route map leads the screen, with a close action layered above it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            TripRouteMap(trip = trip, modifier = Modifier.fillMaxSize())
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(44.dp)
                    .graphicsLayer { alpha = 0.85f },
                shape = CircleShape,
                color = SucculentPrimary.copy(alpha = 0.85f),
                shadowElevation = 3.dp
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close trip details",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Classification Suggestion Card for Unreviewed Trips
            val isUnreviewed = trip.classification == TripClassification.UNREVIEWED.value
            val suggestedClassification = smartNames?.suggestedClassification
            if (isUnreviewed && suggestedClassification != null && !suggestionDismissed) {
                val isBizSuggestion = suggestedClassification.equals("BUSINESS", ignoreCase = true)
                val targetEntity = if (isBizSuggestion) {
                    smartNames.suggestedBusinessEntity ?: trip.businessEntity.ifEmpty { BusinessEntity.MY_SUCCULENT_CORNER.displayName }
                } else {
                    BusinessEntity.PERSONAL.displayName
                }
                val targetPurpose = if (isBizSuggestion) {
                    trip.purpose.ifEmpty { TripPurpose.NURSERY_PICKUP }
                } else {
                    "Personal Drive"
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBizSuggestion) SucculentPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, if (isBizSuggestion) SucculentPrimary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isBizSuggestion) Icons.Default.BusinessCenter else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isBizSuggestion) SucculentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Suggested Classification",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = if (isBizSuggestion) {
                                "Based on your saved location, classify as Business ($targetEntity)?"
                            } else {
                                "Based on your saved location, classify as Personal?"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { suggestionDismissed = true },
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onSaveTrip(
                                        trip.id,
                                        if (isBizSuggestion) TripClassification.BUSINESS.value else TripClassification.PERSONAL.value,
                                        targetEntity,
                                        targetPurpose,
                                        trip.notes,
                                        { /* Keep the suggestion visible so the user can retry. */ },
                                        { suggestionDismissed = true }
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isBizSuggestion) SucculentPrimary else MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Text(
                                    text = if (isBizSuggestion) "Accept Business" else "Accept Personal",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Route and more-info tabs matching container roundness.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TripDetailTabButton(
                        label = "Route",
                        icon = Icons.Default.LocationOn,
                        selected = pagerState.currentPage == 0,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TripDetailTabButton(
                        label = "More info",
                        icon = Icons.Default.Info,
                        selected = pagerState.currentPage == 1,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) { page ->
                    if (page == 0) {
                        val finalStartAddress = startAddress?.takeUnless { ResolveTripAddressesUseCase.needsAddress(it) }
                            ?: if (addressState.tripId == trip.id && addressState.loading) "Finding start address…"
                            else "Approximate start location unavailable"
                        val finalEndAddress = endAddress?.takeUnless { ResolveTripAddressesUseCase.needsAddress(it) }
                            ?: if (addressState.tripId == trip.id && addressState.loading) "Finding destination address…"
                            else "Approximate destination unavailable"
                        val savedStartId = smartNames?.startLocation?.id
                        val savedEndId = smartNames?.endLocation?.id

                        RouteDetailsTab(
                            trip = trip,
                            date = dateFormatted,
                            startAddress = finalStartAddress,
                            endAddress = finalEndAddress,
                            smartStartName = smartNames?.startLocation?.name,
                            smartEndName = smartNames?.endLocation?.name,
                            startTime = startTimeStr,
                            endTime = endTimeStr,
                            startLocationActionLabel = if (smartNames?.startLocation == null) "Save" else "Edit",
                            endLocationActionLabel = if (smartNames?.endLocation == null) "Save" else "Edit",
                            onSaveStartLocation = if (savedStartId != null && onNavigateToSavedLocations != null) {
                                { onNavigateToSavedLocations(savedStartId) }
                            } else if (onSaveLocation != null && onNavigateToSavedLocations != null) {
                                {
                                    val lat = trip.startLatitude ?: 0.0
                                    val lng = trip.startLongitude ?: 0.0
                                    if (!locationSaving) {
                                        locationSaving = true
                                        onSaveLocation("Start Location", lat, lng, finalStartAddress, 150, "place", null, null,
                                            { id -> locationSaving = false; onNavigateToSavedLocations(id) },
                                            { error -> locationSaving = false; locationSaveError = error })
                                    }
                                }
                            } else null,
                            onSaveEndLocation = if (savedEndId != null && onNavigateToSavedLocations != null) {
                                { onNavigateToSavedLocations(savedEndId) }
                            } else if (onSaveLocation != null && onNavigateToSavedLocations != null) {
                                {
                                    val lat = trip.endLatitude ?: 0.0
                                    val lng = trip.endLongitude ?: 0.0
                                    if (!locationSaving) {
                                        locationSaving = true
                                        onSaveLocation("Destination", lat, lng, finalEndAddress, 150, "place", null, null,
                                            { id -> locationSaving = false; onNavigateToSavedLocations(id) },
                                            { error -> locationSaving = false; locationSaveError = error })
                                    }
                                }
                            } else null
                        )
                    } else {
                        TripStatusTab(
                            trip = trip,
                            durationMinutes = durationMinutes,
                            classificationColor = classificationColor,
                            onEdit = { showEditSheet = true },
                            onDelete = { showDeleteConfirmation = true }
                        )
                    }
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

    locationSaveError?.let { error ->
        AlertDialog(
            onDismissRequest = { locationSaveError = null },
            title = { Text("Could not save location") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { locationSaveError = null }) { Text("OK") } }
        )
    }

    // Modal Edit Sheet
    if (showEditSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var selectedClassification by remember { mutableStateOf(trip.classification) }
        var selectedEntity by remember { mutableStateOf(trip.businessEntity) }
        var selectedPurpose by remember { mutableStateOf(trip.purpose) }
        var notesText by remember { mutableStateOf(trip.notes.orEmpty()) }
        var isSaving by remember { mutableStateOf(false) }
        var editErrorMessage by remember { mutableStateOf<String?>(null) }
        val purposes = remember(customPurposes, trip.purpose) {
            val base = if (customPurposes.isNotEmpty()) customPurposes else TripPurpose.DEFAULT_SUCCULENT_PURPOSES
            val list = base.toMutableList()
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
                    Text("Business", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedEntity == BusinessEntity.MY_SUCCULENT_CORNER.displayName,
                            onClick = { selectedEntity = BusinessEntity.MY_SUCCULENT_CORNER.displayName },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.BusinessCenter,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Business") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SucculentPrimary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
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
                    onValueChange = { 
                        notesText = it 
                        editErrorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add trip notes...") },
                    minLines = 2
                )

                if (editErrorMessage != null) {
                    Text(
                        text = editErrorMessage!!,
                        color = SpeedRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { showEditSheet = false },
                        enabled = !isSaving
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (isSaving) return@Button
                            isSaving = true
                            editErrorMessage = null
                            onSaveTrip(
                                trip.id,
                                selectedClassification,
                                selectedEntity,
                                selectedPurpose,
                                notesText.ifBlank { null },
                                { error ->
                                    isSaving = false
                                    editErrorMessage = error
                                },
                                {
                                    isSaving = false
                                    showEditSheet = false
                                }
                            )
                        },
                        enabled = !isSaving,
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
    val selectedColor by animateColorAsState(
        targetValue = if (selected) SucculentPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "trip_detail_color_$label"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "trip_detail_scale_$label"
    )
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val indicatorOffset by animateDpAsState(
            targetValue = if (selected) 0.dp else maxWidth,
            animationSpec = tween(650, easing = FastOutSlowInEasing),
            label = "trip_detail_indicator_$label"
        )
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .fillMaxWidth()
                .height(44.dp)
                .background(
                    color = SucculentPrimary.copy(alpha = if (selected) 0.16f else 0f),
                    shape = RoundedCornerShape(8.dp)
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .semantics {
                    role = Role.Tab
                    this.selected = selected
                }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp).graphicsLayer(scaleX = iconScale, scaleY = iconScale),
                    tint = selectedColor
                )
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = selectedColor)
            }
        }
    }
}

@Composable
private fun RouteDetailsTab(
    trip: TripEntity,
    date: String,
    startAddress: String,
    endAddress: String,
    smartStartName: String? = null,
    smartEndName: String? = null,
    startTime: String,
    endTime: String,
    startLocationActionLabel: String = "Save",
    endLocationActionLabel: String = "Save",
    onSaveStartLocation: (() -> Unit)? = null,
    onSaveEndLocation: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    date.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (trip.startTime > 0) SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(trip.startTime)) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateNeutral,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    String.format(Locale.US, "%.1f", trip.totalDistanceMiles),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text("MILES", style = MaterialTheme.typography.labelSmall, color = SlateNeutral, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "\$${String.format(Locale.US, "%.2f", trip.calculatedDeduction)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = SucculentPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text("POTENTIAL", style = MaterialTheme.typography.labelSmall, color = SlateNeutral, fontWeight = FontWeight.Bold)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        DestinationRow(
            primaryName = smartStartName,
            address = startAddress,
            time = startTime,
            color = SucculentPrimary,
            isStart = true,
            locationActionLabel = startLocationActionLabel,
            onSaveLocation = onSaveStartLocation
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        DestinationRow(
            primaryName = smartEndName,
            address = endAddress,
            time = endTime,
            color = SpeedRed,
            isStart = false,
            locationActionLabel = endLocationActionLabel,
            onSaveLocation = onSaveEndLocation
        )
    }
}

@Composable
private fun DestinationRow(
    primaryName: String? = null,
    address: String,
    time: String,
    color: Color,
    isStart: Boolean,
    locationActionLabel: String,
    onSaveLocation: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            if (isStart) {
                com.businesstracker.mileage.ui.components.StartBeaconIcon(tint = color)
            } else {
                com.businesstracker.mileage.ui.components.EndFlagIcon(tint = color)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (!primaryName.isNullOrBlank()) {
                    Text(
                        text = primaryName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onSaveLocation != null) {
                    TextButton(
                        onClick = onSaveLocation,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                contentDescription = "$locationActionLabel ${if (isStart) "start" else "destination"} location"
                            }
                    ) {
                        Text(
                            text = if (locationActionLabel == "Save") "+ Save" else "Edit",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SucculentPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripStatusTab(
    trip: TripEntity,
    durationMinutes: Long,
    classificationColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // Calculate speed metrics from recorded waypoints or distance & duration
    val speedMetrics = remember(trip) {
        var maxMph = 0.0
        val speeds = mutableListOf<Double>()
        try {
            val array = JSONArray(trip.waypointsJson)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.has("speed")) {
                    val sMps = item.optDouble("speed", 0.0)
                    if (sMps > 0.0) {
                        val sMph = sMps * 2.23694
                        speeds.add(sMph)
                        if (sMph > maxMph) maxMph = sMph
                    }
                }
            }
        } catch (_: Exception) {}

        val avgMph = if (speeds.isNotEmpty()) {
            speeds.average()
        } else if (durationMinutes > 0 && trip.totalDistanceMiles > 0) {
            (trip.totalDistanceMiles / (durationMinutes / 60.0))
        } else {
            0.0
        }

        // Fallback for max speed if individual speeds were absent
        if (maxMph <= 0.0 && avgMph > 0.0) {
            maxMph = avgMph * 1.25 // reasonable estimate if pointwise speed wasn't sampled
        }

        Pair(avgMph, maxMph)
    }
    val avgSpeedStr = remember(speedMetrics) {
        if (speedMetrics.first > 0.0) "${String.format(Locale.US, "%.1f", speedMetrics.first)} mph" else "--"
    }
    val maxSpeedStr = remember(speedMetrics) {
        if (speedMetrics.second > 0.0) "${String.format(Locale.US, "%.1f", speedMetrics.second)} mph" else "--"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
            StatusValue("Average speed", avgSpeedStr, Modifier.weight(1f))
            StatusValue("Max speed", maxSpeedStr, Modifier.weight(1f))
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
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit trip", tint = SucculentPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete trip", tint = SpeedRed, modifier = Modifier.size(18.dp))
            }
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
