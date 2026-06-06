package `in`.arijitk.synapse.llm

import `in`.arijitk.synapse.settings.SettingsRepository
import `in`.arijitk.synapse.ui.chat.LlmModel
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── API request/response models ─────────────────────────────────────────────

@Serializable
data class ChatRequestMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCallInfo>? = null,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatRequestMessage>,
    val stream: Boolean = true,
    val temperature: Double = 0.7,
    val tools: List<OpenAiTool>? = null,
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
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCallInfo>? = null,
)

@Serializable
data class DeltaContent(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCallDelta>? = null,
)

// ── Tool calling models ─────────────────────────────────────────────────────

@Serializable
data class OpenAiTool(
    val type: String = "function",
    val function: OpenAiFunction,
)

@Serializable
data class OpenAiFunction(
    val name: String,
    val description: String = "",
    val parameters: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class ToolCallInfo(
    val id: String = "",
    val type: String = "function",
    val function: ToolCallFunctionInfo = ToolCallFunctionInfo(),
)

@Serializable
data class ToolCallFunctionInfo(
    val name: String = "",
    val arguments: String = "",
)

@Serializable
data class ToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: ToolCallFunctionDelta? = null,
)

@Serializable
data class ToolCallFunctionDelta(
    val name: String? = null,
    val arguments: String? = null,
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

/** Events emitted from a streaming chat completion with tool support. */
sealed class StreamEvent {
    /** A text content token. */
    data class Token(val text: String) : StreamEvent()
    /** Streaming complete; the accumulated tool calls (if any). */
    data class Done(val toolCalls: List<ToolCallInfo>) : StreamEvent()
    /** An error. */
    data class Error(val message: String) : StreamEvent()
}

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
     * Delegates response parsing to [readSseStream] which has
     * platform-specific implementations:
     * - Android: token-by-token via ByteReadChannel
     * - Wasm/JS: full-text parsing via bodyAsText()
     *
     * Uses [preparePost] + [execute] instead of [post] to avoid Ktor's
     * call.save() which buffers the entire response body, defeating
     * incremental streaming on Android.
     */
    fun streamChatCompletion(
        model: LlmModel,
        conversationHistory: List<ChatRequestMessage>,
        tools: List<OpenAiTool>? = null,
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
            tools = tools?.takeIf { it.isNotEmpty() },
        )

        try {
            // preparePost returns an HttpStatement; execute{} keeps the
            // connection open so bodyAsChannel() can stream incrementally.
            // Using client.post() would call save() internally, buffering
            // the entire body before returning — breaking token-by-token rendering.
            client.preparePost("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                if (settings.llmProvider == `in`.arijitk.synapse.settings.LlmProvider.OPENROUTER) {
                    header("HTTP-Referer", "https://synapse.arijitk.in")
                    header("X-Title", "Synapse")
                }
                setBody(request)
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    val errorMessage = try {
                        json.decodeFromString<ApiErrorResponse>(errorBody).error?.message
                            ?: "HTTP ${response.status.value}"
                    } catch (_: Exception) {
                        "HTTP ${response.status.value}: $errorBody"
                    }
                    emit("⚠️ Error: $errorMessage")
                    return@execute
                }

                // Delegate SSE parsing to platform-specific implementation.
                // Must collect inside execute{} — the connection closes when
                // execute returns.
                readSseStream(response, json).collect { token ->
                    emit(token)
                }
            }
        } catch (e: Exception) {
            emit("⚠️ Connection error: ${e.message ?: "Unknown error"}")
        }
    }

    /**
     * Stream chat completion and emit [StreamEvent]s.
     * Handles tool call deltas accumulated across SSE chunks.
     */
    fun streamWithTools(
        model: LlmModel,
        conversationHistory: List<ChatRequestMessage>,
        tools: List<OpenAiTool>? = null,
    ): Flow<StreamEvent> = flow {
        val settings = SettingsRepository.instance
        val baseUrl = settings.resolvedBaseUrl
        val apiKey = settings.llmApiKey

        if (apiKey.isBlank()) {
            emit(StreamEvent.Error("API key not configured"))
            return@flow
        }

        val request = ChatCompletionRequest(
            model = model.id,
            messages = conversationHistory,
            stream = true,
            tools = tools?.takeIf { it.isNotEmpty() },
        )

        // Accumulators for tool calls built from deltas
        val toolCallMap = mutableMapOf<Int, MutableToolCall>()

        try {
            client.preparePost("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                if (settings.llmProvider == `in`.arijitk.synapse.settings.LlmProvider.OPENROUTER) {
                    header("HTTP-Referer", "https://synapse.arijitk.in")
                    header("X-Title", "Synapse")
                }
                setBody(request)
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    val errorMessage = try {
                        json.decodeFromString<ApiErrorResponse>(errorBody).error?.message
                            ?: "HTTP ${response.status.value}"
                    } catch (_: Exception) {
                        "HTTP ${response.status.value}: $errorBody"
                    }
                    emit(StreamEvent.Error(errorMessage))
                    return@execute
                }

                // Parse SSE body
                val fullText = response.bodyAsText()
                for (line in fullText.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || !trimmed.startsWith("data:")) continue
                    val data = trimmed.removePrefix("data:").trim()
                    if (data == "[DONE]") break

                    try {
                        val chunk = json.decodeFromString<ChatCompletionResponse>(data)
                        val delta = chunk.choices.firstOrNull()?.delta ?: continue

                        // Emit text content
                        val content = delta.content
                        if (!content.isNullOrEmpty()) {
                            emit(StreamEvent.Token(content))
                        }

                        // Accumulate tool call deltas
                        delta.toolCalls?.forEach { tcd ->
                            val tc = toolCallMap.getOrPut(tcd.index) { MutableToolCall() }
                            tcd.id?.let { tc.id = it }
                            tcd.type?.let { tc.type = it }
                            tcd.function?.name?.let { tc.name += it }
                            tcd.function?.arguments?.let { tc.arguments += it }
                        }
                    } catch (_: Exception) {
                        // skip malformed chunks
                    }
                }

                // Fallback: try non-streaming parse if no events emitted
                if (toolCallMap.isEmpty()) {
                    try {
                        val result = json.decodeFromString<ChatCompletionResponse>(fullText)
                        val msg = result.choices.firstOrNull()?.message
                        if (msg != null) {
                            val c = msg.content
                            if (!c.isNullOrEmpty()) emit(StreamEvent.Token(c))
                            val tcs = msg.toolCalls
                            if (!tcs.isNullOrEmpty()) {
                                emit(StreamEvent.Done(tcs))
                                return@execute
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Emit completed tool calls
            val finalToolCalls = toolCallMap.entries
                .sortedBy { it.key }
                .map { (_, tc) ->
                    ToolCallInfo(
                        id = tc.id,
                        type = tc.type,
                        function = ToolCallFunctionInfo(
                            name = tc.name,
                            arguments = tc.arguments,
                        )
                    )
                }
            emit(StreamEvent.Done(finalToolCalls))
        } catch (e: Exception) {
            emit(StreamEvent.Error("Connection error: ${e.message ?: "Unknown error"}"))
        }
    }

    /** Mutable accumulator for a tool call being built from stream deltas. */
    private class MutableToolCall {
        var id: String = ""
        var type: String = "function"
        var name: String = ""
        var arguments: String = ""
    }

    /**
     * Non-streaming chat completion with optional tool calling support.
     * Returns the full parsed response for tool call handling.
     */
    suspend fun chatCompletionFull(
        model: LlmModel,
        conversationHistory: List<ChatRequestMessage>,
        tools: List<OpenAiTool>? = null,
    ): Result<ChatCompletionResponse> {
        val settings = SettingsRepository.instance
        val baseUrl = settings.resolvedBaseUrl
        val apiKey = settings.llmApiKey

        if (apiKey.isBlank()) {
            return Result.failure(Exception("API key not configured"))
        }

        val request = ChatCompletionRequest(
            model = model.id,
            messages = conversationHistory,
            stream = false,
            tools = tools?.takeIf { it.isNotEmpty() },
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
                return Result.failure(Exception(errorMessage))
            }

            val body = response.bodyAsText()
            Result.success(json.decodeFromString<ChatCompletionResponse>(body))
        } catch (e: Exception) {
            Result.failure(Exception("Connection error: ${e.message}"))
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
