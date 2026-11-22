package net.venipa.stashling.feature.browse

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.stash.Favorites
import net.venipa.stashling.core.stash.NameSort
import net.venipa.stashling.core.stash.PerformerSort
import net.venipa.stashling.core.stash.SceneSort
import net.venipa.stashling.core.stash.SortDirection
import net.venipa.stashling.core.stash.Tag
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.AppBottomSheet
import net.venipa.stashling.ds.FloatingHeader
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.SkeletonBox
import net.venipa.stashling.ds.SkeletonText
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.fieldColors
import net.venipa.stashling.ds.rememberCollapsingHeader
import net.venipa.stashling.ds.stableFieldHeight
import net.venipa.stashling.feature.common.BrowseIcons
import net.venipa.stashling.feature.common.FilterButton
import net.venipa.stashling.feature.common.ListModeSegmented
import net.venipa.stashling.feature.common.MediaRow
import net.venipa.stashling.feature.common.PagedGrid
import net.venipa.stashling.feature.common.PosterTile
import net.venipa.stashling.feature.common.Region
import net.venipa.stashling.feature.common.SceneRowSkeleton
import net.venipa.stashling.feature.common.ScenesGrid
import net.venipa.stashling.feature.common.groupShape
import net.venipa.stashling.feature.common.listPadding
import net.venipa.stashling.feature.common.rememberSceneListMode
import net.venipa.stashling.feature.common.tabViewModel
import net.venipa.stashling.nav.LocalDockInset

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

/**
 * Browse: segmented kinds, then the search pill + filter button (one full filter sheet). The kinds slide away on
 * scroll down, the search row always stays (2C). Focusing the pill swaps the grid for grouped suggestions + recents (4C).
 */
