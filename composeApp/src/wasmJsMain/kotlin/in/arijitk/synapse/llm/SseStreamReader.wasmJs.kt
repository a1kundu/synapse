package `in`.arijitk.synapse.llm

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * Wasm/JS implementation – reads the full response via [bodyAsText] and
 * parses SSE events from the complete text.
 *
 * The browser Fetch API does not reliably expose a line-oriented
 * streaming channel through Ktor's [ByteReadChannel], so we trade
 * incremental rendering for guaranteed content delivery.
 */
actual fun readSseStream(response: HttpResponse, json: Json): Flow<String> = flow {
    val fullText = response.bodyAsText()
    var hasEmittedContent = false

    for (line in fullText.lines()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        if (!trimmed.startsWith("data:")) continue

        val data = trimmed.removePrefix("data:").trim()
        if (data == "[DONE]") break

        try {
            val chunk = json.decodeFromString<ChatCompletionResponse>(data)
            val content = chunk.choices.firstOrNull()?.delta?.content
            if (!content.isNullOrEmpty()) {
                emit(content)
                hasEmittedContent = true
            }
        } catch (_: Exception) {
            // Skip malformed SSE chunks
        }
    }

    // Fallback: if SSE parsing yielded nothing, try as a regular
    // (non-streaming) JSON response — some providers may ignore the
    // stream flag and return a single JSON object.
    if (!hasEmittedContent) {
        try {
            val result = json.decodeFromString<ChatCompletionResponse>(fullText)
            val content = result.choices.firstOrNull()?.message?.content
            if (!content.isNullOrEmpty()) {
                emit(content)
                hasEmittedContent = true
            }
        } catch (_: Exception) {
            // Not a valid non-streaming response either
        }
    }

    if (!hasEmittedContent) {
        emit("No response content received from the model.")
    }
}
