package `in`.arijitk.synapse.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Renders a LaTeX math expression.
 *
 * @param latex The LaTeX source (without delimiters).
 * @param displayMode true for block-level (centered, large), false for inline.
 * @param textColor The text color to use for rendering.
 */
@Composable
expect fun MathBlock(
    latex: String,
    displayMode: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier,
)
