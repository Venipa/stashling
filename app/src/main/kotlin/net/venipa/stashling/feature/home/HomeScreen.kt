package net.venipa.stashling.feature.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import net.venipa.stashling.core.stash.SceneCard
import net.venipa.stashling.core.stash.SceneFilter
import net.venipa.stashling.core.stash.SceneSort
import net.venipa.stashling.core.stash.TagFilter
import net.venipa.stashling.ds.Space
import net.venipa.stashling.feature.common.ListModeToggle
import net.venipa.stashling.feature.common.PageLoader
import net.venipa.stashling.feature.common.ScenesGrid
import net.venipa.stashling.feature.common.StashTopBar
import net.venipa.stashling.feature.common.listPadding
import net.venipa.stashling.feature.common.rememberSceneListMode
import net.venipa.stashling.feature.common.tabViewModel
import net.venipa.stashling.nav.LocalDockInset

enum class HomeTab(val label: Int) {
    Trending(R.string.home_trending),
    New(R.string.home_new),
    Favorites(R.string.home_favorites),
}

/** JAV = scenes tagged JAV, Western = every scene without that tag. */
enum class HomeRegion(val label: Int) {
    All(R.string.home_region_all),
    Jav(R.string.home_region_jav),
    Western(R.string.home_region_western),
}

class HomeViewModel(private val stash: CachingStashDbApi) : ViewModel() {
    private val _selected = MutableStateFlow(HomeTab.Trending)
    val selected: StateFlow<HomeTab> = _selected
    private val _region = MutableStateFlow(HomeRegion.All)
    val region: StateFlow<HomeRegion> = _region

    /** The "JAV" tag id, resolved once; null = not found, the region row stays hidden. */
    private val _javTag = MutableStateFlow<String?>(null)
    val javTag: StateFlow<String?> = _javTag

    private fun filter(tab: HomeTab, region: HomeRegion): SceneFilter {
        val base = when (tab) {
            HomeTab.Trending -> SceneFilter(sort = SceneSort.TRENDING)
            HomeTab.New -> SceneFilter(sort = SceneSort.DATE)
            HomeTab.Favorites -> SceneFilter(favorites = Favorites.ALL, sort = SceneSort.DATE)
        }
        val jav = _javTag.value ?: return base
        return when (region) {
            HomeRegion.All -> base
            HomeRegion.Jav -> base.copy(tagIds = listOf(jav))
            HomeRegion.Western -> base.copy(excludeTagIds = listOf(jav))
        }
    }

    // One loader and grid state per tab and region, created on first use: switching back keeps pages and scroll.
    private val loaders = mutableMapOf<Pair<HomeTab, HomeRegion>, PageLoader<SceneCard>>()
    private val grids = mutableMapOf<Pair<HomeTab, HomeRegion>, LazyGridState>()

    fun loader(tab: HomeTab, region: HomeRegion) = loaders.getOrPut(tab to region) {
        PageLoader(viewModelScope, { it.id }) { p -> stash.scenes(filter(tab, region), p) }.also { it.ensureStarted() }
    }

    fun gridState(tab: HomeTab, region: HomeRegion) = grids.getOrPut(tab to region) { LazyGridState() }

    init {
        viewModelScope.launch {
            try {
                val q = stash.tags(TagFilter(names = "JAV"), 1).items
                _javTag.value = (q.firstOrNull { it.name.equals("JAV", true) } ?: q.firstOrNull { t -> t.aliases.any { it.equals("JAV", true) } })?.id
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No region row without the tag.
            }
        }
    }

    fun select(tab: HomeTab) {
        _selected.value = tab
    }

    fun selectRegion(r: HomeRegion) {
        _region.value = r
    }

    fun refresh() {
        stash.invalidate("scenes|")
        loader(_selected.value, _region.value).refresh()
    }
}

/** Home: chip tabs (Trending / New / Favorites), a region row (All / JAV / Western), one scene grid. */
@Composable
fun HomeScreen(onOpenScene: (String) -> Unit) {
    val c = LocalAppContainer.current
    val vm = tabViewModel { HomeViewModel(c.stash) }
    val tab by vm.selected.collectAsState()
    val region by vm.region.collectAsState()
    val javTag by vm.javTag.collectAsState()
    // Region only applies once the tag is known.
    val effective = if (javTag == null) HomeRegion.All else region
    val loader = vm.loader(tab, effective)
    val state by loader.state.collectAsState()
    val mode by rememberSceneListMode()
    Scaffold(
        topBar = {
            StashTopBar(stringResource(R.string.app_name), actions = { ListModeToggle(mode) })
        },
    ) { pad ->
        Column(Modifier.padding(top = pad.calculateTopPadding())) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.L, vertical = Space.S), horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                HomeTab.entries.forEach { t -> FilterChip(t == tab, { vm.select(t) }, { Text(stringResource(t.label)) }) }
            }
            if (javTag != null) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.L), horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                    HomeRegion.entries.forEach { r -> FilterChip(r == effective, { vm.selectRegion(r) }, { Text(stringResource(r.label)) }) }
                }
            }
            ScenesGrid(
                state,
                mode,
                listPadding(PaddingValues(bottom = pad.calculateBottomPadding()), LocalDockInset.current),
                loader::loadMore,
                loader::retry,
                onOpenScene,
                gridState = vm.gridState(tab, effective),
                onRefresh = vm::refresh,
            )
        }
    }
}
