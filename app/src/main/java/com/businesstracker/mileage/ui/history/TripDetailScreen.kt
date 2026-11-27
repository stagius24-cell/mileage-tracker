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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.businesstracker.mileage.domain.tracking.VehicleFriendlyNameResolver
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
    val tripVehicleName = VehicleFriendlyNameResolver.resolve(trip.vehicleId, vehicleDirectoryState.vehicles)

    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val fullReceiptDateFormatter = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()) }

    val dateFormatted = remember(trip.startTime) {
        if (trip.startTime > 0) dateFormatter.format(Date(trip.startTime)) else trip.date
    }
    val receiptDateFormatted = remember(trip.startTime, trip.date) {
        if (trip.startTime > 0) fullReceiptDateFormatter.format(Date(trip.startTime)) else trip.date
    }
    val startTimeStr = remember(trip.startTime) { timeFormatter.format(Date(trip.startTime)) }
    val endTimeStr = remember(trip.endTime) { timeFormatter.format(Date(trip.endTime)) }

    val durationMinutes = remember(trip.startTime, trip.endTime) {
        val diff = trip.endTime - trip.startTime
        if (diff > 0) TimeUnit.MILLISECONDS.toMinutes(diff) else 0L
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val pageBackground = if (isDark) MaterialTheme.colorScheme.background else Color(0xFFF1F4F9)
    val cardBackground = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val primaryText = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF172536)
    val mutedText = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF64748B)
    val emeraldAccent = if (isDark) Color(0xFF43D99A) else Color(0xFF168354)
    val dividerColor = if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0)

    val isBusiness = trip.classification == TripClassification.BUSINESS.value
    val isPersonal = trip.classification == TripClassification.PERSONAL.value
    val classificationColor = when {
        isBusiness -> emeraldAccent
        isPersonal -> if (isDark) Color(0xFF75B9FF) else Color(0xFF3B82F6)
        else -> mutedText
    }
    val classificationLabel = when {
        isBusiness -> "Business"
        isPersonal -> "Personal"
        else -> "Unclassified"
    }

    var outerDragOffset by remember(trip.id) { mutableFloatStateOf(0f) }
    var suggestionDismissed by remember(trip.id) { mutableStateOf(false) }
    var isMapExpanded by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackground)
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
        val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
        val collapsedMapHeight = (screenHeightDp * 0.25f).coerceIn(150.dp, 200.dp)
        val mapHeight by animateDpAsState(
            targetValue = if (isMapExpanded) 340.dp else collapsedMapHeight,
            animationSpec = tween(250),
            label = "trip_map_height"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(mapHeight)
        ) {
            TripRouteMap(trip = trip, modifier = Modifier.fillMaxSize())
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .size(34.dp),
                shape = CircleShape,
                color = if (isDark) MaterialTheme.colorScheme.surface.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.94f),
                shadowElevation = 2.dp
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close trip details",
                        tint = mutedText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(34.dp),
                shape = CircleShape,
                color = if (isDark) MaterialTheme.colorScheme.surface.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.94f),
                shadowElevation = 2.dp
            ) {
                IconButton(
                    onClick = { isMapExpanded = !isMapExpanded },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (isMapExpanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                        contentDescription = if (isMapExpanded) "Collapse map" else "Expand map",
                        tint = mutedText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = cardBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Trip details",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryText
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = classificationColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = classificationLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = classificationColor
                            )
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Trip options",
                            tint = mutedText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit trip") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOverflowMenu = false
                                showEditSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Select vehicle") },
                            leadingIcon = { Icon(Icons.Default.BusinessCenter, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOverflowMenu = false
                                showVehiclePicker = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete trip", color = SpeedRed) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = SpeedRed, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOverflowMenu = false
                                showDeleteConfirmation = true
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = receiptDateFormatted,
                    fontSize = 12.sp,
                    color = mutedText
                )
                Row(
                    modifier = Modifier
                        .clickable { showVehiclePicker = true }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isModel3 = tripVehicleName.contains("Model 3", ignoreCase = true)
                    val isModelY = tripVehicleName.contains("Model Y", ignoreCase = true)
                    if (isModelY) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color(0xFF475569), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DirectionsCar,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.DirectionsCar,
                            contentDescription = null,
                            tint = if (isModel3) Color(0xFFDC2626) else mutedText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = tripVehicleName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val sageBg = if (isDark) SucculentPrimary.copy(alpha = 0.10f) else Color(0xFFEDF5F0)
            val deductionPositive = trip.calculatedDeduction > 0.0
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = sageBg
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactMetricCell(
                        icon = Icons.Outlined.Route,
                        iconTint = emeraldAccent,
                        value = "${String.format(Locale.US, "%.1f", trip.totalDistanceMiles)} mi",
                        valueColor = primaryText,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(dividerColor.copy(alpha = 0.9f))
                    )
                    CompactMetricCell(
                        icon = Icons.Outlined.Schedule,
                        iconTint = emeraldAccent,
                        value = "$durationMinutes min",
                        valueColor = primaryText,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(dividerColor.copy(alpha = 0.9f))
                    )
                    CompactMetricCell(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        iconTint = emeraldAccent,
                        value = "$${String.format(Locale.US, "%.2f", trip.calculatedDeduction)}",
                        valueColor = if (deductionPositive) emeraldAccent else mutedText,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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

            // Minimal underline tabs (Trackr style)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TripDetailTabButton(
                        label = "Route",
                        icon = Icons.Default.LocationOn,
                        selected = pagerState.currentPage == 0,
                        activeColor = emeraldAccent,
                        inactiveColor = mutedText,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TripDetailTabButton(
                        label = "More info",
                        icon = Icons.Default.Info,
                        selected = pagerState.currentPage == 1,
                        activeColor = emeraldAccent,
                        inactiveColor = mutedText,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(color = dividerColor.copy(alpha = 0.8f), thickness = 1.dp)
            }

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
                            vehicleName = tripVehicleName,
                            onEditVehicle = { showVehiclePicker = true },
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
                            vehicleName = tripVehicleName,
                            onEditVehicle = { showVehiclePicker = true },
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
private fun CompactMetricCell(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            color = valueColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TripDetailTabButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) activeColor else inactiveColor,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "trip_detail_color_$label"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor
            )
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) activeColor else Color.Transparent)
        )
    }
}

