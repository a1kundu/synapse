package `in`.arijitk.synapse.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.arijitk.synapse.llm.ChatRequestMessage
import `in`.arijitk.synapse.llm.LlmApiClient
import `in`.arijitk.synapse.llm.OpenAiFunction
import `in`.arijitk.synapse.llm.OpenAiTool
import `in`.arijitk.synapse.llm.ToolCallFunctionInfo
import `in`.arijitk.synapse.llm.ToolCallInfo
import `in`.arijitk.synapse.mcp.McpClient
import `in`.arijitk.synapse.mcp.McpServerTool
import `in`.arijitk.synapse.settings.SettingsRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random

/**
 * ViewModel for the Chat screen.
 * Manages messages, model selection, and attachments.
 */
class ChatViewModel : ViewModel() {

    /** Simple monotonic counter for ordering messages (UI-thread only). */
    private var messageCounter = 0L

    private val apiClient = LlmApiClient()

    /** All messages in the current conversation. */
    val messages = mutableStateListOf<ChatMessage>()

    /** Currently selected LLM model. */
    var selectedModel by mutableStateOf<LlmModel?>(null)
        private set

    /** Dynamically fetched models list. */
    val availableModels = mutableStateListOf<LlmModel>()

    /** Whether models are being fetched. */
    var isLoadingModels by mutableStateOf(false)
        private set

    /** Error from last model fetch attempt. */
    var modelFetchError by mutableStateOf<String?>(null)
        private set

    /** Pending file attachments for the next message. */
    val pendingAttachments = mutableStateListOf<ChatAttachment>()

    /** Whether the assistant is currently generating a response. */
    var isGenerating by mutableStateOf(false)
        private set

    /** Current text in the input field (managed here for clearing on send). */
    var inputText by mutableStateOf("")

    // ── MCP tools ───────────────────────────────────────────────────────────

    private val mcpClient = McpClient()
    private val mcpJson = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Discovered MCP tools from all configured servers. */
    val mcpTools = mutableStateListOf<McpServerTool>()

    /** Whether MCP tools are being loaded. */
    var isLoadingMcpTools by mutableStateOf(false)
        private set

    /** Error from last MCP tool discovery. */
    var mcpError by mutableStateOf<String?>(null)
        private set

    init {
        // Auto-fetch models if API key is configured
        refreshModels()
        // Discover MCP tools from configured servers
        refreshMcpTools()
    }

    fun onInputTextChange(text: String) {
        inputText = text
    }

    fun selectModel(model: LlmModel) {
        selectedModel = model
        SettingsRepository.instance.lastSelectedModelId = model.id
    }

    /**
     * Fetch models from the configured provider.
     */
    fun refreshModels() {
        val settings = SettingsRepository.instance
        if (!settings.isLlmConfigured) return

        isLoadingModels = true
        modelFetchError = null

        viewModelScope.launch {
            val result = apiClient.fetchModels()
            result.onSuccess { models ->
                if (models.isNotEmpty()) {
                    availableModels.clear()
                    availableModels.addAll(models)
                    // Restore last selected model, or pick first
                    val lastId = settings.lastSelectedModelId
                    val restored = if (lastId.isNotBlank()) models.find { it.id == lastId } else null
                    if (selectedModel == null || models.none { it.id == selectedModel?.id }) {
                        selectedModel = restored ?: models.first()
                    }
                }
                modelFetchError = null
            }.onFailure { error ->
                modelFetchError = error.message
            }
            isLoadingModels = false
        }
    }

    fun addAttachment(attachment: ChatAttachment) {
        pendingAttachments.add(attachment)
    }

    /**
     * Refresh MCP tools from all configured servers.
     */
    fun refreshMcpTools() {
        val servers = SettingsRepository.instance.mcpServers
        if (servers.isEmpty()) {
            mcpTools.clear()
            mcpError = null
            return
        }

        isLoadingMcpTools = true
        mcpError = null
        viewModelScope.launch {
            val allTools = mutableListOf<McpServerTool>()
            val errors = mutableListOf<String>()
            for (server in servers) {
                mcpClient.discoverTools(server)
                    .onSuccess { tools ->
                        tools.forEach { tool ->
                            allTools.add(McpServerTool(server.name, server, tool))
                        }
                    }
                    .onFailure { e ->
                        errors.add("${server.name}: ${e.message}")
                    }
            }
            mcpTools.clear()
            mcpTools.addAll(allTools)
            mcpError = if (errors.isNotEmpty()) errors.joinToString("; ") else null
            isLoadingMcpTools = false
        }
    }

    fun removeAttachment(index: Int) {
        if (index in pendingAttachments.indices) {
            pendingAttachments.removeAt(index)
        }
    }

