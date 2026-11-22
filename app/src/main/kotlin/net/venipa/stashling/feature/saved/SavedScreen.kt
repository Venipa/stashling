package net.venipa.stashling.feature.saved

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import net.venipa.stashling.core.data.ListMode
import net.venipa.stashling.core.stash.PerformerFilter
import net.venipa.stashling.core.stash.StudioFilter
import net.venipa.stashling.core.whisparr.AddMode
import net.venipa.stashling.core.whisparr.PanelState
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.ActionSheet
import net.venipa.stashling.ds.AlphabetRail
import net.venipa.stashling.ds.AlphabetRailWidth
import net.venipa.stashling.ds.AppSnackbar
import net.venipa.stashling.ds.CollapsingHeader
import net.venipa.stashling.ds.EmptyState
import net.venipa.stashling.ds.PagingFooter
import net.venipa.stashling.ds.RefreshBox
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.SheetAction
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashFilterChip
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.letterPositions
import net.venipa.stashling.ds.rememberCollapsingHeader
import net.venipa.stashling.feature.common.ListModeToggle
import net.venipa.stashling.feature.common.LoadMoreEffect
import net.venipa.stashling.feature.common.MediaRow
import net.venipa.stashling.feature.common.PageLoader
import net.venipa.stashling.feature.common.PosterTile
import net.venipa.stashling.feature.common.dragSelect
import net.venipa.stashling.feature.common.fullSpan
import net.venipa.stashling.feature.common.groupShape
import net.venipa.stashling.feature.common.labelRes
import net.venipa.stashling.feature.common.listPadding
import net.venipa.stashling.feature.common.openLink
import net.venipa.stashling.feature.common.rememberSceneListMode
import net.venipa.stashling.feature.common.stashWebUrl
import net.venipa.stashling.feature.common.tabViewModel
import net.venipa.stashling.nav.BrowseRoute
import net.venipa.stashling.nav.LocalDockInset
import net.venipa.stashling.nav.LocalDockTop
import net.venipa.stashling.nav.LocalNavigator

/** A snackbar line: a string id with an optional argument. */
data class LibraryMessage(
    @StringRes val res: Int,
    val arg: String = "",
)

