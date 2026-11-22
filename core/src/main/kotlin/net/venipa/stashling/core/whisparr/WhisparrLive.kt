package net.venipa.stashling.core.whisparr

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.venipa.stashling.core.data.WhisparrAccount
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/**
 * One Whisparr SignalR `receiveMessage`. [name] = the resource ("queue", "queue/status", "queue/details", "movie",
 * "moviefile", "command", "version", …), [action] = "updated" | "sync" | "deleted" | null.
 * [id] = resource id; [stashId] for movies; [commandName]/[status] for commands ("MoviesSearch", "completed").
 */
data class WhisparrEvent(
    val name: String,
    val action: String? = null,
    val id: Int? = null,
    val stashId: String? = null,
    val commandName: String? = null,
    val status: String? = null,
)

/** SignalR JSON protocol v1 (ASP.NET Core): messages end with the record separator 0x1E. */
object SignalR {
    const val RS = '\u001e'
    const val HANDSHAKE = "{\"protocol\":\"json\",\"version\":1}$RS"
    const val PING = "{\"type\":6}$RS"

    sealed interface Message {
        data object HandshakeOk : Message

        data object HandshakeError : Message

        data object Ping : Message

        data object Close : Message

        data class Event(
            val event: WhisparrEvent,
        ) : Message

        data object Other : Message
    }

    /** Complete messages in [buffer] and the unfinished rest (a message can span WebSocket frames). */
    fun split(buffer: String): Pair<List<String>, String> {
        val parts = buffer.split(RS)
        return parts.dropLast(1).filter { it.isNotBlank() } to parts.last()
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(message: String): Message {
        val o = runCatching { json.parseToJsonElement(message).jsonObject }.getOrNull() ?: return Message.Other
        val type = o["type"]?.jsonPrimitive?.intOrNull
        return when {
            type == null -> if ("error" in o) Message.HandshakeError else Message.HandshakeOk
            type == 6 -> Message.Ping
            type == 7 -> Message.Close
            type == 1 && o.str("target") == "receiveMessage" -> event((o["arguments"] as? JsonArray)?.firstOrNull() as? JsonObject)
            else -> Message.Other
        }
    }

    private fun event(arg: JsonObject?): Message {
        val name = arg?.str("name") ?: return Message.Other
        val body = arg["body"] as? JsonObject
        val res = body?.get("resource") as? JsonObject
        return Message.Event(
            WhisparrEvent(
                name = name,
                action = body?.str("action"),
                id = res?.get("id")?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() },
                stashId = res?.str("stashId") ?: res?.str("foreignId"),
                commandName = if (name == "command") res?.str("name") else null,
                status = res?.str("status"),
            ),
        )
    }

    private fun JsonObject.str(key: String): String? = runCatching { get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()
}

/**
 * Whisparr's SignalR hub (`/signalr/messages`) while [events] is collected: negotiate, WebSocket, handshake, pings,
 * reconnect with backoff. Auth by the X-Api-Key header (the hub's "SignalR" scheme reads it like `access_token`),
 * so the key is never in a URL. Logs only "Whisparr live: connected/disconnected/event <name>".
 */
class WhisparrLive(
    private val account: WhisparrAccount,
    private val log: (String) -> Unit = { System.err.println(it) },
) {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    val events: Flow<WhisparrEvent> =
        channelFlow {
            val client = WhisparrClients.httpClient(account.apiKey, WhisparrClients.engine(account.ignoreCertErrors)) { install(WebSockets) }
            var backoff = MIN_BACKOFF_MS
            try {
                while (true) {
                    try {
                        if (session(client)) backoff = MIN_BACKOFF_MS
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Class name only: Ktor/OkHttp messages can contain the URL.
                        log("Whisparr live: disconnected (${e::class.simpleName})")
                    }
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
                }
            } finally {
                _connected.value = false
                client.close()
            }
        }

    /** One connection until it ends; true if the handshake succeeded (resets the backoff). */
    private suspend fun ProducerScope<WhisparrEvent>.session(client: HttpClient): Boolean {
        val base = account.baseUrl
        val negotiate = client.post("$base/signalr/messages/negotiate?negotiateVersion=1")
        // Proxy auth pages / redirects / old servers: stay on polling, retry later.
        if (!negotiate.status.isSuccess()) return false.also { log("Whisparr live: disconnected (negotiate ${negotiate.status.value})") }
        val info = Json.parseToJsonElement(negotiate.bodyAsText()).jsonObject
        val token = info["connectionToken"]?.jsonPrimitive?.contentOrNull ?: info["connectionId"]?.jsonPrimitive?.contentOrNull ?: return false
        val transports = (info["availableTransports"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.get("transport")?.jsonPrimitive?.contentOrNull }
        if (transports != null && "WebSockets" !in transports) return false.also { log("Whisparr live: disconnected (no websockets)") }

        val ws =
            client.webSocketSession {
                url("ws" + base.removePrefix("http") + "/signalr/messages?id=" + java.net.URLEncoder.encode(token, "UTF-8"))
                timeout { requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS }
            }
        var handshake = false
        var reason = "stopped"
        try {
            ws.send(Frame.Text(SignalR.HANDSHAKE))
            var buffer = ""
            var lastIn = TimeSource.Monotonic.markNow()
            var lastOut = TimeSource.Monotonic.markNow()
            while (true) {
                // The server drops a client silent for 30 s: ping every 15 s, even while events keep coming.
                if (lastOut.elapsedNow().inWholeMilliseconds >= PING_MS) {
                    ws.send(Frame.Text(SignalR.PING))
                    lastOut = TimeSource.Monotonic.markNow()
                }
                val frame = withTimeoutOrNull(PING_MS) { ws.incoming.receive() }
                // The server pings every 15 s too: long silence means a dead link (Doze, Wi-Fi switch).
                if (frame == null) {
                    if (lastIn.elapsedNow().inWholeMilliseconds >= READ_TIMEOUT_MS) return handshake.also { reason = "timeout" }
                    continue
                }
                lastIn = TimeSource.Monotonic.markNow()
                if (frame is Frame.Close) return handshake.also { reason = "closed" }
                if (frame !is Frame.Text) continue
                val (messages, rest) = SignalR.split(buffer + frame.readText())
                buffer = rest
                for (m in messages) {
                    when (val msg = SignalR.parse(m)) {
                        SignalR.Message.HandshakeOk -> if (!handshake) {
                            handshake = true
                            _connected.value = true
                            log("Whisparr live: connected")
                        }

                        SignalR.Message.HandshakeError, SignalR.Message.Close -> return handshake.also { reason = "server close" }

                        SignalR.Message.Ping -> {
                            ws.send(Frame.Text(SignalR.PING))
                            lastOut = TimeSource.Monotonic.markNow()
                        }

                        is SignalR.Message.Event -> {
                            log("Whisparr live: event ${msg.event.name}")
                            send(msg.event)
                        }

                        SignalR.Message.Other -> Unit
                    }
                }
            }
        } finally {
            if (_connected.value) log("Whisparr live: disconnected ($reason)")
            _connected.value = false
            withContext(NonCancellable) { withTimeoutOrNull(1_000) { runCatching { ws.close() } } }
        }
    }

    private companion object {
        const val PING_MS = 15_000L
        const val READ_TIMEOUT_MS = 35_000L
        const val MIN_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 300_000L
    }
}
