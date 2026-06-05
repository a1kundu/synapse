package `in`.arijitk.synapse.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.arijitk.synapse.APP_VERSION
import `in`.arijitk.synapse.LocalSnackbarHostState
import `in`.arijitk.synapse.UpdateDialogController
import `in`.arijitk.synapse.isAndroid
import `in`.arijitk.synapse.isDebug
import `in`.arijitk.synapse.openUrl
import `in`.arijitk.synapse.settings.SettingsRepository
import `in`.arijitk.synapse.theme.ThemeMode
import `in`.arijitk.synapse.theme.ThemeSettings
import `in`.arijitk.synapse.update.UpdateChecker
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import synapse.composeapp.generated.resources.Res
import synapse.composeapp.generated.resources.app_icon

/**
 * Settings screen matching the Flutter SettingsScreen feature-for-feature.
 * Sections: App info, Appearance, Updates (Android only), GitHub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
) {
    val settings = remember { SettingsRepository.instance }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var dynamicColor by remember { mutableStateOf(settings.dynamicColorEnabled) }
    var autoUpdate by remember { mutableStateOf(settings.autoUpdateCheckEnabled) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxHeight()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── App Info ────────────────────────────────────────────
                AppInfoHeader()

                Spacer(Modifier.height(8.dp))

                // ── Appearance ──────────────────────────────────────────
                SectionHeader("Appearance")

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ThemeTile(
                            icon = Icons.Outlined.BrightnessAuto,
                            title = "System",
                            subtitle = "Follow device theme",
                            selected = themeMode == ThemeMode.SYSTEM,
                            onClick = {
                                themeMode = ThemeMode.SYSTEM
                                settings.themeMode = ThemeMode.SYSTEM
                            },
                        )
                        ThemeTile(
                            icon = Icons.Outlined.LightMode,
                            title = "Light",
                            subtitle = "Always use light theme",
                            selected = themeMode == ThemeMode.LIGHT,
                            onClick = {
                                themeMode = ThemeMode.LIGHT
                                settings.themeMode = ThemeMode.LIGHT
                            },
                        )
                        ThemeTile(
                            icon = Icons.Outlined.DarkMode,
                            title = "Dark",
                            subtitle = "Always use dark theme",
                            selected = themeMode == ThemeMode.DARK,
                            onClick = {
                                themeMode = ThemeMode.DARK
                                settings.themeMode = ThemeMode.DARK
                            },
                        )
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("Dynamic color") },
                        supportingContent = { Text("Use wallpaper colors (Android 12+)") },
                        leadingContent = {
                            IconBox(
                                icon = Icons.Outlined.Palette,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = {
                                    dynamicColor = it
                                    settings.dynamicColorEnabled = it
                                },
                            )
                        },
                        modifier = Modifier.clickable {
                            dynamicColor = !dynamicColor
                            settings.dynamicColorEnabled = dynamicColor
                        },
                    )
                }

                // ── Updates (Android only) ──────────────────────────────
                if (isAndroid) {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("Updates")

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            ListItem(
                                headlineContent = { Text("Automatic update check") },
                                supportingContent = {
                                    Text("Check for updates when the app opens or using background updates.")
                                },
                                leadingContent = {
                                    IconBox(
                                        icon = Icons.Outlined.Update,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = autoUpdate,
                                        onCheckedChange = {
                                            autoUpdate = it
                                            settings.autoUpdateCheckEnabled = it
                                        },
                                    )
                                },
                                modifier = Modifier.clickable {
                                    autoUpdate = !autoUpdate
                                    settings.autoUpdateCheckEnabled = autoUpdate
                                },
                            )

                            ListItem(
                                headlineContent = { Text("Check for updates") },
                                supportingContent = { Text("Channel: ${UpdateChecker.channel}") },
                                leadingContent = {
                                    IconBox(
                                        icon = Icons.Outlined.SystemUpdate,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                modifier = Modifier.clickable(enabled = !isCheckingUpdate) {
                                    coroutineScope.launch {
                                        isCheckingUpdate = true
                                        val update = UpdateChecker.checkForUpdate()
                                        isCheckingUpdate = false

                                        if (update != null) {
                                            UpdateDialogController.pendingUpdate = update
                                            UpdateDialogController.showUpdateDialog = true
                                        } else {
                                            snackbarHostState.showSnackbar(
                                                "You're already on the latest version."
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                // ── GitHub ──────────────────────────────────────────────
                Spacer(Modifier.height(8.dp))
                SectionHeader("GitHub")

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        GitHubLinkItem(
                            icon = Icons.Outlined.Code,
                            title = "Source Code",
                            subtitle = "a1kundu/synapse",
                            url = "https://github.com/a1kundu/synapse",
                        )
                        GitHubLinkItem(
                            icon = Icons.Outlined.BugReport,
                            title = "Report an Issue",
                            subtitle = "Bugs & feature requests",
                            url = "https://github.com/a1kundu/synapse/issues",
                        )
                        GitHubLinkItem(
                            icon = Icons.Outlined.NewReleases,
                            title = "Releases",
                            subtitle = "Download latest versions",
                            url = "https://github.com/a1kundu/synapse/releases",
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            // Full-screen checking spinner overlay
            if (isCheckingUpdate) {
                CheckingUpdateOverlay()
            }
        }
    }
}

// ── App Info Header ─────────────────────────────────────────────────────────

@Composable
private fun AppInfoHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(Res.drawable.app_icon),
            contentDescription = "Synapse",
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp)),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Synapse",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        val version = if (APP_VERSION == "APP_VERSION_PLACEHOLDER") "dev" else "v$APP_VERSION"
        val channel = UpdateChecker.channel
        Text(
            text = "$version ($channel)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Theme Tile (matching Flutter's _ThemeTile) ──────────────────────────────

@Composable
private fun ThemeTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    ListItem(
        headlineContent = {
            Text(
                text = title,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) cs.primary else Color.Unspecified,
            )
        },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) cs.primaryContainer
                        else cs.surfaceVariant,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        trailingContent = {
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = cs.primary,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

// ── Section Header ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

// ── Icon Box ────────────────────────────────────────────────────────────────

@Composable
private fun IconBox(
    icon: ImageVector,
    color: Color,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
    }
}

// ── GitHub Link Item ────────────────────────────────────────────────────────

@Composable
private fun GitHubLinkItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    url: String,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            IconBox(
                icon = icon,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        modifier = Modifier.clickable { openUrl(url) },
    )
}

// ── Checking Update Overlay (matches Flutter's fullscreen spinner) ──────────

@Composable
private fun CheckingUpdateOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
