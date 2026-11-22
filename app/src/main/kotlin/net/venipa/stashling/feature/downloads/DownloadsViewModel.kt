package net.venipa.stashling.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.venipa.stashling.AppContainer
import net.venipa.stashling.core.whisparr.HistoryItem
import net.venipa.stashling.core.whisparr.QueueItem
import net.venipa.stashling.core.whisparr.WhisparrError
import net.venipa.stashling.core.whisparr.WhisparrRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** [items] null = first load still running. */
data class ListUi<T>(
    val items: List<T>? = null,
    val error: String? = null,
)

class DownloadsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _tab = MutableStateFlow(0)
    val tab: StateFlow<Int> = _tab.asStateFlow()

    private val _queue = MutableStateFlow(ListUi<QueueItem>())
    val queue: StateFlow<ListUi<QueueItem>> = _queue.asStateFlow()

    private val _history = MutableStateFlow(ListUi<HistoryItem>())
    val history: StateFlow<ListUi<HistoryItem>> = _history.asStateFlow()

    fun selectTab(i: Int) {
        _tab.value = i
    }

    /** While visible: the queue every 5 s while it has items, else 30 s; history on each idle tick. */
    suspend fun poll() {
        var tick = 0
        while (true) {
            val repo = container.whisparr.value
            if (repo != null) {
                loadQueue(repo)
                if (tick % HISTORY_EVERY == 0 || _history.value.items == null) loadHistory(repo)
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
        const val HISTORY_EVERY = 6
        const val HISTORY_DAYS = 14
    }
}
