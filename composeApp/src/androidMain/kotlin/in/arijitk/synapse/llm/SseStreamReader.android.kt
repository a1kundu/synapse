package `in`.arijitk.synapse.llm

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * Android implementation – reads SSE via [ByteReadChannel] for true
 * token-by-token streaming so the chat UI updates incrementally.
 */
actual fun readSseStream(response: HttpResponse, json: Json): Flow<String> = flow {
    val channel: ByteReadChannel = response.bodyAsChannel()
    var hasEmittedContent = false

    // Accumulate raw lines so we can attempt a non-streaming JSON
    // fallback if no SSE content tokens are found.
    val rawLines = StringBuilder()

    while (!channel.isClosedForRead) {
        val line = channel.readUTF8Line() ?: break
        rawLines.appendLine(line)

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

    // Fallback: if SSE parsing yielded nothing, the provider may have
    // returned a regular (non-streaming) JSON response.
    if (!hasEmittedContent) {
        try {
            val result = json.decodeFromString<ChatCompletionResponse>(
                rawLines.toString().trim(),
            )
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
