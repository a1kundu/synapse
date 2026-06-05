package `in`.arijitk.synapse.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.arijitk.synapse.llm.ChatRequestMessage
import `in`.arijitk.synapse.llm.LlmApiClient
import `in`.arijitk.synapse.settings.SettingsRepository
import kotlinx.coroutines.launch
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

    init {
        // Auto-fetch models if API key is configured
        refreshModels()
    }

    fun onInputTextChange(text: String) {
        inputText = text
    }

    fun selectModel(model: LlmModel) {
        selectedModel = model
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
                    // If no model selected or current not in list, select first
                    if (selectedModel == null || models.none { it.id == selectedModel?.id }) {
                        selectedModel = models.first()
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

        generateResponse()
    }

    private fun generateResponse() {
        val settings = SettingsRepository.instance

        if (!settings.isLlmConfigured) {
            // No API key — show a helpful message
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

        // Build conversation history for API
        val conversationHistory = messages
            .filter { it.id != assistantId }
            .map { msg ->
                ChatRequestMessage(
                    role = when (msg.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                    },
                    content = msg.content,
                )
            }

        viewModelScope.launch {
            try {
                if (currentModel == null) {
                    val idx = messages.indexOfFirst { it.id == assistantId }
                    if (idx >= 0) {
                        messages[idx] = messages[idx].copy(
                            content = "⚠️ No model selected. Fetch models first.",
                            isStreaming = false,
                        )
                    }
                    isGenerating = false
                    return@launch
                }
                val responseFlow = apiClient.streamChatCompletion(
                    model = currentModel,
                    conversationHistory = conversationHistory,
                )

                val builder = StringBuilder()
                responseFlow.collect { token ->
                    builder.append(token)
                    val idx = messages.indexOfFirst { it.id == assistantId }
                    if (idx >= 0) {
                        messages[idx] = messages[idx].copy(
                            content = builder.toString(),
                        )
                    }
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
                // Mark streaming complete
                val idx = messages.indexOfFirst { it.id == assistantId }
                if (idx >= 0) {
                    messages[idx] = messages[idx].copy(isStreaming = false)
                }
                isGenerating = false
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
