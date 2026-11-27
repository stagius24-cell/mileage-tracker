package com.businesstracker.mileage.ui.dashboard

import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.KeyboardArrowDown

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.lazy.LazyRow
import com.businesstracker.mileage.domain.model.TimeFilter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.businesstracker.mileage.service.TrackingServiceState
import com.businesstracker.mileage.ui.components.AppFloatingBottomSheet
import com.businesstracker.mileage.ui.components.FilterTripsBottomSheet
import com.businesstracker.mileage.ui.components.SwipeableTripCard
import com.businesstracker.mileage.ui.theme.AmoledBlack
import com.businesstracker.mileage.ui.theme.HighAccuracyGreen
import com.businesstracker.mileage.ui.theme.SlateNeutral
import com.businesstracker.mileage.ui.theme.SpeedRed
import com.businesstracker.mileage.ui.theme.SucculentPrimary
import com.businesstracker.mileage.ui.theme.WeakGpsAmber
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToReports: (() -> Unit)? = null,
    onNavigateToHistory: ((category: String, startDate: String, endDate: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val savedVehicles by viewModel.savedVehicles.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }
    var isAmoledDimmed by remember { mutableStateOf(false) }
    var showActiveVehiclePicker by remember { mutableStateOf(false) }
    var selectedIdleVehicleId by remember { mutableStateOf<String?>(null) }
    val selectedIdleVehicle = remember(savedVehicles, selectedIdleVehicleId) {
        savedVehicles.firstOrNull { it.id == selectedIdleVehicleId } ?: savedVehicles.firstOrNull()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) MaterialTheme.colorScheme.background else Color(0xFFF3F6FA)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Trackr-Style Header: "Dashboard" on left, compact Month/Year pill + Filter icon on right
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dashboard",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            val isLiveOrRecovery = serviceState.isTracking || serviceState.isRecoveryRequired

            if (isLiveOrRecovery) {
                item {
                    DriveTrackingTable(
                        serviceState = serviceState,
                        isAutoTrackingEnabled = state.isAutoTrackingEnabled,
                        fallbackVehicleName = selectedIdleVehicle?.name,
                        onStart = { viewModel.startManualTracking() },
                        onPause = { viewModel.pauseTracking() },
                        onResume = { viewModel.resumeTracking() },
                        onEnd = { viewModel.endTracking() },
                        onSelectVehicle = if (savedVehicles.isNotEmpty()) {
                            { showActiveVehiclePicker = true }
                        } else null
                    )
                }
            }

            item {
                val (startDate, endDate) = state.timeFilter.toDateRange(
                    customStartDate = state.customStartDate,
                    customEndDate = state.customEndDate
                )
                val periodLabel = when {
                    state.selectedMonths.size > 1 -> "${state.selectedMonths.size} months"
                    state.selectedMonths.size == 1 -> {
                        val m = state.selectedMonths.first()
                        try {
                            val d = LocalDate.parse("$m-01")
                            "${d.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${d.year}"
                        } catch (_: Exception) {
                            m
                        }
                    }
                    state.timeFilter == TimeFilter.CUSTOM && state.selectedMonthName.isNotBlank() ->
                        "${state.selectedMonthName} ${state.selectedYear}"
                    state.timeFilter == TimeFilter.THIS_MONTH ->
                        "${state.selectedMonthName} ${state.selectedYear}"
                    else -> state.timeFilter.displayName
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Monthly mileage",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Surface(
                            onClick = { showFilterSheet = true },
                            shape = RoundedCornerShape(10.dp),
                            color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) MaterialTheme.colorScheme.surface else Color.White,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = periodLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select period",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    MileageBreakdownCard(
                        summary = state.summary,
                        periodLabel = periodLabel,
                        onCategoryClick = if (onNavigateToHistory != null) {
                            { category -> onNavigateToHistory(category, startDate, endDate) }
                        } else null,
                        onDeductionClick = onNavigateToReports
                    )
                }
            }

            if (!isLiveOrRecovery) {
                item {
                    DriveTrackingTable(
                        serviceState = serviceState,
                        isAutoTrackingEnabled = state.isAutoTrackingEnabled,
                        fallbackVehicleName = selectedIdleVehicle?.name,
                        onStart = { viewModel.startManualTracking() },
                        onPause = { viewModel.pauseTracking() },
                        onResume = { viewModel.resumeTracking() },
                        onEnd = { viewModel.endTracking() },
                        onSelectVehicle = if (savedVehicles.isNotEmpty()) {
                            { showActiveVehiclePicker = true }
                        } else null
                    )
                }
            }

            // Section: Trips to Review (Hidden by default when 0 to review, only appears if actual drives exist)
            if (state.unreviewedTrips.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trips to Review",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = state.unreviewedTrips.size.toString(),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                items(state.unreviewedTrips, key = { it.id }) { trip ->
                    SwipeableTripCard(
                        trip = trip,
                        onClassifyBusiness = { id, entity, purpose ->
                            viewModel.classifyTripAsBusiness(id, entity, purpose)
                        },
                        onClassifyPersonal = { id ->
                            viewModel.classifyTripAsPersonal(id)
                        },
                        isProcessing = state.processingTripIds.contains(trip.id),
                        customPurposes = state.customPurposes,
                        onAddCustomPurpose = { purpose -> viewModel.addCustomPurpose(purpose) }
                    )
                }
            }

            if (state.isUndoAvailable && state.lastClassifiedTrip != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Drive classified as ${state.lastClassificationLabel ?: state.lastClassifiedTrip?.classification}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            TextButton(onClick = { viewModel.undoLastClassification() }) {
                                Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Undo")
                            }
                        }
                    }
                }
            }

        }

        // AMOLED Dim Screen Overlay (Double-tap to wake)
        AnimatedVisibility(
            visible = isAmoledDimmed,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AmoledBlack)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { isAmoledDimmed = false }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format(Locale.US, "%.2f", serviceState.distanceMiles),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = Color.White.copy(alpha = 0.35f)
                    )
                    Text(
                        text = "MILES",
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 2.sp,
                        color = Color.White.copy(alpha = 0.25f)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "Double-tap to wake screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        var pendingTimeFilter by remember {
            mutableStateOf(
                state.timeFilter.takeIf { it == TimeFilter.TODAY || it == TimeFilter.THIS_WEEK }
            )
        }
        FilterTripsBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            title = "Filter dashboard",
            initialSelectedMonths = state.selectedMonths,
            availableYears = state.availableMonths.mapNotNull { it.take(4).toIntOrNull() }.distinct(),
            showVehicleFilter = false,
            showTripTypeFilter = false,
            showMissingPurposeToggle = false,
            showQuickTimePresets = true,
            initialTimeFilter = pendingTimeFilter,
            onApplyTimeFilter = { tf -> pendingTimeFilter = tf },
            onApplyFilters = { months, _, _, _ ->
                viewModel.applyDashboardFilters(months, pendingTimeFilter)
            }
        )
    }

    if (showActiveVehiclePicker) {
        AppFloatingBottomSheet(
            onDismissRequest = { showActiveVehiclePicker = false },
            title = "Select Trip Vehicle",
            onClose = { showActiveVehiclePicker = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                savedVehicles.forEach { vehicle ->
                    val selected = serviceState.vehicleId == vehicle.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) SucculentPrimary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                            .clickable {
                                selectedIdleVehicleId = vehicle.id
                                viewModel.selectActiveTripVehicle(vehicle.id)
                                showActiveVehiclePicker = false
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = vehicle.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = vehicle.bluetoothDeviceName?.let { "Bluetooth · $it" } ?: "Saved vehicle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected vehicle",
                                tint = SucculentPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (serviceState.vehicleId == null) SucculentPrimary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                        .clickable {
                            viewModel.selectActiveTripVehicle(null)
                            showActiveVehiclePicker = false
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Unknown vehicle",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (serviceState.vehicleId == null) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (serviceState.vehicleId == null) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected Unknown vehicle",
                            tint = SucculentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveTrackingTable(
    serviceState: TrackingServiceState,
    isAutoTrackingEnabled: Boolean,
    fallbackVehicleName: String? = null,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onSelectVehicle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val cardColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val dividerColor = if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0)
    val mutedColor = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF64748B)
    val isHighAccuracy = (serviceState.lastWaypoint?.accuracy ?: 99f) <= 20f
    val isActiveOrRecovery = serviceState.isTracking || serviceState.isRecoveryRequired

    val formattedDuration = remember(serviceState.durationMillis) {
        val hours = TimeUnit.MILLISECONDS.toHours(serviceState.durationMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(serviceState.durationMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(serviceState.durationMillis) % 60
        if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, dividerColor.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (!isActiveOrRecovery) {
            // Compact Ready to Drive Card when idle
            val displayVehicleName = serviceState.vehicleName ?: fallbackVehicleName ?: "Select vehicle"
            val vehicleDrawable = when {
                displayVehicleName.contains("Model 3", ignoreCase = true) -> com.businesstracker.mileage.R.drawable.vehicle_tesla_model_3
                displayVehicleName.contains("Model Y", ignoreCase = true) -> com.businesstracker.mileage.R.drawable.vehicle_tesla_model_y
                else -> null
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = { onSelectVehicle?.invoke() },
                        enabled = onSelectVehicle != null,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = if (vehicleDrawable != null) Color.White else SucculentPrimary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, dividerColor)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (vehicleDrawable != null) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = vehicleDrawable),
                                    contentDescription = "Vehicle: $displayVehicleName",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(3.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = SucculentPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .then(if (onSelectVehicle != null) Modifier.clickable(onClick = onSelectVehicle) else Modifier)
                    ) {
                        Text(
                            text = "Ready to drive",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = displayVehicleName,
                                fontSize = 12.sp,
                                color = mutedColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (onSelectVehicle != null) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select vehicle",
                                    tint = mutedColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onStart,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SucculentPrimary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Start trip",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                HorizontalDivider(color = dividerColor.copy(alpha = 0.7f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (isAutoTrackingEnabled) SucculentPrimary else mutedColor,
                                    CircleShape
                                )
                        )
                        Text(
                            text = if (isAutoTrackingEnabled) "Auto-tracking on" else "Auto-tracking off",
                            fontSize = 12.sp,
                            color = if (isAutoTrackingEnabled) SucculentPrimary else mutedColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = mutedColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Auto-stop enabled",
                            fontSize = 12.sp,
                            color = mutedColor
                        )
                    }
                }
            }
        } else {
            // Active Drive Tracking Card (Trackr minimalist style, no distracting animations)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SucculentPrimary.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = SucculentPrimary,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = when {
                                    serviceState.isRecoveryRequired -> "Drive interrupted"
                                    serviceState.isPaused -> "Drive paused"
                                    serviceState.isCandidate -> "Checking for driving"
                                    else -> "Drive in progress"
                                },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val vehicleLabel = serviceState.vehicleName ?: if (serviceState.ambiguousVehicleCount > 1) {
                                "Select vehicle"
                            } else {
                                "Unknown vehicle"
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = if (onSelectVehicle != null) {
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable(onClick = onSelectVehicle)
                                } else Modifier
                            ) {
                                Text(
                                    text = vehicleLabel,
                                    fontSize = 13.sp,
                                    color = if (serviceState.ambiguousVehicleCount > 1 && serviceState.vehicleName == null) {
                                        SucculentPrimary
                                    } else {
                                        mutedColor
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (serviceState.vehicleBluetoothConnected) {
                                    Spacer(Modifier.width(5.dp))
                                    Icon(
                                        imageVector = Icons.Default.Bluetooth,
                                        contentDescription = "Vehicle Bluetooth connected",
                                        tint = SucculentPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                if (onSelectVehicle != null) {
                                    Spacer(Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = "Select trip vehicle",
                                        tint = mutedColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isHighAccuracy) HighAccuracyGreen else WeakGpsAmber)
                        )
                        Text(
                            text = if (isHighAccuracy) "GPS strong" else "GPS weak",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isHighAccuracy) HighAccuracyGreen else WeakGpsAmber
                        )
                    }
                }

                // Centered Primary Live Mileage (32sp Semibold, tabular numerals)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f", serviceState.distanceMiles),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "mi",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = mutedColor,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                    Text(
                        text = "Live mileage",
                        fontSize = 13.sp,
                        color = mutedColor
                    )
                }

                // Secondary Metrics: Duration and Current speed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = mutedColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Duration",
                                fontSize = 12.sp,
                                color = mutedColor
                            )
                        }
                        Text(
                            text = formattedDuration,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = mutedColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Current speed",
                                fontSize = 12.sp,
                                color = mutedColor
                            )
                        }
                        Text(
                            text = "${String.format(Locale.US, "%.0f", serviceState.currentSpeedMph)} mph",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                HorizontalDivider(color = dividerColor.copy(alpha = 0.8f))

                // Neutral Outlined Pause/Resume + Soft Red End Trip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { if (serviceState.isPaused) onResume() else onPause() },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, dividerColor),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = if (serviceState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (serviceState.isPaused) "Resume" else "Pause",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = onEnd,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) SpeedRed.copy(alpha = 0.18f) else Color(0xFFFEF2F2),
                            contentColor = SpeedRed
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = SpeedRed
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "End trip",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SpeedRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableMetricCell(
    label: String,
    value: String,
    subValue: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            ),
            color = SlateNeutral
        )
        if (!subValue.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = SlateNeutral.copy(alpha = 0.7f)
            )
        }
    }
}
