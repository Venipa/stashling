package net.venipa.stashling.core.stash

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.venipa.stashling.core.data.StashAccount
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StashDbClientTest {
    private val base = "https://stashdb.test"
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(
        account: StashAccount? = StashAccount(base, "KEY"),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = StashDbClient(newStashHttpClient(MockEngine(handler)), { account })

    private fun HttpRequestData.text(): String = when (val b = body) {
        is TextContent -> b.text
        is OutgoingContent.ByteArrayContent -> b.bytes().decodeToString()
        else -> error("unexpected body ${b::class}")
    }

    private fun HttpRequestData.operation(): String = StashJson
        .parseToJsonElement(text())
        .jsonObject["operationName"]!!
        .jsonPrimitive.content

    @Test
    fun `login posts the form, then reads the key with the session cookie only`() = runTest {
        val api =
            client(account = null) { req ->
                when (req.url.encodedPath) {
                    "/login" -> {
                        val form = req.text()
                        assertTrue("username=me" in form && "password=p%40ss" in form, form)
                        respond("", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, "stashbox=abc123; Path=/; Max-Age=2592000; HttpOnly; Secure"))
                    }

                    "/graphql" -> {
                        assertEquals("stashbox=abc123", req.headers[HttpHeaders.Cookie])
                        assertNull(req.headers["ApiKey"])
                        assertEquals("MeKey", req.operation())
                        respond("""{"data":{"me":{"api_key":"THE-KEY"}}}""", HttpStatusCode.OK, json)
                    }

                    else -> error("unexpected ${req.url}")
                }
            }
        assertEquals("THE-KEY", api.login("$base/", "me", "p@ss"))
    }

    @Test
    fun `login 401 is a bad login`() = runTest {
        val api = client(account = null) { respond("access denied", HttpStatusCode.Unauthorized) }
        assertFailsWith<StashError.BadLogin> { api.login(base, "me", "wrong") }
    }

    @Test
    fun `a rejected key (HTTP 401, empty body) is unauthorized`() = runTest {
        val api = client { respond("", HttpStatusCode.Unauthorized) }
        assertFailsWith<StashError.Unauthorized> { api.scenes(SceneFilter(), 1) }
    }

    @Test
    fun `a missing session (200 with not authorized) is unauthorized`() = runTest {
        val api =
            client {
                respond("""{"errors":[{"message":"not authorized","path":["queryScenes"]}],"data":null}""", HttpStatusCode.OK, json)
            }
        assertFailsWith<StashError.Unauthorized> { api.scenes(SceneFilter(), 1) }
    }

    @Test
    fun `other GraphQL errors keep their messages`() = runTest {
        val api = client { respond("""{"errors":[{"message":"bad input"}],"data":null}""", HttpStatusCode.OK, json) }
        val e = assertFailsWith<StashError.GraphQl> { api.tagCategories() }
        assertEquals(listOf("bad input"), e.messages)
    }

    @Test
    fun `no stored account is unauthorized and sends nothing`() = runTest {
        val api = client(account = null) { error("must not send") }
        assertFailsWith<StashError.Unauthorized> { api.scene("x") }
    }

    @Test
    fun `the key never goes over http`() = runTest {
        val api = client(account = StashAccount("http://stashdb.test", "KEY")) { error("must not send") }
        assertFailsWith<StashError.Network> { api.scene("x") }
        assertFailsWith<StashError.Network> { api.verify("http://stashdb.test", "KEY") }
    }

    @Test
    fun `queries carry the ApiKey header and a gone id is null`() = runTest {
        val api =
            client { req ->
                assertEquals("KEY", req.headers["ApiKey"])
                respond("""{"data":{"findScene":null}}""", HttpStatusCode.OK, json)
            }
        assertNull(api.scene("gone"))
    }
}
