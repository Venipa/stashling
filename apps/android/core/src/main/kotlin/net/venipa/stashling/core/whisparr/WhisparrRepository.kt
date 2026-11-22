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
import net.venipa.stashling.core.data.TagRule
import net.venipa.stashling.core.data.WhisparrPreset
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class WhisparrRepository(
    private val api: WhisparrApi,
    private val settings: SettingsStore,
    private val now: () -> Instant = { Clock.System.now() },
) {
    /** For About: the server version. */
    suspend fun status(): SystemStatus = api.status()

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
            val command = searchCommandId?.let { api.command(it) }
            val queue = api.queue(scene.id)
            derivePanelState(scene, queue, command, grabbedPending = grabbedNotQueued(scene, queue, command))
        }
    }

    // A finished search that grabbed a release shows up in history at once, but in the queue only after Whisparr's next
    // download-client refresh: without this the panel says "no release" and the user searches twice.
    private suspend fun grabbedNotQueued(
        scene: WhisparrScene,
        queue: List<QueueItem>,
        command: CommandRes?,
    ): Boolean {
        if (command == null || command.status in RUNNING_COMMAND || scene.hasFile == true || queue.any { it.movieId == scene.id }) return false
        val since = (command.queued ?: now() - GRAB_WINDOW) - 5.seconds
        if (now() - since > GRAB_WINDOW) return false
        val grabbed = api.historySince(since).any { it.movieId == scene.id && it.eventType == "grabbed" }
        if (grabbed) runCatching { api.command("RefreshMonitoredDownloads", emptyList()) }
        return grabbed
    }

    /** Badge per StashDB scene id for list tiles: downloaded (has file) > in library (wanted). Throws WhisparrError. */
    suspend fun libraryIndex(): Map<String, LibraryBadge> = run {
        // DOWNLOADING is overlaid from the queue by the caller (polled far more often than this full library call).
        // Index under both ids: StashDB tiles carry the StashDB UUID, which Whisparr stores as stashId and/or foreignId.
        api.movies().flatMap { m ->
            val badge = when {
                m.hasFile == true -> LibraryBadge.DOWNLOADED
                else -> LibraryBadge.IN_LIBRARY
            }
            listOfNotNull(m.stashId, m.foreignId).distinct().map { it to badge }
        }.toMap()
    }

    /** The preset an add of a scene with these StashDB tag ids uses, and the rule that picked it (null = Default). */
    suspend fun resolvePreset(sceneTagIds: Collection<String>): Pair<WhisparrPreset, TagRule?> = settings.settings.first().resolvePreset(sceneTagIds)

    /** Adds with the preset resolved from [sceneTagIds], or [presetOverride] (a preset id) for this add only. */
    suspend fun add(
        stashId: String,
        mode: AddMode,
        sceneTagIds: Collection<String> = emptyList(),
        presetOverride: String? = null,
    ): PanelState = guard {
        val s = settings.settings.first()
        val preset = presetOverride?.let { id -> s.allPresets.firstOrNull { it.id == id } } ?: s.resolvePreset(sceneTagIds).first
        if (preset.rootFolder == null || preset.qualityProfileId == null) {
            return@guard PanelState.Error("Choose a root folder and quality profile for preset \"${preset.name}\" in Settings → Whisparr → Presets")
        }
        val lookup =
            api.lookupScene(stashId).firstNotNullOfOrNull { it.movie }
                ?: return@guard PanelState.Error("Whisparr can't find this scene in its metadata")
        if ((lookup["id"]?.jsonPrimitive?.intOrNull ?: 0) > 0) return@guard panelState(stashId) // already added elsewhere
        val scene = api.addMovie(addMovieBody(lookup, preset))
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
private val GRAB_WINDOW = 3.minutes

/**
 * Queue beats file (an upgrade can be downloading while a file exists); failed queue items are their own state,
 * except while a "Search again" runs: Whisparr keeps the failed item queued until the new grab replaces it.
 */
internal fun derivePanelState(
    scene: WhisparrScene,
    queue: List<QueueItem>,
    searchCommand: CommandRes?,
    grabbedPending: Boolean = false,
): PanelState {
    val item = queue.firstOrNull { it.movieId == scene.id }
    val running = searchCommand?.takeIf { it.status in RUNNING_COMMAND }
    return when {
        item != null && (item.trackedDownloadState in FAILED_STATES || item.status == "failed") ->
            if (running != null) PanelState.Searching(scene, running.id) else PanelState.Failed(scene, item.errorMessage)

        item != null -> PanelState.InQueue(scene, item)

        scene.hasFile == true -> PanelState.Downloaded(scene)

        running != null -> PanelState.Searching(scene, running.id)

        // Grabbed but not yet in the queue: keep following the search (active polling) instead of "no release".
        grabbedPending && searchCommand != null -> PanelState.Searching(scene, searchCommand.id)

        else -> PanelState.Missing(scene)
    }
}

/** Same shape the Whisparr web UI posts (frontend/src/Utilities/Movie/getNewMovie.js): lookup result + overrides, id 0; tags = lookup's + the preset's. */
internal fun addMovieBody(
    lookup: JsonObject,
    preset: WhisparrPreset,
): JsonObject = JsonObject(
    lookup +
        mapOf(
            "id" to JsonPrimitive(0),
            "monitored" to JsonPrimitive(true),
            "qualityProfileId" to JsonPrimitive(requireNotNull(preset.qualityProfileId)),
            "rootFolderPath" to JsonPrimitive(requireNotNull(preset.rootFolder)),
            "tags" to JsonArray(((lookup["tags"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.intOrNull } + preset.whisparrTagIds).distinct().map(::JsonPrimitive)),
            "addOptions" to
                buildJsonObject {
                    put("monitor", preset.monitor)
                    put("searchForMovie", false)
                },
        ),
)

enum class LibraryBadge { IN_LIBRARY, DOWNLOADING, DOWNLOADED }
