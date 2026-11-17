package com.businesstracker.mileage.ui.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

/** Replaces any transient destination with one notification-targeted History entry. */
internal fun NavController.navigateToNotificationTrip(tripId: String, openConfirmation: Boolean) {
    if (tripId.isBlank()) return

    val encodedTripId = Uri.encode(tripId)
    navigate("${Screen.History.route}?selectedTripId=$encodedTripId&openConfirmation=$openConfirmation") {
        popUpTo(graph.findStartDestination().id) {
            saveState = false
        }
        launchSingleTop = true
    }
}

/** Keeps bottom-tab navigation deterministic when a notification added a transient route. */
internal fun NavController.navigateToBottomTab(screen: Screen) {
    val currentBaseRoute = currentDestination?.route?.substringBefore("?")
    val startDestinationId = graph.findStartDestination().id

    if (screen == Screen.Dashboard) {
        if (currentBaseRoute != Screen.Dashboard.route && !popBackStack(Screen.Dashboard.route, false)) {
            navigate(Screen.Dashboard.route) {
                popUpTo(startDestinationId) {
                    inclusive = true
                    saveState = false
                }
                launchSingleTop = true
            }
        }
        return
    }

    if (currentBaseRoute == screen.route) {
        if (screen == Screen.History) {
            navigate(Screen.History.route) {
                popUpTo(startDestinationId) {
                    saveState = false
                }
                launchSingleTop = true
            }
        }
        return
    }

    navigate(screen.route) {
        popUpTo(startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = screen != Screen.History
    }
}
