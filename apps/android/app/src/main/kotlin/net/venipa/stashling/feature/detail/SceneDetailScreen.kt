package net.venipa.stashling.feature.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.venipa.stashling.AppContainer
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.BookmarkKind
import net.venipa.stashling.core.data.HiddenKind
import net.venipa.stashling.core.stash.Scene
import net.venipa.stashling.core.whisparr.AddMode
import net.venipa.stashling.core.whisparr.PanelState
import net.venipa.stashling.ds.ActionSheet
import net.venipa.stashling.ds.CardTone
import net.venipa.stashling.ds.GlassIconButton
import net.venipa.stashling.ds.GroupRow
import net.venipa.stashling.ds.LocalStashHaptics
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.SheetAction
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.TonalCard
import net.venipa.stashling.ds.TonalGroup
import net.venipa.stashling.ds.concentricShape
import net.venipa.stashling.ds.glassSource
import net.venipa.stashling.ds.readableWidth
import net.venipa.stashling.ds.rememberGlassState
import net.venipa.stashling.feature.common.BrowseIcons
import net.venipa.stashling.feature.common.EntityActions
import net.venipa.stashling.feature.common.Load
import net.venipa.stashling.feature.common.LoadGate
import net.venipa.stashling.feature.common.PortraitAvatar
import net.venipa.stashling.feature.common.PortraitTextInset
import net.venipa.stashling.feature.common.StashImage
import net.venipa.stashling.feature.common.apiValue
import net.venipa.stashling.feature.common.containerTransform
import net.venipa.stashling.feature.common.formatDuration
import net.venipa.stashling.feature.common.loadOf
import net.venipa.stashling.feature.common.openLink
import net.venipa.stashling.feature.common.performerTransformKey
import net.venipa.stashling.feature.common.rememberPerformerMenu
import net.venipa.stashling.feature.common.stashWebUrl
import net.venipa.stashling.feature.detail.whisparr.WhisparrBar
import net.venipa.stashling.feature.detail.whisparr.WhisparrPanelViewModel

class SceneDetailViewModel(private val id: String, private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<Load<Scene>>(Load.Loading)
    val state: StateFlow<Load<Scene>> = _state
    val actions = EntityActions(viewModelScope, c.bookmarks, BookmarkKind.SCENE, MutableStateFlow(id))

    init {
        load()
    }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch { _state.value = loadOf { c.stash.scene(id) } }
    }

    fun toggleBookmark(s: Scene) = actions.toggleBookmark(s.title ?: "", s.studio?.name, s.images.firstOrNull()?.sized(300))
}

