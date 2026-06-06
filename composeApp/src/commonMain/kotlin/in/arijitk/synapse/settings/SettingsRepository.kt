package `in`.arijitk.synapse.settings

import `in`.arijitk.synapse.mcp.McpServerConfig
import `in`.arijitk.synapse.mcp.McpTransportType
import `in`.arijitk.synapse.theme.ThemeMode
import `in`.arijitk.synapse.theme.ThemeSettings
import kotlinx.serialization.json.Json

/**
 * Platform-specific key-value persistence.
 */
expect class PlatformPreferences() {
    fun getString(key: String, default: String): String
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
}

/**
 * Supported LLM API providers.
 */
enum class LlmProvider(val displayName: String, val defaultBaseUrl: String) {
    OPENAI("OpenAI", "https://api.openai.com/v1"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1"),
    GITHUB_MODELS("GitHub Models", "https://models.inference.ai.azure.com"),
}

/**
 * Application settings repository.
 * Manages persistence of all user preferences.
 */
class SettingsRepository(
    private val prefs: PlatformPreferences = PlatformPreferences()
) {
    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_AUTO_UPDATE = "auto_update_check"
        private const val KEY_PENDING_UPDATE = "pending_update_json"
        private const val KEY_LLM_PROVIDER = "llm_provider"
        private const val KEY_LLM_API_KEY = "llm_api_key"
        private const val KEY_LLM_SERVER_URL = "llm_server_url"
        private const val KEY_MCP_SERVERS = "mcp_servers_json"
        private const val KEY_LAST_MODEL_ID = "last_selected_model_id"

        private var _instance: SettingsRepository? = null
        val instance: SettingsRepository
            get() = _instance ?: SettingsRepository().also { _instance = it }
    }

    // Theme mode
    var themeMode: ThemeMode
        get() = ThemeMode.fromString(prefs.getString(KEY_THEME_MODE, "system"))
        set(value) {
            prefs.putString(KEY_THEME_MODE, value.name.lowercase())
            ThemeSettings.themeMode = value
        }

    // Dynamic color
    var dynamicColorEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
        set(value) {
            prefs.putBoolean(KEY_DYNAMIC_COLOR, value)
            ThemeSettings.dynamicColorEnabled = value
        }

    // Auto-update check
    var autoUpdateCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        set(value) {
            prefs.putBoolean(KEY_AUTO_UPDATE, value)
        }

    // Persisted pending update JSON (for notification tap recovery after app kill)
    var pendingUpdateJson: String
        get() = prefs.getString(KEY_PENDING_UPDATE, "")
        set(value) {
            prefs.putString(KEY_PENDING_UPDATE, value)
        }

    // LLM provider
    var llmProvider: LlmProvider
        get() {
            val stored = prefs.getString(KEY_LLM_PROVIDER, LlmProvider.OPENAI.name)
            return try { LlmProvider.valueOf(stored) } catch (_: Exception) { LlmProvider.OPENAI }
        }
        set(value) {
            prefs.putString(KEY_LLM_PROVIDER, value.name)
        }

    // LLM API key
    var llmApiKey: String
        get() = prefs.getString(KEY_LLM_API_KEY, "")
        set(value) {
            prefs.putString(KEY_LLM_API_KEY, value)
        }

    // LLM server URL (empty = use provider default)
    var llmServerUrl: String
        get() = prefs.getString(KEY_LLM_SERVER_URL, "")
        set(value) {
            prefs.putString(KEY_LLM_SERVER_URL, value)
        }

    // ── MCP Servers ─────────────────────────────────────────────────────────

    private val mcpJson = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Raw JSON array of MCP server configs. */
    private var mcpServersJson: String
        get() = prefs.getString(KEY_MCP_SERVERS, "[]")
        set(value) {
            prefs.putString(KEY_MCP_SERVERS, value)
        }

    /** Parsed list of MCP server configs. */
    var mcpServers: List<McpServerConfig>
        get() = try {
            mcpJson.decodeFromString<List<McpServerConfig>>(mcpServersJson)
        } catch (_: Exception) {
            emptyList()
        }
        set(value) {
            mcpServersJson = mcpJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(McpServerConfig.serializer()),
                value,
            )
        }

    fun addMcpServer(server: McpServerConfig) {
        mcpServers = mcpServers + server
    }

    fun removeMcpServer(name: String) {
        mcpServers = mcpServers.filter { it.name != name }
    }

    // Last selected model ID
    var lastSelectedModelId: String
        get() = prefs.getString(KEY_LAST_MODEL_ID, "")
        set(value) {
            prefs.putString(KEY_LAST_MODEL_ID, value)
        }

    /** Resolved base URL: custom if set, otherwise provider default. */
    val resolvedBaseUrl: String
        get() {
            val custom = llmServerUrl.trim()
            return if (custom.isNotEmpty()) custom.trimEnd('/') else llmProvider.defaultBaseUrl
        }

    /** Whether LLM is configured (has API key). */
    val isLlmConfigured: Boolean
        get() = llmApiKey.isNotBlank()

    /**
     * Load all persisted settings into runtime state.
     * Call this at app startup.
     */
    fun loadIntoRuntime() {
        ThemeSettings.themeMode = themeMode
        ThemeSettings.dynamicColorEnabled = dynamicColorEnabled
    }
}
