package com.businesstracker.mileage.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
            DashboardScreen(
                onNavigateToReports = {
                    navController.navigate(Screen.Reports.route) {
                        popUpTo(Screen.Dashboard.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable(Screen.TrackTrip.route) {
            TrackTripScreen()
        }
        composable(
            route = "${Screen.History.route}?month={month}",
            arguments = listOf(
                navArgument("month") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            TripHistoryScreen(
                initialMonthIso = backStackEntry.arguments?.getString("month").orEmpty()
            )
        }
        composable(Screen.Reports.route) {
            ReportsExportScreen(
                onNavigateToTrips = { monthIso ->
                    navController.navigate("${Screen.History.route}?month=$monthIso") {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
