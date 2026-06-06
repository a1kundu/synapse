package `in`.arijitk.synapse.ui.chat

/**
 * A segment of content that is either plain markdown text or a LaTeX math expression.
 */
sealed interface ContentSegment {
    /** Regular markdown text (may be empty). */
    data class Text(val markdown: String) : ContentSegment

    /** A LaTeX math expression. [displayMode] = true for block ($$...$$), false for inline ($...$). */
    data class Math(val latex: String, val displayMode: Boolean) : ContentSegment
}

/**
 * Splits a markdown string into [ContentSegment]s, extracting LaTeX math
 * delimited by `$$…$$` (block) or `$…$` (inline).
 *
 * Also recognises `\[…\]` (block) and `\(…\)` (inline) delimiters.
 *
 * Code fences (``` … ```) are left untouched so the markdown renderer
 * handles them normally — math delimiters inside fences are ignored.
 */
fun parseContentSegments(input: String): List<ContentSegment> {
    if (input.isBlank()) return listOf(ContentSegment.Text(input))

    // Pattern matches (in order of priority):
    // 1. Code fences (```...```) — captured to skip over them
    // 2. Block math: $$...$$ or \[...\]
    // 3. Inline math: $...$ or \(...\)
    val pattern = Regex(
        """(```[\s\S]*?```)|""" +                 // Group 1: code fence (skip)
            """(\$\$[\s\S]+?\$\$)|""" +            // Group 2: block math $$...$$
            """(\\\[[\s\S]+?\\])|""" +             // Group 3: block math \[...\]
            """(\$(?!\$)(?:[^$\\]|\\.)+\$)|""" +   // Group 4: inline math $...$
            """(\\\((?:[^)\\]|\\.)+\\\))""",        // Group 5: inline math \(...\)
    )

    val segments = mutableListOf<ContentSegment>()
    var lastIndex = 0

    for (match in pattern.findAll(input)) {
        // Add any text before this match
        if (match.range.first > lastIndex) {
            val text = input.substring(lastIndex, match.range.first)
            if (text.isNotEmpty()) {
                segments.add(ContentSegment.Text(text))
            }
        }

        when {
            // Code fence — emit as plain text (let markdown renderer handle it)
            match.groups[1] != null -> {
                segments.add(ContentSegment.Text(match.value))
            }
            // Block math: $$...$$
            match.groups[2] != null -> {
                val latex = match.value.removeSurrounding("$$")
                if (latex.isNotBlank()) {
                    segments.add(ContentSegment.Math(latex.trim(), displayMode = true))
                }
            }
            // Block math: \[...\]
            match.groups[3] != null -> {
                val latex = match.value.removePrefix("\\[").removeSuffix("\\]")
                if (latex.isNotBlank()) {
                    segments.add(ContentSegment.Math(latex.trim(), displayMode = true))
                }
            }
            // Inline math: $...$
            match.groups[4] != null -> {
                val latex = match.value.removeSurrounding("$")
                if (latex.isNotBlank()) {
                    segments.add(ContentSegment.Math(latex.trim(), displayMode = false))
                }
            }
            // Inline math: \(...\)
            match.groups[5] != null -> {
                val latex = match.value.removePrefix("\\(").removeSuffix("\\)")
                if (latex.isNotBlank()) {
                    segments.add(ContentSegment.Math(latex.trim(), displayMode = false))
                }
            }
        }

        lastIndex = match.range.last + 1
    }

    // Add any remaining text after the last match
    if (lastIndex < input.length) {
        val text = input.substring(lastIndex)
        if (text.isNotEmpty()) {
            segments.add(ContentSegment.Text(text))
        }
    }

    // If nothing was found, return the entire input as text
    if (segments.isEmpty()) {
        return listOf(ContentSegment.Text(input))
    }

    return segments
}

/**
 * Returns true if the content contains any math delimiters that would
 * produce [ContentSegment.Math] segments. Use this as a fast check to
 * avoid the overhead of [parseContentSegments] when there's no math.
 */
fun containsMath(input: String): Boolean {
    return input.contains("$$") ||
        input.contains("\\[") ||
        input.contains("\\(") ||
        // Single $ that isn't $$ — quick heuristic
        Regex("""\$(?!\$)[^$]+\$""").containsMatchIn(input)
}
