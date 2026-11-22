package net.venipa.stashling.feature.browse

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.venipa.stashling.R
import net.venipa.stashling.core.data.Settings
import net.venipa.stashling.core.stash.CachingStashDbApi
import net.venipa.stashling.core.stash.NameSort
import net.venipa.stashling.core.stash.Performer
import net.venipa.stashling.core.stash.PerformerFilter
import net.venipa.stashling.core.stash.SceneCard
import net.venipa.stashling.core.stash.SceneFilter
import net.venipa.stashling.core.stash.SortDirection
import net.venipa.stashling.core.stash.Studio
import net.venipa.stashling.core.stash.StudioFilter
import net.venipa.stashling.core.stash.Tag
import net.venipa.stashling.core.stash.TagCategory
import net.venipa.stashling.core.stash.TagFilter
import net.venipa.stashling.feature.common.PageLoader
import net.venipa.stashling.feature.common.PagedState
import net.venipa.stashling.feature.common.Region
import net.venipa.stashling.feature.common.apiValue
import net.venipa.stashling.feature.common.javTagId
import net.venipa.stashling.feature.common.matches
import net.venipa.stashling.feature.common.withRegion

private const val SUGGEST = 4

enum class BrowseKind(val label: Int) {
    Scenes(R.string.browse_kind_scenes),
    Performers(R.string.browse_kind_performers),
    Studios(R.string.browse_kind_studios),
    Tags(R.string.browse_kind_tags),
}

data class Filters(
    val scene: SceneFilter = SceneFilter(),
    val performer: PerformerFilter = PerformerFilter(),
    val studio: StudioFilter = StudioFilter(),
    val tag: TagFilter = TagFilter(),
    val region: Region = Region.All,
    /** Tags: A–Z list with the letter rail (default) or grouped by category. Not a filter: not counted, not reset. */
    val tagsAz: Boolean = true,
)

/** Non-default filters of [kind] (the filter button badge). */
fun Filters.activeCount(kind: BrowseKind): Int = when (kind) {
    BrowseKind.Scenes -> {
        val d = SceneFilter()
        listOf(scene.sort != d.sort || scene.direction != d.direction, scene.studioIds.isNotEmpty(), scene.performerIds.isNotEmpty(), scene.tagIds.isNotEmpty(), scene.date != null, scene.favorites != null, region != Region.All)
    }

    BrowseKind.Performers -> listOf(performer.sort != PerformerFilter().sort || performer.direction != PerformerFilter().direction, performer.favoritesOnly)

    BrowseKind.Studios -> listOf(studio.sort != StudioFilter().sort || studio.direction != StudioFilter().direction, studio.favoritesOnly)

    BrowseKind.Tags -> listOf(tag.sort != TagFilter().sort || tag.direction != TagFilter().direction, tag.categoryId != null)
}.count { it }

/** Search-pill suggestions: a few rows per kind. */
data class Suggestions(val performers: List<PickItem>, val studios: List<PickItem>, val tags: List<PickItem>)

private data class Spec(val kind: BrowseKind, val query: String, val filter: Any)

/** Tags in A–Z mode: every page of [filter] at once. */
private data class AllTags(val filter: TagFilter)

