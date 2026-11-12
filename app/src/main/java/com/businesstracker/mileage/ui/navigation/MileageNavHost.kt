package com.businesstracker.mileage.ui.navigation

import android.net.Uri
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
            val monthIso = backStackEntry.arguments?.getString("month").orEmpty()
            TripHistoryScreen(
                initialMonthIso = monthIso,
                onNavigateToSavedLocations = { locationId ->
                    navController.navigate("${Screen.Settings.route}?editLocationId=${Uri.encode(locationId)}") {
                        launchSingleTop = true
                    }
                },
                onBackToReports = if (monthIso.isNotBlank()) {
                    { navController.popBackStack() }
                } else {
                    null
                }
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
        composable(
            route = "${Screen.Settings.route}?editLocationId={editLocationId}",
            arguments = listOf(navArgument("editLocationId") {
                type = NavType.StringType
                defaultValue = ""
            })
        ) { backStackEntry ->
            SettingsScreen(initialEditLocationId = backStackEntry.arguments?.getString("editLocationId").orEmpty())
        }
    }
}
