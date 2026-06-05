package `in`.arijitk.synapse.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an available app update from GitHub Releases.
 */
data class AppUpdate(
    val buildNumber: Int,
    val version: String,
    val tagName: String,
    val downloadUrl: String,
    val fileSize: Long,
    val releaseName: String,
    val changelog: String,
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
        val speedFormatted: String = "",
        val remainingFormatted: String = "",
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

// ---------------------------------------------------------------------------
// Formatting utilities
// ---------------------------------------------------------------------------

/** Format bytes as human-readable string (e.g., "12.5 MB"). */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "${(bytes / (1024.0 * 1024.0)).fmt1()} MB"
    bytes >= 1_000 -> "${(bytes / 1024.0).fmt1()} KB"
    else -> "$bytes B"
}

/** Format a Double to 1 decimal place (multiplatform-safe, no String.format). */
internal fun Double.fmt1(): String {
    val abs = kotlin.math.abs(this)
    val rounded = kotlin.math.round(abs * 10).toLong()
    val intPart = rounded / 10
    val fracPart = rounded % 10
    val sign = if (this < 0) "-" else ""
    return "$sign$intPart.$fracPart"
}
