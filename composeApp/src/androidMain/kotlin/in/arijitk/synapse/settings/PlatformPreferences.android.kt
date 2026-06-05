package `in`.arijitk.synapse.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Android SharedPreferences-based persistence.
 */
actual class PlatformPreferences actual constructor() {
    private val prefs: SharedPreferences by lazy {
        ApplicationContextHolder.context.getSharedPreferences("synapse_prefs", Context.MODE_PRIVATE)
    }

    actual fun getString(key: String, default: String): String {
        return prefs.getString(key, default) ?: default
    }

    actual fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        return prefs.getBoolean(key, default)
    }

    actual fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    actual fun getInt(key: String, default: Int): Int {
        return prefs.getInt(key, default)
    }

    actual fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }
}

/**
 * Holder for the application context.
 * Must be initialized in Application.onCreate() or MainActivity.onCreate().
 */
object ApplicationContextHolder {
    lateinit var context: Context
        private set

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }
}