class SavedViewModel(private val c: AppContainer) : ViewModel() {
    val kind = MutableStateFlow(BookmarkKind.SCENE)
    val byTitle = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val items = combine(kind.flatMapLatest { c.bookmarks.observe(it) }, byTitle) { list, title ->
        if (title) list.sortedBy { it.title.lowercase() } else list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "StashDB favorites" section (performers/studios): a remote query, no sync with the bookmarks. */
    val performers = PageLoader(viewModelScope, { it.id }) { p -> c.stash.performers(PerformerFilter(favoritesOnly = true), p) }
    val studios = PageLoader(viewModelScope, { it.id }) { p -> c.stash.studios(StudioFilter(favoritesOnly = true), p) }

    fun remoteFor(k: BookmarkKind): PageLoader<*>? = when (k) {
        BookmarkKind.PERFORMER -> performers
        BookmarkKind.STUDIO -> studios
        else -> null
    }

    // Scroll positions survive tab switches: this ViewModel is Activity-scoped.
    val grids = BookmarkKind.entries.associateWith { LazyGridState() }

    /** Selection mode: stash ids of the current kind; empty = not selecting. */
    var selected by mutableStateOf(emptySet<String>())

    val messages = MutableSharedFlow<LibraryMessage>(extraBufferCapacity = 4)

    fun selectKind(k: BookmarkKind) {
        kind.value = k
        selected = emptySet()
    }

    /** Pull to refresh on the StashDB favorites section. */
    fun refreshRemote() {
        if (kind.value == BookmarkKind.PERFORMER) {
            c.stash.invalidate("erformers|")
            performers.refresh()
        } else {
            c.stash.invalidate("tudios|")
            studios.refresh()
        }
    }

    fun remove(list: List<Bookmark>) {
        viewModelScope.launch { list.forEach { c.bookmarks.remove(it.kind, it.stashId) } }
    }

    /** Undo: the same rows back, original added time kept. */
    fun restore(list: List<Bookmark>) {
        viewModelScope.launch { list.forEach { c.bookmarks.add(it) } }
    }

    /** Whisparr add with the preset the scene's tags resolve to (its "search on add" picks the mode); the outcome goes to the snackbar. */
    fun addToWhisparr(stashId: String) {
        val repo = c.whisparr.value ?: return
        viewModelScope.launch {
            val tags =
                try {
                    c.stash.scene(stashId)?.tags?.map { it.id }.orEmpty()
                } catch (e: net.venipa.stashling.core.stash.StashError) {
                    // Without tags the add would silently fall back to Default: report instead.
                    messages.emit(LibraryMessage(R.string.library_whisparr_error, e.message.orEmpty()))
                    return@launch
                }
            val mode = if (repo.resolvePreset(tags).first.searchOnAdd) AddMode.QUEUE else AddMode.REQUEST
            val msg =
                when (val s = repo.add(stashId, mode, tags)) {
                    is PanelState.Error -> LibraryMessage(R.string.library_whisparr_error, s.message)
                    is PanelState.Searching -> LibraryMessage(R.string.library_added_searching)
                    is PanelState.Missing -> LibraryMessage(R.string.library_added_whisparr)
                    else -> LibraryMessage(R.string.library_already_whisparr)
                }
            messages.emit(msg)
            c.sceneActions.changed(stashId)
        }
    }
}

/**
 * Library → Saved: kind chips; a "Bookmarks" section (sort Added | Title) then, on Performers/Studios,
 * a paged "StashDB favorites" section, one grid with one aspect ratio per kind. Long-press (+ drag) selects like Google Photos;
 * the actions sheet opens from "⋮" in the selection bar when one item is selected.
 */
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
    val items by vm.items.collectAsState()
    val mode by rememberSceneListMode()
    val whisparr by c.whisparr.collectAsState()
    val performers by vm.performers.state.collectAsState()
    val studios by vm.studios.state.collectAsState()
    val remote = vm.remoteFor(kind)
    val remoteState =
        when (kind) {
            BookmarkKind.PERFORMER -> performers
            BookmarkKind.STUDIO -> studios
            else -> null
        }
    LaunchedEffect(kind) { remote?.ensureStarted() }
    val list = items.orEmpty()
    val keys = remember(list) { list.map { it.stashId } }
    val selected = vm.selected
    val selecting = selected.isNotEmpty()
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var sheetFor by remember { mutableStateOf<Bookmark?>(null) }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(context.getString(it.res, it.arg)) } }
    BackHandler(selecting) { vm.selected = emptySet() }

    val open = { b: Bookmark ->
        when (b.kind) {
            BookmarkKind.SCENE -> onOpenScene(b.stashId)
            BookmarkKind.PERFORMER -> onOpenPerformer(b.stashId)
            BookmarkKind.STUDIO -> onOpenStudio(b.stashId)
            BookmarkKind.TAG -> onOpenTag(b.stashId)
        }
    }
    val remove = { gone: List<Bookmark> ->
        vm.remove(gone)
        vm.selected = emptySet()
        scope.launch {
            val text = context.resources.getQuantityString(R.plurals.library_removed, gone.size, gone.size)
            if (snackbar.showSnackbar(text, context.getString(R.string.library_undo), duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) vm.restore(gone)
        }
    }

    val header = rememberCollapsingHeader()
    val dock = LocalDockInset.current
    Scaffold(
        Modifier.nestedScroll(header.connection),
        topBar = {
            CollapsingHeader(header) {
                if (selecting) {
                    SelectionBar(
                        selected.size,
                        onClose = { vm.selected = emptySet() },
                        onSelectAll = { vm.selected = keys.toSet() },
                        onMore = if (selected.size == 1) ({ sheetFor = list.firstOrNull { it.stashId in selected } }) else null,
                        onRemove = { remove(list.filter { it.stashId in selected }) },
                    )
                } else {
                    // No title bar (the dock names the tab, Saved | Downloads lives on it); kinds + layout toggle only.
                    Row(Modifier.statusBarsPadding().padding(top = LocalDockTop.current, end = Space.S), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = Space.L, vertical = Space.Xs), horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                            BookmarkKind.entries.forEach { k -> StashFilterChip(k == kind, { vm.selectKind(k) }, { Text(stringResource(k.labelRes())) }) }
                        }
                        if (kind == BookmarkKind.SCENE) ListModeToggle(mode)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = dock)) { AppSnackbar(it) } },
    ) { pad ->
        val rows = kind == BookmarkKind.SCENE && mode == ListMode.ROWS
        val gridState = vm.grids.getValue(kind)
        // Title sort gets the letter rail (bookmarks start after the header + sort rows, grid index 2).
        val rail = byTitle && list.size > 1
        val padding = listPadding(pad, dock).let { if (rail) PaddingValues(start = Space.L, top = it.calculateTopPadding(), end = AlphabetRailWidth + Space.Xs, bottom = it.calculateBottomPadding()) else it }
        LoadMoreEffect(gridState, remoteState != null && remoteState.started && remoteState.hasNext && !remoteState.loading && remoteState.error == null) { remote?.loadMore() }
        val remoteItems = remoteState?.items.orEmpty()
        val grid: @Composable () -> Unit = {
            LazyVerticalGrid(
                if (rows) GridCells.Fixed(1) else GridCells.Adaptive(160.dp),
                Modifier.fillMaxSize().dragSelect(gridState, keys, selected, { vm.selected = it }, Space.L),
                gridState,
                padding,
                verticalArrangement = Arrangement.spacedBy(if (rows) 2.dp else Space.M),
                horizontalArrangement = Arrangement.spacedBy(Space.M),
            ) {
                val remoteSettled = remoteState == null || (remoteState.started && !remoteState.loading)
                if (items != null && list.isEmpty() && remoteItems.isEmpty() && remoteSettled) {
                    fullSpan("empty") {
                        EmptyState(StashlingIcons.Saved, stringResource(R.string.saved_empty), body = stringResource(R.string.saved_empty_body)) {
                            ActionButton(stringResource(R.string.library_browse), { nav.tab(BrowseRoute) }, Modifier.padding(top = Space.S))
                        }
                    }
                }
                if (list.isNotEmpty()) {
                    fullSpan("h-bookmarks") { SectionHeader(stringResource(R.string.library_bookmarks)) }
                    fullSpan("sort") {
                        StashSegmented(listOf(stringResource(R.string.saved_sort_added), stringResource(R.string.saved_sort_title)), if (byTitle) 1 else 0, { vm.byTitle.value = it == 1 })
                    }
                    itemsIndexed(list, key = { _, b -> b.stashId }) { i, b ->
                        val isSelected = b.stashId in selected
                        SavedItem(
                            b,
                            rows,
                            i,
                            list.size,
                            selecting,
                            isSelected,
                            onClick = { if (selecting) vm.selected = if (isSelected) selected - b.stashId else selected + b.stashId else open(b) },
                            onSelect = { vm.selected = selected + b.stashId },
                        )
                    }
                }
                if (remoteItems.isNotEmpty() && remoteState != null) {
                    fullSpan("h-remote") { SectionHeader(stringResource(R.string.library_stashdb_favorites), Modifier.padding(top = Space.M)) }
                    if (kind == BookmarkKind.PERFORMER) {
                        itemsIndexed(performers.items, key = { _, p -> "r:${p.id}" }) { _, p ->
                            PosterTile(p.name, null, (p.images.firstOrNull() ?: p.thumbnail)?.sized(300), 2f / 3f, { onOpenPerformer(p.id) })
                        }
                    } else {
                        itemsIndexed(studios.items, key = { _, s -> "r:${s.id}" }) { _, s ->
                            PosterTile(s.name, s.parent?.name, s.images.firstOrNull()?.sized(300), 16f / 9f, { onOpenStudio(s.id) }, fit = true)
                        }
                    }
                    if (remoteState.loading || remoteState.error != null) fullSpan("footer") { PagingFooter(remoteState.loading, remoteState.error != null, { remote?.retry() }) }
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            if (remoteState != null) RefreshBox(remoteState.refreshing, vm::refreshRemote, Modifier.fillMaxSize()) { grid() } else grid()
            if (rail) AlphabetRail(remember(list) { letterPositions(list, 2) { it.title } }, { gridState.scrollToItem(it) }, Modifier.align(Alignment.TopEnd).padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()))
        }
    }

    sheetFor?.let { b ->
        ActionSheet(
            b.title,
            listOfNotNull(
                SheetAction(stringResource(R.string.library_open_stashdb), StashlingIcons.OpenExternal) { c.secrets.stash.value?.let { openLink(context, stashWebUrl(it.baseUrl, b.kind, b.stashId)) } },
                if (b.kind == BookmarkKind.SCENE && whisparr != null) SheetAction(stringResource(R.string.library_add_whisparr), StashlingIcons.Download) { vm.addToWhisparr(b.stashId) } else null,
                SheetAction(stringResource(R.string.library_remove), StashlingIcons.Delete, destructive = true) { remove(listOf(b)) },
            ),
            { sheetFor = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onMore: (() -> Unit)?,
    onRemove: () -> Unit,
) = TopAppBar(
    title = { Text(stringResource(R.string.library_selected, count)) },
    navigationIcon = { IconButton(onClose) { Icon(StashlingIcons.Close, stringResource(R.string.library_clear_selection)) } },
    actions = {
        TextButton(onSelectAll) { Text(stringResource(R.string.library_select_all)) }
        onMore?.let { IconButton(it) { Icon(StashlingIcons.More, stringResource(R.string.library_more)) } }
        IconButton(onRemove) { Icon(StashlingIcons.Delete, stringResource(R.string.library_remove)) }
    },
)

/** A bookmark tile (or scene row) with the selection look: scaled in, primary outline, check mark. */
@Composable
private fun SavedItem(
    b: Bookmark,
    rows: Boolean,
    index: Int,
    count: Int,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onSelect: () -> Unit,
) {
    val scale by animateFloatAsState(if (selected) 0.94f else 1f, label = "selected")
    val shape = if (rows) groupShape(index, count) else RoundedCornerShape(16.dp)
    val selectLabel = stringResource(R.string.library_select)
    val badges by LocalAppContainer.current.whisparrIndex.collectAsState()
    // The select mark takes the card's top-end corner while selecting.
    val badge = if (b.kind == BookmarkKind.SCENE && !selecting) badges[b.stashId] else null
    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .semantics {
                if (selecting) {
                    this.selected = selected
                } else {
                    onLongClick(selectLabel) {
                        onSelect()
                        true
                    }
                }
            },
    ) {
        if (rows) {
            MediaRow(b.title, b.subtitle, b.imageUrl, index, count, onClick, badge = badge)
        } else {
            // Scenes and studios are landscape, performers 2:3, tags text only (as before). The stored url is already sized.
            PosterTile(b.title, b.subtitle, b.imageUrl, if (b.kind == BookmarkKind.PERFORMER || b.kind == BookmarkKind.TAG) 2f / 3f else 16f / 9f, onClick, fit = b.kind == BookmarkKind.STUDIO, badge = badge)
        }
        if (selecting) SelectMark(selected, Modifier.align(if (rows) Alignment.TopStart else Alignment.TopEnd).padding(Space.M))
    }
}

@Composable
private fun SelectMark(
    selected: Boolean,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .size(24.dp)
            .background(if (selected) cs.primary else Color.Black.copy(alpha = 0.3f), CircleShape)
            .border(2.dp, if (selected) cs.primary else Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (selected) Icon(StashlingIcons.Check, null, Modifier.size(16.dp), tint = cs.onPrimary) }
}
