package `in`.arijitk.synapse.mcp

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

/**
 * MCP protocol client.
 * Supports HTTP Streamable and SSE transports for tool discovery and execution.
 */
class McpClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private var requestId = 0
    private fun nextId() = ++requestId

    /**
     * Discover tools from an MCP server.
     */
    suspend fun discoverTools(server: McpServerConfig): Result<List<McpTool>> {
        return try {
            val postUrl = when (server.type) {
                McpTransportType.HTTP_STREAMABLE -> server.url
                McpTransportType.SSE -> discoverSsePostEndpoint(server.url)
            }

            // 1. Initialize
            val initResult = sendJsonRpc(postUrl, "initialize", buildJsonObject {
                put("protocolVersion", "2025-03-26")
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") {
                    put("name", "Synapse")
                    put("version", "1.0.0")
                }
            })
            if (initResult.isFailure) return Result.failure(initResult.exceptionOrNull()!!)

            // 2. Notify initialized
            sendNotification(postUrl, "notifications/initialized")

            // 3. List tools
            val toolsResult = sendJsonRpc(postUrl, "tools/list", buildJsonObject {})
            toolsResult.map { resp ->
                val result = resp.result ?: return Result.success(emptyList())
                json.decodeFromJsonElement<McpToolsListResult>(result).tools
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to discover tools from ${server.name}: ${e.message}"))
        }
    }

    /**
     * Call a tool on an MCP server.
     * Re-initializes the session before the call (MCP requires init first).
     */
    suspend fun callTool(
        server: McpServerConfig,
        toolName: String,
        arguments: JsonObject,
    ): Result<String> {
        return try {
            val postUrl = when (server.type) {
                McpTransportType.HTTP_STREAMABLE -> server.url
                McpTransportType.SSE -> discoverSsePostEndpoint(server.url)
            }

            // MCP requires initialize before any tool call
            val initResult = sendJsonRpc(postUrl, "initialize", buildJsonObject {
                put("protocolVersion", "2025-03-26")
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") {
                    put("name", "Synapse")
                    put("version", "1.0.0")
                }
            })
            if (initResult.isFailure) return Result.failure(initResult.exceptionOrNull()!!)
            sendNotification(postUrl, "notifications/initialized")

            val result = sendJsonRpc(postUrl, "tools/call", buildJsonObject {
                put("name", toolName)
                put("arguments", arguments)
            })
            result.map { resp ->
                val resultElement = resp.result ?: return Result.success("")
                val callResult = json.decodeFromJsonElement<McpToolCallResult>(resultElement)
                if (callResult.isError) {
                    return Result.failure(Exception(callResult.content.joinToString("\n") { it.text }))
                }
                callResult.content.joinToString("\n") { it.text }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Tool call failed: ${e.message}"))
        }
    }

    /**
     * For SSE transport, GET the SSE endpoint and parse the `endpoint` event
     * to discover the POST URL for JSON-RPC messages.
     */
    private suspend fun discoverSsePostEndpoint(sseUrl: String): String {
        return discoverSseEndpoint(sseUrl)
    }

    private suspend fun sendJsonRpc(
        url: String,
        method: String,
        params: JsonElement?,
    ): Result<JsonRpcResponse> {
        val id = nextId()
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            if (params != null) put("params", params)
        }

        return try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), request))
            }

            if (!response.status.isSuccess()) {
                return Result.failure(Exception("HTTP ${response.status.value}"))
            }

            val body = response.bodyAsText()

            // Response might be SSE-wrapped or direct JSON
            val jsonBody = if (body.trimStart().startsWith("{")) {
                body
            } else {
                // Parse SSE-wrapped response
                body.lines()
                    .filter { it.trim().startsWith("data:") }
                    .map { it.trim().removePrefix("data:").trim() }
                    .firstOrNull { it.isNotBlank() && it != "[DONE]" }
                    ?: return Result.failure(Exception("Empty response"))
            }

            val rpcResponse = json.decodeFromString<JsonRpcResponse>(jsonBody)
            if (rpcResponse.error != null) {
                Result.failure(Exception("MCP error: ${rpcResponse.error.message}"))
            } else {
                Result.success(rpcResponse)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun sendNotification(url: String, method: String) {
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
        }
        try {
            client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), request))
            }
        } catch (_: Exception) {
            // Notifications don't require responses
        }
    }
}
