package `in`.arijitk.synapse.update

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Singleton download manager that survives dialog dismissal.
 * Tracks download progress, speed, and remaining time.
 * Mirrors Flutter's DownloadManager pattern.
 */
object DownloadManager {

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    var activeUpdate: AppUpdate? = null
        private set

    val isDownloading: Boolean get() = _state.value is DownloadState.Downloading
    val isCompleted: Boolean get() = _state.value is DownloadState.Completed
    val error: String? get() = (_state.value as? DownloadState.Failed)?.error

    private val downloader = PlatformDownloader()
    private var downloadJob: Job? = null

    // Speed & ETA calculation (smoothed, matches Flutter's 70/30 weighting)
    private var speedBps = 0.0
    private var lastProgress = 0f
    private var lastSpeedMark: TimeMark? = null
    private var lastNotifMark: TimeMark? = null

    /**
     * Start downloading [update]. No-op if already downloading the same update.
     */
    fun start(update: AppUpdate) {
        if (downloadJob?.isActive == true && activeUpdate?.tagName == update.tagName) return

        activeUpdate = update
        _state.value = DownloadState.Downloading(0f, 0, update.fileSize)
        speedBps = 0.0
        lastProgress = 0f
        lastSpeedMark = TimeSource.Monotonic.markNow()
        lastNotifMark = TimeSource.Monotonic.markNow()

        downloadJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            downloader.startForegroundNotification(update)
            try {
                downloader.download(update) { downloaded, total ->
                    val progress = if (total > 0) downloaded.toFloat() / total else 0f
                    val speedStr = computeSpeed(progress, total)
                    val remainingStr = computeRemaining(progress, total)

                    _state.value = DownloadState.Downloading(
                        progress = progress,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        speedFormatted = speedStr,
                        remainingFormatted = remainingStr,
                    )

                    throttledNotifUpdate(update, progress, downloaded, total)
                }

                // Download complete → show notification + auto-install
                val filePath = downloader.getCompletedFilePath(update)
                _state.value = DownloadState.Completed(filePath)
                downloader.stopForegroundNotification()
                downloader.showCompletedNotification(update)

                delay(200) // let UI update before install intent
                try {
                    downloader.install(filePath)
                } catch (_: Exception) {
                    // install intent failure is non-fatal
                }
            } catch (e: CancellationException) {
                _state.value = DownloadState.Cancelled
                downloader.stopForegroundNotification()
            } catch (e: Exception) {
                if (e.message?.contains("cancelled", ignoreCase = true) == true) {
                    _state.value = DownloadState.Cancelled
                } else {
                    _state.value = DownloadState.Failed(e.message ?: "Download failed")
                    downloader.showFailedNotification(update)
                }
                downloader.stopForegroundNotification()
            }
        }
    }

    /** Cancel the ongoing download and stop foreground notification. */
    fun cancel() {
        downloadJob?.cancel()
        downloader.cancel()
        downloader.stopForegroundNotification()
        _state.value = DownloadState.Cancelled
    }

    /** Reset state to [DownloadState.Idle]. */
    fun reset() {
        activeUpdate = null
        _state.value = DownloadState.Idle
        speedBps = 0.0
    }

    /** Install the completed APK (from Completed state or existing file). */
    suspend fun installCompleted() {
        val s = _state.value
        if (s is DownloadState.Completed) {
            downloader.install(s.filePath)
        }
    }

    /** Install existing APK if already downloaded. Returns true if installed. */
    suspend fun installExisting(update: AppUpdate): Boolean {
        val path = downloader.getExistingApk(update) ?: return false
        downloader.install(path)
        return true
    }

    /** Check if APK already downloaded and complete. */
    suspend fun getExistingApk(update: AppUpdate): String? =
        downloader.getExistingApk(update)

    // ── Speed & ETA ─────────────────────────────────────────────────────────

    private fun computeSpeed(progress: Float, totalBytes: Long): String {
        val mark = lastSpeedMark ?: return ""
        val dt = mark.elapsedNow().inWholeMilliseconds
        if (dt > 500) {
            val bytesInInterval = (progress - lastProgress) * totalBytes
            val instantSpeed = bytesInInterval / (dt / 1000.0)
            speedBps = if (speedBps == 0.0) instantSpeed else speedBps * 0.7 + instantSpeed * 0.3
            lastProgress = progress
            lastSpeedMark = TimeSource.Monotonic.markNow()
        }
        return formatSpeed(speedBps)
    }

    private fun computeRemaining(progress: Float, totalBytes: Long): String {
        if (speedBps <= 0) return ""
        val remainingBytes = (1f - progress) * totalBytes
        if (remainingBytes <= 0) return ""
        val secondsLeft = (remainingBytes / speedBps).toInt()
        if (secondsLeft <= 0) return ""
        return when {
            secondsLeft >= 3600 -> {
                val h = secondsLeft / 3600
                val m = (secondsLeft % 3600) / 60
                "${h}h ${m}m left"
            }
            secondsLeft >= 60 -> {
                val m = secondsLeft / 60
                val s = secondsLeft % 60
                "${m}m ${s}s left"
            }
            else -> "${secondsLeft}s left"
        }
    }

    private fun formatSpeed(bps: Double): String {
        if (bps <= 0) return ""
        return if (bps >= 1024 * 1024) {
            "${(bps / (1024 * 1024)).fmt1()} MB/s"
        } else {
            "${(bps / 1024).toInt()} KB/s"
        }
    }

    // ── Notification throttle ───────────────────────────────────────────────

    private fun throttledNotifUpdate(
        update: AppUpdate,
        progress: Float,
        downloaded: Long,
        total: Long,
    ) {
        val mark = lastNotifMark ?: return
        if (mark.elapsedNow().inWholeMilliseconds < 500) return
        lastNotifMark = TimeSource.Monotonic.markNow()

        val dlMb = (downloaded / (1024.0 * 1024.0)).fmt1()
        val totalMb = (total / (1024.0 * 1024.0)).fmt1()
        val percent = (progress * 100).roundToInt()
        val remaining = computeRemaining(progress, total)
        val speed = formatSpeed(speedBps)

        val parts = mutableListOf("$dlMb / $totalMb MB \u2014 $percent%")
        if (remaining.isNotEmpty()) parts.add(remaining)
        if (speed.isNotEmpty()) parts.add(speed)

        downloader.updateForegroundNotification(update, progress, parts.joinToString(" \u00B7 "))
    }
}
