package `in`.arijitk.synapse.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders a Mermaid diagram from the given [code].
 * Platform-specific implementations use mermaid.js under the hood.
 */
@Composable
expect fun MermaidDiagram(code: String, modifier: Modifier = Modifier)
