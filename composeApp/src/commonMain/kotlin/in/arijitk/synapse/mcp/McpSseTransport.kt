package `in`.arijitk.synapse.mcp

/**
 * Platform-specific SSE endpoint discovery.
 *
 * Connects to an MCP SSE endpoint, waits for the `endpoint` event,
 * and returns the POST URL for JSON-RPC messages.
 *
 * - **Android** – uses Ktor's ByteReadChannel for line-by-line SSE parsing.
 * - **Wasm/JS** – uses the browser's EventSource API.
 */
expect suspend fun discoverSseEndpoint(sseUrl: String): String
