package net.venipa.stashling.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.venipa.stashling.R
import net.venipa.stashling.core.stash.Performer
import net.venipa.stashling.core.stash.PerformerFilter
import net.venipa.stashling.core.stash.SceneCard
import net.venipa.stashling.core.stash.SceneFilter
import net.venipa.stashling.core.stash.StashDbApi
import net.venipa.stashling.core.stash.Studio
import net.venipa.stashling.core.stash.StudioFilter
import net.venipa.stashling.core.stash.Tag
import net.venipa.stashling.core.stash.TagCategory
import net.venipa.stashling.core.stash.TagFilter
import net.venipa.stashling.feature.common.PageLoader

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
)

private data class Spec(val kind: BrowseKind, val query: String, val filter: Any)

class BrowseViewModel(private val stash: StashDbApi) : ViewModel() {
    val kind = MutableStateFlow(BrowseKind.Scenes)
    val query = MutableStateFlow("")
    val filters = MutableStateFlow(Filters())

    /** Names for the ids held in the scene filter (chip labels). */
    val labels = MutableStateFlow<Map<String, String>>(emptyMap())
    val categories = MutableStateFlow<List<TagCategory>>(emptyList())

    // The first reload comes from the spec collector below.
    val scenes = PageLoader<SceneCard>(viewModelScope, { it.id }) { p -> stash.scenes(SceneFilter(), p) }
    val performers = PageLoader<Performer>(viewModelScope, { it.id }) { p -> stash.performers(PerformerFilter(), p) }
    val studios = PageLoader<Studio>(viewModelScope, { it.id }) { p -> stash.studios(StudioFilter(), p) }
    val tags = PageLoader<Tag>(viewModelScope, { it.id }) { p -> stash.tags(TagFilter(), p) }

    init {
        viewModelScope.launch {
            // 400 ms after the last keystroke; clearing the box searches at once.
            @OptIn(FlowPreview::class)
            val q = query.debounce { if (it.isEmpty()) 0L else 400L }.distinctUntilChanged()
            combine(kind, q, filters) { k, text, f ->
                Spec(
                    k,
                    text.trim(),
                    when (k) {
                        BrowseKind.Scenes -> f.scene
                        BrowseKind.Performers -> f.performer
                        BrowseKind.Studios -> f.studio
                        BrowseKind.Tags -> f.tag
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

            is PerformerFilter -> performers.reload { p -> if (q.isNotEmpty() && f == PerformerFilter()) stash.searchPerformers(q, p) else stash.performers(f.copy(names = q.ifEmpty { null }), p) }

            is StudioFilter -> studios.reload { p -> stash.studios(f.copy(names = q.ifEmpty { null }), p) }

            is TagFilter -> tags.reload { p -> stash.tags(f.copy(names = q.ifEmpty { null }), p) }
        }
    }

    fun retry() = when (kind.value) {
        BrowseKind.Scenes -> scenes.retry()
        BrowseKind.Performers -> performers.retry()
        BrowseKind.Studios -> studios.retry()
        BrowseKind.Tags -> tags.retry()
    }

    fun loadMore() = when (kind.value) {
        BrowseKind.Scenes -> scenes.loadMore()
        BrowseKind.Performers -> performers.loadMore()
        BrowseKind.Studios -> studios.loadMore()
        BrowseKind.Tags -> tags.loadMore()
    }

    fun update(transform: (Filters) -> Filters) = filters.update(transform)

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

    suspend fun searchPerformers(q: String) = stash.performers(PerformerFilter(names = q.ifEmpty { null }), 1).items.map { PickItem(it.id, it.name, it.disambiguation) }
}
