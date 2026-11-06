package com.businesstracker.mileage.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.ui.theme.SlateNeutral
import com.businesstracker.mileage.ui.theme.SucculentPrimary

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isD1Expanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Automatic Drive Detection & Work Hours Schedule Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Master Auto Tracking Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatic Drive Detection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Low-power Activity Recognition automatically detects drives and notifies you to review.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        Switch(
                            checked = state.autoTrackingEnabled,
                            onCheckedChange = { viewModel.setAutoTrackingEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = SucculentPrimary)
                        )
                    }

                    // Background Location Permission Warning (if permission missing on Android 10+)
                    if (state.autoTrackingEnabled && !state.hasBackgroundLocationPermission) {
                        val localContext = androidx.compose.ui.platform.LocalContext.current
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Background Location Required",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "To detect drives automatically when your phone is in your pocket or vehicle, set Location to 'Allow all the time'.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = android.net.Uri.fromParts("package", localContext.packageName, null)
                                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        localContext.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
                                ) {
                                    Text("Open Location Settings", color = Color.White)
                                }
                            }
                        }
                    } else if (state.autoTrackingEnabled && state.isAutoTrackingReady) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(SucculentPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Auto Drive Detection Ready • Active in Background",
                                style = MaterialTheme.typography.labelSmall,
                                color = SucculentPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Work Schedule & Day-of-Week Filters (Visible when Auto Tracking is ON)
                    AnimatedVisibility(
                        visible = state.autoTrackingEnabled,
                        enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                        exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            // Schedule Filter Toggle Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = SucculentPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Work Hours & Days Filter",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Customize business drive tracking schedule",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                Switch(
                                    checked = state.scheduleFilteringEnabled,
                                    onCheckedChange = { viewModel.setScheduleFilteringEnabled(it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = SucculentPrimary)
                                )
                            }

                            if (state.scheduleFilteringEnabled) {
                                // 1. Days of Week Selector
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "ACTIVE WORK DAYS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            letterSpacing = 1.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = SlateNeutral
                                    )

                                    val days = listOf(
                                        "MON" to "Mon",
                                        "TUE" to "Tue",
                                        "WED" to "Wed",
                                        "THU" to "Thu",
                                        "FRI" to "Fri",
                                        "SAT" to "Sat",
                                        "SUN" to "Sun"
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        days.forEach { (code, label) ->
                                            val isSelected = state.workDays.contains(code)
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) SucculentPrimary else MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                    .clickable { viewModel.toggleWorkDay(code) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                                                    ),
                                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }

                                // 2. Work Hours Range
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "WORK HOURS WINDOW",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            letterSpacing = 1.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = SlateNeutral
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = state.workHoursStart,
                                            onValueChange = { viewModel.setWorkHoursStart(it) },
                                            label = { Text("Start Time") },
                                            placeholder = { Text("08:00") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )

                                        Text(
                                            text = "to",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = SlateNeutral
                                        )

                                        OutlinedTextField(
                                            value = state.workHoursEnd,
                                            onValueChange = { viewModel.setWorkHoursEnd(it) },
                                            label = { Text("End Time") },
                                            placeholder = { Text("18:00") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }

                                // 3. Outside Schedule Behavior Selector
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "OUTSIDE SCHEDULE BEHAVIOR",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            letterSpacing = 1.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = SlateNeutral
                                    )

                                    val isClassifyPersonal = state.outsideScheduleBehavior == TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
                                    val isConserveBattery = state.outsideScheduleBehavior == TrackingPreferencesManager.BEHAVIOR_CONSERVE_BATTERY

                                    // Option A: Auto-Classify as Personal
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                width = if (isClassifyPersonal) 2.dp else 1.dp,
                                                color = if (isClassifyPersonal) SucculentPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                viewModel.setOutsideScheduleBehavior(TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL)
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isClassifyPersonal) SucculentPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .border(
                                                        2.dp,
                                                        if (isClassifyPersonal) SucculentPrimary else SlateNeutral,
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isClassifyPersonal) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .clip(CircleShape)
                                                            .background(SucculentPrimary)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "Auto-classify as Personal Drive",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = "Logs drive and automatically marks as Personal without tax deduction.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }

                                    // Option B: Turn Off to Conserve Battery
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                width = if (isConserveBattery) 2.dp else 1.dp,
                                                color = if (isConserveBattery) SucculentPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                viewModel.setOutsideScheduleBehavior(TrackingPreferencesManager.BEHAVIOR_CONSERVE_BATTERY)
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isConserveBattery) SucculentPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .border(
                                                        2.dp,
                                                        if (isConserveBattery) SucculentPrimary else SlateNeutral,
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isConserveBattery) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .clip(CircleShape)
                                                            .background(SucculentPrimary)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "Turn Off Tracking (Conserve Battery)",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = "Stops GPS & Activity Detection outside work schedule to save battery.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Primary Vehicle Information Card (Tesla Model Y)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = SucculentPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Primary Vehicle",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = state.defaultVehicle,
                        onValueChange = { viewModel.setDefaultVehicle(it) },
                        label = { Text("Vehicle Description") },
                        placeholder = { Text("Tesla Model Y") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // Cloudflare D1 Database & Google Sheets Sync (Expandable Table, Collapsed by Default)
        item {
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
                    // Clickable Header to Expand / Collapse Table
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isD1Expanded = !isD1Expanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SucculentPrimary.copy(alpha = 0.15f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = SucculentPrimary,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Cloudflare D1 Database & Sync",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isD1Expanded) "Tap to collapse credentials" else "Tap to expand credentials table",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }

                        val rotationAngle by animateFloatAsState(
                            targetValue = if (isD1Expanded) 180f else 0f,
                            animationSpec = tween(durationMillis = 400),
                            label = "d1_expand_rotation"
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isD1Expanded) "Collapse" else "Expand",
                            modifier = Modifier
                                .size(28.dp)
                                .rotate(rotationAngle),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    // Collapsible Table Content
                    AnimatedVisibility(
                        visible = isD1Expanded,
                        enter = expandVertically(animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)),
                        exit = shrinkVertically(animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = SucculentPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Syncs mileage logs directly into Cloudflare D1 and Google Sheets.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = state.cfEndpoint,
                                onValueChange = { viewModel.setCfEndpoint(it) },
                                label = { Text("API Endpoint URL") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = state.cfClientId,
                                onValueChange = { viewModel.setCfClientId(it) },
                                label = { Text("CF-Access-Client-Id") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = state.cfClientSecret,
                                onValueChange = { viewModel.setCfClientSecret(it) },
                                label = { Text("CF-Access-Client-Secret") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = state.cfBearerToken,
                                onValueChange = { viewModel.setCfBearerToken(it) },
                                label = { Text("Bearer Token (Optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = { viewModel.triggerSyncNow() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SucculentPrimary,
                                    contentColor = Color.White
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Trigger Cloud Sync Now", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
