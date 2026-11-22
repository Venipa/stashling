package net.venipa.stashling.core.update

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GithubReleasesTest {
    @Test
    fun `version order`() {
        assertTrue(isNewerVersion("v1.3.0", "1.2.0"))
        assertTrue(isNewerVersion("1.10.0", "1.9.9"))
        assertTrue(isNewerVersion("1.2.0", "1.2.0-beta.1"))
        assertTrue(isNewerVersion("1.2.0-beta.2", "1.2.0-beta.1"))
        assertTrue(isNewerVersion("1.2.0-beta.10", "1.2.0-beta.9"))
        assertTrue(isNewerVersion("0.1.0", "0.1.0-dev"))
        assertTrue(isNewerVersion("1.0.0", "0.0.0-abc1234"))
        assertFalse(isNewerVersion("1.2.0", "1.2.0"))
        assertFalse(isNewerVersion("1.2", "1.2.0"))
        assertFalse(isNewerVersion("1.2.0-beta.1", "1.2.0"))
        assertFalse(isNewerVersion("1.2.0+build.5", "1.2.0"))
    }

    @Test
    fun `notes are cleaned for plain text`() {
        val body = "## What's Changed\r\n* feat: filters by @someone in https://github.com/o/r/pull/1\r\n- see [docs](https://x.y)\r\n\r\n\r\n\r\n**Full Changelog**: https://github.com/o/r/compare/a...b"
        assertEquals("What's Changed\n• feat: filters\n• see docs", cleanNotes(body))
    }

    private fun release(
        tag: String,
        pre: Boolean = false,
        draft: Boolean = false,
        apk: Boolean = true,
    ) = """{"tag_name":"$tag","prerelease":$pre,"draft":$draft,"html_url":"https://github.com/o/r/releases/$tag","published_at":"2026-11-20T10:00:00Z",
        |"body":"* x","assets":[${if (apk) """{"name":"app-$tag.apk","browser_download_url":"https://dl/$tag.apk","size":12,"digest":"sha256:ABC"}""" else ""}]}
    """.trimMargin()

    private fun api(json: String) = GithubReleases("o/r", newUpdateHttpClient(MockEngine { respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }))

    @Test
    fun `latest skips drafts, pre-releases and releases without an apk`() = runTest {
        val json = "[${release("v2.0.0", draft = true)},${release("v1.9.0-beta.1", pre = true)},${release("v1.8.0", apk = false)},${release("v1.7.0")},${release("v1.6.0")}]"
        val stable = api(json).latest(includePrerelease = false)!!
        assertEquals("1.7.0", stable.version)
        assertEquals("https://dl/v1.7.0.apk", stable.apkUrl)
        assertEquals("abc", stable.sha256)
        assertEquals("1.9.0-beta.1", api(json).latest(includePrerelease = true)!!.version)
        assertNull(api("[${release("v1.0.0", apk = false)}]").latest(false))
    }

    @Test
    fun `http error is reported`() = runTest {
        val gh = GithubReleases("o/r", newUpdateHttpClient(MockEngine { respond("", HttpStatusCode.Forbidden) }))
        assertEquals(403, assertFailsWith<UpdateError.Http> { gh.latest(false) }.status)
    }

    @Test
    fun `download verifies the checksum`(
        @TempDir dir: File,
    ) = runTest {
        val bytes = "apk-bytes".toByteArray()
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val gh = GithubReleases("o/r", newUpdateHttpClient(MockEngine { respond(bytes, HttpStatusCode.OK) }))
        val rel = AppRelease("1.0.0", "v1.0.0", "", null, "", "https://dl/a.apk", bytes.size.toLong(), sha, false)
        val ok = File(dir, "ok.apk")
        var last = 0L
        gh.download(rel, ok) { read, _ -> last = read }
        assertEquals(bytes.size.toLong(), last)
        assertTrue(ok.readBytes().contentEquals(bytes))

        val bad = File(dir, "bad.apk")
        assertFailsWith<UpdateError.Checksum> { gh.download(rel.copy(sha256 = "00"), bad) { _, _ -> } }
        assertFalse(bad.exists())
    }
}
