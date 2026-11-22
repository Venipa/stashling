package net.venipa.stashling.feature.browse

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filter
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.stash.Favorites
import net.venipa.stashling.core.stash.NameSort
import net.venipa.stashling.core.stash.PerformerRef
import net.venipa.stashling.core.stash.PerformerSort
import net.venipa.stashling.core.stash.SceneSort
import net.venipa.stashling.core.stash.SortDirection
import net.venipa.stashling.core.stash.Tag
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.AlphabetRail
import net.venipa.stashling.ds.AlphabetRailWidth
import net.venipa.stashling.ds.AppBottomSheet
import net.venipa.stashling.ds.DockItemShape
import net.venipa.stashling.ds.DockScrollState
import net.venipa.stashling.ds.EmptyState
import net.venipa.stashling.ds.INDEX_LETTERS
import net.venipa.stashling.ds.LoadingPill
import net.venipa.stashling.ds.RefreshBox
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.SkeletonBox
import net.venipa.stashling.ds.SkeletonText
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashFilterChip
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.fieldColors
import net.venipa.stashling.ds.indexLetter
import net.venipa.stashling.ds.letterPositions
import net.venipa.stashling.ds.stableFieldHeight
import net.venipa.stashling.feature.common.BrowseIcons
import net.venipa.stashling.feature.common.ErrorState
import net.venipa.stashling.feature.common.FilterButton
import net.venipa.stashling.feature.common.ListModeSegmented
import net.venipa.stashling.feature.common.MediaRow
import net.venipa.stashling.feature.common.PageLoader
import net.venipa.stashling.feature.common.PagedGrid
import net.venipa.stashling.feature.common.PagedState
import net.venipa.stashling.feature.common.PosterTile
import net.venipa.stashling.feature.common.Region
import net.venipa.stashling.feature.common.SceneRowSkeleton
import net.venipa.stashling.feature.common.ScenesGrid
import net.venipa.stashling.feature.common.collectVisible
import net.venipa.stashling.feature.common.containerTransform
import net.venipa.stashling.feature.common.groupShape
import net.venipa.stashling.feature.common.listPadding
import net.venipa.stashling.feature.common.performerTransformKey
import net.venipa.stashling.feature.common.rememberPerformerMenu
import net.venipa.stashling.feature.common.rememberSceneListMode
import net.venipa.stashling.feature.common.seekLetter
import net.venipa.stashling.feature.common.tabViewModel
import net.venipa.stashling.nav.LocalDockInset
import net.venipa.stashling.nav.LocalDockTop

private enum class Sheet { Filters, Tags, Studio, Performer, Date }

@StringRes
private fun SceneSort.res() = when (this) {
    SceneSort.TITLE -> R.string.browse_sort_title
    SceneSort.DATE -> R.string.browse_sort_date
    SceneSort.DURATION -> R.string.browse_sort_duration
    SceneSort.TRENDING -> R.string.browse_sort_trending
    SceneSort.POPULARITY -> R.string.browse_sort_popularity
    SceneSort.CREATED_AT -> R.string.browse_sort_created
    SceneSort.UPDATED_AT -> R.string.browse_sort_updated
}

@StringRes
private fun PerformerSort.res() = when (this) {
    PerformerSort.NAME -> R.string.browse_sort_name
    PerformerSort.SCENE_COUNT -> R.string.browse_sort_scene_count
    PerformerSort.POPULARITY -> R.string.browse_sort_popularity
    PerformerSort.LAST_SCENE -> R.string.browse_sort_last_scene
    PerformerSort.DEBUT -> R.string.browse_sort_debut
    PerformerSort.CREATED_AT -> R.string.browse_sort_created
}

@StringRes
private fun NameSort.res() = when (this) {
    NameSort.NAME -> R.string.browse_sort_name
    NameSort.CREATED_AT -> R.string.browse_sort_created
    NameSort.UPDATED_AT -> R.string.browse_sort_updated
}

/** Browse: pure content; the kinds, search pill, suggestions and filter live on the dock ([BrowseDockControls]). */
@Composable
fun BrowseScreen(
    onOpenScene: (String) -> Unit,
    onOpenPerformer: (String) -> Unit,
    onOpenStudio: (String) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    val c = LocalAppContainer.current
    val vm = tabViewModel { BrowseViewModel(c.stash, c.settings) }
    val kind by vm.kind.collectAsState()
    val f by vm.filters.collectAsState()
    val mode by rememberSceneListMode()
    val padding = listPadding(PaddingValues(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + LocalDockTop.current), LocalDockInset.current)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        key(kind) { Results(vm, kind, f, mode, padding, onOpenScene, onOpenPerformer, onOpenStudio, onOpenTag) }
    }
}

