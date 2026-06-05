package `in`.arijitk.synapse.ui.chat

/**
 * Represents a selectable LLM model.
 */
data class LlmModel(
    val id: String,
    val displayName: String,
    val provider: String,
)

/**
 * Predefined list of available models.
 */
object AvailableModels {
    val models = listOf(
        LlmModel("gpt-4o", "GPT-4o", "OpenAI"),
        LlmModel("gpt-4o-mini", "GPT-4o Mini", "OpenAI"),
        LlmModel("gpt-3.5-turbo", "GPT-3.5 Turbo", "OpenAI"),
        LlmModel("claude-3.5-sonnet", "Claude 3.5 Sonnet", "Anthropic"),
        LlmModel("claude-3-haiku", "Claude 3 Haiku", "Anthropic"),
        LlmModel("gemini-1.5-pro", "Gemini 1.5 Pro", "Google"),
        LlmModel("gemini-1.5-flash", "Gemini 1.5 Flash", "Google"),
    )

    val default = models.first()
}

/**
 * A file attachment on a chat message.
 */
data class ChatAttachment(
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
) {
    val displaySize: String
        get() {
            val kb = fileSizeBytes / 1024.0
            return if (kb < 1024) {
                "${(kb * 10).toLong() / 10.0} KB"
            } else {
                "${(kb / 1024.0 * 10).toLong() / 10.0} MB"
            }
        }
}

/**
 * Sender role for a chat message.
 */
enum class MessageRole {
    USER,
    ASSISTANT,
}

/**
 * A single chat message.
 */
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val attachments: List<ChatAttachment> = emptyList(),
    val model: LlmModel? = null,
    val isStreaming: Boolean = false,
)
