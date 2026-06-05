package `in`.arijitk.synapse

actual fun getPlatformName(): String = "Android"

actual val isAndroid: Boolean = true

actual val isDebug: Boolean = BuildConfig.DEBUG
