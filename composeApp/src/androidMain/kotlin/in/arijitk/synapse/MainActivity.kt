package `in`.arijitk.synapse

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import `in`.arijitk.synapse.settings.ApplicationContextHolder
import `in`.arijitk.synapse.update.DownloadManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApplicationContextHolder.initialize(applicationContext)
        enableEdgeToEdge()

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
     */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SHOW_DOWNLOAD_DIALOG -> {
                val update = DownloadManager.activeUpdate
                if (update != null) {
                    UpdateDialogController.pendingUpdate = update
                    UpdateDialogController.showDownloadDialog = true
                }
            }
            ACTION_INSTALL_UPDATE -> {
                val update = DownloadManager.activeUpdate
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
