package com.businesstracker.mileage.ui.settings

import androidx.compose.ui.graphics.graphicsLayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Share
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
    val vehicleDirectoryViewModel: VehicleDirectoryViewModel = hiltViewModel()
    val vehicleDirectoryState by vehicleDirectoryViewModel.state.collectAsStateWithLifecycle()
    val currentVehicle = vehicleDirectoryState.currentVehicle
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
    var showDiagnosticReportSheet by rememberSaveable { mutableStateOf(false) }
    var diagnosticReportText by remember { mutableStateOf("") }
    var showAutoTrackResumeDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDisconnectConfirmation by rememberSaveable { mutableStateOf(false) }
    var showEditVehicle by rememberSaveable { mutableStateOf(false) }
    var showVehicleManager by rememberSaveable { mutableStateOf(false) }
    var editingVehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingVehicleBluetoothAddress by rememberSaveable { mutableStateOf("") }
    var editingVehicleBluetoothName by rememberSaveable { mutableStateOf("") }
    var bluetoothPickerForVehicle by rememberSaveable { mutableStateOf(false) }
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
            .background(if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) MaterialTheme.colorScheme.background else Color(0xFFF1F4F9)),
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
            Text(
                text = "Driving & vehicle",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        VehicleSettingsIcon(Icons.Default.DirectionsCar)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { showVehicleManager = true }) {
                            Text("Vehicle", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Manage vehicle",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Manage vehicles",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        VehicleSettingsIcon(Icons.Default.Bluetooth)
                        Spacer(Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Bluetooth auto-stop",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
                    CompactSettingsDivider()
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
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (!state.autoTrackingEnabled) {
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showAutoTrackResumeDatePicker = true }
                                            .padding(vertical = 2.dp)
                                    } else {
                                        Modifier
                                    }
                                ),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Drive tracking",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            val driveTrackingSubtitle = when {
                                state.autoTrackingEnabled -> "Automatic detection"
                                !state.autoTrackingResumeDate.isNullOrBlank() -> {
                                    val formattedDate = runCatching {
                                        java.time.LocalDate.parse(state.autoTrackingResumeDate)
                                            .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
                                    }.getOrDefault(state.autoTrackingResumeDate)
                                    "Resumes on $formattedDate"
                                }
                                else -> "Tap to resume schedule"
                            }
                            Text(
                                text = driveTrackingSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (!state.autoTrackingEnabled && !state.autoTrackingResumeDate.isNullOrBlank()) {
                                    SucculentPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
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
            Text(
                text = "Trip organization",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    com.businesstracker.mileage.ui.smart.SmartClassificationCard(
                        isEnabled = state.isSmartTripRecognitionEnabled,
                        currentMode = state.smartClassificationMode,
                        onToggleEnabled = viewModel::setSmartTripRecognitionEnabled,
                        onModeSelected = viewModel::setSmartClassificationMode
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
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
            Text(
                text = "Data & synchronization",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            val timeFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
            val lastBackupTime = state.latestBackupTimestamp.takeIf { it > 0L }
            var isBackupExpanded by rememberSaveable { mutableStateOf(false) }
            var isAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
            val clipboardManager = LocalClipboardManager.current
            val isBackingUp = state.isUnifiedBackingUp
            val isRestoring = state.isLoadingRestorePreview || state.isApplyingRestore
            val backupError = state.unifiedBackupError ?: state.savedLocationsLastBackupError ?: state.sheetsLastError
            val backupCurrent = state.isAllDataBackedUp && backupError.isNullOrBlank() && !isBackingUp

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isBackupExpanded = !isBackupExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VehicleSettingsIcon(Icons.Outlined.CloudUpload)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Backup & restore", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            val syncSubtitle = if (state.isSheetsConnected) {
                                lastBackupTime?.let { "Last synced ${timeFormat.format(Date(it))}" } ?: "Connected"
                            } else {
                                "Disconnected"
                            }
                            Text(
                                text = syncSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (isBackupExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isBackupExpanded) "Collapse Backup & restore" else "Expand Backup & restore",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!backupError.isNullOrBlank()) {
                        Text(backupError, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }

                    if (isBackupExpanded) {
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
            Text(
                text = "Preferences & diagnostics",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VehicleSettingsIcon(Icons.Default.DarkMode)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Dark mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        SettingsToggle(
                            checked = state.themeMode == AppThemeMode.DARK,
                            onCheckedChange = { isDark ->
                                viewModel.setThemeMode(if (isDark) AppThemeMode.DARK else AppThemeMode.LIGHT)
                            },
                            label = "Dark mode"
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                    Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val vehicleSummaries = vehicleDirectoryState.vehicles.map { v ->
                                "${v.name}(bt=${!v.bluetoothAddress.isNullOrBlank()})"
                            }
                            diagnosticReportText = viewModel.exportDiagnosticReport(vehicleSummaries)
                            showDiagnosticReportSheet = true
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VehicleSettingsIcon(Icons.Default.Info)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Bluetooth diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Bluetooth & auto-start log",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open Bluetooth diagnostics",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • 2026-11-26",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showVehicleManager) {
        AppFloatingBottomSheet(
            onDismissRequest = { showVehicleManager = false },
            title = "Saved Vehicles",
            onClose = { showVehicleManager = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vehicleDirectoryState.vehicles.forEach { vehicle ->
                    val selected = vehicle.id == vehicleDirectoryState.currentVehicleId
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) SucculentPrimary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .clickable {
                                coroutineScope.launch {
                                    vehicleDirectoryViewModel.selectCurrentVehicle(vehicle.id)
                                    showVehicleManager = false
                                }
                            }.padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(vehicle.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                vehicle.bluetoothDeviceName?.let { "Bluetooth · $it" } ?: "No Bluetooth device linked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "Current vehicle", tint = SucculentPrimary, modifier = Modifier.size(20.dp))
                        IconButton(onClick = {
                            editingVehicleId = vehicle.id
                            vehicleNameDraft = vehicle.name
                            editingVehicleBluetoothAddress = vehicle.bluetoothAddress.orEmpty()
                            editingVehicleBluetoothName = vehicle.bluetoothDeviceName.orEmpty()
                            vehicleNameError = null
                            showVehicleManager = false
                            showEditVehicle = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit ${vehicle.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Button(
                    onClick = {
                        editingVehicleId = null
                        vehicleNameDraft = ""
                        editingVehicleBluetoothAddress = ""
                        editingVehicleBluetoothName = ""
                        vehicleNameError = null
                        showVehicleManager = false
                        showEditVehicle = true
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Vehicle")
                }
            }
        }
    }

    if (showBluetoothDevicePicker) {
        AppFloatingBottomSheet(
            onDismissRequest = {
                if (!isSavingBluetoothDevice) {
                    showBluetoothDevicePicker = false
                    if (bluetoothPickerForVehicle) showEditVehicle = true
                    bluetoothPickerForVehicle = false
                }
            },
            title = if (bluetoothPickerForVehicle) "Link Vehicle Bluetooth" else "Select Bluetooth Device",
            onClose = {
                if (!isSavingBluetoothDevice) {
                    showBluetoothDevicePicker = false
                    if (bluetoothPickerForVehicle) showEditVehicle = true
                    bluetoothPickerForVehicle = false
                }
            },
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
                        val selected = device.address.equals(
                            if (bluetoothPickerForVehicle) editingVehicleBluetoothAddress else state.vehicleBluetoothAddress,
                            ignoreCase = true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) SucculentPrimary.copy(alpha = 0.10f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable(enabled = !isSavingBluetoothDevice) {
                                    bluetoothPickerError = null
                                    if (bluetoothPickerForVehicle) {
                                        editingVehicleBluetoothAddress = device.address
                                        editingVehicleBluetoothName = device.name
                                        bluetoothPickerForVehicle = false
                                        showBluetoothDevicePicker = false
                                        showEditVehicle = true
                                    } else {
                                        isSavingBluetoothDevice = true
                                        coroutineScope.launch {
                                            val result = viewModel.selectVehicleBluetoothDevice(device)
                                            isSavingBluetoothDevice = false
                                            if (result.isSuccess) showBluetoothDevicePicker = false
                                            else bluetoothPickerError = result.exceptionOrNull()?.localizedMessage
                                                ?: "Could not save device. Try again."
                                        }
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
            title = if (editingVehicleId == null) "Add Vehicle" else "Edit Vehicle",
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
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            bluetoothPickerForVehicle = true
                            showEditVehicle = false
                            viewModel.refreshPairedBluetoothDevices()
                            showBluetoothDevicePicker = true
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = SucculentPrimary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text("Paired Bluetooth device", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(editingVehicleBluetoothName.ifBlank { "Not linked" }, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                        }
                        if (editingVehicleBluetoothAddress.isNotBlank()) {
                            TextButton(onClick = {
                                editingVehicleBluetoothAddress = ""
                                editingVehicleBluetoothName = ""
                            }) { Text("Remove") }
                        } else {
                            Icon(Icons.Default.ExpandMore, contentDescription = "Choose paired device",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
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
                                val result = vehicleDirectoryViewModel.saveVehicle(
                                    editingVehicleId,
                                    trimmedName,
                                    editingVehicleBluetoothAddress.ifBlank { null },
                                    editingVehicleBluetoothName.ifBlank { null }
                                )
                                isSavingVehicle = false
                                if (result.isSuccess) {
                                    viewModel.reconcileBluetoothMonitor()
                                    keyboardController?.hide()
                                    showEditVehicle = false
                                } else {
                                    vehicleNameError = result.exceptionOrNull()?.localizedMessage
                                        ?: "Could not save vehicle name. Try again."
                                }
                            }
                        },
                        enabled = isValidName && !isSavingVehicle && (
                            editingVehicleId == null ||
                                trimmedName != vehicleDirectoryState.vehicles.firstOrNull { it.id == editingVehicleId }?.name ||
                                editingVehicleBluetoothAddress != vehicleDirectoryState.vehicles.firstOrNull { it.id == editingVehicleId }?.bluetoothAddress.orEmpty()
                            ),
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

    if (showAutoTrackResumeDatePicker) {
        val today = remember { java.time.LocalDate.now() }
        var selectedDate by remember(state.autoTrackingResumeDate) {
            mutableStateOf(
                state.autoTrackingResumeDate?.let {
                    runCatching { java.time.LocalDate.parse(it) }.getOrNull()
                } ?: today
            )
        }
        var displayedMonth by remember(selectedDate) {
            mutableStateOf(java.time.YearMonth.from(selectedDate))
        }

        AppFloatingBottomSheet(
            onDismissRequest = { showAutoTrackResumeDatePicker = false },
            title = "Schedule Reactivation",
            onClose = { showAutoTrackResumeDatePicker = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { displayedMonth = displayedMonth.minusMonths(1) },
                        enabled = displayedMonth.isAfter(java.time.YearMonth.from(today))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous month"
                        )
                    }
                    Text(
                        text = displayedMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next month"
                        )
                    }
                }

                val weekDays = listOf("S", "M", "T", "W", "T", "F", "S")
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekDays.forEach { dayLabel ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val firstOfMonth = displayedMonth.atDay(1)
                val leadingEmpty = firstOfMonth.dayOfWeek.value % 7
                val daysInMonth = displayedMonth.lengthOfMonth()
                val totalCells = ((leadingEmpty + daysInMonth + 6) / 7) * 7

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (rowStart in 0 until totalCells step 7) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (offset in 0 until 7) {
                                val dayNum = rowStart + offset - leadingEmpty + 1
                                if (dayNum in 1..daysInMonth) {
                                    val date = displayedMonth.atDay(dayNum)
                                    val isSelectable = !date.isBefore(today)
                                    val isSelected = date == selectedDate
                                    val isToday = date == today
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isSelected -> SucculentPrimary
                                                    isToday -> SucculentPrimary.copy(alpha = 0.12f)
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .clickable(enabled = isSelectable) { selectedDate = date },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isSelected -> Color.White
                                                !isSelectable -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
                                                isToday -> SucculentPrimary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f).height(40.dp))
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!state.autoTrackingResumeDate.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.scheduleAutoTrackingResume(null)
                                showAutoTrackResumeDatePicker = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Clear")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showAutoTrackResumeDatePicker = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Cancel")
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.scheduleAutoTrackingResume(selectedDate.toString())
                            showAutoTrackResumeDatePicker = false
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
                    ) {
                        Text("Schedule", color = Color.White)
                    }
                }
            }
        }
    }

    if (showDiagnosticReportSheet) {
        val reportClipboard = LocalClipboardManager.current
        AppFloatingBottomSheet(
            onDismissRequest = { showDiagnosticReportSheet = false },
            title = "Diagnostic Report",
            onClose = { showDiagnosticReportSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Bluetooth MAC addresses and raw GPS coordinates are redacted for privacy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 300.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = diagnosticReportText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        ),
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            reportClipboard.setText(AnnotatedString(diagnosticReportText))
                            Toast.makeText(context, "Diagnostic report copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy")
                    }
                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Mileage Tracker Diagnostic Report")
                                putExtra(Intent.EXTRA_TEXT, diagnosticReportText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Diagnostic Report"))
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }
            }
        }
    }

    if (showVersionHistoryDialog) {
        val uriHandler = LocalUriHandler.current
        data class VersionEntry(
            val version: String,
            val summary: String,
            val commitHash: String? = null,
            val date: String? = null
        )
        val recentUpdates = remember {
            listOf(
                VersionEntry(
                    version = "v1.0.253",
                    summary = "Place Trip Details classification chip beside mileage and replace Additional trip information row with discreet More details link.",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.252",
                    summary = "Show realistic vehicle thumbnail in Active Drive Tracking card.",
                    commitHash = "62fd2b3",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.251",
                    summary = "Remove filter icon inline with Dashboard title.",
                    commitHash = "403a280",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.250",
                    summary = "Move idle Ready to drive card below Monthly mileage, make Start trip full-width, and fix vehicle picker tap.",
                    commitHash = "cc688e1",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.249",
                    summary = "Redesign Dashboard Ready to drive card with circular vehicle image, footer status, canvas-level total mileage, and compact breakdown card.",
                    commitHash = "12f6802",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.248",
                    summary = "Move Ready to Drive card to the top of the Dashboard.",
                    commitHash = "d7d3720",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.247",
                    summary = "Compact toggle switches and replace Vehicle edit button with chevron in Settings.",
                    commitHash = "62da123",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.246",
                    summary = "Merge Smart classification into the Trip organization card in Settings.",
                    commitHash = "241be23",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.245",
                    summary = "Group Settings into 4 unified cards (Driving & vehicle, Trip organization, Data & sync, Preferences & diagnostics) with collapsible Backup & restore.",
                    commitHash = "2ac6920",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.244",
                    summary = "Merge Driving & vehicle into a single white card in Settings.",
                    commitHash = "aaa07a9",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.243",
                    summary = "Move Dark mode into Preferences & diagnostics group in Settings.",
                    commitHash = "e11482e",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.242",
                    summary = "Redesign Settings with grouped white containers, clean outline icons, and #F1F4F9 canvas.",
                    commitHash = "802010c",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.241",
                    summary = "Redesign Trip History with grouped daily receipt containers, 3-row trip layout, and Trip Details-matched classification chips.",
                    commitHash = "4b2ea15",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.240",
                    summary = "Final visual polish on Trip Details: 20% smaller status chip, 44dp uncropped vehicle button, grouped route & additional info card, and lighter typography.",
                    commitHash = "fcbc474",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.239",
                    summary = "Group classification chip closer to vehicle button and zoom in vehicle thumbnail.",
                    commitHash = "58c42dd",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.238",
                    summary = "Add realistic white Tesla Model Y and red Tesla Model 3 icons to Trip Details vehicle button.",
                    commitHash = "a1e6304",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.237",
                    summary = "Compact Digital Trip Receipt: 2-row summary card with single classification chip, circular vehicle button, and inline duration/deduction strip.",
                    commitHash = "8d0030e",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.236",
                    summary = "Refactor Trip Details with modern fintech & mobility card system, desaturated map style, segmented classification toggle, and tonal metric tiles.",
                    commitHash = "383dcbe",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.235",
                    summary = "Move vehicle name underneath the Personal/Business classification button on Trip Details.",
                    commitHash = "42631e6",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.234",
                    summary = "Redesign Trip Details as a digital trip receipt with header above map, primary distance, inline duration/deduction, and expandable additional info.",
                    commitHash = "60f2165",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.233",
                    summary = "Move Route / More info tabs above the metric strip on Trip Details (Option A).",
                    commitHash = "049c498",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.232",
                    summary = "Adjust Trip Details metric strip font and spacing so duration never truncates.",
                    commitHash = "662dc97",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.231",
                    summary = "Use last moving waypoint for finalized trip end time instead of parked GPS drift.",
                    commitHash = "2fa691c",
                    date = "Nov 27, 2026"
                ),
                VersionEntry(
                    version = "v1.0.230",
                    summary = "Rounded Trip Details map with matching padding and adjusted close/expand buttons.",
                    commitHash = "52a77db",
                    date = "Nov 26, 2026"
                ),
                VersionEntry(
                    version = "v1.0.229",
                    summary = "Use Tune filter icon in Trip History.",
                    commitHash = "cd0c242",
                    date = "Nov 26, 2026"
                ),
                VersionEntry(
                    version = "v1.0.228",
                    summary = "Remove Only missing purpose toggle and lock inner scroll in Filter trips sheet.",
                    commitHash = "d88725f",
                    date = "Nov 26, 2026"
                ),
                VersionEntry(
                    version = "v1.0.227",
                    summary = "Move Create report button underneath Mileage Report card with full-width layout.",
                    date = "Nov 26, 2026"
                ),
                VersionEntry(
                    version = "v1.0.226",
                    summary = "Add outer padding and matching 20dp rounded bottom corners to Trip details card.",
                    commitHash = "5fb9c37",
                    date = "Nov 26, 2026"
                ),
                VersionEntry(
                    version = "v1.0.225",
                    summary = "Remove top-right month dropdown pill from Dashboard header.",
                    commitHash = "d8b2ce4",
                    date = "Nov 26, 2026"
                ),
                VersionEntry(
                    version = "v1.0.224",
                    summary = "Clean up Dashboard filter sheet: full-width year selector with arrows, remove Period header and Last 3 months chip, and fix preset row spacing.",
                    commitHash = "385f807",
                    date = "Nov 26, 2026"
                ),
                VersionEntry(
                    version = "v1.0.223",
                    summary = "Fix crash when tapping Create Report by formatting selectedYear as String instead of %04d.",
                    commitHash = "8a070e6",
                    date = "Nov 26, 2026"
                ),
                VersionEntry(
                    version = "v1.0.222",
                    summary = "Formatted Version History with clickable GitHub commit links and release dates, plus automated GitHub Release APK publishing.",
                    commitHash = "71cd710",
                    date = "Nov 26, 2026"
                ),
                VersionEntry(
                    version = "v1.0.221",
                    summary = "Rework Dashboard filter button into a floating filter sheet with multi-month, Today, and This Week selection.",
                    commitHash = "d76ddcd",
                    date = "Nov 26, 2026"
                )
            )
        }
        val legacyUpdates = remember {
            listOf(
                "Version 1.0.214 (Build 214)" to "Rename the Bluetooth diagnostics description to Bluetooth & auto-start log.",
                "Version 1.0.213 (Build 213)" to "Move the Settings version label below all Settings sections.",
                "Version 1.0.212 (Build 212)" to "Move Bluetooth diagnostics to the bottom of Settings.",
                "Version 1.0.211 (Build 211)" to "Change the Vehicle card subtitle to Manage vehicle.",
                "Version 1.0.210 (Build 210)" to "Update the Drive tracking off-state description to Tap to resume schedule.",
                "Version 1.0.209 (Build 209)" to "Fix background auto-start ForegroundServiceStartNotAllowedException (LaunchFailedReconciled) with automatic background GPS fallback & FGS promotion, fix 138ms GPS freshness clock-skew false rejection, preserve origin anchor on slow departures, and add detailed candidate launch diagnostics.",
                "Version 1.0.208 (Build 208)" to "Match scheduled auto-tracking reactivation calendar table to floating bottom sheet design.",
                "Version 1.0.207 (Build 207)" to "Add scheduled auto-tracking reactivation date picker and status under Drive tracking.",
                "Version 1.0.206 (Build 206)" to "Full GPS auto-start & multi-vehicle Bluetooth audit: separate drive detection from vehicle matching, prevent competing vehicle events from disarming tracking, prioritize active A2DP/HEADSET audio profiles, recover vehicle match after disconnect or 200m confirmation, add tap-to-pick active vehicle on Dashboard, and add redacted diagnostic report export in Settings.",
                "Version 1.0.205 (Build 205)" to "Reflect true trip end time using the timestamp of the last recorded movement/waypoint, eliminating the debounce delay from recorded trip duration.",
                "Version 1.0.204 (Build 204)" to "Optimize Trip History filter pills: remove trailing chevrons and enable smooth horizontal scrolling to prevent text truncation.",
                "Version 1.0.203 (Build 203)" to "Redesign Trip History search & filter UI: clean white pill filters with outline funnel toggle.",
                "Version 1.0.202 (Build 202)" to "Add 4-filter compact bar with Vehicle filter bottom sheet in Trip History.",
                "Version 1.0.201 (Build 201)" to "Change tracking notification title to Active tracking.",
                "Version 1.0.200 (Build 200)" to "Apply Bluetooth auto-stop to all connected vehicles without requiring a specific car selection.",
                "Version 1.0.199 (Build 199)" to "Fix Bluetooth vehicle identification using profile proxies and event-driven recognition.",
                "Version 1.0.198 (Build 198)" to "Remove excess vertical spacing from the Vehicle row.",
                "Version 1.0.197 (Build 197)" to "Tighten Vehicle row and separate average and maximum speed.",
                "Version 1.0.196 (Build 196)" to "Refine Trip Details More info alignment and spacing.",
                "Version 1.0.195 (Build 195)" to "Increase Trip Details More info text size and separate speed values.",
                "Version 1.0.194 (Build 194)" to "Compact Trip Details More info and show trip purpose inline.",
                "Version 1.0.193 (Build 193)" to "Show vehicle details inside the Trip Details More info card.",
                "Version 1.0.192 (Build 192)" to "Show updated friendly vehicle names in active drives and trip details.",
                "Version 1.0.191 (Build 191)" to "Identify vehicles from paired Bluetooth devices and show vehicle details on trips.",
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
        val updates = remember(recentUpdates, legacyUpdates) {
            recentUpdates + legacyUpdates.map { (ver, sum) ->
                val shortVer = ver.removePrefix("Version ").substringBefore(" (")
                VersionEntry(version = "v$shortVer", summary = sum)
            }
        }

        AppFloatingBottomSheet(
            onDismissRequest = { showVersionHistoryDialog = false },
            title = "Version History",
            onClose = { showVersionHistoryDialog = false }
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                ) {
                    items(updates.size) { index ->
                        val entry = updates[index]
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = entry.version,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (entry.commitHash != null) {
                                        Text(
                                            text = entry.commitHash,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = Color(0xFF1D6FA5),
                                            modifier = Modifier.clickable {
                                                uriHandler.openUri(
                                                    "https://github.com/stagius24-cell/mileage-tracker/commit/${entry.commitHash}"
                                                )
                                            }
                                        )
                                    }
                                }
                                if (entry.date != null) {
                                    Text(
                                        text = entry.date,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = entry.summary,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (index < updates.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleSettingsIcon(icon: ImageVector) {
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Icon(
            icon,
            contentDescription = null,
            tint = SucculentPrimary,
            modifier = Modifier.size(21.dp)
        )
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
        modifier = Modifier
            .graphicsLayer(scaleX = 0.85f, scaleY = 0.85f)
            .semantics { contentDescription = label },
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
