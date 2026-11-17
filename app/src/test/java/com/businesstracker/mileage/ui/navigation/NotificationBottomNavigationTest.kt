package com.businesstracker.mileage.ui.navigation

import android.content.Context
import androidx.navigation.NavType
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationBottomNavigationTest {

    private fun newController(): TestNavHostController {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TestNavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            graph = createGraph(startDestination = Screen.Dashboard.route) {
                composable(Screen.Dashboard.route) {}
                composable(
                    route = "${Screen.History.route}?selectedTripId={selectedTripId}&openConfirmation={openConfirmation}",
                    arguments = listOf(
                        navArgument("selectedTripId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("openConfirmation") {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    )
                ) {}
                composable(Screen.Reports.route) {}
                composable(Screen.Settings.route) {}
            }
        }
    }

    private fun routes(controller: TestNavHostController): List<String> {
        return listOfNotNull(
            controller.previousBackStackEntry?.destination?.route,
            controller.currentBackStackEntry?.destination?.route
        ).map { it.substringBefore("?") }
    }

    @Test
    fun normalHistoryToDashboardReturnsToRootInOneTap() {
        val nav = newController()
        nav.navigateToBottomTab(Screen.History)

        nav.navigateToBottomTab(Screen.Dashboard)

        assertEquals(listOf(Screen.Dashboard.route), routes(nav))
    }

    @Test
    fun notificationClassificationThenDashboardReturnsToRootInOneTap() {
        val nav = newController()
        nav.navigateToNotificationTrip("trip-1", openConfirmation = true)
        // Classification closes its sheet without changing the notification-owned destination.

        nav.navigateToBottomTab(Screen.Dashboard)

        assertEquals(listOf(Screen.Dashboard.route), routes(nav))
    }

    @Test
    fun notificationThenReportsThenDashboardWorks() {
        val nav = newController()
        nav.navigateToNotificationTrip("trip-2", openConfirmation = true)
        nav.navigateToBottomTab(Screen.Reports)
        nav.navigateToBottomTab(Screen.Dashboard)

        assertEquals(listOf(Screen.Dashboard.route), routes(nav))
    }

    @Test
    fun notificationThenSettingsThenDashboardWorks() {
        val nav = newController()
        nav.navigateToNotificationTrip("trip-3", openConfirmation = true)
        nav.navigateToBottomTab(Screen.Settings)
        nav.navigateToBottomTab(Screen.Dashboard)

        assertEquals(listOf(Screen.Dashboard.route), routes(nav))
    }

    @Test
    fun notificationBackArrowThenDashboardRemainsUsable() {
        val nav = newController()
        nav.navigateToNotificationTrip("trip-4", openConfirmation = true)
        assertTrue(nav.popBackStack())

        nav.navigateToBottomTab(Screen.Dashboard)

        assertEquals(listOf(Screen.Dashboard.route), routes(nav))
    }

    @Test
    fun repeatedNotificationTapsReplaceHistoryInsteadOfStackingIt() {
        val nav = newController()
        nav.navigateToNotificationTrip("trip-5", openConfirmation = true)
        nav.navigateToNotificationTrip("trip-5", openConfirmation = true)

        assertEquals(listOf(Screen.Dashboard.route, Screen.History.route), routes(nav))
        assertEquals("trip-5", nav.currentBackStackEntry?.arguments?.getString("selectedTripId"))
    }

    @Test
    fun notificationWhileAppIsOnReportsStartsOneHistoryEntry() {
        val nav = newController()
        nav.navigateToBottomTab(Screen.Reports)

        nav.navigateToNotificationTrip("trip-warm", openConfirmation = true)

        assertEquals(listOf(Screen.Dashboard.route, Screen.History.route), routes(nav))
    }

    @Test
    fun coldStartNotificationOpensHistoryAboveDashboard() {
        val nav = newController()

        nav.navigateToNotificationTrip("trip-cold", openConfirmation = true)

        assertEquals(listOf(Screen.Dashboard.route, Screen.History.route), routes(nav))
        assertEquals("trip-cold", nav.currentBackStackEntry?.arguments?.getString("selectedTripId"))
    }

    @Test
    fun repeatedDifferentNotificationsDoNotAccumulateHistoryDestinations() {
        val nav = newController()
        nav.navigateToNotificationTrip("trip-a", openConfirmation = true)
        nav.navigateToNotificationTrip("trip-b", openConfirmation = true)

        assertEquals(listOf(Screen.Dashboard.route, Screen.History.route), routes(nav))
        assertEquals("trip-b", nav.currentBackStackEntry?.arguments?.getString("selectedTripId"))
    }

    @Test
    fun restoredNotificationStackStillReturnsToDashboard() {
        val original = newController()
        original.navigateToNotificationTrip("trip-restored", openConfirmation = true)
        val savedState = original.saveState()

        val restored = newController()
        restored.restoreState(savedState)
        restored.graph = original.graph
        restored.navigateToBottomTab(Screen.Dashboard)

        assertEquals(listOf(Screen.Dashboard.route), routes(restored))
    }
}
