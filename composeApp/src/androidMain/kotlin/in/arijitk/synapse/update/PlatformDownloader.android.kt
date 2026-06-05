@file:Suppress("TooManyFunctions")

package `in`.arijitk.synapse.update

import `in`.arijitk.synapse.settings.ApplicationContextHolder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Android implementation of [PlatformDownloader].
 * Downloads APK with HTTP Range resume and exponential backoff retry.
 * Manages foreground service notification for background download survival.
 */
actual class PlatformDownloader actual constructor() {

    private val client = HttpClient(OkHttp)

    @Volatile
    private var cancelled = false

    companion object {
        private const val CHANNEL_ID = "download_progress"
        private const val CHANNEL_NAME = "Download Progress"
        private const val NOTIFICATION_ID = 42
        private const val MAX_RETRIES = 5
    }

    // ── Download ────────────────────────────────────────────────────────────

    actual suspend fun download(
        update: AppUpdate,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        cancelled = false

        val context = ApplicationContextHolder.context
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val channel = UpdateChecker.channel
        val fileName = "synapse-$channel-${update.buildNumber}.apk"
        val file = File(dir, fileName)
        val partFile = File(dir, "$fileName.part")

        // Clean old APKs of the same channel
        dir.listFiles()?.filter {
            it.name.startsWith("synapse-$channel-") &&
                it.name.endsWith(".apk") &&
                it.name != fileName
        }?.forEach { it.delete() }

        // Already fully downloaded?
        if (file.exists() && file.length() == update.fileSize) {
            onProgress(update.fileSize, update.fileSize)
            return@withContext
        }

        val totalBytes = update.fileSize
        var attempt = 0

        while (true) {
            if (cancelled) throw Exception("Download cancelled")
            if (!coroutineContext.isActive) throw Exception("Download cancelled")

            try {
                var alreadyReceived = if (partFile.exists()) partFile.length() else 0L
                if (totalBytes > 0 && alreadyReceived >= totalBytes) {
                    partFile.delete()
                    alreadyReceived = 0
                }

                val response: HttpResponse = client.get(update.downloadUrl) {
                    if (alreadyReceived > 0) {
                        header("Range", "bytes=$alreadyReceived-")
                    }
                }

                // Server ignored Range header → restart from scratch
                if (response.status.value == 200 && alreadyReceived > 0) {
                    partFile.delete()
                    alreadyReceived = 0
                }

                if (response.status.value !in listOf(200, 206)) {
                    throw Exception("Download failed: HTTP ${response.status.value}")
                }

                val bodyChannel = response.bodyAsChannel()
                val output = FileOutputStream(partFile, alreadyReceived > 0)
                val buffer = ByteArray(8192)
                var received = alreadyReceived

                output.use { out ->
                    while (!bodyChannel.isClosedForRead) {
                        if (cancelled || !coroutineContext.isActive) {
                            throw Exception("Download cancelled")
                        }
                        val read = bodyChannel.readAvailable(buffer)
                        if (read == -1) break
                        if (read > 0) {
                            out.write(buffer, 0, read)
                            received += read
                            onProgress(received, totalBytes)
                        }
                    }
                }

                // Verify completeness
                if (totalBytes > 0 && received < totalBytes) {
                    throw Exception("Incomplete download ($received / $totalBytes bytes)")
                }
                val partLen = partFile.length()
                if (totalBytes > 0 && partLen != totalBytes) {
                    partFile.delete()
                    throw Exception("File verification failed (disk: $partLen, expected: $totalBytes)")
                }

                // Rename .part → final APK
                partFile.renameTo(file)
                onProgress(totalBytes, totalBytes)
                return@withContext
            } catch (e: Exception) {
                if (e.message?.contains("cancelled", ignoreCase = true) == true) throw e

                attempt++
                if (attempt >= MAX_RETRIES) {
                    try { if (partFile.exists()) partFile.delete() } catch (_: Exception) {}
                    throw e
                }

                // Exponential backoff: 2s, 4s, 8s, 16s, 32s
                val delaySec = 1L shl attempt
                delay(delaySec * 1000)
            }
        }
    }

    // ── Install ─────────────────────────────────────────────────────────────

    actual suspend fun install(filePath: String) = withContext(Dispatchers.Main) {
        val context = ApplicationContextHolder.context
        val file = File(filePath)
        if (!file.exists()) return@withContext

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    // ── Cancel ──────────────────────────────────────────────────────────────

    actual fun cancel() {
        cancelled = true
    }

    // ── Existing APK check ──────────────────────────────────────────────────

    actual suspend fun getExistingApk(update: AppUpdate): String? =
        withContext(Dispatchers.IO) {
            val context = ApplicationContextHolder.context
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(dir, apkFileName(update))
            if (file.exists()) {
                val len = file.length()
                if (update.fileSize > 0 && len == update.fileSize) return@withContext file.absolutePath
                if (update.fileSize <= 0 && len > 0) return@withContext file.absolutePath
                file.delete()
            }
            null
        }

    actual fun getCompletedFilePath(update: AppUpdate): String {
        val context = ApplicationContextHolder.context
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, apkFileName(update)).absolutePath
    }

    private fun apkFileName(update: AppUpdate): String =
        "synapse-${UpdateChecker.channel}-${update.buildNumber}.apk"

    // ── Notifications ───────────────────────────────────────────────────────

    actual fun startForegroundNotification(update: AppUpdate) {
        try {
            val context = ApplicationContextHolder.context
            ensureNotificationChannel()

            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                putExtra("update_version", update.version)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {
            // Foreground service may fail on some devices; download still continues
        }
    }

    actual fun updateForegroundNotification(
        update: AppUpdate,
        progress: Float,
        text: String,
    ) {
        try {
            val context = ApplicationContextHolder.context
            val percent = (progress * 100).toInt().coerceIn(0, 100)

            val contentIntent = createOpenAppIntent(context, "SHOW_DOWNLOAD_DIALOG")
            val cancelIntent = createServiceIntent(context, DownloadForegroundService.ACTION_CANCEL)

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Downloading update v${update.version}")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, percent, percent == 0)
                .setContentIntent(contentIntent)
                .addAction(0, "Cancel", cancelIntent)
                .setColor(0xFF673AB7.toInt()) // deepPurple
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    actual fun stopForegroundNotification() {
        try {
            val context = ApplicationContextHolder.context
            context.stopService(Intent(context, DownloadForegroundService::class.java))
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    actual fun showCompletedNotification(update: AppUpdate) {
        try {
            val context = ApplicationContextHolder.context
            ensureNotificationChannel()

            val contentIntent = createOpenAppIntent(context, "INSTALL_UPDATE")

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Update ready")
                .setContentText("v${update.version} downloaded \u2014 tap to install")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setColor(0xFF673AB7.toInt())
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    actual fun showFailedNotification(update: AppUpdate) {
        try {
            val context = ApplicationContextHolder.context
            ensureNotificationChannel()

            val contentIntent = createOpenAppIntent(context, "SHOW_DOWNLOAD_DIALOG")

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Download failed")
                .setContentText("v${update.version} \u2014 open app to retry")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setColor(0xFF673AB7.toInt())
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    // ── Notification helpers ────────────────────────────────────────────────

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val context = ApplicationContextHolder.context
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shows download progress for app updates"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun createOpenAppIntent(context: android.content.Context, action: String): PendingIntent {
        // Resolve the launcher activity dynamically
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().apply { setClassName(context, "${context.packageName}.MainActivity") }
        intent.action = "in.arijitk.synapse.$action"
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP

        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createServiceIntent(
        context: android.content.Context,
        action: String,
    ): PendingIntent {
        val intent = Intent(context, DownloadForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
