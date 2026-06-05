package `in`.arijitk.synapse

import kotlinx.browser.window

actual fun getPlatformName(): String = "Web (Wasm)"

actual val isAndroid: Boolean = false

actual val isDebug: Boolean = false // Web builds are always "release" in terms of update channel

actual fun openUrl(url: String) {
    window.open(url, "_blank")
}
