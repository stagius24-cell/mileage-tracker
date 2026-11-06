package com.businesstracker.mileage.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.businesstracker.mileage.ui.dashboard.DashboardScreen
import com.businesstracker.mileage.ui.history.TripHistoryScreen
import com.businesstracker.mileage.ui.reports.ReportsExportScreen
import com.businesstracker.mileage.ui.settings.SettingsScreen
import com.businesstracker.mileage.ui.track.TrackTripScreen

@Composable
fun MileageNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen()
        }
        composable(Screen.TrackTrip.route) {
            TrackTripScreen()
        }
        composable(Screen.History.route) {
            TripHistoryScreen()
        }
        composable(Screen.Reports.route) {
            ReportsExportScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
