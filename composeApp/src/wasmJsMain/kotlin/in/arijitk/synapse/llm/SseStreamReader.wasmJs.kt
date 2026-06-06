package `in`.arijitk.synapse.llm

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Wasm/JS implementation – reads the full response via [bodyAsText] and
 * emits raw SSE `data:` field values (JSON strings) from the complete text.
 *
 * The browser Fetch API does not reliably expose a line-oriented
 * streaming channel through Ktor's [io.ktor.utils.io.ByteReadChannel],
 * so incremental rendering is not possible on this platform; all tokens
 * are emitted in a batch after the full response has been received.
 *
 * If no SSE `data:` lines are found (non-streaming response), the full
 * body text is emitted as a single value for fallback parsing.
 */
actual fun readSseStream(response: HttpResponse): Flow<String> = flow {
    val fullText = response.bodyAsText()
    var hasEmittedData = false

    for (line in fullText.lines()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("data:")) continue

        val data = trimmed.removePrefix("data:").trim()
        if (data == "[DONE]") break

        emit(data)
        hasEmittedData = true
    }

    // Fallback: if no SSE data lines were found, emit the full body
    // so the caller can try parsing it as a non-streaming JSON response.
    if (!hasEmittedData) {
        emit(fullText)
    }
}
