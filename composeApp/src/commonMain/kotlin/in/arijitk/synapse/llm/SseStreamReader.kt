package `in`.arijitk.synapse.llm

import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.flow.Flow

/**
 * Platform-specific SSE response reader.
 *
 * Reads an [HttpResponse] body as Server-Sent Events and emits the raw
 * `data:` field values (JSON strings) one at a time. The `[DONE]`
 * sentinel is filtered out and never emitted.
 *
 * - **Android** – uses `ByteReadChannel.readUTF8Line()` for true
 *   line-by-line incremental streaming so the UI can update token-by-token.
 * - **Wasm/JS** – uses `bodyAsText()` because the browser Fetch API does
 *   not reliably expose a line-oriented streaming channel through Ktor.
 *
 * If no SSE `data:` lines are found (provider returned a non-streaming
 * JSON response), the full body text is emitted as a single value so
 * that callers can attempt a non-streaming parse as a fallback.
 */
expect fun readSseStream(response: HttpResponse): Flow<String>
