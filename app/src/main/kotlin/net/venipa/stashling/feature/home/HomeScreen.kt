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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.stash.CachingStashDbApi
import net.venipa.stashling.core.stash.Favorites
import net.venipa.stashling.core.stash.SceneCard
import net.venipa.stashling.core.stash.SceneFilter
import net.venipa.stashling.core.stash.SceneSort
import net.venipa.stashling.ds.Space
import net.venipa.stashling.feature.common.ListModeToggle
import net.venipa.stashling.feature.common.PageLoader
import net.venipa.stashling.feature.common.ScenesGrid
import net.venipa.stashling.feature.common.StashTopBar
import net.venipa.stashling.feature.common.listPadding
import net.venipa.stashling.feature.common.rememberSceneListMode
import net.venipa.stashling.feature.common.tabViewModel
import net.venipa.stashling.nav.LocalDockInset
import net.venipa.stashling.nav.SettingsAction

enum class HomeTab(val label: Int) {
    Trending(R.string.home_trending),
    New(R.string.home_new),
    Favorites(R.string.home_favorites),
}

class HomeViewModel(private val stash: CachingStashDbApi) : ViewModel() {
    private val _selected = MutableStateFlow(HomeTab.Trending)
    val selected: StateFlow<HomeTab> = _selected

    private fun filter(tab: HomeTab) = when (tab) {
        HomeTab.Trending -> SceneFilter(sort = SceneSort.TRENDING)
        HomeTab.New -> SceneFilter(sort = SceneSort.DATE)
        HomeTab.Favorites -> SceneFilter(favorites = Favorites.ALL, sort = SceneSort.DATE)
    }

    // One loader per tab: switching back keeps its pages.
    val loaders: Map<HomeTab, PageLoader<SceneCard>> =
        HomeTab.entries.associateWith { tab -> PageLoader(viewModelScope, { it.id }) { p -> stash.scenes(filter(tab), p) } }

    // Scroll positions live here too: the screen is recreated on every tab switch.
    val gridStates = HomeTab.entries.associateWith { LazyGridState() }

    init {
        select(HomeTab.Trending)
    }

    fun refresh() {
        stash.invalidate("scenes|")
        loaders.getValue(_selected.value).refresh()
    }

    fun select(tab: HomeTab) {
        _selected.value = tab
        loaders.getValue(tab).ensureStarted()
    }
}

/** Home: chip tabs (Trending / New / Favorites) over one scene grid. */
@Composable
fun HomeScreen(onOpenScene: (String) -> Unit) {
    val c = LocalAppContainer.current
    val vm = tabViewModel { HomeViewModel(c.stash) }
    val tab by vm.selected.collectAsState()
    val loader = vm.loaders.getValue(tab)
    val state by loader.state.collectAsState()
    val mode by rememberSceneListMode()
    Scaffold(
        topBar = {
            StashTopBar(stringResource(R.string.app_name), actions = {
                ListModeToggle(mode)
                SettingsAction()
            })
        },
    ) { pad ->
        Column(Modifier.padding(top = pad.calculateTopPadding())) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.L, vertical = Space.S), horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                HomeTab.entries.forEach { t -> FilterChip(t == tab, { vm.select(t) }, { Text(stringResource(t.label)) }) }
            }
            ScenesGrid(
                state,
                mode,
                listPadding(PaddingValues(bottom = pad.calculateBottomPadding()), LocalDockInset.current),
                loader::loadMore,
                loader::retry,
                onOpenScene,
                gridState = vm.gridStates.getValue(tab),
                onRefresh = vm::refresh,
            )
        }
    }
}