@Composable
private fun RouteDetailsTab(
    trip: TripEntity,
    date: String,
    vehicleName: String,
    onEditVehicle: () -> Unit,
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
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val dividerColor = if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0)
    val emeraldAccent = if (isDark) Color(0xFF43D99A) else Color(0xFF168354)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        DestinationRow(
            primaryName = smartStartName,
            address = startAddress,
            time = startTime,
            color = emeraldAccent,
            isStart = true,
            locationActionLabel = startLocationActionLabel,
            onSaveLocation = onSaveStartLocation
        )
        Box(
            modifier = Modifier
                .padding(start = 11.dp)
                .width(1.5.dp)
                .height(16.dp)
                .background(dividerColor.copy(alpha = 0.8f))
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
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF172536)
    val mutedText = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF64748B)
    val addressParts = remember(address) {
        val parts = address.split(",", limit = 2)
        val street = parts.getOrNull(0)?.trim().orEmpty().ifBlank { address }
        val cityState = parts.getOrNull(1)?.trim().orEmpty()
        street to cityState
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(22.dp),
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
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = primaryName?.takeIf { it.isNotBlank() } ?: addressParts.first,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = primaryText
            )
            val secondaryLine = if (!primaryName.isNullOrBlank()) {
                address
            } else {
                addressParts.second
            }
            if (secondaryLine.isNotBlank()) {
                Text(
                    text = secondaryLine,
                    fontSize = 12.sp,
                    color = mutedText
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum"
                ),
                color = mutedText
            )
            if (onSaveLocation != null) {
                IconButton(
                    onClick = onSaveLocation,
                    modifier = Modifier
                        .size(24.dp)
                        .semantics {
                            contentDescription = "$locationActionLabel ${if (isStart) "start" else "destination"} location"
                        }
                ) {
                    Icon(
                        imageVector = if (locationActionLabel == "Save") Icons.Outlined.BookmarkBorder else Icons.Default.Edit,
                        contentDescription = null,
                        tint = mutedText,
                        modifier = Modifier.size(16.dp)
                    )
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
    vehicleName: String,
    onEditVehicle: () -> Unit,
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
    val speedSummary = remember(speedMetrics) {
        TripDetailsDisplayFormatter.speedSummary(speedMetrics.first, speedMetrics.second)
    }
    val tripTypePurpose = remember(trip.classification, trip.purpose) {
        TripDetailsDisplayFormatter.tripTypeAndPurpose(trip.classification, trip.purpose)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            StatusValue("Sync status", trip.syncStatus, Modifier.weight(1f), if (trip.syncStatus == "SYNCED") SucculentPrimary else SlateNeutral)
            StatusValue("Tracking", TripDetailsDisplayFormatter.trackingMethod(trip.trackingMethod), Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            StatusValue("Speed", speedSummary, Modifier.weight(1f), maxLines = 4)
            StatusValue("IRS rate", TripDetailsDisplayFormatter.irsRate(trip.applicableRate), Modifier.weight(1f))
        }
        if (!trip.notes.isNullOrBlank()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            StatusValue("Notes", trip.notes, Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun StatusValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 2
) {
    Column(modifier = modifier.padding(end = 8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 1.15f),
            color = SlateNeutral
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize * 1.15f),
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = maxLines,
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
