package `in`.arijitk.synapse.update

import `in`.arijitk.synapse.APP_VERSION
import `in`.arijitk.synapse.isDebug
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Checks for app updates via GitHub Releases API.
 * Fully shared across all platforms (uses Ktor common client).
 */
object UpdateChecker {
    private const val OWNER = "a1kundu"
    private const val REPO = "synapse"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(this@UpdateChecker.json)
            }
        }
    }

    /** Current build channel (debug/release). */
    val channel: String get() = if (isDebug) "debug" else "release"

    /** Current build number extracted from APP_VERSION, or null if dev build. */
    val currentBuildNumber: Int?
        get() {
            if (APP_VERSION == "APP_VERSION_PLACEHOLDER") return null
            return APP_VERSION.split(".").firstOrNull()?.toIntOrNull()
        }

    /**
     * Check GitHub releases for a newer version.
     * Returns the latest matching update, or null if already up-to-date.
     * Returns null (does not throw) on network/parse errors.
     */
    suspend fun checkForUpdate(): AppUpdate? = withContext(Dispatchers.Default) {
        try {
            val releases: List<GitHubRelease> = client.get(
                "https://api.github.com/repos/$OWNER/$REPO/releases",
            ) {
                header("Accept", "application/vnd.github+json")
            }.body()

            val ch = channel
            var latest: AppUpdate? = null

            for (release in releases) {
                val tag = release.tagName
                if (!tag.contains("-$ch-", ignoreCase = true)) continue

                val buildNum = extractBuildNumber(tag) ?: continue
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    ?: continue

                if (latest == null || buildNum > latest.buildNumber) {
                    latest = AppUpdate(
                        buildNumber = buildNum,
                        version = extractVersion(tag),
                        tagName = tag,
                        downloadUrl = apkAsset.browserDownloadUrl,
                        fileSize = apkAsset.size,
                        releaseName = release.name ?: tag,
                        changelog = extractChangelog(release.body ?: ""),
                    )
                }
            }

            if (latest == null) return@withContext null

            val current = currentBuildNumber
            if (current == null || latest.buildNumber > current) latest else null
        } catch (_: Exception) {
            null
        }
    }

    // Tag format: v{buildNumber}-{channel}-{sha}
    private fun extractBuildNumber(tag: String): Int? {
        val match = Regex("""^v(\d+)-""").find(tag)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractVersion(tag: String): String {
        val parts = tag.removePrefix("v").split("-")
        return if (parts.size >= 3) "${parts[0]}.0.0-${parts.last()}" else tag
    }

    private fun extractChangelog(body: String): String {
        val idx = body.indexOf("### Changelog")
        if (idx >= 0) return body.substring(idx + "### Changelog".length).trim()
        return body.split("\n")
            .filter { it.trimStart().startsWith("\u2022") || it.trimStart().startsWith("- ") }
            .joinToString("\n")
    }
}
