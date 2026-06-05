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
