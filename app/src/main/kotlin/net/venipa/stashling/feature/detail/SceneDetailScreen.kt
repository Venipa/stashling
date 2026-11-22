package net.venipa.stashling.feature.detail

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.platform.LocalContext
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
import net.venipa.stashling.feature.common.StashImage
import net.venipa.stashling.feature.common.formatDuration
import net.venipa.stashling.feature.common.loadOf
import net.venipa.stashling.feature.common.openLink
import net.venipa.stashling.feature.common.rememberPerformerMenu
import net.venipa.stashling.feature.common.stashWebUrl
import net.venipa.stashling.feature.detail.whisparr.WhisparrPanel
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

/** Scene detail: hero with glass buttons, Whisparr card, facts, performers, tags, details, links, image strip. */
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
        SceneBody(scene, bookmarked, onBack, { vm.toggleBookmark(scene) }, onOpenPerformer, onOpenStudio, onOpenTag, onOpenGallery)
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Space.L)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            StashImage(
                scene.images.firstOrNull()?.sized(1280),
                Modifier.fillMaxSize().glassSource(glass).then(if (scene.images.isEmpty()) Modifier else Modifier.clickable { onOpenGallery(0) }),
                scene.title,
            )
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
            WhisparrPanel(scene.id)
            Column(verticalArrangement = Arrangement.spacedBy(Space.S)) {
                Text(scene.title ?: stringResource(R.string.browse_untitled), style = MaterialTheme.typography.headlineSmall)
                scene.studio?.let { s ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                        SuggestionChip({ onOpenStudio(s.id) }, { Text(s.name) })
                        s.parent?.let { p -> SuggestionChip({ onOpenStudio(p.id) }, { Text(p.name) }) }
                    }
                }
            }
            val facts = listOfNotNull(
                scene.releaseDate?.let { R.string.detail_release_date to it },
                scene.durationSec?.let { R.string.detail_duration to formatDuration(it) },
                scene.code?.let { R.string.detail_code to it },
                scene.director?.let { R.string.detail_director to it },
                scene.productionDate?.let { R.string.detail_production_date to it },
            )
            if (facts.isNotEmpty()) {
                TonalGroup {
                    facts.forEach { (label, value) -> GroupRow(value, supporting = stringResource(label)) }
                }
            }
            if (scene.performers.isNotEmpty()) {
                Column {
                    SectionHeader(stringResource(R.string.detail_performers))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.M)) {
                        scene.performers.forEach { p ->
                            val menu = rememberPerformerMenu(p, MaterialTheme.shapes.medium)
                            PortraitAvatar(p.name, p.thumbnail?.sized(300), { onOpenPerformer(p.id) }, menu.modifier, supporting = p.asName?.let { stringResource(R.string.detail_as, it) }, onLongClick = menu.open)
                        }
                    }
                }
            }
            if (scene.tags.isNotEmpty()) {
                Column {
                    SectionHeader(stringResource(R.string.detail_tags))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                        scene.tags.forEach { t -> SuggestionChip({ onOpenTag(t.id) }, { Text(t.name) }) }
                    }
                }
            }
            scene.details?.takeIf { it.isNotBlank() }?.let { d ->
                Column {
                    SectionHeader(stringResource(R.string.detail_details))
                    TonalCard { Text(d, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (scene.urls.isNotEmpty()) {
                Column {
                    SectionHeader(stringResource(R.string.detail_links))
                    TonalGroup {
                        scene.urls.forEach { l -> GroupRow(l.site, supporting = l.url, onClick = { openLink(context, l.url) }) }
                    }
                }
            }
        }
        if (scene.images.size > 1) {
            Column {
                SectionHeader(stringResource(R.string.detail_images), Modifier.padding(horizontal = Space.L))
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.L), horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                    items(scene.images.size, key = { scene.images[it].id }) { i ->
                        StashImage(scene.images[i].sized(300), Modifier.size(width = 160.dp, height = 90.dp).clip(concentricShape(16.dp, 0.dp)).clickable { onOpenGallery(i) })
                    }
                }
            }
        }
        Box(Modifier.size(Space.L))
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
