package com.shiftnote.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.shiftnote.AppContainer
import com.shiftnote.ui.clients.ClientEditorScreen
import com.shiftnote.ui.main.MainScreen
import com.shiftnote.ui.settings.SettingsScreen

object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val CLIENTS = "clients"
}

@Composable
fun ShiftNoteNavGraph(
    navController: NavHostController,
    container: AppContainer
) {
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                container = container,
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onNavigateToClients = {
                    navController.navigate(Routes.CLIENTS) { launchSingleTop = true }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.CLIENTS) {
            ClientEditorScreen(
                container = container,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
