package net.venipa.stashling.feature.detail.whisparr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.venipa.stashling.AppContainer
import net.venipa.stashling.core.whisparr.AddMode
import net.venipa.stashling.core.whisparr.PanelState
import net.venipa.stashling.core.whisparr.Release
import net.venipa.stashling.core.whisparr.WhisparrError
import net.venipa.stashling.core.whisparr.WhisparrRepository
import net.venipa.stashling.core.whisparr.WhisparrScene

/** Interactive search sheet; null in the ViewModel = closed. */
data class ReleasesUi(
    val movieId: Int,
    val loading: Boolean = true,
    val items: List<Release> = emptyList(),
    val error: String? = null,
    val grabbing: String? = null,
)

class WhisparrPanelViewModel(
    private val stashId: String,
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow<PanelState>(PanelState.Loading)
    val state: StateFlow<PanelState> = _state.asStateFlow()

    /** Settings → Whisparr "search on add": which add button is the filled one. */
    val searchOnAdd: StateFlow<Boolean> =
        container.settings.settings
            .map { it.whisparr.searchOnAdd }
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** A search just ended without a grab. Cleared by the next action. */
    private val _noRelease = MutableStateFlow(false)
    val noRelease: StateFlow<Boolean> = _noRelease.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _releases = MutableStateFlow<ReleasesUi?>(null)
    val releases: StateFlow<ReleasesUi?> = _releases.asStateFlow()

    private var searchCommandId: Int? = null
    private val refreshLock = Mutex()

    /** Run while the panel is visible (the composable ties it to STARTED): 5 s while searching/downloading, else 30 s. */
    suspend fun poll() {
        while (true) {
            refresh()
            val active = _state.value is PanelState.Searching || _state.value is PanelState.InQueue
            delay(if (active) ACTIVE_MS else IDLE_MS)
        }
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
        searchCommandId = (next as? PanelState.Searching)?.commandId
        if (was is PanelState.Searching && next is PanelState.Missing) _noRelease.value = true
        _state.value = next
    }

    fun add(mode: AddMode) = action { repo ->
        val next = repo.add(stashId, mode)
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

    fun retry() = action { refresh() }

    fun openReleases(scene: WhisparrScene) {
        val repo = container.whisparr.value ?: return
        _noRelease.value = false
        _releases.value = ReleasesUi(scene.id)
        viewModelScope.launch {
            val result =
                try {
                    ReleasesUi(scene.id, loading = false, items = repo.releases(scene.id))
                } catch (e: WhisparrError) {
                    ReleasesUi(scene.id, loading = false, error = e.message)
                }
            // Ignore the result if the sheet was closed meanwhile.
            _releases.update { if (it?.movieId == scene.id && it.loading) result else it }
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
            } catch (e: WhisparrError) {
                _releases.update { it?.copy(grabbing = null, error = e.message) }
            }
        }
    }

    fun closeReleases() {
        _releases.value = null
    }

    private fun action(block: suspend (WhisparrRepository) -> Unit) {
        if (_busy.value) return
        val repo = container.whisparr.value ?: return
        _noRelease.value = false
        _busy.value = true
        viewModelScope.launch {
            try {
                block(repo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: WhisparrError) {
                _state.value = PanelState.Error(e.message ?: "")
            } finally {
                _busy.value = false
            }
        }
    }

    private companion object {
        const val ACTIVE_MS = 5_000L
        const val IDLE_MS = 30_000L
    }
}
