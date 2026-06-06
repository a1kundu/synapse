package `in`.arijitk.synapse.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ── MCP server config (persisted in settings) ───────────────────────────────

enum class McpTransportType { SSE, HTTP_STREAMABLE }

@Serializable
data class McpServerConfig(
    val name: String,
    val url: String,
    val type: McpTransportType,
)

// ── JSON-RPC 2.0 models ────────────────────────────────────────────────────

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
)

@Serializable
data class JsonRpcError(
    val code: Int = 0,
    val message: String = "Unknown error",
    val data: JsonElement? = null,
)

// ── MCP tool models ─────────────────────────────────────────────────────────

@Serializable
data class McpTool(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonElement? = null,
)

@Serializable
data class McpToolsListResult(
    val tools: List<McpTool> = emptyList(),
)

@Serializable
data class McpToolCallResult(
    val content: List<McpContent> = emptyList(),
    val isError: Boolean = false,
)

@Serializable
data class McpContent(
    val type: String = "text",
    val text: String = "",
)

// ── Resolved tool with server info (for UI + system prompt) ─────────────────

data class McpServerTool(
    val serverName: String,
    val serverConfig: McpServerConfig,
    val tool: McpTool,
)
