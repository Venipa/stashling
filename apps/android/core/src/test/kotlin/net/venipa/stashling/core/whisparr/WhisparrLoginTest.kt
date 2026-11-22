package net.venipa.stashling.core.whisparr

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WhisparrLoginTest {
    private val init = """{ "apiRoot": "/whisparr/api/v3", "apiKey": "k123", "urlBase": "/whisparr" }"""
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `initialize json without login`() = runTest {
        val paths = mutableListOf<String>()
        val engine =
            MockEngine { req ->
                paths += req.url.encodedPath
                respond(init, HttpStatusCode.OK, json)
            }
        assertEquals("k123", WhisparrLogin.fetchApiKey("192.168.1.5:6969/whisparr/", null, null, engine = engine))
        assertEquals(listOf("/whisparr/initialize.json"), paths)
    }

    @Test
    fun `302 login sets cookie then initialize json`() = runTest {
        var signedIn = false
        var form: Map<String, String> = emptyMap()
        val engine =
            MockEngine { req ->
                when (req.url.encodedPath) {
                    "/initialize.json" ->
                        if (req.headers[HttpHeaders.Cookie]?.contains("WhisparrAuth=abc") == true) {
                            respond(init, HttpStatusCode.OK, json)
                        } else {
                            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/login?returnUrl=%2Finitialize.json"))
                        }

                    "/login" -> {
                        signedIn = true
                        form = (req.body as FormDataContent).formData.let { p -> p.names().associateWith { p[it]!! } }
                        respond(
                            "",
                            HttpStatusCode.Found,
                            headersOf(HttpHeaders.Location to listOf("/"), HttpHeaders.SetCookie to listOf("WhisparrAuth=abc; path=/; httponly")),
                        )
                    }

                    else -> error("unexpected ${req.url}")
                }
            }
        assertEquals("k123", WhisparrLogin.fetchApiKey("http://whisparr.lan", "me", "pw", engine = engine))
        assertEquals(true, signedIn)
        assertEquals(mapOf("username" to "me", "password" to "pw", "rememberMe" to "on"), form)
    }

    @Test
    fun `302 with loginFailed is a bad login`() = runTest {
        val engine =
            MockEngine { req ->
                when (req.url.encodedPath) {
                    "/initialize.json" -> respond("", HttpStatusCode.Unauthorized)
                    else -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/login?returnUrl=&loginFailed=true"))
                }
            }
        assertFailsWith<WhisparrError.BadLogin> { WhisparrLogin.fetchApiKey("http://whisparr.lan", "me", "bad", engine = engine) }
    }

    @Test
    fun `no credentials when login needed`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        assertFailsWith<WhisparrError.LoginRequired> { WhisparrLogin.fetchApiKey("http://whisparr.lan", null, null, engine = engine) }
    }

    @Test
    fun `url normaliser`() {
        assertEquals("http://192.168.1.5:6969", normalizeWhisparrUrl(" 192.168.1.5:6969/ "))
        assertEquals("https://w.example.com/whisparr", normalizeWhisparrUrl("https://w.example.com/whisparr/api/v3/"))
        assertEquals("http://whisparr.lan", normalizeWhisparrUrl("HTTP://whisparr.lan"))
        assertFailsWith<IllegalArgumentException> { normalizeWhisparrUrl("ftp://x") }
        assertFailsWith<IllegalArgumentException> { normalizeWhisparrUrl("") }
    }
}
