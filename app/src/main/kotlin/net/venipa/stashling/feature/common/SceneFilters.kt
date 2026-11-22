package net.venipa.stashling.feature.common

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.venipa.stashling.AppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.stash.Favorites
import net.venipa.stashling.core.stash.PerformerFilter
import net.venipa.stashling.core.stash.SceneFilter
import net.venipa.stashling.core.stash.SceneSort
import net.venipa.stashling.core.stash.SortDirection
import net.venipa.stashling.core.stash.StudioFilter
import net.venipa.stashling.core.stash.TagCategory
import net.venipa.stashling.core.stash.TagFilter
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.AppBottomSheet
import net.venipa.stashling.ds.LocalStashHaptics
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashFilterChip
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.ds.StashSwitch
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.fieldColors
import net.venipa.stashling.ds.fieldShape
import net.venipa.stashling.ds.stableFieldHeight
import net.venipa.stashling.feature.browse.DateSheet
import net.venipa.stashling.feature.browse.PickItem
import net.venipa.stashling.feature.browse.PickerSheet
import net.venipa.stashling.feature.browse.SheetColumn
import net.venipa.stashling.feature.browse.SheetRow

/** The fixed scope of a detail page's scene list: shown in the sheet, never removable. */
enum class SceneScope(@StringRes val label: Int) {
    Studio(R.string.browse_filter_studio),
    Performer(R.string.browse_filter_performer),
    Tag(R.string.browse_filter_tags),
}

/** What the user lays over the page's scope. [subStudios] only matters on studio pages. */
data class PageSceneFilter(
    val scene: SceneFilter = SceneFilter(),
    val region: Region = Region.All,
    val subStudios: Boolean = true,
)

/** Non-default filters (the filter button badge). */
fun PageSceneFilter.activeCount(): Int {
    val d = SceneFilter()
    return listOf(
        scene.sort != d.sort || scene.direction != d.direction,
        scene.studioIds.isNotEmpty(),
        scene.performerIds.isNotEmpty(),
        scene.tagIds.isNotEmpty(),
        scene.date != null,
        scene.favorites != null,
        !scene.title.isNullOrBlank(),
        region != Region.All,
        !subStudios,
    ).count { it }
}

/** [base] = the page's scope (studio / performer / tag ids); the user's filters go on top, scope ids always win. */
fun PageSceneFilter.build(
    base: SceneFilter,
    jav: String?,
): SceneFilter = scene.copy(
    studioIds = base.studioIds.ifEmpty { scene.studioIds },
    performerIds = base.performerIds.ifEmpty { scene.performerIds },
    tagIds = (base.tagIds + scene.tagIds).distinct(),
    title = scene.title?.trim()?.ifEmpty { null },
).withRegion(region, jav)

/** Per page instance: held by the page ViewModel, so it survives back navigation and starts fresh on a new page. */
class PageSceneFilters(
    private val scope: CoroutineScope,
    private val c: AppContainer,
    val kind: SceneScope,
) {
    val state = MutableStateFlow(PageSceneFilter())
    val labels = MutableStateFlow<Map<String, String>>(emptyMap())
    val categories = MutableStateFlow<List<TagCategory>>(emptyList())
    val javTag = MutableStateFlow<String?>(null)

    init {
        scope.launch { javTag.value = javTagId(c.stash) }
    }

    fun build(base: SceneFilter) = state.value.build(base, javTag.value)

    fun update(transform: (PageSceneFilter) -> PageSceneFilter) = state.update(transform)

    fun label(id: String, name: String) = labels.update { it + (id to name) }

    /** [reload] after each change once typing settles; never for the initial value, so coming back to the page does not reload. */
    @OptIn(FlowPreview::class)
    fun onChange(reload: () -> Unit) {
        scope.launch { state.drop(1).debounce(300).collect { reload() } }
    }

    fun loadCategories() {
        if (categories.value.isNotEmpty()) return
        scope.launch {
            try {
                categories.value = c.stash.tagCategories()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The tag sheet works without categories.
            }
        }
    }

    suspend fun searchTags(q: String, categoryId: String?) = c.stash.tags(TagFilter(names = q.ifEmpty { null }, categoryId = categoryId), 1).items.map { PickItem(it.id, it.name, null, it.category) }

    suspend fun searchStudios(q: String) = c.stash.studios(StudioFilter(names = q.ifEmpty { null }), 1).items.map { PickItem(it.id, it.name, it.parent?.name) }

    suspend fun searchPerformers(q: String) = c.stash.performers(PerformerFilter(names = q.ifEmpty { null }, gender = c.settingsNow.value?.performerGender?.apiValue()), 1).items.map {
        PickItem(it.id, it.name, it.disambiguation, imageUrl = (it.images.firstOrNull() ?: it.thumbnail)?.sized(300))
    }
}

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

