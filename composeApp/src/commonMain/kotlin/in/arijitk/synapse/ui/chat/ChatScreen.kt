package `in`.arijitk.synapse.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.jetbrains.compose.resources.painterResource
import synapse.composeapp.generated.resources.Res
import synapse.composeapp.generated.resources.app_icon

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
                EmptyState(modelName = viewModel.selectedModel?.displayName)
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

        // MCP tools status
        val mcpToolCount = viewModel.mcpTools.size
        val mcpError = viewModel.mcpError
        val isLoadingMcpTools = viewModel.isLoadingMcpTools

        if (isLoadingMcpTools || mcpToolCount > 0 || mcpError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isLoadingMcpTools) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Text(
                        "Discovering MCP tools…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (mcpError != null && mcpToolCount == 0) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "MCP error: $mcpError",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { viewModel.refreshMcpTools() },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else if (mcpToolCount > 0) {
                    Icon(
                        Icons.Outlined.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "$mcpToolCount MCP tool${if (mcpToolCount > 1) "s" else ""} available",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { viewModel.refreshMcpTools() },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Refresh MCP tools",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
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
    selectedModel: LlmModel?,
    onModelSelected: (LlmModel) -> Unit,
    models: List<LlmModel>,
    isLoading: Boolean = false,
    onRefresh: () -> Unit = {},
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
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = cs.onSurfaceVariant,
                    )
                } else {
                    Image(
                        painter = painterResource(Res.drawable.app_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape),
                    )
                }
                Text(
                    text = selectedModel?.displayName ?: "Select model",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
            // Refresh button at top
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = cs.primary,
                        )
                        Text(
                            text = if (isLoading) "Fetching models..." else "Refresh models",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.primary,
                        )
                    }
                },
                onClick = {
                    onRefresh()
                },
                enabled = !isLoading,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (isLoading && models.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                    onClick = {},
                    enabled = false,
                )
            } else {
                val grouped = models.groupBy { it.provider }
                grouped.forEach { (provider, providerModels) ->
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
                    providerModels.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = model.displayName,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (model.id == selectedModel?.id) {
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
}

// ── Empty State ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(modelName: String?) {
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
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.app_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                )
            }
            Text(
                text = "Start a conversation",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (modelName != null) "Using $modelName" else "Configure your API key in Settings",
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
                Image(
                    painter = painterResource(Res.drawable.app_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape),
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
            modifier = Modifier.widthIn(
                min = 60.dp,
                max = if (isUser) 340.dp else 520.dp,
            ),
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
                    if (isUser || message.isStreaming) {
                        // Plain text for user messages and during streaming.
                        // Using Text() while streaming keeps rendering cheap
                        // so tokens appear incrementally in real time.
                        // Markdown is rendered once streaming completes.
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor,
                        )
                    } else {
                        // Full markdown rendering for completed assistant messages.
                        // If the content contains LaTeX math, split it into
                        // segments and render math via MathBlock.
                        val segments = remember(message.content) {
                            if (containsMath(message.content)) {
                                parseContentSegments(message.content)
                            } else {
                                listOf(ContentSegment.Text(message.content))
                            }
                        }

                        segments.forEach { segment ->
                            when (segment) {
                                is ContentSegment.Math -> {
                                    MathBlock(
                                        latex = segment.latex,
                                        displayMode = segment.displayMode,
                                        textColor = contentColor,
                                    )
                                }
                                is ContentSegment.Text -> {
                                    if (segment.markdown.isNotBlank()) {
                                        AssistantMarkdown(
                                            content = segment.markdown,
                                            contentColor = contentColor,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (!message.isStreaming) {
                    // Fallback for empty non-streaming messages — prevents
                    // the bubble from collapsing to an invisible dot.
                    Text(
                        text = "Empty response",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.5f),
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

// ── Assistant Markdown Renderer ─────────────────────────────────────────────

/**
 * Renders a markdown text segment for assistant messages with syntax highlighting,
 * mermaid diagram support, and themed styling.
 */
@Composable
private fun AssistantMarkdown(
    content: String,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Markdown(
        content = content,
        components = markdownComponents(
            codeFence = { model ->
                val lang = model.node
                    .findChildOfType(MarkdownTokenTypes.FENCE_LANG)
                    ?.getTextInNode(model.content)
                    ?.toString()
                    ?.trim()
                if (lang.equals("mermaid", ignoreCase = true)) {
                    val children = model.node.children
                    if (children.size >= 3) {
                        val start = children[2].startOffset
                        val end = children[(children.size - 2).coerceAtLeast(2)].endOffset
                        val mermaidCode = model.content
                            .subSequence(start, end)
                            .toString()
                            .trim()
                        MermaidDiagram(mermaidCode)
                    }
                } else {
                    MarkdownHighlightedCodeFence(
                        content = model.content,
                        node = model.node,
                    )
                }
            },
            codeBlock = {
                MarkdownHighlightedCodeBlock(
                    content = it.content,
                    node = it.node,
                )
            },
        ),
        colors = markdownColor(
            text = contentColor,
            codeText = contentColor,
            inlineCodeText = contentColor,
            linkText = MaterialTheme.colorScheme.primary,
            codeBackground = contentColor.copy(alpha = 0.08f),
            inlineCodeBackground = contentColor.copy(alpha = 0.08f),
            dividerColor = contentColor.copy(alpha = 0.2f),
        ),
        typography = markdownTypography(
            h1 = MaterialTheme.typography.titleLarge.copy(color = contentColor),
            h2 = MaterialTheme.typography.titleMedium.copy(color = contentColor),
            h3 = MaterialTheme.typography.titleSmall.copy(color = contentColor),
            h4 = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                color = contentColor,
            ),
            h5 = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                color = contentColor,
            ),
            h6 = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                color = contentColor,
            ),
            paragraph = MaterialTheme.typography.bodyMedium.copy(color = contentColor),
            quote = MaterialTheme.typography.bodyMedium.copy(
                color = contentColor.copy(alpha = 0.7f),
            ),
            code = MaterialTheme.typography.bodySmall.copy(color = contentColor),
            list = MaterialTheme.typography.bodyMedium.copy(color = contentColor),
            ordered = MaterialTheme.typography.bodyMedium.copy(color = contentColor),
            bullet = MaterialTheme.typography.bodyMedium.copy(color = contentColor),
        ),
    )
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
