package net.venipa.stashling.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.ds.DockTab
import net.venipa.stashling.ds.GlassCorner
import net.venipa.stashling.ds.GlassDock
import net.venipa.stashling.ds.GlassRole
import net.venipa.stashling.ds.LocalGlass
import net.venipa.stashling.ds.LocalReduceMotion
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.TransitionSpecs
import net.venipa.stashling.ds.backEnter
import net.venipa.stashling.ds.backExit
import net.venipa.stashling.ds.concentricShape
import net.venipa.stashling.ds.fadeThroughEnter
import net.venipa.stashling.ds.fadeThroughExit
import net.venipa.stashling.ds.glass
import net.venipa.stashling.ds.glassSource
import net.venipa.stashling.ds.navBarEnter
import net.venipa.stashling.ds.navBarExit
import net.venipa.stashling.ds.rememberGlassState
import net.venipa.stashling.ds.sharedAxisXEnter
import net.venipa.stashling.ds.sharedAxisXExit
import net.venipa.stashling.feature.browse.BrowseScreen
import net.venipa.stashling.feature.common.tabViewModel
import net.venipa.stashling.feature.detail.PerformerDetailScreen
import net.venipa.stashling.feature.detail.SceneDetailScreen
import net.venipa.stashling.feature.detail.StudioDetailScreen
import net.venipa.stashling.feature.detail.TagDetailScreen
import net.venipa.stashling.feature.downloads.DownloadsViewModel
import net.venipa.stashling.feature.gallery.GalleryScreen
import net.venipa.stashling.feature.home.HomeDockControls
import net.venipa.stashling.feature.home.HomeScreen
import net.venipa.stashling.feature.library.LibraryScreen
import net.venipa.stashling.feature.onboarding.OnboardingScreen
import net.venipa.stashling.feature.settings.SettingsScreen

private const val PUSHED = "pushed"

private fun Scene<NavKey>.isPushed() = entries.lastOrNull()?.metadata?.containsKey(PUSHED) == true

/**
 * The shell. No StashAccount: onboarding alone. Otherwise tab roots sit alone on the stack (a tab switch replaces it),
 * back on a non-Home tab goes Home, back on Home exits; pushed routes hide the dock and use the back pair.
 * [deepLinkScene] is a StashDB scene id from `stashling://scene/<id>`; [onDeepLinkHandled] clears it.
 */
