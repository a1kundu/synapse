package `in`.arijitk.synapse.settings

import `in`.arijitk.synapse.theme.ThemeMode
import `in`.arijitk.synapse.theme.ThemeSettings

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

    /**
     * Load all persisted settings into runtime state.
     * Call this at app startup.
     */
    fun loadIntoRuntime() {
        ThemeSettings.themeMode = themeMode
        ThemeSettings.dynamicColorEnabled = dynamicColorEnabled
    }
}
