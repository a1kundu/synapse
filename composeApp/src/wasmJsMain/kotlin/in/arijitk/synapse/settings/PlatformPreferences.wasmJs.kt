package `in`.arijitk.synapse.settings

import kotlinx.browser.window

/**
 * Web localStorage-based persistence.
 */
actual class PlatformPreferences actual constructor() {

    actual fun getString(key: String, default: String): String {
        return window.localStorage.getItem(key) ?: default
    }

    actual fun putString(key: String, value: String) {
        window.localStorage.setItem(key, value)
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        val stored = window.localStorage.getItem(key) ?: return default
        return stored.toBooleanStrictOrNull() ?: default
    }

    actual fun putBoolean(key: String, value: Boolean) {
        window.localStorage.setItem(key, value.toString())
    }

    actual fun getInt(key: String, default: Int): Int {
        val stored = window.localStorage.getItem(key) ?: return default
        return stored.toIntOrNull() ?: default
    }

    actual fun putInt(key: String, value: Int) {
        window.localStorage.setItem(key, value.toString())
    }
}
