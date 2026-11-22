package net.venipa.stashling.feature.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import net.venipa.stashling.core.stash.PerformerTrait
import net.venipa.stashling.core.stash.SceneCard
import net.venipa.stashling.core.stash.SceneFilter
import net.venipa.stashling.core.stash.TraitKey
import net.venipa.stashling.ds.ActionSheet
import net.venipa.stashling.ds.AppSnackbar
import net.venipa.stashling.ds.GroupRow
import net.venipa.stashling.ds.LocalStashHaptics
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.SheetAction
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashFilterChip
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.TonalGroup
import net.venipa.stashling.ds.blockPointerPassThrough
import net.venipa.stashling.ds.concentricShape
import net.venipa.stashling.ds.groupItemShape
import net.venipa.stashling.feature.browse.BrowseViewModel
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
import net.venipa.stashling.feature.common.stashWebUrl
import net.venipa.stashling.feature.common.tabViewModel
import net.venipa.stashling.nav.BrowseRoute
import net.venipa.stashling.nav.LocalNavigator

class PerformerDetailViewModel(startId: String, private val c: AppContainer) : ViewModel() {
    // The id after following merged_into_id.
    private val id = MutableStateFlow(startId)
    private val _state = MutableStateFlow<Load<Performer>>(Load.Loading)
    val state: StateFlow<Load<Performer>> = _state
    val actions = EntityActions(viewModelScope, c.bookmarks, BookmarkKind.PERFORMER, id)
    val filters = PageSceneFilters(viewModelScope, c, SceneScope.Performer)
    val scenes = PageLoader<SceneCard>(viewModelScope, { it.id }) { p -> c.stash.scenes(filters.build(SceneFilter(performerIds = listOf(id.value))), p) }

    init {
        load()
        filters.onChange { if (_state.value is Load.Data) scenes.reload() }
    }

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

    /** The studio chips set the same single studio filter as the sheet. */
    fun filterStudio(studioId: String?, name: String) {
        studioId?.let { filters.label(it, name) }
        filters.update { it.copy(scene = it.scene.copy(studioIds = listOfNotNull(studioId))) }
    }

    private fun image(p: Performer) = (p.images.firstOrNull() ?: p.thumbnail)?.sized(300)

    fun toggleBookmark(p: Performer) = actions.toggleBookmark(p.name, p.disambiguation, image(p))

    fun toggleFavorite(p: Performer) = actions.toggleFavorite(c.stash::setFavoritePerformer, p.name, p.disambiguation, image(p))
}

