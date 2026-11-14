package com.businesstracker.mileage.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.businesstracker.mileage.ui.theme.AppDimensions
import com.businesstracker.mileage.ui.theme.AppShapes
import com.businesstracker.mileage.ui.theme.AppSpacing
import com.businesstracker.mileage.ui.theme.SpeedRed
import com.businesstracker.mileage.ui.theme.SucculentPrimary

/**
 * Standardized Centered Floating Confirmation Dialog (Type B).
 *
 * Implements the centralized design system rules:
 * - 100% opaque theme-aware surface with 20dp corners on all four sides.
 * - Intentionally NO drag handle and NO swipe gestures (requires deliberate user action).
 * - Standardized typography, padding, and confirmation actions.
 */
@Composable
fun AppConfirmationDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String = "",
    confirmLabel: String = "Confirm",
    dismissLabel: String? = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = onDismissRequest,
    isDestructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable () -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = AppShapes.FloatingDialog,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = if (message.isNotBlank() || content != null) {
            {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (message.isNotBlank()) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (content != null) {
                        if (message.isNotBlank()) {
                            Spacer(Modifier.height(AppSpacing.Small))
                        }
                        content()
                    }
                }
            }
        } else null,
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                shape = AppShapes.Button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) SpeedRed else SucculentPrimary,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = confirmLabel,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = if (!dismissLabel.isNullOrBlank() && onDismiss != null) {
            {
                TextButton(
                    onClick = onDismiss,
                    shape = AppShapes.Button
                ) {
                    Text(
                        text = dismissLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else null
    )
}
