package net.venipa.stashling.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.venipa.stashling.AppContainer
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.stash.CachingStashDbApi
import net.venipa.stashling.core.stash.Favorites
import net.venipa.stashling.core.stash.Performer
import net.venipa.stashling.core.stash.PerformerFilter
import net.venipa.stashling.core.stash.PerformerRef
import net.venipa.stashling.core.stash.SceneCard
import net.venipa.stashling.core.stash.SceneFilter
import net.venipa.stashling.core.stash.SceneSort
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.AppBottomSheet
import net.venipa.stashling.ds.AppSnackbar
import net.venipa.stashling.ds.DockItemShape
import net.venipa.stashling.ds.DockScrollState
import net.venipa.stashling.ds.GlassCorner
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.SkeletonText
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.ds.concentric
import net.venipa.stashling.ds.concentricShape
import net.venipa.stashling.feature.browse.SheetColumn
import net.venipa.stashling.feature.common.FilterButton
import net.venipa.stashling.feature.common.ListModeSegmented
import net.venipa.stashling.feature.common.PageLoader
import net.venipa.stashling.feature.common.PosterTile
import net.venipa.stashling.feature.common.Region
import net.venipa.stashling.feature.common.SceneSwipeController
import net.venipa.stashling.feature.common.ScenesGrid
import net.venipa.stashling.feature.common.StashImage
import net.venipa.stashling.feature.common.apiValue
import net.venipa.stashling.feature.common.collectVisible
import net.venipa.stashling.feature.common.containerTransform
import net.venipa.stashling.feature.common.fullSpan
import net.venipa.stashling.feature.common.groupShape
import net.venipa.stashling.feature.common.javTagId
import net.venipa.stashling.feature.common.listPadding
import net.venipa.stashling.feature.common.performerTransformKey
import net.venipa.stashling.feature.common.rememberPerformerMenu
import net.venipa.stashling.feature.common.rememberSceneListMode
import net.venipa.stashling.feature.common.tabViewModel
import net.venipa.stashling.feature.common.withRegion
import net.venipa.stashling.nav.LocalDockInset
import net.venipa.stashling.nav.LocalDockTop

enum class HomeTab(val label: Int) {
    Trending(R.string.home_trending),
    New(R.string.home_new),
    Favorites(R.string.home_favorites),
}

class HomeViewModel(c: AppContainer) : ViewModel() {
    private val stash: CachingStashDbApi = c.stash
    private val gender = c.settings.settings.map { it.performerGender.apiValue() }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _selected = MutableStateFlow(HomeTab.Trending)
    val selected: StateFlow<HomeTab> = _selected
    private val _region = MutableStateFlow(Region.All)
    val region: StateFlow<Region> = _region

    /** The "JAV" tag id, resolved once; null = not found, the region control stays hidden. */
    private val _javTag = MutableStateFlow<String?>(null)
    val javTag: StateFlow<String?> = _javTag

    /** Performers favorited on StashDB (avatar row); empty hides it. */
    val favorites = MutableStateFlow<List<Performer>>(emptyList())

    private fun filter(tab: HomeTab, region: Region): SceneFilter = when (tab) {
        HomeTab.Trending -> SceneFilter(sort = SceneSort.TRENDING)
        HomeTab.New -> SceneFilter(sort = SceneSort.DATE)
        HomeTab.Favorites -> SceneFilter(favorites = Favorites.ALL, sort = SceneSort.DATE)
    }.withRegion(region, _javTag.value)

    // One loader and grid state per tab and region, created on first use: switching back keeps pages and scroll.
    private val loaders = mutableMapOf<Pair<HomeTab, Region>, PageLoader<SceneCard>>()
    private val grids = mutableMapOf<Pair<HomeTab, Region>, LazyGridState>()

    fun loader(tab: HomeTab, region: Region) = loaders.getOrPut(tab to region) {
        PageLoader(viewModelScope, { it.id }) { p -> stash.scenes(filter(tab, region), p) }.also { it.ensureStarted() }
    }

    fun gridState(tab: HomeTab, region: Region) = grids.getOrPut(tab to region) { LazyGridState() }

    init {
        viewModelScope.launch { _javTag.value = javTagId(stash) }
        // Reloads the avatar row when the performer gender setting changes.
        viewModelScope.launch { gender.collect { loadFavorites() } }
    }

    private fun loadFavorites() = viewModelScope.launch {
        try {
            favorites.value = stash.performers(PerformerFilter(favoritesOnly = true, gender = gender.value), 1).items
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No avatar row without the list.
        }
    }

    fun select(tab: HomeTab) {
        _selected.value = tab
    }

    fun selectRegion(r: Region) {
        _region.value = r
    }

    fun refresh() {
        stash.invalidate("scenes|")
        stash.invalidate("performers|")
        loadFavorites()
        loader(_selected.value, _region.value).refresh()
    }
}

