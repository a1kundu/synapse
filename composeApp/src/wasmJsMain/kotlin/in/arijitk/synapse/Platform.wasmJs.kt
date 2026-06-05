package `in`.arijitk.synapse

actual fun getPlatformName(): String = "Web (Wasm)"

actual val isAndroid: Boolean = false

actual val isDebug: Boolean = false // Web builds are always "release" in terms of update channel
