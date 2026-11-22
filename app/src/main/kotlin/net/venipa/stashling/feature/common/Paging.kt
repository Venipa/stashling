package net.venipa.stashling.feature.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.venipa.stashling.core.stash.Page

data class PagedState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val error: Throwable? = null,
    val hasNext: Boolean = true,
    val started: Boolean = false,
    /** Pull to refresh: page 1 is reloading while the old items stay on screen. */
    val refreshing: Boolean = false,
    /** Server-side total (Page.count); -1 until the first page. */
    val total: Int = -1,
)

/** ViewModel page loader (1-based pages, 40 per page via the API default). An error stops auto-loading until [retry]. */
class PageLoader<T>(
    private val scope: CoroutineScope,
    private val key: (T) -> String,
    private var fetch: suspend (page: Int) -> Page<T>,
) {
    private val _state = MutableStateFlow(PagedState<T>())
    val state: StateFlow<PagedState<T>> = _state.asStateFlow()
    private var job: Job? = null
    private var page = 0

    fun ensureStarted() {
        if (!_state.value.started) reload()
    }

    fun reload(newFetch: (suspend (Int) -> Page<T>)? = null) {
        newFetch?.let { fetch = it }
        job?.cancel()
        page = 0
        _state.value = PagedState(started = true)
        loadMore()
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || !s.hasNext || s.error != null) return
        _state.update { it.copy(loading = true) }
        val next = page + 1
        job = scope.launch {
            try {
                val p = fetch(next)
                page = next
                _state.update { PagedState((it.items + p.items).distinctBy(key), hasNext = p.hasNext, started = true, total = p.count) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e) }
            }
        }
    }

    /** Pull to refresh: keeps the shown items until page 1 arrives; a failure keeps them too. */
    fun refresh() {
        if (_state.value.refreshing) return
        job?.cancel()
        _state.update { it.copy(refreshing = true, loading = false, error = null) }
        job = scope.launch {
            try {
                val p = fetch(1)
                page = 1
                _state.value = PagedState(p.items.distinctBy(key), hasNext = p.hasNext, started = true, total = p.count)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun retry() {
        _state.update { it.copy(error = null) }
        loadMore()
    }
}

/** One-shot load state of a detail screen. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>

    data object NotFound : Load<Nothing>

    data class Failed(val error: Throwable) : Load<Nothing>

    data class Data<T>(val value: T) : Load<T>
}

suspend fun <T : Any> loadOf(block: suspend () -> T?): Load<T> = try {
    block()?.let { Load.Data(it) } ?: Load.NotFound
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Load.Failed(e)
}

/** ViewModel of a tab root: owned by the Activity, so filters, loaded pages and scroll survive tab switches (the nav stack is cleared). */
@androidx.compose.runtime.Composable
inline fun <reified VM : androidx.lifecycle.ViewModel> tabViewModel(noinline create: () -> VM): VM = androidx.lifecycle.viewmodel.compose.viewModel(
    viewModelStoreOwner = androidx.activity.compose.LocalActivity.current as androidx.activity.ComponentActivity,
    initializer = { create() },
)
