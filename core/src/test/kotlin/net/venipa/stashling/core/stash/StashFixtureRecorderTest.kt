package net.venipa.stashling.core.stash

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.venipa.stashling.core.data.StashAccount
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File

/**
 * Records live responses into src/test/resources/fixtures/<OperationName>.json and decodes each one through the typed API,
 * so a query typo or schema drift on the deployed stashdb.org fails here. Skipped unless STASHDB_API_KEY is set:
 *   STASHDB_API_KEY=... ./gradlew :core:testDebugUnitTest --tests '*StashFixtureRecorderTest*'
 * runBlocking, not runTest: the virtual clock would fire HttpTimeout at once on a real network. STASHDB_URL overrides https://stashdb.org. Me/MeKey are never recorded (they carry the account); api_key is redacted anyway.
 */
@EnabledIfEnvironmentVariable(named = "STASHDB_API_KEY", matches = ".+")
class StashFixtureRecorderTest {
    private val account = StashAccount(System.getenv("STASHDB_URL") ?: "https://stashdb.org", System.getenv("STASHDB_API_KEY").orEmpty())
    private val api = StashDbClient(newStashHttpClient()) { account }
    private val pretty = Json { prettyPrint = true }
    private val dir = File("src/test/resources/fixtures").apply { mkdirs() }

    private suspend fun record(query: String, variables: JsonObject?): JsonObject {
        val text =
            api
                .raw(query, variables)
                .replace(Regex("\"api_key\"\\s*:\\s*\"[^\"]*\""), "\"api_key\":\"REDACTED\"")
        val root = StashJson.parseToJsonElement(text).jsonObject
        check(root["errors"] == null) { "${StashQueries.nameOf(query)}: $text" }
        File(dir, "${StashQueries.nameOf(query)}.json").writeText(pretty.encodeToString(JsonObject.serializer(), root) + "\n")
        return root["data"]!!.jsonObject
    }

    private fun input(vararg pairs: Pair<String, String>) = buildJsonObject {
        put(
            "input",
            buildJsonObject {
                pairs.forEach { (k, v) -> put(k, v) }
                put("page", 1)
                put("per_page", 3)
            },
        )
    }

    private fun search(term: String) = buildJsonObject {
        put("term", term)
        put("page", 1)
        put("per", 3)
    }

    private fun id(id: String) = buildJsonObject { put("id", id) }

    @Test
    fun record() = runBlocking {
        val scenes = record(StashQueries.SCENES, input("sort" to "TRENDING", "direction" to "DESC"))
        val sceneId =
            scenes["queryScenes"]!!
                .jsonObject["scenes"]!!
                .jsonArray[0]
                .jsonObject["id"]!!
                .jsonPrimitive.content
        record(StashQueries.SEARCH_SCENES, search("hotel"))
        val scene = record(StashQueries.SCENE, id(sceneId))["findScene"]!!.jsonObject
        val performerId =
            scene["performers"]!!
                .jsonArray[0]
                .jsonObject["performer"]!!
                .jsonObject["id"]!!
                .jsonPrimitive.content
        val studioId = scene["studio"]!!.jsonObject["id"]!!.jsonPrimitive.content
        val tagId =
            scene["tags"]!!
                .jsonArray[0]
                .jsonObject["id"]!!
                .jsonPrimitive.content
        record(StashQueries.PERFORMERS, input("sort" to "POPULARITY", "direction" to "DESC"))
        record(StashQueries.SEARCH_PERFORMERS, search("anna"))
        record(StashQueries.PERFORMER, id(performerId))
        record(StashQueries.STUDIOS, input("sort" to "NAME", "direction" to "ASC"))
        record(StashQueries.STUDIO, id(studioId))
        record(StashQueries.TAGS, input("sort" to "NAME", "direction" to "ASC"))
        record(StashQueries.TAG_CATEGORIES, null)
        record(StashQueries.TAG, id(tagId))

        // The typed calls: filters as the app sends them, and the decoders against the live shapes.
        api.scenes(SceneFilter(tagIds = listOf(tagId), date = DateBound("2020-01-01", before = false)), 1, 3)
        api.scenes(SceneFilter(studioIds = listOf(studioId), performerIds = listOf(performerId)), 1, 3)
        api.scenes(SceneFilter(favorites = Favorites.ALL), 1, 3)
        api.searchScenes("hotel", 1, 3)
        checkNotNull(api.scene(sceneId))
        api.performers(PerformerFilter(studioId = studioId, gender = "FEMALE", favoritesOnly = true), 1, 3)
        api.searchPerformers("anna", 1, 3)
        checkNotNull(api.performer(performerId))
        api.studios(StudioFilter(parentId = studioId, favoritesOnly = true), 1, 3)
        checkNotNull(api.studio(studioId))
        api.tags(TagFilter(names = "a"), 1, 3)
        api.tagCategories()
        checkNotNull(api.tag(tagId))
        api.verify(account.baseUrl, account.apiKey)
    }
}