/**
 * Browse's dock row: segmented kinds, then the search pill + filter button (one full filter sheet). Focusing the pill
 * opens grouped suggestions + recents upward on the same glass; the dock rides above the keyboard. Compact (4C): the active kind,
 * [ring] and the filter; tapping the kind expands again.
 */
@Composable
fun BrowseDockControls(
    dock: DockScrollState,
    ring: @Composable () -> Unit,
    onOpenPerformer: (String) -> Unit,
    onOpenStudio: (String) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    val c = LocalAppContainer.current
    val vm = tabViewModel { BrowseViewModel(c.stash, c.settings) }
    val kind by vm.kind.collectAsState()
    val f by vm.filters.collectAsState()
    var text by rememberSaveable { mutableStateOf(vm.query.value) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var searching by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    BackHandler(searching) { focus.clearFocus() }
    val openFilters = {
        if (kind == BrowseKind.Tags) vm.loadCategories()
        sheet = Sheet.Filters
    }
    val icons = listOf(StashlingIcons.Scenes, StashlingIcons.Person, StashlingIcons.Studio, StashlingIcons.Tag)
    if (dock.compact && !searching) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(DockItemShape).background(MaterialTheme.colorScheme.secondaryContainer).clickable { dock.compact = false }.padding(horizontal = Space.L),
                horizontalArrangement = Arrangement.spacedBy(Space.S, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icons[kind.ordinal], null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(text.ifBlank { stringResource(kind.label) }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ring()
            FilterButton(f.activeCount(kind), openFilters)
        }
    } else {
        if (searching) {
            SuggestionsPanel(vm, text.isBlank(), Modifier.fillMaxWidth().heightIn(max = 280.dp), onRecent = {
                text = it
                vm.query.value = it
                vm.addRecent(it)
                focus.clearFocus()
            }, onPerformer = onOpenPerformer, onStudio = onOpenStudio, onTag = onOpenTag)
        } else {
            StashSegmented(BrowseKind.entries.map { stringResource(it.label) }, kind.ordinal, { vm.kind.value = BrowseKind.entries[it] }, icons = icons)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                text,
                {
                    text = it
                    vm.query.value = it
                },
                Modifier.weight(1f).stableFieldHeight().onFocusChanged { searching = it.isFocused },
                singleLine = true,
                shape = CircleShape,
                colors = fieldColors(),
                placeholder = { Text(stringResource(R.string.browse_search_hint)) },
                leadingIcon = { Icon(StashlingIcons.Search, null) },
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton({
                            text = ""
                            vm.query.value = ""
                        }) { Icon(StashlingIcons.Close, stringResource(R.string.browse_clear)) }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    vm.addRecent(text)
                    focus.clearFocus()
                }),
            )
            FilterButton(f.activeCount(kind), openFilters)
        }
    }

    val back = { sheet = Sheet.Filters }
    when (sheet) {
        Sheet.Filters -> FilterSheet(vm, kind, f, { sheet = it }, { sheet = null })

        Sheet.Tags -> {
            val categories by vm.categories.collectAsState()
            var cat by remember { mutableStateOf<String?>(null) }
            PickerSheet(
                stringResource(R.string.browse_filter_tags),
                f.scene.tagIds.toSet(),
                { q -> vm.searchTags(q, cat) },
                { item ->
                    vm.label(item.id, item.title)
                    vm.update { it.copy(scene = it.scene.copy(tagIds = if (item.id in it.scene.tagIds) it.scene.tagIds - item.id else it.scene.tagIds + item.id)) }
                },
                back,
                searchKey = cat,
                header = {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                        StashFilterChip(cat == null, { cat = null }, { Text(stringResource(R.string.browse_any)) })
                        categories.forEach { c -> StashFilterChip(cat == c.id, { cat = c.id }, { Text(c.name) }) }
                    }
                },
            )
        }

        Sheet.Studio -> PickerSheet(
            stringResource(R.string.browse_filter_studio),
            f.scene.studioIds.toSet(),
            vm::searchStudios,
            { item ->
                val on = item.id in f.scene.studioIds
                if (!on) vm.label(item.id, item.title)
                vm.update { it.copy(scene = it.scene.copy(studioIds = if (on) emptyList() else listOf(item.id))) }
                back()
            },
            back,
        )

        Sheet.Performer -> PickerSheet(
            stringResource(R.string.browse_filter_performer),
            f.scene.performerIds.toSet(),
            vm::searchPerformers,
            { item ->
                val on = item.id in f.scene.performerIds
                if (!on) vm.label(item.id, item.title)
                vm.update { it.copy(scene = it.scene.copy(performerIds = if (on) emptyList() else listOf(item.id))) }
                back()
            },
            back,
        )

        Sheet.Date -> DateSheet(f.scene.date, { d ->
            vm.update { it.copy(scene = it.scene.copy(date = d)) }
            back()
        }, back)

        null -> Unit
    }
}

