package net.venipa.stashling.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.venipa.stashling.AppContainer
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.BookmarkKind
import net.venipa.stashling.core.stash.SceneCard
import net.venipa.stashling.core.stash.SceneFilter
import net.venipa.stashling.core.stash.SceneSort
import net.venipa.stashling.core.stash.Tag
import net.venipa.stashling.ds.GroupRow
import net.venipa.stashling.ds.LocalStashHaptics
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.TonalCard
import net.venipa.stashling.ds.TonalGroup
import net.venipa.stashling.feature.common.BrowseIcons
import net.venipa.stashling.feature.common.EntityActions
import net.venipa.stashling.feature.common.ListModeToggle
import net.venipa.stashling.feature.common.Load
import net.venipa.stashling.feature.common.LoadGate
import net.venipa.stashling.feature.common.PageLoader
import net.venipa.stashling.feature.common.ScenesGrid
import net.venipa.stashling.feature.common.StashTopBar
import net.venipa.stashling.feature.common.collectVisible
import net.venipa.stashling.feature.common.fullSpan
import net.venipa.stashling.feature.common.listPadding
import net.venipa.stashling.feature.common.loadOf
import net.venipa.stashling.feature.common.rememberSceneListMode

class TagDetailViewModel(private val id: String, private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<Load<Tag>>(Load.Loading)
    val state: StateFlow<Load<Tag>> = _state
    val actions = EntityActions(viewModelScope, c.bookmarks, BookmarkKind.TAG, MutableStateFlow(id))
    val scenes = PageLoader<SceneCard>(viewModelScope, { it.id }) { p -> c.stash.scenes(SceneFilter(tagIds = listOf(id), sort = SceneSort.DATE), p) }

    init {
        load()
    }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = loadOf { c.stash.tag(id) }
            if (_state.value is Load.Data) scenes.reload()
        }
    }

    fun refresh() {
        c.stash.invalidate(id)
        c.stash.invalidate("scenes|")
        scenes.refresh()
        viewModelScope.launch { (loadOf { c.stash.tag(id) } as? Load.Data)?.let { _state.value = it } }
    }
}

/** Tag: category, aliases, description and its scenes. */
@Composable
fun TagDetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpenScene: (String) -> Unit,
) {
    val c = LocalAppContainer.current
    val vm = viewModel(key = "tag-$id") { TagDetailViewModel(id, c) }
    val load by vm.state.collectAsState()
    val bookmarked by vm.actions.bookmarked.collectAsState()
    val scenes = vm.scenes.collectVisible()
    val mode by rememberSceneListMode()
    LoadGate(load, onBack, vm::load) { t ->
        Scaffold(
            topBar = {
                StashTopBar(t.name, onBack, actions = {
                    ListModeToggle(mode)
                    val haptic = LocalStashHaptics.current
                    IconButton({
                        haptic.confirm()
                        vm.actions.toggleBookmark(t.name, t.category, null)
                    }) {
                        Icon(if (bookmarked) BrowseIcons.BookmarkFilled else BrowseIcons.Bookmark, stringResource(if (bookmarked) R.string.detail_unbookmark else R.string.detail_bookmark))
                    }
                })
            },
        ) { pad ->
            ScenesGrid(scenes, mode, listPadding(pad), vm.scenes::loadMore, vm.scenes::retry, onOpenScene, onRefresh = vm::refresh, header = {
                // Same compact header as the studio: "Category › Tag" breadcrumb, aliases, description clamped to 3 lines.
                fullSpan("info") {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.Xs)) {
                        Breadcrumb(listOfNotNull(t.category?.let { it to null }, t.name to null))
                        if (t.aliases.isNotEmpty()) Text(t.aliases.joinToString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        t.description?.takeIf { it.isNotBlank() }?.let { ClampedText(it, Modifier.padding(top = Space.Xs)) }
                    }
                }
                fullSpan("scenes-header") { SectionHeader(stringResource(R.string.detail_scenes), Modifier.padding(top = Space.S)) }
            })
        }
    }
}