private fun Favorites?.res() = when (this) {
    null -> R.string.browse_fav_off
    Favorites.PERFORMER -> R.string.browse_fav_performers
    Favorites.STUDIO -> R.string.browse_fav_studios
    Favorites.ALL -> R.string.browse_fav_both
}

private enum class Sheet { Filters, Tags, Studio, Performer, Date }

/**
 * Compact filter row over a detail page's scenes: the filter button (badge = active count) opening the Browse-style scene sheet,
 * then one removable chip per active filter. [scopeName] is the page's own studio / performer / tag (locked in the sheet).
 */
@Composable
fun SceneFilterBar(
    f: PageSceneFilters,
    scopeName: String,
    hasSubStudios: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val s by f.state.collectAsState()
    val labels by f.labels.collectAsState()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val haptic = LocalStashHaptics.current
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.S), verticalAlignment = Alignment.CenterVertically) {
        FilterButton(s.activeCount(), { sheet = Sheet.Filters })

        @Composable
        fun Chip(
            label: String,
            next: (PageSceneFilter) -> PageSceneFilter,
        ) {
            val remove = stringResource(R.string.browse_trait_remove, label)
            InputChip(true, {
                haptic.tick()
                f.update(next)
            }, { Text(label, maxLines = 1) }, Modifier.semantics { contentDescription = remove }, trailingIcon = { Icon(StashlingIcons.Close, null, Modifier.size(InputChipDefaults.IconSize)) })
        }
        val sc = s.scene
        val d = SceneFilter()
        if (sc.sort != d.sort || sc.direction != d.direction) {
            Chip("${stringResource(sc.sort.res())} ${if (sc.direction == SortDirection.DESC) "↓" else "↑"}") { it.copy(scene = it.scene.copy(sort = d.sort, direction = d.direction)) }
        }
        sc.title?.takeIf { it.isNotBlank() }?.let { t -> Chip("“${t.trim()}”") { it.copy(scene = it.scene.copy(title = null)) } }
        if (!s.subStudios) Chip(stringResource(R.string.scene_filter_without_sub)) { it.copy(subStudios = true) }
        sc.studioIds.forEach { id -> Chip(labels[id] ?: "?") { it.copy(scene = it.scene.copy(studioIds = it.scene.studioIds - id)) } }
        sc.performerIds.forEach { id -> Chip(labels[id] ?: "?") { it.copy(scene = it.scene.copy(performerIds = it.scene.performerIds - id)) } }
        sc.tagIds.forEach { id -> Chip(labels[id] ?: "?") { it.copy(scene = it.scene.copy(tagIds = it.scene.tagIds - id)) } }
        sc.date?.let { b -> Chip(stringResource(if (b.before) R.string.browse_date_before_value else R.string.browse_date_after_value, b.date)) { it.copy(scene = it.scene.copy(date = null)) } }
        sc.favorites?.let { fav -> Chip(stringResource(fav.res())) { it.copy(scene = it.scene.copy(favorites = null)) } }
        if (s.region != Region.All) Chip(stringResource(s.region.label)) { it.copy(region = Region.All) }
    }

    val back = { sheet = Sheet.Filters }
    when (sheet) {
        Sheet.Filters -> FilterSheet(f, s, labels, scopeName, hasSubStudios, { sheet = it }, { sheet = null })

        Sheet.Tags -> {
            val categories by f.categories.collectAsState()
            var cat by remember { mutableStateOf<String?>(null) }
            PickerSheet(
                stringResource(R.string.browse_filter_tags),
                s.scene.tagIds.toSet(),
                { q -> f.searchTags(q, cat) },
                { item ->
                    f.label(item.id, item.title)
                    f.update { it.copy(scene = it.scene.copy(tagIds = if (item.id in it.scene.tagIds) it.scene.tagIds - item.id else it.scene.tagIds + item.id)) }
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

        Sheet.Studio -> PickerSheet(stringResource(R.string.browse_filter_studio), s.scene.studioIds.toSet(), f::searchStudios, { item ->
            val on = item.id in s.scene.studioIds
            if (!on) f.label(item.id, item.title)
            f.update { it.copy(scene = it.scene.copy(studioIds = if (on) emptyList() else listOf(item.id))) }
            back()
        }, back)

        Sheet.Performer -> PickerSheet(stringResource(R.string.browse_filter_performer), s.scene.performerIds.toSet(), f::searchPerformers, { item ->
            val on = item.id in s.scene.performerIds
            if (!on) f.label(item.id, item.title)
            f.update { it.copy(scene = it.scene.copy(performerIds = if (on) emptyList() else listOf(item.id))) }
            back()
        }, back)

        Sheet.Date -> DateSheet(s.scene.date, { d ->
            f.update { it.copy(scene = it.scene.copy(date = d)) }
            back()
        }, back)

        null -> Unit
    }
}

/** The Browse scene sheet (sort + direction, region, pickers, favorites, layout) plus title search, the locked scope and sub-studios. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    f: PageSceneFilters,
    s: PageSceneFilter,
    labels: Map<String, String>,
    scopeName: String,
    hasSubStudios: Boolean,
    onPick: (Sheet) -> Unit,
    onDismiss: () -> Unit,
) = AppBottomSheet(onDismiss) {
    val javTag by f.javTag.collectAsState()
    val any = stringResource(R.string.browse_any)
    val sc = s.scene
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SheetColumn(stringResource(R.string.browse_filters)) {
            TextField(
                sc.title.orEmpty(),
                { t -> f.update { it.copy(scene = it.scene.copy(title = t)) } },
                Modifier.fillMaxWidth().stableFieldHeight(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.scene_filter_title)) },
                leadingIcon = { Icon(StashlingIcons.Search, null) },
                shape = fieldShape(),
                colors = fieldColors(),
            )
            SectionHeader(stringResource(R.string.browse_sort_title_sheet))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                SceneSort.entries.forEach { o -> StashFilterChip(o == sc.sort, { f.update { it.copy(scene = it.scene.copy(sort = o)) } }, { Text(stringResource(o.res())) }) }
            }
            StashSegmented(listOf(stringResource(R.string.browse_ascending), stringResource(R.string.browse_descending)), if (sc.direction == SortDirection.DESC) 1 else 0, { i ->
                f.update { it.copy(scene = it.scene.copy(direction = if (i == 1) SortDirection.DESC else SortDirection.ASC)) }
            })
            // On a tag page the scope is a tag, and stash-box ignores the Western exclude while tags are set.
            if (javTag != null && f.kind != SceneScope.Tag) {
                SectionHeader(stringResource(R.string.browse_region))
                StashSegmented(Region.entries.map { stringResource(it.label) }, s.region.ordinal, { i -> f.update { it.copy(region = Region.entries[i]) } })
            }
            // Locked scope first, then the pickers (not the one the scope fixes; a tag page can add more tags), then sub-studios.
            val rows = buildList<@Composable (Int, Int) -> Unit> {
                add { i, n -> LockedRow(stringResource(f.kind.label), stringResource(R.string.scene_filter_fixed, scopeName), groupShape(i, n, 12.dp)) }
                if (f.kind != SceneScope.Studio) add { i, n -> Picker(R.string.browse_filter_studio, sc.studioIds.firstOrNull()?.let { labels[it] }, any, i, n) { onPick(Sheet.Studio) } }
                if (f.kind != SceneScope.Performer) add { i, n -> Picker(R.string.browse_filter_performer, sc.performerIds.firstOrNull()?.let { labels[it] }, any, i, n) { onPick(Sheet.Performer) } }
                add { i, n ->
                    Picker(R.string.browse_filter_tags, sc.tagIds.takeIf { it.isNotEmpty() }?.joinToString { labels[it] ?: "?" }, any, i, n) {
                        f.loadCategories()
                        onPick(Sheet.Tags)
                    }
                }
                add { i, n -> Picker(R.string.browse_filter_date, sc.date?.let { stringResource(if (it.before) R.string.browse_date_before_value else R.string.browse_date_after_value, it.date) }, any, i, n) { onPick(Sheet.Date) } }
                if (f.kind == SceneScope.Studio && hasSubStudios) {
                    add { i, n ->
                        Row(
                            Modifier.fillMaxWidth().clip(groupShape(i, n, 12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = Space.L, vertical = Space.S),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.scene_filter_sub_studios), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            StashSwitch(s.subStudios, { on -> f.update { it.copy(subStudios = on) } })
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { rows.forEachIndexed { i, row -> row(i, rows.size) } }
            SectionHeader(stringResource(R.string.browse_filter_favorites))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                listOf(null, Favorites.PERFORMER, Favorites.STUDIO, Favorites.ALL).forEach { o ->
                    StashFilterChip(sc.favorites == o, { f.update { it.copy(scene = it.scene.copy(favorites = o)) } }, { Text(stringResource(o.res())) })
                }
            }
            SectionHeader(stringResource(R.string.browse_layout))
            val mode by rememberSceneListMode()
            ListModeSegmented(mode)
            if (s.activeCount() > 0) ActionButton(stringResource(R.string.browse_reset), { f.update { PageSceneFilter() } }, tonal = true)
        }
    }
}

@Composable
private fun Picker(
    @StringRes label: Int,
    value: String?,
    any: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) = SheetRow(stringResource(label), value ?: any, value != null, groupShape(index, count, 12.dp), onClick = onClick)

/** Same look as [SheetRow], not clickable: the page's own scope. */
@Composable
private fun LockedRow(
    label: String,
    value: String,
    shape: androidx.compose.foundation.shape.RoundedCornerShape,
) = Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = Space.L, vertical = Space.M)) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
