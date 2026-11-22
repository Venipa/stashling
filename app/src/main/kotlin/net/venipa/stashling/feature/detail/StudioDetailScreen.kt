package net.venipa.stashling.feature.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import net.venipa.stashling.core.stash.Studio
import net.venipa.stashling.ds.ActionSheet
import net.venipa.stashling.ds.AppSnackbar
import net.venipa.stashling.ds.GroupRow
import net.venipa.stashling.ds.LocalStashHaptics
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.SheetAction
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.TonalGroup
import net.venipa.stashling.ds.concentricShape
import net.venipa.stashling.feature.common.BrowseIcons
import net.venipa.stashling.feature.common.EntityActions
import net.venipa.stashling.feature.common.ListModeToggle
import net.venipa.stashling.feature.common.Load
import net.venipa.stashling.feature.common.LoadGate
import net.venipa.stashling.feature.common.PageLoader
import net.venipa.stashling.feature.common.PageSceneFilters
import net.venipa.stashling.feature.common.SceneFilterBar
import net.venipa.stashling.feature.common.SceneScope
import net.venipa.stashling.feature.common.ScenesGrid
import net.venipa.stashling.feature.common.StashImage
import net.venipa.stashling.feature.common.StashTopBar
import net.venipa.stashling.feature.common.collectVisible
import net.venipa.stashling.feature.common.fullSpan
import net.venipa.stashling.feature.common.listPadding
import net.venipa.stashling.feature.common.loadOf
import net.venipa.stashling.feature.common.openLink
import net.venipa.stashling.feature.common.rememberSceneListMode

class StudioDetailViewModel(private val id: String, private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<Load<Studio>>(Load.Loading)
    val state: StateFlow<Load<Studio>> = _state
    val actions = EntityActions(viewModelScope, c.bookmarks, BookmarkKind.STUDIO, MutableStateFlow(id))
    private var children = emptyList<String>()
    val filters = PageSceneFilters(viewModelScope, c, SceneScope.Studio)

    // Scenes of the studio and (switch on, default) its sub-studios: one INCLUDES filter over all of them.
    val scenes = PageLoader<SceneCard>(viewModelScope, { it.id }) { p ->
        val ids = listOf(id) + if (filters.state.value.subStudios) children else emptyList()
        c.stash.scenes(filters.build(SceneFilter(studioIds = ids)), p)
    }

    init {
        load()
        filters.onChange { if (_state.value is Load.Data) scenes.reload() }
    }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = loadOf { c.stash.studio(id) }
            (_state.value as? Load.Data)?.value?.let { s ->
                actions.favorite.value = s.isFavorite
                children = s.children.map { it.id }
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

/** Studio: compact breadcrumb header, StashDB favorite, bookmark and its scenes (incl. sub-studios). */
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
    val scenes = vm.scenes.collectVisible()
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
                    val haptic = LocalStashHaptics.current
                    IconButton({
                        haptic.confirm()
                        vm.toggleFavorite(s)
                    }) { Icon(if (favorite) BrowseIcons.FavoriteFilled else BrowseIcons.Favorite, stringResource(if (favorite) R.string.detail_unfavorite else R.string.detail_favorite)) }
                    IconButton({
                        haptic.confirm()
                        vm.toggleBookmark(s)
                    }) { Icon(if (bookmarked) BrowseIcons.BookmarkFilled else BrowseIcons.Bookmark, stringResource(if (bookmarked) R.string.detail_unbookmark else R.string.detail_bookmark)) }
                })
            },
            snackbarHost = { SnackbarHost(snackbar) { AppSnackbar(it) } },
        ) { pad ->
            ScenesGrid(scenes, mode, listPadding(pad), vm.scenes::loadMore, vm.scenes::retry, onOpenScene, onRefresh = vm::refresh, header = {
                fullSpan("info") { Header(s, onOpenStudio, onOpenGallery) }
                fullSpan("scenes-header") { SectionHeader(stringResource(R.string.detail_scenes), Modifier.padding(top = Space.S)) }
                fullSpan("scene-filters") { SceneFilterBar(vm.filters, s.name, hasSubStudios = s.children.isNotEmpty()) }
            })
        }
    }
}

/** 5C: logo beside the "Network › Studio" breadcrumb, aliases and one chip row; sub-studios and links open sheets. */
@Composable
private fun Header(
    s: Studio,
    onOpenStudio: (String) -> Unit,
    onOpenGallery: (Int) -> Unit,
) {
    val context = LocalContext.current
    var sheet by remember { mutableIntStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.L)) {
        val logo = s.images.firstOrNull()
        StashImage(
            logo?.sized(600),
            Modifier.width(128.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium).then(if (logo != null) Modifier.clickable { onOpenGallery(0) } else Modifier),
            s.name,
            ContentScale.Fit,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Xs)) {
            Breadcrumb(listOfNotNull(s.parent?.let { p -> p.name to { onOpenStudio(p.id) } }, s.name to null))
            if (s.aliases.isNotEmpty()) Text(s.aliases.joinToString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                val arrow: @Composable () -> Unit = { Icon(StashlingIcons.ChevronRight, null, Modifier.size(AssistChipDefaults.IconSize)) }
                if (s.children.isNotEmpty()) AssistChip({ sheet = 1 }, { Text(stringResource(R.string.detail_sub_studios_n, s.children.size)) }, trailingIcon = arrow)
                if (s.urls.isNotEmpty()) AssistChip({ sheet = 2 }, { Text(stringResource(R.string.detail_links_n, s.urls.size)) }, trailingIcon = arrow)
            }
        }
    }
    when (sheet) {
        1 -> ActionSheet(stringResource(R.string.detail_sub_studios), s.children.map { ch -> SheetAction(ch.name, StashlingIcons.Studio) { onOpenStudio(ch.id) } }) { sheet = 0 }
        2 -> ActionSheet(stringResource(R.string.detail_links), s.urls.map { l -> SheetAction(l.site, StashlingIcons.OpenExternal) { openLink(context, l.url) } }) { sheet = 0 }
    }
}
