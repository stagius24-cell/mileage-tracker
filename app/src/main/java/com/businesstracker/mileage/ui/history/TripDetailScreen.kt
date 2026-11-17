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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
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
import com.businesstracker.mileage.ui.components.AppConfirmationDialog
import com.businesstracker.mileage.ui.components.AppFloatingBottomSheet
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
import androidx.compose.ui.draw.drawBehind
import com.businesstracker.mileage.util.AddressUtil
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
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
    onNavigateToSavedLocations: ((String) -> Unit)? = null,
    onConfirmSmartSuggestion: ((tripId: String, category: String, entity: String?, purpose: String?) -> Unit)? = null,
    onDismissSmartSuggestion: ((tripId: String) -> Unit)? = null,
    onVehicleSelected: ((tripId: String, vehicleId: String?) -> Unit)? = null,
    vehicleDirectoryViewModel: com.businesstracker.mileage.ui.settings.VehicleDirectoryViewModel = hiltViewModel()
) {
    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var locationSaveError by remember { mutableStateOf<String?>(null) }
    var locationSaving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val addressState by addressViewModel.state.collectAsStateWithLifecycle()
    val vehicleDirectoryState by vehicleDirectoryViewModel.state.collectAsStateWithLifecycle()
    var showVehiclePicker by remember(trip.id) { mutableStateOf(false) }
    LaunchedEffect(trip.id, trip.startAddress, trip.endAddress) { addressViewModel.resolve(trip) }
    DisposableEffect(trip.id) { onDispose { addressViewModel.cancelLookup() } }
    val startAddress = addressState.takeIf { it.tripId == trip.id }?.start ?: trip.startAddress
    val endAddress = addressState.takeIf { it.tripId == trip.id }?.end ?: trip.endAddress
    val tripVehicle = vehicleDirectoryState.vehicles.firstOrNull { it.id == trip.vehicleId }

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
                    .size(44.dp),
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 3.dp
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close trip details",
                        tint = Color(0xFF718096),
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
            // Smart Auto-Classified Indicator or Smart Suggestion Card
            val isUnreviewed = trip.classification == TripClassification.UNREVIEWED.value
            val isSmartAuto = trip.classificationSource == com.businesstracker.mileage.domain.model.ClassificationSource.SMART_AUTO.value
            val hasSmartSuggestion = isUnreviewed && (trip.suggestedClassification == "BUSINESS" || trip.suggestedClassification == "PERSONAL") && !suggestionDismissed

            if (isSmartAuto) {
                com.businesstracker.mileage.ui.smart.SmartAutoClassifiedIndicator(
                    classification = trip.classification,
                    onEditClassification = { showEditSheet = true }
                )
            } else if (hasSmartSuggestion) {
                com.businesstracker.mileage.ui.smart.SmartTripSuggestionCard(
                    suggestedCategory = trip.suggestedClassification!!,
                    confirmedCount = 5,
                    suggestedEntity = if (trip.suggestedClassification == "BUSINESS") trip.businessEntity else null,
                    suggestedPurpose = if (trip.suggestedClassification == "BUSINESS") trip.purpose else null,
                    onConfirm = {
                        suggestionDismissed = true
                        onConfirmSmartSuggestion?.invoke(
                            trip.id,
                            trip.suggestedClassification!!,
                            if (trip.suggestedClassification == "BUSINESS") trip.businessEntity else null,
                            if (trip.suggestedClassification == "BUSINESS") trip.purpose else null
                        ) ?: onSaveTrip(
                            trip.id,
                            trip.suggestedClassification!!,
                            if (trip.suggestedClassification == "BUSINESS") trip.businessEntity else BusinessEntity.PERSONAL.displayName,
                            if (trip.suggestedClassification == "BUSINESS") trip.purpose else "Personal Drive",
                            trip.notes,
                            null,
                            null
                        )
                    },
                    onChangeToOpposite = { oppCategory ->
                        val isBiz = oppCategory.equals("BUSINESS", ignoreCase = true)
                        onSaveTrip(
                            trip.id,
                            oppCategory,
                            if (isBiz) BusinessEntity.MY_SUCCULENT_CORNER.displayName else BusinessEntity.PERSONAL.displayName,
                            if (isBiz) TripPurpose.NURSERY_PICKUP else "Personal Drive",
                            trip.notes,
                            null,
                            null
                        )
                    },
                    onDismiss = {
                        suggestionDismissed = true
                        onDismissSmartSuggestion?.invoke(trip.id)
                    }
                )
            } else if (isUnreviewed && smartNames?.suggestedClassification != null && !suggestionDismissed) {
                val suggestedClassification = smartNames.suggestedClassification
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

            Card(
                modifier = Modifier.fillMaxWidth().clickable { showVehiclePicker = true },
                shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), color = SucculentPrimary.copy(alpha = 0.10f)) {
                        Icon(
                            imageVector = if (tripVehicle?.bluetoothAddress != null) Icons.Default.Bluetooth else Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = SucculentPrimary,
                            modifier = Modifier.padding(10.dp).size(20.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Vehicle", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            tripVehicle?.name ?: "Vehicle not identified",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Default.Edit, contentDescription = "Edit trip vehicle", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            // Route and more-info tabs matching container roundness.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(19.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
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
                        val resolvedStart = startAddress?.takeUnless { ResolveTripAddressesUseCase.needsAddress(it) }
                            ?: if (addressState.tripId == trip.id && addressState.loading) "Finding start address…"
                            else "Approximate start location unavailable"
                        val finalStartAddress = AddressUtil.abbreviateAddress(resolvedStart)
                        val resolvedEnd = endAddress?.takeUnless { ResolveTripAddressesUseCase.needsAddress(it) }
                            ?: if (addressState.tripId == trip.id && addressState.loading) "Finding destination address…"
                            else "Approximate destination unavailable"
                        val finalEndAddress = AddressUtil.abbreviateAddress(resolvedEnd)
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

    if (showVehiclePicker) {
        AppFloatingBottomSheet(
            onDismissRequest = { showVehiclePicker = false },
            title = "Select Vehicle",
            onClose = { showVehiclePicker = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf<com.businesstracker.mileage.data.local.entity.VehicleEntity?>(null) + vehicleDirectoryState.vehicles
                options.forEach { vehicle ->
                    val selected = vehicle?.id == trip.vehicleId || (vehicle == null && trip.vehicleId == null)
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) SucculentPrimary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .clickable {
                                onVehicleSelected?.invoke(trip.id, vehicle?.id)
                                showVehiclePicker = false
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(vehicle?.name ?: "Not identified", fontWeight = FontWeight.SemiBold)
                            vehicle?.bluetoothDeviceName?.let {
                                Text("Bluetooth · $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = SucculentPrimary, modifier = Modifier.size(20.dp))
                    }
                }
                if (vehicleDirectoryState.vehicles.isEmpty()) {
                    Text("Add vehicles in Settings to assign one to this trip.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AppConfirmationDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = "Delete this trip?",
            message = "This removes the trip from local history. It does not silently delete a Google Sheets row.",
            confirmLabel = "Delete",
            onConfirm = {
                showDeleteConfirmation = false
                onDeleteTrip(trip.id)
                onBack()
            },
            dismissLabel = "Cancel",
            onDismiss = { showDeleteConfirmation = false },
            isDestructive = true
        )
    }

    locationSaveError?.let { error ->
        AppConfirmationDialog(
            onDismissRequest = { locationSaveError = null },
            title = "Could not save location",
            message = error,
            confirmLabel = "OK",
            onConfirm = { locationSaveError = null },
            dismissLabel = null,
            onDismiss = null
        )
    }

    // Modal Edit Sheet
    if (showEditSheet) {
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

        AppFloatingBottomSheet(
            onDismissRequest = { if (!isSaving) showEditSheet = false },
            title = "Edit Trip",
            onClose = { showEditSheet = false },
            isDismissible = !isSaving
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

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
                .height(38.dp)
                .background(
                    color = SucculentPrimary.copy(alpha = if (selected) 0.16f else 0f),
                    shape = RoundedCornerShape(19.dp)
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 38.dp)
                .clip(RoundedCornerShape(19.dp))
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
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Formatted date string (e.g. NOV 15, 2026) and day of week (e.g. Sunday)
        val fullDateHeader = remember(trip.startTime) {
            if (trip.startTime > 0) {
                SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(trip.startTime)).uppercase(Locale.US)
            } else {
                date.uppercase(Locale.US)
            }
        }
        val dayOfWeekStr = remember(trip.startTime) {
            if (trip.startTime > 0) {
                SimpleDateFormat("EEEE", Locale.US).format(Date(trip.startTime))
            } else ""
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Column 1: Date & Day of Week
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    tint = SucculentPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = fullDateHeader,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = dayOfWeekStr,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = SlateNeutral,
                    fontWeight = FontWeight.Medium
                )
            }

            // Divider 1
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(44.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
            )

            // Column 2: Miles & Road graphic
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RoadHighwayIcon(
                    modifier = Modifier.size(24.dp),
                    tint = SlateNeutral
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = String.format(Locale.US, "%.1f", trip.totalDistanceMiles),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "miles",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = SlateNeutral,
                    fontWeight = FontWeight.Medium
                )
            }

            // Divider 2
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(44.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
            )

            // Column 3: Potential & Stash of coins
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StackedCoinsIcon(
                    modifier = Modifier.size(24.dp),
                    tint = SucculentPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$" + String.format(Locale.US, "%.2f", trip.calculatedDeduction),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Black,
                    color = SucculentPrimary
                )
                Text(
                    text = "Potential",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = SlateNeutral,
                    fontWeight = FontWeight.Medium
                )
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
        DestinationRow(
            primaryName = smartEndName,
            address = endAddress,
            time = endTime,
            color = Color(0xFFD7737D),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .heightIn(min = 64.dp)
                .drawBehind {
                    val cx = size.width / 2f
                    val timelineLineColor = Color(0xFFCBD5E1)
                    val timelineStrokeWidth = 1.dp.toPx()
                    if (isStart) {
                        drawLine(
                            color = timelineLineColor,
                            start = Offset(cx, size.height / 2f),
                            end = Offset(cx, size.height),
                            strokeWidth = timelineStrokeWidth
                        )
                    } else {
                        drawLine(
                            color = timelineLineColor,
                            start = Offset(cx, 0f),
                            end = Offset(cx, size.height / 2f),
                            strokeWidth = timelineStrokeWidth
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isStart) {
                com.businesstracker.mileage.ui.components.StartBeaconIcon(
                    size = 20.dp,
                    tint = color
                )
            } else {
                com.businesstracker.mileage.ui.components.EndPinIcon(
                    size = 20.dp,
                    tint = color,
                    fillColor = MaterialTheme.colorScheme.surface
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            // Keep the timestamp and action together, centered as a single group.
            // A fixed-height wrapper prevents either control from being pinned to a row edge.
            Box(
                modifier = Modifier
                    .widthIn(min = 84.dp)
                    .height(64.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onSaveLocation != null) {
                    IconButton(
                        onClick = onSaveLocation,
                        modifier = Modifier
                            .size(32.dp)
                            .semantics {
                                contentDescription = "$locationActionLabel ${if (isStart) "start" else "destination"} location"
                            }
                    ) {
                        Icon(
                            imageVector = if (locationActionLabel == "Save") Icons.Outlined.BookmarkBorder else Icons.Default.Edit,
                            contentDescription = null,
                            tint = SucculentPrimary,
                            modifier = Modifier.size(18.dp)
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
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            StatusValue("Trip type", trip.classification, Modifier.weight(1f), classificationColor)
            StatusValue("Sync status", trip.syncStatus, Modifier.weight(1f), if (trip.syncStatus == "SYNCED") SucculentPrimary else SlateNeutral)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            StatusValue("Duration", "$durationMinutes min", Modifier.weight(1f))
            StatusValue("Tracking", trip.trackingMethod, Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            StatusValue("Average speed", avgSpeedStr, Modifier.weight(1f))
            StatusValue("Max speed", maxSpeedStr, Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            StatusValue("Purpose", trip.purpose, Modifier.weight(1f))
            StatusValue("IRS rate", "\$${trip.applicableRate} / mi", Modifier.weight(1f))
        }
        if (!trip.notes.isNullOrBlank()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            StatusValue("Notes", trip.notes, Modifier.fillMaxWidth().padding(vertical = 8.dp))
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

@Composable
private fun RoadHighwayIcon(
    modifier: Modifier = Modifier,
    tint: Color = SlateNeutral
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()

        // Left lane border (angled /)
        drawLine(
            color = tint,
            start = Offset(w * 0.40f, 0f),
            end = Offset(w * 0.16f, h),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Right lane border (angled \)
        drawLine(
            color = tint,
            start = Offset(w * 0.60f, 0f),
            end = Offset(w * 0.84f, h),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Dashed center line (|)
        drawLine(
            color = tint,
            start = Offset(w * 0.50f, h * 0.08f),
            end = Offset(w * 0.50f, h * 0.40f),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.50f, h * 0.60f),
            end = Offset(w * 0.50f, h * 0.92f),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
private fun StackedCoinsIcon(
    modifier: Modifier = Modifier,
    tint: Color = SucculentPrimary
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val stroke = 2.0.dp.toPx()
        val w = size.width
        val h = size.height

        // Left stack of coins
        val leftCenter = Offset(w * 0.38f, h * 0.40f)
        val leftRadiusX = w * 0.28f
        val leftRadiusY = h * 0.14f

        // Bottom ellipse of left stack
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(leftCenter.x - leftRadiusX, h * 0.64f - leftRadiusY),
            size = androidx.compose.ui.geometry.Size(leftRadiusX * 2, leftRadiusY * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
        )
        // Middle ellipse of left stack
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(leftCenter.x - leftRadiusX, h * 0.48f - leftRadiusY),
            size = androidx.compose.ui.geometry.Size(leftRadiusX * 2, leftRadiusY * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
        )
        // Top full ellipse of left stack
        drawOval(
            color = tint,
            topLeft = Offset(leftCenter.x - leftRadiusX, h * 0.32f - leftRadiusY),
            size = androidx.compose.ui.geometry.Size(leftRadiusX * 2, leftRadiusY * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
        )
        // Left stack side vertical lines
        drawLine(
            color = tint,
            start = Offset(leftCenter.x - leftRadiusX, h * 0.32f),
            end = Offset(leftCenter.x - leftRadiusX, h * 0.64f),
            strokeWidth = stroke
        )
        drawLine(
            color = tint,
            start = Offset(leftCenter.x + leftRadiusX, h * 0.32f),
            end = Offset(leftCenter.x + leftRadiusX, h * 0.64f),
            strokeWidth = stroke
        )

        // Right higher stack
        val rightCenter = Offset(w * 0.68f, h * 0.22f)
        val rightRadiusX = w * 0.24f
        val rightRadiusY = h * 0.12f

        // Bottom arc of right stack
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(rightCenter.x - rightRadiusX, h * 0.42f - rightRadiusY),
            size = androidx.compose.ui.geometry.Size(rightRadiusX * 2, rightRadiusY * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
        )
        // Top oval of right stack
        drawOval(
            color = tint,
            topLeft = Offset(rightCenter.x - rightRadiusX, h * 0.22f - rightRadiusY),
            size = androidx.compose.ui.geometry.Size(rightRadiusX * 2, rightRadiusY * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
        )
        // Right stack side vertical lines
        drawLine(
            color = tint,
            start = Offset(rightCenter.x - rightRadiusX, h * 0.22f),
            end = Offset(rightCenter.x - rightRadiusX, h * 0.42f),
            strokeWidth = stroke
        )
        drawLine(
            color = tint,
            start = Offset(rightCenter.x + rightRadiusX, h * 0.22f),
            end = Offset(rightCenter.x + rightRadiusX, h * 0.42f),
            strokeWidth = stroke
        )
    }
}
