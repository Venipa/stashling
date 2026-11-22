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
    /** After [PageLoader.jumpTo] past page 1: earlier pages load at the top ([PageLoader.loadPrevious]). */
    val hasPrevious: Boolean = false,
    val loadingPrevious: Boolean = false,
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
    private var prevJob: Job? = null
    private var page = 0
    private var first = 1

    fun ensureStarted() {
        if (!_state.value.started) reload()
    }

    fun reload(newFetch: (suspend (Int) -> Page<T>)? = null) {
        newFetch?.let { fetch = it }
        job?.cancel()
        prevJob?.cancel()
        page = 0
        first = 1
        _state.value = PagedState(started = true)
        loadMore()
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.refreshing || !s.hasNext || s.error != null) return
        _state.update { it.copy(loading = true) }
        val next = page + 1
        job = scope.launch {
            try {
                val p = fetch(next)
                page = next
                _state.update { it.copy(items = (it.items + p.items).distinctBy(key), loading = false, hasNext = p.hasNext, started = true, total = p.count) }
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
        prevJob?.cancel()
        _state.update { it.copy(refreshing = true, loading = false, error = null) }
        job = scope.launch {
            try {
                val p = fetch(1)
                page = 1
                first = 1
                _state.value = PagedState(p.items.distinctBy(key), hasNext = p.hasNext, started = true, total = p.count)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    /** A page as the list would load it (cached by the API), without changing the list; for [seekLetter]. */
    suspend fun peek(p: Int): Page<T> = fetch(p)

    /** Replaces the list with page [p]; the list then loads forward ([loadMore]) and backward ([loadPrevious]) from there. */
    suspend fun jumpTo(p: Int) {
        job?.cancel()
        prevJob?.cancel()
        val pg = fetch(p)
        page = p
        first = p
        _state.value = PagedState(pg.items.distinctBy(key), hasNext = pg.hasNext, started = true, total = pg.count, hasPrevious = p > 1)
    }

    fun loadPrevious() {
        val s = _state.value
        if (s.loadingPrevious || !s.hasPrevious || s.error != null || s.refreshing) return
        _state.update { it.copy(loadingPrevious = true) }
        val prev = first - 1
        prevJob = scope.launch {
            try {
                val p = fetch(prev)
                first = prev
                _state.update { it.copy(items = (p.items + it.items).distinctBy(key), hasPrevious = prev > 1, loadingPrevious = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingPrevious = false, error = e) }
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

/**
 * Server-side letter jump on a name-ascending list: binary search over the pages by the first item's [seekRank] of its raw
 * name (~log2(pages) cached requests), then [PageLoader.jumpTo] the page holding the first item at or after [letter].
 * Returns the item index to scroll to. Server order is by raw name, so non-Latin names (after Z) are reached from the end.
 */
suspend fun <T> PageLoader<T>.seekLetter(
    letter: Char,
    name: (T) -> String,
): Int {
    val target = seekRank(letter)
    val one = peek(1)
    val pages = if (one.perPage <= 0) 1 else (one.count + one.perPage - 1) / one.perPage
    var lo = 1
    var hi = pages
    var hit = 1
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        val head = (if (mid == 1) one else peek(mid)).items.firstOrNull()
        if (head != null && seekRank(name(head)) < target) {
            hit = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    jumpTo(hit)
    val items = state.value.items
    val i = items.indexOfFirst { seekRank(name(it)) >= target }
    if (i >= 0 || hit >= pages) return i.coerceAtLeast(0)
    // The letter starts on the next page.
    jumpTo(hit + 1)
    return 0
}

/** Order of a name in a server NAME ASC list: digits and symbols 0, A–Z 1–26 (diacritics folded), other scripts 27 (after Z). */
fun seekRank(name: String): Int {
    val c = java.text.Normalizer.normalize(name.trimStart().take(1), java.text.Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: return 0
    return when {
        c in 'A'..'Z' -> c - 'A' + 1
        c.isLetter() -> 27
        else -> 0
    }
}

private fun seekRank(letter: Char): Int = if (letter in 'A'..'Z') letter - 'A' + 1 else 0
