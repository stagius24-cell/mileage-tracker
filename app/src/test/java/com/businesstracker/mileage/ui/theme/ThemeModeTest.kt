package com.businesstracker.mileage.ui.theme

import com.businesstracker.mileage.domain.model.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun auto_followsSystemAppearance() {
        assertTrue(resolveDarkTheme(AppThemeMode.AUTO, systemIsDark = true))
        assertFalse(resolveDarkTheme(AppThemeMode.AUTO, systemIsDark = false))
    }

    @Test
    fun lightAndDark_overrideSystemAppearance() {
        assertFalse(resolveDarkTheme(AppThemeMode.LIGHT, systemIsDark = true))
        assertTrue(resolveDarkTheme(AppThemeMode.DARK, systemIsDark = false))
    }

    @Test
    fun storedValues_areStableAndUnknownValuesFallBackToAuto() {
        assertEquals(AppThemeMode.AUTO, AppThemeMode.fromStoredValue("AUTO"))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromStoredValue("LIGHT"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStoredValue("DARK"))
        assertEquals(AppThemeMode.AUTO, AppThemeMode.fromStoredValue(null))
        assertEquals(AppThemeMode.AUTO, AppThemeMode.fromStoredValue("UNKNOWN"))
    }
}
