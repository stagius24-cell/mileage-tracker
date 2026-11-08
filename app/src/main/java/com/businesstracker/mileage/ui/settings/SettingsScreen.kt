package com.businesstracker.mileage.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BusinessCenter
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
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
            SettingsSection(title = "Appearance", icon = Icons.Default.Palette) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dark Mode",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (state.themeMode == AppThemeMode.DARK) "Dark theme enabled" else "Light theme enabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.themeMode == AppThemeMode.DARK,
                        onCheckedChange = { isDark ->
                            viewModel.setThemeMode(if (isDark) AppThemeMode.DARK else AppThemeMode.LIGHT)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SucculentPrimary
                        )
                    )
                }
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
                        onBehaviorChange = viewModel::setOutsideScheduleBehavior
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
                    OutlinedTextField(
                        value = newPurposeText,
                        onValueChange = {
                            newPurposeText = it
                            if (purposeError != null) purposeError = null
                        },
                        label = { Text("New purpose") },
                        placeholder = { Text("e.g. Pop-up Market") },
                        singleLine = true,
                        isError = purposeError != null,
                        supportingText = purposeError?.let { err -> { Text(err, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

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
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add purpose")
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
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
            val timeFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
            val lastSync = if (state.sheetsLastSyncTime > 0L) {
                timeFormat.format(Date(state.sheetsLastSyncTime))
            } else {
                "Never"
            }
            var isSpreadsheetIdExpanded by remember { mutableStateOf(false) }

            SettingsSection(
                title = "Google Sheets",
                subtitle = if (state.isSheetsConnected) "Connected" else "Not connected",
                subtitlePositive = state.isSheetsConnected,
                icon = Icons.Default.TableChart
            ) {
                if (!state.sheetsLastError.isNullOrBlank()) {
                    CompactError(state.sheetsLastError.orEmpty())
                }

                AccountRow(
                    connected = state.hasOAuthCredential,
                    email = state.connectedAccountEmail,
                    onConnect = {
                        googleSignInLauncher.launch(viewModel.getGoogleSignInClient().signInIntent)
                    },
                    onDisconnect = viewModel::disconnectSheets
                )

                // Collapsible Spreadsheet ID configuration
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isSpreadsheetIdExpanded = !isSpreadsheetIdExpanded }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Spreadsheet ID configuration",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = if (isSpreadsheetIdExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isSpreadsheetIdExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (isSpreadsheetIdExpanded) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.sheetsSpreadsheetId,
                            onValueChange = viewModel::setSheetsSpreadsheetId,
                            label = { Text("Spreadsheet ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetaText("Last sync", lastSync)
                    MetaText("Pending", state.pendingTripsCount.toString(), Alignment.End)
                }

                Button(
                    onClick = viewModel::syncSheetsNow,
                    enabled = !state.isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
                ) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.isSyncing) "Syncing" else "Sync now")
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
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

@Composable
private fun ScheduleControls(
    state: SettingsUiState,
    onToggleDay: (String) -> Unit,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onBehaviorChange: (String) -> Unit
) {
    val days = listOf(
        "MON" to "M", "TUE" to "T", "WED" to "W", "THU" to "T",
        "FRI" to "F", "SAT" to "S", "SUN" to "S"
    )

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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = state.workHoursStart,
            onValueChange = onStartChange,
            label = { Text("Start") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = state.workHoursEnd,
            onValueChange = onEndChange,
            label = { Text("End") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
    }

    Text("Outside work hours", style = MaterialTheme.typography.labelLarge)
    CompactRadioRow(
        title = "Classify as personal",
        selected = state.outsideScheduleBehavior == TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL,
        onClick = { onBehaviorChange(TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL) }
    )
    CompactRadioRow(
        title = "Do not track",
        selected = state.outsideScheduleBehavior == TrackingPreferencesManager.BEHAVIOR_CONSERVE_BATTERY,
        onClick = { onBehaviorChange(TrackingPreferencesManager.BEHAVIOR_CONSERVE_BATTERY) }
    )
}

@Composable
private fun CompactRadioRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium)
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
