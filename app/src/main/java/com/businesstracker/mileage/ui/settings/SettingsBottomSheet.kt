package com.businesstracker.mileage.ui.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.businesstracker.mileage.ui.components.AppFloatingBottomSheet

/**
 * Backward-compatible delegator to the standardized [AppFloatingBottomSheet].
 */
@Composable
internal fun SettingsBottomSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AppFloatingBottomSheet(
        onDismissRequest = onDismissRequest,
        onClose = null,
        content = content
    )
}