/** Scene detail: paging hero with the meta on its scrim, performers, clamped tags/details, expandable links/production rows, floating Whisparr bar. */
@Composable
fun SceneDetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpenPerformer: (String) -> Unit,
    onOpenStudio: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenGallery: (Int) -> Unit,
) {
    val c = LocalAppContainer.current
    val vm = viewModel(key = "scene-$id") { SceneDetailViewModel(id, c) }
    val load by vm.state.collectAsState()
    val bookmarked by vm.actions.bookmarked.collectAsState()
    LoadGate(load, onBack, vm::load) { scene ->
        val haptic = LocalStashHaptics.current
        SceneBody(scene, bookmarked, onBack, {
            haptic.confirm()
            vm.toggleBookmark(scene)
        }, onOpenPerformer, onOpenStudio, onOpenTag, onOpenGallery)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SceneBody(
    scene: Scene,
    bookmarked: Boolean,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
    onOpenPerformer: (String) -> Unit,
    onOpenStudio: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenGallery: (Int) -> Unit,
) {
    val glass = rememberGlassState()
    val context = LocalContext.current
    var actions by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    // 2C: the bar is glass while the hero is behind it, tonal over text.
    var heroBottom by remember { mutableFloatStateOf(0f) }
    var barTop by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var barPx by remember { mutableIntStateOf(0) }
    val pager = rememberPagerState { scene.images.size }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.L)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).onGloballyPositioned { heroBottom = it.boundsInRoot().bottom }) {
                HeroPager(scene.images, 1280, Modifier.fillMaxSize().glassSource(glass), scene.title, pager, onOpenGallery)
                // 1C: title, studio, date and duration on the hero's bottom scrim.
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                        .padding(start = Space.L, end = Space.L, top = Space.Xl, bottom = Space.M),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(scene.title ?: stringResource(R.string.browse_untitled), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        PagerDots(pager, Modifier.padding(start = Space.S, bottom = Space.S))
                    }
                    scene.studio?.let { st ->
                        Breadcrumb(listOfNotNull(st.parent?.let { p -> p.name to { onOpenStudio(p.id) } }, st.name to { onOpenStudio(st.id) }), color = Color.White.copy(alpha = 0.9f))
                    }
                    val meta = listOfNotNull(scene.releaseDate, scene.durationSec?.let(::formatDuration)).joinToString(" · ")
                    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                }
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(Space.S), horizontalArrangement = Arrangement.SpaceBetween) {
                    GlassIconButton(glass, StashlingIcons.Back, stringResource(R.string.detail_back), onBack)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                        GlassIconButton(
                            glass,
                            if (bookmarked) BrowseIcons.BookmarkFilled else BrowseIcons.Bookmark,
                            stringResource(if (bookmarked) R.string.detail_unbookmark else R.string.detail_bookmark),
                            onBookmark,
                        )
                        GlassIconButton(glass, StashlingIcons.More, stringResource(R.string.library_more), { actions = true })
                    }
                }
            }
            if (actions) SceneActionsSheet(scene, bookmarked, onBookmark) { actions = false }
            Column(Modifier.padding(horizontal = Space.L).readableWidth(), verticalArrangement = Arrangement.spacedBy(Space.L)) {
                if (scene.deleted) {
                    TonalCard(tone = CardTone.Error) { Text(stringResource(R.string.detail_deleted)) }
                }
                if (scene.performers.isNotEmpty()) Performers(scene, onOpenPerformer)
                if (scene.tags.isNotEmpty()) {
                    Column {
                        SectionHeader(stringResource(R.string.detail_tags))
                        TagsLine(scene.tags, onOpenTag)
                    }
                }
                scene.details?.takeIf { it.isNotBlank() }?.let { d ->
                    Column {
                        SectionHeader(stringResource(R.string.detail_details))
                        ClampedText(d)
                    }
                }
                // 1C: links and production info as one-line rows that open in place.
                val production = listOfNotNull(
                    scene.code?.let { R.string.detail_code to it },
                    scene.director?.let { R.string.detail_director to it },
                    scene.productionDate?.let { R.string.detail_production_date to it },
                )
                if (scene.urls.isNotEmpty() || production.isNotEmpty()) {
                    TonalGroup {
                        if (scene.urls.isNotEmpty()) {
                            ExpandRow(stringResource(R.string.detail_links), scene.urls.map { it.site }.distinct().joinToString(" · ")) {
                                scene.urls.forEach { l -> GroupRow(l.site, supporting = l.url, onClick = { openLink(context, l.url) }) }
                            }
                        }
                        if (production.isNotEmpty()) {
                            val labels = production.map { (l, _) -> stringResource(l) }
                            ExpandRow(stringResource(R.string.detail_production), labels.joinToString(" · ")) {
                                production.forEach { (label, value) -> GroupRow(value, supporting = stringResource(label)) }
                            }
                        }
                    }
                }
            }
            // Room for the floating Whisparr bar (its footprint includes the nav bar), or the nav bar without it.
            Box(if (barPx > 0) Modifier.height(with(density) { barPx.toDp() }) else Modifier.navigationBarsPadding().height(Space.L))
        }
        WhisparrBar(
            scene.id,
            glass,
            overImage = heroBottom > barTop,
            Modifier.align(Alignment.BottomCenter).onGloballyPositioned { barTop = it.boundsInRoot().top },
            onHeight = { barPx = it },
        )
    }
}

/** Performers portrait row; genders hidden by Settings fold into a "+N" tile. */
@Composable
private fun Performers(
    scene: Scene,
    onOpenPerformer: (String) -> Unit,
) = Column {
    SectionHeader(stringResource(R.string.detail_performers))
    val wanted = LocalAppContainer.current.settings.settings.collectAsState(null).value?.performerGender?.apiValue()
    // Unknown gender counts as matching; no setting (BOTH) hides nothing.
    val (shown, hidden) = scene.performers.partition { wanted == null || it.gender == null || it.gender.equals(wanted, ignoreCase = true) }
    var expanded by remember { mutableStateOf(false) }
    val haptic = LocalStashHaptics.current
    val spec = spring<Float>(Spring.DampingRatioNoBouncy)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Start) {
        // Gap is end padding per tile (not spacedBy) so the collapsed, zero-width reveal adds no gap.
        @Composable
        fun Tile(p: net.venipa.stashling.core.stash.PerformerRef) {
            val menu = rememberPerformerMenu(p, MaterialTheme.shapes.medium)
            PortraitAvatar(p.name, p.thumbnail?.sized(300), { onOpenPerformer(p.id) }, menu.modifier.padding(end = Space.M).containerTransform(performerTransformKey(p.id), MaterialTheme.shapes.medium), supporting = p.asName?.let { stringResource(R.string.detail_as, it) }, onLongClick = menu.open)
        }
        shown.forEach { Tile(it) }
        if (hidden.isNotEmpty()) {
            AnimatedVisibility(
                expanded,
                enter = expandHorizontally(spring(Spring.DampingRatioNoBouncy)) + fadeIn(spec),
                exit = shrinkHorizontally(spring(Spring.DampingRatioNoBouncy)) + fadeOut(spec),
            ) {
                Row { hidden.forEach { Tile(it) } }
            }
            HiddenPerformersTile(hidden.size, hidden.first().thumbnail?.sized(300), expanded) {
                haptic.tick()
                expanded = !expanded
            }
        }
    }
}

