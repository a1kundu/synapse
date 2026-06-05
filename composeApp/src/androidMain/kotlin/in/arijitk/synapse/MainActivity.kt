package `in`.arijitk.synapse

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import `in`.arijitk.synapse.settings.ApplicationContextHolder
import `in`.arijitk.synapse.update.DownloadManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // No special handling needed — foreground service notification always shows;
            // this permission only affects completed/failed notifications on Android 13+
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApplicationContextHolder.initialize(applicationContext)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Handle intent from notification tap (cold start)
        handleIntent(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle intent from notification tap (app already running)
        handleIntent(intent)
    }

    /**
     * Process notification-triggered intents:
     * - SHOW_DOWNLOAD_DIALOG → reopen the download progress dialog
     * - INSTALL_UPDATE → install the completed APK
     *
     * If the app was killed (DownloadManager.activeUpdate is null), we attempt
     * to restore from persisted JSON in SharedPreferences.
     */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SHOW_DOWNLOAD_DIALOG -> {
                val update = DownloadManager.activeUpdate
                    ?: DownloadManager.restoreActiveUpdate()
                if (update != null) {
                    UpdateDialogController.pendingUpdate = update
                    UpdateDialogController.showDownloadDialog = true
                }
            }
            ACTION_INSTALL_UPDATE -> {
                val update = DownloadManager.activeUpdate
                    ?: DownloadManager.restoreActiveUpdate()
                if (update != null) {
                    UpdateDialogController.pendingUpdate = update
                    lifecycleScope.launch {
                        DownloadManager.installCompleted()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_SHOW_DOWNLOAD_DIALOG = "in.arijitk.synapse.SHOW_DOWNLOAD_DIALOG"
        const val ACTION_INSTALL_UPDATE = "in.arijitk.synapse.INSTALL_UPDATE"
    }
}
