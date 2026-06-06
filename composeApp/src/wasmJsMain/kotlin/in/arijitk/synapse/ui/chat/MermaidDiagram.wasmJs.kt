package `in`.arijitk.synapse.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Image as SkiaImage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// -- JS interop ---

/**
 * Calls window.__synapseMermaidRender(id, code) which returns a Promise
 * containing a PNG data-URL of the rendered diagram.
 */
@JsFun("(id, code) => { return window.__synapseMermaidRender(id, code); }")
private external fun jsMermaidRender(id: String, code: String): kotlin.js.Promise<JsString>

/**
 * Awaits a JS Promise by converting it to a Kotlin coroutine.
 */
private suspend fun awaitMermaidRender(id: String, code: String): String {
    return suspendCoroutine { cont ->
        jsMermaidRender(id, code).then(
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
}

/**
 * Decodes a base-64 string into a byte array via JS atob.
 * Returns each byte as an Int array which is then converted to ByteArray.
 */
@JsFun(
    """(base64) => {
    var bin = atob(base64);
    var len = bin.length;
    var arr = new Int8Array(len);
    for (var i = 0; i < len; i++) arr[i] = bin.charCodeAt(i);
    return arr;
}""",
)
private external fun jsBase64ToInt8Array(base64: JsString): JsAny

private fun decodeBase64ToByteArray(base64: String): ByteArray {
    val jsResult = jsBase64ToInt8Array(base64.toJsString())
    return jsResultToByteArray(jsResult)
}

@JsFun("(arr) => { return new Int8Array(arr.buffer, arr.byteOffset, arr.byteLength); }")
private external fun toInt8Array(arr: JsAny): JsAny

private fun jsResultToByteArray(jsArr: JsAny): ByteArray {
    // The Int8Array from JS maps to Kotlin ByteArray via interop
    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    val int8 = toInt8Array(jsArr)
    return byteArrayFromJsInt8Array(int8)
}

@JsFun("""(int8arr) => {
    var len = int8arr.length;
    var result = [];
    for (var i = 0; i < len; i++) result.push(int8arr[i]);
    return result;
}""")
private external fun jsInt8ArrayToList(arr: JsAny): JsArray<JsNumber>

private fun byteArrayFromJsInt8Array(int8: JsAny): ByteArray {
    val list = jsInt8ArrayToList(int8)
    return ByteArray(list.length) { i -> list[i]!!.toInt().toByte() }
}

// -- Composable ---

@Composable
actual fun MermaidDiagram(code: String, modifier: Modifier) {
    var bitmap: ImageBitmap? by remember(code) { mutableStateOf<ImageBitmap?>(null) }
    var error: String? by remember(code) { mutableStateOf<String?>(null) }
    val diagramId = remember(code) { "mermaid-${code.hashCode().toUInt()}" }

    LaunchedEffect(code) {
        try {
            val dataUrl = awaitMermaidRender(diagramId, code)
            // dataUrl is "data:image/png;base64,<base64data>"
            val base64 = dataUrl.substringAfter("base64,")
            val pngBytes = decodeBase64ToByteArray(base64)
            bitmap = SkiaImage.makeFromEncoded(pngBytes).toComposeImageBitmap()
        } catch (e: Exception) {
            error = e.message ?: "Mermaid rendering failed"
        }
    }

    val bgColor = MaterialTheme.colorScheme.surfaceVariant

    when {
        bitmap != null -> {
            Image(
                bitmap = bitmap!!,
                contentDescription = "Mermaid diagram",
                contentScale = ContentScale.FillWidth,
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .padding(4.dp),
            )
        }
        error != null -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .padding(12.dp),
            ) {
                Text(
                    text = "Mermaid error: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        else -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .padding(12.dp),
            ) {
                Text(
                    text = "Rendering diagram\u2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
