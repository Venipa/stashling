package net.venipa.stashling.core.whisparr

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.venipa.stashling.core.data.SettingsStore
import net.venipa.stashling.core.data.WhisparrDefaults
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant

class WhisparrRepository(
    private val api: WhisparrApi,
    private val settings: SettingsStore,
    private val now: () -> Instant = { Clock.System.now() },
) {
    /**
     * Library first (`movie?stashId=`), else metadata lookup. Pass the id of a running search
     * ([PanelState.Searching.commandId]) so the panel stays in Searching until the command ends.
     */
    suspend fun panelState(
        stashId: String,
        searchCommandId: Int? = null,
    ): PanelState = guard {
        val scene = api.movieByStashId(stashId).firstOrNull()
        if (scene == null) {
            notInLibrary(stashId)
        } else {
            derivePanelState(scene, api.queue(scene.id), searchCommandId?.let { api.command(it) })
        }
    }

    suspend fun add(
        stashId: String,
        mode: AddMode,
    ): PanelState = guard {
        val defaults = settings.settings.first().whisparr
        if (defaults.rootFolder == null || defaults.qualityProfileId == null) {
            return@guard PanelState.Error("Choose a root folder and quality profile in Settings → Whisparr")
        }
        val lookup =
            api.lookupScene(stashId).firstNotNullOfOrNull { it.movie }
                ?: return@guard PanelState.Error("Whisparr can't find this scene in its metadata")
        if ((lookup["id"]?.jsonPrimitive?.intOrNull ?: 0) > 0) return@guard panelState(stashId) // already added elsewhere
        val scene = api.addMovie(addMovieBody(lookup, defaults))
        // Search is a separate command (not addOptions.searchForMovie) so the panel gets a command id to follow.
        if (mode == AddMode.QUEUE) searching(scene) else PanelState.Missing(scene)
    }

    suspend fun search(movieId: Int): PanelState = guard { searching(api.movie(movieId)) }

    suspend fun releases(movieId: Int): List<Release> = api.releases(movieId)

    suspend fun grab(
        release: Release,
        movieId: Int,
    ) = api.grab(release.guid, release.indexerId, movieId)

    suspend fun setMonitored(
        scene: WhisparrScene,
        on: Boolean,
    ) = api.setMonitored(listOf(scene.id), on)

    suspend fun queue(): List<QueueItem> = api.queue()

    /** Removes from the download client too; [blocklist] = never grab this release again (Whisparr then searches anew). */
    suspend fun removeFromQueue(
        id: Int,
        blocklist: Boolean,
    ) = api.removeFromQueue(id, blocklist)

    suspend fun history(since: Instant): List<HistoryItem> = api.historySince(since)

    /**
     * Grabbed / imported / failed events since the stored cursor, oldest first; advances the cursor.
     * First run only sets the cursor to now (no backlog of old notifications).
     */
    suspend fun newEvents(): List<HistoryItem> {
        val cursorMs = settings.settings.first().historyCursor
        if (cursorMs == null) {
            val start = now().toEpochMilliseconds()
            settings.update { it.copy(historyCursor = start) }
            return emptyList()
        }
        val events =
            api
                .historySince(Instant.fromEpochMilliseconds(cursorMs))
                .filter { it.eventType in NOTIFY_EVENTS && it.date.toEpochMilliseconds() > cursorMs }
                .sortedBy { it.date }
        events.lastOrNull()?.let { last -> settings.update { it.copy(historyCursor = last.date.toEpochMilliseconds()) } }
        return events
    }

    private suspend fun notInLibrary(stashId: String): PanelState {
        val lookup =
            api.lookupScene(stashId).firstNotNullOfOrNull { it.movie }
                ?: return PanelState.Error("Whisparr can't find this scene in its metadata")
        return PanelState.NotInLibrary(lookup, excluded = lookup["isExcluded"]?.jsonPrimitive?.booleanOrNull == true)
    }

    private suspend fun searching(scene: WhisparrScene): PanelState = PanelState.Searching(scene, api.command(SEARCH_COMMAND, listOf(scene.id)).id)

    private suspend inline fun guard(block: () -> PanelState): PanelState = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: WhisparrError) {
        PanelState.Error(e.message ?: "Whisparr error")
    }

    companion object {
        const val SEARCH_COMMAND = "MoviesSearch"
        val NOTIFY_EVENTS = setOf("grabbed", "downloadFolderImported", "downloadFailed")
    }
}

private val FAILED_STATES = setOf("failed", "failedPending")
private val RUNNING_COMMAND = setOf("queued", "started")

/** Queue beats file (an upgrade can be downloading while a file exists); failed queue items are their own state. */
internal fun derivePanelState(
    scene: WhisparrScene,
    queue: List<QueueItem>,
    searchCommand: CommandRes?,
): PanelState {
    val item = queue.firstOrNull { it.movieId == scene.id }
    return when {
        item != null && (item.trackedDownloadState in FAILED_STATES || item.status == "failed") -> PanelState.Failed(scene, item.errorMessage)
        item != null -> PanelState.InQueue(scene, item)
        scene.hasFile == true -> PanelState.Downloaded(scene)
        searchCommand != null && searchCommand.status in RUNNING_COMMAND -> PanelState.Searching(scene, searchCommand.id)
        else -> PanelState.Missing(scene)
    }
}

/** Same shape the Whisparr web UI posts (frontend/src/Utilities/Movie/getNewMovie.js): lookup result + overrides, id 0. */
internal fun addMovieBody(
    lookup: JsonObject,
    defaults: WhisparrDefaults,
): JsonObject = JsonObject(
    lookup +
        mapOf(
            "id" to JsonPrimitive(0),
            "monitored" to JsonPrimitive(true),
            "qualityProfileId" to JsonPrimitive(requireNotNull(defaults.qualityProfileId)),
            "rootFolderPath" to JsonPrimitive(requireNotNull(defaults.rootFolder)),
            "tags" to (lookup["tags"] as? JsonArray ?: JsonArray(emptyList())),
            "addOptions" to
                buildJsonObject {
                    put("monitor", defaults.monitor)
                    put("searchForMovie", false)
                },
        ),
)
