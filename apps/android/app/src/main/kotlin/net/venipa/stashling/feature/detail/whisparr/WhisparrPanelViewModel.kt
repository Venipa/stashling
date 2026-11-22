package net.venipa.stashling.feature.detail.whisparr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import net.venipa.stashling.AppContainer
import net.venipa.stashling.core.data.TagRule
import net.venipa.stashling.core.data.WhisparrPreset
import net.venipa.stashling.core.stash.StashError
import net.venipa.stashling.core.whisparr.AddMode
import net.venipa.stashling.core.whisparr.PanelState
import net.venipa.stashling.core.whisparr.Release
import net.venipa.stashling.core.whisparr.WhisparrError
import net.venipa.stashling.core.whisparr.WhisparrEvent
import net.venipa.stashling.core.whisparr.WhisparrRepository
import net.venipa.stashling.core.whisparr.WhisparrScene
import java.io.IOException

/** Interactive search sheet; null in the ViewModel = closed. */
data class ReleasesUi(
    val movieId: Int,
    val loading: Boolean = true,
    val items: List<Release> = emptyList(),
    val error: String? = null,
    val grabbing: String? = null,
    /** The grab hit Whisparr's expired release cache; this list is a fresh search. */
    val expired: Boolean = false,
    val startedAt: Long = System.currentTimeMillis(),
)

/** [rule] = the rule that matched (null = Default, or [overridden]). */
data class PresetChoice(
    val preset: WhisparrPreset,
    val rule: TagRule?,
    val overridden: Boolean,
    val all: List<WhisparrPreset>,
)

