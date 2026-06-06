package `in`.arijitk.synapse.mcp

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Uses the browser's EventSource API to connect to an MCP SSE endpoint
 * and discover the POST URL for JSON-RPC messages.
 */
@JsFun("""(url) => {
    return new Promise((resolve, reject) => {
        try {
            const es = new EventSource(url);
            const timer = setTimeout(() => {
                es.close();
                reject(new Error('SSE endpoint discovery timed out'));
            }, 15000);
            es.addEventListener('endpoint', (e) => {
                clearTimeout(timer);
                es.close();
                resolve(e.data);
            });
            es.onerror = () => {
                clearTimeout(timer);
                es.close();
                reject(new Error('SSE connection error'));
            };
        } catch (e) {
            reject(e);
        }
    });
}""")
private external fun jsSseDiscoverEndpoint(url: JsString): kotlin.js.Promise<JsString>

actual suspend fun discoverSseEndpoint(sseUrl: String): String {
    val endpointPath = suspendCoroutine { cont ->
        jsSseDiscoverEndpoint(sseUrl.toJsString()).then(
            onFulfilled = { result: JsString ->
                cont.resume(result.toString())
                null
            },
            onRejected = { error: JsAny ->
                cont.resumeWithException(Exception(error.toString()))
                null
            },
        )
    }

    // Resolve relative URL
    return if (endpointPath.startsWith("http")) {
        endpointPath
    } else {
        val base = sseUrl.trimEnd('/').substringBeforeLast('/')
        "$base/${endpointPath.trimStart('/')}"
    }
}
