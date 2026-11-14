package com.businesstracker.mileage.ui.settings

import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.businesstracker.mileage.ui.theme.AppShapes

/**
 * Legacy dialog surface preserved for backwards compatibility.
 * Replaced by [com.businesstracker.mileage.ui.components.AppFloatingBottomSheet]
 * and [com.businesstracker.mileage.ui.components.AppConfirmationDialog].
 *
 * Updated to use 100% opaque theme-aware surface per design system requirements.
 */
@Deprecated("Use AppFloatingBottomSheet for editors or AppConfirmationDialog for alerts")
@Composable
internal fun SettingsDialogSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.setDimAmount(0.50f)
    }
    Surface(
        modifier = modifier,
        shape = AppShapes.FloatingDialog,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = MaterialTheme.colorScheme.surfaceTint.let { 0.dp },
        content = content
    )
}
