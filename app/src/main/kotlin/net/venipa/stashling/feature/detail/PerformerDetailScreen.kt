package net.venipa.stashling.feature.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.venipa.stashling.AppContainer
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.BookmarkKind
import net.venipa.stashling.core.data.HiddenKind
import net.venipa.stashling.core.stash.Performer
import net.venipa.stashling.core.stash.PerformerRef
import net.venipa.stashling.core.stash.SceneCard
import net.venipa.stashling.core.stash.SceneFilter
import net.venipa.stashling.core.stash.SceneSort
import net.venipa.stashling.ds.ActionSheet
import net.venipa.stashling.ds.AppSnackbar
import net.venipa.stashling.ds.GroupRow
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
import net.venipa.stashling.feature.common.ScenesGrid
import net.venipa.stashling.feature.common.StashImage
import net.venipa.stashling.feature.common.StashTopBar
import net.venipa.stashling.feature.common.collectVisible
import net.venipa.stashling.feature.common.fullSpan
import net.venipa.stashling.feature.common.listPadding
import net.venipa.stashling.feature.common.loadOf
import net.venipa.stashling.feature.common.openLink
import net.venipa.stashling.feature.common.rememberSceneListMode
import net.venipa.stashling.feature.common.stashWebUrl

class PerformerDetailViewModel(startId: String, private val c: AppContainer) : ViewModel() {
    // The id after following merged_into_id.
    private val id = MutableStateFlow(startId)
    private val _state = MutableStateFlow<Load<Performer>>(Load.Loading)
    val state: StateFlow<Load<Performer>> = _state
    val studio = MutableStateFlow<String?>(null)
    val actions = EntityActions(viewModelScope, c.bookmarks, BookmarkKind.PERFORMER, id)
    val scenes = PageLoader<SceneCard>(viewModelScope, { it.id }) { p -> c.stash.scenes(filter(), p) }

    init {
        load()
    }

    private fun filter() = SceneFilter(performerIds = listOf(id.value), studioIds = listOfNotNull(studio.value), sort = SceneSort.DATE)

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = loadOf { follow() }
            if (_state.value is Load.Data) scenes.reload()
        }
    }

    fun refresh() {
        c.stash.invalidate(id.value)
        c.stash.invalidate("scenes|")
        scenes.refresh()
        viewModelScope.launch { (loadOf { follow() } as? Load.Data)?.let { _state.value = it } }
    }

    /** A merged performer redirects to its target (at most 3 hops); a bookmark on the old id moves along. */
    private suspend fun follow(): Performer? {
        var cur = id.value
        repeat(3) {
            val p = c.stash.performer(cur) ?: return null
            val next = p.mergedIntoId ?: return p.also {
                id.value = cur
                actions.favorite.value = it.isFavorite
            }
            c.bookmarks.rekey(BookmarkKind.PERFORMER, cur, next)
            cur = next
        }
        return null
    }

    fun filterStudio(studioId: String?) {
        studio.value = studioId
        scenes.reload()
    }

    private fun image(p: Performer) = (p.images.firstOrNull() ?: p.thumbnail)?.sized(300)

    fun toggleBookmark(p: Performer) = actions.toggleBookmark(p.name, p.disambiguation, image(p))

    fun toggleFavorite(p: Performer) = actions.toggleFavorite(c.stash::setFavoritePerformer, p.name, p.disambiguation, image(p))
}

