package `in`.arijitk.synapse.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * ViewModel for the Chat screen.
 * Manages messages, model selection, and attachments.
 */
class ChatViewModel : ViewModel() {

    /** Simple monotonic counter for ordering messages (UI-thread only). */
    private var messageCounter = 0L

    /** All messages in the current conversation. */
    val messages = mutableStateListOf<ChatMessage>()

    /** Currently selected LLM model. */
    var selectedModel by mutableStateOf(AvailableModels.default)
        private set

    /** Pending file attachments for the next message. */
    val pendingAttachments = mutableStateListOf<ChatAttachment>()

    /** Whether the assistant is currently generating a response. */
    var isGenerating by mutableStateOf(false)
        private set

    /** Current text in the input field (managed here for clearing on send). */
    var inputText by mutableStateOf("")

    fun onInputTextChange(text: String) {
        inputText = text
    }

    fun selectModel(model: LlmModel) {
        selectedModel = model
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
     * Send a user message and trigger a simulated assistant response.
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

        // Simulate assistant response
        simulateResponse(trimmed)
    }

    private fun simulateResponse(userQuery: String) {
        isGenerating = true

        val assistantId = generateId()
        val streamingMessage = ChatMessage(
            id = assistantId,
            role = MessageRole.ASSISTANT,
            content = "",
            timestamp = nextTimestamp(),
            model = selectedModel,
            isStreaming = true,
        )
        messages.add(streamingMessage)

        viewModelScope.launch {
            val response = generateSimulatedResponse(userQuery)
            val words = response.split(" ")
            val builder = StringBuilder()

            for ((i, word) in words.withIndex()) {
                if (i > 0) builder.append(" ")
                builder.append(word)
                delay(Random.nextLong(30, 80))

                val idx = messages.indexOfFirst { it.id == assistantId }
                if (idx >= 0) {
                    messages[idx] = messages[idx].copy(
                        content = builder.toString(),
                    )
                }
            }

            // Mark streaming complete
            val idx = messages.indexOfFirst { it.id == assistantId }
            if (idx >= 0) {
                messages[idx] = messages[idx].copy(isStreaming = false)
            }
            isGenerating = false
        }
    }

    fun clearConversation() {
        messages.clear()
        pendingAttachments.clear()
        inputText = ""
    }

    private fun generateSimulatedResponse(query: String): String {
        val lowerQuery = query.lowercase()
        return when {
            lowerQuery.contains("hello") || lowerQuery.contains("hi") ->
                "Hello! I'm your AI assistant powered by ${selectedModel.displayName}. How can I help you today?"

            lowerQuery.contains("help") ->
                "I'd be happy to help! You can ask me questions, share files for analysis, or switch between different AI models using the model selector at the top. What would you like to know?"

            lowerQuery.contains("model") ->
                "You're currently using ${selectedModel.displayName} by ${selectedModel.provider}. You can switch models anytime using the dropdown in the top bar. Each model has different strengths -- for example, larger models tend to be more capable but slower."

            lowerQuery.contains("code") || lowerQuery.contains("program") ->
                "I can help with coding tasks! Share your code or describe what you'd like to build, and I'll assist you. You can also attach files using the attachment button for me to review."

            lowerQuery.isEmpty() ->
                "I see you've sent some attachments. Let me take a look at those files and get back to you with my analysis."

            else ->
                "That's an interesting question about \"$query\". As a simulated response from ${selectedModel.displayName}, I'm demonstrating how the chat interface works. In a production setup, this would connect to the actual ${selectedModel.provider} API to provide real responses. You can try switching models, attaching files, or asking different questions to explore the UI."
        }
    }

    private fun generateId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..12).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private fun nextTimestamp(): Long = ++messageCounter
}
