package `in`.arijitk.synapse

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import `in`.arijitk.synapse.navigation.Routes
import `in`.arijitk.synapse.settings.SettingsRepository
import `in`.arijitk.synapse.theme.SynapseTheme
import `in`.arijitk.synapse.ui.home.HomeShell
import `in`.arijitk.synapse.ui.settings.SettingsScreen
import `in`.arijitk.synapse.ui.update.DownloadProgressDialog
import `in`.arijitk.synapse.ui.update.UpdateDialog
import `in`.arijitk.synapse.update.AppUpdate
import `in`.arijitk.synapse.update.DownloadManager

/**
 * CompositionLocal for global snackbar access.
 */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

/**
 * Global update dialog controller.
 * Can be triggered from settings screen, startup check, or notification tap.
 */
object UpdateDialogController {
    var showUpdateDialog by mutableStateOf(false)
    var showDownloadDialog by mutableStateOf(false)
    var pendingUpdate by mutableStateOf<AppUpdate?>(null)
}

/**
 * Root application composable.
 * Sets up theming, navigation, global snackbar, and update dialog overlays.
 */
@Composable
fun App() {
    // Load persisted settings at startup
    LaunchedEffect(Unit) {
        SettingsRepository.instance.loadIntoRuntime()
    }

    SynapseTheme {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }

        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(300),
                        )
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -fullWidth / 3 },
                            animationSpec = tween(300),
                        )
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> -fullWidth / 3 },
                            animationSpec = tween(300),
                        )
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(300),
                        )
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

                // Global snackbar overlay
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            // ── Global update dialogs ───────────────────────────────────
            GlobalUpdateDialogs()
        }
    }
}

/**
 * Global update dialog composables.
 * Shows UpdateDialog or DownloadProgressDialog based on [UpdateDialogController] state.
 */
@Composable
private fun GlobalUpdateDialogs() {
    val update = UpdateDialogController.pendingUpdate

    // Update available dialog
    if (UpdateDialogController.showUpdateDialog && update != null) {
        UpdateDialog(
            update = update,
            onDismiss = {
                UpdateDialogController.showUpdateDialog = false
            },
            onDownload = {
                UpdateDialogController.showUpdateDialog = false
                UpdateDialogController.showDownloadDialog = true
            },
            onDisableAutoUpdate = {
                SettingsRepository.instance.autoUpdateCheckEnabled = false
            },
        )
    }

    // Download progress dialog
    if (UpdateDialogController.showDownloadDialog && update != null) {
        DownloadProgressDialog(
            update = update,
            onDismiss = {
                UpdateDialogController.showDownloadDialog = false
            },
        )
    }
}
