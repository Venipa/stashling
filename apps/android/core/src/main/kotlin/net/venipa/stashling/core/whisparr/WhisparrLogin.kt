package net.venipa.stashling.core.whisparr

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/** Own client: no redirects (Whisparr answers /login with 302 either way), cookies kept for this call only. */
object WhisparrLogin {
    /**
     * initialize.json first (works without login when Whisparr has auth off or "disabled for local addresses");
     * on 401/302 POST the forms login, then initialize.json again with the cookie. The cookie is dropped afterwards.
     */
    suspend fun fetchApiKey(
        baseUrl: String,
        user: String?,
        pass: String?,
        ignoreCertErrors: Boolean = false,
        engine: HttpClientEngine = WhisparrClients.engine(ignoreCertErrors),
    ): String {
        val base = normalizeWhisparrUrl(baseUrl)
        val client =
            HttpClient(engine) {
                expectSuccess = false
                followRedirects = false
                install(HttpCookies) { storage = AcceptAllCookiesStorage() }
                install(HttpTimeout) {
                    connectTimeoutMillis = 10_000
                    requestTimeoutMillis = 30_000
                }
            }
        return try {
            client.use { http ->
                apiKeyOf(http.get("$base/initialize.json"))?.let { return it }
                if (user.isNullOrBlank() || pass.isNullOrEmpty()) throw WhisparrError.LoginRequired()
                val login =
                    http.submitForm(
                        "$base/login",
                        parameters {
                            append("username", user)
                            append("password", pass)
                            append("rememberMe", "on")
                        },
                    )
                val location = login.headers[HttpHeaders.Location].orEmpty()
                if (login.status.value == 401 || "loginFailed" in location) throw WhisparrError.BadLogin()
                if (login.status.value !in 300..399) {
                    throw WhisparrError.Http(login.status.value, "Unexpected login response (HTTP ${login.status.value})")
                }
                apiKeyOf(http.get("$base/initialize.json"))
                    // Logged in but still bounced: usually an auth proxy (Authelia etc.) in front of Whisparr.
                    ?: throw WhisparrError.Http(401, "Signed in, but Whisparr did not return an API key. Paste the API key instead.")
            }
        } catch (e: IOException) {
            throw WhisparrError.Network(e)
        } finally {
            engine.close() // HttpClient(engine) leaves a passed-in engine open
        }
    }

    /** null = not signed in (401 or redirect to /login, or an HTML login page from a proxy). */
    private suspend fun apiKeyOf(response: HttpResponse): String? {
        if (response.status.value != 200) return null
        val json = runCatching { whisparrJson.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrNull() ?: return null
        return json["apiKey"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }
}
