package net.venipa.stashling.feature.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.venipa.stashling.R
import net.venipa.stashling.core.data.Bookmark
import net.venipa.stashling.core.data.BookmarkKind
import net.venipa.stashling.core.data.BookmarkRepository
import net.venipa.stashling.core.stash.StashError

/** Bookmark toggle and StashDB favorite (with local-bookmark fallback) shared by the four detail ViewModels. [messages] are string ids for a snackbar. */
class EntityActions(
    private val scope: CoroutineScope,
    private val bookmarks: BookmarkRepository,
    private val kind: BookmarkKind,
    private val id: StateFlow<String>,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val bookmarked = id.flatMapLatest { bookmarks.isBookmarked(kind, it) }.stateIn(scope, SharingStarted.Eagerly, false)
    val favorite = MutableStateFlow(false)
    val messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    fun toggleBookmark(title: String, subtitle: String?, imageUrl: String?) {
        scope.launch {
            if (bookmarked.value) bookmarks.remove(kind, id.value) else bookmarks.add(Bookmark(kind, id.value, title, subtitle, imageUrl, System.currentTimeMillis()))
        }
    }

    fun toggleFavorite(set: suspend (String, Boolean) -> Unit, title: String, subtitle: String?, imageUrl: String?) {
        scope.launch {
            val want = !favorite.value
            favorite.value = want
            try {
                set(id.value, want)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                favorite.value = !want
                if (want && e is StashError.Unauthorized) {
                    bookmarks.add(Bookmark(kind, id.value, title, subtitle, imageUrl, System.currentTimeMillis()))
                    messages.tryEmit(R.string.detail_fav_local)
                } else {
                    messages.tryEmit(R.string.detail_fav_failed)
                }
            }
        }
    }
}
