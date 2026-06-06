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
import `in`.arijitk.synapse.mcp.McpServerConfig
import `in`.arijitk.synapse.mcp.McpTransportType
import `in`.arijitk.synapse.settings.LlmProvider
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

    // LLM provider settings
    var llmProvider by remember { mutableStateOf(settings.llmProvider) }
    var llmApiKey by remember { mutableStateOf(settings.llmApiKey) }
    var llmServerUrl by remember { mutableStateOf(settings.llmServerUrl) }
    var showApiKey by remember { mutableStateOf(false) }

    // MCP servers
    var mcpServers by remember { mutableStateOf(settings.mcpServers) }
    var showAddMcpDialog by remember { mutableStateOf(false) }

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

                // ── LLM Provider ────────────────────────────────────────
                Spacer(Modifier.height(8.dp))
                SectionHeader("LLM Provider")

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        // Provider selection
                        LlmProvider.entries.forEach { provider ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = provider.displayName,
                                        fontWeight = if (llmProvider == provider) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (llmProvider == provider) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                    )
                                },
                                supportingContent = { Text(provider.defaultBaseUrl) },
                                leadingContent = {
                                    IconBox(
                                        icon = when (provider) {
                                            LlmProvider.OPENAI -> Icons.Outlined.SmartToy
                                            LlmProvider.GITHUB_MODELS -> Icons.Outlined.Code
                                            else -> Icons.Outlined.Hub
                                        },
                                        color = if (llmProvider == provider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingContent = {
                                    if (llmProvider == provider) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                modifier = Modifier.clickable {
                                    llmProvider = provider
                                    settings.llmProvider = provider
                                    // Reset server URL to default when switching
                                    if (llmServerUrl.isBlank() || llmServerUrl == LlmProvider.entries.first { it != provider }.defaultBaseUrl) {
                                        llmServerUrl = ""
                                        settings.llmServerUrl = ""
                                    }
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // API Key
                        Text(
                            text = "API Key",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = llmApiKey,
                            onValueChange = {
                                llmApiKey = it
                                settings.llmApiKey = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Enter your API key") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = if (showApiKey) {
                                androidx.compose.ui.text.input.VisualTransformation.None
                            } else {
                                androidx.compose.ui.text.input.PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (showApiKey) "Hide" else "Show",
                                    )
                                }
                            },
                        )

                        Spacer(Modifier.height(16.dp))

                        // Server URL
                        Text(
                            text = "Server URL",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Leave empty to use default: ${llmProvider.defaultBaseUrl}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = llmServerUrl,
                            onValueChange = {
                                llmServerUrl = it
                                settings.llmServerUrl = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(llmProvider.defaultBaseUrl) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }

                // ── MCP Servers ─────────────────────────────────────────
                Spacer(Modifier.height(8.dp))
                SectionHeader("MCP Servers")

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        if (mcpServers.isEmpty()) {
                            ListItem(
                                headlineContent = { Text("No MCP servers configured") },
                                supportingContent = { Text("Add servers to enable tool calling in chat") },
                                leadingContent = {
                                    IconBox(
                                        icon = Icons.Outlined.Extension,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                        } else {
                            mcpServers.forEach { server ->
                                ListItem(
                                    headlineContent = { Text(server.name) },
                                    supportingContent = {
                                        Text(
                                            "${server.type.name.replace('_', ' ')} · ${server.url}",
                                            maxLines = 1,
                                        )
                                    },
                                    leadingContent = {
                                        IconBox(
                                            icon = Icons.Outlined.Extension,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    },
                                    trailingContent = {
                                        IconButton(onClick = {
                                            settings.removeMcpServer(server.name)
                                            mcpServers = settings.mcpServers
                                        }) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    },
                                )
                            }
                        }

                        // Add button
                        ListItem(
                            headlineContent = {
                                Text(
                                    "Add MCP Server",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            leadingContent = {
                                IconBox(
                                    icon = Icons.Filled.Add,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            modifier = Modifier.clickable { showAddMcpDialog = true },
                        )
                    }
                }

                if (showAddMcpDialog) {
                    AddMcpServerDialog(
                        onDismiss = { showAddMcpDialog = false },
                        onAdd = { server ->
                            settings.addMcpServer(server)
                            mcpServers = settings.mcpServers
                            showAddMcpDialog = false
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

// ── Add MCP Server Dialog ───────────────────────────────────────────────────

@Composable
private fun AddMcpServerDialog(
    onDismiss: () -> Unit,
    onAdd: (McpServerConfig) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var transportType by remember { mutableStateOf(McpTransportType.HTTP_STREAMABLE) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add MCP Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. My Tools Server") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; urlError = null },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://example.com/mcp") },
                    singleLine = true,
                    isError = urlError != null,
                    supportingText = urlError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                Text(
                    text = "Transport Type",
                    style = MaterialTheme.typography.labelLarge,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    McpTransportType.entries.forEach { type ->
                        FilterChip(
                            selected = transportType == type,
                            onClick = { transportType = type },
                            label = {
                                Text(
                                    when (type) {
                                        McpTransportType.HTTP_STREAMABLE -> "HTTP Streamable"
                                        McpTransportType.SSE -> "SSE"
                                    }
                                )
                            },
                            leadingIcon = if (transportType == type) {
                                {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else null,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Validate
                    val trimName = name.trim()
                    val trimUrl = url.trim()
                    var valid = true

                    if (trimName.isBlank()) {
                        nameError = "Name is required"
                        valid = false
                    }
                    if (trimUrl.isBlank()) {
                        urlError = "URL is required"
                        valid = false
                    } else if (!trimUrl.startsWith("http://") && !trimUrl.startsWith("https://")) {
                        urlError = "Must start with http:// or https://"
                        valid = false
                    }

                    if (valid) {
                        onAdd(McpServerConfig(name = trimName, url = trimUrl, type = transportType))
                    }
                },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
