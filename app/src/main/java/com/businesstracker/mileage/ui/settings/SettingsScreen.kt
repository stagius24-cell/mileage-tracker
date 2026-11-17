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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.businesstracker.mileage.BuildConfig
import com.businesstracker.mileage.data.preferences.TrackingPreferencesManager
import com.businesstracker.mileage.domain.model.AppThemeMode
import com.businesstracker.mileage.ui.components.AppConfirmationDialog
import com.businesstracker.mileage.ui.components.AppFloatingBottomSheet
import com.businesstracker.mileage.ui.components.AppFloatingCard
import com.businesstracker.mileage.ui.components.AppPrimaryButton
import com.businesstracker.mileage.ui.components.AppSecondaryButton
import com.businesstracker.mileage.ui.theme.AppDimensions
import com.businesstracker.mileage.ui.theme.AppShapes
import com.businesstracker.mileage.ui.theme.AppSpacing
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

    val bluetoothPickerPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.refreshPairedBluetoothDevices() }

    LaunchedEffect(Unit) { viewModel.refreshPairedBluetoothDevices() }

    var showVersionHistoryDialog by rememberSaveable { mutableStateOf(false) }
    var showDisconnectConfirmation by rememberSaveable { mutableStateOf(false) }
    var showEditVehicle by rememberSaveable { mutableStateOf(false) }
    var vehicleNameDraft by rememberSaveable { mutableStateOf("") }
    var vehicleNameError by rememberSaveable { mutableStateOf<String?>(null) }
    var isSavingVehicle by remember { mutableStateOf(false) }
    var showBluetoothDevicePicker by rememberSaveable { mutableStateOf(false) }
    var isSavingBluetoothDevice by remember { mutableStateOf(false) }
    var bluetoothPickerError by remember { mutableStateOf<String?>(null) }
    var isScheduleExpanded by rememberSaveable { mutableStateOf(false) }
    var showSpreadsheetIdEditor by rememberSaveable { mutableStateOf(false) }
    var spreadsheetIdInput by rememberSaveable { mutableStateOf("") }
    var spreadsheetIdError by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Saved Locations State
    var showLocationDialog by remember { mutableStateOf(false) }
    var showSavedLocationsList by remember { mutableStateOf(false) }
    var showPurposeList by remember { mutableStateOf(false) }
    var newPurposeText by remember { mutableStateOf("") }
    var showAddPurpose by remember { mutableStateOf(false) }
    var purposeError by remember { mutableStateOf<String?>(null) }
    var editingPurpose by remember { mutableStateOf<String?>(null) }
    var editPurposeText by remember { mutableStateOf("") }
    var editPurposeError by remember { mutableStateOf<String?>(null) }
    var deleteConfirmationPurpose by remember { mutableStateOf<String?>(null) }
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
            .background(if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) MaterialTheme.colorScheme.background else Color(0xFFF3F6FA)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        VehicleSettingsIcon(Icons.Default.DirectionsCar)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Vehicle", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(3.dp))
                            Text(state.defaultVehicle, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(14.dp), color = SucculentPrimary.copy(alpha = 0.10f)) {
                            IconButton(
                                onClick = {
                                    vehicleNameDraft = state.defaultVehicle
                                    vehicleNameError = null
                                    showEditVehicle = true
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit vehicle name",
                                    tint = SucculentPrimary, modifier = Modifier.size(21.dp))
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        VehicleSettingsIcon(Icons.Default.Bluetooth)
                        Spacer(Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    bluetoothPickerError = null
                                    viewModel.refreshPairedBluetoothDevices()
                                    showBluetoothDevicePicker = true
                                },
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Bluetooth auto-stop", style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    state.vehicleBluetoothName.ifBlank { "Select Bluetooth device" },
                                    modifier = Modifier.weight(1f, fill = false),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Default.ExpandMore, contentDescription = "Select Bluetooth device",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        SettingsToggle(
                            checked = state.bluetoothAutoStopEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                                    != PackageManager.PERMISSION_GRANTED
                                ) {
                                    bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                } else {
                                    viewModel.setBluetoothAutoStopEnabled(enabled)
                                    if (enabled) viewModel.refreshPairedBluetoothDevices()
                                }
                            },
                            label = "Bluetooth auto-stop"
                        )
                    }
                }
            }
        }

        item {
            SettingsSection(
                title = "Dark mode",
                icon = Icons.Default.DarkMode,
                trailing = {
                    SettingsToggle(
                        checked = state.themeMode == AppThemeMode.DARK,
                        onCheckedChange = { isDark ->
                            viewModel.setThemeMode(if (isDark) AppThemeMode.DARK else AppThemeMode.LIGHT)
                        },
                        label = "Dark mode"
                    )
                }
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    // Section 1 — Drive tracking
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VehicleSettingsIcon(Icons.Outlined.LocationOn)
                        Spacer(Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Drive tracking",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Automatic detection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        SettingsToggle(
                            checked = state.autoTrackingEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setAutoTrackingEnabled(enabled)
                            },
                            label = "Automatic detection"
                        )
                    }

                    if (state.autoTrackingEnabled && !state.hasBackgroundLocationPermission) {
                        Spacer(Modifier.height(4.dp))
                        CompactAlert(
                            text = "Allow location all the time for automatic tracking.",
                            actionLabel = "Open settings",
                            onAction = {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                })
                            }
                        )
                    }
                    if (state.autoTrackingEnabled && !state.isTransitionsRegistered && state.transitionRegistrationError != null) {
                        Spacer(Modifier.height(4.dp))
                        CompactAlert(
                            text = "Activity detection failed: ${state.transitionRegistrationError}",
                            actionLabel = "Retry",
                            onAction = { viewModel.retryRegisterTransitions() }
                        )
                    }

                    // Subtle divider between Section 1 and Section 2
                    CompactSettingsDivider()

                    // Section 2 — Schedule
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VehicleSettingsIcon(Icons.Default.Schedule)
                        Spacer(Modifier.width(12.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isScheduleExpanded = !isScheduleExpanded }
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Schedule",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isScheduleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isScheduleExpanded) "Collapse schedule" else "Expand schedule",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = formatScheduleSummary(state.workDays, state.workHoursStart, state.workHoursEnd),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        SettingsToggle(
                            checked = state.scheduleFilteringEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setScheduleFilteringEnabled(enabled)
                            },
                            label = "Schedule"
                        )
                    }

                    // Expandable schedule controls
                    AnimatedVisibility(
                        visible = isScheduleExpanded,
                        enter = expandVertically(animationSpec = tween(180)),
                        exit = shrinkVertically(animationSpec = tween(180))
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ScheduleControls(
                                state = state,
                                onToggleDay = viewModel::toggleWorkDay,
                                onStartChange = viewModel::setWorkHoursStart,
                                onEndChange = viewModel::setWorkHoursEnd,
                                onBehaviorChange = viewModel::setOutsideScheduleBehavior
                            )
                        }
                    }
                }
            }
        }

        item {
            com.businesstracker.mileage.ui.smart.SmartClassificationCard(
                isEnabled = state.isSmartTripRecognitionEnabled,
                currentMode = state.smartClassificationMode,
                onToggleEnabled = viewModel::setSmartTripRecognitionEnabled,
                onModeSelected = viewModel::setSmartClassificationMode
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    val purposeCount = state.customPurposes.size
                    val purposeSubtitle = if (purposeCount == 1) "1 business purpose" else "$purposeCount business purposes"
                    SettingsManagementRow(
                        title = "Business purposes",
                        subtitle = purposeSubtitle,
                        icon = Icons.Outlined.BusinessCenter,
                        onAdd = { purposeError = null; showAddPurpose = true },
                        onOpen = { showPurposeList = true }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                    val locationCount = state.savedLocations.size
                    val locationSubtitle = if (locationCount == 1) "1 saved place" else "$locationCount saved places"
                    SettingsManagementRow(
                        title = "Saved Locations",
                        subtitle = locationSubtitle,
                        icon = Icons.Outlined.LocationOn,
                        onAdd = { editingLocation = null; showLocationDialog = true },
                        onOpen = { showSavedLocationsList = true }
                    )
                }
            }
        }

        item {
            val timeFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
            val lastBackupTime = state.latestBackupTimestamp.takeIf { it > 0L }
            var isAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
            val clipboardManager = LocalClipboardManager.current
            val isBackingUp = state.isUnifiedBackingUp
            val isRestoring = state.isLoadingRestorePreview || state.isApplyingRestore
            val backupError = state.unifiedBackupError ?: state.savedLocationsLastBackupError ?: state.sheetsLastError
            val backupCurrent = state.isAllDataBackedUp && backupError.isNullOrBlank() && !isBackingUp

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = SucculentPrimary.copy(alpha = .10f)) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.CloudUpload, contentDescription = null,
                                    tint = SucculentPrimary, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Backup & Restore", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            val syncSubtitle = if (state.isSheetsConnected) {
                                lastBackupTime?.let { "Synced on ${timeFormat.format(Date(it))}" } ?: "Connected"
                            } else {
                                "Disconnected"
                            }
                            Text(
                                text = syncSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!backupError.isNullOrBlank()) {
                        Text(backupError, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = viewModel::backupAllNow,
                            enabled = !isBackingUp && !isRestoring && state.isSheetsConnected,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SucculentPrimary.copy(alpha = 0.12f),
                                contentColor = SucculentPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            if (isBackingUp) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = SucculentPrimary)
                            else Icon(Icons.Outlined.CloudUpload, contentDescription = null,
                                tint = SucculentPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isBackingUp) "Backing up" else "Back Up", fontWeight = FontWeight.SemiBold)
                        }
                        Button(onClick = viewModel::restoreSavedLocationsFromSheets,
                            enabled = !isBackingUp && !isRestoring && state.isSheetsConnected,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface),
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            if (isRestoring) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.CloudDownload, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isRestoring) "Loading" else "Restore", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                    Column(Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(180))) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                                .semantics {
                                    contentDescription = if (isAdvancedExpanded) "Collapse Google Sheets settings" else "Expand Google Sheets settings"
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VehicleSettingsIcon(Icons.Outlined.TableChart)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Google Sheets settings", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text("Account, spreadsheet & sync", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isAdvancedExpanded) {
                            Spacer(Modifier.height(12.dp))
                            AccountRow(
                                connected = state.hasOAuthCredential,
                                email = state.connectedAccountEmail,
                                onConnect = { googleSignInLauncher.launch(viewModel.getGoogleSignInClient().signInIntent) },
                                onDisconnect = { showDisconnectConfirmation = true }
                            )
                            HorizontalDivider(Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                            Text("Spreadsheet ID", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(state.sheetsSpreadsheetId.ifBlank { "No spreadsheet ID set" },
                                    modifier = Modifier.weight(1f), maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = {
                                    spreadsheetIdInput = state.sheetsSpreadsheetId
                                    spreadsheetIdError = false
                                    showSpreadsheetIdEditor = true
                                }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Spreadsheet ID",
                                        modifier = Modifier.size(18.dp))
                                }
                                if (state.sheetsSpreadsheetId.isNotBlank()) IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(state.sheetsSpreadsheetId))
                                        Toast.makeText(context, "Spreadsheet ID copied", Toast.LENGTH_SHORT).show()
                                    }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Spreadsheet ID",
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("Last sync", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (state.sheetsLastSyncTime > 0L) timeFormat.format(Date(state.sheetsLastSyncTime)) else "Never",
                                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Pending", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${state.pendingTripsCount} trips · ${state.savedLocationsPendingBackupCount} places",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (state.totalPendingCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
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
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showVersionHistoryDialog = true }
                        .heightIn(min = 48.dp)
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

    if (showBluetoothDevicePicker) {
        AppFloatingBottomSheet(
            onDismissRequest = { if (!isSavingBluetoothDevice) showBluetoothDevicePicker = false },
            title = "Select Bluetooth Device",
            onClose = { if (!isSavingBluetoothDevice) showBluetoothDevicePicker = false },
            isDismissible = !isSavingBluetoothDevice
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.pairedBluetoothDevices.isEmpty()) {
                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                        PackageManager.PERMISSION_GRANTED
                    Text(
                        if (needsPermission) "Allow Nearby devices access to see paired devices."
                        else "No paired Bluetooth devices found. Pair a device in Android settings first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (needsPermission) TextButton(
                        onClick = { bluetoothPickerPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("Allow access") }
                } else {
                    state.pairedBluetoothDevices.forEach { device ->
                        val selected = device.address.equals(state.vehicleBluetoothAddress, ignoreCase = true)
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) SucculentPrimary.copy(alpha = 0.10f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable(enabled = !isSavingBluetoothDevice) {
                                    isSavingBluetoothDevice = true
                                    bluetoothPickerError = null
                                    coroutineScope.launch {
                                        val result = viewModel.selectVehicleBluetoothDevice(device)
                                        isSavingBluetoothDevice = false
                                        if (result.isSuccess) showBluetoothDevicePicker = false
                                        else bluetoothPickerError = result.exceptionOrNull()?.localizedMessage
                                            ?: "Could not save device. Try again."
                                    }
                                }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                                Text(device.address, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "Selected",
                                tint = SucculentPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                bluetoothPickerError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showEditVehicle) {
        val vehicleFocusRequester = remember { FocusRequester() }
        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        val trimmedName = vehicleNameDraft.trim()
        val isValidName = trimmedName.isNotEmpty() && trimmedName.length <= 60
        AppFloatingBottomSheet(
            onDismissRequest = { if (!isSavingVehicle) showEditVehicle = false },
            title = "Edit Vehicle Name",
            onClose = { if (!isSavingVehicle) showEditVehicle = false },
            isDismissible = !isSavingVehicle
        ) {
            LaunchedEffect(Unit) {
                vehicleFocusRequester.requestFocus()
                keyboardController?.show()
            }
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = vehicleNameDraft,
                    onValueChange = {
                        vehicleNameDraft = it
                        vehicleNameError = when {
                            it.trim().isEmpty() -> "Enter a vehicle name"
                            it.trim().length > 60 -> "Use 60 characters or fewer"
                            else -> null
                        }
                    },
                    label = { Text("Vehicle name") },
                    singleLine = true,
                    isError = vehicleNameError != null,
                    supportingText = vehicleNameError?.let { error -> { Text(error) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .focusRequester(vehicleFocusRequester),
                    shape = RoundedCornerShape(13.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { showEditVehicle = false },
                        enabled = !isSavingVehicle,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            isSavingVehicle = true
                            coroutineScope.launch {
                                val result = viewModel.saveVehicleName(trimmedName)
                                isSavingVehicle = false
                                if (result.isSuccess) {
                                    keyboardController?.hide()
                                    showEditVehicle = false
                                } else {
                                    vehicleNameError = result.exceptionOrNull()?.localizedMessage
                                        ?: "Could not save vehicle name. Try again."
                                }
                            }
                        },
                        enabled = isValidName && trimmedName != state.defaultVehicle && !isSavingVehicle,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
                    ) { Text(if (isSavingVehicle) "Saving" else "Save") }
                }
            }
        }
    }

    if (showSpreadsheetIdEditor) {
        val sheetFocusRequester = remember { FocusRequester() }
        AppFloatingBottomSheet(
            onDismissRequest = { showSpreadsheetIdEditor = false },
            title = "Spreadsheet ID",
            onClose = { showSpreadsheetIdEditor = false }
        ) {
            LaunchedEffect(Unit) { sheetFocusRequester.requestFocus() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Paste the ID or the full Google Sheets link.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = spreadsheetIdInput,
                    onValueChange = { spreadsheetIdInput = it; spreadsheetIdError = false },
                    label = { Text("Spreadsheet ID or link") },
                    isError = spreadsheetIdError,
                    supportingText = if (spreadsheetIdError) {
                        { Text("Enter a valid Google Sheets ID or link", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .focusRequester(sheetFocusRequester),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppSecondaryButton(
                        text = "Cancel",
                        onClick = { showSpreadsheetIdEditor = false },
                        modifier = Modifier.weight(1f)
                    )
                    AppPrimaryButton(
                        text = "Save",
                        onClick = {
                            val entered = spreadsheetIdInput.trim()
                            val id = if (entered.startsWith("https://")) {
                                Regex("^https://docs\\.google\\.com/spreadsheets/d/([A-Za-z0-9_-]+)")
                                    .find(entered)?.groupValues?.get(1).orEmpty()
                            } else entered
                            if (id.matches(Regex("[A-Za-z0-9_-]+"))) {
                                viewModel.setSheetsSpreadsheetId(id)
                                showSpreadsheetIdEditor = false
                            } else spreadsheetIdError = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showDisconnectConfirmation) {
        AppFloatingBottomSheet(
            onDismissRequest = { showDisconnectConfirmation = false },
            title = "Disconnect Google Sheets?",
            onClose = { showDisconnectConfirmation = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Backup and sync will stop until you connect an account again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppSecondaryButton(
                        text = "Cancel",
                        onClick = { showDisconnectConfirmation = false },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            viewModel.disconnectSheets()
                            showDisconnectConfirmation = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = AppDimensions.PrimaryButtonHeight),
                        shape = AppShapes.Button,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpeedRed,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Disconnect", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }

    // Edit Purpose Dialog
    if (editingPurpose != null) {
        val purposeFocusRequester = remember { FocusRequester() }
        AppFloatingBottomSheet(
            onDismissRequest = {
                editingPurpose = null
                editPurposeError = null
            },
            title = "Edit Purpose",
            onClose = {
                editingPurpose = null
                editPurposeError = null
            }
        ) {
            LaunchedEffect(Unit) { purposeFocusRequester.requestFocus() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Rename this business purpose. Existing trips will be updated if assigned to this name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .focusRequester(purposeFocusRequester),
                    shape = RoundedCornerShape(12.dp)
                )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
                ) {
                    Text("Save")
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (deleteConfirmationPurpose != null) {
        val purposeToDelete = deleteConfirmationPurpose!!
        AppConfirmationDialog(
            onDismissRequest = { deleteConfirmationPurpose = null },
            title = "Delete Purpose",
            message = "Are you sure you want to delete \"$purposeToDelete\"? Existing trips will retain their recorded purpose.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.removeCustomPurpose(purposeToDelete)
                deleteConfirmationPurpose = null
            },
            dismissLabel = "Cancel",
            onDismiss = { deleteConfirmationPurpose = null },
            isDestructive = true
        )
    }

    if (showAddPurpose) {
        val purposeFocusRequester = remember { FocusRequester() }
        AppFloatingBottomSheet(
            onDismissRequest = { showAddPurpose = false },
            title = "Add Business Purpose",
            onClose = { showAddPurpose = false }
        ) {
            LaunchedEffect(Unit) { purposeFocusRequester.requestFocus() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = newPurposeText,
                    onValueChange = { newPurposeText = it; purposeError = null },
                    label = { Text("Purpose name") },
                    singleLine = true,
                    isError = purposeError != null,
                    supportingText = purposeError?.let { error -> { Text(error) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .focusRequester(purposeFocusRequester),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) MaterialTheme.colorScheme.surfaceVariant else Color.White,
                        unfocusedContainerColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) MaterialTheme.colorScheme.surfaceVariant else Color.White,
                        errorContainerColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) MaterialTheme.colorScheme.surfaceVariant else Color.White
                    )
                )
                Button(
                    onClick = {
                        val trimmed = newPurposeText.trim()
                        when {
                            trimmed.isBlank() -> purposeError = "Purpose cannot be empty"
                            state.customPurposes.any { it.equals(trimmed, ignoreCase = true) } -> purposeError = "Purpose already exists"
                            else -> {
                                viewModel.addCustomPurpose(trimmed)
                                newPurposeText = ""
                                purposeError = null
                                showAddPurpose = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
                ) {
                    Text("Add Purpose")
                }
            }
        }
    }

    if (showPurposeList) {
        AppFloatingBottomSheet(
            onDismissRequest = { showPurposeList = false },
            title = "Business Purposes",
            onClose = { showPurposeList = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.customPurposes.isEmpty()) {
                        item {
                            Text(
                                "No business purposes yet.",
                                modifier = Modifier.padding(vertical = 16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(state.customPurposes.size) { index ->
                        val purpose = state.customPurposes[index]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    purpose,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium
                                )
                                IconButton(
                                    onClick = {
                                        showPurposeList = false
                                        editingPurpose = purpose
                                        editPurposeText = purpose
                                        editPurposeError = null
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit $purpose",
                                        tint = SucculentPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        showPurposeList = false
                                        deleteConfirmationPurpose = purpose
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete $purpose",
                                        tint = SpeedRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSavedLocationsList) {
        AppFloatingBottomSheet(
            onDismissRequest = { showSavedLocationsList = false },
            title = "Saved Locations",
            onClose = { showSavedLocationsList = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.savedLocations.isEmpty()) {
                        item {
                            Text(
                                "No saved locations yet.",
                                modifier = Modifier.padding(vertical = 16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(state.savedLocations.size) { index ->
                        val location = state.savedLocations[index]
                        SavedLocationItem(
                            location = location,
                            onEdit = {
                                showSavedLocationsList = false
                                editingLocation = location
                                showLocationDialog = true
                            },
                            onDelete = {
                                showSavedLocationsList = false
                                locationPendingDeletion = location
                            }
                        )
                    }
                }
            }
        }
    }

    // Saved Location Delete Dialog
    if (locationPendingDeletion != null) {
        val loc = locationPendingDeletion!!
        AppConfirmationDialog(
            onDismissRequest = {
                locationPendingDeletion = null
                locationActionError = null
            },
            title = "Delete Saved Location",
            confirmLabel = "Delete",
            onConfirm = {
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
            dismissLabel = "Cancel",
            onDismiss = {
                locationPendingDeletion = null
                locationActionError = null
            },
            isDestructive = true,
            content = {
                Column {
                    Text(
                        text = "Are you sure you want to delete \"${loc.name}\"? Associated trips and GPS data will not be deleted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                "Version 1.0.190 (Build 190)" to "Re-arm automatic drive detection after an interrupted stationary verification.",
                "Version 1.0.189 (Build 189)" to "Fix Dashboard navigation after Smart Classification notifications and correct the Undo classification label.",
                "Version 1.0.188 (Build 188)" to "Add long-press actions to Trip History cards for quick category editing and trip deletion.",
                "Version 1.0.187 (Build 187)" to "Update Business purposes subtitle to '<count> business purposes' and replace arrow navigation button with plus action button in management cards.",
                "Version 1.0.186 (Build 186)" to "Format schedule subtitle onto two lines when 2 or more work days are selected to avoid text overflow.",
                "Version 1.0.185 (Build 185)" to "Trip detail redesign with 20% shorter capsule tabs, white close button, address abbreviations, and 3-column stats card.",
                "Version 1.0.184 (Build 184)" to "Display purpose count under Business purposes, remove sync checkmark, and style sync status in muted gray.",
                "Version 1.0.183 (Build 183)" to "Position Trip History title above search bar, pinning search bar and date headers on scroll.",
                "Version 1.0.182 (Build 182)" to "Harmonize Settings icon indentation, chevron size, and vertical alignment.",
                "Version 1.0.181 (Build 181)" to "Implement smart sticky date headers in Trip History.",
                "Version 1.0.180 (Build 180)" to "Redesign Smart Classification card with bottom sheet mode selector and clean subtitle.",
                "Version 1.0.177 (Build 178)" to "Keep date headers sticky below the fixed search controls for every sort order.",
                "Version 1.0.176 (Build 177)" to "Combine Build 168 tracking behavior with Build 172 Trip History layout.",
                "Version 1.0.174 (Build 175)" to "Restore Build 172 Trip History layout.",
                "Version 1.0.172 (Build 173)" to "Keep the Trip History title scrolling while search controls stay fixed.",
                "Version 1.0.171 (Build 172)" to "Keep Trip History search and filters fixed while scrolling date groups.",
                "Version 1.0.170 (Build 171)" to "Add native sticky date headers to Trip History.",
                "Version 1.0.169 (Build 170)" to "Await admitted GPS writes before candidate expiry and preserve surviving recovered sessions.",
                "Version 1.0.168 (Build 169)" to "Replace the custom business-purpose button with a compact green plus control.",
                "Version 1.0.167 (Build 168)" to "Ignore stale or unrelated GPS callbacks when measuring silence for automatic trip stopping.",
                "Version 1.0.166 (Build 167)" to "Re-audit tracking completion: real GPS admission barrier during candidate expiry, serialized startup recovery, and GPS-silence watchdog recheck with 10-minute stationary stop fallback.",
                "Version 1.0.165 (Build 166)" to "Audit follow-up tracking repair: strict motion corroboration for stops and restarts, preserved candidate deadlines, and scoped stop debounce.",
                "Version 1.0.164 (Build 165)" to "Matched Google Sheets settings icon and container styling with other settings cards, aligning indentation perfectly.",
                "Version 1.0.163 (Build 164)" to "Fixed false automatic starts while stationary, required fresh corroborated motion for stops, and enforced a 4-minute candidate timeout.",
                "Version 1.0.162 (Build 163)" to "Redesigned compact Drive Tracking and Schedule settings card with inline toggles and independent schedule expansion.",
                "Version 1.0.161 (Build 162)" to "Styled Back Up button background and content color to match the succulent Personal button shade.",
                "Version 1.0.160 (Build 161)" to "Reverted walking auto-stop and Tesla Bluetooth tracking commits to restore clean motion auto-detection.",
                "Version 1.0.159 (Build 160)" to "Shifted plus and chevron action buttons further right with 20% tighter spacing in Settings management cards.",
                "Version 1.0.158 (Build 159)" to "Multi-line work schedule wrap for selections over 4 days to prevent header overflow.",
                "Version 1.0.157 (Build 158)" to "Audited and restored motion auto-detection: clean activity transitions (excluding unsupported ON_FOOT), failure feedback with retry, and motion-based drive confirmation.",
                "Version 1.0.156 (Build 157)" to "Audited and restored motion auto-detection: clean activity transitions, immediate Tesla Model Y Bluetooth drive trigger, unblocked foreground location permissions, and schedule filter default fix.",
                "Version 1.0.155 (Build 156)" to "Restored automatic drive detection with walking transition protection and Tesla Model Y Bluetooth motion trigger.",
                "Version 1.0.150 (Build 151)" to "Refined vertical route connector lines with thinner 1dp stroke and lighter color.",
                "Version 1.0.149 (Build 150)" to "Consistent Google Sheets floating modal with drag handle pull-to-close, app theme surface color, and active custom purpose sync.",
                "Version 1.0.148 (Build 149)" to "Clean Backup & Restore card with inline sync timestamp and removed duplicate status line.",
                "Version 1.0.147 (Build 148)" to "Reliable restore for Custom Business Purposes from Google Sheets with smart sheet detection, Sheet1 trips fallback, and preview confirmation.",
                "Version 1.0.146 (Build 147)" to "Only display Tesla Model Y in the active drive tracker when confirmed connected with car Bluetooth 'Tesla model Y teela'.",
                "Version 1.0.145 (Build 146)" to "Full Google Sheets backup & restore for Business Custom Purposes, plus enhanced vertical route timeline connector lines.",
                "Version 1.0.144 (Build 145)" to "New modern app icon, route timeline connector lines, and walking auto-stop tracking.",
                "Version 1.0.143 (Build 144)" to "Added vertical route timeline connector line in Trip Details.",
                "Version 1.0.142 (Build 143)" to "Aligned destination pin with map marker and added walking detection for auto-stop tracking.",
                "Version 1.0.141 (Build 142)" to "Full GPS coordinates and route polyline backup/restore to Google Sheets.",
                "Version 1.0.140 (Build 141)" to "Primary Google Sheets sync engine and Sheet1 trip restore.",
                "Version 1.0.138 (Build 139)" to "Compact Backup & Restore settings with account details on demand.",
            )
        }

        AppFloatingBottomSheet(
            onDismissRequest = { showVersionHistoryDialog = false },
            title = "Version History",
            onClose = { showVersionHistoryDialog = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(updates.size) { index ->
                        val (version, summary) = updates[index]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
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
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleSettingsIcon(icon: ImageVector) {
    Surface(shape = RoundedCornerShape(12.dp), color = SucculentPrimary.copy(alpha = 0.10f)) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = SucculentPrimary,
                modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun CompactSettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    subtitlePositive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    shape: RoundedCornerShape = AppShapes.FloatingCard,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(12.dp),
                    color = SucculentPrimary.copy(alpha = 0.10f)) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null,
                            tint = SucculentPrimary, modifier = Modifier.size(21.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
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
            content?.invoke(this)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
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
        }
        Spacer(Modifier.width(8.dp))
        SettingsToggle(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange, label = title)
    }
}

@Composable
private fun SettingsToggle(
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    label: String
) {
    Switch(
        modifier = Modifier.semantics { contentDescription = label },
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

internal fun formatScheduleSummary(days: Set<String>, start: String, end: String): String {
    val orderedDays = listOf(
        "MON" to "Mon", "TUE" to "Tue", "WED" to "Wed", "THU" to "Thu",
        "FRI" to "Fri", "SAT" to "Sat", "SUN" to "Sun"
    )
    val selected = orderedDays.filter { it.first in days }.map { it.second }
    val daySummary = when {
        selected.size == 7 -> "Every day"
        selected == listOf("Mon", "Tue", "Wed", "Thu", "Fri") -> "Mon–Fri"
        selected.isEmpty() -> "No work days"
        else -> selected.joinToString(", ")
    }
    val timePeriod = "${formatDisplayTime(start).removePrefix("0")} – ${formatDisplayTime(end).removePrefix("0")}"
    val separator = if (selected.size >= 2) "\n" else " · "
    return "$daySummary$separator$timePeriod"
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
    var pickingTimeFor by rememberSaveable { mutableStateOf<String?>(null) }

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

    Text("Work days", style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEach { (code, label) ->
            val selected = code in state.workDays
            Box(
                modifier = Modifier.weight(1f).height(48.dp)
                    .clip(RoundedCornerShape(11.dp)).clickable { onToggleDay(code) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                        .background(
                            if (selected) SucculentPrimary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            RoundedCornerShape(11.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) SucculentPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScheduleTimeField("Start", state.workHoursStart, Modifier.weight(1f)) { pickingTimeFor = "START" }
        ScheduleTimeField("End", state.workHoursEnd, Modifier.weight(1f)) { pickingTimeFor = "END" }
    }

    Text("Outside work hours", style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScheduleBehaviorOption(
            label = "Personal",
            selected = state.outsideScheduleBehavior == TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL,
            modifier = Modifier.weight(1f),
            onClick = { onBehaviorChange(TrackingPreferencesManager.BEHAVIOR_CLASSIFY_PERSONAL) }
        )
        ScheduleBehaviorOption(
            label = "Do not track",
            selected = state.outsideScheduleBehavior == TrackingPreferencesManager.BEHAVIOR_CONSERVE_BATTERY,
            modifier = Modifier.weight(1f),
            onClick = { onBehaviorChange(TrackingPreferencesManager.BEHAVIOR_CONSERVE_BATTERY) }
        )
    }
}

@Composable
private fun ScheduleTimeField(label: String, time: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDisplayTime(time).removePrefix("0"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ExpandMore, contentDescription = "Pick $label time",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ScheduleBehaviorOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) SucculentPrimary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            ).clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null, modifier = Modifier.size(18.dp),
            tint = if (selected) SucculentPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (selected) SucculentPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompactAlert(text: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        TextButton(onClick = onAction, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            email?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
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
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsManagementRow(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    onAdd: () -> Unit,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).heightIn(min = 72.dp).clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SucculentPrimary.copy(alpha = 0.10f)
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = SucculentPrimary, modifier = Modifier.size(21.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            IconButton(onClick = onAdd, modifier = Modifier.size(44.dp)) {
                Box(Modifier.size(36.dp).background(SucculentPrimary.copy(alpha = 0.10f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "Add $title", tint = SucculentPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
