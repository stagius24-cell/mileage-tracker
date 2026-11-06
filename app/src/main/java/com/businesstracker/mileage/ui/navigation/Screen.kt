package com.businesstracker.mileage.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object TrackTrip : Screen("track", "Track", Icons.Default.DirectionsCar)
    object History : Screen("history", "History", Icons.Default.History)
    object Reports : Screen("reports", "Reports", Icons.Default.ReceiptLong)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    companion object {
        val bottomNavItems = listOf(
            Dashboard,
            History,
            Reports,
            Settings
        )
    }
}
