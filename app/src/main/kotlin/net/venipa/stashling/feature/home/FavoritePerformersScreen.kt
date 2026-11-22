package net.venipa.stashling.feature.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.venipa.stashling.AppContainer
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.stash.Performer
import net.venipa.stashling.core.stash.PerformerFilter
import net.venipa.stashling.core.stash.PerformerRef
import net.venipa.stashling.core.stash.PerformerSort
import net.venipa.stashling.core.stash.SortDirection
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.indexLetter
import net.venipa.stashling.feature.browse.NameRail
import net.venipa.stashling.feature.common.PageLoader
import net.venipa.stashling.feature.common.PagedGrid
import net.venipa.stashling.feature.common.PosterTile
import net.venipa.stashling.feature.common.apiValue
import net.venipa.stashling.feature.common.containerTransform
import net.venipa.stashling.feature.common.performerTransformKey
import net.venipa.stashling.feature.common.rememberPerformerMenu

/** All StashDB favorite performers, name ascending (the A–Z rail seeks server-side), honoring the performer gender setting. */
class FavoritePerformersViewModel(private val c: AppContainer) : ViewModel() {
    private val gender = c.settings.settings.map { it.performerGender.apiValue() }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val loader = PageLoader<Performer>(viewModelScope, { it.id }) { p ->
        c.stash.performers(PerformerFilter(favoritesOnly = true, gender = gender.value, sort = PerformerSort.NAME, direction = SortDirection.ASC), p)
    }
    val grid = LazyGridState()

    init {
        loader.ensureStarted()
        // A changed gender setting reloads; the first (current) value does not.
        viewModelScope.launch { gender.drop(1).collect { loader.reload() } }
    }

    fun refresh() {
        c.stash.invalidate("performers|")
        loader.refresh()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritePerformersScreen(
    onBack: () -> Unit,
    onOpenPerformer: (String) -> Unit,
) {
    val c = LocalAppContainer.current
    val vm: FavoritePerformersViewModel = viewModel { FavoritePerformersViewModel(c) }
    val st by vm.loader.state.collectAsState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                { Text(stringResource(R.string.browse_fav_performers)) },
                navigationIcon = { IconButton(onBack) { Icon(StashlingIcons.Back, stringResource(R.string.detail_back)) } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        val pad = PaddingValues(start = Space.L, top = padding.calculateTopPadding() + Space.S, end = Space.L, bottom = padding.calculateBottomPadding() + Space.L)
        NameRail(vm.loader, st, st.items.isNotEmpty(), pad, vm.grid, { it.id }, { it.name }, { indexLetter(it.name, it.aliases) }) { p ->
            PagedGrid(
                st,
                { it.id },
                GridCells.Adaptive(150.dp),
                p,
                vm.loader::loadMore,
                vm.loader::retry,
                stringResource(R.string.home_favorites_empty),
                gridState = vm.grid,
                onRefresh = vm::refresh,
            ) { _, perf ->
                val image = perf.images.firstOrNull() ?: perf.thumbnail
                val menu = rememberPerformerMenu(PerformerRef(perf.id, perf.name, perf.disambiguation, null, image), RoundedCornerShape(16.dp))
                PosterTile(perf.name, stringResource(R.string.browse_scenes_count, perf.sceneCount), image?.sized(300), 2f / 3f, { onOpenPerformer(perf.id) }, menu.modifier.containerTransform(performerTransformKey(perf.id), RoundedCornerShape(16.dp)), onLongClick = menu.open)
            }
        }
    }
}
