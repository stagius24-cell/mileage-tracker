package com.businesstracker.mileage.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import com.businesstracker.mileage.domain.model.TripPurpose
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.businesstracker.mileage.BuildConfig
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.AppThemeMode
import com.businesstracker.mileage.ui.theme.SpeedRed
import com.businesstracker.mileage.ui.theme.SucculentPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    initialEditLocationId: String = "",
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.handleGoogleSignInResult(result.data) }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.refreshPairedBluetoothDevices()
            viewModel.setBluetoothAutoStopEnabled(true)
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshPairedBluetoothDevices() }

    var showVersionHistoryDialog by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Saved Locations State
    var showLocationDialog by remember { mutableStateOf(false) }
    var editingLocation by remember { mutableStateOf<com.businesstracker.mileage.data.local.entity.SavedLocationEntity?>(null) }
    var locationPendingDeletion by remember { mutableStateOf<com.businesstracker.mileage.data.local.entity.SavedLocationEntity?>(null) }
    var locationActionError by remember { mutableStateOf<String?>(null) }
    // Open the requested saved location editor as soon as its Room record is loaded.
    // Navigation from a trip's bookmark must not stop at the Settings list.
    var handledEditLocationId by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(initialEditLocationId, state.savedLocations) {
        if (initialEditLocationId.isNotBlank() && initialEditLocationId != handledEditLocationId) {
            state.savedLocations.firstOrNull { it.id == initialEditLocationId }?.let { location ->
                handledEditLocationId = initialEditLocationId
                editingLocation = location
                showLocationDialog = true
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .then(if (showVersionHistoryDialog) Modifier.blur(8.dp) else Modifier)
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            var bluetoothMenuExpanded by remember { mutableStateOf(false) }

            SettingsSection(title = "Vehicle", icon = Icons.Default.DirectionsCar) {
                OutlinedTextField(
                    value = state.defaultVehicle,
                    onValueChange = viewModel::setDefaultVehicle,
                    label = { Text("Vehicle name") },
                    placeholder = { Text("Tesla Model Y") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                SectionDivider()
                SettingsSwitchRow(
                    title = "Bluetooth auto-stop",
                    description = "Stop after the selected car disconnects",
                    checked = state.bluetoothAutoStopEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        } else {
                            viewModel.setBluetoothAutoStopEnabled(enabled)
                            if (enabled) viewModel.refreshPairedBluetoothDevices()
                        }
                    }
                )

                if (state.bluetoothAutoStopEnabled) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.refreshPairedBluetoothDevices()
                                bluetoothMenuExpanded = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                state.vehicleBluetoothName.takeIf { it.isNotBlank() }
                                    ?: "Choose paired car"
                            )
                        }
                        DropdownMenu(
                            expanded = bluetoothMenuExpanded,
                            onDismissRequest = { bluetoothMenuExpanded = false }
                        ) {
                            if (state.pairedBluetoothDevices.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No paired devices found") },
                                    onClick = {},
                                    enabled = false
                                )
                            } else {
                                state.pairedBluetoothDevices.forEach { device ->
                                    DropdownMenuItem(
                                        text = { Text(device.name) },
                                        onClick = {
                                            viewModel.selectVehicleBluetoothDevice(device)
                                            bluetoothMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsSection(title = "Appearance", icon = Icons.Default.Palette) {
                SettingsSwitchRow(
                    title = "Dark Mode",
                    description = if (state.themeMode == AppThemeMode.DARK) "Dark theme enabled" else "Light theme enabled",
                    checked = state.themeMode == AppThemeMode.DARK,
                    onCheckedChange = { isDark ->
                        viewModel.setThemeMode(if (isDark) AppThemeMode.DARK else AppThemeMode.LIGHT)
                    }
                )
            }
        }

        item {
            SettingsSection(title = "Drive tracking", icon = Icons.Default.LocationOn) {
                SettingsSwitchRow(
                    title = "Automatic detection",
                    description = "Detect drives in the background",
                    checked = state.autoTrackingEnabled,
                    onCheckedChange = viewModel::setAutoTrackingEnabled
                )

                if (state.autoTrackingEnabled && !state.hasBackgroundLocationPermission) {
                    CompactAlert(
                        text = "Allow location all the time for automatic tracking.",
                        actionLabel = "Open settings",
                        onAction = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            )
                        }
                    )
                }

                SectionDivider()
                SettingsSwitchRow(
                    title = "Work schedule",
                    description = "Apply work days and hours",
                    checked = state.scheduleFilteringEnabled,
                    enabled = state.autoTrackingEnabled,
                    onCheckedChange = viewModel::setScheduleFilteringEnabled
                )

                if (state.autoTrackingEnabled && state.scheduleFilteringEnabled) {
                    ScheduleControls(
                        state = state,
                        onToggleDay = viewModel::toggleWorkDay,
                        onStartChange = viewModel::setWorkHoursStart,
                        onEndChange = viewModel::setWorkHoursEnd,
                        onBehaviorChange = viewModel::setOutsideScheduleBehavior,
                        onFieldFocus = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index = 3, scrollOffset = 0)
                            }
                        }
                    )
                }

                SectionDivider()
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (_: Exception) {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.BatterySaver, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Battery optimization")
                }
            }
        }

                item {
            var newPurposeText by remember { mutableStateOf("") }
            var purposeError by remember { mutableStateOf<String?>(null) }
            var editingPurpose by remember { mutableStateOf<String?>(null) }
            var editPurposeText by remember { mutableStateOf("") }
            var editPurposeError by remember { mutableStateOf<String?>(null) }
            var deleteConfirmationPurpose by remember { mutableStateOf<String?>(null) }

            // Edit Purpose Dialog
            if (editingPurpose != null) {
                AlertDialog(
                    onDismissRequest = {
                        editingPurpose = null
                        editPurposeError = null
                    },
                    title = {
                        Text(
                            text = "Edit Purpose",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "Rename this business purpose. Existing trips will be updated if assigned to this name.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = editPurposeText,
                                onValueChange = {
                                    editPurposeText = it
                                    if (editPurposeError != null) editPurposeError = null
                                },
                                label = { Text("Purpose Name") },
                                singleLine = true,
                                isError = editPurposeError != null,
                                supportingText = editPurposeError?.let { err -> { Text(err, color = MaterialTheme.colorScheme.error) } },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val trimmed = editPurposeText.trim()
                                val old = editingPurpose ?: ""
                                if (trimmed.isBlank()) {
                                    editPurposeError = "Purpose cannot be empty"
                                } else if (!trimmed.equals(old, ignoreCase = true) &&
                                    state.customPurposes.any { it.equals(trimmed, ignoreCase = true) }
                                ) {
                                    editPurposeError = "Purpose already exists"
                                } else {
                                    if (!trimmed.equals(old, ignoreCase = false)) {
                                        viewModel.updateCustomPurpose(old, trimmed)
                                    }
                                    editingPurpose = null
                                    editPurposeError = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                editingPurpose = null
                                editPurposeError = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Delete Confirmation Dialog
            if (deleteConfirmationPurpose != null) {
                AlertDialog(
                    onDismissRequest = { deleteConfirmationPurpose = null },
                    title = {
                        Text(
                            text = "Delete Purpose",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "Are you sure you want to delete \"${deleteConfirmationPurpose}\"? Existing trips will retain their recorded purpose.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                deleteConfirmationPurpose?.let { viewModel.removeCustomPurpose(it) }
                                deleteConfirmationPurpose = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SpeedRed)
                        ) {
                            Text("Delete", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteConfirmationPurpose = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            SettingsSection(
                title = "Business Trip Purposes",
                icon = Icons.Default.BusinessCenter
            ) {
                Text(
                    text = "Add, edit, or remove custom purposes for categorizing your business drives.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Input row to add new custom purpose
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (purposeError != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (newPurposeText.isEmpty()) {
                                Text(
                                    text = "New purpose",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1
                                )
                            }
                            BasicTextField(
                                value = newPurposeText,
                                onValueChange = {
                                    newPurposeText = it
                                    if (purposeError != null) purposeError = null
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(SucculentPrimary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val trimmed = newPurposeText.trim()
                            if (trimmed.isBlank()) {
                                purposeError = "Purpose cannot be empty"
                            } else if (state.customPurposes.any { it.equals(trimmed, ignoreCase = true) }) {
                                purposeError = "Purpose already exists"
                            } else {
                                viewModel.addCustomPurpose(trimmed)
                                newPurposeText = ""
                                purposeError = null
                            }
                        },
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add purpose", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }

                if (purposeError != null) {
                    Text(
                        text = purposeError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Custom purposes chips
                if (state.customPurposes.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.customPurposes.forEach { purpose ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SucculentPrimary.copy(alpha = 0.12f),
                                contentColor = SucculentPrimary
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Text(
                                        text = purpose,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    // Edit button
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                editingPurpose = purpose
                                                editPurposeText = purpose
                                                editPurposeError = null
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit $purpose",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(2.dp))
                                    // Delete button
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .clickable { deleteConfirmationPurpose = purpose },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete $purpose",
                                            tint = SpeedRed,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsSection(
                title = "Saved Locations",
                subtitle = "Custom place names for trip history",
                icon = Icons.Default.LocationOn,
                trailing = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Saved Locations options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            ) {
                if (state.savedLocations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No saved locations yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.savedLocations.forEach { loc ->
                            SavedLocationItem(
                                location = loc,
                                onEdit = {
                                    editingLocation = loc
                                    showLocationDialog = true
                                },
                                onDelete = {
                                    locationPendingDeletion = loc
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        editingLocation = null
                        showLocationDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Saved Location", fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            val timeFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
            val latestTime = state.latestBackupTimestamp
            val lastBackupFormatted = if (latestTime > 0L) {
                timeFormat.format(Date(latestTime))
            } else {
                "Never"
            }
            var isAdvancedExpanded by remember { mutableStateOf(false) }
            val clipboardManager = LocalClipboardManager.current

            SettingsSection(
                title = "Backup & Restore",
                subtitle = if (state.isSheetsConnected) "Google Sheets · Connected" else "Google Sheets · Not connected",
                subtitlePositive = state.isSheetsConnected,
                icon = Icons.Default.CloudSync,
                trailing = {
                    IconButton(
                        onClick = { showVersionHistoryDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Backup info & version history",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            ) {
                // 1. Combined Backup Status
                CombinedBackupStatusBox(
                    state = state,
                    lastBackupFormatted = lastBackupFormatted
                )

                // 2. Compact Inline Pill Action Buttons (Back Up & Restore)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isBackingUp = state.isUnifiedBackingUp
                    val isRestoring = state.isLoadingRestorePreview || state.isApplyingRestore

                    // Left Button: Back Up (Solid SucculentPrimary container, white text/icon)
                    Button(
                        onClick = viewModel::backupAllNow,
                        enabled = !isBackingUp && !isRestoring && state.isSheetsConnected,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SucculentPrimary,
                            contentColor = Color.White,
                            disabledContainerColor = SucculentPrimary.copy(alpha = 0.4f),
                            disabledContentColor = Color.White.copy(alpha = 0.7f)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        if (isBackingUp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Backing Up...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Back Up",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = "Trips + Locations",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }

                    // Right Button: Restore (Outlined/Soft Container, SucculentPrimary border & text)
                    Button(
                        onClick = viewModel::restoreSavedLocationsFromSheets,
                        enabled = !isBackingUp && !isRestoring && state.isSheetsConnected,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, SucculentPrimary.copy(alpha = 0.35f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        if (isRestoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = SucculentPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Loading...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = SucculentPrimary
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = SucculentPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Restore",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "From Google Sheets",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // 3. Collapsible Google Sheets (Advanced) Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            tint = SucculentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Google Sheets (Advanced)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isAdvancedExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (isAdvancedExpanded) {
                        Spacer(Modifier.height(8.dp))

                        // AccountRow (Connected account, switch, disconnect)
                        AccountRow(
                            connected = state.hasOAuthCredential,
                            email = state.connectedAccountEmail,
                            onConnect = {
                                googleSignInLauncher.launch(viewModel.getGoogleSignInClient().signInIntent)
                            },
                            onDisconnect = viewModel::disconnectSheets
                        )

                        Spacer(Modifier.height(10.dp))

                        // Spreadsheet ID with Copy icon
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Spreadsheet ID",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = state.sheetsSpreadsheetId.ifBlank { "No spreadsheet ID set" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (state.sheetsSpreadsheetId.isNotBlank()) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (state.sheetsSpreadsheetId.isNotBlank()) {
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(state.sheetsSpreadsheetId))
                                                Toast.makeText(context, "Spreadsheet ID copied", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Spreadsheet ID",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Last sync & Pending metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Last sync",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (state.sheetsLastSyncTime > 0L) timeFormat.format(Date(state.sheetsLastSyncTime)) else "Never",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Pending",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${state.pendingTripsCount} trips · ${state.savedLocationsPendingBackupCount} places",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showVersionHistoryDialog = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Saved Location Delete Dialog
    if (locationPendingDeletion != null) {
        val loc = locationPendingDeletion!!
        AlertDialog(
            onDismissRequest = { locationPendingDeletion = null },
            title = {
                Text(
                    text = "Delete Saved Location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to delete \"${loc.name}\"? Associated trips and GPS data will not be deleted.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (locationActionError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = locationActionError!!,
                            color = SpeedRed,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        locationActionError = null
                        viewModel.deleteLocation(
                            location = loc,
                            onSuccess = {
                                locationPendingDeletion = null
                            },
                            onError = { err ->
                                locationActionError = err
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpeedRed),
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        locationPendingDeletion = null
                        locationActionError = null
                    },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Saved Location Add/Edit Dialog
    if (showLocationDialog) {
        SavedLocationDialog(
            initialLocation = editingLocation,
            onDismiss = {
                showLocationDialog = false
                editingLocation = null
            },
            onSearchAddress = { query -> viewModel.searchAddress(query) },
            onGetCurrentLocation = { viewModel.getCurrentLocation() },
            onSave = { id, name, lat, lng, addr, radius, icon, pref, entity, onSaveComplete ->
                viewModel.saveLocation(
                    id = id,
                    name = name,
                    latitude = lat,
                    longitude = lng,
                    address = addr,
                    radiusMeters = radius,
                    icon = icon,
                    classificationPreference = pref,
                    businessEntity = entity,
                    onSuccess = {
                        onSaveComplete(null)
                        showLocationDialog = false
                        editingLocation = null
                    },
                    onError = { err ->
                        onSaveComplete(err)
                    }
                )
            }
        )
    }

    state.restorePreviewState?.let { previewUi ->
        SavedLocationsRestorePreviewDialog(
            state = previewUi,
            isApplying = state.isApplyingRestore,
            onToggleCandidate = viewModel::toggleRestoreCandidateSelection,
            onSetOverwriteConflicts = viewModel::setOverwriteConflictsApproved,
            onConfirmApply = viewModel::confirmApplySavedLocationsRestore,
            onDismiss = viewModel::dismissRestorePreview
        )
    }

    if (showVersionHistoryDialog) {
        val updates = remember {
            listOf(
                "Version 1.0.108 (Build 109)" to "Clean mileage ring with labeled category cards.",
                "Version 1.0.107 (Build 108)" to "Removed redundant dashboard totals and heading.",
                "Version 1.0.106 (Build 107)" to "Simplified mileage ring and category value cards."
            )
        }

        Dialog(
            onDismissRequest = { showVersionHistoryDialog = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showVersionHistoryDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                    tonalElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Version History",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { showVersionHistoryDialog = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Floating Version List with Summary Underneath
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .border(
                                    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            updates.forEachIndexed { index, (version, summary) ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = version,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = SucculentPrimary
                                    )
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (index < updates.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    subtitlePositive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SucculentPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (subtitlePositive) SucculentPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.45f
                )
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SucculentPrimary,
                checkedBorderColor = SucculentPrimary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                disabledCheckedThumbColor = Color.White.copy(alpha = 0.7f),
                disabledCheckedTrackColor = SucculentPrimary.copy(alpha = 0.35f),
                disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        )
    }
}

private fun formatDisplayTime(time24: String): String {
    return try {
        val parts = time24.trim().split(":")
        val h = parts[0].toInt().coerceIn(0, 23)
        val m = if (parts.size > 1) parts[1].toInt().coerceIn(0, 59) else 0
        val isPm = h >= 12
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        val amPm = if (isPm) "PM" else "AM"
        String.format("%02d:%02d %s", h12, m, amPm)
    } catch (_: Exception) {
        time24
    }
}

@Composable
private fun ScheduleControls(
    state: SettingsUiState,
    onToggleDay: (String) -> Unit,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onBehaviorChange: (String) -> Unit,
    onFieldFocus: () -> Unit = {}
) {
    val days = listOf(
        "MON" to "M", "TUE" to "T", "WED" to "W", "THU" to "T",
        "FRI" to "F", "SAT" to "S", "SUN" to "S"
    )
    var pickingTimeFor by remember { mutableStateOf<String?>(null) } // "START", "END", or null

    if (pickingTimeFor != null) {
        val isStart = pickingTimeFor == "START"
        val currentTime = if (isStart) state.workHoursStart else state.workHoursEnd
        WheelTimePickerDialog(
            initialTime24 = currentTime,
            onDismiss = { pickingTimeFor = null },
            onTimeConfirm = { newTime24 ->
                if (isStart) {
                    onStartChange(newTime24)
                } else {
                    onEndChange(newTime24)
                }
                pickingTimeFor = null
            }
        )
    }

    Text("Work days", style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        days.forEach { (code, label) ->
            FilterChip(
                selected = code in state.workDays,
                onClick = { onToggleDay(code) },
                modifier = Modifier.weight(1f),
                label = { Text(label) }
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Start Time Card (clickable, opens Wheel Time Picker Dialog)
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { pickingTimeFor = "START" },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Start",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatDisplayTime(state.workHoursStart),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "Pick start time",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // End Time Card (clickable, opens Wheel Time Picker Dialog)
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { pickingTimeFor = "END" },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "End",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatDisplayTime(state.workHoursEnd),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "Pick end time",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    Text("Outside work hours", style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isPersonal = state.outsideScheduleBehavior == TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL
        val isDoNotTrack = state.outsideScheduleBehavior == TrackingPreferencesManager.BEHAVIOR_CONSERVE_BATTERY

        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onBehaviorChange(TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isPersonal,
                onClick = { onBehaviorChange(TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL) }
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Personal",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onBehaviorChange(TrackingPreferencesManager.BEHAVIOR_CONSERVE_BATTERY) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isDoNotTrack,
                onClick = { onBehaviorChange(TrackingPreferencesManager.BEHAVIOR_CONSERVE_BATTERY) }
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Do not track",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactAlert(text: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun AccountRow(
    connected: Boolean,
    email: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (connected) "Google account" else "Connect Google account",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            email?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (connected) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Switch account button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .clickable(onClick = onConnect),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Switch account",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Disconnect account button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SpeedRed.copy(alpha = 0.12f))
                        .clickable(onClick = onDisconnect),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = "Disconnect account",
                        tint = SpeedRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            Button(
                onClick = onConnect,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun CompactSuccess(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SucculentPrimary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = SucculentPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = SucculentPrimary)
    }
}

@Composable
private fun CompactError(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpeedRed.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            tint = SpeedRed,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = SpeedRed)
    }
}

@Composable
private fun StatusText(text: String, positive: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (positive) SucculentPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun MetaText(
    label: String,
    value: String,
    alignment: Alignment.Horizontal = Alignment.Start
) {
    Column(horizontalAlignment = alignment) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun CombinedBackupStatusBox(
    state: SettingsUiState,
    lastBackupFormatted: String
) {
    val isAllBackedUp = state.isAllDataBackedUp
    val hasPending = state.totalPendingCount > 0
    val hasError = !state.unifiedBackupError.isNullOrBlank() ||
        !state.sheetsLastError.isNullOrBlank() ||
        !state.savedLocationsLastBackupError.isNullOrBlank()
    val isConnected = state.isSheetsConnected

    val boxBg = when {
        hasError -> SpeedRed.copy(alpha = 0.08f)
        isAllBackedUp -> SucculentPrimary.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    val boxBorder = when {
        hasError -> BorderStroke(1.dp, SpeedRed.copy(alpha = 0.3f))
        isAllBackedUp -> BorderStroke(1.dp, SucculentPrimary.copy(alpha = 0.25f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = boxBg,
        border = boxBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Status Banner (CheckCircle / CloudOff / Error / Sync)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasError) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = SpeedRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (!state.sheetsLastError.isNullOrBlank() && !state.savedLocationsLastBackupError.isNullOrBlank()) {
                            "Backup failed"
                        } else {
                            "Partial backup completed"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SpeedRed
                    )
                } else if (isAllBackedUp) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SucculentPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "All data backed up",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else if (!isConnected) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Google Sheets not connected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (hasPending) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = SucculentPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Pending backup changes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Ready to back up",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Row 2: Visual Metrics Row (Car icon + "Trips: Up to date" | Divider | Location icon + "Locations: X")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Trips Metric
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = SucculentPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (state.pendingTripsCount > 0) {
                            "Trips: ${state.pendingTripsCount} pending"
                        } else {
                            "Trips: Up to date"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Vertical Divider between metrics
                Box(
                    modifier = Modifier
                        .height(14.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )

                Spacer(Modifier.width(12.dp))

                // Locations Metric
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = SucculentPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    val locCount = state.savedLocations.size
                    val locText = if (state.savedLocationsPendingBackupCount > 0) {
                        "Locations: ${state.savedLocationsPendingBackupCount} pending"
                    } else {
                        "Locations: $locCount"
                    }
                    Text(
                        text = locText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Row 3: Clock icon + "Last backup: Nov 11, 5:46 PM"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Last backup: $lastBackupFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Error message banner if present
            val errorMsg = state.unifiedBackupError ?: state.sheetsLastError ?: state.savedLocationsLastBackupError
            if (!errorMsg.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = errorMsg,
                    style = MaterialTheme.typography.labelSmall,
                    color = SpeedRed
                )
            }
        }
    }
}
