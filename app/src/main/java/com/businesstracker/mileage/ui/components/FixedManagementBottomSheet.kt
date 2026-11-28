package com.businesstracker.mileage.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/**
 * Reusable fixed-height management bottom sheet.
 *
 * Designed for list-management sheets (such as Saved Locations) that require:
 * - A fixed ~75% screen height without auto-expanding or intermediate snap points.
 * - Handle-only vertical drag-to-dismiss so scrolling the list, header, or footer
 *   never moves or dismisses the sheet.
 * - Four distinct regions: draggable top handle, fixed header, independently
 *   scrollable middle content, and optional fixed footer.
 */
@Composable
fun FixedManagementBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    heightFraction: Float = 0.75f,
    header: @Composable ColumnScope.() -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val scrimColor = if (isDark) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.45f)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val sheetHeight = (configuration.screenHeightDp * heightFraction).dp
    val dismissThresholdPx = with(density) { 80.dp.toPx() }

    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val animatedOffsetPx by animateFloatAsState(targetValue = dragOffsetPx, label = "sheetDragOffset")

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .offset { IntOffset(0, animatedOffsetPx.roundToInt().coerceAtLeast(0)) }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume clicks so they don't hit the backdrop
                    ),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // 1. Draggable top handle (only region that drags/dismisses the sheet)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (dragOffsetPx >= dismissThresholdPx) {
                                            onDismissRequest()
                                        } else {
                                            dragOffsetPx = 0f
                                        }
                                    },
                                    onDragCancel = {
                                        dragOffsetPx = 0f
                                    },
                                    onVerticalDrag = { _, dragAmount ->
                                        dragOffsetPx = (dragOffsetPx + dragAmount).coerceAtLeast(0f)
                                    }
                                )
                            }
                            .padding(top = 10.dp, bottom = 6.dp),
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

                    // 2. Fixed header
                    Column(modifier = Modifier.fillMaxWidth()) {
                        header()
                    }

                    // 3. Independently scrollable content
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        content()
                    }

                    // 4. Optional fixed footer
                    if (footer != null) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            footer()
                        }
                    }
                }
            }
        }
    }
}
