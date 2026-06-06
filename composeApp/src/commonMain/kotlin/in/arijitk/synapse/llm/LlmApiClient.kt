package `in`.arijitk.synapse.llm

import `in`.arijitk.synapse.settings.SettingsRepository
import `in`.arijitk.synapse.ui.chat.LlmModel
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── API request/response models ─────────────────────────────────────────────

@Serializable
data class ChatRequestMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatRequestMessage>,
    val stream: Boolean = true,
    val temperature: Double = 0.7,
)

@Serializable
data class ChatCompletionResponse(
    val id: String = "",
    val choices: List<Choice> = emptyList(),
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ResponseMessage? = null,
    val delta: DeltaContent? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
data class ResponseMessage(
    val role: String = "",
    val content: String = "",
)

@Serializable
data class DeltaContent(
    val role: String? = null,
    val content: String? = null,
)

@Serializable
data class ApiErrorResponse(
    val error: ApiError? = null,
)

@Serializable
data class ApiError(
    val message: String = "Unknown error",
    val type: String? = null,
    val code: String? = null,
)

// ── Models list response ────────────────────────────────────────────────────

@Serializable
data class ModelsListResponse(
    val data: List<ModelInfo> = emptyList(),
)

@Serializable
data class ModelInfo(
    val id: String,
    @SerialName("owned_by")
    val ownedBy: String = "",
)

// ── Client ──────────────────────────────────────────────────────────────────

class LlmApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    /**
     * Fetch available models from the provider's /models endpoint.
     * Returns a list of LlmModel sorted by id.
     */
    suspend fun fetchModels(): Result<List<LlmModel>> {
        val settings = SettingsRepository.instance
        val baseUrl = settings.resolvedBaseUrl
        val apiKey = settings.llmApiKey

        if (apiKey.isBlank()) {
            return Result.failure(Exception("API key not configured"))
        }

        return try {
            val response: HttpResponse = client.get("$baseUrl/models") {
                header("Authorization", "Bearer $apiKey")
                if (settings.llmProvider == `in`.arijitk.synapse.settings.LlmProvider.OPENROUTER) {
                    header("HTTP-Referer", "https://synapse.arijitk.in")
                    header("X-Title", "Synapse")
                }
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                val errorMessage = try {
                    json.decodeFromString<ApiErrorResponse>(errorBody).error?.message
                        ?: "HTTP ${response.status.value}"
                } catch (_: Exception) {
                    "HTTP ${response.status.value}"
                }
                return Result.failure(Exception(errorMessage))
            }

            val body = response.bodyAsText()
            val modelsResponse = json.decodeFromString<ModelsListResponse>(body)

            val models = modelsResponse.data
                .filter { info ->
                    // Filter to chat-capable models (skip embeddings, tts, whisper, dall-e, etc.)
                    val id = info.id.lowercase()
                    !id.contains("embedding") &&
                        !id.contains("tts") &&
                        !id.contains("whisper") &&
                        !id.contains("dall-e") &&
                        !id.contains("davinci") &&
                        !id.contains("babbage") &&
                        !id.contains("moderation")
                }
                .map { info ->
                    LlmModel(
                        id = info.id,
                        displayName = formatModelName(info.id),
                        provider = formatProvider(info.ownedBy),
                    )
                }
                .sortedBy { it.displayName.lowercase() }

            Result.success(models)
        } catch (e: Exception) {
            Result.failure(Exception("Connection error: ${e.message}"))
        }
    }

    private fun formatModelName(id: String): String {
        // Convert model IDs like "gpt-4o-mini" to "GPT-4o Mini"
        return id.split("/").last()
            .split("-", "_")
            .joinToString(" ") { part ->
                when {
                    part.all { it.isDigit() || it == '.' } -> part
                    part.length <= 3 && part.all { it.isLetterOrDigit() } -> part.uppercase()
                    else -> part.replaceFirstChar { it.uppercaseChar() }
                }
            }
    }

    private fun formatProvider(ownedBy: String): String {
        if (ownedBy.isBlank()) return "Unknown"
        return when {
            ownedBy.contains("openai", ignoreCase = true) -> "OpenAI"
            ownedBy.contains("anthropic", ignoreCase = true) -> "Anthropic"
            ownedBy.contains("google", ignoreCase = true) -> "Google"
            ownedBy.contains("meta", ignoreCase = true) -> "Meta"
            ownedBy.contains("mistral", ignoreCase = true) -> "Mistral"
            else -> ownedBy.replaceFirstChar { it.uppercaseChar() }
        }
    }

    /**
     * Send a chat completion request and stream back tokens via SSE.
     * Reads the response body incrementally line-by-line using bodyAsChannel().
     */
    fun streamChatCompletion(
        model: LlmModel,
        conversationHistory: List<ChatRequestMessage>,
    ): Flow<String> = flow {
        val settings = SettingsRepository.instance
        val baseUrl = settings.resolvedBaseUrl
        val apiKey = settings.llmApiKey

        if (apiKey.isBlank()) {
            emit("⚠️ API key not configured. Go to Settings → LLM Provider to set your API key.")
            return@flow
        }

        val request = ChatCompletionRequest(
            model = model.id,
            messages = conversationHistory,
            stream = true,
        )

        try {
            val response: HttpResponse = client.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                if (settings.llmProvider == `in`.arijitk.synapse.settings.LlmProvider.OPENROUTER) {
                    header("HTTP-Referer", "https://synapse.arijitk.in")
                    header("X-Title", "Synapse")
                }
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                val errorMessage = try {
                    json.decodeFromString<ApiErrorResponse>(errorBody).error?.message
                        ?: "HTTP ${response.status.value}"
                } catch (_: Exception) {
                    "HTTP ${response.status.value}: $errorBody"
                }
                emit("⚠️ Error: $errorMessage")
                return@flow
            }

            // Read SSE stream line-by-line via ByteReadChannel
            val channel: ByteReadChannel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (!trimmed.startsWith("data: ")) continue
                val data = trimmed.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                try {
                    val chunk = json.decodeFromString<ChatCompletionResponse>(data)
                    val content = chunk.choices.firstOrNull()?.delta?.content
                    if (!content.isNullOrEmpty()) {
                        emit(content)
                    }
                } catch (_: Exception) {
                    // Skip malformed chunks
                }
            }
        } catch (e: Exception) {
            emit("⚠️ Connection error: ${e.message ?: "Unknown error"}")
        }
    }

    /**
     * Non-streaming chat completion (fallback).
     */
    suspend fun chatCompletion(
        model: LlmModel,
        conversationHistory: List<ChatRequestMessage>,
    ): String {
        val settings = SettingsRepository.instance
        val baseUrl = settings.resolvedBaseUrl
        val apiKey = settings.llmApiKey

        if (apiKey.isBlank()) {
            return "⚠️ API key not configured. Go to Settings → LLM Provider to set your API key."
        }

        val request = ChatCompletionRequest(
            model = model.id,
            messages = conversationHistory,
            stream = false,
        )

        return try {
            val response: HttpResponse = client.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                if (settings.llmProvider == `in`.arijitk.synapse.settings.LlmProvider.OPENROUTER) {
                    header("HTTP-Referer", "https://synapse.arijitk.in")
                    header("X-Title", "Synapse")
                }
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                val errorMessage = try {
                    json.decodeFromString<ApiErrorResponse>(errorBody).error?.message
                        ?: "HTTP ${response.status.value}"
                } catch (_: Exception) {
                    "HTTP ${response.status.value}: $errorBody"
                }
                return "⚠️ Error: $errorMessage"
            }

            val body = response.bodyAsText()
            val result = json.decodeFromString<ChatCompletionResponse>(body)
            result.choices.firstOrNull()?.message?.content ?: "No response from model."
        } catch (e: Exception) {
            "⚠️ Connection error: ${e.message ?: "Unknown error"}"
        }
    }
}