@Composable
private fun Results(
    vm: BrowseViewModel,
    kind: BrowseKind,
    f: Filters,
    mode: net.venipa.stashling.core.data.ListMode,
    padding: PaddingValues,
    onOpenScene: (String) -> Unit,
    onOpenPerformer: (String) -> Unit,
    onOpenStudio: (String) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    val grid = vm.gridStates.getValue(kind)
    val empty = stringResource(R.string.browse_empty)
    when (kind) {
        BrowseKind.Scenes -> {
            val st = vm.scenes.collectVisible()
            ScenesGrid(st, mode, padding, vm::loadMore, vm::retry, onOpenScene, gridState = grid, onRefresh = vm::refresh)
        }

        // 6A: 2:3 grid, name + scene count.
        BrowseKind.Performers -> {
            val st by vm.performers.state.collectAsState()
            val q by vm.query.collectAsState()
            // Fuzzy search (query, default filters) is relevance order: no rail there.
            val fuzzy = q.isNotBlank() && f.performer.copy(gender = null) == BrowsePerformers
            val byName = f.performer.sort == PerformerSort.NAME && f.performer.direction == SortDirection.ASC && !fuzzy
            NameRail(vm.performers, st, byName, padding, grid, { it.name }, { indexLetter(it.name, it.aliases) }) { padding ->
                PagedGrid(st, { it.id }, GridCells.Adaptive(150.dp), padding, vm::loadMore, vm::retry, empty, gridState = grid, onRefresh = vm::refresh) { _, p ->
                    val image = p.images.firstOrNull() ?: p.thumbnail
                    val menu = rememberPerformerMenu(PerformerRef(p.id, p.name, p.disambiguation, null, image), RoundedCornerShape(16.dp))
                    PosterTile(p.name, stringResource(R.string.browse_scenes_count, p.sceneCount), image?.sized(300), 2f / 3f, { onOpenPerformer(p.id) }, menu.modifier.containerTransform(performerTransformKey(p.id), RoundedCornerShape(16.dp)), onLongClick = menu.open)
                }
            }
        }

        // 6B: list rows, logo (contained) + name + parent studio.
        BrowseKind.Studios -> {
            val st by vm.studios.state.collectAsState()
            NameRail(vm.studios, st, f.studio.sort == NameSort.NAME && f.studio.direction == SortDirection.ASC, padding, grid, { it.name }, { indexLetter(it.name, it.aliases) }) { padding ->
                PagedGrid(st, { it.id }, GridCells.Fixed(1), padding, vm::loadMore, vm::retry, empty, gridState = grid, spacing = 2.dp, onRefresh = vm::refresh, skeleton = { i, n -> SceneRowSkeleton(i, n) }) { i, s ->
                    MediaRow(s.name, s.parent?.name, s.images.firstOrNull()?.sized(300), i, st.items.size, { onOpenStudio(s.id) }, scale = ContentScale.Fit)
                }
            }
        }

        BrowseKind.Tags -> if (f.tagsAz) {
            AzTags(vm, padding, onOpenTag)
        } else {
            // 6A: grouped list under category headers (loaded pages re-sorted by category, name order kept inside).
            val st by vm.tags.state.collectAsState()
            val sorted = remember(st.items) { st.items.sortedWith(compareBy(nullsLast()) { it.category }) }
            val pos = remember(sorted) {
                val m = HashMap<String, Pair<Int, Int>>()
                sorted.groupBy { it.category }.values.forEach { g -> g.forEachIndexed { i, t -> m[t.id] = i to g.size } }
                m
            }
            PagedGrid(
                st.copy(items = sorted),
                { it.id },
                GridCells.Fixed(1),
                padding,
                vm::loadMore,
                vm::retry,
                empty,
                gridState = grid,
                spacing = 2.dp,
                onRefresh = vm::refresh,
                skeleton = { i, n -> SkeletonBox(Modifier.fillMaxWidth().height(56.dp), groupShape(i, n)) },
            ) { _, t ->
                val (i, n) = pos.getValue(t.id)
                Column {
                    if (i == 0) SectionHeader(t.category ?: stringResource(R.string.browse_no_category), Modifier.padding(top = Space.M, start = Space.Xs))
                    TagRow(t, i, n) { onOpenTag(t.id) }
                }
            }
        }
    }
}

