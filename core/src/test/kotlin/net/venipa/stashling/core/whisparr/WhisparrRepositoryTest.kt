package net.venipa.stashling.core.whisparr

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.venipa.stashling.core.data.Settings
import net.venipa.stashling.core.data.SettingsStore
import net.venipa.stashling.core.data.WhisparrDefaults
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class WhisparrRepositoryTest {
    private val scene = WhisparrScene(id = 7, title = "S", foreignId = "uuid-1", monitored = true)

    private fun queueItem(
        status: String = "downloading",
        state: String? = "downloading",
    ) = QueueItem(id = 1, movieId = 7, size = 100.0, sizeleft = 25.0, status = status, trackedDownloadState = state)

    @Test
    fun `panel state derivation`() {
        assertIs<PanelState.Missing>(derivePanelState(scene, emptyList(), null))
        assertIs<PanelState.Downloaded>(derivePanelState(scene.copy(hasFile = true), emptyList(), null))
        val q = derivePanelState(scene.copy(hasFile = true), listOf(queueItem()), null)
        assertIs<PanelState.InQueue>(q)
        assertEquals(0.75, q.item.progress)
        assertIs<PanelState.Failed>(derivePanelState(scene, listOf(queueItem(state = "failedPending")), null))
        assertIs<PanelState.Failed>(derivePanelState(scene, listOf(queueItem(status = "failed", state = null)), null))
        assertIs<PanelState.Missing>(derivePanelState(scene, listOf(queueItem().copy(movieId = 99)), null))
        assertIs<PanelState.Searching>(derivePanelState(scene, emptyList(), CommandRes(3, "MoviesSearch", "started")))
        assertIs<PanelState.Missing>(derivePanelState(scene, emptyList(), CommandRes(3, "MoviesSearch", "completed")))
        assertIs<PanelState.Searching>(derivePanelState(scene, listOf(queueItem(state = "failedPending")), CommandRes(3, "MoviesSearch", "queued")))
        assertIs<PanelState.Failed>(derivePanelState(scene, listOf(queueItem(state = "failedPending")), CommandRes(3, "MoviesSearch", "completed")))
    }

    @Test
    fun `add body matches the web UI shape`() {
        val body = addMovieBody(whisparrJson.parseToJsonElement(LOOKUP).jsonObject["movie"]!!.jsonObject, WhisparrDefaults("/data/scenes", 4))
        val expected =
            whisparrJson.parseToJsonElement(
                """{"id":0,"title":"Some Scene","foreignId":"uuid-1","stashId":"uuid-1","tpdbId":null,"tmdbId":0,
                "images":[{"coverType":"screenshot","remoteUrl":"https://x/y.jpg"}],"monitored":true,"qualityProfileId":4,
                "rootFolderPath":"/data/scenes","tags":[],"addOptions":{"monitor":"movieOnly","searchForMovie":false}}""",
            )
        assertEquals(expected, body)
    }

    @Test
    fun `add, search and grab request bodies over http`() = runTest {
        val sent = mutableListOf<Pair<String, JsonObject?>>()
        val engine =
            MockEngine { req ->
                val body = (req.body as? TextContent)?.text?.let { whisparrJson.parseToJsonElement(it).jsonObject }
                sent += "${req.method.value} ${req.url.encodedPath}" to body
                if (req.url.encodedPath.endsWith("lookup/scene")) assertEquals("stash:uuid-1", req.url.parameters["term"])
                assertEquals("key", req.headers["X-Api-Key"])
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                when (req.url.encodedPath) {
                    "/w/api/v3/lookup/scene" -> respond("[$LOOKUP]", HttpStatusCode.OK, json)
                    "/w/api/v3/movie" -> respond("""{"id":7,"title":"Some Scene","foreignId":"uuid-1"}""", HttpStatusCode.Created, json)
                    "/w/api/v3/command" -> respond("""{"id":42,"name":"MoviesSearch","status":"queued"}""", HttpStatusCode.Created, json)
                    "/w/api/v3/release" -> respond("{}", HttpStatusCode.OK, json)
                    else -> error("unexpected ${req.url}")
                }
            }
        val api = KtorWhisparrApi(WhisparrClients.httpClient("key", engine), "http://h:6969/w")
        val repo = WhisparrRepository(api, FakeSettings(Settings(whisparr = WhisparrDefaults("/data/scenes", 4))))

        val state = repo.add("uuid-1", AddMode.QUEUE)
        assertEquals(PanelState.Searching(WhisparrScene(id = 7, title = "Some Scene", foreignId = "uuid-1"), 42), state)
        assertEquals(false, sent.single { it.first == "POST /w/api/v3/movie" }.second!!["addOptions"]!!.jsonObject["searchForMovie"].toString().toBoolean())
        assertEquals(
            whisparrJson.parseToJsonElement("""{"name":"MoviesSearch","movieIds":[7]}""").jsonObject,
            sent.single { it.first == "POST /w/api/v3/command" }.second,
        )
        repo.grab(Release("g-1", 3, "idx", "t", 1, 1.0, QualityWrap(Named(1, "1080p")), protocol = "torrent", approved = true), 7)
        assertEquals(
            whisparrJson.parseToJsonElement("""{"guid":"g-1","indexerId":3,"movieId":7}""").jsonObject,
            sent.single { it.first == "POST /w/api/v3/release" }.second,
        )
    }

    @Test
    fun `validation error text reaches the panel`() = runTest {
        val engine =
            MockEngine { req ->
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                when (req.url.encodedPath) {
                    "/api/v3/lookup/scene" -> respond("[$LOOKUP]", HttpStatusCode.OK, json)
                    else -> respond("""[{"propertyName":"","errorMessage":"Studio: [X] has been excluded"}]""", HttpStatusCode.BadRequest, json)
                }
            }
        val repo = WhisparrRepository(KtorWhisparrApi(WhisparrClients.httpClient("key", engine), "h"), FakeSettings(Settings(whisparr = WhisparrDefaults("/d", 1))))
        assertEquals(PanelState.Error("Studio: [X] has been excluded"), repo.add("uuid-1", AddMode.REQUEST))
    }

    @Test
    fun `newEvents cursor`() = runTest {
        val t0 = Instant.parse("2026-11-22T10:00:00Z")
        val history =
            listOf(
                HistoryItem(1, 7, "a", Instant.parse("2026-11-22T10:05:00Z"), "grabbed"),
                HistoryItem(2, 7, "a", Instant.parse("2026-11-22T10:01:00Z"), "movieFileRenamed"),
                HistoryItem(3, 7, "a", Instant.parse("2026-11-22T10:09:00Z"), "downloadFolderImported"),
                HistoryItem(4, 8, "b", t0, "downloadFailed"), // == cursor: already seen
            )
        var askedSince: Instant? = null
        val api =
            object : FakeApi() {
                override suspend fun historySince(date: Instant): List<HistoryItem> {
                    askedSince = date
                    return history
                }
            }
        val settings = FakeSettings(Settings())
        val repo = WhisparrRepository(api, settings, now = { t0 })

        assertEquals(emptyList(), repo.newEvents()) // first run: cursor only
        assertEquals(t0.toEpochMilliseconds(), settings.state.value.historyCursor)

        assertEquals(listOf(1, 3), repo.newEvents().map { it.id })
        assertEquals(t0, askedSince)
        assertEquals(Instant.parse("2026-11-22T10:09:00Z").toEpochMilliseconds(), settings.state.value.historyCursor)
    }

    @Test
    fun `offsetless dotnet dates are utc`() {
        assertEquals(Instant.parse("2026-11-22T10:00:00Z"), parseWhisparrInstant("2026-11-22T10:00:00"))
        assertEquals(Instant.parse("2026-11-22T10:00:00Z"), parseWhisparrInstant("2026-11-22T12:00:00+02:00"))
    }

    private class FakeSettings(
        initial: Settings,
    ) : SettingsStore {
        val state = MutableStateFlow(initial)
        override val settings = state

        override suspend fun update(transform: (Settings) -> Settings) {
            state.value = transform(state.value)
        }
    }

    private open class FakeApi : WhisparrApi {
        override suspend fun status(): SystemStatus = TODO()

        override suspend fun rootFolders(): List<RootFolder> = TODO()

        override suspend fun qualityProfiles(): List<QualityProfile> = TODO()

        override suspend fun lookupScene(stashId: String): List<SearchResult> = TODO()

        override suspend fun movieByStashId(stashId: String): List<WhisparrScene> = TODO()

        override suspend fun movie(id: Int): WhisparrScene = TODO()

        override suspend fun addMovie(body: JsonObject): WhisparrScene = TODO()

        override suspend fun updateMovie(body: JsonObject): WhisparrScene = TODO()

        override suspend fun setMonitored(
            movieIds: List<Int>,
            monitored: Boolean,
        ): Unit = TODO()

        override suspend fun command(
            name: String,
            movieIds: List<Int>,
        ): CommandRes = TODO()

        override suspend fun command(id: Int): CommandRes = TODO()

        override suspend fun releases(movieId: Int): List<Release> = TODO()

        override suspend fun grab(
            guid: String,
            indexerId: Int,
            movieId: Int,
        ): Unit = TODO()

        override suspend fun queue(movieId: Int?): List<QueueItem> = TODO()

        override suspend fun removeFromQueue(
            id: Int,
            blocklist: Boolean,
        ): Unit = TODO()

        override suspend fun historySince(date: Instant): List<HistoryItem> = TODO()
    }

    private companion object {
        // Shape of GET lookup/scene (Whisparr.Api.V3/Search/SearchResource.cs), trimmed.
        const val LOOKUP =
            """{"id":1,"foreignId":"uuid-1","movie":{"id":0,"title":"Some Scene","foreignId":"uuid-1","stashId":"uuid-1",
            "tpdbId":null,"tmdbId":0,"images":[{"coverType":"screenshot","remoteUrl":"https://x/y.jpg"}],"monitored":false,
            "qualityProfileId":0}}"""
    }
}
