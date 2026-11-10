package com.businesstracker.mileage.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Time",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Swipe up or down to set time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(144.dp), // 3 items * 48.dp
                    contentAlignment = Alignment.Center
                ) {
                    // Center highlighted selection band
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = SucculentPrimary.copy(alpha = 0.12f)
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
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        // Minute Column
                        WheelColumn(
                            items = minutesList,
                            initialIndex = initialMinuteIndex,
                            itemHeight = 48.dp,
                            modifier = Modifier.weight(1f),
                            onItemSelected = { selectedMinuteIndex = it }
                        )

                        Spacer(Modifier.width(8.dp))

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
            }
        },
        confirmButton = {
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
                colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
                                fontSize = 20.sp
                            )
                        } else {
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp
                            )
                        },
                        color = if (isSelected) SucculentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(if (isSelected) 1f else 0.45f)
                    )
                }
            }
        }
    }
}
