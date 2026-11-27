package com.businesstracker.mileage.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.businesstracker.mileage.data.local.entity.VehicleEntity
import com.businesstracker.mileage.domain.model.TimeFilter
import com.businesstracker.mileage.ui.theme.AppShapes
import com.businesstracker.mileage.ui.theme.SucculentPrimary
import java.time.LocalDate

/**
 * Reusable floating modal bottom sheet for filtering trips (or reports/dashboard).
 *
 * Features:
 * - Fixed header ("Filter trips", Reset, X) and stationary footer (Clear, Apply filters).
 * - Scrollable content area capped at ~90% of usable screen height.
 * - Calendar-style 4x3 month/year picker with multi-select (consecutive or non-consecutive)
 *   and quick presets (YTD, Last 3 months, All year).
 * - Optional Vehicle multi-select chips, Trip type multi-select chips, and
 *   "Only missing purpose" toggle.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterTripsBottomSheet(
    onDismissRequest: () -> Unit,
    initialSelectedMonths: Set<String>,
    availableYears: List<Int>,
    onApplyFilters: (
        months: Set<String>,
        vehicles: Set<String>,
        tripTypes: Set<String>,
        onlyMissingPurpose: Boolean
    ) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Filter trips",
    vehicles: List<VehicleEntity> = emptyList(),
    initialSelectedVehicles: Set<String> = emptySet(),
    initialSelectedTripTypes: Set<String> = emptySet(),
    initialOnlyMissingPurpose: Boolean = false,
    showVehicleFilter: Boolean = true,
    showTripTypeFilter: Boolean = true,
    showMissingPurposeToggle: Boolean = true,
    showQuickTimePresets: Boolean = false,
    initialTimeFilter: TimeFilter? = null,
    onApplyTimeFilter: ((TimeFilter?) -> Unit)? = null
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val scrimColor = if (isDark) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.45f)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentYear = remember { LocalDate.now().year }
    val currentMonth = remember { LocalDate.now().monthValue }
    val sortedYears = remember(availableYears, currentYear) {
        (availableYears + currentYear).distinct().sortedDescending()
    }

    var activeYear by remember {
        mutableIntStateOf(
            initialSelectedMonths.firstOrNull()?.take(4)?.toIntOrNull() ?: currentYear
        )
    }
    var draftMonths by remember { mutableStateOf(initialSelectedMonths) }
    var draftTimeFilter by remember { mutableStateOf(initialTimeFilter) }
    var draftVehicles by remember { mutableStateOf(initialSelectedVehicles) }
    var draftTripTypes by remember { mutableStateOf(initialSelectedTripTypes) }
    var draftOnlyMissingPurpose by remember { mutableStateOf(initialOnlyMissingPurpose) }
    var showYearDropdown by remember { mutableStateOf(false) }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val maxSheetHeight = (screenHeightDp * 0.90f).dp

    val chipUnselectedBg = if (isDark) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    } else {
        Color(0xFFF1F5F9)
    }
    val chipUnselectedText = MaterialTheme.colorScheme.onSurface
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    fun resetDraft() {
        draftMonths = emptySet()
        draftTimeFilter = null
        draftVehicles = emptySet()
        draftTripTypes = emptySet()
        draftOnlyMissingPurpose = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = AppShapes.BottomSheet,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = scrimColor,
        contentWindowInsets = { WindowInsets.ime.union(WindowInsets.navigationBars) },
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
        ) {
            // Fixed Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                // Period / Year Section
                if (showQuickTimePresets) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = chipUnselectedBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { activeYear -= 1 },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous year",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = activeYear.toString(),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { if (activeYear < currentYear) activeYear += 1 },
                                enabled = activeYear < currentYear,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next year",
                                    tint = if (activeYear < currentYear) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Period",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Select one or more months",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Year Selector Pill
                        Box {
                            Surface(
                                onClick = { showYearDropdown = true },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = activeYear.toString(),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Select year",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showYearDropdown,
                                onDismissRequest = { showYearDropdown = false }
                            ) {
                                sortedYears.forEach { year ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = year.toString(),
                                                fontWeight = if (year == activeYear) FontWeight.Bold else FontWeight.Normal,
                                                color = if (year == activeYear) SucculentPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            activeYear = year
                                            showYearDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val ytdMonths = (1..(if (activeYear == currentYear) currentMonth else 12))
                        .map { "%04d-%02d".format(activeYear, it) }
                        .toSet()
                    val last3End = if (activeYear == currentYear) currentMonth else 12
                    val last3Start = (last3End - 2).coerceAtLeast(1)
                    val last3Months = (last3Start..last3End)
                        .map { "%04d-%02d".format(activeYear, it) }
                        .toSet()
                    val allYearMonths = (1..12)
                        .map { "%04d-%02d".format(activeYear, it) }
                        .toSet()

                    if (showQuickTimePresets) {
                        FilterPresetChip(
                            label = "Today",
                            selected = draftTimeFilter == TimeFilter.TODAY && draftMonths.isEmpty(),
                            unselectedBg = chipUnselectedBg,
                            onClick = {
                                draftTimeFilter = TimeFilter.TODAY
                                draftMonths = emptySet()
                            }
                        )
                        FilterPresetChip(
                            label = "This Week",
                            selected = draftTimeFilter == TimeFilter.THIS_WEEK && draftMonths.isEmpty(),
                            unselectedBg = chipUnselectedBg,
                            onClick = {
                                draftTimeFilter = TimeFilter.THIS_WEEK
                                draftMonths = emptySet()
                            }
                        )
                    }
                    FilterPresetChip(
                        label = "YTD",
                        selected = draftMonths == ytdMonths,
                        unselectedBg = chipUnselectedBg,
                        onClick = {
                            draftTimeFilter = null
                            draftMonths = ytdMonths
                        }
                    )
                    if (!showQuickTimePresets) {
                        FilterPresetChip(
                            label = "Last 3 months",
                            selected = draftMonths == last3Months,
                            unselectedBg = chipUnselectedBg,
                            onClick = {
                                draftTimeFilter = null
                                draftMonths = last3Months
                            }
                        )
                    }
                    FilterPresetChip(
                        label = "All year",
                        selected = draftMonths == allYearMonths,
                        unselectedBg = chipUnselectedBg,
                        onClick = {
                            draftTimeFilter = null
                            draftMonths = allYearMonths
                        }
                    )
                }

                // 4x3 Month Grid
                val monthNames = listOf(
                    "Jan", "Feb", "Mar", "Apr",
                    "May", "Jun", "Jul", "Aug",
                    "Sep", "Oct", "Nov", "Dec"
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (rowIndex in 0 until 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (colIndex in 0 until 4) {
                                val monthNumber = rowIndex * 4 + colIndex + 1
                                val monthKey = "%04d-%02d".format(activeYear, monthNumber)
                                val isSelected = monthKey in draftMonths
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) SucculentPrimary else chipUnselectedBg)
                                        .clickable {
                                            draftTimeFilter = null
                                            draftMonths = if (isSelected) {
                                                draftMonths - monthKey
                                            } else {
                                                draftMonths + monthKey
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = monthNames[monthNumber - 1],
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else chipUnselectedText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showVehicleFilter) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = dividerColor
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Vehicle",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        FilterSelectableChip(
                            label = "All vehicles",
                            selected = draftVehicles.isEmpty(),
                            unselectedBg = chipUnselectedBg,
                            onClick = { draftVehicles = emptySet() }
                        )
                        vehicles.forEach { vehicle ->
                            val isSelected = vehicle.id in draftVehicles
                            FilterSelectableChip(
                                label = vehicle.name,
                                selected = isSelected,
                                unselectedBg = chipUnselectedBg,
                                onClick = {
                                    draftVehicles = if (isSelected) {
                                        draftVehicles - vehicle.id
                                    } else {
                                        draftVehicles + vehicle.id
                                    }
                                }
                            )
                        }
                    }
                }

                if (showTripTypeFilter) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = dividerColor
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocalOffer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Trip type",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    val tripTypeOptions = listOf(
                        "BUSINESS" to "Business",
                        "PERSONAL" to "Personal",
                        "UNREVIEWED" to "Unclassified"
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        FilterSelectableChip(
                            label = "All",
                            selected = draftTripTypes.isEmpty(),
                            unselectedBg = chipUnselectedBg,
                            onClick = { draftTripTypes = emptySet() }
                        )
                        tripTypeOptions.forEach { (value, label) ->
                            val isSelected = value in draftTripTypes
                            FilterSelectableChip(
                                label = label,
                                selected = isSelected,
                                unselectedBg = chipUnselectedBg,
                                onClick = {
                                    draftTripTypes = if (isSelected) {
                                        draftTripTypes - value
                                    } else {
                                        draftTripTypes + value
                                    }
                                }
                            )
                        }
                    }
                }

                if (showMissingPurposeToggle) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = dividerColor
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Only missing purpose",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Switch(
                            checked = draftOnlyMissingPurpose,
                            onCheckedChange = { draftOnlyMissingPurpose = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SucculentPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Stationary Footer
            HorizontalDivider(color = dividerColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { resetDraft() },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "Clear",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SucculentPrimary
                    )
                }
                Button(
                    onClick = {
                        onApplyTimeFilter?.invoke(draftTimeFilter)
                        onApplyFilters(
                            draftMonths,
                            draftVehicles,
                            draftTripTypes,
                            draftOnlyMissingPurpose
                        )
                        onDismissRequest()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SucculentPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Apply filters",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPresetChip(
    label: String,
    selected: Boolean,
    unselectedBg: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) SucculentPrimary.copy(alpha = 0.14f) else unselectedBg,
        border = if (selected) BorderStroke(1.dp, SucculentPrimary) else null
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            color = if (selected) SucculentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun FilterSelectableChip(
    label: String,
    selected: Boolean,
    unselectedBg: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) SucculentPrimary else unselectedBg
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
