package `in`.arijitk.synapse.update

/**
 * Platform-specific download, install, and notification operations.
 *
 * Android: Downloads APK with HTTP Range resume + exponential backoff retry,
 *          manages foreground service notification for background survival.
 * Web:     No-op (updates not applicable for web builds).
 */
expect class PlatformDownloader() {

    /** Download the update APK. Calls [onProgress] with (downloadedBytes, totalBytes). */
    suspend fun download(
        update: AppUpdate,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    )

    /** Install a downloaded APK file. */
    suspend fun install(filePath: String)

    /** Cancel an ongoing download. */
    fun cancel()

    /** Check if APK already downloaded & complete. Returns file path or null. */
    suspend fun getExistingApk(update: AppUpdate): String?

    /** Get the expected file path for a completed APK. */
    fun getCompletedFilePath(update: AppUpdate): String

    /** Start foreground service with initial "Starting download" notification. */
    fun startForegroundNotification(update: AppUpdate)

    /** Update foreground notification with progress text. */
    fun updateForegroundNotification(update: AppUpdate, progress: Float, text: String)

    /** Stop foreground service / dismiss ongoing notification. */
    fun stopForegroundNotification()

    /** Show "Update ready — tap to install" notification. */
    fun showCompletedNotification(update: AppUpdate)

    /** Show "Download failed — open app to retry" notification. */
    fun showFailedNotification(update: AppUpdate)
}
