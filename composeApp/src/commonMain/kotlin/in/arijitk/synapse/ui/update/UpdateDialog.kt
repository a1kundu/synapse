package `in`.arijitk.synapse.ui.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.arijitk.synapse.update.AppUpdate
import `in`.arijitk.synapse.update.DownloadManager
import `in`.arijitk.synapse.update.UpdateChecker
import `in`.arijitk.synapse.update.fmt1
import kotlinx.coroutines.launch

/**
 * Update-available dialog matching the Flutter implementation.
 * Shows release info, changelog, "Don't remind me" checkbox, Later/Download buttons.
 */
@Composable
fun UpdateDialog(
    update: AppUpdate,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onDisableAutoUpdate: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var dontRemind by remember { mutableStateOf(false) }
    var alreadyDownloaded by remember { mutableStateOf(false) }
    var existingApkPath by remember { mutableStateOf<String?>(null) }

    // Check for existing APK
    LaunchedEffect(update) {
        val path = DownloadManager.getExistingApk(update)
        existingApkPath = path
        alreadyDownloaded = path != null
    }

    val sizeMb = (update.fileSize / (1024.0 * 1024.0)).fmt1()

    AlertDialog(
        onDismissRequest = {
            if (dontRemind) onDisableAutoUpdate()
            onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        },
        title = { Text("Update Available") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .widthIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(update.releaseName)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Version: ${update.version}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Size: $sizeMb MB",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Channel: ${UpdateChecker.channel}",
                    style = MaterialTheme.typography.bodySmall,
                )

                if (alreadyDownloaded) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "APK already downloaded \u2014 ready to install.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (update.changelog.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "What's New",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        update.changelog,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(12.dp))

                // "Don't remind me again" checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = dontRemind,
                        onCheckedChange = { dontRemind = it },
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Don't remind me again",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { dontRemind = !dontRemind },
                    )
                }

                if (dontRemind) {
                    Padding(start = 32.dp, top = 2.dp) {
                        Text(
                            "You can re-enable this in Settings \u2192 Updates.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (dontRemind) onDisableAutoUpdate()
                onDismiss()
            }) {
                Text("Later")
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                if (alreadyDownloaded && existingApkPath != null) {
                    // Install existing APK
                    coroutineScope.launch {
                        DownloadManager.installExisting(update)
                    }
                    onDismiss()
                } else {
                    onDownload()
                }
            }) {
                Icon(
                    imageVector = if (alreadyDownloaded) Icons.Default.InstallMobile
                    else Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (alreadyDownloaded) "Install" else "Download & Install")
            }
        },
    )
}

@Composable
private fun Padding(
    start: androidx.compose.ui.unit.Dp = 0.dp,
    top: androidx.compose.ui.unit.Dp = 0.dp,
    end: androidx.compose.ui.unit.Dp = 0.dp,
    bottom: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.padding(start = start, top = top, end = end, bottom = bottom)) {
        content()
    }
}
