package `in`.arijitk.synapse.mcp

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.readUTF8Line

actual suspend fun discoverSseEndpoint(sseUrl: String): String {
    val client = HttpClient()
    var endpoint: String? = null

    try {
        client.prepareGet(sseUrl) {
            header("Accept", "text/event-stream")
        }.execute { response ->
            val channel = response.bodyAsChannel()
            var lastEventType: String? = null

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                val trimmed = line.trim()

                if (trimmed.startsWith("event:")) {
                    lastEventType = trimmed.removePrefix("event:").trim()
                } else if (trimmed.startsWith("data:") && lastEventType == "endpoint") {
                    endpoint = trimmed.removePrefix("data:").trim()
                    break
                }
            }
        }
    } finally {
        client.close()
    }

    // Resolve relative URL
    if (endpoint != null && !endpoint!!.startsWith("http")) {
        val base = sseUrl.trimEnd('/').substringBeforeLast('/')
        endpoint = "$base/${endpoint!!.trimStart('/')}"
    }

    return endpoint ?: throw Exception("No endpoint event received from SSE server")
}
