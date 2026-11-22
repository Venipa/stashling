package net.venipa.stashling.core.stash

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.venipa.stashling.core.data.StashAccount
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * One decode per query against fixtures/<OperationName>.json. The fixtures start hand-made from the master schema and are
 * replaced by StashFixtureRecorderTest with live responses, so the checks are structural, never exact values.
 */
class StashDecodeTest {
    private val api =
        StashDbClient(
            newStashHttpClient(
                MockEngine { req ->
                    val op =
                        StashJson
                            .parseToJsonElement((req.body as TextContent).text)
                            .jsonObject["operationName"]!!
                            .jsonPrimitive.content
                    val fixture = javaClass.getResource("/fixtures/$op.json") ?: error("no fixture for $op")
                    respond(fixture.readText(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
        ) { StashAccount("https://stashdb.test", "KEY") }

    private fun <T> Page<T>.check() {
        assertTrue(items.isNotEmpty(), "empty page")
        assertTrue(count >= items.size, "count $count < ${items.size}")
    }

    @Test
    fun scenes() = runTest { api.scenes(SceneFilter(sort = SceneSort.TRENDING), 1).check() }

    @Test
    fun searchScenes() = runTest { api.searchScenes("a", 1).check() }

    @Test
    fun scene() = runTest {
        val s = assertNotNull(api.scene("s1"))
        assertTrue(s.id.isNotBlank() && s.images.all { it.url.startsWith("http") })
    }

    @Test
    fun performers() = runTest { api.performers(PerformerFilter(), 1).check() }

    @Test
    fun searchPerformers() = runTest { api.searchPerformers("a", 1).check() }

    @Test
    fun performer() = runTest { assertTrue(assertNotNull(api.performer("p1")).name.isNotBlank()) }

    @Test
    fun studios() = runTest { api.studios(StudioFilter(), 1).check() }

    @Test
    fun studio() = runTest { assertTrue(assertNotNull(api.studio("st1")).name.isNotBlank()) }

    @Test
    fun tags() = runTest { api.tags(TagFilter(), 1).check() }

    @Test
    fun tagCategories() = runTest { assertTrue(api.tagCategories().isNotEmpty()) }

    @Test
    fun tag() = runTest { assertTrue(assertNotNull(api.tag("t1")).name.isNotBlank()) }

    @Test
    fun verify() = runTest { assertTrue(api.verify("https://stashdb.test", "KEY").id.isNotBlank()) }
}
