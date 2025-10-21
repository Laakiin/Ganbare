package fr.lkn.ganbare.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.lkn.ganbare.ui.screens.PlanningScreen
import fr.lkn.ganbare.ui.screens.SettingsScreen
import fr.lkn.ganbare.ui.screens.TasksScreen

private enum class Dest(val route: String, val label: String, val iconText: String) {
    Planning("planning", "Planning", "📅"),
    Tasks("tasks", "Tâches", "✅"),
    Settings("settings", "Réglages", "⚙️")
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val items = listOf(Dest.Planning, Dest.Tasks, Dest.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val current = nav.currentBackStackEntryAsState().value?.destination?.route
                items.forEach { dest ->
                    NavigationBarItem(
                        selected = current == dest.route,
                        onClick = {
                            nav.navigate(dest.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(dest.iconText) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Dest.Planning.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Planning.route) { PlanningScreen() }
            composable(Dest.Tasks.route) { TasksScreen() }
            composable(Dest.Settings.route) { SettingsScreen() }
        }
    }
}