private fun PaddingValues.withRail() = PaddingValues(start = Space.L, top = calculateTopPadding(), end = AlphabetRailWidth + Space.Xs, bottom = calculateBottomPadding())

/**
 * A name-ascending remote list with the letter rail (always, any size). A letter seeks server-side ([seekLetter]: binary search
 * over cached pages, then the list restarts at that page and loads both ways). [letter] is the shown key (English alias for JAV names).
 */
@Composable
internal fun <T> NameRail(
    loader: PageLoader<T>,
    st: PagedState<T>,
    byName: Boolean,
    padding: PaddingValues,
    grid: LazyGridState,
    name: (T) -> String,
    letter: (T) -> Char,
    content: @Composable (PaddingValues) -> Unit,
) {
    // After a jump: earlier pages load when the top comes near (prepended items keep the scroll anchored by key).
    LaunchedEffect(grid, st.hasPrevious) {
        if (st.hasPrevious) snapshotFlow { grid.firstVisibleItemIndex <= 4 }.filter { it }.collect { loader.loadPrevious() }
    }
    val items by rememberUpdatedState(st.items)
    val current by remember(grid) { derivedStateOf { items.getOrNull(grid.firstVisibleItemIndex)?.let(letter) } }
    Box(Modifier.fillMaxSize()) {
        content(if (byName) padding.withRail() else padding)
        if (byName) {
            AlphabetRail(
                emptyMap(),
                {},
                Modifier.align(Alignment.TopEnd).padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
                seek = { l ->
                    try {
                        grid.scrollToItem(loader.seekLetter(l, name))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // A failed page keeps the list as it was.
                    }
                },
                current = current,
            )
        }
    }
}

/** Tags A–Z: every tag, sticky letter headers, the letter rail. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AzTags(
    vm: BrowseViewModel,
    padding: PaddingValues,
    onOpenTag: (String) -> Unit,
) {
    val st by vm.allTags.collectAsState()
    val groups = remember(st.items) { st.items.groupBy { indexLetter(it.name) }.toSortedMap(compareBy { INDEX_LETTERS.indexOf(it) }) }
    // Header + rows per letter, so a letter's position is its header item.
    val positions = remember(groups) {
        var i = 0
        groups.mapValues { (_, g) -> i.also { i += 1 + g.size } }
    }
    val list = vm.azList
    // Sticky headers stick at the list's own top, not below its content padding: the list starts below the status bar.
    Box(Modifier.fillMaxSize()) {
        RefreshBox(st.refreshing, vm::refresh, Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            LazyColumn(Modifier.fillMaxSize(), list, PaddingValues(start = Space.L, top = Space.S, end = AlphabetRailWidth + Space.Xs, bottom = padding.calculateBottomPadding()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val error = st.error
                when {
                    st.items.isEmpty() && error != null -> item { ErrorState(error, vm::retry) }

                    st.items.isEmpty() && st.loading -> items(8) { SkeletonBox(Modifier.fillMaxWidth().height(56.dp), groupShape(it, 8)) }

                    st.items.isEmpty() -> item { EmptyState(StashlingIcons.Search, stringResource(R.string.browse_empty)) }

                    else -> groups.forEach { (l, g) ->
                        stickyHeader(key = "h:$l") {
                            Text(l.toString(), Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = Space.Xs, vertical = Space.S), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        itemsIndexed(g, key = { _, t -> t.id }) { i, t -> TagRow(t, i, g.size) { onOpenTag(t.id) } }
                    }
                }
            }
        }
        if (positions.size > 1) AlphabetRail(positions, { list.scrollToItem(it) }, Modifier.align(Alignment.TopEnd).padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()))
        LoadingPill(st.loading && st.items.isEmpty(), Modifier.align(Alignment.Center))
    }
}

@Composable
private fun TagRow(
    t: Tag,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) = Column(
    Modifier.fillMaxWidth().clip(groupShape(index, count)).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(horizontal = Space.L, vertical = Space.M),
) {
    Text(t.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    t.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** 4C: recents while the pill is empty, else Performers / Studios / Tags groups from the debounced search calls. */
