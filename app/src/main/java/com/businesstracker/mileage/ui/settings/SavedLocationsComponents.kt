package com.businesstracker.mileage.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import kotlin.math.roundToInt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import com.businesstracker.mileage.data.remote.RestoreCandidate
import com.businesstracker.mileage.data.remote.RestoreItemStatus
import com.businesstracker.mileage.domain.model.BusinessEntity
import com.businesstracker.mileage.domain.usecase.SearchAddressResult
import com.businesstracker.mileage.domain.usecase.CurrentLocationResult
import com.businesstracker.mileage.ui.theme.SlateNeutral
import com.businesstracker.mileage.ui.theme.SpeedRed
import com.businesstracker.mileage.ui.theme.AppDimensions
import com.businesstracker.mileage.ui.theme.AppShapes
import com.businesstracker.mileage.ui.theme.AppSpacing
import com.businesstracker.mileage.ui.components.AppConfirmationDialog
import com.businesstracker.mileage.ui.components.AppFloatingBottomSheet
import com.businesstracker.mileage.ui.components.AppPrimaryButton
import com.businesstracker.mileage.ui.components.AppSecondaryButton
import com.businesstracker.mileage.ui.theme.SucculentPrimary
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

fun getIconForLocation(iconKey: String): ImageVector {
    return when (iconKey.lowercase()) {
        "home" -> Icons.Default.Home
        "nursery", "plant" -> Icons.Default.LocalFlorist
        "supplier", "store" -> Icons.Default.Store
        "warehouse" -> Icons.Default.Warehouse
        "office", "work" -> Icons.Default.Work
        "school" -> Icons.Default.School
        else -> Icons.Default.Place
    }
}

val AVAILABLE_LOCATION_ICONS = listOf(
    "place" to "Default",
    "home" to "Home",
    "nursery" to "Nursery",
    "store" to "Store",
    "warehouse" to "Warehouse",
    "office" to "Office",
    "school" to "School"
)

