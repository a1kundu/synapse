package `in`.arijitk.synapse.llm

import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * Platform-specific SSE response reader.
 *
 * Parses an [HttpResponse] body as Server-Sent Events and emits content
 * tokens extracted from OpenAI-compatible streaming chunks.
 *
 * - **Android** – uses `ByteReadChannel.readUTF8Line()` for true
 *   token-by-token streaming so the UI updates incrementally.
 * - **Wasm/JS** – uses `bodyAsText()` because the browser Fetch API does
 *   not reliably expose a line-oriented streaming channel through Ktor.
 *
 * Both implementations include a fallback that tries to parse the body as
 * a regular (non-streaming) JSON response in case the provider ignores
 * the `stream` flag. If nothing can be parsed, a user-visible fallback
 * message is emitted.
 */
expect fun readSseStream(response: HttpResponse, json: Json): Flow<String>