/** Same 72x108 footprint as [PortraitAvatar]: blurred preview of the first hidden performer (tonal when none), "+N" on top, label below. */
@Composable
private fun HiddenPerformersTile(
    count: Int,
    imageUrl: String?,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Column(Modifier.width(72.dp).clip(shape).clickable(onClick = onClick).padding(bottom = PortraitTextInset), verticalArrangement = Arrangement.spacedBy(Space.Xs)) {
        Box(Modifier.size(72.dp, 108.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            StashImage(imageUrl, Modifier.fillMaxSize().blur(16.dp), null)
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)))
            Text(if (expanded) "−" else "+$count", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        Text(
            stringResource(if (expanded) R.string.detail_performers_less else R.string.detail_performers_more, count),
            Modifier.padding(horizontal = PortraitTextInset),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Scene overflow: Open on StashDB, Add to Whisparr (while the panel says not in Whisparr; same panel ViewModel), Remove bookmark. */
@Composable
private fun SceneActionsSheet(
    scene: Scene,
    bookmarked: Boolean,
    onRemoveBookmark: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val whisparr by c.whisparr.collectAsState()
    val id = scene.id
    val panel: WhisparrPanelViewModel = viewModel(key = "whisparr-$id") { WhisparrPanelViewModel(id, c) }
    val panelState by panel.state.collectAsState()
    val searchOnAdd by panel.searchOnAdd.collectAsState()
    val hidden by c.hiddenIds.collectAsState()
    val acts = c.sceneActions
    val sceneHidden = id in hidden.scenes
    val one = scene.performers.singleOrNull()
    ActionSheet(
        scene.title,
        listOfNotNull(
            SheetAction(stringResource(R.string.library_open_stashdb), StashlingIcons.OpenExternal) { c.secrets.stash.value?.let { openLink(context, stashWebUrl(it.baseUrl, BookmarkKind.SCENE, id)) } },
            if (whisparr != null && panelState is PanelState.NotInLibrary) SheetAction(stringResource(R.string.library_add_whisparr), StashlingIcons.Download) { panel.add(if (searchOnAdd) AddMode.QUEUE else AddMode.REQUEST) } else null,
            // Hide from here too; the page stays, lists drop the scene (snackbar with Undo from SceneActionsHost).
            if (sceneHidden) {
                SheetAction(stringResource(R.string.swipe_unhide_scene), StashlingIcons.Visibility) { acts.unhide(HiddenKind.SCENE, id) }
            } else {
                SheetAction(stringResource(R.string.swipe_hide_scene), BrowseIcons.VisibilityOff) { acts.hideScene(id, scene.title, scene.images.firstOrNull()?.sized(300)) }
            },
            when {
                one != null && one.id in hidden.performers -> SheetAction(stringResource(R.string.swipe_unhide_performer), StashlingIcons.Visibility) { acts.unhide(HiddenKind.PERFORMER, one.id) }
                one != null -> SheetAction(stringResource(R.string.swipe_hide_performer), BrowseIcons.PersonRemove) { acts.hidePerformerOf(id, scene.performers) }
                scene.performers.size > 1 -> SheetAction(stringResource(R.string.swipe_hide_performer_menu), BrowseIcons.PersonRemove) { acts.hidePerformerOf(id, scene.performers.filterNot { it.id in hidden.performers }) }
                else -> null
            },
            if (bookmarked) SheetAction(stringResource(R.string.library_remove), StashlingIcons.Delete, destructive = true, onClick = onRemoveBookmark) else null,
        ),
        onDismiss,
    )
}