    fun clearAttachments() {
        pendingAttachments.clear()
    }

    /**
     * Send a user message and get an LLM response.
     */
    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && pendingAttachments.isEmpty()) return

        val userMessage = ChatMessage(
            id = generateId(),
            role = MessageRole.USER,
            content = trimmed,
            timestamp = nextTimestamp(),
            attachments = pendingAttachments.toList(),
        )
        messages.add(userMessage)
        pendingAttachments.clear()
        inputText = ""

        // Re-discover MCP tools in case servers were added/removed in settings
        val servers = SettingsRepository.instance.mcpServers
        if (servers.size != mcpTools.map { it.serverName }.distinct().size ||
            servers.any { s -> mcpTools.none { it.serverName == s.name } }
        ) {
            // Servers changed — refresh synchronously before generating
            viewModelScope.launch {
                refreshMcpToolsSync()
                generateResponse()
            }
            return
        }

        generateResponse()
    }

    /**
     * Synchronous version of MCP tool refresh (suspending, not fire-and-forget).
     */
    private suspend fun refreshMcpToolsSync() {
        val servers = SettingsRepository.instance.mcpServers
        if (servers.isEmpty()) {
            mcpTools.clear()
            mcpError = null
            return
        }

        isLoadingMcpTools = true
        mcpError = null
        val allTools = mutableListOf<McpServerTool>()
        val errors = mutableListOf<String>()
        for (server in servers) {
            mcpClient.discoverTools(server)
                .onSuccess { tools ->
                    tools.forEach { tool ->
                        allTools.add(McpServerTool(server.name, server, tool))
                    }
                }
                .onFailure { e ->
                    errors.add("${server.name}: ${e.message}")
                }
        }
        mcpTools.clear()
        mcpTools.addAll(allTools)
        mcpError = if (errors.isNotEmpty()) errors.joinToString("; ") else null
        isLoadingMcpTools = false
    }

    private fun generateResponse() {
        val settings = SettingsRepository.instance

        if (!settings.isLlmConfigured) {
            val assistantId = generateId()
            messages.add(
                ChatMessage(
                    id = assistantId,
                    role = MessageRole.ASSISTANT,
                    content = "⚠️ API key not configured. Go to Settings → LLM Provider to set your API key.",
                    timestamp = nextTimestamp(),
                    model = selectedModel,
                    isStreaming = false,
                )
            )
            return
        }

        isGenerating = true

        val currentModel = selectedModel
        val assistantId = generateId()
        val streamingMessage = ChatMessage(
            id = assistantId,
            role = MessageRole.ASSISTANT,
            content = "",
            timestamp = nextTimestamp(),
            model = currentModel,
            isStreaming = true,
        )
        messages.add(streamingMessage)

        viewModelScope.launch {
            try {
                if (currentModel == null) {
                    updateMessage(assistantId, "⚠️ No model selected. Fetch models first.", streaming = false)
                    isGenerating = false
                    return@launch
                }

                val tools = mcpTools.toList()
                val hasTools = tools.isNotEmpty()

                // Build conversation history with system prompt
                val conversationHistory = buildConversationHistory(assistantId, tools)

                if (hasTools) {
                    // Use non-streaming for tool-calling round
                    val openAiTools = tools.map { serverTool ->
                        OpenAiTool(
                            function = OpenAiFunction(
                                name = serverTool.tool.name,
                                description = serverTool.tool.description ?: "",
                                parameters = serverTool.tool.inputSchema,
                            )
                        )
                    }

                    val result = apiClient.chatCompletionFull(
                        model = currentModel,
                        conversationHistory = conversationHistory,
                        tools = openAiTools,
                    )

                    result.onSuccess { response ->
                        val choice = response.choices.firstOrNull()
                        val toolCalls = choice?.message?.toolCalls

                        if (!toolCalls.isNullOrEmpty()) {
                            // Execute tool calls and continue
                            handleToolCalls(assistantId, currentModel, conversationHistory, choice.message!!, toolCalls, tools)
                        } else {
                            // No tool calls — display the text response
                            val content = choice?.message?.content ?: "No response received."
                            updateMessage(assistantId, content, streaming = false)
                        }
                    }.onFailure { error ->
                        updateMessage(assistantId, "⚠️ Error: ${error.message}", streaming = false)
                    }
                } else {
                    // No MCP tools — stream directly (existing behavior)
                    streamResponse(assistantId, currentModel, conversationHistory)
                }
            } catch (e: Exception) {
                val idx = messages.indexOfFirst { it.id == assistantId }
                if (idx >= 0) {
                    val current = messages[idx].content
                    messages[idx] = messages[idx].copy(
                        content = if (current.isEmpty()) "⚠️ Error: ${e.message}" else current,
                    )
                }
            } finally {
                val idx = messages.indexOfFirst { it.id == assistantId }
                if (idx >= 0) {
                    messages[idx] = messages[idx].copy(isStreaming = false)
                }
                isGenerating = false
            }
        }
    }

    /**
     * Build conversation history including system prompt with MCP tools.
     */
    private fun buildConversationHistory(
        excludeId: String,
        tools: List<McpServerTool>,
    ): List<ChatRequestMessage> {
        val history = mutableListOf<ChatRequestMessage>()

        // System prompt with MCP tool descriptions
        if (tools.isNotEmpty()) {
            val toolDescriptions = tools.joinToString("\n\n") { serverTool ->
                val schema = serverTool.tool.inputSchema?.toString() ?: "{}"
                "- **${serverTool.tool.name}** (server: ${serverTool.serverName}): " +
                    "${serverTool.tool.description ?: "No description"}\n" +
                    "  Input schema: $schema"
            }

            history.add(
                ChatRequestMessage(
                    role = "system",
                    content = """You have access to the following tools via MCP (Model Context Protocol) servers. Use them when appropriate to help the user.

Available tools:
$toolDescriptions

When you need to use a tool, the system will automatically invoke it for you via function calling.""",
                )
            )
        }

        // Add conversation messages
        history.addAll(
            messages
                .filter { it.id != excludeId }
                .map { msg ->
                    ChatRequestMessage(
                        role = when (msg.role) {
                            MessageRole.USER -> "user"
                            MessageRole.ASSISTANT -> "assistant"
                        },
                        content = msg.content,
                    )
                }
        )

        return history
    }

    /**
     * Execute MCP tool calls and continue the conversation.
     */
    private suspend fun handleToolCalls(
        assistantId: String,
        model: LlmModel,
        originalHistory: List<ChatRequestMessage>,
        assistantMessage: `in`.arijitk.synapse.llm.ResponseMessage,
        toolCalls: List<ToolCallInfo>,
        tools: List<McpServerTool>,
    ) {
        // Show the tool calling status
        val toolNames = toolCalls.mapNotNull { it.function.name.takeIf { n -> n.isNotBlank() } }
        updateMessage(assistantId, "🔧 Calling tools: ${toolNames.joinToString(", ")}...", streaming = true)

        // Build the extended conversation with tool results
        val extendedHistory = originalHistory.toMutableList()

        // Add the assistant's tool call message
        extendedHistory.add(
            ChatRequestMessage(
                role = "assistant",
                content = assistantMessage.content,
                toolCalls = toolCalls,
            )
        )

        // Execute each tool call and add results
        for (call in toolCalls) {
            val toolName = call.function.name
            val serverTool = tools.find { it.tool.name == toolName }

            val resultContent = if (serverTool != null) {
                try {
                    val args = try {
                        mcpJson.decodeFromString<JsonObject>(call.function.arguments)
                    } catch (_: Exception) {
                        JsonObject(emptyMap())
                    }

                    mcpClient.callTool(serverTool.serverConfig, toolName, args)
                        .getOrElse { "Error: ${it.message}" }
                } catch (e: Exception) {
                    "Error executing tool: ${e.message}"
                }
            } else {
                "Error: Tool '$toolName' not found"
            }

            extendedHistory.add(
                ChatRequestMessage(
                    role = "tool",
                    content = resultContent,
                    toolCallId = call.id,
                )
            )
        }

        // Get the final response by streaming
        streamResponse(assistantId, model, extendedHistory)
    }

    /**
     * Stream a response from the LLM API.
     */
    private suspend fun streamResponse(
        assistantId: String,
        model: LlmModel,
        conversationHistory: List<ChatRequestMessage>,
    ) {
        val responseFlow = apiClient.streamChatCompletion(
            model = model,
            conversationHistory = conversationHistory,
        )

        val builder = StringBuilder()
        responseFlow.collect { token ->
            builder.append(token)
            updateMessage(assistantId, builder.toString())
            yield()
        }

        if (builder.isEmpty()) {
            updateMessage(assistantId, "No response received from the model. Please try again.")
        }
    }

    private fun updateMessage(id: String, content: String, streaming: Boolean? = null) {
        val idx = messages.indexOfFirst { it.id == id }
        if (idx >= 0) {
            messages[idx] = if (streaming != null) {
                messages[idx].copy(content = content, isStreaming = streaming)
            } else {
                messages[idx].copy(content = content)
            }
        }
    }

    fun clearConversation() {
        messages.clear()
        pendingAttachments.clear()
        inputText = ""
    }

    private fun generateId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..12).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private fun nextTimestamp(): Long = ++messageCounter
}
