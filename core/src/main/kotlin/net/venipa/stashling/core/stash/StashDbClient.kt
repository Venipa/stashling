package net.venipa.stashling.core.stash

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import net.venipa.stashling.core.data.StashAccount

internal val StashJson = Json { ignoreUnknownKeys = true }

/**
 * The StashDB client. It must not follow redirects: OkHttp strips only Authorization on a cross-host redirect, never the
 * custom ApiKey header. Keep it separate from the Coil and Whisparr clients so the key never reaches another host.
 */
fun newStashHttpClient(engine: HttpClientEngine = OkHttp.create()): HttpClient = HttpClient(engine) {
    followRedirects = false
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 8_000
        socketTimeoutMillis = 20_000
    }
}

/** [account] is read per call (SecretStore.stash.value); null = signed out → [StashError.Unauthorized]. */
class StashDbClient(
    private val client: HttpClient,
    private val account: () -> StashAccount?,
) : StashDbApi {
    override suspend fun login(baseUrl: String, username: String, password: String): String {
        val base = checkedBase(baseUrl)
        // stash-box handleLogin: form username/password → 200 + Set-Cookie stashbox=…, 401 on a wrong login, no redirect.
        val res =
            call {
                client.submitForm(
                    "$base/login",
                    parameters {
                        append("username", username)
                        append("password", password)
                    },
                )
            }
        when {
            res.status.value == 401 -> throw StashError.BadLogin()
            res.status.value !in 200..299 -> throw StashError.Http(res.status.value)
        }
        val cookie =
            res.headers
                .getAll(HttpHeaders.SetCookie)
                .orEmpty()
                .map { it.substringBefore(';').trim() }
                .firstOrNull { it.startsWith("$SESSION_COOKIE=") }
                ?: throw StashError.GraphQl(listOf("login returned no session cookie"))
        val me = query(base, Auth.Cookie(cookie), StashQueries.ME_KEY, null, "me", serializer<MeDto>())
        return me?.apiKey?.takeIf { it.isNotBlank() } ?: throw StashError.Unauthorized()
    }

    override suspend fun verify(baseUrl: String, apiKey: String): Me {
        val me = query(checkedBase(baseUrl), Auth.Key(apiKey), StashQueries.ME, null, "me", serializer<MeDto>())
        return Me(me?.id ?: throw StashError.Unauthorized(), me.name.orEmpty())
    }

    override suspend fun scenes(filter: SceneFilter, page: Int, perPage: Int): Page<SceneCard> = listed(StashQueries.SCENES, input(filter.toInput(), page, perPage), "queryScenes", serializer<ScenesResult>())
        .let { r -> Page(r.scenes.map { it.toCard() }, page, perPage, r.count) }

    override suspend fun searchScenes(term: String, page: Int, perPage: Int): Page<SceneCard> = listed(StashQueries.SEARCH_SCENES, searchVars(term, page, perPage), "searchScenes", serializer<ScenesResult>())
        .let { r -> Page(r.scenes.map { it.toCard() }, page, perPage, r.count) }

    override suspend fun scene(id: String): Scene? = stored(StashQueries.SCENE, idVars(id), "findScene", serializer<SceneDto>())?.toModel()

    override suspend fun performers(filter: PerformerFilter, page: Int, perPage: Int): Page<Performer> = listed(StashQueries.PERFORMERS, input(filter.toInput(), page, perPage), "queryPerformers", serializer<PerformersResult>())
        .let { r -> Page(r.performers.map { it.toModel() }, page, perPage, r.count) }

    override suspend fun searchPerformers(term: String, page: Int, perPage: Int): Page<Performer> = listed(StashQueries.SEARCH_PERFORMERS, searchVars(term, page, perPage), "searchPerformers", serializer<PerformersResult>())
        .let { r -> Page(r.performers.map { it.toModel() }, page, perPage, r.count) }

    override suspend fun performer(id: String): Performer? = stored(StashQueries.PERFORMER, idVars(id), "findPerformer", serializer<PerformerDto>())?.toModel()

    override suspend fun studios(filter: StudioFilter, page: Int, perPage: Int): Page<Studio> = listed(StashQueries.STUDIOS, input(filter.toInput(), page, perPage), "queryStudios", serializer<StudiosResult>())
        .let { r -> Page(r.studios.map { it.toModel() }, page, perPage, r.count) }

    override suspend fun studio(id: String): Studio? = stored(StashQueries.STUDIO, idVars(id), "findStudio", serializer<StudioDto>())?.toModel()

    override suspend fun tags(filter: TagFilter, page: Int, perPage: Int): Page<Tag> = listed(StashQueries.TAGS, input(filter.toInput(), page, perPage), "queryTags", serializer<TagsResult>())
        .let { r -> Page(r.tags.map { it.toModel() }, page, perPage, r.count) }

    override suspend fun tagCategories(): List<TagCategory> = listed(StashQueries.TAG_CATEGORIES, null, "queryTagCategories", serializer<TagCategoriesResult>()).categories.map { it.toModel() }

    override suspend fun tag(id: String): Tag? = stored(StashQueries.TAG, idVars(id), "findTag", serializer<TagDto>())?.toModel()

    override suspend fun setFavoritePerformer(id: String, favorite: Boolean) {
        stored(StashQueries.FAV_PERFORMER, favVars(id, favorite), "favoritePerformer", serializer<Boolean>())
    }

    override suspend fun setFavoriteStudio(id: String, favorite: Boolean) {
        stored(StashQueries.FAV_STUDIO, favVars(id, favorite), "favoriteStudio", serializer<Boolean>())
    }

    /** Raw response text of [query] with the stored account; for the fixture recorder only. */
    internal suspend fun raw(query: String, variables: JsonObject?): String {
        val acc = account() ?: throw StashError.Unauthorized()
        return send(checkedBase(acc.baseUrl), Auth.Key(acc.apiKey), query, variables).bodyAsText()
    }

    /** For root fields the schema declares non-null (query* results). */
    private suspend fun <T> listed(query: String, variables: JsonObject?, field: String, strategy: DeserializationStrategy<T>): T = stored(query, variables, field, strategy) ?: throw StashError.GraphQl(listOf("$field returned null"))

    private suspend fun <T> stored(query: String, variables: JsonObject?, field: String, strategy: DeserializationStrategy<T>): T? {
        val acc = account() ?: throw StashError.Unauthorized()
        return query(checkedBase(acc.baseUrl), Auth.Key(acc.apiKey), query, variables, field, strategy)
    }

    /** Runs [query] and decodes `data.<field>`; null when that field is null (find* of a gone id). */
    private suspend fun <T> query(
        base: String,
        auth: Auth,
        query: String,
        variables: JsonObject?,
        field: String,
        strategy: DeserializationStrategy<T>,
    ): T? {
        val res = send(base, auth, query, variables)
        if (res.status.value == 401) throw StashError.Unauthorized()
        val text = call { res.bodyAsText() }
        try {
            // gqlgen answers a query the deployed schema rejects with 422 + errors[] (schema drift vs master): keep the messages.
            if (res.status.value !in 200..299 && res.status.value != 422) throw StashError.Http(res.status.value)
            val root = StashJson.parseToJsonElement(text).jsonObject
            val errors = (root["errors"] as? JsonArray)?.mapNotNull { e -> e.jsonObject["message"]?.jsonPrimitive?.contentOrNull }
            if (res.status.value == 422 && errors.isNullOrEmpty()) throw StashError.Http(422)
            if (!errors.isNullOrEmpty()) {
                // Diagnosable in logcat (System.err): operation + server messages only, never the key or variables.
                System.err.println("Stashling StashDB ${StashQueries.nameOf(query)} HTTP ${res.status.value}: ${errors.joinToString(" | ")}")
                // No key / expired cookie: HTTP 200 with errors[].message "not authorized" (seen live 2026-11-22).
                if (errors.any { it.contains("not authorized", ignoreCase = true) }) throw StashError.Unauthorized()
                throw StashError.GraphQl(errors)
            }
            val value = (root["data"] as? JsonObject)?.get(field)
            if (value == null || value is JsonNull) return null
            return StashJson.decodeFromJsonElement(strategy, value)
        } catch (e: StashError) {
            throw e
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException: an unreadable or drifted response.
            throw StashError.Network(e)
        }
    }

    private suspend fun send(base: String, auth: Auth, query: String, variables: JsonObject?): HttpResponse {
        val body =
            buildJsonObject {
                put("operationName", StashQueries.nameOf(query))
                put("query", query)
                if (variables != null) put("variables", variables)
            }
        return call {
            client.post("$base/graphql") {
                when (auth) {
                    is Auth.Key -> header(API_KEY_HEADER, auth.key)
                    is Auth.Cookie -> header(HttpHeaders.Cookie, auth.cookie)
                }
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }
    }

    private sealed interface Auth {
        class Key(val key: String) : Auth

        class Cookie(val cookie: String) : Auth
    }

    internal companion object {
        const val API_KEY_HEADER = "ApiKey"
        const val SESSION_COOKIE = "stashbox"

        /** The key travels only over https; trailing slashes are dropped. */
        fun checkedBase(baseUrl: String): String {
            val base = baseUrl.trim().trimEnd('/')
            if (!base.startsWith("https://", ignoreCase = true) || base.length <= "https://".length) {
                throw StashError.Network(IllegalArgumentException("StashDB URL must be https"))
            }
            return base
        }

        private suspend fun <T> call(block: suspend () -> T): T = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: StashError) {
            throw e
        } catch (e: Exception) {
            throw StashError.Network(e)
        }

        private fun input(fields: JsonObject, page: Int, perPage: Int) = buildJsonObject {
            put(
                "input",
                buildJsonObject {
                    fields.forEach { (k, v) -> put(k, v) }
                    put("page", page)
                    put("per_page", perPage)
                },
            )
        }

        private fun searchVars(term: String, page: Int, perPage: Int) = buildJsonObject {
            put("term", term)
            put("page", page)
            put("per", perPage)
        }

        private fun idVars(id: String) = buildJsonObject { put("id", id) }

        private fun favVars(id: String, favorite: Boolean) = buildJsonObject {
            put("id", id)
            put("f", favorite)
        }

        private fun ids(values: List<String>, modifier: String): JsonElement = buildJsonObject {
            put("value", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
            put("modifier", modifier)
        }

        internal fun SceneFilter.toInput() = buildJsonObject {
            title?.takeIf { it.isNotBlank() }?.let { put("title", it) }
            if (studioIds.isNotEmpty()) put("studios", ids(studioIds, "INCLUDES"))
            parentStudioId?.let { put("parentStudio", it) }
            if (performerIds.isNotEmpty()) put("performers", ids(performerIds, "INCLUDES_ALL"))
            if (tagIds.isNotEmpty()) {
                put("tags", ids(tagIds, if (allTags) "INCLUDES_ALL" else "INCLUDES"))
            } else if (excludeTagIds.isNotEmpty()) {
                put("tags", ids(excludeTagIds, "EXCLUDES"))
            }
            date?.let {
                put(
                    "date",
                    buildJsonObject {
                        put("value", it.date)
                        put("modifier", if (it.before) "LESS_THAN" else "GREATER_THAN")
                    },
                )
            }
            favorites?.let { put("favorites", it.name) }
            put("sort", sort.name)
            put("direction", direction.name)
        }

        internal fun PerformerFilter.toInput() = buildJsonObject {
            names?.takeIf { it.isNotBlank() }?.let { put("names", it) }
            studioId?.let { put("studio_id", it) }
            gender?.let { put("gender", it) }
            // false would mean "not a favorite"; the filter only narrows to favorites.
            if (favoritesOnly) put("is_favorite", true)
            put("sort", sort.name)
            put("direction", direction.name)
        }

        internal fun StudioFilter.toInput() = buildJsonObject {
            names?.takeIf { it.isNotBlank() }?.let { put("names", it) }
            parentId?.let { put("parent", ids(listOf(it), "EQUALS")) }
            if (favoritesOnly) put("is_favorite", true)
            put("sort", sort.name)
            put("direction", direction.name)
        }

        internal fun TagFilter.toInput() = buildJsonObject {
            names?.takeIf { it.isNotBlank() }?.let { put("names", it) }
            categoryId?.let { put("category_id", it) }
            put("sort", sort.name)
            put("direction", direction.name)
        }
    }
}
