package net.venipa.stashling.feature.browse

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.stash.Favorites
import net.venipa.stashling.core.stash.NameSort
import net.venipa.stashling.core.stash.PerformerSort
import net.venipa.stashling.core.stash.SceneSort
import net.venipa.stashling.core.stash.SortDirection
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.feature.common.ListModeToggle
import net.venipa.stashling.feature.common.PagedGrid
import net.venipa.stashling.feature.common.PosterTile
import net.venipa.stashling.feature.common.ScenesGrid
import net.venipa.stashling.feature.common.StashTopBar
import net.venipa.stashling.feature.common.listPadding
import net.venipa.stashling.feature.common.rememberSceneListMode
import net.venipa.stashling.nav.LocalDockInset
import net.venipa.stashling.nav.SettingsAction

private enum class Sheet { Sort, Tags, Studio, Performer, Date, Favorites, Category }

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

/** Browse: kind chips, debounced search, active-filter chips (one small sheet each), then the grid of the chosen kind. */
@Composable
fun BrowseScreen(
    onOpenScene: (String) -> Unit,
    onOpenPerformer: (String) -> Unit,
    onOpenStudio: (String) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    val c = LocalAppContainer.current
    val vm = viewModel { BrowseViewModel(c.stash) }
    val kind by vm.kind.collectAsState()
    val f by vm.filters.collectAsState()
    val labels by vm.labels.collectAsState()
    val categories by vm.categories.collectAsState()
    val mode by rememberSceneListMode()
    var text by rememberSaveable { mutableStateOf(vm.query.value) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    Scaffold(
        topBar = {
            Column {
                StashTopBar(stringResource(R.string.nav_browse), actions = {
                    if (kind == BrowseKind.Scenes) ListModeToggle(mode)
                    SettingsAction()
                })
                TextField(
                    text,
                    {
                        text = it
                        vm.query.value = it
                    },
                    Modifier.fillMaxWidth().padding(horizontal = Space.L),
                    singleLine = true,
                    shape = CircleShape,
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
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                )
                ChipRow {
                    BrowseKind.entries.forEach { k -> FilterChip(k == kind, { vm.kind.value = k }, { Text(stringResource(k.label)) }) }
                }
                ChipRow {
                    when (kind) {
                        BrowseKind.Scenes -> {
                            val s = f.scene
                            Chip(stringResource(R.string.browse_sort, stringResource(s.sort.res())), s.sort != SceneSort.DATE || s.direction != SortDirection.DESC) { sheet = Sheet.Sort }
                            Chip(
                                if (s.tagIds.isEmpty()) stringResource(R.string.browse_filter_tags) else stringResource(R.string.browse_filter_tags_n, s.tagIds.size),
                                s.tagIds.isNotEmpty(),
                            ) {
                                vm.loadCategories()
                                sheet = Sheet.Tags
                            }
                            Chip(s.studioIds.firstOrNull()?.let { labels[it] } ?: stringResource(R.string.browse_filter_studio), s.studioIds.isNotEmpty()) { sheet = Sheet.Studio }
                            Chip(s.performerIds.firstOrNull()?.let { labels[it] } ?: stringResource(R.string.browse_filter_performer), s.performerIds.isNotEmpty()) { sheet = Sheet.Performer }
                            Chip(
                                s.date?.let { stringResource(if (it.before) R.string.browse_date_before_value else R.string.browse_date_after_value, it.date) } ?: stringResource(R.string.browse_filter_date),
                                s.date != null,
                            ) { sheet = Sheet.Date }
                            Chip(stringResource(R.string.browse_filter_favorites), s.favorites != null) { sheet = Sheet.Favorites }
                        }

                        BrowseKind.Performers -> {
                            val p = f.performer
                            Chip(stringResource(R.string.browse_sort, stringResource(p.sort.res())), p.sort != PerformerSort.POPULARITY || p.direction != SortDirection.DESC) { sheet = Sheet.Sort }
                            Chip(stringResource(R.string.browse_filter_favorites), p.favoritesOnly) { vm.update { it.copy(performer = p.copy(favoritesOnly = !p.favoritesOnly)) } }
                        }

                        BrowseKind.Studios -> {
                            val s = f.studio
                            Chip(stringResource(R.string.browse_sort, stringResource(s.sort.res())), s.sort != NameSort.NAME || s.direction != SortDirection.ASC) { sheet = Sheet.Sort }
                            Chip(stringResource(R.string.browse_filter_favorites), s.favoritesOnly) { vm.update { it.copy(studio = s.copy(favoritesOnly = !s.favoritesOnly)) } }
                        }

                        BrowseKind.Tags -> {
                            val t = f.tag
                            Chip(stringResource(R.string.browse_sort, stringResource(t.sort.res())), t.sort != NameSort.NAME || t.direction != SortDirection.ASC) { sheet = Sheet.Sort }
                            Chip(categories.firstOrNull { it.id == t.categoryId }?.name ?: stringResource(R.string.browse_filter_category), t.categoryId != null) {
                                vm.loadCategories()
                                sheet = Sheet.Category
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        val padding = listPadding(pad, LocalDockInset.current)
        key(kind) {
            when (kind) {
                BrowseKind.Scenes -> {
                    val st by vm.scenes.state.collectAsState()
                    ScenesGrid(st, mode, padding, vm::loadMore, vm::retry, onOpenScene)
                }

                BrowseKind.Performers -> {
                    val st by vm.performers.state.collectAsState()
                    PagedGrid(st, { it.id }, GridCells.Adaptive(150.dp), padding, vm::loadMore, vm::retry, stringResource(R.string.browse_empty)) { _, p ->
                        PosterTile(p.name, stringResource(R.string.browse_scenes_count, p.sceneCount), (p.images.firstOrNull() ?: p.thumbnail)?.sized(300), 2f / 3f, { onOpenPerformer(p.id) })
                    }
                }

                BrowseKind.Studios -> {
                    val st by vm.studios.state.collectAsState()
                    PagedGrid(st, { it.id }, GridCells.Adaptive(160.dp), padding, vm::loadMore, vm::retry, stringResource(R.string.browse_empty)) { _, s ->
                        PosterTile(s.name, s.parent?.name, s.images.firstOrNull()?.sized(300), 16f / 9f, { onOpenStudio(s.id) }, fit = true)
                    }
                }

                BrowseKind.Tags -> {
                    val st by vm.tags.state.collectAsState()
                    PagedGrid(st, { it.id }, GridCells.Adaptive(150.dp), padding, vm::loadMore, vm::retry, stringResource(R.string.browse_empty)) { _, t ->
                        PosterTile(t.name, t.category, null, 2f / 3f, { onOpenTag(t.id) })
                    }
                }
            }
        }
    }

    val close = { sheet = null }
    when (sheet) {
        Sheet.Sort -> when (kind) {
            BrowseKind.Scenes -> OptionsSheet(
                stringResource(R.string.browse_sort_title_sheet),
                SceneSort.entries.map { stringResource(it.res()) },
                f.scene.sort.ordinal,
                { i -> vm.update { it.copy(scene = it.scene.copy(sort = SceneSort.entries[i])) } },
                close,
                f.scene.direction == SortDirection.DESC,
                { d -> vm.update { it.copy(scene = it.scene.copy(direction = dir(d))) } },
            )

            BrowseKind.Performers -> OptionsSheet(
                stringResource(R.string.browse_sort_title_sheet),
                PerformerSort.entries.map { stringResource(it.res()) },
                f.performer.sort.ordinal,
                { i -> vm.update { it.copy(performer = it.performer.copy(sort = PerformerSort.entries[i])) } },
                close,
                f.performer.direction == SortDirection.DESC,
                { d -> vm.update { it.copy(performer = it.performer.copy(direction = dir(d))) } },
            )

            BrowseKind.Studios -> OptionsSheet(
                stringResource(R.string.browse_sort_title_sheet),
                NameSort.entries.map { stringResource(it.res()) },
                f.studio.sort.ordinal,
                { i -> vm.update { it.copy(studio = it.studio.copy(sort = NameSort.entries[i])) } },
                close,
                f.studio.direction == SortDirection.DESC,
                { d -> vm.update { it.copy(studio = it.studio.copy(direction = dir(d))) } },
            )

            BrowseKind.Tags -> OptionsSheet(
                stringResource(R.string.browse_sort_title_sheet),
                NameSort.entries.map { stringResource(it.res()) },
                f.tag.sort.ordinal,
                { i -> vm.update { it.copy(tag = it.tag.copy(sort = NameSort.entries[i])) } },
                close,
                f.tag.direction == SortDirection.DESC,
                { d -> vm.update { it.copy(tag = it.tag.copy(direction = dir(d))) } },
            )
        }

        Sheet.Tags -> {
            var cat by remember { mutableStateOf<String?>(null) }
            PickerSheet(
                stringResource(R.string.browse_filter_tags),
                f.scene.tagIds.toSet(),
                { q -> vm.searchTags(q, cat) },
                { item ->
                    vm.label(item.id, item.title)
                    vm.update { it.copy(scene = it.scene.copy(tagIds = if (item.id in it.scene.tagIds) it.scene.tagIds - item.id else it.scene.tagIds + item.id)) }
                },
                close,
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
                sheet = null
            },
            close,
        )

        Sheet.Performer -> PickerSheet(
            stringResource(R.string.browse_filter_performer),
            f.scene.performerIds.toSet(),
            vm::searchPerformers,
            { item ->
                val on = item.id in f.scene.performerIds
                if (!on) vm.label(item.id, item.title)
                vm.update { it.copy(scene = it.scene.copy(performerIds = if (on) emptyList() else listOf(item.id))) }
                sheet = null
            },
            close,
        )

        Sheet.Date -> DateSheet(f.scene.date, { d ->
            vm.update { it.copy(scene = it.scene.copy(date = d)) }
            sheet = null
        }, close)

        Sheet.Favorites -> {
            val opts = listOf(null, Favorites.PERFORMER, Favorites.STUDIO, Favorites.ALL)
            OptionsSheet(
                stringResource(R.string.browse_filter_favorites),
                listOf(R.string.browse_fav_off, R.string.browse_fav_performers, R.string.browse_fav_studios, R.string.browse_fav_both).map { stringResource(it) },
                opts.indexOf(f.scene.favorites),
                { i ->
                    vm.update { it.copy(scene = it.scene.copy(favorites = opts[i])) }
                    sheet = null
                },
                close,
            )
        }

        Sheet.Category -> OptionsSheet(
            stringResource(R.string.browse_filter_category),
            listOf(stringResource(R.string.browse_any)) + categories.map { it.name },
            categories.indexOfFirst { it.id == f.tag.categoryId } + 1,
            { i ->
                vm.update { it.copy(tag = it.tag.copy(categoryId = categories.getOrNull(i - 1)?.id)) }
                sheet = null
            },
            close,
        )

        null -> Unit
    }
}

private fun dir(desc: Boolean) = if (desc) SortDirection.DESC else SortDirection.ASC

@Composable
private fun ChipRow(content: @Composable () -> Unit) = Row(
    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.L, vertical = Space.Xs),
    horizontalArrangement = Arrangement.spacedBy(Space.S),
) { content() }

@Composable
private fun Chip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) = FilterChip(active, onClick, { Text(label, maxLines = 1) })