@Composable
private fun SuggestionsPanel(
    vm: BrowseViewModel,
    blank: Boolean,
    modifier: Modifier,
    onRecent: (String) -> Unit,
    onPerformer: (String) -> Unit,
    onStudio: (String) -> Unit,
    onTag: (String) -> Unit,
) {
    val recents by vm.recents.collectAsState()
    val s by vm.suggestions.collectAsState()
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (blank) {
            if (recents.isNotEmpty()) item { SectionHeader(stringResource(R.string.browse_recent)) }
            itemsIndexed(recents, key = { _, r -> "r:$r" }) { i, r ->
                Row(
                    Modifier.fillMaxWidth().clip(groupShape(i, recents.size)).background(MaterialTheme.colorScheme.surfaceContainer).clickable { onRecent(r) }.padding(horizontal = Space.L, vertical = Space.M),
                    horizontalArrangement = Arrangement.spacedBy(Space.M),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(BrowseIcons.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(r, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            val r = s
            if (r == null) {
                item { SkeletonText(Modifier.padding(vertical = Space.M), 4) }
            } else {
                listOf(
                    Triple(R.string.browse_kind_performers, r.performers, onPerformer),
                    Triple(R.string.browse_kind_studios, r.studios, onStudio),
                    Triple(R.string.browse_kind_tags, r.tags, onTag),
                ).forEach { (label, items, open) ->
                    if (items.isNotEmpty()) {
                        item(key = "h:$label") { SectionHeader(stringResource(label), Modifier.padding(top = Space.S)) }
                        itemsIndexed(items, key = { _, it -> "$label:${it.id}" }) { i, it -> SheetRow(it.title, it.subtitle, false, groupShape(i, items.size), it.imageUrl) { open(it.id) } }
                    }
                }
                if (r.performers.isEmpty() && r.studios.isEmpty() && r.tags.isEmpty()) item { Text(stringResource(R.string.browse_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

private fun dir(desc: Boolean) = if (desc) SortDirection.DESC else SortDirection.ASC

/** One full filter sheet per kind: sort + direction, then the kind's filters; pickers open on top and return here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    vm: BrowseViewModel,
    kind: BrowseKind,
    f: Filters,
    onPick: (Sheet) -> Unit,
    onDismiss: () -> Unit,
) = AppBottomSheet(onDismiss) {
    val labels by vm.labels.collectAsState()
    val categories by vm.categories.collectAsState()
    val javTag by vm.javTag.collectAsState()
    val any = stringResource(R.string.browse_any)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SheetColumn(stringResource(R.string.browse_filters)) {
            if (kind == BrowseKind.Tags) {
                SectionHeader(stringResource(R.string.browse_layout))
                StashSegmented(listOf(stringResource(R.string.browse_tags_by_category), stringResource(R.string.browse_tags_az)), if (f.tagsAz) 1 else 0, { i -> vm.update { it.copy(tagsAz = i == 1) } })
            }
            // A–Z tags are always by name.
            val showSort = kind != BrowseKind.Tags || !f.tagsAz
            if (showSort) SectionHeader(stringResource(R.string.browse_sort_title_sheet))
            // Sort chips + direction for the current kind.
            val (names, selected, desc) = when (kind) {
                BrowseKind.Scenes -> Triple(SceneSort.entries.map { it.res() }, f.scene.sort.ordinal, f.scene.direction == SortDirection.DESC)
                BrowseKind.Performers -> Triple(PerformerSort.entries.map { it.res() }, f.performer.sort.ordinal, f.performer.direction == SortDirection.DESC)
                BrowseKind.Studios -> Triple(NameSort.entries.map { it.res() }, f.studio.sort.ordinal, f.studio.direction == SortDirection.DESC)
                BrowseKind.Tags -> Triple(NameSort.entries.map { it.res() }, f.tag.sort.ordinal, f.tag.direction == SortDirection.DESC)
            }
            if (showSort) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                    names.forEachIndexed { i, res ->
                        StashFilterChip(i == selected, {
                            vm.update {
                                when (kind) {
                                    BrowseKind.Scenes -> it.copy(scene = it.scene.copy(sort = SceneSort.entries[i]))
                                    BrowseKind.Performers -> it.copy(performer = it.performer.copy(sort = PerformerSort.entries[i]))
                                    BrowseKind.Studios -> it.copy(studio = it.studio.copy(sort = NameSort.entries[i]))
                                    BrowseKind.Tags -> it.copy(tag = it.tag.copy(sort = NameSort.entries[i]))
                                }
                            }
                        }, { Text(stringResource(res)) })
                    }
                }
            }
            if (showSort) {
                StashSegmented(listOf(stringResource(R.string.browse_ascending), stringResource(R.string.browse_descending)), if (desc) 1 else 0, { i ->
                    val d = dir(i == 1)
                    vm.update {
                        when (kind) {
                            BrowseKind.Scenes -> it.copy(scene = it.scene.copy(direction = d))
                            BrowseKind.Performers -> it.copy(performer = it.performer.copy(direction = d))
                            BrowseKind.Studios -> it.copy(studio = it.studio.copy(direction = d))
                            BrowseKind.Tags -> it.copy(tag = it.tag.copy(direction = d))
                        }
                    }
                })
            }
            when (kind) {
                BrowseKind.Scenes -> {
                    val s = f.scene
                    if (javTag != null) {
                        SectionHeader(stringResource(R.string.browse_region))
                        StashSegmented(Region.entries.map { stringResource(it.label) }, f.region.ordinal, { i -> vm.update { it.copy(region = Region.entries[i]) } })
                    }
                    val rows = listOf(
                        Triple(R.string.browse_filter_studio, s.studioIds.firstOrNull()?.let { labels[it] }, Sheet.Studio),
                        Triple(R.string.browse_filter_performer, s.performerIds.firstOrNull()?.let { labels[it] }, Sheet.Performer),
                        Triple(R.string.browse_filter_tags, s.tagIds.takeIf { it.isNotEmpty() }?.joinToString { labels[it] ?: "?" }, Sheet.Tags),
                        Triple(R.string.browse_filter_date, s.date?.let { stringResource(if (it.before) R.string.browse_date_before_value else R.string.browse_date_after_value, it.date) }, Sheet.Date),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        rows.forEachIndexed { i, (label, value, target) ->
                            SheetRow(stringResource(label), value ?: any, value != null, groupShape(i, rows.size, 12.dp)) {
                                if (target == Sheet.Tags) vm.loadCategories()
                                onPick(target)
                            }
                        }
                    }
                    SectionHeader(stringResource(R.string.browse_filter_favorites))
                    val opts = listOf(null, Favorites.PERFORMER, Favorites.STUDIO, Favorites.ALL)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                        listOf(R.string.browse_fav_off, R.string.browse_fav_performers, R.string.browse_fav_studios, R.string.browse_fav_both).forEachIndexed { i, res ->
                            StashFilterChip(s.favorites == opts[i], { vm.update { it.copy(scene = it.scene.copy(favorites = opts[i])) } }, { Text(stringResource(res)) })
                        }
                    }
                    SectionHeader(stringResource(R.string.browse_layout))
                    val mode by rememberSceneListMode()
                    ListModeSegmented(mode)
                }

                BrowseKind.Performers -> StashFilterChip(f.performer.favoritesOnly, { vm.update { it.copy(performer = it.performer.copy(favoritesOnly = !it.performer.favoritesOnly)) } }, { Text(stringResource(R.string.browse_filter_favorites)) })

                BrowseKind.Studios -> StashFilterChip(f.studio.favoritesOnly, { vm.update { it.copy(studio = it.studio.copy(favoritesOnly = !it.studio.favoritesOnly)) } }, { Text(stringResource(R.string.browse_filter_favorites)) })

                BrowseKind.Tags -> {
                    SectionHeader(stringResource(R.string.browse_filter_category))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                        StashFilterChip(f.tag.categoryId == null, { vm.update { it.copy(tag = it.tag.copy(categoryId = null)) } }, { Text(any) })
                        categories.forEach { cat -> StashFilterChip(f.tag.categoryId == cat.id, { vm.update { it.copy(tag = it.tag.copy(categoryId = cat.id)) } }, { Text(cat.name) }) }
                    }
                }
            }
            if (f.activeCount(kind) > 0) ActionButton(stringResource(R.string.browse_reset), { vm.reset(kind) }, tonal = true)
        }
    }
}
