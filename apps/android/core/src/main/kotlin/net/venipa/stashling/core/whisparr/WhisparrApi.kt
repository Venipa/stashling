package net.venipa.stashling.core.whisparr

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.serializer
import java.io.IOException
import kotlin.time.Instant

/** Throws [WhisparrError]. Base "$baseUrl/api/v3", header X-Api-Key. */
interface WhisparrApi {
    suspend fun status(): SystemStatus

    suspend fun rootFolders(): List<RootFolder>

    suspend fun qualityProfiles(): List<QualityProfile>

    suspend fun tags(): List<WhisparrTag>

    /** Whisparr lowercases [label]; the result has the stored label. */
    suspend fun createTag(label: String): WhisparrTag

    suspend fun lookupScene(stashId: String): List<SearchResult>

    suspend fun movieByStashId(stashId: String): List<WhisparrScene>

    /** Whole library (GET movie): one call for the per-tile badges. */
    suspend fun movies(): List<LibraryEntry>

    suspend fun movie(id: Int): WhisparrScene

    suspend fun addMovie(body: JsonObject): WhisparrScene

    suspend fun updateMovie(body: JsonObject): WhisparrScene

    /** PUT movie/editor: flips only `monitored`, so a partial resource can't clobber path/profile. */
    suspend fun setMonitored(
        movieIds: List<Int>,
        monitored: Boolean,
    )

    suspend fun command(
        name: String,
        movieIds: List<Int>,
    ): CommandRes

    suspend fun command(id: Int): CommandRes

    suspend fun releases(movieId: Int): List<Release>

    suspend fun grab(
        guid: String,
        indexerId: Int,
        movieId: Int,
    )

    suspend fun queue(movieId: Int? = null): List<QueueItem>

    suspend fun removeFromQueue(
        id: Int,
        blocklist: Boolean,
    )

    suspend fun historySince(date: Instant): List<HistoryItem>
}

internal val whisparrJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

