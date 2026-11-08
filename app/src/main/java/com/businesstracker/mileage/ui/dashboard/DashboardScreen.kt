package com.businesstracker.mileage.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.businesstracker.mileage.service.TrackingServiceState
import com.businesstracker.mileage.ui.components.SwipeableTripCard
import com.businesstracker.mileage.ui.theme.AmoledBlack
import com.businesstracker.mileage.ui.theme.HighAccuracyGreen
import com.businesstracker.mileage.ui.theme.SlateNeutral
import com.businesstracker.mileage.ui.theme.PersonalBlue
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
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    var monthMenuExpanded by remember { mutableStateOf(false) }
    var isTimeFilterExpanded by remember { mutableStateOf(false) }
    var isAmoledDimmed by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // MileIQ-Style Header Bar (Month Picker + Document Icon & 3-Stat Bar)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Row: Month Dropdown in Center, Document Icon on Right
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Center: Month Selector
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { monthMenuExpanded = true }
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = state.selectedMonthName.ifEmpty { "November" },
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Month",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(24.dp)
                            )

                            DropdownMenu(
                                expanded = monthMenuExpanded,
                                onDismissRequest = { monthMenuExpanded = false }
                            ) {
                                state.availableMonths.forEach { monthIso ->
                                    val date = try {
                                        LocalDate.parse("$monthIso-01")
                                    } catch (_: Exception) {
                                        LocalDate.now()
                                    }
                                    val name = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                                    DropdownMenuItem(
                                        text = { Text("$name ${date.year}") },
                                        onClick = {
                                            viewModel.selectMonth(monthIso)
                                            monthMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Right: Sort / Time Filter Toggle Button (replaces Reports icon)
                        IconButton(
                            onClick = { isTimeFilterExpanded = !isTimeFilterExpanded },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Filter by Time Period",
                                tint = if (isTimeFilterExpanded) SucculentPrimary else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Collapsible Area: Time Filter Chips & Secondary Summary Metrics
                    AnimatedVisibility(
                        visible = isTimeFilterExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Spacer(modifier = Modifier.height(2.dp))
                            // Time Filter Chips (Today | This Week | This Month | This Year | Custom)
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(TimeFilter.entries.toTypedArray()) { filter ->
                                    FilterChip(
                                        selected = state.timeFilter == filter,
                                        onClick = { viewModel.setTimeFilter(filter) },
                                        label = { Text(filter.displayName) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SucculentPrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            // Secondary Summary Metrics (Total | Personal | Unreviewed)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total: ${String.format(Locale.US, "%.1f", state.summary.totalMiles)} mi",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "Personal: ${String.format(Locale.US, "%.1f", state.summary.personalMiles)} mi",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateNeutral
                                )
                                Text(
                                    text = "Unreviewed: ${String.format(Locale.US, "%.1f", state.summary.unreviewedMiles)} mi",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SpeedRed
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 3-Stat Bar (DRIVES | BUSINESS MILES (Odometer) | DEDUCTION ($))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. DRIVES
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = state.summary.totalTripsCount.toString(),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "DRIVES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SlateNeutral
                            )
                        }

                        // 2. BUSINESS MILES (Odometer style)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1.3f)
                        ) {
                            OdometerMilesDisplay(miles = state.summary.businessMiles)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "BUSINESS MI",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SlateNeutral
                            )
                        }

                        // 3. LOGGED ($ in green)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "\$${String.format(Locale.US, "%.0f", state.summary.estimatedDeduction)}",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black
                                ),
                                color = SucculentPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "DEDUCTION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SlateNeutral
                            )
                        }
                    }
                }
            }

            // Section: Drive Tracking Table (Single Table incorporated from Track tab)
            item {
                DriveTrackingTable(
                    serviceState = serviceState,
                    onStart = { viewModel.startManualTracking() },
                    onPause = { viewModel.pauseTracking() },
                    onResume = { viewModel.resumeTracking() },
                    onEnd = { viewModel.endTracking() }
                )
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
                                text = "Drive classified as ${state.lastClassifiedTrip?.classification}",
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

            // Section: Classification Breakdown (MySucculentCorner vs Personal)
            item {
                Text(
                    text = "Mileage Breakdown",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                val bMiles = state.summary.businessMiles
                val pMiles = state.summary.personalMiles
                val totalLogged = bMiles + pMiles
                val businessFraction = if (totalLogged > 0) (bMiles / totalLogged).toFloat() else 0f
                val personalFraction = if (totalLogged > 0) (pMiles / totalLogged).toFloat() else 0f
                val businessPercent = businessFraction * 100f
                val personalPercent = personalFraction * 100f

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Business Row
                        BreakdownRow(
                            title = "Business",
                            miles = bMiles,
                            fraction = businessFraction,
                            percent = businessPercent,
                            deduction = state.summary.estimatedDeduction,
                            color = SucculentPrimary,
                            icon = Icons.Default.BusinessCenter
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Personal Row
                        BreakdownRow(
                            title = "Personal",
                            miles = pMiles,
                            fraction = personalFraction,
                            percent = personalPercent,
                            deduction = 0.0,
                            color = PersonalBlue,
                            icon = Icons.Default.Person
                        )
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
}

@Composable
private fun DriveTrackingTable(
    serviceState: TrackingServiceState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHighAccuracy = (serviceState.lastWaypoint?.accuracy ?: 99f) <= 20f

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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Table Header: Title + Tesla Badge + GPS Indicator (No Dim button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (serviceState.isTracking) SucculentPrimary.copy(alpha = 0.15f) else SlateNeutral.copy(alpha = 0.15f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = if (serviceState.isTracking) SucculentPrimary else SlateNeutral,
                            modifier = Modifier.padding(5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (serviceState.isTracking) {
                                if (serviceState.isPaused) "Active Drive (Paused)" else "Active Drive Tracking"
                            } else if (serviceState.isRecoveryRequired) {
                                "Drive Interrupted"
                            } else {
                                "Drive Tracker"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                        if (!serviceState.isTracking) {
                            Text(
                                text = if (serviceState.isRecoveryRequired) {
                                    serviceState.statusMessage ?: "Recovery Required • Preserved"
                                } else {
                                    "Tesla Model Y • Ready"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // GPS Accuracy Badge (Right side of header, no Dim button)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isHighAccuracy) HighAccuracyGreen.copy(alpha = 0.15f) else WeakGpsAmber.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isHighAccuracy) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                            contentDescription = null,
                            tint = if (isHighAccuracy) HighAccuracyGreen else WeakGpsAmber,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isHighAccuracy) "High GPS" else "GPS...",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Bold,
                            color = if (isHighAccuracy) HighAccuracyGreen else WeakGpsAmber
                        )
                    }
                }
            }

            // Table Main Section: Compact Metrics Bar (DURATION | LIVE MILES | SPEED - No waypoints under speed)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableMetricCell(
                        label = "DURATION",
                        value = formattedDuration,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f", serviceState.distanceMiles),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "LIVE MILES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = SucculentPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    )
                    TableMetricCell(
                        label = "SPEED",
                        value = "${String.format(Locale.US, "%.0f", serviceState.currentSpeedMph)} mph",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Table Actions: Start Drive (when idle) OR Pause/Resume + End Trip (when tracking)
            if (!serviceState.isTracking) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SucculentPrimary,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    contentPadding = PaddingValues(vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Start Manual Drive",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { if (serviceState.isPaused) onResume() else onPause() },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (serviceState.isPaused) SucculentPrimary else SlateNeutral,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        contentPadding = PaddingValues(vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = if (serviceState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (serviceState.isPaused) "Resume" else "Pause",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = onEnd,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpeedRed,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        contentPadding = PaddingValues(vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "End Trip",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
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

@Composable
private fun OdometerMilesDisplay(
    miles: Double,
    modifier: Modifier = Modifier
) {
    val totalInt = kotlin.math.round(miles).toInt().coerceAtLeast(0)
    val formatted = String.format(Locale.US, "%06d", totalInt)
    val firstNonZero = formatted.indexOfFirst { it != '0' }.let { if (it == -1) formatted.length - 1 else it }
    val leadingZeros = formatted.substring(0, firstNonZero)
    val activeDigits = formatted.substring(firstNonZero)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (leadingZeros.isNotEmpty()) {
            Text(
                text = leadingZeros,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
            )
        }
        Text(
            text = activeDigits,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun BreakdownRow(
    title: String,
    miles: Double,
    fraction: Float,
    percent: Float,
    deduction: Double,
    color: Color,
    icon: ImageVector
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top line: Icon, Title, Miles, inline Deduction & Percentage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Surface(
                    shape = CircleShape,
                    color = color.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.padding(6.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• ${String.format(Locale.US, "%.1f", miles)} miles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                if (deduction > 0) {
                    Text(
                        text = "\$${String.format(Locale.US, "%,.2f", deduction)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "${String.format(Locale.US, "%.0f%%", percent)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Percentage bar underneath category
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.15f))
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
            }
        }
    }
}
