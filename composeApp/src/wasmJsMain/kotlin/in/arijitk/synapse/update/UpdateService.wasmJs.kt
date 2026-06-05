package `in`.arijitk.synapse.update

/**
 * Web implementation of UpdateService.
 * Updates are not applicable for web (always served fresh from server).
 */
actual class UpdateService actual constructor() {

    actual suspend fun checkForUpdate(): AppUpdate? = null

    actual suspend fun downloadUpdate(
        update: AppUpdate,
        onStateChange: (DownloadState) -> Unit,
    ) {
        // No-op on web
    }

    actual suspend fun installUpdate(filePath: String) {
        // No-op on web
    }

    actual fun cancelDownload() {
        // No-op on web
    }
}
