package net.venipa.stashling.feature.saved

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.venipa.stashling.AppContainer
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.Bookmark
import net.venipa.stashling.core.data.BookmarkKind
import net.venipa.stashling.core.stash.PerformerFilter
import net.venipa.stashling.core.stash.StudioFilter
import net.venipa.stashling.ds.ConfirmSheet
import net.venipa.stashling.ds.EmptyState
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.feature.common.PageLoader
import net.venipa.stashling.feature.common.PagedGrid
import net.venipa.stashling.feature.common.PosterSkeleton
import net.venipa.stashling.feature.common.PosterTile
import net.venipa.stashling.feature.common.StashTopBar
import net.venipa.stashling.feature.common.labelRes
import net.venipa.stashling.feature.common.listPadding
import net.venipa.stashling.feature.common.tabViewModel
import net.venipa.stashling.nav.LocalDockInset
import net.venipa.stashling.nav.SettingsAction

class SavedViewModel(private val c: AppContainer) : ViewModel() {
    val kind = MutableStateFlow(BookmarkKind.SCENE)
    val byTitle = MutableStateFlow(false)

    /** "StashDB favorites" chip (performers/studios): a remote query, no sync with the bookmarks. */
    val remote = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val items = combine(kind.flatMapLatest { c.bookmarks.observe(it) }, byTitle) { list, title ->
        if (title) list.sortedBy { it.title.lowercase() } else list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val performers = PageLoader(viewModelScope, { it.id }) { p -> c.stash.performers(PerformerFilter(favoritesOnly = true), p) }
    val studios = PageLoader(viewModelScope, { it.id }) { p -> c.stash.studios(StudioFilter(favoritesOnly = true), p) }

    // Scroll positions survive tab switches: this ViewModel is Activity-scoped.
    val sceneGrid = LazyGridState()
    val remoteGrids = mapOf(BookmarkKind.PERFORMER to LazyGridState(), BookmarkKind.STUDIO to LazyGridState())
    val localGrids = BookmarkKind.entries.associateWith { LazyGridState() }

    /** Pull to refresh on the StashDB favorites grids. */
    fun refreshRemote() {
        if (kind.value == BookmarkKind.PERFORMER) {
            c.stash.invalidate("erformers|")
            performers.refresh()
        } else {
            c.stash.invalidate("tudios|")
            studios.refresh()
        }
    }

    fun selectKind(k: BookmarkKind) {
        kind.value = k
        remote.value = false
    }

    fun toggleRemote() {
        remote.value = !remote.value
        if (remote.value) (if (kind.value == BookmarkKind.PERFORMER) performers else studios).ensureStarted()
    }

    fun remove(b: Bookmark) {
        viewModelScope.launch { c.bookmarks.remove(b.kind, b.stashId) }
    }
}

/** Saved: kind chips, 2-column grid of bookmarks, sort Added/Title, long-press to remove; StashDB favorites chip on Performers/Studios. */
@Composable
fun SavedScreen(
    onOpenScene: (String) -> Unit,
    onOpenPerformer: (String) -> Unit,
    onOpenStudio: (String) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    val c = LocalAppContainer.current
    val vm = tabViewModel { SavedViewModel(c) }
    val kind by vm.kind.collectAsState()
    val byTitle by vm.byTitle.collectAsState()
    val remote by vm.remote.collectAsState()
    val items by vm.items.collectAsState()
    var toRemove by remember { mutableStateOf<Bookmark?>(null) }
    val open = { k: BookmarkKind, id: String ->
        when (k) {
            BookmarkKind.SCENE -> onOpenScene(id)
            BookmarkKind.PERFORMER -> onOpenPerformer(id)
            BookmarkKind.STUDIO -> onOpenStudio(id)
            BookmarkKind.TAG -> onOpenTag(id)
        }
    }

    Scaffold(
        topBar = {
            Column {
                StashTopBar(stringResource(R.string.nav_saved), actions = { SettingsAction() })
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.L, vertical = Space.Xs), horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                    BookmarkKind.entries.forEach { k -> FilterChip(k == kind, { vm.selectKind(k) }, { Text(stringResource(k.labelRes())) }) }
                }
                if (kind == BookmarkKind.PERFORMER || kind == BookmarkKind.STUDIO) {
                    Row(Modifier.padding(horizontal = Space.L, vertical = Space.Xs)) {
                        FilterChip(remote, vm::toggleRemote, { Text(stringResource(R.string.saved_stashdb_favorites)) })
                    }
                }
                if (!remote) {
                    StashSegmented(
                        listOf(stringResource(R.string.saved_sort_added), stringResource(R.string.saved_sort_title)),
                        if (byTitle) 1 else 0,
                        { vm.byTitle.value = it == 1 },
                        Modifier.padding(horizontal = Space.L, vertical = Space.Xs),
                    )
                }
            }
        },
    ) { pad ->
        val padding = listPadding(pad, LocalDockInset.current)
        if (remote) {
            if (kind == BookmarkKind.PERFORMER) {
                val st by vm.performers.state.collectAsState()
                PagedGrid(st, { it.id }, GridCells.Fixed(2), padding, vm.performers::loadMore, vm.performers::retry, stringResource(R.string.saved_empty_remote), gridState = vm.remoteGrids.getValue(kind), onRefresh = vm::refreshRemote) { _, p ->
                    PosterTile(p.name, null, (p.images.firstOrNull() ?: p.thumbnail)?.sized(300), 2f / 3f, { onOpenPerformer(p.id) })
                }
            } else {
                val st by vm.studios.state.collectAsState()
                PagedGrid(st, { it.id }, GridCells.Fixed(2), padding, vm.studios::loadMore, vm.studios::retry, stringResource(R.string.saved_empty_remote), gridState = vm.remoteGrids.getValue(kind), onRefresh = vm::refreshRemote, skeleton = { _, _ -> PosterSkeleton(16f / 9f) }) { _, s ->
                    PosterTile(s.name, s.parent?.name, s.images.firstOrNull()?.sized(300), 16f / 9f, { onOpenStudio(s.id) }, fit = true)
                }
            }
        } else {
            val list = items
            if (list != null && list.isEmpty()) {
                EmptyState(StashlingIcons.Saved, stringResource(R.string.saved_empty), Modifier.padding(pad), stringResource(R.string.saved_empty_body))
            } else {
                LazyVerticalGrid(
                    GridCells.Fixed(2),
                    state = vm.localGrids.getValue(kind),
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(Space.M),
                    horizontalArrangement = Arrangement.spacedBy(Space.M),
                ) {
                    items(list.orEmpty(), key = { "${it.kind}:${it.stashId}" }) { b ->
                        // Scenes and studios are landscape, performers 2:3, tags text only. The stored url is already sized.
                        PosterTile(
                            b.title,
                            b.subtitle,
                            b.imageUrl,
                            if (b.kind == BookmarkKind.PERFORMER || b.kind == BookmarkKind.TAG) 2f / 3f else 16f / 9f,
                            { open(b.kind, b.stashId) },
                            fit = b.kind == BookmarkKind.STUDIO,
                            onLongClick = { toRemove = b },
                        )
                    }
                }
            }
        }
    }

    toRemove?.let { b ->
        ConfirmSheet(
            stringResource(R.string.saved_remove_title),
            stringResource(R.string.saved_remove_body, b.title),
            stringResource(R.string.saved_remove),
            { vm.remove(b).also { toRemove = null } },
            { toRemove = null },
        )
    }
}
