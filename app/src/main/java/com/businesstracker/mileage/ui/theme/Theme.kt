package com.businesstracker.mileage.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.businesstracker.mileage.domain.model.AppThemeMode

private val LightColorScheme = lightColorScheme(
    primary = SucculentPrimary,
    onPrimary = Color.White,
    primaryContainer = SucculentContainer,
    onPrimaryContainer = SucculentPrimaryVariant,
    secondary = SucculentSecondary,
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = SucculentContainer,
    onSecondaryContainer = SucculentPrimaryVariant,
    tertiary = SlateNeutral,
    onTertiary = Color.White,
    background = NeutralBackgroundLight,
    onBackground = Color(0xFF1E293B),
    surface = NeutralSurfaceLight,
    onSurface = Color(0xFF1E293B),
    surfaceVariant = NeutralBackgroundLight,
    outline = NeutralBorderLight
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF22C55E),
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = Color(0xFFDCFCE7),
    secondary = SucculentSecondary,
    onSecondary = Color(0xFF052E16),
    secondaryContainer = Color(0xFF14532D),
    onSecondaryContainer = Color(0xFFDCFCE7),
    tertiary = SlateLight,
    onTertiary = SlateDark,
    background = NeutralBackgroundDark,
    onBackground = Color(0xFFF1F5F9),
    surface = NeutralSurfaceDark,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),
    outline = NeutralBorderDark
)

@Composable
fun BusinessMileageTrackerTheme(
    themeMode: AppThemeMode = AppThemeMode.AUTO,
    content: @Composable () -> Unit
) {
    val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

internal fun resolveDarkTheme(themeMode: AppThemeMode, systemIsDark: Boolean): Boolean =
    when (themeMode) {
        AppThemeMode.AUTO -> systemIsDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
