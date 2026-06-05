package `in`.arijitk.synapse

/**
 * Platform identification for multiplatform code.
 */
expect fun getPlatformName(): String

/**
 * Whether the current platform is Android.
 */
expect val isAndroid: Boolean

/**
 * Whether the app is running in debug mode.
 */
expect val isDebug: Boolean

/**
 * Open a URL in the system browser.
 */
expect fun openUrl(url: String)
