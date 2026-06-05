package `in`.arijitk.synapse.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Main Chat screen composable.
 * Displays a conversation between the user and an LLM with model selection and file attachments.
 */
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel { ChatViewModel() },
) {
    val messages = viewModel.messages
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive or content updates
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier) {
        // Messages list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (messages.isEmpty()) {
                EmptyState(modelName = viewModel.selectedModel.displayName)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(
                        items = messages,
                        key = { it.id },
                    ) { message ->
                        MessageBubble(message = message)
                    }
                }
            }
        }

        // Pending attachments preview
        AnimatedVisibility(
            visible = viewModel.pendingAttachments.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            AttachmentPreviewBar(
                attachments = viewModel.pendingAttachments,
                onRemove = viewModel::removeAttachment,
            )
        }

        // Input bar
        ChatInputBar(
            text = viewModel.inputText,
            onTextChange = viewModel::onInputTextChange,
            onSend = { viewModel.sendMessage(viewModel.inputText) },
            onAttach = {
                // Simulate file pick with a dummy attachment
                viewModel.addAttachment(
                    ChatAttachment(
                        fileName = "document_${viewModel.pendingAttachments.size + 1}.pdf",
                        fileSizeBytes = (1024L * (50..5000).random()),
                        mimeType = "application/pdf",
                    )
                )
            },
            isGenerating = viewModel.isGenerating,
            onClearChat = viewModel::clearConversation,
            hasMessages = messages.isNotEmpty(),
        )
    }
}

// ── Model Selector Dropdown ────────────────────────────────────────────────

@Composable
internal fun ModelSelectorChip(
    selectedModel: LlmModel,
    onModelSelected: (LlmModel) -> Unit,
) {
    var showModelDropdown by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Box {
        Surface(
            onClick = { showModelDropdown = true },
            shape = RoundedCornerShape(16.dp),
            color = cs.surfaceVariant.copy(alpha = 0.6f),
            contentColor = cs.onSurfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Outlined.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = selectedModel.displayName,
                    style = MaterialTheme.typography.labelMedium,
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        DropdownMenu(
            expanded = showModelDropdown,
            onDismissRequest = { showModelDropdown = false },
        ) {
            val grouped = AvailableModels.models.groupBy { it.provider }
            grouped.forEach { (provider, models) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = provider,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary,
                        )
                    },
                    onClick = {},
                    enabled = false,
                )
                models.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = model.displayName,
                                    modifier = Modifier.weight(1f),
                                )
                                if (model.id == selectedModel.id) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = cs.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        onClick = {
                            onModelSelected(model)
                            showModelDropdown = false
                        },
                    )
                }
                if (provider != grouped.keys.last()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

// ── Empty State ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(modelName: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = "Start a conversation",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Using $modelName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Type a message below or attach a file to get started",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Message Bubble ──────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val cs = MaterialTheme.colorScheme

    val bubbleColor = if (isUser) {
        cs.primaryContainer
    } else {
        cs.surfaceVariant
    }
    val contentColor = if (isUser) {
        cs.onPrimaryContainer
    } else {
        cs.onSurfaceVariant
    }
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = alignment,
    ) {
        // Role label + model badge for assistant
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            if (!isUser) {
                Icon(
                    Icons.Outlined.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = cs.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = message.model?.displayName ?: "Assistant",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                )
            } else {
                Text(
                    text = "You",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }

        // Message bubble
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Attachments
                if (message.attachments.isNotEmpty()) {
                    message.attachments.forEach { attachment ->
                        AttachmentChip(attachment = attachment, tint = contentColor)
                    }
                    if (message.content.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // Text content
                if (message.content.isNotEmpty()) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                    )
                }

                // Streaming indicator
                if (message.isStreaming) {
                    Spacer(Modifier.height(4.dp))
                    StreamingIndicator(tint = contentColor)
                }
            }
        }
    }
}

// ── Attachment Chip (inside message) ────────────────────────────────────────

@Composable
private fun AttachmentChip(
    attachment: ChatAttachment,
    tint: Color,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.08f),
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = tint.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = attachment.displaySize,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = tint.copy(alpha = 0.6f),
                )
            }
        }
    }
}

// ── Streaming Indicator ─────────────────────────────────────────────────────

@Composable
private fun StreamingIndicator(tint: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorBlink",
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 2.dp, height = 14.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(tint.copy(alpha = alpha)),
        )
    }
}

// ── Attachment Preview Bar ──────────────────────────────────────────────────

@Composable
private fun AttachmentPreviewBar(
    attachments: List<ChatAttachment>,
    onRemove: (Int) -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(attachments) { index, attachment ->
                InputChip(
                    selected = false,
                    onClick = {},
                    label = {
                        Text(
                            text = attachment.fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Outlined.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onRemove(index) },
                        )
                    },
                )
            }
        }
    }
}

// ── Chat Input Bar ──────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    isGenerating: Boolean,
    onClearChat: () -> Unit,
    hasMessages: Boolean,
) {
    val cs = MaterialTheme.colorScheme

    Surface(
        color = cs.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Text field with border
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cs.surface,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = cs.outline.copy(alpha = 0.3f),
                ),
            ) {
                Column {
                    // Text input area
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp, max = 150.dp),
                        placeholder = {
                            Text(
                                text = if (isGenerating) "Waiting for response..." else "Type a message...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        },
                        enabled = !isGenerating,
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        keyboardActions = KeyboardActions(
                            onSend = { if (text.isNotBlank() && !isGenerating) onSend() },
                        ),
                        minLines = 1,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = cs.onSurface,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Bottom toolbar: attach, clear, send
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Attach button
                IconButton(
                    onClick = onAttach,
                    enabled = !isGenerating,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Outlined.AttachFile,
                        contentDescription = "Attach file",
                        modifier = Modifier.size(20.dp),
                        tint = if (isGenerating) cs.onSurfaceVariant.copy(alpha = 0.4f) else cs.onSurfaceVariant,
                    )
                }

                // Clear chat button
                if (hasMessages) {
                    IconButton(
                        onClick = onClearChat,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Outlined.DeleteSweep,
                            contentDescription = "Clear chat",
                            modifier = Modifier.size(20.dp),
                            tint = cs.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Send button
                FilledIconButton(
                    onClick = onSend,
                    enabled = text.isNotBlank() && !isGenerating,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = cs.primary,
                        contentColor = cs.onPrimary,
                        disabledContainerColor = cs.onSurface.copy(alpha = 0.12f),
                        disabledContentColor = cs.onSurface.copy(alpha = 0.38f),
                    ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