@Composable
fun SavedLocationItem(
    location: SavedLocationEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val iconBg = if (isDark) SucculentPrimary.copy(alpha = 0.16f) else Color(0xFFEDF5F0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getIconForLocation(location.icon),
                contentDescription = null,
                tint = SucculentPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = location.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = location.address,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            if (!location.classificationPreference.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                val isBiz = location.classificationPreference == "BUSINESS"
                val chipBg = if (isBiz) {
                    if (isDark) SucculentPrimary.copy(alpha = 0.18f) else Color(0xFFEDF5F0)
                } else {
                    if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.35f) else Color(0xFFEFF6FF)
                }
                val chipText = if (isBiz) {
                    if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D)
                } else {
                    if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(chipBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isBiz) "Business" else "Personal",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = chipText
                        )
                    }
                    if (isBiz && !location.businessEntity.isNullOrBlank()) {
                        Text(
                            text = location.businessEntity,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .size(32.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "More options for ${location.name}"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit location") },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete location", color = SpeedRed) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
fun SavedLocationDialog(
    initialLocation: SavedLocationEntity? = null,
    prefillAddress: String? = null,
    prefillLat: Double? = null,
    prefillLng: Double? = null,
    onDismiss: () -> Unit,
    onSearchAddress: suspend (String) -> List<SearchAddressResult>,
    onGetCurrentLocation: suspend () -> CurrentLocationResult?,
    onSave: (
        id: String?,
        name: String,
        latitude: Double,
        longitude: Double,
        address: String,
        radiusMeters: Int,
        icon: String,
        classificationPreference: String?,
        businessEntity: String?,
        onComplete: (error: String?) -> Unit
    ) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val scrimColor = if (isDark) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.45f)
    val sageBg = if (isDark) SucculentPrimary.copy(alpha = 0.16f) else Color(0xFFEDF5F0)
    val neutralTileBg = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else Color(0xFFF8FAFC)
    val mutedBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    // Do not populate new saved locations with auto-generated names like "Start Location" / "End Location"
    val initialNameText = remember(initialLocation) {
        val raw = initialLocation?.name.orEmpty()
        if (initialLocation == null && (raw == "Start Location" || raw == "End Location" || raw == "Current Location" || raw == "Saved Location")) {
            ""
        } else {
            raw
        }
    }
    var nameValue by remember { mutableStateOf(TextFieldValue(initialNameText)) }
    var hasSelectedNameOnFirstFocus by remember { mutableStateOf(false) }

    var address by remember { mutableStateOf(initialLocation?.address ?: prefillAddress ?: "") }
    var latitude by remember { mutableStateOf(initialLocation?.latitude ?: prefillLat ?: 0.0) }
    var longitude by remember { mutableStateOf(initialLocation?.longitude ?: prefillLng ?: 0.0) }
    var radiusMeters by remember { mutableFloatStateOf((initialLocation?.radiusMeters ?: 150).toFloat()) }
    var selectedIcon by remember { mutableStateOf(initialLocation?.icon ?: "place") }
    var classificationPref by remember { mutableStateOf(initialLocation?.classificationPreference ?: "NONE") }
    var businessEntity by remember { mutableStateOf(initialLocation?.businessEntity ?: BusinessEntity.MY_SUCCULENT_CORNER.displayName) }

    // Search & GPS state
    var searchResults by remember { mutableStateOf<List<SearchAddressResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isLocating by remember { mutableStateOf(false) }
    var showLocationDetails by remember { mutableStateOf(false) }

    fun triggerAddressSearch() {
        val requestedQuery = address.trim()
        if (requestedQuery.isBlank()) return
        searchJob?.cancel()
        searchError = null
        searchJob = coroutineScope.launch {
            isSearching = true
            try {
                val results = onSearchAddress(requestedQuery)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (address.trim() != requestedQuery) return@launch
                searchResults = results
                if (results.isEmpty()) {
                    searchError = "No locations found"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                searchError = "Search unavailable: ${e.localizedMessage ?: "network error"}"
            } finally {
                isSearching = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            coroutineScope.launch {
                isLocating = true
                try {
                    val res = onGetCurrentLocation()
                    if (res != null) {
                        latitude = res.latitude
                        longitude = res.longitude
                        address = res.formattedAddress ?: address
                        searchResults = emptyList()
                        searchError = null
                    } else {
                        formError = "Unable to determine current location"
                    }
                } catch (e: Exception) {
                    formError = e.localizedMessage ?: "Location lookup failed"
                } finally {
                    isLocating = false
                }
            }
        } else {
            formError = "Location permission is required to use current location"
        }
    }

    val maxSheetHeight = (configuration.screenHeightDp * 0.85f).dp
    val dismissThresholdPx = with(density) { 80.dp.toPx() }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
                .navigationBarsPadding()
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!isSaving) onDismiss() }
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp)
                    .heightIn(max = maxSheetHeight)
                    .offset { IntOffset(0, dragOffsetPx.roundToInt().coerceAtLeast(0)) }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // 1. Draggable top handle (only region that drags/dismisses the sheet)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(isSaving) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (!isSaving && dragOffsetPx >= dismissThresholdPx) {
                                            onDismiss()
                                        } else {
                                            dragOffsetPx = 0f
                                        }
                                    },
                                    onDragCancel = { dragOffsetPx = 0f },
                                    onVerticalDrag = { _, dragAmount ->
                                        if (!isSaving) {
                                            dragOffsetPx = (dragOffsetPx + dragAmount).coerceAtLeast(0f)
                                        }
                                    }
                                )
                            }
                            .padding(top = 10.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }

                    // 2. Fixed compact header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (initialLocation != null) "Edit saved location" else "Add saved location",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { if (!isSaving) onDismiss() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 3. Independently scrollable form content
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Location name (compact 44dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Location name",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, mutedBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (nameValue.text.isEmpty()) {
                                        Text(
                                            text = "Enter location name",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = nameValue,
                                        onValueChange = {
                                            nameValue = it
                                            formError = null
                                        },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(SucculentPrimary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { focusState ->
                                                if (focusState.isFocused && !hasSelectedNameOnFirstFocus && nameValue.text.isNotEmpty()) {
                                                    hasSelectedNameOnFirstFocus = true
                                                    nameValue = nameValue.copy(selection = TextRange(0, nameValue.text.length))
                                                }
                                            }
                                    )
                                }
                            }
                        }

                        // Combined searchable, editable Address + compact locate-fixed button (44dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Address",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, mutedBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(start = 10.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (address.isEmpty()) {
                                                Text(
                                                    text = "Search or enter address",
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            BasicTextField(
                                                value = address,
                                                onValueChange = {
                                                    searchJob?.cancel()
                                                    isSearching = false
                                                    address = it
                                                    searchResults = emptyList()
                                                    searchError = null
                                                    formError = null
                                                },
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                ),
                                                cursorBrush = SolidColor(SucculentPrimary),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                                keyboardActions = KeyboardActions(onSearch = { triggerAddressSearch() }),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        if (isSearching) {
                                            CircularProgressIndicator(
                                                modifier = Modifier
                                                    .padding(horizontal = 8.dp)
                                                    .size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            IconButton(
                                                onClick = { triggerAddressSearch() },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .semantics {
                                                        role = Role.Button
                                                        contentDescription = "Search address"
                                                    }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Surface(
                                    onClick = {
                                        if (hasLocationPermission(context)) {
                                            coroutineScope.launch {
                                                isLocating = true
                                                try {
                                                    val res = onGetCurrentLocation()
                                                    if (res != null) {
                                                        latitude = res.latitude
                                                        longitude = res.longitude
                                                        address = res.formattedAddress ?: address
                                                        searchResults = emptyList()
                                                        searchError = null
                                                    } else {
                                                        formError = "Unable to determine current location"
                                                    }
                                                } catch (e: Exception) {
                                                    formError = e.localizedMessage ?: "Location lookup failed"
                                                } finally {
                                                    isLocating = false
                                                }
                                            }
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                        }
                                    },
                                    enabled = !isLocating && !isSaving,
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, mutedBorder),
                                    modifier = Modifier
                                        .size(44.dp)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = "Use current GPS location"
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isLocating) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.MyLocation,
                                                contentDescription = null,
                                                tint = SucculentPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (searchError != null) {
                            Text(
                                text = searchError!!,
                                color = SpeedRed,
                                fontSize = 12.sp
                            )
                        }

                        if (searchResults.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = neutralTileBg,
                                border = BorderStroke(1.dp, mutedBorder)
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    searchResults.forEachIndexed { idx, result ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    address = result.formattedAddress
                                                    latitude = result.latitude
                                                    longitude = result.longitude
                                                    searchResults = emptyList()
                                                    searchError = null
                                                }
                                                .padding(vertical = 8.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Place, contentDescription = null, tint = SucculentPrimary, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = result.formattedAddress,
                                                fontSize = 13.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (idx < searchResults.lastIndex) {
                                            HorizontalDivider(color = mutedBorder.copy(alpha = 0.4f))
                                        }
                                    }
                                }
                            }
                        }

                        // Location icon selector
                        var iconMenuExpanded by remember { mutableStateOf(false) }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Location icon",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Box {
                                    Row(
                                        modifier = Modifier
                                            .clickable { iconMenuExpanded = true }
                                            .padding(vertical = 4.dp, horizontal = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "All icons",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SucculentPrimary
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = SucculentPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = iconMenuExpanded,
                                        onDismissRequest = { iconMenuExpanded = false }
                                    ) {
                                        AVAILABLE_LOCATION_ICONS.forEach { (key, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                leadingIcon = { Icon(getIconForLocation(key), contentDescription = null) },
                                                onClick = {
                                                    selectedIcon = key
                                                    iconMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            val baseIcons = listOf("place", "home", "nursery", "work")
                            val visibleIcons = if (selectedIcon in baseIcons) {
                                baseIcons
                            } else {
                                listOf("place", "home", "nursery", selectedIcon)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                visibleIcons.forEach { key ->
                                    val isSelected = selectedIcon == key
                                    val label = AVAILABLE_LOCATION_ICONS.firstOrNull { it.first == key }?.second ?: key
                                    Surface(
                                        onClick = { selectedIcon = key },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) sageBg else neutralTileBg,
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) SucculentPrimary else mutedBorder.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 9.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = getIconForLocation(key),
                                                contentDescription = label,
                                                tint = if (isSelected) SucculentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Matching radius
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val feetValue = (radiusMeters * 3.28084).roundToInt()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Matching radius",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$feetValue ft",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SucculentPrimary
                                )
                            }
                            Slider(
                                value = radiusMeters,
                                onValueChange = { radiusMeters = it },
                                valueRange = 50f..500f,
                                steps = 8,
                                thumb = {
                                    Surface(
                                        modifier = Modifier.size(18.dp),
                                        shape = CircleShape,
                                        color = Color.White,
                                        border = BorderStroke(2.dp, SucculentPrimary),
                                        shadowElevation = 2.dp
                                    ) {}
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = SucculentPrimary,
                                    inactiveTrackColor = mutedBorder.copy(alpha = 0.5f)
                                ),
                                track = { sliderState ->
                                    SliderDefaults.Track(
                                        sliderState = sliderState,
                                        modifier = Modifier.height(4.dp)
                                    )
                                }
                            )
                        }

                        // Trip classification
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Trip classification",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("NONE" to "None", "BUSINESS" to "Business", "PERSONAL" to "Personal").forEach { (key, label) ->
                                    val isSelected = classificationPref == key
                                    Surface(
                                        onClick = { classificationPref = key },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) sageBg else neutralTileBg,
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) SucculentPrimary else mutedBorder.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (isSelected) SucculentPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (classificationPref == "BUSINESS") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BusinessEntity.entries.forEach { entity ->
                                    val isSelected = businessEntity == entity.displayName
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { businessEntity = entity.displayName },
                                        modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                                        label = { Text(entity.displayName, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = sageBg,
                                            selectedLabelColor = SucculentPrimary
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            selectedBorderColor = SucculentPrimary
                                        )
                                    )
                                }
                            }
                        }

                        // Collapsed Location details (raw GPS coordinates)
                        if (latitude != 0.0 || longitude != 0.0) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clickable { showLocationDetails = !showLocationDetails }
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Location details",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = if (showLocationDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                AnimatedVisibility(visible = showLocationDetails) {
                                    Text(
                                        text = "Coordinates: ${String.format(java.util.Locale.US, "%.5f", latitude)}, ${String.format(java.util.Locale.US, "%.5f", longitude)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        if (formError != null) {
                            Text(
                                text = formError!!,
                                color = SpeedRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // 4. Fixed Save location footer
                    HorizontalDivider(color = mutedBorder.copy(alpha = 0.4f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isSaving) return@Button
                                val trimmedName = nameValue.text.trim()
                                if (trimmedName.isBlank()) {
                                    formError = "Please enter a location name"
                                    return@Button
                                }
                                if (latitude == 0.0 && longitude == 0.0) {
                                    formError = "Please select an address or use GPS coordinates"
                                    return@Button
                                }
                                isSaving = true
                                formError = null
                                onSave(
                                    initialLocation?.id,
                                    trimmedName,
                                    latitude,
                                    longitude,
                                    address.ifBlank { trimmedName },
                                    radiusMeters.toInt(),
                                    selectedIcon,
                                    if (classificationPref == "NONE") null else classificationPref,
                                    if (classificationPref == "BUSINESS") businessEntity else null
                                ) { error ->
                                    isSaving = false
                                    if (error != null) {
                                        formError = error
                                    }
                                }
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = "Save location",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun hasLocationPermission(context: android.content.Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun SavedLocationsRestorePreviewDialog(
    state: SavedLocationsRestorePreviewUi,
    isApplying: Boolean,
    onToggleCandidate: (String) -> Unit,
    onSetOverwriteConflicts: (Boolean) -> Unit,
    onConfirmApply: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is SavedLocationsRestorePreviewUi.Empty -> {
            AppFloatingBottomSheet(
                onDismissRequest = onDismiss,
                title = "Restore from Google Sheets",
                onClose = onDismiss
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (state.localCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SucculentPrimary.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Local records (${state.localCount}) will NOT be erased and remain up to date.",
                                style = MaterialTheme.typography.labelSmall,
                                color = SucculentPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                    AppPrimaryButton(
                        text = "OK",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        is SavedLocationsRestorePreviewUi.Failure -> {
            AppFloatingBottomSheet(
                onDismissRequest = onDismiss,
                title = "Restore Unavailable",
                onClose = onDismiss
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Your local records were not modified.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppPrimaryButton(
                        text = "Close",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        is SavedLocationsRestorePreviewUi.Loaded -> {
            val preview = state.preview
            val conflictCandidates = preview.candidates.filter { it.requiresOverwriteApproval }
            val allConflictsApproved = conflictCandidates.isNotEmpty() &&
                conflictCandidates.all { it.cloudLocation.id in state.approvedConflictIds }
            val selectedCount = state.selectedIds.size

            AppFloatingBottomSheet(
                onDismissRequest = { if (!isApplying) onDismiss() },
                title = "Preview Restore from Sheets",
                onClose = { onDismiss() },
                isDismissible = !isApplying
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = "Choose which locations to import. Existing local changes stay protected unless you check them to overwrite.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SucculentPrimary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val tripText = if (state.tripsRestoredCount > 0) {
                                "✓ Restored ${state.tripsRestoredCount} trip(s) from Sheet1 into your history."
                            } else {
                                "Trips on Sheet1 are already synced locally."
                            }
                            Text(
                                text = tripText,
                                style = MaterialTheme.typography.labelSmall,
                                color = SucculentPrimary,
                                fontWeight = FontWeight.SemiBold
                            )

                            val purpText = if (state.purposesRestoredCount > 0) {
                                "✓ Restored ${state.purposesRestoredCount} custom business purpose(s)."
                            } else {
                                "Custom business purposes are already up to date."
                            }
                            Text(
                                text = purpText,
                                style = MaterialTheme.typography.labelSmall,
                                color = SucculentPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    if (preview.localOnlyLocations.isNotEmpty()) {
                        Text(
                            text = "${preview.localOnlyLocations.size} local-only location(s) will remain untouched.",
                            style = MaterialTheme.typography.labelMedium,
                            color = SucculentPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    if (conflictCandidates.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFF8E1),
                            border = BorderStroke(1.dp, Color(0xFFFFD54F)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSetOverwriteConflicts(!allConflictsApproved) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = allConflictsApproved,
                                    onCheckedChange = onSetOverwriteConflicts
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Overwrite all ${conflictCandidates.size} local conflict/deleted item(s)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF5D4037)
                                    )
                                    Text(
                                        text = "Leave unchecked to preserve your local versions.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF795548)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(preview.candidates, key = { it.cloudLocation.id }) { candidate ->
                            RestoreCandidateRow(
                                candidate = candidate,
                                isSelected = candidate.cloudLocation.id in state.selectedIds,
                                onToggle = { onToggleCandidate(candidate.cloudLocation.id) }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppSecondaryButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            enabled = !isApplying,
                            modifier = Modifier.weight(1f)
                        )
                        AppPrimaryButton(
                            text = if (isApplying) "Applying..." else "Apply ($selectedCount)",
                            onClick = onConfirmApply,
                            enabled = !isApplying && selectedCount > 0,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RestoreCandidateRow(
    candidate: RestoreCandidate,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val loc = candidate.cloudLocation
    val isIdentical = candidate.status == RestoreItemStatus.IDENTICAL
    val statusLabel = when (candidate.status) {
        RestoreItemStatus.NEW_FROM_CLOUD -> "New from cloud"
        RestoreItemStatus.IDENTICAL -> "Already matches local"
        RestoreItemStatus.CLOUD_NEWER -> "Cloud newer — check to overwrite local"
        RestoreItemStatus.LOCAL_NEWER -> "Local newer — check to overwrite local"
        RestoreItemStatus.CONFLICT_MODIFIED -> "Modified — check to overwrite local"
        RestoreItemStatus.PREVIOUSLY_DELETED_LOCALLY -> "Deleted locally — check to restore"
    }
    val statusColor = when (candidate.status) {
        RestoreItemStatus.NEW_FROM_CLOUD -> SucculentPrimary
        RestoreItemStatus.IDENTICAL -> SlateNeutral
        RestoreItemStatus.CLOUD_NEWER,
        RestoreItemStatus.LOCAL_NEWER,
        RestoreItemStatus.CONFLICT_MODIFIED,
        RestoreItemStatus.PREVIOUSLY_DELETED_LOCALLY -> Color(0xFFE65100)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isIdentical) Modifier.clickable(onClick = onToggle) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = if (isIdentical) false else isSelected,
                enabled = !isIdentical,
                onCheckedChange = { if (!isIdentical) onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = SucculentPrimary)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = loc.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (loc.address.isNotBlank()) {
                    Text(
                        text = loc.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SettingsSegmentedOptions(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    iconFor: ((String) -> ImageVector)? = null
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            val active = selected.equals(key, ignoreCase = true)
            Surface(
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp)).clickable { onSelect(key) },
                shape = RoundedCornerShape(12.dp),
                color = if (active) SucculentPrimary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Row(Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    iconFor?.let { Icon(it(key), contentDescription = null, modifier = Modifier.size(16.dp)) }
                    if (iconFor != null) Spacer(Modifier.width(4.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
