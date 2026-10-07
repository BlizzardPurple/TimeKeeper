package com.office.timekeeper.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.office.timekeeper.ui.dashboard.DashboardScreen
import com.office.timekeeper.ui.dashboard.DashboardViewModel
import com.office.timekeeper.ui.history.HistoryScreen
import com.office.timekeeper.ui.history.HistoryViewModel
import com.office.timekeeper.ui.settings.SettingsScreen
import com.office.timekeeper.ui.settings.SettingsViewModel

/**
 * Sealed class representing the three bottom-navigation routes.
 *
 * Each entry carries a route string, display label, and icon for the
 * [NavigationBar] items.
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    data object History : Screen("history", "History", Icons.Default.DateRange)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

/** Ordered list of bottom-bar destinations. */
private val bottomNavItems = listOf(Screen.Dashboard, Screen.History, Screen.Settings)

/**
 * Top-level navigation graph with Material 3 bottom navigation.
 *
 * Uses a [Scaffold] with a [NavigationBar] in the bottomBar slot and a
 * [NavHost] that routes between Dashboard, History, and Settings destinations.
 * Each screen receives its ViewModel as a parameter — the caller (MainActivity)
 * is responsible for providing them.
 *
 * @param dashboardViewModel ViewModel driving the Dashboard screen
 * @param historyViewModel ViewModel driving the History screen
 * @param settingsViewModel ViewModel driving the Settings screen
 */
@Composable
fun NavGraph(
    dashboardViewModel: DashboardViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
                        } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                // Pop up to the start destination to avoid stacking
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(viewModel = dashboardViewModel)
            }
            composable(Screen.History.route) {
                HistoryScreen(viewModel = historyViewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}

