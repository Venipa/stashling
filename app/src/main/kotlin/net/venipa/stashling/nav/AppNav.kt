package net.venipa.stashling.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import net.venipa.stashling.ds.GlassDock
import net.venipa.stashling.ds.LocalGlass
import net.venipa.stashling.ds.LocalReduceMotion
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.TransitionSpecs
import net.venipa.stashling.ds.backEnter
import net.venipa.stashling.ds.backExit
import net.venipa.stashling.ds.fadeThroughEnter
import net.venipa.stashling.ds.fadeThroughExit
import net.venipa.stashling.ds.glassSource
import net.venipa.stashling.ds.navBarEnter
import net.venipa.stashling.ds.navBarExit
import net.venipa.stashling.ds.rememberGlassState
import net.venipa.stashling.ds.sharedAxisXEnter
import net.venipa.stashling.ds.sharedAxisXExit
import net.venipa.stashling.feature.browse.BrowseScreen
import net.venipa.stashling.feature.detail.PerformerDetailScreen
import net.venipa.stashling.feature.detail.SceneDetailScreen
import net.venipa.stashling.feature.detail.StudioDetailScreen
import net.venipa.stashling.feature.detail.TagDetailScreen
import net.venipa.stashling.feature.downloads.DownloadsScreen
import net.venipa.stashling.feature.gallery.GalleryScreen
import net.venipa.stashling.feature.home.HomeScreen
import net.venipa.stashling.feature.onboarding.OnboardingScreen
import net.venipa.stashling.feature.saved.SavedScreen
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
    LaunchedEffect(hasDownloads) { if (!hasDownloads && backStack.lastOrNull() == DownloadsRoute) goTab(HomeRoute) }
    LaunchedEffect(deepLinkScene) {
        deepLinkScene?.let {
            goTab(HomeRoute)
            backStack.add(SceneDetailRoute(it))
            onDeepLinkHandled()
        }
    }
    // A tab root other than Home: back goes Home; on Home the system closes the app.
    BackHandler(enabled = backStack.size == 1 && backStack.last() != HomeRoute) { goTab(HomeRoute) }

    val tabKeys = remember(hasDownloads) { listOfNotNull(HomeRoute, BrowseRoute, SavedRoute, DownloadsRoute.takeIf { hasDownloads }) }
    val tabs =
        listOfNotNull(
            DockTab(stringResource(R.string.nav_home), StashlingIcons.Home),
            DockTab(stringResource(R.string.nav_browse), StashlingIcons.Browse),
            DockTab(stringResource(R.string.nav_saved), StashlingIcons.Saved),
            DockTab(stringResource(R.string.nav_downloads), StashlingIcons.Download).takeIf { hasDownloads },
        )
    val glass = rememberGlassState()
    val showDock = backStack.size == 1
    var dockPx by remember { mutableIntStateOf(0) }
    // The target value, not animated: an animated inset would relayout the tab grid every frame of a transition.
    val dockInset = if (showDock) with(LocalDensity.current) { dockPx.toDp() } else 0.dp
    val navigator = remember(backStack) { Navigator(push = { backStack.add(it) }, pop = { backStack.removeLastOrNull() }) }

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
                    entry<HomeRoute> { HomeScreen(onOpenScene = { navigator.push(SceneDetailRoute(it)) }) }
                    entry<BrowseRoute> { BrowseScreen(onOpenScene = { navigator.push(SceneDetailRoute(it)) }, onOpenPerformer = { navigator.push(PerformerDetailRoute(it)) }, onOpenStudio = { navigator.push(StudioDetailRoute(it)) }, onOpenTag = { navigator.push(TagDetailRoute(it)) }) }
                    entry<SavedRoute> { SavedScreen(onOpenScene = { navigator.push(SceneDetailRoute(it)) }, onOpenPerformer = { navigator.push(PerformerDetailRoute(it)) }, onOpenStudio = { navigator.push(StudioDetailRoute(it)) }, onOpenTag = { navigator.push(TagDetailRoute(it)) }) }
                    entry<DownloadsRoute> { DownloadsScreen() }
                    entry<SceneDetailRoute>(metadata = mapOf(PUSHED to true)) { r -> SceneDetailScreen(r.id, onBack = navigator.pop, onOpenPerformer = { navigator.push(PerformerDetailRoute(it)) }, onOpenStudio = { navigator.push(StudioDetailRoute(it)) }, onOpenTag = { navigator.push(TagDetailRoute(it)) }, onOpenGallery = { i -> navigator.push(GalleryRoute("scene", r.id, i)) }) }
                    entry<PerformerDetailRoute>(metadata = mapOf(PUSHED to true)) { r -> PerformerDetailScreen(r.id, navigator.pop, onOpenScene = { navigator.push(SceneDetailRoute(it)) }, onOpenGallery = { i -> navigator.push(GalleryRoute("performer", r.id, i)) }) }
                    entry<StudioDetailRoute>(metadata = mapOf(PUSHED to true)) { r -> StudioDetailScreen(r.id, navigator.pop, onOpenScene = { navigator.push(SceneDetailRoute(it)) }, onOpenStudio = { navigator.push(StudioDetailRoute(it)) }, onOpenGallery = { i -> navigator.push(GalleryRoute("studio", r.id, i)) }) }
                    entry<TagDetailRoute>(metadata = mapOf(PUSHED to true)) { r -> TagDetailScreen(r.id, navigator.pop, onOpenScene = { navigator.push(SceneDetailRoute(it)) }) }
                    entry<GalleryRoute>(metadata = mapOf(PUSHED to true)) { GalleryScreen(it.kind, it.id, it.index, navigator.pop) }
                    // shell
                    entry<SettingsRoute>(metadata = mapOf(PUSHED to true)) { SettingsScreen(it.section, onBack = navigator.pop, onOpen = { s -> navigator.push(SettingsRoute(s)) }, onRerun = { step -> navigator.push(OnboardingRoute(step)) }) }
                    entry<OnboardingRoute>(metadata = mapOf(PUSHED to true)) { OnboardingScreen(only = it.only, onFinished = navigator.pop, onExit = navigator.pop) }
                },
            )
        }
        AnimatedVisibility(
            showDock,
            Modifier.align(Alignment.BottomCenter),
            enter = navBarEnter(reduce),
            exit = navBarExit(reduce),
        ) {
            val selected = tabKeys.indexOf(backStack.first()).coerceAtLeast(0)
            GlassDock(glass, tabs, selected, onSelect = { goTab(tabKeys[it]) }, onHeightChanged = { dockPx = it })
        }
    }
}
