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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.businesstracker.mileage.ui.theme.SpeedRed
import com.businesstracker.mileage.ui.theme.SlateNeutral
import com.businesstracker.mileage.ui.theme.SucculentPrimary
import com.businesstracker.mileage.ui.theme.SucculentSecondary
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
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var showBusinessBottomSheet by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swiped Right -> Open MySucculentCorner Triage Sheet
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
        TripCardContent(trip = trip)
    }

    // MySucculentCorner Business Classification Bottom Sheet
    if (showBusinessBottomSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val purposes = remember { TripPurpose.DEFAULT_SUCCULENT_PURPOSES }
        var selectedPurpose by remember { mutableStateOf(purposes.first()) }

        ModalBottomSheet(
            onDismissRequest = {
                showBusinessBottomSheet = false
                coroutineScope.launch { dismissState.reset() }
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "MySucculentCorner Business Drive",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "${String.format(Locale.US, "%.1f", trip.totalDistanceMiles)} miles • Tax Deduction: \$${String.format(Locale.US, "%.2f", trip.totalDistanceMiles * trip.applicableRate)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Text(
                    text = "Select Purpose",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Quick Tag FilterChips for MySucculentCorner
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    purposes.forEach { purpose ->
                        FilterChip(
                            selected = selectedPurpose == purpose,
                            onClick = { selectedPurpose = purpose },
                            label = { Text(purpose) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SucculentPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                            showBusinessBottomSheet = false
                            coroutineScope.launch { dismissState.reset() }
                            onClassifyBusiness(
                                trip.id,
                                BusinessEntity.MY_SUCCULENT_CORNER.displayName,
                                selectedPurpose
                            )
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
                            text = "Save & Sync",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun TripCardContent(
    trip: TripEntity,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    val formattedDate = remember(trip.startTime) {
        if (trip.startTime > 0) dateFormatter.format(Date(trip.startTime)) else trip.date
    }
    val timeRangeStr = remember(trip.startTime, trip.endTime) {
        "${timeFormatter.format(Date(trip.startTime))} – ${timeFormatter.format(Date(trip.endTime))}"
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
            // Row 1: Left (Icon + Date & Time on 2 lines) | Right (Miles & Potential Deduction)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Date & Time well divided
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SucculentPrimary.copy(alpha = 0.15f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = SucculentPrimary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        // Date on top line
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // Time range underneath on 2nd line
                        Text(
                            text = timeRangeStr,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                        Text(
                            text = "Auto-detected Drive",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = SucculentPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Right Column: Total Miles Drive + Potential Deduction
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format(Locale.US, "%.1f", trip.totalDistanceMiles),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "mi",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    Text(
                        text = "≈ \$${String.format(Locale.US, "%.2f", trip.totalDistanceMiles * trip.applicableRate)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = SucculentPrimary
                    )
                }
            }

            // In Between: Display a Summary Map Showing that Trip
            TripSummaryMap(trip = trip)

            // Separate Lines for Start Location and Ending Location with Icons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Starting Location Line
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(SucculentPrimary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(SucculentPrimary, CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "START LOCATION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = SlateNeutral
                        )
                        Text(
                            text = trip.startAddress ?: "Start Location (San Jose, CA)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Ending Location Line
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(SpeedRed.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = SpeedRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ENDING LOCATION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = SlateNeutral
                        )
                        Text(
                            text = trip.endAddress ?: "Ending Location",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripSummaryMap(
    trip: TripEntity,
    modifier: Modifier = Modifier
) {
    // Parse coordinates from waypointsJson or fallback to start/end lat/long
    val coordinates = remember(trip.waypointsJson, trip.startLatitude, trip.endLatitude) {
        val list = mutableListOf<Pair<Double, Double>>()
        if (!trip.waypointsJson.isNullOrEmpty()) {
            try {
                val array = JSONArray(trip.waypointsJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(Pair(obj.getDouble("latitude"), obj.getDouble("longitude")))
                }
            } catch (_: Exception) {}
        }
        if (list.isEmpty()) {
            if (trip.startLatitude != 0.0 || trip.startLongitude != 0.0) {
                list.add(Pair(trip.startLatitude, trip.startLongitude))
            }
            if (trip.endLatitude != 0.0 || trip.endLongitude != 0.0) {
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
        // Native canvas rendering trip polyline & GPS track
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Subtle background grid
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

            if (coordinates.size >= 2) {
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
            } else {
                // Aesthetic schematic curve for drive with no waypoints recorded yet
                val padX = 28.dp.toPx()
                val startOffset = Offset(padX, height * 0.70f)
                val endOffset = Offset(width - padX, height * 0.30f)

                val path = Path().apply {
                    moveTo(startOffset.x, startOffset.y)
                    cubicTo(
                        width * 0.35f, height * 0.85f,
                        width * 0.65f, height * 0.15f,
                        endOffset.x, endOffset.y
                    )
                }

                drawPath(
                    path = path,
                    color = SucculentPrimary.copy(alpha = 0.25f),
                    style = Stroke(width = 10f, cap = StrokeCap.Round)
                )
                drawPath(
                    path = path,
                    color = SucculentPrimary,
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )

                drawCircle(color = SucculentPrimary, radius = 9f, center = startOffset)
                drawCircle(color = Color.White, radius = 4f, center = startOffset)

                drawCircle(color = SpeedRed, radius = 9f, center = endOffset)
                drawCircle(color = Color.White, radius = 4f, center = endOffset)
            }
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
                    text = "GPS Route",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