class WhisparrPanelViewModel(
    private val stashId: String,
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow<PanelState>(PanelState.Loading)
    val state: StateFlow<PanelState> = _state.asStateFlow()

    /** The scene's StashDB tag ids (cached scene call); null while loading. */
    private val sceneTags = MutableStateFlow<List<String>?>(null)

    /** A preset picked on the card for this add only (5A). */
    private val override = MutableStateFlow<String?>(null)

    /** The preset the next add uses and why; null until the scene tags are in. */
    val preset: StateFlow<PresetChoice?> =
        combine(container.settings.settings, sceneTags, override) { s, tags, o ->
            if (tags == null) return@combine null
            val (resolved, rule) = s.resolvePreset(tags)
            // Picking the resolved preset again is the same as no override.
            val picked = o?.takeIf { it != resolved.id }?.let { id -> s.allPresets.firstOrNull { it.id == id } }
            PresetChoice(picked ?: resolved, if (picked == null) rule else null, picked != null, s.allPresets)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The applied preset's "search on add": which add button is the filled one. */
    val searchOnAdd: StateFlow<Boolean> =
        preset
            .map { it?.preset?.searchOnAdd ?: true }
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        viewModelScope.launch {
            // No tags (StashDB unreachable) resolves to Default; the card then says "Default".
            sceneTags.value =
                try {
                    container.stash.scene(stashId)?.tags?.map { it.id }.orEmpty()
                } catch (e: StashError) {
                    emptyList()
                }
        }
    }

    fun overridePreset(id: String?) {
        override.value = id
    }

    /** A search just ended without a grab. Cleared by the next action. */
    private val _noRelease = MutableStateFlow(false)
    val noRelease: StateFlow<Boolean> = _noRelease.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _releases = MutableStateFlow<ReleasesUi?>(null)
    val releases: StateFlow<ReleasesUi?> = _releases.asStateFlow()

    private var searchCommandId: Int? = null

    // Serialises polls and actions so a stale poll result can't overwrite an action's newer state.
    private val refreshLock = Mutex()
    private var releasesJob: Job? = null

    /**
     * Run while the panel is visible (the composable ties it to STARTED): 5 s while searching/downloading, else 30 s.
     * Wakes early when an action flips that, and refreshes as soon as the Whisparr client is built (cold start).
     */
    suspend fun poll() {
        while (true) {
            refresh()
            if (container.whisparr.value == null) {
                container.whisparr.filterNotNull().first()
                continue
            }
            val active = isActive(_state.value)
            // Live (SignalR): this scene's movie/command events or any queue change wake it; the timer is the fallback.
            // SignalR alone wasn't reliable — always poll (3 s active / 10 s idle); events only wake it sooner.
            val wait = if (active) ACTIVE_MS else IDLE_MS
            val woke =
                withTimeoutOrNull(wait) {
                    merge(_state.filter { isActive(it) != active }, container.whisparrEvents.filter { concerns(it) }, container.whisparrLiveConnected.drop(1)).first()
                }
            // Queue events come in bursts (every progress tick): one refresh per burst.
            if (woke is WhisparrEvent) delay(EVENT_SETTLE_MS)
        }
    }

    private fun isActive(s: PanelState) = s is PanelState.Searching || s is PanelState.InQueue

    private fun concerns(e: WhisparrEvent): Boolean = when (e.name) {
        "movie" -> e.stashId == stashId || (e.id != null && e.id == sceneOf(_state.value)?.id)
        "command" -> e.id != null && e.id == searchCommandId && e.status != "queued" && e.status != "started"
        "queue" -> true
        else -> false
    }

    private fun sceneOf(s: PanelState): WhisparrScene? = when (s) {
        is PanelState.Missing -> s.scene
        is PanelState.Searching -> s.scene
        is PanelState.InQueue -> s.scene
        is PanelState.Failed -> s.scene
        is PanelState.Downloaded -> s.scene
        else -> null
    }

    private suspend fun refresh() = refreshLock.withLock {
        val repo = container.whisparr.value
        if (repo == null) {
            // Account present but the client is still being built: stay Loading.
            if (container.secrets.whisparr.value == null) _state.value = PanelState.Unavailable
            return@withLock
        }
        apply(repo.panelState(stashId, searchCommandId))
    }

    private fun apply(next: PanelState) {
        val was = _state.value
        searchCommandId =
            when (next) {
                is PanelState.Searching -> next.commandId

                // The command has ended (derivePanelState only leaves Searching then); on Error etc. keep following it.
                is PanelState.Missing, is PanelState.Downloaded, is PanelState.InQueue, is PanelState.Failed -> null

                else -> searchCommandId
            }
        if (was is PanelState.Searching && next is PanelState.Missing) _noRelease.value = true
        _state.value = next
    }

    fun add(mode: AddMode) = action { repo ->
        val next = repo.add(stashId, mode, sceneTags.filterNotNull().first(), override.value)
        apply(next)
        if (mode == AddMode.PICK && next is PanelState.Missing) openReleases(next.scene)
    }

    fun search(scene: WhisparrScene) = action { repo -> apply(repo.search(scene.id)) }

    fun setMonitored(
        scene: WhisparrScene,
        on: Boolean,
    ) = action { repo ->
        repo.setMonitored(scene, on)
        apply(repo.panelState(stashId, searchCommandId))
    }

    fun retry() = action { repo -> apply(repo.panelState(stashId, searchCommandId)) }

    fun openReleases(scene: WhisparrScene) {
        _noRelease.value = false
        loadReleases(scene.id)
    }

    private fun loadReleases(
        movieId: Int,
        expired: Boolean = false,
    ) {
        val repo = container.whisparr.value ?: return
        releasesJob?.cancel()
        _releases.value = ReleasesUi(movieId, expired = expired)
        releasesJob =
            viewModelScope.launch {
                val result =
                    try {
                        ReleasesUi(movieId, loading = false, items = repo.releases(movieId), expired = expired)
                    } catch (e: WhisparrError) {
                        ReleasesUi(movieId, loading = false, error = e.message)
                    }
                // Ignore the result if the sheet was closed meanwhile.
                _releases.update { if (it?.movieId == movieId && it.loading) result else it }
            }
    }

    fun grab(release: Release) {
        val sheet = _releases.value ?: return
        val repo = container.whisparr.value ?: return
        _releases.value = sheet.copy(grabbing = release.guid, error = null)
        viewModelScope.launch {
            try {
                repo.grab(release, sheet.movieId)
                _releases.value = null
                refresh()
                container.sceneActions.changed(stashId)
            } catch (e: WhisparrError.Http) {
                // 404 = Whisparr's release cache expired ("try searching again"): search again for the user.
                if (e.status == 404) loadReleases(sheet.movieId, expired = true) else _releases.update { it?.copy(grabbing = null, error = e.message) }
            } catch (e: WhisparrError) {
                _releases.update { it?.copy(grabbing = null, error = e.message) }
            }
        }
    }

    fun closeReleases() {
        releasesJob?.cancel()
        _releases.value = null
    }

    private fun action(block: suspend (WhisparrRepository) -> Unit) {
        if (_busy.value) return
        val repo = container.whisparr.value ?: return
        _noRelease.value = false
        _busy.value = true
        viewModelScope.launch {
            try {
                refreshLock.withLock {
                    try {
                        block(repo)
                    } catch (e: WhisparrError) {
                        apply(PanelState.Error(e.message ?: ""))
                    } catch (e: IOException) { // settings DataStore read in add()
                        apply(PanelState.Error(e.message ?: ""))
                    }
                }
            } finally {
                _busy.value = false
            }
            container.sceneActions.changed(stashId)
        }
    }

    private companion object {
        const val ACTIVE_MS = 3_000L
        const val IDLE_MS = 10_000L
        const val EVENT_SETTLE_MS = 1_000L
    }
}
