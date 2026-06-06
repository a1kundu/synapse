package `in`.arijitk.synapse.ui.chat

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun MermaidDiagram(code: String, modifier: Modifier) {
    val bgColor = MaterialTheme.colorScheme.surfaceVariant

    // Sanitize mermaid code for safe HTML embedding
    val sanitizedCode = remember(code) {
        code.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    val html = remember(sanitizedCode) {
        """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
        <style>
            body { margin: 0; padding: 8px; background: transparent; display: flex; justify-content: center; }
            .mermaid { max-width: 100%; }
            svg { max-width: 100% !important; height: auto !important; }
        </style>
        </head><body>
        <pre class="mermaid">$sanitizedCode</pre>
        <script>mermaid.initialize({ startOnLoad: true, theme: 'neutral' });</script>
        </body></html>
        """.trimIndent()
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                settings.javaScriptEnabled = true
                setBackgroundColor(Color.TRANSPARENT)
                webViewClient = WebViewClient()
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp, max = 400.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor),
    )
}
