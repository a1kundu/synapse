package `in`.arijitk.synapse.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an available app update from GitHub Releases.
 */
data class AppUpdate(
    val versionName: String,
    val buildNumber: Int,
    val downloadUrl: String,
    val fileSize: Long,
    val changelog: String,
    val tagName: String,
    val releaseName: String,
)

/**
 * Download state for the update process.
 */
sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long = 0,
    ) : DownloadState()
    data class Completed(val filePath: String) : DownloadState()
    data class Failed(val error: String) : DownloadState()
    data object Cancelled : DownloadState()
}

/**
 * GitHub Release API response models.
 */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String,
    val size: Long,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

/**
 * Update service for checking and downloading updates from GitHub Releases.
 * Platform-specific implementations handle the actual download and install.
 */
expect class UpdateService() {
    /**
     * Check GitHub releases for a newer version.
     * Returns null if no update is available.
     */
    suspend fun checkForUpdate(): AppUpdate?

    /**
     * Download the update APK. Only meaningful on Android.
     * Emits download progress via the state flow.
     */
    suspend fun downloadUpdate(update: AppUpdate, onStateChange: (DownloadState) -> Unit)

    /**
     * Install a downloaded APK. Only meaningful on Android.
     */
    suspend fun installUpdate(filePath: String)

    /**
     * Cancel an ongoing download.
     */
    fun cancelDownload()
}