private fun String.pretty() = lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/**
 * Performer: header with a paging portrait, name, heart and bookmark; four stat tiles; sticky "Scenes · About"
 * tabs. Scenes = studio filter chips + the grid; About = every fact as a grouped list. A searchable trait opens Browse → Performers
 * filtered by it.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    val sceneFilter by vm.filters.state.collectAsState()
    val studioId = sceneFilter.scene.studioIds.firstOrNull()
    val scenes = vm.scenes.collectVisible(keepPerformer = (load as? Load.Data)?.value?.id ?: id)
    val mode by rememberSceneListMode()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val msg = vm.actions.messages
    LaunchedEffect(msg) { msg.collect { snackbar.showSnackbar(context.getString(it)) } }
    val browse = tabViewModel { BrowseViewModel(c.stash, c.settings) }
    val nav = LocalNavigator.current
    val haptic = LocalStashHaptics.current
    val onTrait: (PerformerTrait?, String?) -> Unit = { t, g ->
        haptic.tick()
        browse.showPerformers(t, g)
        nav.tab(BrowseRoute)
    }

    var menu by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val grid = rememberLazyGridState()
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
        // The name moves into the top bar once the header has scrolled away.
        val scrolled by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 } }
        Scaffold(
            topBar = {
                StashTopBar(if (scrolled) p.name else "", onBack, actions = {
                    if (tab == 0) ListModeToggle(mode)
                    IconButton({ menu = true }) { Icon(StashlingIcons.More, stringResource(R.string.library_more)) }
                })
            },
            snackbarHost = { SnackbarHost(snackbar) { AppSnackbar(it) } },
        ) { pad ->
            // Top padding as a modifier: the sticky tabs pin under the top bar, not behind it.
            ScenesGrid(
                scenes,
                mode,
                listPadding(PaddingValues(bottom = pad.calculateBottomPadding())),
                vm.scenes::loadMore,
                vm.scenes::retry,
                onOpenScene,
                Modifier.padding(top = pad.calculateTopPadding()),
                gridState = grid,
                onRefresh = vm::refresh,
                showItems = tab == 0,
                header = {
                    fullSpan("profile") {
                        Header(p, favorite, bookmarked, onOpenGallery, onFavorite = {
                            haptic.confirm()
                            vm.toggleFavorite(p)
                        }, onBookmark = {
                            haptic.confirm()
                            vm.toggleBookmark(p)
                        })
                    }
                    fullSpan("stats") { StatTiles(p, onTrait) }
                    stickyHeader("tabs") {
                        StashSegmented(
                            listOf(stringResource(R.string.detail_scenes_n, p.sceneCount), stringResource(R.string.detail_about)),
                            tab,
                            { tab = it },
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).blockPointerPassThrough().padding(vertical = Space.S),
                        )
                    }
                    if (tab == 1) {
                        fullSpan("about") { About(p, onTrait) }
                    } else {
                        fullSpan("scene-filters") { SceneFilterBar(vm.filters, p.name) }
                    }
                    if (tab == 0 && p.studios.size > 1) {
                        fullSpan("studios") {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                                p.studios.sortedByDescending { it.second }.forEach { (s, n) ->
                                    StashFilterChip(studioId == s.id, { vm.filterStudio(if (studioId == s.id) null else s.id, s.name) }, { Text("${s.name} · $n") })
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun Header(
    p: Performer,
    favorite: Boolean,
    bookmarked: Boolean,
    onOpenGallery: (Int) -> Unit,
    onFavorite: () -> Unit,
    onBookmark: () -> Unit,
) = Row(horizontalArrangement = Arrangement.spacedBy(Space.L)) {
    val images = p.images.ifEmpty { listOfNotNull(p.thumbnail) }
    val pager = rememberPagerState { images.size }
    Box(Modifier.width(128.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.large)) {
        HeroPager(images, 600, Modifier.fillMaxSize(), p.name, pager, onOpenGallery)
        PagerDots(pager, Modifier.align(Alignment.BottomCenter).padding(bottom = Space.S))
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Xs)) {
        Text(p.name, style = MaterialTheme.typography.headlineSmall)
        p.disambiguation?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (p.aliases.isNotEmpty()) Text(p.aliases.joinToString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row {
            IconButton(onFavorite) { Icon(if (favorite) BrowseIcons.FavoriteFilled else BrowseIcons.Favorite, stringResource(if (favorite) R.string.detail_unfavorite else R.string.detail_favorite)) }
            IconButton(onBookmark) { Icon(if (bookmarked) BrowseIcons.BookmarkFilled else BrowseIcons.Bookmark, stringResource(if (bookmarked) R.string.detail_unbookmark else R.string.detail_bookmark)) }
        }
    }
}

private class Fact(val label: Int, val value: String, val trait: PerformerTrait? = null, val gender: String? = null)

/** 4A: age, height, country, career as one connected row; a missing fact drops its tile. Tiles with a trait open Browse. */
@Composable
private fun StatTiles(
    p: Performer,
    onTrait: (PerformerTrait?, String?) -> Unit,
) {
    val tiles = listOfNotNull(
        p.age?.takeIf { p.deathDate == null }?.let { Fact(R.string.detail_age, "$it", p.birthDate?.take(4)?.let { y -> PerformerTrait(TraitKey.BIRTH_YEAR, y) }) },
        p.heightCm?.let { Fact(R.string.detail_height, "$it cm", PerformerTrait(TraitKey.HEIGHT, "$it")) },
        p.country?.let { Fact(R.string.detail_country, it, PerformerTrait(TraitKey.COUNTRY, it)) },
        (p.careerStart ?: p.careerEnd)?.let { Fact(R.string.detail_career, "${p.careerStart ?: "?"}–${p.careerEnd ?: ""}", p.careerStart?.let { y -> PerformerTrait(TraitKey.CAREER_START, "$y") }) },
    )
    if (tiles.isEmpty()) return
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        tiles.forEachIndexed { i, f ->
            val label = stringResource(f.label)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(groupItemShape(i, tiles.size, vertical = false))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .then(if (f.trait != null) Modifier.clickable(onClickLabel = stringResource(R.string.detail_find_like, label)) { onTrait(f.trait, null) } else Modifier)
                    .padding(horizontal = Space.M, vertical = Space.S),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.value, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (f.trait != null) Icon(StashlingIcons.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** 4A About tab: the rest as grouped rows (identity, body, marks, links); a row with a searchable trait ends in an arrow. */
@Composable
private fun About(
    p: Performer,
    onTrait: (PerformerTrait?, String?) -> Unit,
) {
    val context = LocalContext.current
    fun mods(list: List<net.venipa.stashling.core.stash.BodyMod>) = list.joinToString { b -> listOfNotNull(b.location, b.description).joinToString(": ") }
    val groups = listOf(
        listOfNotNull(
            p.aliases.takeIf { it.isNotEmpty() }?.let { Fact(R.string.detail_aliases, it.joinToString()) },
            p.gender?.let { Fact(R.string.detail_gender, it.pretty(), gender = it) },
            p.birthDate?.let { Fact(R.string.detail_born, it, PerformerTrait(TraitKey.BIRTH_YEAR, it.take(4))) },
            p.deathDate?.let { Fact(R.string.detail_died, it) },
            p.ethnicity?.let { Fact(R.string.detail_ethnicity, it.pretty(), PerformerTrait(TraitKey.ETHNICITY, it)) },
        ),
        listOfNotNull(
            p.eyeColor?.let { Fact(R.string.detail_eyes, it.pretty(), PerformerTrait(TraitKey.EYE_COLOR, it)) },
            p.hairColor?.let { Fact(R.string.detail_hair, it.pretty(), PerformerTrait(TraitKey.HAIR_COLOR, it)) },
            measurements(p)?.let { Fact(R.string.detail_measurements, it, p.cupSize?.let { c -> PerformerTrait(TraitKey.CUP_SIZE, c) }) },
            p.breastType?.let { Fact(R.string.detail_breasts, it.pretty(), PerformerTrait(TraitKey.BREAST_TYPE, it)) },
        ),
        listOfNotNull(
            p.tattoos.takeIf { it.isNotEmpty() }?.let { Fact(R.string.detail_tattoos, mods(it), PerformerTrait(TraitKey.TATTOOS, "")) },
            p.piercings.takeIf { it.isNotEmpty() }?.let { Fact(R.string.detail_piercings, mods(it), PerformerTrait(TraitKey.PIERCINGS, "")) },
        ),
    ).filter { it.isNotEmpty() }
    Column(Modifier.padding(top = Space.S), verticalArrangement = Arrangement.spacedBy(Space.L)) {
        groups.forEach { g ->
            TonalGroup {
                g.forEach { f ->
                    val searchable = f.trait != null || f.gender != null
                    GroupRow(f.value, supporting = stringResource(f.label), onClick = if (searchable) ({ onTrait(f.trait, f.gender) }) else null)
                }
            }
        }
        if (p.urls.isNotEmpty()) TonalGroup { p.urls.forEach { l -> GroupRow(l.site, supporting = l.url, onClick = { openLink(context, l.url) }) } }
    }
}

private fun measurements(p: Performer): String? {
    val bust = listOfNotNull(p.bandSize?.toString(), p.cupSize).joinToString("").ifEmpty { null }
    val parts = listOfNotNull(bust, p.waistSize?.toString(), p.hipSize?.toString())
    return parts.takeIf { it.isNotEmpty() }?.joinToString("-")
}