/** [client] comes from [WhisparrClients]; it already carries the X-Api-Key header. */
class KtorWhisparrApi(
    private val client: HttpClient,
    baseUrl: String,
) : WhisparrApi {
    private val api = "${normalizeWhisparrUrl(baseUrl)}/api/v3"

    override suspend fun status(): SystemStatus = get("system/status")

    override suspend fun rootFolders(): List<RootFolder> = get("rootfolder")

    override suspend fun qualityProfiles(): List<QualityProfile> = get("qualityprofile")

    override suspend fun tags(): List<WhisparrTag> = get("tag")

    override suspend fun createTag(label: String): WhisparrTag = decode(send(HttpMethod.Post, "tag", buildJsonObject { put("label", label) }))

    override suspend fun lookupScene(stashId: String): List<SearchResult> = get("lookup/scene") { parameter("term", "stash:$stashId") }

    override suspend fun movieByStashId(stashId: String): List<WhisparrScene> = get("movie") { parameter("stashId", stashId) }

    // The full library can be ~100 MB of JSON: stream-decode just the slim entries instead of building one String (OOM).
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override suspend fun movies(): List<LibraryEntry> {
        val stmt = client.prepareRequest("$api/movie") {
            header("Accept", "application/json")
            timeout { requestTimeoutMillis = 120_000 }
        }
        return try {
            stmt.execute { response ->
                if (response.status.value == 401) throw WhisparrError.Unauthorized()
                if (!response.status.isSuccess()) throw WhisparrError.Http(response.status.value, "HTTP ${response.status.value}")
                response.bodyAsChannel().toInputStream().use { whisparrJson.decodeFromStream(ListSerializer(LibraryEntry.serializer()), it) }
            }
        } catch (e: IOException) {
            throw WhisparrError.Network(e)
        } catch (e: IllegalArgumentException) {
            throw WhisparrError.Http(200, "Unexpected reply from Whisparr (not its API?)")
        }
    }

    override suspend fun movie(id: Int): WhisparrScene = get("movie/$id")

    override suspend fun addMovie(body: JsonObject): WhisparrScene = decode(send(HttpMethod.Post, "movie", body))

    override suspend fun updateMovie(body: JsonObject): WhisparrScene {
        val id = body["id"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("movie body without id")
        return decode(send(HttpMethod.Put, "movie/$id", body))
    }

    override suspend fun setMonitored(
        movieIds: List<Int>,
        monitored: Boolean,
    ) {
        send(
            HttpMethod.Put,
            "movie/editor",
            buildJsonObject {
                putJsonArray("movieIds") { movieIds.forEach { add(JsonPrimitive(it)) } }
                put("monitored", monitored)
            },
        )
    }

    override suspend fun command(
        name: String,
        movieIds: List<Int>,
    ): CommandRes = decode(
        send(
            HttpMethod.Post,
            "command",
            buildJsonObject {
                put("name", name)
                putJsonArray("movieIds") { movieIds.forEach { add(JsonPrimitive(it)) } }
            },
        ),
    )

    override suspend fun command(id: Int): CommandRes = get("command/$id")

    // Whisparr searches every indexer inside this request.
    override suspend fun releases(movieId: Int): List<Release> = get("release") {
        parameter("movieId", movieId)
        timeout {
            requestTimeoutMillis = RELEASES_TIMEOUT_MS
            socketTimeoutMillis = RELEASES_TIMEOUT_MS
        }
    }

    override suspend fun grab(
        guid: String,
        indexerId: Int,
        movieId: Int,
    ) {
        send(HttpMethod.Post, "release", grabBody(guid, indexerId, movieId))
    }

    override suspend fun queue(movieId: Int?): List<QueueItem> = get("queue/details") {
        if (movieId != null) parameter("movieId", movieId)
        parameter("includeMovie", true)
    }

    override suspend fun removeFromQueue(
        id: Int,
        blocklist: Boolean,
    ) {
        call(HttpMethod.Delete, "queue/$id") {
            parameter("removeFromClient", true)
            parameter("blocklist", blocklist)
        }
    }

    override suspend fun historySince(date: Instant): List<HistoryItem> = get("history/since") {
        parameter("date", date.toString())
        parameter("includeMovie", true)
    }

    private suspend inline fun <reified T> get(
        path: String,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): T = decode(call(HttpMethod.Get, path, block))

    private suspend fun send(
        method: HttpMethod,
        path: String,
        body: JsonObject,
    ): String = call(method, path) {
        contentType(ContentType.Application.Json)
        setBody(body.toString())
    }

    private suspend fun call(
        method: HttpMethod,
        path: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): String {
        val response: HttpResponse =
            try {
                client.request("$api/$path") {
                    this.method = method
                    header("Accept", "application/json")
                    block()
                }
            } catch (e: IOException) {
                throw WhisparrError.Network(e)
            }
        val text =
            try {
                response.bodyAsText()
            } catch (e: IOException) {
                throw WhisparrError.Network(e)
            }
        if (response.status.value in 300..399) throw WhisparrError.Http(response.status.value, "Whisparr redirected (auth proxy in front of /api?)")
        if (response.status.value == 401) throw WhisparrError.Unauthorized()
        if (!response.status.isSuccess()) throw WhisparrError.Http(response.status.value, errorText(text, response.status.value))
        return text
    }

    // Non-JSON 200s (wrong urlBase, proxy login page) must surface as WhisparrError, callers only catch that.
    private inline fun <reified T> decode(text: String): T = try {
        whisparrJson.decodeFromString(serializer<T>(), text)
    } catch (e: IllegalArgumentException) { // SerializationException is one
        throw WhisparrError.Http(200, "Unexpected reply from Whisparr (not its API?)")
    }

    companion object {
        const val RELEASES_TIMEOUT_MS = 300_000L // Indexer searches may take up to 5 min
    }
}

/** Grab body: the release cache is keyed by indexerId + guid, movieId names the target. */
internal fun grabBody(
    guid: String,
    indexerId: Int,
    movieId: Int,
): JsonObject = buildJsonObject {
    put("guid", guid)
    put("indexerId", indexerId)
    put("movieId", movieId)
}

/** Whisparr errors: validation `[{errorMessage}]`, or `{message}`, or plain text. */
internal fun errorText(
    body: String,
    status: Int,
): String {
    val parsed = runCatching { whisparrJson.parseToJsonElement(body) }.getOrNull()
    val messages =
        when (parsed) {
            is JsonArray -> parsed.mapNotNull { runCatching { it.jsonObject["errorMessage"]?.jsonPrimitive?.contentOrNull }.getOrNull() }
            is JsonObject -> listOfNotNull(parsed["message"]?.jsonPrimitive?.contentOrNull)
            else -> emptyList()
        }
    return messages.joinToString("\n").ifBlank { body.take(200).ifBlank { "HTTP $status" } }
}