@Composable
fun BrowseScreen(
    onOpenScene: (String) -> Unit,
    onOpenPerformer: (String) -> Unit,
    onOpenStudio: (String) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    val c = LocalAppContainer.current
    val vm = tabViewModel { BrowseViewModel(c.stash) }
    val kind by vm.kind.collectAsState()
    val f by vm.filters.collectAsState()
    val mode by rememberSceneListMode()
    var text by rememberSaveable { mutableStateOf(vm.query.value) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var searching by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    BackHandler(searching) { focus.clearFocus() }

    val header = rememberCollapsingHeader()
    var headerPx by remember { mutableIntStateOf(0) }
    val padding = listPadding(PaddingValues(top = with(LocalDensity.current) { headerPx.toDp() }), LocalDockInset.current)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(header.follow)) {
        if (searching) {
            SuggestionsPanel(vm, text.isBlank(), padding, onRecent = {
                text = it
                vm.query.value = it
                vm.addRecent(it)
                focus.clearFocus()
            }, onPerformer = onOpenPerformer, onStudio = onOpenStudio, onTag = onOpenTag)
        } else {
            key(kind) { Results(vm, kind, mode, padding, onOpenScene, onOpenPerformer, onOpenStudio, onOpenTag) }
        }
        FloatingHeader(
            header,
            Modifier.onSizeChanged { headerPx = it.height },
            collapsible = {
                StashSegmented(BrowseKind.entries.map { stringResource(it.label) }, kind.ordinal, { vm.kind.value = BrowseKind.entries[it] }, Modifier.padding(horizontal = Space.L).padding(top = Space.S))
            },
            pinned = {
                Row(Modifier.padding(horizontal = Space.L, vertical = Space.S), horizontalArrangement = Arrangement.spacedBy(Space.S), verticalAlignment = Alignment.CenterVertically) {
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
                    FilterButton(f.activeCount(kind), {
                        if (kind == BrowseKind.Tags) vm.loadCategories()
                        sheet = Sheet.Filters
                    })
                }
            },
        )
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
                        FilterChip(cat == null, { cat = null }, { Text(stringResource(R.string.browse_any)) })
                        categories.forEach { c -> FilterChip(cat == c.id, { cat = c.id }, { Text(c.name) }) }
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
            val st by vm.scenes.state.collectAsState()
            ScenesGrid(st, mode, padding, vm::loadMore, vm::retry, onOpenScene, gridState = grid, onRefresh = vm::refresh)
        }

        // 6A: 2:3 grid, name + scene count.
        BrowseKind.Performers -> {
            val st by vm.performers.state.collectAsState()
            PagedGrid(st, { it.id }, GridCells.Adaptive(150.dp), padding, vm::loadMore, vm::retry, empty, gridState = grid, onRefresh = vm::refresh) { _, p ->
                PosterTile(p.name, stringResource(R.string.browse_scenes_count, p.sceneCount), (p.images.firstOrNull() ?: p.thumbnail)?.sized(300), 2f / 3f, { onOpenPerformer(p.id) })
            }
        }

        // 6B: list rows, logo (contained) + name + parent studio.
        BrowseKind.Studios -> {
            val st by vm.studios.state.collectAsState()
            PagedGrid(st, { it.id }, GridCells.Fixed(1), padding, vm::loadMore, vm::retry, empty, gridState = grid, spacing = 2.dp, onRefresh = vm::refresh, skeleton = { i, n -> SceneRowSkeleton(i, n) }) { i, s ->
                MediaRow(s.name, s.parent?.name, s.images.firstOrNull()?.sized(300), i, st.items.size, { onOpenStudio(s.id) }, scale = ContentScale.Fit)
            }
        }

        // 6A: grouped list under category headers (loaded pages re-sorted by category, name order kept inside).
        BrowseKind.Tags -> {
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
    padding: PaddingValues,
    onRecent: (String) -> Unit,
    onPerformer: (String) -> Unit,
    onStudio: (String) -> Unit,
    onTag: (String) -> Unit,
) {
    val recents by vm.recents.collectAsState()
    val s by vm.suggestions.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                        itemsIndexed(items, key = { _, it -> "$label:${it.id}" }) { i, it -> SheetRow(it.title, it.subtitle, false, groupShape(i, items.size)) { open(it.id) } }
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
            SectionHeader(stringResource(R.string.browse_sort_title_sheet))
            // Sort chips + direction for the current kind.
            val (names, selected, desc) = when (kind) {
                BrowseKind.Scenes -> Triple(SceneSort.entries.map { it.res() }, f.scene.sort.ordinal, f.scene.direction == SortDirection.DESC)
                BrowseKind.Performers -> Triple(PerformerSort.entries.map { it.res() }, f.performer.sort.ordinal, f.performer.direction == SortDirection.DESC)
                BrowseKind.Studios -> Triple(NameSort.entries.map { it.res() }, f.studio.sort.ordinal, f.studio.direction == SortDirection.DESC)
                BrowseKind.Tags -> Triple(NameSort.entries.map { it.res() }, f.tag.sort.ordinal, f.tag.direction == SortDirection.DESC)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                names.forEachIndexed { i, res ->
                    FilterChip(i == selected, {
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
            when (kind) {
                BrowseKind.Scenes -> {
                    val s = f.scene
                    if (javTag != null) {
                        SectionHeader(stringResource(R.string.browse_region))
                        StashSegmented(Region.entries.map { stringResource(it.label) }, f.region.ordinal, { i -> vm.update { it.copy(region = Region.entries[i]) } })
                    }
                    SectionHeader(stringResource(R.string.browse_filters))
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
                            FilterChip(s.favorites == opts[i], { vm.update { it.copy(scene = it.scene.copy(favorites = opts[i])) } }, { Text(stringResource(res)) })
                        }
                    }
                    SectionHeader(stringResource(R.string.browse_layout))
                    val mode by rememberSceneListMode()
                    ListModeSegmented(mode)
                }

                BrowseKind.Performers -> FilterChip(f.performer.favoritesOnly, { vm.update { it.copy(performer = it.performer.copy(favoritesOnly = !it.performer.favoritesOnly)) } }, { Text(stringResource(R.string.browse_filter_favorites)) })

                BrowseKind.Studios -> FilterChip(f.studio.favoritesOnly, { vm.update { it.copy(studio = it.studio.copy(favoritesOnly = !it.studio.favoritesOnly)) } }, { Text(stringResource(R.string.browse_filter_favorites)) })

                BrowseKind.Tags -> {
                    SectionHeader(stringResource(R.string.browse_filter_category))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                        FilterChip(f.tag.categoryId == null, { vm.update { it.copy(tag = it.tag.copy(categoryId = null)) } }, { Text(any) })
                        categories.forEach { cat -> FilterChip(f.tag.categoryId == cat.id, { vm.update { it.copy(tag = it.tag.copy(categoryId = cat.id)) } }, { Text(cat.name) }) }
                    }
                }
            }
            if (f.activeCount(kind) > 0) ActionButton(stringResource(R.string.browse_reset), { vm.reset(kind) }, tonal = true)
        }
    }
}
