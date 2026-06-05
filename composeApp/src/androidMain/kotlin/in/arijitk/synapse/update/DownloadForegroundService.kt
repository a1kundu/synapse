package `in`.arijitk.synapse.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Minimal foreground service that keeps the process alive during APK download.
 * The actual download runs in [DownloadManager]'s coroutine; this service only
 * provides the required foreground notification so Android won't kill the process.
 */
class DownloadForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "download_progress"
        const val CHANNEL_NAME = "Download Progress"
        const val NOTIFICATION_ID = 42
        const val ACTION_CANCEL = "in.arijitk.synapse.CANCEL_DOWNLOAD"
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                DownloadManager.cancel()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val version = intent?.getStringExtra("update_version") ?: ""
        val notification = buildInitialNotification(version)

        // Start foreground with DATA_SYNC type for Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
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

    private fun buildInitialNotification(version: String): Notification {
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().apply { setClassName(this@DownloadForegroundService, "$packageName.MainActivity") }
        contentIntent.action = "in.arijitk.synapse.SHOW_DOWNLOAD_DIALOG"
        contentIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP

        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val cancelIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading update${if (version.isNotEmpty()) " v$version" else ""}")
            .setContentText("Starting download\u2026")
            .setSmallIcon(`in`.arijitk.synapse.R.mipmap.ic_launcher_monochrome)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, true) // indeterminate
            .setContentIntent(pendingIntent)
            .addAction(0, "Cancel", cancelPendingIntent)
            .setColor(0xFF673AB7.toInt()) // deepPurple
            .build()
    }
}
