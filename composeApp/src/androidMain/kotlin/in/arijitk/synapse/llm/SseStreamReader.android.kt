package `in`.arijitk.synapse.llm

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Android implementation – reads SSE via [ByteReadChannel] for true
 * line-by-line incremental streaming. Emits raw `data:` field values
 * (JSON strings) as they arrive so the caller can parse and update the
 * UI token-by-token.
 *
 * If no SSE `data:` lines are found (non-streaming response), the
 * accumulated body text is emitted as a single value for fallback parsing.
 */
actual fun readSseStream(response: HttpResponse): Flow<String> = flow {
    val channel: ByteReadChannel = response.bodyAsChannel()
    var hasEmittedData = false

    // Accumulate raw lines so we can attempt a non-streaming fallback
    // if the provider ignores the stream flag.
    val rawLines = StringBuilder()

    while (!channel.isClosedForRead) {
        val line = channel.readUTF8Line() ?: break
        rawLines.appendLine(line)

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
        val fullText = rawLines.toString().trim()
        if (fullText.isNotEmpty()) {
            emit(fullText)
        }
    }
}
