package com.businesstracker.mileage.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.72f) // ~20% smaller width compared to typical 90% dialogs
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Dialog Title
                    Text(
                        text = "Select Time",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(10.dp))

                    // Wheels container: 3 items * 36.dp = 108.dp height
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(108.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Center highlighted selection band
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            shape = RoundedCornerShape(8.dp),
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
                                itemHeight = 36.dp,
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
                                itemHeight = 36.dp,
                                modifier = Modifier.weight(1f),
                                onItemSelected = { selectedMinuteIndex = it }
                            )

                            Spacer(Modifier.width(4.dp))

                            // AM/PM Column
                            WheelColumn(
                                items = amPmList,
                                initialIndex = initialAmPmIndex,
                                itemHeight = 36.dp,
                                modifier = Modifier.weight(1f),
                                onItemSelected = { selectedAmPmIndex = it }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Buttons row with compacted spacing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
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
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = "Confirm",
                                color = Color.White,
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
