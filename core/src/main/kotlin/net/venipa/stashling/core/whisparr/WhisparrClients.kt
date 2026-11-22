package net.venipa.stashling.core.whisparr

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import net.venipa.stashling.core.data.WhisparrAccount
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object WhisparrClients {
    fun create(account: WhisparrAccount): WhisparrApi = KtorWhisparrApi(httpClient(account.apiKey, engine(account.ignoreCertErrors)), account.baseUrl)

    /** OkHttp engine for Whisparr only; [ignoreCertErrors] = owner's per-connection opt-in (self-signed homelab TLS). */
    fun engine(ignoreCertErrors: Boolean): HttpClientEngine = OkHttp.create {
        if (ignoreCertErrors) {
            config {
                val trustAll = TrustAllManager()
                val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustAll), SecureRandom()) }
                sslSocketFactory(ssl.socketFactory, trustAll)
                hostnameVerifier { _, _ -> true }
            }
        }
    }

    internal fun httpClient(
        apiKey: String,
        engine: HttpClientEngine,
    ): HttpClient = HttpClient(engine) {
        expectSuccess = false
        // Ktor keeps custom headers on cross-host redirects (it only strips Authorization): never follow, or an auth proxy's 302 gets the key.
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }
        defaultRequest { header("X-Api-Key", apiKey) }
    }
}

@Suppress("CustomX509TrustManager", "TrustAllX509TrustManager") // opt-in per connection, Whisparr client only
private class TrustAllManager : X509TrustManager {
    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) = Unit

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) = Unit

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/**
 * "192.168.1.5:6969", "whisparr.lan/whisparr/", "https://x.example" → scheme://host[:port][/urlBase], no trailing slash.
 * No scheme means http (LAN default). Throws IllegalArgumentException on anything else.
 */
fun normalizeWhisparrUrl(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    require(trimmed.isNotEmpty()) { "Enter the Whisparr address" }
    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
    val uri = runCatching { URI(withScheme) }.getOrElse { throw IllegalArgumentException("Not a valid address: $input") }
    val scheme = uri.scheme?.lowercase()
    require(scheme == "http" || scheme == "https") { "Address must start with http:// or https://" }
    require(!uri.host.isNullOrBlank()) { "Not a valid address: $input" }
    require(uri.rawQuery == null && uri.rawFragment == null) { "Remove the ?query or #part from the address" }
    val port = if (uri.port == -1) "" else ":${uri.port}"
    val path = (uri.rawPath ?: "").trimEnd('/').removeSuffix("/api/v3")
    return "$scheme://${uri.host}$port$path"
}
