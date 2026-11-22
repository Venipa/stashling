package net.venipa.stashling.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.ds.ContextMenuHost
import net.venipa.stashling.ds.ContextMenuState
import net.venipa.stashling.ds.DockScrollState
import net.venipa.stashling.ds.DockTab
import net.venipa.stashling.ds.GlassCorner
import net.venipa.stashling.ds.GlassDock
import net.venipa.stashling.ds.GlassRole
import net.venipa.stashling.ds.LocalContextMenu
import net.venipa.stashling.ds.LocalGlass
import net.venipa.stashling.ds.LocalReduceMotion
import net.venipa.stashling.ds.LocalStashHaptics
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
import net.venipa.stashling.feature.browse.BrowseDockControls
import net.venipa.stashling.feature.browse.BrowseScreen
import net.venipa.stashling.feature.common.LocalSharedScope
import net.venipa.stashling.feature.common.SceneActionsHost
import net.venipa.stashling.feature.common.SwipeMessage
import net.venipa.stashling.feature.common.containerTransform
import net.venipa.stashling.feature.common.performerTransformKey
import net.venipa.stashling.feature.common.sceneTransformKey
import net.venipa.stashling.feature.common.tabViewModel
import net.venipa.stashling.feature.detail.PerformerDetailScreen
import net.venipa.stashling.feature.detail.SceneDetailScreen
import net.venipa.stashling.feature.detail.StudioDetailScreen
import net.venipa.stashling.feature.detail.TagDetailScreen
import net.venipa.stashling.feature.downloads.DownloadsViewModel
import net.venipa.stashling.feature.gallery.GalleryScreen
import net.venipa.stashling.feature.home.FavoritePerformersScreen
import net.venipa.stashling.feature.home.HomeDockControls
import net.venipa.stashling.feature.home.HomeScreen
import net.venipa.stashling.feature.library.LibraryDockControls
import net.venipa.stashling.feature.library.LibraryScreen
import net.venipa.stashling.feature.onboarding.OnboardingScreen
import net.venipa.stashling.feature.settings.SettingsScreen
import net.venipa.stashling.feature.update.UpdatePill
import net.venipa.stashling.feature.update.UpdateSheets
import net.venipa.stashling.feature.update.showsUpdatePill

