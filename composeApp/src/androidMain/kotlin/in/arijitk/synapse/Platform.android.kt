package `in`.arijitk.synapse

import android.content.Intent
import android.net.Uri
import `in`.arijitk.synapse.settings.ApplicationContextHolder

actual fun getPlatformName(): String = "Android"

actual val isAndroid: Boolean = true

actual val isDebug: Boolean = BuildConfig.DEBUG

actual fun openUrl(url: String) {
    try {
        val context = ApplicationContextHolder.context
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // Ignore if no browser available
    }
}