/** Home: no top bar; favorites avatar row, then one scene grid. Source chips + filter live in the dock ([HomeDockControls]). */
@Composable
fun HomeScreen(
    onOpenScene: (String) -> Unit,
    onOpenPerformer: (String) -> Unit,
    onViewAll: () -> Unit,
) {
    val c = LocalAppContainer.current
    val vm = tabViewModel { HomeViewModel(c) }
    val tab by vm.selected.collectAsState()
    val region by vm.region.collectAsState()
    val javTag by vm.javTag.collectAsState()
    val favorites by vm.favorites.collectAsState()
    // Region only applies once the tag is known.
    val effective = if (javTag == null) Region.All else region
    val loader = vm.loader(tab, effective)
    val state = loader.collectVisible()
    val mode by rememberSceneListMode()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + LocalDockTop.current
    val grid = vm.gridState(tab, effective)
    // The carousel arrives after the first scenes; the grid keeps the first scene anchored, so show the new header when at the top.
    val hasFavorites = favorites.isNotEmpty()
    LaunchedEffect(grid, hasFavorites) {
        if (hasFavorites && grid.firstVisibleItemIndex == 1 && grid.firstVisibleItemScrollOffset == 0) grid.scrollToItem(0)
    }
    ScenesGrid(
        state,
        mode,
        listPadding(PaddingValues(top = top), LocalDockInset.current),
        loader::loadMore,
        loader::retry,
        onOpenScene,
        Modifier.background(MaterialTheme.colorScheme.background),
        gridState = grid,
        header = if (!hasFavorites) null else ({ fullSpan("favorites") { FavoritesRow(favorites, onOpenPerformer, onViewAll) } }),
        onRefresh = vm::refresh,
    )
}

/** Favorites as an uncontained card carousel (every card full size, so the text below the image is never squeezed) + "View all". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesRow(
    performers: List<Performer>,
    onOpen: (String) -> Unit,
    onViewAll: () -> Unit,
) = Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(stringResource(R.string.browse_fav_performers), Modifier.weight(1f))
        TextButton(onViewAll) { Text(stringResource(R.string.home_view_all)) }
    }
    HorizontalUncontainedCarousel(rememberCarouselState { performers.size }, itemWidth = 128.dp, itemSpacing = Space.S) { i ->
        val p = performers[i]
        val image = p.images.firstOrNull() ?: p.thumbnail
        val menu = rememberPerformerMenu(PerformerRef(p.id, p.name, p.disambiguation, null, image), RoundedCornerShape(16.dp))
        // The card's own clip is the 16 dp tile; the carousel mask only narrows the card at the edge while it scrolls out.
        PosterTile(
            p.name,
            stringResource(R.string.browse_scenes_count, p.sceneCount),
            image?.sized(300),
            2f / 3f,
            { onOpen(p.id) },
            menu.modifier.containerTransform(performerTransformKey(p.id), RoundedCornerShape(16.dp)).maskClip(RoundedCornerShape(16.dp)),
            onLongClick = menu.open,
        )
    }
}

/**
 * Home's controls attached to the glass dock: source chips + filter button with badge. Compact only the
 * active source stays, the others shrink away, [ring] (downloads progress) sits before the filter; tapping the source expands again.
 * The filter sheet holds region (2B) and the cards/rows layout.
 */
@Composable
fun HomeDockControls(
    dock: DockScrollState,
    ring: @Composable () -> Unit,
) {
    val c = LocalAppContainer.current
    val vm = tabViewModel { HomeViewModel(c) }
    val tab by vm.selected.collectAsState()
    val region by vm.region.collectAsState()
    val javTag by vm.javTag.collectAsState()
    val collapsed = dock.compact
    var sheet by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        HomeTab.entries.forEach { t ->
            val on = t == tab
            // Weight never reaches 0 (Row rejects it); the hidden chips fade while they shrink.
            val w by animateFloatAsState(if (collapsed && !on) 0.001f else 1f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "homeChip")
            Box(
                Modifier
                    .weight(w.coerceAtLeast(0.001f))
                    .graphicsLayer { alpha = w.coerceIn(0f, 1f) }
                    .heightIn(min = 48.dp)
                    .clip(DockItemShape)
                    .background(if (on) cs.secondaryContainer else cs.secondaryContainer.copy(alpha = 0f))
                    .selectable(on, role = Role.Tab) { if (collapsed) dock.compact = false else vm.select(t) },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(t.label), style = MaterialTheme.typography.labelLarge, color = if (on) cs.onSecondaryContainer else cs.onSurface, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            }
        }
        ring()
        FilterButton(if (javTag != null && region != Region.All) 1 else 0, { sheet = true })
    }
    if (sheet) {
        val mode by rememberSceneListMode()
        AppBottomSheet({ sheet = false }) {
            SheetColumn(stringResource(R.string.browse_filters)) {
                if (javTag != null) {
                    SectionHeader(stringResource(R.string.browse_region))
                    StashSegmented(Region.entries.map { stringResource(it.label) }, region.ordinal, { vm.selectRegion(Region.entries[it]) })
                }
                SectionHeader(stringResource(R.string.browse_layout))
                ListModeSegmented(mode)
            }
        }
    }
}
