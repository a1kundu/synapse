package `in`.arijitk.synapse.ui.chat

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun MathBlock(
    latex: String,
    displayMode: Boolean,
    textColor: Color,
    modifier: Modifier,
) {
    // Sanitize LaTeX for safe embedding in HTML/JS string literal
    val sanitizedLatex = remember(latex) {
        latex.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    val cssColor = remember(textColor) {
        val argb = textColor.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        "rgb($r,$g,$b)"
    }

    val html = remember(sanitizedLatex, displayMode, cssColor) {
        """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
        <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
        <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body {
                background: transparent;
                color: $cssColor;
                font-size: 16px;
                padding: ${if (displayMode) "8px 4px" else "0 2px"};
                display: ${if (displayMode) "flex" else "inline"};
                ${if (displayMode) "justify-content: center;" else ""}
            }
            .katex { color: $cssColor !important; }
            .katex .mord, .katex .mbin, .katex .mrel,
            .katex .mopen, .katex .mclose, .katex .mpunct,
            .katex .mop, .katex .minner { color: $cssColor !important; }
        </style>
        </head><body>
        <div id="math"></div>
        <script>
            try {
                katex.render("$sanitizedLatex", document.getElementById("math"), {
                    displayMode: $displayMode,
                    throwOnError: false,
                    output: "html"
                });
            } catch(e) {
                document.getElementById("math").textContent = "Error: " + e.message;
            }
        </script>
        </body></html>
        """.trimIndent()
    }

    val heightMod = if (displayMode) {
        modifier.fillMaxWidth().heightIn(min = 40.dp, max = 200.dp)
    } else {
        modifier.heightIn(min = 24.dp, max = 60.dp)
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                settings.javaScriptEnabled = true
                setBackgroundColor(AndroidColor.TRANSPARENT)
                webViewClient = WebViewClient()
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
        modifier = heightMod,
    )
}
