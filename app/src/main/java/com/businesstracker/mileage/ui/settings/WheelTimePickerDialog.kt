package com.businesstracker.mileage.ui.settings

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.businesstracker.mileage.ui.theme.SucculentPrimary

@Composable
fun WheelTimePickerDialog(
    initialTime24: String, // "08:00" or "16:30"
    onDismiss: () -> Unit,
    onTimeConfirm: (String) -> Unit // returns "HH:mm" in 24h
) {
    // Parse initial time
    val (parsedHour, parsedMinute) = try {
        val parts = initialTime24.trim().split(":")
        val h = parts[0].toInt().coerceIn(0, 23)
        val m = if (parts.size > 1) parts[1].toInt().coerceIn(0, 59) else 0
        Pair(h, m)
    } catch (_: Exception) {
        Pair(8, 0)
    }

    // Convert 24h to 12h: hour12 in 1..12, isPm boolean
    val initialIsPm = parsedHour >= 12
    val initialHour12 = when {
        parsedHour == 0 -> 12
        parsedHour > 12 -> parsedHour - 12
        else -> parsedHour
    }

    val hoursList = remember { (1..12).map { String.format("%02d", it) } }
    val minutesList = remember { (0..59).map { String.format("%02d", it) } }
    val amPmList = remember { listOf("AM", "PM") }

    val initialHourIndex = initialHour12 - 1
    val initialMinuteIndex = parsedMinute
    val initialAmPmIndex = if (initialIsPm) 1 else 0

    var selectedHourIndex by remember { mutableIntStateOf(initialHourIndex) }
    var selectedMinuteIndex by remember { mutableIntStateOf(initialMinuteIndex) }
    var selectedAmPmIndex by remember { mutableIntStateOf(initialAmPmIndex) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 400.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Dialog Title
                    Text(
                        text = "Select Time",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(12.dp))

                    // Three 48dp rows keep the selected value centered and touchable.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(144.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Center highlighted selection band
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = SucculentPrimary.copy(alpha = 0.10f)
                        ) {}

                        // 3 scrolling wheel columns: Hour : Minute  AM/PM
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hour Column
                            WheelColumn(
                                items = hoursList,
                                initialIndex = initialHourIndex,
                                itemHeight = 48.dp,
                                modifier = Modifier.weight(1f),
                                onItemSelected = { selectedHourIndex = it }
                            )

                            // Colon separator
                            Text(
                                text = ":",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )

                            // Minute Column
                            WheelColumn(
                                items = minutesList,
                                initialIndex = initialMinuteIndex,
                                itemHeight = 48.dp,
                                modifier = Modifier.weight(1f),
                                onItemSelected = { selectedMinuteIndex = it }
                            )

                            Spacer(Modifier.width(4.dp))

                            // AM/PM Column
                            WheelColumn(
                                items = amPmList,
                                initialIndex = initialAmPmIndex,
                                itemHeight = 48.dp,
                                modifier = Modifier.weight(1f),
                                onItemSelected = { selectedAmPmIndex = it }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Buttons row with compacted spacing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = "Cancel",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val hour12 = hoursList.getOrElse(selectedHourIndex) { "08" }.toInt()
                                val minute = minutesList.getOrElse(selectedMinuteIndex) { "00" }.toInt()
                                val isPm = selectedAmPmIndex == 1

                                val hour24 = when {
                                    isPm && hour12 < 12 -> hour12 + 12
                                    !isPm && hour12 == 12 -> 0
                                    else -> hour12
                                }

                                val formatted24 = String.format("%02d:%02d", hour24, minute)
                                onTimeConfirm(formatted24)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = "Confirm",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WheelColumn(
    items: List<String>,
    initialIndex: Int,
    itemHeight: Dp,
    modifier: Modifier = Modifier,
    onItemSelected: (Int) -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceIn(0, items.size - 1))
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Calculate current center item index
    val centerIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) initialIndex
            else {
                val centerOffset = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val closest = visibleItems.minByOrNull { item ->
                    val itemCenter = item.offset + (item.size / 2)
                    kotlin.math.abs(itemCenter - centerOffset)
                }
                closest?.index ?: initialIndex
            }
        }
    }

    LaunchedEffect(centerIndex) {
        if (centerIndex in items.indices) {
            onItemSelected(centerIndex)
        }
    }

    Box(
        modifier = modifier.height(itemHeight * 3),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items) { index, item ->
                val isSelected = index == centerIndex
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item,
                        style = if (isSelected) {
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        } else {
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        },
                        color = if (isSelected) SucculentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(if (isSelected) 1f else 0.4f)
                    )
                }
            }
        }
    }
}
