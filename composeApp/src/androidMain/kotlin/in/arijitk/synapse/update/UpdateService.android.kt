package `in`.arijitk.synapse.update

import `in`.arijitk.synapse.APP_VERSION
import `in`.arijitk.synapse.isDebug
import `in`.arijitk.synapse.settings.ApplicationContextHolder
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android implementation of UpdateService.
 * Downloads from GitHub Releases and installs APK via content provider.
 */
actual class UpdateService actual constructor() {

    private val repoOwner = "OWNER"  // TODO: Set your GitHub username/org
    private val repoName = "synapse" // TODO: Set your repo name

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private var downloadJob: Job? = null

    private val channel: String
        get() = if (isDebug) "debug" else "release"

    private val currentBuildNumber: Int
        get() {
            if (APP_VERSION == "APP_VERSION_PLACEHOLDER") return 0
            return APP_VERSION.split(".").firstOrNull()?.toIntOrNull() ?: 0
        }

    actual suspend fun checkForUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        val releases: List<GitHubRelease> = client.get(
            "https://api.github.com/repos/$repoOwner/$repoName/releases"
        ).body()

        // Filter by channel tag pattern: v{buildNumber}-{channel}-{sha}
        val channelReleases = releases.filter { release ->
            release.tagName.contains("-$channel-", ignoreCase = true)
        }

        if (channelReleases.isEmpty()) return@withContext null

        val latest = channelReleases.first()
        val latestBuildNumber = extractBuildNumber(latest.tagName)

        if (latestBuildNumber <= currentBuildNumber) return@withContext null

        val apkAsset = latest.assets.firstOrNull { it.name.endsWith(".apk") }
            ?: return@withContext null

        AppUpdate(
            versionName = latest.name ?: latest.tagName,
            buildNumber = latestBuildNumber,
            downloadUrl = apkAsset.browserDownloadUrl,
            fileSize = apkAsset.size,
            changelog = latest.body ?: "",
            tagName = latest.tagName,
            releaseName = latest.name ?: latest.tagName,
        )
    }

    actual suspend fun downloadUpdate(
        update: AppUpdate,
        onStateChange: (DownloadState) -> Unit,
    ) {
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                onStateChange(DownloadState.Downloading(0f, 0, update.fileSize))

                val context = ApplicationContextHolder.context
                val downloadsDir = context.getExternalFilesDir(null)
                    ?: context.filesDir
                val fileName = "synapse-${channel}-${update.buildNumber}.apk"
                val file = File(downloadsDir, fileName)
                val partFile = File(downloadsDir, "$fileName.part")

                // Clean old APKs of same channel
                downloadsDir.listFiles()?.filter {
                    it.name.startsWith("synapse-$channel-") &&
                        it.name.endsWith(".apk") &&
                        it.name != fileName
                }?.forEach { it.delete() }

                // Check if already downloaded
                if (file.exists() && file.length() == update.fileSize) {
                    onStateChange(DownloadState.Completed(file.absolutePath))
                    return@launch
                }

                // Download with resume support
                val existingBytes = if (partFile.exists()) partFile.length() else 0L

                val response: HttpResponse = client.get(update.downloadUrl) {
                    if (existingBytes > 0) {
                        header("Range", "bytes=$existingBytes-")
                    }
                }

                val totalBytes = update.fileSize
                var downloadedBytes = existingBytes
                val startTime = System.currentTimeMillis()

                val channel = response.bodyAsChannel()

                val appendStream = java.io.FileOutputStream(partFile, existingBytes > 0)

                val buffer = ByteArray(8192)
                appendStream.use { output ->
                    while (!channel.isClosedForRead) {
                        if (!isActive) {
                            onStateChange(DownloadState.Cancelled)
                            return@launch
                        }

                        val packet = channel.readAvailable(buffer)
                        if (packet == -1) break
                        if (packet > 0) {
                            output.write(buffer, 0, packet)
                            downloadedBytes += packet

                            val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                            val elapsed = System.currentTimeMillis() - startTime
                            val speed = if (elapsed > 0) {
                                ((downloadedBytes - existingBytes) * 1000L / elapsed)
                            } else 0L

                            onStateChange(
                                DownloadState.Downloading(
                                    progress = progress,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = speed,
                                )
                            )
                        }
                    }
                }

                // Rename part file to final
                partFile.renameTo(file)
                onStateChange(DownloadState.Completed(file.absolutePath))

            } catch (e: CancellationException) {
                onStateChange(DownloadState.Cancelled)
            } catch (e: Exception) {
                onStateChange(DownloadState.Failed(e.message ?: "Download failed"))
            }
        }
    }

    actual suspend fun installUpdate(filePath: String) {
        val context = ApplicationContextHolder.context
        val file = File(filePath)
        if (!file.exists()) return

        // Use FileProvider for API 24+
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    actual fun cancelDownload() {
        downloadJob?.cancel()
    }

    private fun extractBuildNumber(tag: String): Int {
        // Tag format: v{buildNumber}-{channel}-{sha}
        return tag.removePrefix("v")
            .split("-")
            .firstOrNull()
            ?.toIntOrNull() ?: 0
    }
}
