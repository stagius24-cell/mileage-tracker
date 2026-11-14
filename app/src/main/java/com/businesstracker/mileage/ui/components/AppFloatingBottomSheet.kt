package com.businesstracker.mileage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.businesstracker.mileage.ui.theme.AppDimensions
import com.businesstracker.mileage.ui.theme.AppDragHandleColor
import com.businesstracker.mileage.ui.theme.AppShapes
import com.businesstracker.mileage.ui.theme.AppSpacing

/**
 * Standardized Material 3 Floating Bottom Sheet (Type A).
 *
 * Implements the centralized design system rules:
 * - 100% fully opaque theme-aware surface (never translucent).
 * - Rounded top corners of 24dp.
 * - Centered 32x4dp drag handle in muted slate gray.
 * - Standardized header with 20sp semibold title and 48dp touch target close button.
 * - Keyboard-aware layout using IME + navigation bar window insets.
 * - Natural swipe-down, scrim click, and back button dismissal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFloatingBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    onClose: (() -> Unit)? = onDismissRequest,
    isDismissible: Boolean = true,
    sheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { isDismissible }
    ),
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val scrimColor = if (isDark) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.45f)

    ModalBottomSheet(
        onDismissRequest = {
            if (isDismissible) {
                onDismissRequest()
            }
        },
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
                    .padding(top = AppSpacing.Small, bottom = AppSpacing.Medium)
                    .size(width = AppDimensions.DragHandleWidth, height = AppDimensions.DragHandleHeight)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        shape = AppShapes.DragHandle
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AppSpacing.Large)
        ) {
            if (title != null || onClose != null || headerAction != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.ExtraLarge),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.ExtraSmall)
                    ) {
                        headerAction?.invoke()

                        if (onClose != null) {
                            IconButton(
                                onClick = {
                                    if (isDismissible) onClose()
                                },
                                enabled = isDismissible,
                                modifier = Modifier
                                    .size(AppDimensions.MinimumTouchTarget)
                                    .semantics {
                                        role = Role.Button
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(AppDimensions.StandardIconSize)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(AppSpacing.Medium))
            }

            content()
        }
    }
}
