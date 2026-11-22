package net.venipa.stashling.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.venipa.stashling.AppContainer
import net.venipa.stashling.core.whisparr.HistoryItem
import net.venipa.stashling.core.whisparr.QueueItem
import net.venipa.stashling.core.whisparr.WhisparrError
import net.venipa.stashling.core.whisparr.WhisparrRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** Downloads list: queue and recent history merged into status groups, newest history first. */
data class DownloadSections(
    val downloading: List<QueueItem> = emptyList(),
    val queued: List<QueueItem> = emptyList(),
    val failed: List<QueueItem> = emptyList(),
    /** Failures that already left the queue (Whisparr removes failed downloads), unless the scene was imported or queued again since. */
    val failedHistory: List<HistoryItem> = emptyList(),
    val imported: List<HistoryItem> = emptyList(),
) {
    /** Overall progress of [downloading], 0..1. */
    val progress: Double get() = downloading.sumOf { it.size }.let { total -> if (total > 0) 1 - downloading.sumOf { it.sizeleft } / total else 0.0 }
}

private val FAILED_STATES = setOf("failed", "failedPending")
private val WAITING = setOf("queued", "paused", "delay", "downloadClientUnavailable")

fun downloadSections(
    queue: List<QueueItem>,
    history: List<HistoryItem>,
): DownloadSections {
    val failed = queue.filter { it.trackedDownloadState in FAILED_STATES || it.status == "failed" }
    val rest = queue - failed.toSet()
    val queuedIds = queue.mapNotNull { it.movieId }.toSet()
    // history is newest first: a failure counts only if no import of the same scene came after it.
    val failedHistory =
        history.filterIndexed { i, h ->
            h.eventType == "downloadFailed" && h.movieId !in queuedIds && history.take(i).none { it.movieId == h.movieId && it.eventType == "downloadFolderImported" }
        }
    return DownloadSections(
        downloading = rest.filter { it.status !in WAITING },
        queued = rest.filter { it.status in WAITING },
        failed = failed,
        failedHistory = failedHistory.distinctBy { it.movieId },
        imported = history.filter { it.eventType == "downloadFolderImported" },
    )
}

/** [items] null = first load still running. */
data class ListUi<T>(
    val items: List<T>? = null,
    val error: String? = null,
)

class DownloadsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    /** Library segment (0 Saved, 1 Downloads): here so the downloads pill on any tab can open Downloads. */
    val librarySegment = MutableStateFlow(0)

    private val _queue = MutableStateFlow(ListUi<QueueItem>())
    val queue: StateFlow<ListUi<QueueItem>> = _queue.asStateFlow()

    private val _history = MutableStateFlow(ListUi<HistoryItem>())
    val history: StateFlow<ListUi<HistoryItem>> = _history.asStateFlow()

    val sections: StateFlow<DownloadSections> =
        combine(_queue, _history) { q, h -> downloadSections(q.items.orEmpty(), h.items.orEmpty()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, DownloadSections())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Pull to refresh = poll now. */
    fun refresh() {
        val repo = container.whisparr.value ?: return
        viewModelScope.launch {
            _refreshing.value = true
            loadQueue(repo)
            loadHistory(repo)
            _refreshing.value = false
        }
    }

    /** While the app is visible (one poller, started by the shell): the queue every 5 s while it has items, else 30 s; history every 6th tick. */
    suspend fun poll() {
        var tick = 0
        var indexAt = 0L
        while (true) {
            // Cold start: the client is built a moment after the account loads; wait for it instead of a 30 s idle tick.
            val repo = container.whisparr.value ?: container.whisparr.filterNotNull().first()
            loadQueue(repo)
            if (tick % HISTORY_EVERY == 0 || _history.value.items == null) loadHistory(repo)
            // Tile badges: the index is a full library call, so at most once a minute (first tick = on connect).
            if (System.currentTimeMillis() - indexAt >= INDEX_MS) {
                container.refreshWhisparrIndex()
                indexAt = System.currentTimeMillis()
            }
            val active = !_queue.value.items.isNullOrEmpty()
            tick++
            delay(if (active) ACTIVE_MS else IDLE_MS)
        }
    }

    fun remove(
        item: QueueItem,
        blocklist: Boolean,
    ) {
        val repo = container.whisparr.value ?: return
        viewModelScope.launch {
            try {
                repo.removeFromQueue(item.id, blocklist)
                _queue.value = _queue.value.copy(items = _queue.value.items?.filterNot { it.id == item.id }, error = null)
                loadQueue(repo)
            } catch (e: WhisparrError) {
                _queue.value = _queue.value.copy(error = e.message)
            }
        }
    }

    private suspend fun loadQueue(repo: WhisparrRepository) {
        _queue.value =
            try {
                ListUi(repo.queue())
            } catch (e: WhisparrError) {
                _queue.value.copy(error = e.message)
            }
    }

    private suspend fun loadHistory(repo: WhisparrRepository) {
        _history.value =
            try {
                ListUi(repo.history(Clock.System.now() - HISTORY_DAYS.days).filter { it.eventType in WhisparrRepository.NOTIFY_EVENTS }.sortedByDescending { it.date })
            } catch (e: WhisparrError) {
                _history.value.copy(error = e.message)
            }
    }

    private companion object {
        const val ACTIVE_MS = 5_000L
        const val IDLE_MS = 30_000L
        const val INDEX_MS = 60_000L
        const val HISTORY_EVERY = 6
        const val HISTORY_DAYS = 14
    }
}
