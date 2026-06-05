package `in`.arijitk.synapse.ui.settings

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
import `in`.arijitk.synapse.isAndroid
import `in`.arijitk.synapse.isDebug
import `in`.arijitk.synapse.settings.SettingsRepository
import `in`.arijitk.synapse.theme.ThemeMode
import `in`.arijitk.synapse.theme.ThemeSettings
import `in`.arijitk.synapse.update.AppUpdate
import `in`.arijitk.synapse.update.DownloadState
import `in`.arijitk.synapse.update.UpdateService
import kotlinx.coroutines.launch

/**
 * Settings screen with appearance, updates, and about sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
) {
    val settings = remember { SettingsRepository.instance }
    val updateService = remember { UpdateService() }
    val coroutineScope = rememberCoroutineScope()

    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var dynamicColor by remember { mutableStateOf(settings.dynamicColorEnabled) }
    var autoUpdate by remember { mutableStateOf(settings.autoUpdateCheckEnabled) }

    // Update check state
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf<AppUpdate?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // App Info Section
            AppInfoCard()

            // Appearance Section
            SectionHeader("Appearance")
            AppearanceSection(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                onThemeModeChange = { mode ->
                    themeMode = mode
                    settings.themeMode = mode
                },
                onDynamicColorChange = { enabled ->
                    dynamicColor = enabled
                    settings.dynamicColorEnabled = enabled
                },
            )

            // Updates Section (Android only)
            if (isAndroid) {
                SectionHeader("Updates")
                UpdatesSection(
                    autoUpdate = autoUpdate,
                    isChecking = isCheckingUpdate,
                    updateAvailable = updateAvailable,
                    updateError = updateError,
                    downloadState = downloadState,
                    onAutoUpdateChange = { enabled ->
                        autoUpdate = enabled
                        settings.autoUpdateCheckEnabled = enabled
                    },
                    onCheckForUpdate = {
                        coroutineScope.launch {
                            isCheckingUpdate = true
                            updateError = null
                            try {
                                updateAvailable = updateService.checkForUpdate()
                                if (updateAvailable == null) {
                                    updateError = "You're on the latest version"
                                }
                            } catch (e: Exception) {
                                updateError = e.message ?: "Failed to check for updates"
                            } finally {
                                isCheckingUpdate = false
                            }
                        }
                    },
                    onDownloadUpdate = { update ->
                        coroutineScope.launch {
                            updateService.downloadUpdate(update) { state ->
                                downloadState = state
                            }
                        }
                    },
                    onInstallUpdate = { filePath ->
                        coroutineScope.launch {
                            updateService.installUpdate(filePath)
                        }
                    },
                    onCancelDownload = {
                        updateService.cancelDownload()
                    },
                )
            }

            // GitHub Section
            SectionHeader("GitHub")
            GitHubSection()

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Hub,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column {
                Text(
                    text = "Synapse",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                val version = if (APP_VERSION == "APP_VERSION_PLACEHOLDER") "dev" else APP_VERSION
                val channel = if (isDebug) "debug" else "release"
                Text(
                    text = "v$version ($channel)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun AppearanceSection(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Theme mode options
            ThemeMode.entries.forEach { mode ->
                val icon = when (mode) {
                    ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                    ThemeMode.LIGHT -> Icons.Outlined.LightMode
                    ThemeMode.DARK -> Icons.Outlined.DarkMode
                }
                val label = when (mode) {
                    ThemeMode.SYSTEM -> "System default"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                }

                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = {
                        IconBox(
                            icon = icon,
                            color = if (themeMode == mode)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        RadioButton(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                        )
                    },
                    modifier = Modifier.clickable { onThemeModeChange(mode) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Dynamic color toggle
            ListItem(
                headlineContent = { Text("Dynamic color") },
                supportingContent = { Text("Use wallpaper-based colors (Android 12+)") },
                leadingContent = {
                    IconBox(
                        icon = Icons.Outlined.Palette,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = onDynamicColorChange,
                    )
                },
                modifier = Modifier.clickable { onDynamicColorChange(!dynamicColor) },
            )
        }
    }
}

@Composable
private fun UpdatesSection(
    autoUpdate: Boolean,
    isChecking: Boolean,
    updateAvailable: AppUpdate?,
    updateError: String?,
    downloadState: DownloadState,
    onAutoUpdateChange: (Boolean) -> Unit,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: (AppUpdate) -> Unit,
    onInstallUpdate: (String) -> Unit,
    onCancelDownload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Auto-update toggle
            ListItem(
                headlineContent = { Text("Auto-check for updates") },
                supportingContent = { Text("Periodically check for new versions") },
                leadingContent = {
                    IconBox(
                        icon = Icons.Outlined.SystemUpdate,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = autoUpdate,
                        onCheckedChange = onAutoUpdateChange,
                    )
                },
                modifier = Modifier.clickable { onAutoUpdateChange(!autoUpdate) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Manual check button
            ListItem(
                headlineContent = { Text("Check for updates") },
                supportingContent = {
                    when {
                        isChecking -> Text("Checking...")
                        updateAvailable != null -> Text("Update available: ${updateAvailable.versionName}")
                        updateError != null -> Text(updateError)
                        else -> {
                            val channel = if (isDebug) "debug" else "release"
                            Text("Channel: $channel")
                        }
                    }
                },
                leadingContent = {
                    IconBox(
                        icon = Icons.Outlined.Refresh,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                },
                trailingContent = {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                },
                modifier = Modifier.clickable(enabled = !isChecking) { onCheckForUpdate() },
            )

            // Download progress / install button
            when (val state = downloadState) {
                is DownloadState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Text(
                        text = "${(state.progress * 100).toInt()}% - ${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    TextButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text("Cancel")
                    }
                }
                is DownloadState.Completed -> {
                    ListItem(
                        headlineContent = { Text("Download complete") },
                        supportingContent = { Text("Tap to install") },
                        leadingContent = {
                            IconBox(
                                icon = Icons.Filled.CheckCircle,
                                color = Color(0xFF4CAF50),
                            )
                        },
                        modifier = Modifier.clickable { onInstallUpdate(state.filePath) },
                    )
                }
                is DownloadState.Failed -> {
                    ListItem(
                        headlineContent = { Text("Download failed") },
                        supportingContent = { Text(state.error) },
                        leadingContent = {
                            IconBox(
                                icon = Icons.Filled.Error,
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
                else -> {
                    // Show download button if update available
                    if (updateAvailable != null) {
                        FilledTonalButton(
                            onClick = { onDownloadUpdate(updateAvailable) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Download & Install (${formatBytes(updateAvailable.fileSize)})")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            GitHubLinkItem(
                icon = Icons.Outlined.Code,
                title = "Source Code",
                subtitle = "View project on GitHub",
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            GitHubLinkItem(
                icon = Icons.Outlined.BugReport,
                title = "Issues",
                subtitle = "Report bugs or request features",
                color = MaterialTheme.colorScheme.error,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            GitHubLinkItem(
                icon = Icons.Outlined.NewReleases,
                title = "Releases",
                subtitle = "View all releases and changelogs",
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun GitHubLinkItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { IconBox(icon = icon, color = color) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        modifier = Modifier.clickable {
            // TODO: Launch URL via platform-specific implementation
        },
    )
}

@Composable
private fun IconBox(
    icon: ImageVector,
    color: Color,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
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

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "${(bytes / 100_000_000) / 10.0} GB"
        bytes >= 1_000_000 -> "${(bytes / 100_000) / 10.0} MB"
        bytes >= 1_000 -> "${(bytes / 100) / 10.0} KB"
        else -> "$bytes B"
    }
}
