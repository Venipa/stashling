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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.stash.CachingStashDbApi
import net.venipa.stashling.core.stash.Favorites
import net.venipa.stashling.core.stash.Performer
import net.venipa.stashling.core.stash.PerformerFilter
import net.venipa.stashling.core.stash.SceneCard
import net.venipa.stashling.core.stash.SceneFilter
import net.venipa.stashling.core.stash.SceneSort
import net.venipa.stashling.ds.AppBottomSheet
import net.venipa.stashling.ds.GlassCorner
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.ds.concentricShape
import net.venipa.stashling.feature.browse.SheetColumn
import net.venipa.stashling.feature.common.FilterButton
import net.venipa.stashling.feature.common.ListModeSegmented
import net.venipa.stashling.feature.common.PageLoader
import net.venipa.stashling.feature.common.Region
import net.venipa.stashling.feature.common.ScenesGrid
import net.venipa.stashling.feature.common.StashImage
import net.venipa.stashling.feature.common.fullSpan
import net.venipa.stashling.feature.common.javTagId
import net.venipa.stashling.feature.common.listPadding
import net.venipa.stashling.feature.common.rememberSceneListMode
import net.venipa.stashling.feature.common.tabViewModel
import net.venipa.stashling.feature.common.withRegion
import net.venipa.stashling.nav.LocalDockInset

enum class HomeTab(val label: Int) {
    Trending(R.string.home_trending),
    New(R.string.home_new),
    Favorites(R.string.home_favorites),
}

class HomeViewModel(private val stash: CachingStashDbApi) : ViewModel() {
    private val _selected = MutableStateFlow(HomeTab.Trending)
    val selected: StateFlow<HomeTab> = _selected
    private val _region = MutableStateFlow(Region.All)
    val region: StateFlow<Region> = _region

    /** The "JAV" tag id, resolved once; null = not found, the region control stays hidden. */
    private val _javTag = MutableStateFlow<String?>(null)
    val javTag: StateFlow<String?> = _javTag

    /** Performers favorited on StashDB (avatar row); empty hides it. */
    val favorites = MutableStateFlow<List<Performer>>(emptyList())

    /** The dock controls shrink to the active source + filter while scrolling down. */
    val collapsed = MutableStateFlow(false)

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
        loadFavorites()
    }

    private fun loadFavorites() = viewModelScope.launch {
        try {
            favorites.value = stash.performers(PerformerFilter(favoritesOnly = true), 1).items
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
) {
    val c = LocalAppContainer.current
    val vm = tabViewModel { HomeViewModel(c.stash) }
    val tab by vm.selected.collectAsState()
    val region by vm.region.collectAsState()
    val javTag by vm.javTag.collectAsState()
    val favorites by vm.favorites.collectAsState()
    // Region only applies once the tag is known.
    val effective = if (javTag == null) Region.All else region
    val loader = vm.loader(tab, effective)
    val state by loader.state.collectAsState()
    val mode by rememberSceneListMode()
    val scroll = remember(vm) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y < -4f) {
                    vm.collapsed.value = true
                } else if (consumed.y > 4f) {
                    vm.collapsed.value = false
                }
                return Offset.Zero
            }
        }
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    ScenesGrid(
        state,
        mode,
        listPadding(PaddingValues(top = top), LocalDockInset.current),
        loader::loadMore,
        loader::retry,
        onOpenScene,
        Modifier.background(MaterialTheme.colorScheme.background).nestedScroll(scroll),
        gridState = vm.gridState(tab, effective),
        header = if (favorites.isEmpty()) null else ({ fullSpan("favorites") { FavoritesRow(favorites, onOpenPerformer) } }),
        onRefresh = vm::refresh,
    )
}

@Composable
private fun FavoritesRow(
    performers: List<Performer>,
    onOpen: (String) -> Unit,
) = Column {
    SectionHeader(stringResource(R.string.browse_fav_performers))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
        items(performers, key = { it.id }) { p ->
            Column(
                Modifier.width(76.dp).clip(MaterialTheme.shapes.medium).clickable { onOpen(p.id) }.padding(vertical = Space.Xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.Xs),
            ) {
                StashImage((p.images.firstOrNull() ?: p.thumbnail)?.sized(300), Modifier.size(64.dp).clip(CircleShape), p.name)
                Text(p.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// Rows sit 8dp inside the 28dp dock: concentric 20dp, same as the tab pills.
private val DockItemShape = concentricShape(GlassCorner, 8.dp)

/**
 * Home's controls attached to the glass dock: source chips + filter button with badge. Collapsed only the active
 * source stays, the others shrink away; tapping it expands again. The filter sheet holds region (2B) and the cards/rows layout.
 */
@Composable
fun HomeDockControls() {
    val c = LocalAppContainer.current
    val vm = tabViewModel { HomeViewModel(c.stash) }
    val tab by vm.selected.collectAsState()
    val region by vm.region.collectAsState()
    val javTag by vm.javTag.collectAsState()
    val collapsed by vm.collapsed.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        HomeTab.entries.forEach { t ->
            val on = t == tab
            // Weight never reaches 0 (Row rejects it); the hidden chips fade while they shrink.
            val w by animateFloatAsState(if (collapsed && !on) 0.001f else 1f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "homeChip")
            Box(
                Modifier
                    .weight(w.coerceAtLeast(0.001f))
                    .graphicsLayer { alpha = w.coerceIn(0f, 1f) }
                    .heightIn(min = 48.dp)
                    .clip(DockItemShape)
                    .background(if (on) cs.secondaryContainer else cs.secondaryContainer.copy(alpha = 0f))
                    .selectable(on, role = Role.Tab) { if (collapsed) vm.collapsed.value = false else vm.select(t) },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(t.label), style = MaterialTheme.typography.labelLarge, color = if (on) cs.onSecondaryContainer else cs.onSurface, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            }
        }
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
