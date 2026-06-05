package `in`.arijitk.synapse.theme

import androidx.compose.ui.graphics.Color

/**
 * Application color palette with light and dark variants.
 */
object AppColors {
    // Primary seed
    val seed = Color(0xFF6750A4) // Deep Purple

    // Green tonal palette (for available/success states)
    object Green {
        val container = Color(0xFFD7F5D7)
        val containerDark = Color(0xFF1B3A1B)
        val border = Color(0xFF4CAF50)
        val borderDark = Color(0xFF81C784)
        val onContainer = Color(0xFF1B5E20)
        val onContainerDark = Color(0xFFA5D6A7)
    }

    // Error/Danger palette
    object Error {
        val container = Color(0xFFFFDAD6)
        val containerDark = Color(0xFF3B1014)
        val onContainer = Color(0xFF410002)
        val onContainerDark = Color(0xFFFFB4AB)
    }

    // Avatar colors for user identification
    val avatarPalette = listOf(
        Color(0xFF6750A4),
        Color(0xFF0061A4),
        Color(0xFF006D3B),
        Color(0xFF984061),
        Color(0xFF7D5260),
        Color(0xFF006874),
        Color(0xFF5D5F5F),
        Color(0xFF8B5000),
    )

    fun avatarColor(id: Int): Color = avatarPalette[id % avatarPalette.size]
}
