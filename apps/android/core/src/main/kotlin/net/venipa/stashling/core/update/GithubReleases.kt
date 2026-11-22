package net.venipa.stashling.core.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import kotlin.time.Instant

/** A GitHub release that carries an APK, reduced to what the updater shows and needs. */
data class AppRelease(
    /** Tag without a leading v, e.g. 1.3.0 or 1.4.0-beta.1. */
    val version: String,
    val tag: String,
    /** Release body cleaned for plain-text display ([cleanNotes]). */
    val notes: String,
    val publishedAt: Instant?,
    val pageUrl: String,
    val apkUrl: String,
    val apkSize: Long,
    /** Lower-case hex SHA-256 from the asset's digest, when GitHub provides one. */
    val sha256: String?,
    val prerelease: Boolean,
)

sealed class UpdateError(message: String) : Exception(message) {
    class Http(val status: Int) : UpdateError("HTTP $status")

    class Checksum : UpdateError("checksum mismatch")
}

@Serializable
internal data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
    val digest: String? = null,
)

@Serializable
internal data class GhRelease(
    @SerialName("tag_name") val tag: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GhAsset> = emptyList(),
)

private val UpdateJson = Json { ignoreUnknownKeys = true }

/** Follows redirects: release downloads redirect from github.com to GitHub's object storage. No credentials are ever sent. */
fun newUpdateHttpClient(engine: HttpClientEngine = OkHttp.create()): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 30_000
    }
}

/** Public releases of [repo] ("owner/name"), read anonymously (60 requests an hour per IP is plenty for a daily check). */
class GithubReleases(
    private val repo: String,
    private val client: HttpClient = newUpdateHttpClient(),
) {
    /** Newest non-draft release with an APK asset; pre-releases only when [includePrerelease]. Null when there is none. */
    suspend fun latest(includePrerelease: Boolean): AppRelease? {
        val res =
            client.get("https://api.github.com/repos/$repo/releases?per_page=20") {
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
        if (!res.status.isSuccess()) throw UpdateError.Http(res.status.value)
        return pickLatest(UpdateJson.decodeFromString<List<GhRelease>>(res.bodyAsText()), includePrerelease)
    }

    /** Streams [release]'s APK to [dest], reporting (bytesRead, total); verifies the SHA-256 when known. Deletes [dest] on failure. */
    suspend fun download(
        release: AppRelease,
        dest: File,
        onProgress: (Long, Long) -> Unit,
    ) {
        dest.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            client.prepareGet(release.apkUrl).execute { res ->
                if (!res.status.isSuccess()) throw UpdateError.Http(res.status.value)
                val total = res.contentLength() ?: release.apkSize
                val channel = res.bodyAsChannel()
                val buffer = ByteArray(64 * 1024)
                var read = 0L
                dest.outputStream().use { out ->
                    while (true) {
                        val n = channel.readAvailable(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            if (release.sha256 != null && release.sha256 != hex) throw UpdateError.Checksum()
        } catch (e: Throwable) {
            dest.delete()
            throw e
        }
    }
}

internal fun pickLatest(
    releases: List<GhRelease>,
    includePrerelease: Boolean,
): AppRelease? = releases
    .asSequence()
    .filter { !it.draft && (includePrerelease || !it.prerelease) }
    .mapNotNull { it.toAppRelease() }
    .maxWithOrNull { a, b -> compareVersions(a.version, b.version) }

private fun GhRelease.toAppRelease(): AppRelease? {
    val apk = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
    return AppRelease(
        version = tag.removePrefix("v").removePrefix("V"),
        tag = tag,
        notes = cleanNotes(body.orEmpty()),
        publishedAt = publishedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
        pageUrl = htmlUrl,
        apkUrl = apk.url,
        apkSize = apk.size,
        sha256 = apk.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
        prerelease = prerelease,
    )
}

/** True when [candidate] is a newer version than [current] (both may carry a leading v). */
fun isNewerVersion(
    candidate: String,
    current: String,
): Boolean = compareVersions(candidate.removePrefix("v"), current.removePrefix("v")) > 0

/**
 * Semver-style order: numeric parts first (1.10 > 1.9, missing parts = 0), then a release beats its pre-release
 * (1.2.0 > 1.2.0-beta.1 > 1.2.0-alpha), pre-release parts compared numerically where both are numbers. Build metadata (+…) is ignored.
 */
fun compareVersions(
    a: String,
    b: String,
): Int {
    fun split(v: String): Pair<List<Int>, List<String>> {
        val core = v.substringBefore('+')
        val main = core.substringBefore('-')
        val pre = core.substringAfter('-', "").split('.').filter { it.isNotEmpty() }
        return main.split('.').map { it.toIntOrNull() ?: 0 } to pre
    }
    val (am, ap) = split(a)
    val (bm, bp) = split(b)
    for (i in 0 until maxOf(am.size, bm.size)) {
        val c = am.getOrElse(i) { 0 }.compareTo(bm.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    if (ap.isEmpty() || bp.isEmpty()) return bp.size.compareTo(0) - ap.size.compareTo(0)
    for (i in 0 until maxOf(ap.size, bp.size)) {
        val x = ap.getOrNull(i) ?: return -1
        val y = bp.getOrNull(i) ?: return 1
        val xi = x.toIntOrNull()
        val yi = y.toIntOrNull()
        val c =
            when {
                xi != null && yi != null -> xi.compareTo(yi)
                xi != null -> -1
                yi != null -> 1
                else -> x.compareTo(y)
            }
        if (c != 0) return c
    }
    return 0
}

/**
 * GitHub release notes as plain text: headings lose their #, list markers become •, links keep their text,
 * generated "by @user in <url>" credits and the "Full Changelog" line are dropped, blank runs collapse.
 */
fun cleanNotes(body: String): String = body
    .replace("\r\n", "\n")
    .lines()
    .filterNot { it.trimStart().startsWith("**Full Changelog**") }
    .map { line ->
        line
            .replace(Regex("""\s+by @[\w-]+ in https?://\S+"""), "")
            .replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
            .replace(Regex("""^#{1,6}\s+"""), "")
            .replace(Regex("""^(\s*)[*-]\s+"""), "$1• ")
            .replace("**", "")
            .trimEnd()
    }.joinToString("\n")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()