private fun String.pretty() = lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Performer profile, image carousel, StashDB favorite heart, bookmark and a scene list with a studio filter. */
@Composable
fun PerformerDetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpenScene: (String) -> Unit,
    onOpenGallery: (Int) -> Unit,
) {
    val c = LocalAppContainer.current
    val vm = viewModel(key = "performer-$id") { PerformerDetailViewModel(id, c) }
    val load by vm.state.collectAsState()
    val bookmarked by vm.actions.bookmarked.collectAsState()
    val favorite by vm.actions.favorite.collectAsState()
    val studioId by vm.studio.collectAsState()
    val scenes = vm.scenes.collectVisible(keepPerformer = (load as? Load.Data)?.value?.id ?: id)
    val mode by rememberSceneListMode()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val msg = vm.actions.messages
    LaunchedEffect(msg) { msg.collect { snackbar.showSnackbar(context.getString(it)) } }

    var menu by remember { mutableStateOf(false) }
    val hidden by c.hiddenIds.collectAsState()
    LoadGate(load, onBack, vm::load) { p ->
        if (menu) {
            val isHidden = p.id in hidden.performers
            ActionSheet(
                p.name,
                listOf(
                    SheetAction(stringResource(R.string.library_open_stashdb), StashlingIcons.OpenExternal) { c.secrets.stash.value?.let { openLink(context, stashWebUrl(it.baseUrl, BookmarkKind.PERFORMER, p.id)) } },
                    // The page stays; lists drop the performer's scenes (snackbar with Undo from SceneActionsHost).
                    if (isHidden) {
                        SheetAction(stringResource(R.string.swipe_unhide_performer), StashlingIcons.Visibility) { c.sceneActions.unhide(HiddenKind.PERFORMER, p.id) }
                    } else {
                        SheetAction(stringResource(R.string.swipe_hide_performer), BrowseIcons.PersonRemove) { c.sceneActions.hidePerformers(listOf(PerformerRef(p.id, p.name, p.disambiguation, null, p.images.firstOrNull() ?: p.thumbnail))) }
                    },
                ),
            ) { menu = false }
        }
        Scaffold(
            topBar = {
                StashTopBar(p.name, onBack, actions = {
                    ListModeToggle(mode)
                    IconButton({ vm.toggleFavorite(p) }) { Icon(if (favorite) BrowseIcons.FavoriteFilled else BrowseIcons.Favorite, stringResource(if (favorite) R.string.detail_unfavorite else R.string.detail_favorite)) }
                    IconButton({ vm.toggleBookmark(p) }) { Icon(if (bookmarked) BrowseIcons.BookmarkFilled else BrowseIcons.Bookmark, stringResource(if (bookmarked) R.string.detail_unbookmark else R.string.detail_bookmark)) }
                    IconButton({ menu = true }) { Icon(StashlingIcons.More, stringResource(R.string.library_more)) }
                })
            },
            snackbarHost = { SnackbarHost(snackbar) { AppSnackbar(it) } },
        ) { pad ->
            ScenesGrid(scenes, mode, listPadding(pad), vm.scenes::loadMore, vm.scenes::retry, onOpenScene, onRefresh = vm::refresh, header = {
                if (p.images.isNotEmpty()) {
                    fullSpan("images") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                            items(p.images.size, key = { p.images[it].id }) { i ->
                                StashImage(p.images[i].sized(600), Modifier.height(240.dp).aspectRatio(2f / 3f).clip(concentricShape(16.dp, 0.dp)).clickable { onOpenGallery(i) }, p.name)
                            }
                        }
                    }
                }
                fullSpan("profile") { Profile(p) }
                if (p.studios.size > 1) {
                    fullSpan("studios") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                            p.studios.sortedByDescending { it.second }.forEach { (s, n) ->
                                FilterChip(studioId == s.id, { vm.filterStudio(if (studioId == s.id) null else s.id) }, { Text("${s.name} · $n") })
                            }
                        }
                    }
                }
                fullSpan("scenes-header") { SectionHeader(stringResource(R.string.detail_scenes_n, p.sceneCount), Modifier.padding(top = Space.S)) }
            })
        }
    }
}

@Composable
private fun Profile(p: Performer) {
    val context = LocalContext.current
    val rows = listOfNotNull(
        p.disambiguation?.let { R.string.detail_disambiguation to it },
        p.aliases.takeIf { it.isNotEmpty() }?.let { R.string.detail_aliases to it.joinToString() },
        p.gender?.let { R.string.detail_gender to it.pretty() },
        p.birthDate?.let { R.string.detail_born to (if (p.age != null && p.deathDate == null) "$it (${p.age})" else it) },
        p.deathDate?.let { R.string.detail_died to it },
        p.country?.let { R.string.detail_country to it },
        p.ethnicity?.let { R.string.detail_ethnicity to it.pretty() },
        p.eyeColor?.let { R.string.detail_eyes to it.pretty() },
        p.hairColor?.let { R.string.detail_hair to it.pretty() },
        p.heightCm?.let { R.string.detail_height to "$it cm" },
        measurements(p)?.let { R.string.detail_measurements to it },
        p.breastType?.let { R.string.detail_breasts to it.pretty() },
        (p.careerStart ?: p.careerEnd)?.let { R.string.detail_career to "${p.careerStart ?: "?"} – ${p.careerEnd ?: ""}".trim() },
        p.tattoos.takeIf { it.isNotEmpty() }?.let { R.string.detail_tattoos to it.joinToString { b -> listOfNotNull(b.location, b.description).joinToString(": ") } },
        p.piercings.takeIf { it.isNotEmpty() }?.let { R.string.detail_piercings to it.joinToString { b -> listOfNotNull(b.location, b.description).joinToString(": ") } },
    )
    Column(verticalArrangement = Arrangement.spacedBy(Space.L)) {
        if (rows.isNotEmpty()) TonalGroup { rows.forEach { (l, v) -> GroupRow(v, supporting = stringResource(l)) } }
        if (p.urls.isNotEmpty()) TonalGroup { p.urls.forEach { l -> GroupRow(l.site, supporting = l.url, onClick = { openLink(context, l.url) }) } }
    }
}

private fun measurements(p: Performer): String? {
    val bust = listOfNotNull(p.bandSize?.toString(), p.cupSize).joinToString("").ifEmpty { null }
    val parts = listOfNotNull(bust, p.waistSize?.toString(), p.hipSize?.toString())
    return parts.takeIf { it.isNotEmpty() }?.joinToString("-")
}
