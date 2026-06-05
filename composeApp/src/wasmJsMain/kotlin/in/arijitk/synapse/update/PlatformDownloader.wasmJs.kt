package `in`.arijitk.synapse.update

/**
 * Web (Wasm) implementation of [PlatformDownloader].
 * All operations are no-ops — web builds are always served fresh from the server.
 */
actual class PlatformDownloader actual constructor() {

    actual suspend fun download(
        update: AppUpdate,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ) {
        // No-op on web
    }

    actual suspend fun install(filePath: String) {
        // No-op on web
    }

    actual fun cancel() {
        // No-op on web
    }

    actual suspend fun getExistingApk(update: AppUpdate): String? = null

    actual fun getCompletedFilePath(update: AppUpdate): String = ""

    actual fun startForegroundNotification(update: AppUpdate) {}
    actual fun updateForegroundNotification(update: AppUpdate, progress: Float, text: String) {}
    actual fun stopForegroundNotification() {}
    actual fun showCompletedNotification(update: AppUpdate) {}
    actual fun showFailedNotification(update: AppUpdate) {}
}
