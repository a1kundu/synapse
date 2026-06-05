package `in`.arijitk.synapse

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import `in`.arijitk.synapse.navigation.Routes
import `in`.arijitk.synapse.settings.SettingsRepository
import `in`.arijitk.synapse.theme.SynapseTheme
import `in`.arijitk.synapse.ui.home.HomeShell
import `in`.arijitk.synapse.ui.settings.SettingsScreen

/**
 * Root application composable.
 * Sets up theming, navigation, and initializes settings.
 */
@Composable
fun App() {
    // Load persisted settings at startup
    LaunchedEffect(Unit) {
        SettingsRepository.instance.loadIntoRuntime()
    }

    SynapseTheme {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(300),
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth / 3 },
                    animationSpec = tween(300),
                ) + fadeOut(animationSpec = tween(150))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth / 3 },
                    animationSpec = tween(300),
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(300),
                ) + fadeOut(animationSpec = tween(150))
            },
        ) {
            composable(Routes.HOME) {
                HomeShell(
                    onNavigateToSettings = {
                        navController.navigate(Routes.SETTINGS)
                    },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}
