package `in`.arijitk.synapse.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * WasmJS fallback — renders raw LaTeX as monospace text.
 */
@Composable
actual fun MathBlock(
    latex: String,
    displayMode: Boolean,
    textColor: Color,
    modifier: Modifier,
) {
    val bg = MaterialTheme.colorScheme.surfaceVariant
    Text(
        text = if (displayMode) "[$latex]" else latex,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            color = textColor,
        ),
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