private const val PUSHED = "pushed"
private const val TRANSFORM = "transform"

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
    // On Home the first back only arms exit ("Press back again to exit", 2 s); armed, the handler is off, so the second
    // back reaches the system with its predictive back-to-home animation. Any navigation disarms.
    var exitArmed by remember { mutableStateOf(false) }
    val onHomeRoot = backStack.size == 1 && backStack.last() == HomeRoute
    LaunchedEffect(backStack.toList()) { exitArmed = false }
    LaunchedEffect(exitArmed) {
        if (exitArmed) {
            kotlinx.coroutines.delay(2000)
            exitArmed = false
        }
    }
    BackHandler(enabled = onHomeRoot && !exitArmed) {
        exitArmed = true
        container.sceneActions.messages.tryEmit(SwipeMessage(R.string.back_again_exit, brief = true))
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
    val haptic = LocalStashHaptics.current
    // From 600dp a tonal rail on the start edge and the context row as a glass pill at the top of the content.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val dockScroll = remember { DockScrollState() }
    LaunchedEffect(backStack.toList()) { dockScroll.compact = false }
    val compact = dockScroll.compact && !wide
    var dockPx by remember { mutableIntStateOf(0) }
    var pillPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // GlassDock reports its target height (not each animation frame), so grids relayout once per dock state.
    val dockInset = if (showDock && !wide) with(density) { dockPx.toDp() } else 0.dp
    val navigator = remember(backStack) { Navigator(push = { backStack.add(it) }, pop = { backStack.removeLastOrNull() }, tab = { goTab(it) }) }
    // The one Whisparr queue poller + the live (SignalR) connection (activity-scoped Downloads ViewModel): runs while the app is visible, feeds Downloads and the pill.
    val downloads = tabViewModel { DownloadsViewModel(container) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Disconnected: refreshWhisparrIndex clears the tile badges.
    LaunchedEffect(hasDownloads, lifecycle) {
        if (hasDownloads) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { container.runWhisparrLive() }
                downloads.poll()
            }
        } else {
            container.refreshWhisparrIndex()
        }
    }
    val sections by downloads.sections.collectAsState()

    // In-app updater: resume continues an install that waited for Android's permission; an automatic check at most every 6 h.
    val updater = container.updater
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            updater.onResumed()
            val s = container.settings.settings.first()
            if (s.updateAuto && System.currentTimeMillis() - (s.updateLastCheck ?: 0L) > 6 * 3_600_000L) updater.check(manual = false)
        }
    }
    val updateState by updater.state.collectAsState()
    val updateHidden by updater.hiddenForSession.collectAsState()
    val showUpdate = showDock && showsUpdatePill(updateState, updateHidden)
    var updatePx by remember { mutableIntStateOf(0) }
    // The pill floats 8dp above the dock (wide: 16dp above the nav bar); lists and snackbars keep clear of it.
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pillBottom = if (wide) navBar + 16.dp else dockInset + 8.dp
    val contentInset = if (showUpdate) pillBottom + with(density) { updatePx.toDp() } else dockInset

    val root = backStack.first()
    val selected = tabKeys.indexOf(root).coerceAtLeast(0)
    val active = if (hasDownloads) sections.downloading.size else 0
    val downloadingLabel = stringResource(R.string.library_downloading_pill, active, (sections.progress * 100).toInt())
    val openDownloads = {
        downloads.librarySegment.value = 1
        goTab(LibraryRoute)
    }
    // Compact, the downloading row folds into a progress ring on the tab's context line.
    val ring: @Composable () -> Unit = { if (active > 0 && compact) DownloadRing(sections.progress.toFloat(), downloadingLabel, openDownloads) }
    val controls: (@Composable () -> Unit)? =
        when (root) {
            HomeRoute -> ({ HomeDockControls(dockScroll, ring) })
            BrowseRoute -> ({ BrowseDockControls(dockScroll, ring, { navigator.push(PerformerDetailRoute(it)) }, { navigator.push(StudioDetailRoute(it)) }, { navigator.push(TagDetailRoute(it)) }) })
            LibraryRoute -> ({ LibraryDockControls(dockScroll, ring) })
            else -> null
        }
    // Every tab's context row and the downloading row (always on top) are attached to the dock glass.
    val top: (@Composable ColumnScope.() -> Unit)? =
        if (active > 0 || controls != null) {
            {
                if (active > 0 && (!compact || controls == null)) DownloadingRow(downloadingLabel, openDownloads)
                controls?.invoke()
            }
        } else {
            null
        }

    // One long-press menu host over everything (blur covers the dock too).
    val contextMenu = remember { ContextMenuState() }
    CompositionLocalProvider(LocalContextMenu provides contextMenu) {
        ContextMenuHost(contextMenu) {
            UpdateSheets(updater)
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if (wide) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        tabs.forEachIndexed { i, t ->
                            NavigationRailItem(i == selected, {
                                if (i != selected) haptic.tick()
                                goTab(tabKeys[i])
                            }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val dockTop = if (wide && showDock && top != null) with(density) { pillPx.toDp() } + 16.dp else 0.dp
                    CompositionLocalProvider(LocalNavigator provides navigator, LocalDockInset provides contentInset, LocalDockTop provides dockTop, LocalGlass provides glass) {
                        SharedTransitionLayout(Modifier.fillMaxSize().glassSource(glass)) {
                            CompositionLocalProvider(LocalSharedScope provides this) {
                                NavDisplay(
                                    backStack = backStack,
                                    modifier = Modifier.fillMaxSize().then(if (wide) Modifier else Modifier.nestedScroll(dockScroll.connection)),
                                    onBack = { backStack.removeLastOrNull() },
                                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                                    sharedTransitionScope = this,
                                    transitionSpec = {
                                        when {
                                            // Container transform: the tile grows into the page (shared bounds); the grid stays underneath.
                                            !reduce && targetState.isTransform() -> EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished

                                            initialState.isPushed() || targetState.isPushed() -> sharedAxisXEnter(offsetPx, reduce) togetherWith sharedAxisXExit(offsetPx, reduce)

                                            else -> fadeThroughEnter(reduce) togetherWith fadeThroughExit()
                                        }
                                    },
                                    popTransitionSpec = { popSpec(reduce, ltr) },
                                    // The shared bounds follow the gesture: the page shrinks back into its tile.
                                    predictivePopTransitionSpec = { popSpec(reduce, ltr) },
                                    entryProvider =
                                    entryProvider {
                                        entry<HomeRoute> { HomeScreen(onOpenScene = { navigator.push(SceneDetailRoute(it)) }, onOpenPerformer = { navigator.push(PerformerDetailRoute(it)) }, onViewAll = { navigator.push(FavoritePerformersRoute) }) }
                                        entry<FavoritePerformersRoute>(metadata = mapOf(PUSHED to true)) { FavoritePerformersScreen(navigator.pop, onOpenPerformer = { navigator.push(PerformerDetailRoute(it)) }) }
                                        entry<BrowseRoute> { BrowseScreen(onOpenScene = { navigator.push(SceneDetailRoute(it)) }, onOpenPerformer = { navigator.push(PerformerDetailRoute(it)) }, onOpenStudio = { navigator.push(StudioDetailRoute(it)) }, onOpenTag = { navigator.push(TagDetailRoute(it)) }) }
                                        entry<LibraryRoute> { LibraryScreen(hasDownloads, onOpenScene = { navigator.push(SceneDetailRoute(it)) }, onOpenPerformer = { navigator.push(PerformerDetailRoute(it)) }, onOpenStudio = { navigator.push(StudioDetailRoute(it)) }, onOpenTag = { navigator.push(TagDetailRoute(it)) }) }
                                        entry<SceneDetailRoute>(metadata = TRANSFORMED) { r ->
                                            Transformed(sceneTransformKey(r.id)) { SceneDetailScreen(r.id, onBack = navigator.pop, onOpenPerformer = { navigator.push(PerformerDetailRoute(it)) }, onOpenStudio = { navigator.push(StudioDetailRoute(it)) }, onOpenTag = { navigator.push(TagDetailRoute(it)) }, onOpenGallery = { i -> navigator.push(GalleryRoute("scene", r.id, i)) }) }
                                        }
                                        entry<PerformerDetailRoute>(metadata = TRANSFORMED) { r ->
                                            Transformed(performerTransformKey(r.id)) { PerformerDetailScreen(r.id, navigator.pop, onOpenScene = { navigator.push(SceneDetailRoute(it)) }, onOpenGallery = { i -> navigator.push(GalleryRoute("performer", r.id, i)) }) }
                                        }
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
                        }
                    }
                    // Content melts into a progressive blur under the status bar on the bar-less tabs (tonal fallback in Glass).
                    androidx.compose.animation.AnimatedVisibility(showDock && root != SettingsTabRoute, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
                        Box(Modifier.fillMaxWidth().height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp).glass(glass, GlassRole.EdgeTop))
                    }
                    if (wide) {
                        androidx.compose.animation.AnimatedVisibility(showDock && top != null, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
                            Column(
                                Modifier
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 8.dp)
                                    .widthIn(max = 640.dp)
                                    .glass(glass, GlassRole.Dock)
                                    .onSizeChanged { pillPx = it.height }
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) { top?.invoke(this) }
                        }
                    } else {
                        androidx.compose.animation.AnimatedVisibility(showDock, Modifier.align(Alignment.BottomCenter), enter = navBarEnter(reduce), exit = navBarExit(reduce)) {
                            GlassDock(glass, tabs, selected, onSelect = { goTab(tabKeys[it]) }, compact = compact, onHeightChanged = { dockPx = it }, top = top)
                        }
                    }
                    // The update pill floats above the dock (detached from it), on every tab root.
                    androidx.compose.animation.AnimatedVisibility(
                        showUpdate,
                        Modifier.align(Alignment.BottomCenter).padding(bottom = pillBottom),
                        enter = if (reduce) fadeIn() else fadeIn() + slideInVertically { it / 2 },
                        exit = if (reduce) fadeOut() else fadeOut() + slideOutVertically { it / 2 },
                    ) { UpdatePill(updater, glass, Modifier.onSizeChanged { updatePx = it.height }) }
                    // Scene action messages (swipe / detail menus) above the dock, or above the nav bar on pushed routes.
                    SceneActionsHost(Modifier.align(Alignment.BottomCenter).then(if (contentInset > 0.dp) Modifier.padding(bottom = contentInset) else Modifier.navigationBarsPadding()))
                }
            }
        }
    }
}

private val TRANSFORMED = mapOf(PUSHED to true, TRANSFORM to true)

private fun Scene<NavKey>.isTransform() = entries.lastOrNull()?.metadata?.containsKey(TRANSFORM) == true

private fun AnimatedContentTransitionScope<Scene<NavKey>>.popSpec(
    reduce: Boolean,
    ltr: Boolean,
): ContentTransform = if (!reduce && initialState.isTransform()) EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished else backEnter(ltr, reduce) togetherWith backExit(ltr, reduce)

/** The detail page side of the container transform: an opaque page whose bounds are shared with the tapped tile. */
@Composable
private fun Transformed(
    key: String,
    content: @Composable () -> Unit,
) = Box(Modifier.fillMaxSize().containerTransform(key, RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.background)) { content() }

@Composable
private fun DownloadRing(
    progress: Float,
    label: String,
    onClick: () -> Unit,
) = Box(
    Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
) { CircularProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, Modifier.size(28.dp)) }

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
