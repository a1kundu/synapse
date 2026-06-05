package `in`.arijitk.synapse.ui.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Minimize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import `in`.arijitk.synapse.update.AppUpdate
import `in`.arijitk.synapse.update.DownloadManager
import `in`.arijitk.synapse.update.DownloadState
import `in`.arijitk.synapse.update.fmt1
import kotlinx.coroutines.delay

/**
 * Download progress dialog matching the Flutter implementation.
 * Shows progress bar, MB downloaded/total, percentage, speed, ETA, changelog.
 * Error state with Retry. Cancel and Background buttons.
 */
@Composable
fun DownloadProgressDialog(
    update: AppUpdate,
    onDismiss: () -> Unit,
) {
    val dm = DownloadManager
    val state by dm.state.collectAsState()

    // Start download if needed
    LaunchedEffect(update) {
        when {
            dm.isDownloading && dm.activeUpdate?.tagName == update.tagName -> {
                // Reattach to existing download — just observe
            }
            dm.isCompleted -> {
                // Already done, dismiss after brief delay
                delay(300)
                onDismiss()
            }
            dm.error != null -> {
                // Error state — will be shown
            }
            else -> {
                // Start new download
                dm.reset()
                dm.start(update)
            }
        }
    }

    // Auto-dismiss on completion
    LaunchedEffect(state) {
        if (state is DownloadState.Completed) {
            delay(300)
            onDismiss()
        }
        if (state is DownloadState.Cancelled) {
            onDismiss()
        }
    }

    when (val currentState = state) {
        is DownloadState.Failed -> ErrorDialog(
            error = currentState.error,
            onClose = onDismiss,
            onRetry = {
                dm.reset()
                dm.start(update)
            },
        )

        is DownloadState.Downloading -> ProgressDialog(
            update = update,
            state = currentState,
            onCancel = {
                dm.cancel()
                onDismiss()
            },
            onBackground = onDismiss,
        )

        is DownloadState.Idle, is DownloadState.Cancelled -> {
            // Briefly shown while download starts
            ProgressDialog(
                update = update,
                state = DownloadState.Downloading(0f, 0, update.fileSize),
                onCancel = {
                    dm.cancel()
                    onDismiss()
                },
                onBackground = onDismiss,
            )
        }

        is DownloadState.Completed -> {
            // Brief flash before auto-dismiss
        }
    }
}

@Composable
private fun ProgressDialog(
    update: AppUpdate,
    state: DownloadState.Downloading,
    onCancel: () -> Unit,
    onBackground: () -> Unit,
) {
    val p = state.progress
    val totalMb = (state.totalBytes / (1024.0 * 1024.0)).fmt1()
    val dlMb = (state.downloadedBytes / (1024.0 * 1024.0)).fmt1()
    val percent = (p * 100).toInt().coerceIn(0, 100)

    AlertDialog(
        onDismissRequest = onBackground,
        title = { Text("Downloading Update v${update.version}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .widthIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Progress bar: indeterminate when 0%, determinate otherwise
                if (p > 0f) {
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("$dlMb / $totalMb MB")
                Spacer(Modifier.height(4.dp))
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))

                // Remaining time + speed
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.remainingFormatted.isNotEmpty()) {
                        Text(
                            state.remainingFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (state.remainingFormatted.isNotEmpty() && state.speedFormatted.isNotEmpty()) {
                        Text(
                            " \u00B7 ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (state.speedFormatted.isNotEmpty()) {
                        Text(
                            state.speedFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }

                // Changelog section
                if (update.changelog.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "What's New",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.align(Alignment.Start),
                    )
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        update.changelog,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.Start),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Icon(
                    Icons.Outlined.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = onBackground) {
                Icon(
                    Icons.Outlined.Minimize,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Background")
            }
        },
    )
}

@Composable
private fun ErrorDialog(
    error: String,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        icon = {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        },
        title = { Text("Download Failed") },
        text = { Text(error) },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Close")
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onRetry) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Retry")
            }
        },
    )
}
