package com.businesstracker.mileage.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = SucculentPrimary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = getIconForLocation(location.icon),
                        contentDescription = null,
                        tint = SucculentPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = location.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${location.radiusMeters}m",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = location.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!location.classificationPreference.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    val isBiz = location.classificationPreference == "BUSINESS"
                    val badgeColor = if (isBiz) SucculentPrimary else Color(0xFF2563EB)
                    val label = if (isBiz) {
                        "Suggest: Business" + if (!location.businessEntity.isNullOrBlank()) " (${location.businessEntity})" else ""
                    } else {
                        "Suggest: Personal"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Edit saved location ${location.name}"
                    }
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = SucculentPrimary, modifier = Modifier.size(20.dp))
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Delete saved location ${location.name}"
                    }
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = SpeedRed, modifier = Modifier.size(20.dp))
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

    var name by remember { mutableStateOf(initialLocation?.name ?: "") }
    var address by remember { mutableStateOf(initialLocation?.address ?: prefillAddress ?: "") }
    var latitude by remember { mutableStateOf(initialLocation?.latitude ?: prefillLat ?: 0.0) }
    var longitude by remember { mutableStateOf(initialLocation?.longitude ?: prefillLng ?: 0.0) }
    var radiusMeters by remember { mutableFloatStateOf((initialLocation?.radiusMeters ?: 150).toFloat()) }
    var selectedIcon by remember { mutableStateOf(initialLocation?.icon ?: "place") }
    var classificationPref by remember { mutableStateOf(initialLocation?.classificationPreference ?: "NONE") }
    var businessEntity by remember { mutableStateOf(initialLocation?.businessEntity ?: BusinessEntity.MY_SUCCULENT_CORNER.displayName) }

    // Search Mode State
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchAddressResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // GPS fetch state
    var isLocating by remember { mutableStateOf(false) }

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
                        if (name.isBlank()) name = "Current Location"
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

    Dialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialLocation != null) "Edit Saved Location" else "Add Saved Location",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Close dialog"
                            }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Quick Action Buttons: Search Address & Current Location
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
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
                                                if (name.isBlank()) name = "Current Location"
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
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isLocating) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Use GPS", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    // Search address bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchJob?.cancel()
                            isSearching = false
                            searchQuery = it
                            searchResults = emptyList()
                            searchError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search Address") },
                        placeholder = { Text("e.g. 123 Main St, San Jose, CA") },
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(
                                    onClick = {
                                        if (searchQuery.isNotBlank()) {
                                            searchJob?.cancel()
                                            val requestedQuery = searchQuery.trim()
                                            searchJob = coroutineScope.launch {
                                                isSearching = true
                                                try {
                                                    val results = onSearchAddress(requestedQuery)
                                                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                                    if (searchQuery.trim() != requestedQuery) return@launch
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
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = "Search address query"
                                        }
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (searchError != null) {
                        Text(
                            text = searchError!!,
                            color = SpeedRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (searchResults.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Select result:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                searchResults.forEach { result ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .clickable {
                                                address = result.formattedAddress
                                                latitude = result.latitude
                                                longitude = result.longitude
                                                if (name.isBlank()) {
                                                    name = "Saved Location"
                                                }
                                                searchResults = emptyList()
                                                searchQuery = ""
                                            }
                                            .padding(vertical = 8.dp, horizontal = 6.dp)
                                            .semantics {
                                                role = Role.Button
                                                contentDescription = "Select address ${result.formattedAddress}"
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Place, contentDescription = null, tint = SucculentPrimary, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(result.formattedAddress, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                }
                            }
                        }
                    }

                    // Name Field
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            formError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Location Name *") },
                        placeholder = { Text("e.g. Home, Succulent Nursery, Warehouse") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Address Field (resolved)
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Address *") },
                        placeholder = { Text("123 Example Street, San Jose, CA") },
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (latitude != 0.0 || longitude != 0.0) {
                        Text(
                            text = "Coordinates: ${String.format(java.util.Locale.US, "%.5f", latitude)}, ${String.format(java.util.Locale.US, "%.5f", longitude)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Icon Picker
                    Text("Location Icon", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(AVAILABLE_LOCATION_ICONS) { (key, label) ->
                            FilterChip(
                                selected = selectedIcon.equals(key, ignoreCase = true),
                                onClick = { selectedIcon = key },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                leadingIcon = {
                                    Icon(getIconForLocation(key), contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SucculentPrimary,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                )
                            )
                        }
                    }

                    // Matching Radius Slider (50m - 500m)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Matching Radius", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("${radiusMeters.toInt()} meters", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SucculentPrimary)
                        }
                        Slider(
                            value = radiusMeters,
                            onValueChange = { radiusMeters = it },
                            valueRange = 50f..500f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = SucculentPrimary,
                                activeTrackColor = SucculentPrimary
                            )
                        )
                    }

                    // Classification Preference
                    Text("Classification Preference", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("NONE" to "None", "BUSINESS" to "Business", "PERSONAL" to "Personal").forEach { (pref, label) ->
                            FilterChip(
                                selected = classificationPref == pref,
                                onClick = { classificationPref = pref },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SucculentPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    if (classificationPref == "BUSINESS") {
                        Text("Business Entity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BusinessEntity.entries.forEach { entity ->
                                FilterChip(
                                    selected = businessEntity == entity.displayName,
                                    onClick = { businessEntity = entity.displayName },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                    label = { Text(entity.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SucculentPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    if (formError != null) {
                        Text(
                            text = formError!!,
                            color = SpeedRed,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (isSaving) return@Button
                            if (name.isBlank()) {
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
                                name.trim(),
                                latitude,
                                longitude,
                                address.ifBlank { name.trim() },
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
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Saving...", color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Save Location", color = Color.White, fontWeight = FontWeight.Bold)
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
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Restore from Google Sheets", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        if (state.localCount > 0) {
                            Text(
                                text = "Local records (${state.localCount}) will NOT be erased.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SucculentPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
                    ) {
                        Text("OK")
                    }
                }
            )
        }
        is SavedLocationsRestorePreviewUi.Failure -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Restore Unavailable", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Your local Saved Locations were not modified.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
                    ) {
                        Text("Close")
                    }
                }
            )
        }
        is SavedLocationsRestorePreviewUi.Loaded -> {
            val preview = state.preview
            val conflictCandidates = preview.candidates.filter { it.requiresOverwriteApproval }
            val allConflictsApproved = conflictCandidates.isNotEmpty() &&
                conflictCandidates.all { it.cloudLocation.id in state.approvedConflictIds }
            val selectedCount = state.selectedIds.size

            Dialog(
                onDismissRequest = { if (!isApplying) onDismiss() },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Preview Restore from Sheets",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Choose which locations to import. Existing local changes stay protected unless you check them to overwrite.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

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
                                .heightIn(max = 320.dp),
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
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss, enabled = !isApplying) {
                                Text("Cancel")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = onConfirmApply,
                                enabled = !isApplying && selectedCount > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isApplying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Apply ($selectedCount)", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
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