@Composable
fun AppNav(
    deepLinkScene: String? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val stash by container.secrets.stash.collectAsState()
    val whisparr by container.secrets.whisparr.collectAsState()
    if (stash == null) {
        OnboardingScreen(only = -1, onFinished = {}, onExit = {})
        return
    }
    val hasDownloads = whisparr != null
    val backStack = rememberNavBackStack(HomeRoute)
    val reduce = LocalReduceMotion.current
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val offsetPx = with(LocalDensity.current) { TransitionSpecs.SHARED_AXIS_OFFSET.roundToPx() }

    fun goTab(key: NavKey) {
        if (backStack.size == 1 && backStack.last() == key) return
        backStack.clear()
        backStack.add(key)
    }
    LaunchedEffect(deepLinkScene) {
        deepLinkScene?.let {
            goTab(HomeRoute)
            backStack.add(SceneDetailRoute(it))
            onDeepLinkHandled()
        }
    }
    // A tab root other than Home: back goes Home; on Home the system closes the app.
    BackHandler(enabled = backStack.size == 1 && backStack.last() != HomeRoute) { goTab(HomeRoute) }

    val tabKeys = remember { listOf(HomeRoute, BrowseRoute, LibraryRoute, SettingsTabRoute) }
    val tabs =
        listOf(
            DockTab(stringResource(R.string.nav_home), StashlingIcons.Home),
            DockTab(stringResource(R.string.nav_browse), StashlingIcons.Browse),
            DockTab(stringResource(R.string.nav_library), StashlingIcons.Saved),
            DockTab(stringResource(R.string.nav_settings), StashlingIcons.Settings),
        )
    val glass = rememberGlassState()
    val showDock = backStack.size == 1
    var dockPx by remember { mutableIntStateOf(0) }
    // The target value, not animated: an animated inset would relayout the tab grid every frame of a transition.
    val dockInset = if (showDock) with(LocalDensity.current) { dockPx.toDp() } else 0.dp
    val navigator = remember(backStack) { Navigator(push = { backStack.add(it) }, pop = { backStack.removeLastOrNull() }, tab = { goTab(it) }) }
    // The one Whisparr queue poller (activity-scoped Downloads ViewModel): runs while the app is visible, feeds Downloads and the pill.
    val downloads = tabViewModel { DownloadsViewModel(container) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(hasDownloads, lifecycle) { if (hasDownloads) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { downloads.poll() } }
    val sections by downloads.sections.collectAsState()

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNavigator provides navigator, LocalDockInset provides dockInset, LocalGlass provides glass) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize().glassSource(glass),
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                transitionSpec = {
                    if (initialState.isPushed() || targetState.isPushed()) {
                        sharedAxisXEnter(offsetPx, reduce) togetherWith sharedAxisXExit(offsetPx, reduce)
                    } else {
                        fadeThroughEnter(reduce) togetherWith fadeThroughExit()
                    }
                },
                popTransitionSpec = { backEnter(ltr, reduce) togetherWith backExit(ltr, reduce) },
                predictivePopTransitionSpec = { backEnter(ltr, reduce) togetherWith backExit(ltr, reduce) },
                entryProvider =
                entryProvider {
                    entry<HomeRoute> { HomeScreen(onOpenScene = { navigator.push(SceneDetailRoute(it)) }, onOpenPerformer = { navigator.push(PerformerDetailRoute(it)) }) }
                    entry<BrowseRoute> { BrowseScreen(onOpenScene = { navigator.push(SceneDetailRoute(it)) }, onOpenPerformer = { navigator.push(PerformerDetailRoute(it)) }, onOpenStudio = { navigator.push(StudioDetailRoute(it)) }, onOpenTag = { navigator.push(TagDetailRoute(it)) }) }
                    entry<LibraryRoute> { LibraryScreen(hasDownloads, onOpenScene = { navigator.push(SceneDetailRoute(it)) }, onOpenPerformer = { navigator.push(PerformerDetailRoute(it)) }, onOpenStudio = { navigator.push(StudioDetailRoute(it)) }, onOpenTag = { navigator.push(TagDetailRoute(it)) }) }
                    entry<SceneDetailRoute>(metadata = mapOf(PUSHED to true)) { r -> SceneDetailScreen(r.id, onBack = navigator.pop, onOpenPerformer = { navigator.push(PerformerDetailRoute(it)) }, onOpenStudio = { navigator.push(StudioDetailRoute(it)) }, onOpenTag = { navigator.push(TagDetailRoute(it)) }, onOpenGallery = { i -> navigator.push(GalleryRoute("scene", r.id, i)) }) }
                    entry<PerformerDetailRoute>(metadata = mapOf(PUSHED to true)) { r -> PerformerDetailScreen(r.id, navigator.pop, onOpenScene = { navigator.push(SceneDetailRoute(it)) }, onOpenGallery = { i -> navigator.push(GalleryRoute("performer", r.id, i)) }) }
                    entry<StudioDetailRoute>(metadata = mapOf(PUSHED to true)) { r -> StudioDetailScreen(r.id, navigator.pop, onOpenScene = { navigator.push(SceneDetailRoute(it)) }, onOpenStudio = { navigator.push(StudioDetailRoute(it)) }, onOpenGallery = { i -> navigator.push(GalleryRoute("studio", r.id, i)) }) }
                    entry<TagDetailRoute>(metadata = mapOf(PUSHED to true)) { r -> TagDetailScreen(r.id, navigator.pop, onOpenScene = { navigator.push(SceneDetailRoute(it)) }) }
                    entry<GalleryRoute>(metadata = mapOf(PUSHED to true)) { GalleryScreen(it.kind, it.id, it.index, navigator.pop) }
                    // shell
                    entry<SettingsTabRoute> { SettingsScreen(SettingsSection.Menu, onBack = navigator.pop, tab = true, onOpen = { s -> navigator.push(SettingsRoute(s)) }, onRerun = { step -> navigator.push(OnboardingRoute(step)) }) }
                    entry<SettingsRoute>(metadata = mapOf(PUSHED to true)) { SettingsScreen(it.section, onBack = navigator.pop, onOpen = { s -> navigator.push(SettingsRoute(s)) }, onRerun = { step -> navigator.push(OnboardingRoute(step)) }) }
                    entry<OnboardingRoute>(metadata = mapOf(PUSHED to true)) { OnboardingScreen(only = it.only, onFinished = navigator.pop, onExit = navigator.pop) }
                },
            )
        }
        val root = backStack.first()
        // Content melts into a progressive blur under the status bar on the bar-less tabs (tonal fallback in Glass).
        AnimatedVisibility(showDock && (root == HomeRoute || root == BrowseRoute), Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxWidth().height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp).glass(glass, GlassRole.EdgeTop))
        }
        val active = if (hasDownloads) sections.downloading.size else 0
        val onHome = root == HomeRoute
        AnimatedVisibility(
            showDock,
            Modifier.align(Alignment.BottomCenter),
            enter = navBarEnter(reduce),
            exit = navBarExit(reduce),
        ) {
            val selected = tabKeys.indexOf(backStack.first()).coerceAtLeast(0)
            // The downloading row and Home's controls are attached to the dock glass, not stacked pills.
            val top: (@Composable ColumnScope.() -> Unit)? =
                if (active > 0 || onHome) {
                    {
                        if (active > 0) {
                            DownloadingRow(stringResource(R.string.library_downloading_pill, active, (sections.progress * 100).toInt())) {
                                downloads.librarySegment.value = 1
                                goTab(LibraryRoute)
                            }
                        }
                        if (onHome) HomeDockControls()
                    }
                } else {
                    null
                }
            GlassDock(glass, tabs, selected, onSelect = { goTab(tabKeys[it]) }, onHeightChanged = { dockPx = it }, top = top)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadingRow(
    label: String,
    onClick: () -> Unit,
) = Row(
    Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .clip(concentricShape(GlassCorner, 8.dp))
        .clickable(onClickLabel = stringResource(R.string.library_downloading_open), onClick = onClick)
        .padding(horizontal = Space.S),
    horizontalArrangement = Arrangement.spacedBy(Space.M),
    verticalAlignment = Alignment.CenterVertically,
) {
    ContainedLoadingIndicator(Modifier.size(32.dp))
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    Icon(StashlingIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
