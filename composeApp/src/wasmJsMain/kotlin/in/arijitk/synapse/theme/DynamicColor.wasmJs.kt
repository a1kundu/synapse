package `in`.arijitk.synapse.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * Web does not support dynamic color / Material You.
 */
@Composable
actual fun dynamicColorScheme(dark: Boolean): ColorScheme? = null
