package com.businesstracker.mileage.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.businesstracker.mileage.ui.components.AppFloatingBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.model.TripPurpose
import com.businesstracker.mileage.ui.theme.SlateNeutral
import com.businesstracker.mileage.ui.theme.SpeedRed
import com.businesstracker.mileage.ui.theme.SucculentPrimary
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SwipeableTripCard(
    trip: TripEntity,
    onClassifyBusiness: (tripId: String, businessEntity: String, purpose: String) -> Unit,
    onClassifyPersonal: (tripId: String) -> Unit,
    modifier: Modifier = Modifier,
    isProcessing: Boolean = false,
    customPurposes: List<String> = emptyList(),
    onAddCustomPurpose: ((String) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var showBusinessBottomSheet by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (isProcessing) return@rememberSwipeToDismissBoxState false
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swiped Right -> Open Business Triage Sheet
                    showBusinessBottomSheet = true
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swiped Left -> Classify as Personal
                    onClassifyPersonal(trip.id)
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> SucculentPrimary
                    SwipeToDismissBoxValue.EndToStart -> SlateNeutral
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                label = "swipe_bg_color"
            )

            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }

            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.BusinessCenter
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Person
                else -> Icons.Default.DirectionsCar
            }

            val scale by animateFloatAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.75f else 1.2f,
                label = "icon_scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (direction == SwipeToDismissBoxValue.StartToEnd) {
                        Icon(
                            imageVector = icon,
                            contentDescription = "Classify Business",
                            tint = Color.White,
                            modifier = Modifier.scale(scale)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BUSINESS",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                        Text(
                            text = "PERSONAL",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = icon,
                            contentDescription = "Classify Personal",
                            tint = Color.White,
                            modifier = Modifier.scale(scale)
                        )
                    }
                }
            }
        }
    ) {
        TripCardContent(
            trip = trip,
            onTapBusiness = {
                if (!isProcessing) showBusinessBottomSheet = true
            },
            onTapPersonal = {
                if (!isProcessing) onClassifyPersonal(trip.id)
            },
            isProcessing = isProcessing
        )
    }

    // Business Classification Bottom Sheet
    if (showBusinessBottomSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val purposes = remember(customPurposes) {
            if (customPurposes.isNotEmpty()) customPurposes else TripPurpose.DEFAULT_SUCCULENT_PURPOSES
        }
        var selectedEntity by remember { mutableStateOf(BusinessEntity.MY_SUCCULENT_CORNER.displayName) }
        var selectedPurpose by remember { mutableStateOf(purposes.first()) }
        var customPurposeText by remember { mutableStateOf("") }
        var isCustomPurpose by remember { mutableStateOf(false) }

        AppFloatingBottomSheet(
            onDismissRequest = {
                showBusinessBottomSheet = false
                coroutineScope.launch { dismissState.reset() }
            },
            title = "Approve Business Drive",
            onClose = {
                showBusinessBottomSheet = false
                coroutineScope.launch { dismissState.reset() }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Text(
                    text = "${String.format(Locale.US, "%.1f", trip.totalDistanceMiles)} miles • Tax Deduction: \$${String.format(Locale.US, "%.2f", trip.totalDistanceMiles * trip.applicableRate)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Text(
                    text = "Business",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

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

                Text(
                    text = "Select Purpose",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                // Quick Tag FilterChips for Purposes
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    purposes.forEach { purpose ->
                        FilterChip(
                            selected = !isCustomPurpose && selectedPurpose == purpose,
                            onClick = {
                                selectedPurpose = purpose
                                isCustomPurpose = false
                            },
                            label = { Text(purpose) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SucculentPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    FilterChip(
                        selected = isCustomPurpose,
                        onClick = { isCustomPurpose = true },
                        label = { Text("Custom...") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SucculentPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                if (isCustomPurpose) {
                    OutlinedTextField(
                        value = customPurposeText,
                        onValueChange = { customPurposeText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter custom business purpose") },
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            showBusinessBottomSheet = false
                            coroutineScope.launch { dismissState.reset() }
                        }
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            val finalPurpose = if (isCustomPurpose && customPurposeText.isNotBlank()) {
                                val trimmed = customPurposeText.trim()
                                onAddCustomPurpose?.invoke(trimmed)
                                trimmed
                            } else {
                                selectedPurpose
                            }
                            showBusinessBottomSheet = false
                            coroutineScope.launch { dismissState.reset() }
                            onClassifyBusiness(trip.id, selectedEntity, finalPurpose)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SucculentPrimary,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Approve Business",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun TripCardContent(
    trip: TripEntity,
    onTapBusiness: (() -> Unit)? = null,
    onTapPersonal: (() -> Unit)? = null,
    isProcessing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val monthDayFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val yearFormatter = remember { SimpleDateFormat("yyyy", Locale.getDefault()) }

    val formattedMonthDay = remember(trip.startTime) {
        if (trip.startTime > 0) monthDayFormatter.format(Date(trip.startTime)).uppercase(Locale.getDefault()) else trip.date.uppercase(Locale.getDefault())
    }
    val formattedYear = remember(trip.startTime) {
        if (trip.startTime > 0) yearFormatter.format(Date(trip.startTime)) else ""
    }
    val startTimeStr = remember(trip.startTime) {
        if (trip.startTime > 0) timeFormatter.format(Date(trip.startTime)) else ""
    }
    val endTimeStr = remember(trip.endTime) {
        if (trip.endTime > 0) timeFormatter.format(Date(trip.endTime)) else ""
    }
    val startAddressStr = remember(trip) {
        trip.startAddress?.takeIf { it.isNotBlank() }
            ?: "Recorded GPS (${String.format(Locale.US, "%.4f", trip.startLatitude)}, ${String.format(Locale.US, "%.4f", trip.startLongitude)})"
    }
    val endAddressStr = remember(trip) {
        trip.endAddress?.takeIf { it.isNotBlank() }
            ?: "Recorded GPS (${String.format(Locale.US, "%.4f", trip.endLatitude)}, ${String.format(Locale.US, "%.4f", trip.endLongitude)})"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Row 1: 3-Column Header (Date | Miles | Potential) matching TripDetail
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Column 1: Month & Day + Year
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formattedMonthDay,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (formattedYear.isNotEmpty()) {
                        Text(
                            text = formattedYear,
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateNeutral,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Column 2: Miles + MILES label
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f", trip.totalDistanceMiles),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "MILES",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateNeutral,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Column 3: Potential Deduction + POTENTIAL label
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val deduction = if (trip.calculatedDeduction > 0) {
                        trip.calculatedDeduction
                    } else {
                        trip.totalDistanceMiles * trip.applicableRate
                    }
                    Text(
                        text = "\$${String.format(Locale.US, "%.2f", deduction)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = SucculentPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "POTENTIAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateNeutral,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Route Map: Google Map rendering of trip route
            TripRouteMap(
                trip = trip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )

            // Start & Destination Locations with Timestamps on Far Right (without "Start" and "Destination" titles)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Start Location Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StartBeaconIcon(tint = SucculentPrimary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = startAddressStr,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (startTimeStr.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = startTimeStr,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Destination Location Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EndFlagIcon(tint = SpeedRed)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = endAddressStr,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (endTimeStr.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = endTimeStr,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Tap Action Buttons (For users who prefer tapping over swiping)
            if (onTapBusiness != null && onTapPersonal != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onTapPersonal,
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateNeutral)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Personal",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Button(
                        onClick = onTapBusiness,
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SucculentPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Business",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripSummaryMap(
    trip: TripEntity,
    modifier: Modifier = Modifier
) {
    // Parse coordinates from waypointsJson or fallback to start/end lat/long
    val coordinates = remember(trip.waypointsJson, trip.startLatitude, trip.endLatitude) {
        val list = mutableListOf<Pair<Double, Double>>()
        if (!trip.waypointsJson.isNullOrEmpty() && trip.waypointsJson != "[]") {
            try {
                val array = JSONArray(trip.waypointsJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(Pair(obj.getDouble("latitude"), obj.getDouble("longitude")))
                }
            } catch (_: Exception) {}
        }
        if (list.isEmpty()) {
            if ((trip.startLatitude != 0.0 || trip.startLongitude != 0.0) &&
                (trip.endLatitude != 0.0 || trip.endLongitude != 0.0) &&
                (trip.startLatitude != trip.endLatitude || trip.startLongitude != trip.endLongitude)) {
                list.add(Pair(trip.startLatitude, trip.startLongitude))
                list.add(Pair(trip.endLatitude, trip.endLongitude))
            }
        }
        list
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        if (coordinates.size >= 2) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Subtle grid
                val gridStep = 24.dp.toPx()
                var x = 0f
                while (x < width) {
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.06f),
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1f
                    )
                    x += gridStep
                }
                var y = 0f
                while (y < height) {
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.06f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                    y += gridStep
                }

                val minLat = coordinates.minOf { it.first }
                val maxLat = coordinates.maxOf { it.first }
                val minLng = coordinates.minOf { it.second }
                val maxLng = coordinates.maxOf { it.second }

                val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
                val lngSpan = (maxLng - minLng).coerceAtLeast(0.0005)

                val padX = 28.dp.toPx()
                val padY = 24.dp.toPx()
                val usableW = width - 2 * padX
                val usableH = height - 2 * padY

                val points = coordinates.map { (lat, lng) ->
                    val px = padX + ((lng - minLng) / lngSpan * usableW).toFloat()
                    val py = padY + ((maxLat - lat) / latSpan * usableH).toFloat()
                    Offset(px, py)
                }

                val path = Path()
                path.moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    path.lineTo(points[i].x, points[i].y)
                }

                // Outer route glow
                drawPath(
                    path = path,
                    color = SucculentPrimary.copy(alpha = 0.25f),
                    style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Main route line
                drawPath(
                    path = path,
                    color = SucculentPrimary,
                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Start Marker (Green origin)
                drawCircle(color = SucculentPrimary, radius = 9f, center = points.first())
                drawCircle(color = Color.White, radius = 4f, center = points.first())

                // End Marker (Red destination)
                drawCircle(color = SpeedRed, radius = 9f, center = points.last())
                drawCircle(color = Color.White, radius = 4f, center = points.last())
            }

            // Top-right Map Badge
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = SucculentPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "GPS Route (${coordinates.size} pts)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            // Honest state when waypoint information is unavailable: do NOT fabricate a route
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = SlateNeutral,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Route map unavailable (insufficient GPS waypoints)",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateNeutral
                    )
                }
            }
        }
    }
}