class BrowseViewModel(
    private val stash: CachingStashDbApi,
    settings: Flow<Settings>,
) : ViewModel() {
    /** Settings.performerGender as a StashDB GenderFilterEnum name; null = both. */
    private val gender = settings.map { it.performerGender.apiValue() }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val kind = MutableStateFlow(BrowseKind.Scenes)
    val query = MutableStateFlow("")
    val filters = MutableStateFlow(Filters())

    /** Names for the ids held in the scene filter (chip labels). */
    val labels = MutableStateFlow<Map<String, String>>(emptyMap())
    val categories = MutableStateFlow<List<TagCategory>>(emptyList())
    val javTag = MutableStateFlow<String?>(null)

    /** null = typing / empty query. */
    val suggestions = MutableStateFlow<Suggestions?>(null)

    // shortcut: recents live in this activity-scoped VM (lost on process death); move to Settings once core has the field.
    val recents = MutableStateFlow<List<String>>(emptyList())

    // The first reload comes from the spec collector below.
    val scenes = PageLoader<SceneCard>(viewModelScope, { it.id }) { p -> stash.scenes(SceneFilter(), p) }
    val performers = PageLoader<Performer>(viewModelScope, { it.id }) { p -> stash.performers(PerformerFilter(), p) }
    val studios = PageLoader<Studio>(viewModelScope, { it.id }) { p -> stash.studios(StudioFilter(), p) }
    val tags = PageLoader<Tag>(viewModelScope, { it.id }) { p -> stash.tags(TagFilter(), p) }

    init {
        viewModelScope.launch { javTag.value = javTagId(stash) }
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            combine(query.debounce(400).map { it.trim() }.distinctUntilChanged(), gender) { q, g -> q to g }.collectLatest { (q, g) ->
                suggestions.value = null
                if (q.isNotEmpty()) {
                    suggestions.value = coroutineScope {
                        val p = async { quiet { stash.searchPerformers(q, 1, SUGGEST).items.filter { it.matches(g) }.map { PickItem(it.id, it.name, it.disambiguation) } } }
                        val s = async { quiet { stash.studios(StudioFilter(names = q), 1, SUGGEST).items.map { PickItem(it.id, it.name, it.parent?.name) } } }
                        val t = async { quiet { stash.tags(TagFilter(names = q), 1, SUGGEST).items.map { PickItem(it.id, it.name, it.category) } } }
                        Suggestions(p.await(), s.await(), t.await())
                    }
                }
            }
        }
        viewModelScope.launch {
            // 400 ms after the last keystroke; clearing the box searches at once.
            @OptIn(FlowPreview::class)
            val q = query.debounce { if (it.isEmpty()) 0L else 400L }.distinctUntilChanged()
            combine(kind, q, filters, javTag, gender) { k, text, f, jav, g ->
                Spec(
                    k,
                    text.trim(),
                    when (k) {
                        BrowseKind.Scenes -> f.scene.withRegion(f.region, jav)
                        BrowseKind.Performers -> f.performer.copy(gender = g)
                        BrowseKind.Studios -> f.studio
                        BrowseKind.Tags -> if (f.tagsAz) AllTags(f.tag) else f.tag
                    },
                )
            }.distinctUntilChanged().collect { load(it) }
        }
    }

    private fun load(spec: Spec) {
        val q = spec.query
        when (val f = spec.filter) {
            // searchScenes (fuzzy) only when no filter is set; otherwise the title filter.
            is SceneFilter -> scenes.reload { p -> if (q.isNotEmpty() && f == SceneFilter()) stash.searchScenes(q, p) else stash.scenes(f.copy(title = q.ifEmpty { null }), p) }

            // searchPerformers takes no gender: its pages are filtered here (counts/paging stay the server's).
            is PerformerFilter -> performers.reload { p -> if (q.isNotEmpty() && f.copy(gender = null) == PerformerFilter()) stash.searchPerformers(q, p).let { pg -> pg.copy(items = pg.items.filter { it.matches(f.gender) }) } else stash.performers(f.copy(names = q.ifEmpty { null }), p) }

            is StudioFilter -> studios.reload { p -> stash.studios(f.copy(names = q.ifEmpty { null }), p) }

            is TagFilter -> tags.reload { p -> stash.tags(f.copy(names = q.ifEmpty { null }), p) }

            is AllTags -> loadAllTags(f.filter.copy(names = q.ifEmpty { null }, sort = NameSort.NAME, direction = SortDirection.ASC))
        }
    }

    fun retry() = when (kind.value) {
        BrowseKind.Scenes -> scenes.retry()
        BrowseKind.Performers -> performers.retry()
        BrowseKind.Studios -> studios.retry()
        BrowseKind.Tags -> if (filters.value.tagsAz) loadAllTags(allFilter) else tags.retry()
    }

    fun loadMore() = when (kind.value) {
        BrowseKind.Scenes -> scenes.loadMore()
        BrowseKind.Performers -> performers.loadMore()
        BrowseKind.Studios -> studios.loadMore()
        BrowseKind.Tags -> if (filters.value.tagsAz) Unit else tags.loadMore()
    }

    val gridStates = BrowseKind.entries.associateWith { LazyGridState() }
    val azList = LazyListState()

    /** Pull to refresh: drop this kind's cached pages, then reload page 1 keeping what is shown. */
    fun refresh() {
        when (kind.value) {
            BrowseKind.Scenes -> stash.invalidate("cenes|").also { scenes.refresh() }
            BrowseKind.Performers -> stash.invalidate("erformers|").also { performers.refresh() }
            BrowseKind.Studios -> stash.invalidate("tudios|").also { studios.refresh() }
            BrowseKind.Tags -> stash.invalidate("tags|").also { if (filters.value.tagsAz) loadAllTags(allFilter, keep = true) else tags.refresh() }
        }
    }

    fun update(transform: (Filters) -> Filters) = filters.update(transform)

    /** A–Z tags: all pages (100 each, cached) so the letter rail can reach every letter. */
    val allTags = MutableStateFlow(PagedState<Tag>())
    private var allFilter = TagFilter()
    private var allJob: Job? = null

    private fun loadAllTags(
        f: TagFilter,
        keep: Boolean = false,
    ) {
        allFilter = f
        allJob?.cancel()
        allTags.value = if (keep) allTags.value.copy(refreshing = true) else PagedState(loading = true, started = true)
        allJob = viewModelScope.launch {
            try {
                val out = mutableListOf<Tag>()
                var page = 1
                do {
                    val p = stash.tags(f, page++, 100)
                    out += p.items
                } while (p.hasNext)
                allTags.value = PagedState(out.distinctBy { it.id }, hasNext = false, started = true, total = out.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                allTags.value = PagedState(allTags.value.items, error = e, hasNext = false, started = true)
            }
        }
    }

    fun reset(kind: BrowseKind) = filters.update {
        when (kind) {
            BrowseKind.Scenes -> it.copy(scene = SceneFilter(), region = Region.All)
            BrowseKind.Performers -> it.copy(performer = PerformerFilter())
            BrowseKind.Studios -> it.copy(studio = StudioFilter())
            BrowseKind.Tags -> it.copy(tag = TagFilter())
        }
    }

    /** Last 10, newest first, case-insensitive unique. */
    fun addRecent(q: String) {
        val t = q.trim()
        if (t.isNotEmpty()) recents.update { (listOf(t) + it.filterNot { r -> r.equals(t, true) }).take(10) }
    }

    // A failed suggestion group is just empty; the grid shows the real error.
    private suspend fun quiet(block: suspend () -> List<PickItem>) = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    fun label(id: String, name: String) = labels.update { it + (id to name) }

    fun loadCategories() {
        if (categories.value.isNotEmpty()) return
        viewModelScope.launch {
            try {
                categories.value = stash.tagCategories()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The tag sheet works without categories (no grouping).
            }
        }
    }

    // Picker searches: first page only.
    suspend fun searchTags(q: String, categoryId: String?) = stash.tags(TagFilter(names = q.ifEmpty { null }, categoryId = categoryId), 1).items.map { PickItem(it.id, it.name, null, it.category) }

    suspend fun searchStudios(q: String) = stash.studios(StudioFilter(names = q.ifEmpty { null }), 1).items.map { PickItem(it.id, it.name, it.parent?.name) }

    suspend fun searchPerformers(q: String) = stash.performers(PerformerFilter(names = q.ifEmpty { null }, gender = gender.value), 1).items.map { PickItem(it.id, it.name, it.disambiguation) }
}
