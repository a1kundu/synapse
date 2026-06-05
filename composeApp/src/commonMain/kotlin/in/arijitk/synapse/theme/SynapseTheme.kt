package `in`.arijitk.synapse.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Theme mode options matching system/light/dark.
 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromString(value: String): ThemeMode = when (value.lowercase()) {
            "light" -> LIGHT
            "dark" -> DARK
            else -> SYSTEM
        }
    }
}

/**
 * Global theme state holders.
 */
object ThemeSettings {
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    var dynamicColorEnabled by mutableStateOf(true)
}

/**
 * Expect function for platform-specific dynamic color scheme.
 * Returns null if dynamic color is not available on this platform.
 */
@Composable
expect fun dynamicColorScheme(dark: Boolean): ColorScheme?

/**
 * Main application theme composable.
 */
@Composable
fun SynapseTheme(
    content: @Composable () -> Unit,
) {
    val isDark = when (ThemeSettings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (ThemeSettings.dynamicColorEnabled) {
        dynamicColorScheme(isDark) ?: defaultColorScheme(isDark)
    } else {
        defaultColorScheme(isDark)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = SynapseShapes,
        content = content,
    )
}

private fun defaultColorScheme(dark: Boolean): ColorScheme {
    return if (dark) {
        darkColorScheme(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFFCCC2DC),
            onSecondary = Color(0xFF332D41),
            secondaryContainer = Color(0xFF4A4458),
            onSecondaryContainer = Color(0xFFE8DEF8),
            tertiary = Color(0xFFEFB8C8),
            onTertiary = Color(0xFF492532),
            tertiaryContainer = Color(0xFF633B48),
            onTertiaryContainer = Color(0xFFFFD8E4),
            background = Color(0xFF1C1B1F),
            onBackground = Color(0xFFE6E1E5),
            surface = Color(0xFF1C1B1F),
            onSurface = Color(0xFFE6E1E5),
            surfaceVariant = Color(0xFF49454F),
            onSurfaceVariant = Color(0xFFCAC4D0),
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6750A4),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEADDFF),
            onPrimaryContainer = Color(0xFF21005D),
            secondary = Color(0xFF625B71),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE8DEF8),
            onSecondaryContainer = Color(0xFF1D192B),
            tertiary = Color(0xFF7D5260),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFD8E4),
            onTertiaryContainer = Color(0xFF31111D),
            background = Color(0xFFFFFBFE),
            onBackground = Color(0xFF1C1B1F),
            surface = Color(0xFFFFFBFE),
            onSurface = Color(0xFF1C1B1F),
            surfaceVariant = Color(0xFFE7E0EC),
            onSurfaceVariant = Color(0xFF49454F),
            error = Color(0xFFB3261E),
            onError = Color.White,
        )
    }
}

private val SynapseShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
