package net.venipa.stashling.feature.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import net.venipa.stashling.core.stash.Studio
import net.venipa.stashling.ds.AppSnackbar
import net.venipa.stashling.ds.GroupRow
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.TonalGroup
import net.venipa.stashling.ds.concentricShape
import net.venipa.stashling.feature.common.BrowseIcons
import net.venipa.stashling.feature.common.EntityActions
import net.venipa.stashling.feature.common.ListModeToggle
import net.venipa.stashling.feature.common.Load
import net.venipa.stashling.feature.common.LoadGate
import net.venipa.stashling.feature.common.PageLoader
import net.venipa.stashling.feature.common.ScenesGrid
import net.venipa.stashling.feature.common.StashImage
import net.venipa.stashling.feature.common.StashTopBar
import net.venipa.stashling.feature.common.fullSpan
import net.venipa.stashling.feature.common.listPadding
import net.venipa.stashling.feature.common.loadOf
import net.venipa.stashling.feature.common.openLink
import net.venipa.stashling.feature.common.rememberSceneListMode

class StudioDetailViewModel(private val id: String, private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<Load<Studio>>(Load.Loading)
    val state: StateFlow<Load<Studio>> = _state
    val actions = EntityActions(viewModelScope, c.bookmarks, BookmarkKind.STUDIO, MutableStateFlow(id))
    private var studioIds = listOf(id)
    val scenes = PageLoader<SceneCard>(viewModelScope, { it.id }) { p -> c.stash.scenes(SceneFilter(studioIds = studioIds, sort = SceneSort.DATE), p) }

    init {
        load()
    }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = loadOf { c.stash.studio(id) }
            (_state.value as? Load.Data)?.value?.let { s ->
                actions.favorite.value = s.isFavorite
                // Scenes of the studio and its sub-studios: one INCLUDES filter over all of them.
                studioIds = listOf(id) + s.children.map { it.id }
                scenes.reload()
            }
        }
    }

    fun refresh() {
        c.stash.invalidate(id)
        c.stash.invalidate("scenes|")
        scenes.refresh()
        viewModelScope.launch { (loadOf { c.stash.studio(id) } as? Load.Data)?.let { _state.value = it } }
    }

    private fun image(s: Studio) = s.images.firstOrNull()?.sized(300)

    fun toggleBookmark(s: Studio) = actions.toggleBookmark(s.name, s.parent?.name, image(s))

    fun toggleFavorite(s: Studio) = actions.toggleFavorite(c.stash::setFavoriteStudio, s.name, s.parent?.name, image(s))
}

/** Studio: logo, aliases, links, parent and sub-studio chips, StashDB favorite, bookmark and its scenes (incl. sub-studios). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioDetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpenScene: (String) -> Unit,
    onOpenStudio: (String) -> Unit,
    onOpenGallery: (Int) -> Unit,
) {
    val c = LocalAppContainer.current
    val vm = viewModel(key = "studio-$id") { StudioDetailViewModel(id, c) }
    val load by vm.state.collectAsState()
    val bookmarked by vm.actions.bookmarked.collectAsState()
    val favorite by vm.actions.favorite.collectAsState()
    val scenes by vm.scenes.state.collectAsState()
    val mode by rememberSceneListMode()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val msg = vm.actions.messages
    LaunchedEffect(msg) { msg.collect { snackbar.showSnackbar(context.getString(it)) } }

    LoadGate(load, onBack, vm::load) { s ->
        Scaffold(
            topBar = {
                StashTopBar(s.name, onBack, actions = {
                    ListModeToggle(mode)
                    IconButton({ vm.toggleFavorite(s) }) { Icon(if (favorite) BrowseIcons.FavoriteFilled else BrowseIcons.Favorite, stringResource(if (favorite) R.string.detail_unfavorite else R.string.detail_favorite)) }
                    IconButton({ vm.toggleBookmark(s) }) { Icon(if (bookmarked) BrowseIcons.BookmarkFilled else BrowseIcons.Bookmark, stringResource(if (bookmarked) R.string.detail_unbookmark else R.string.detail_bookmark)) }
                })
            },
            snackbarHost = { SnackbarHost(snackbar) { AppSnackbar(it) } },
        ) { pad ->
            ScenesGrid(scenes, mode, listPadding(pad), vm.scenes::loadMore, vm.scenes::retry, onOpenScene, onRefresh = vm::refresh, header = {
                s.images.firstOrNull()?.let { img ->
                    fullSpan("logo") {
                        StashImage(img.sized(600), Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(concentricShape(16.dp, 0.dp)).clickable { onOpenGallery(0) }, s.name, ContentScale.Fit)
                    }
                }
                fullSpan("info") {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.L)) {
                        s.parent?.let { p ->
                            Column {
                                SectionHeader(stringResource(R.string.detail_parent_studio))
                                SuggestionChip({ onOpenStudio(p.id) }, { Text(p.name) })
                            }
                        }
                        if (s.children.isNotEmpty()) {
                            Column {
                                SectionHeader(stringResource(R.string.detail_sub_studios))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) { s.children.forEach { ch -> SuggestionChip({ onOpenStudio(ch.id) }, { Text(ch.name) }) } }
                            }
                        }
                        if (s.aliases.isNotEmpty()) TonalGroup { GroupRow(s.aliases.joinToString(), supporting = stringResource(R.string.detail_aliases)) }
                        if (s.urls.isNotEmpty()) TonalGroup { s.urls.forEach { l -> GroupRow(l.site, supporting = l.url, onClick = { openLink(context, l.url) }) } }
                    }
                }
                fullSpan("scenes-header") { SectionHeader(stringResource(R.string.detail_scenes), Modifier.padding(top = Space.S)) }
            })
        }
    }
}
