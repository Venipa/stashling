package net.venipa.stashling.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.Bookmark
import net.venipa.stashling.core.data.BookmarkKind
import net.venipa.stashling.core.stash.PerformerRef
import net.venipa.stashling.core.stash.SceneCard
import net.venipa.stashling.core.whisparr.PanelState
import net.venipa.stashling.ds.ContextMenuItem
import net.venipa.stashling.ds.ContextMenuRequest
import net.venipa.stashling.ds.LocalContextMenu
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.contextMenuAnchor
import net.venipa.stashling.ds.rememberContextMenuAnchor

/** A long-press menu bound to one item: put [modifier] on the item, call [open] from its long-press (null outside a host). */
class ItemMenu(val modifier: Modifier, val open: (() -> Unit)?)

@Composable
private fun rememberItemMenu(
    key: Any,
    shape: Shape,
    beside: Boolean,
    items: @Composable () -> List<ContextMenuItem>,
): ItemMenu {
    val host = LocalContextMenu.current ?: return ItemMenu(Modifier, null)
    val anchor = rememberContextMenuAnchor()
    return remember(key, host, anchor, shape) {
        ItemMenu(Modifier.contextMenuAnchor(anchor)) { host.open(ContextMenuRequest(anchor, shape, beside, items)) }
    }
}

/**
 * Scene card / row menu, above or below the item: Add to Whisparr · preset or Open in Whisparr (by the scene's
 * Whisparr state, fetched when the menu opens), Bookmark, Open on StashDB, Hide performer…, Hide scene (destructive, last).
 */
@Composable
fun rememberSceneMenu(
    scene: SceneCard,
    shape: Shape,
): ItemMenu = rememberItemMenu(scene.id, shape, beside = false) { sceneMenuItems(scene) }

@Composable
private fun sceneMenuItems(scene: SceneCard): List<ContextMenuItem> {
    val c = LocalAppContainer.current
    val ctrl = c.sceneActions
    val context = LocalContext.current
    val repo by c.whisparr.collectAsState()
    val whisparrBase = c.secrets.whisparr.collectAsState().value?.baseUrl
    val states by ctrl.states.collectAsState()
    LaunchedEffect(scene.id, repo) { ctrl.prefetch(scene.id) }
    val state = states[scene.id]?.first
    val bookmarked by remember(scene.id) { c.bookmarks.isBookmarked(BookmarkKind.SCENE, scene.id) }.collectAsState(false)
    val slug = when (state) {
        is PanelState.Missing -> state.scene.titleSlug
        is PanelState.Searching -> state.scene.titleSlug
        is PanelState.InQueue -> state.scene.titleSlug
        is PanelState.Failed -> state.scene.titleSlug
        is PanelState.Downloaded -> state.scene.titleSlug
        else -> null
    }
    val add = addLabelFor(state, presetNameOf(scene))
    return listOfNotNull(
        // One stable first row: Add · preset (also while the state loads) → Search again, or Open in Whisparr when it's there.
        when {
            repo == null -> null
            !add.enabled && slug != null && whisparrBase != null -> ContextMenuItem(stringResource(R.string.whisparr_open), StashlingIcons.OpenExternal) { openLink(context, "$whisparrBase/movie/$slug") }
            else -> ContextMenuItem(stringResource(add.res, add.arg), StashlingIcons.Download) { ctrl.add(scene.id, scene.tagIds) }
        },
        if (bookmarked) {
            ContextMenuItem(stringResource(R.string.detail_unbookmark), BrowseIcons.BookmarkFilled) { c.appScope.launch { c.bookmarks.remove(BookmarkKind.SCENE, scene.id) } }
        } else {
            ContextMenuItem(stringResource(R.string.detail_bookmark), BrowseIcons.Bookmark) {
                c.appScope.launch { c.bookmarks.add(Bookmark(BookmarkKind.SCENE, scene.id, scene.title.orEmpty(), scene.meta(), scene.cover?.sized(300), System.currentTimeMillis())) }
            }
        },
        ContextMenuItem(stringResource(R.string.library_open_stashdb), StashlingIcons.OpenExternal) { c.secrets.stash.value?.let { openLink(context, stashWebUrl(it.baseUrl, BookmarkKind.SCENE, scene.id)) } },
        if (scene.performerIds.isEmpty()) null else ContextMenuItem(stringResource(R.string.swipe_hide_performer_menu), BrowseIcons.PersonRemove, destructive = true) { ctrl.hidePerformerOf(scene) },
        ContextMenuItem(stringResource(R.string.swipe_hide_scene), BrowseIcons.VisibilityOff, destructive = true) { ctrl.hideScene(scene.id, scene.title, scene.cover?.sized(300)) },
    )
}

/**
 * Performer portrait menu, beside the item: Favorite on StashDB / remove, Bookmark, Open on StashDB, Hide performer
 * (destructive, last). Monitor in Whisparr is skipped: the repository has no performer monitoring.
 */
@Composable
fun rememberPerformerMenu(
    performer: PerformerRef,
    shape: Shape,
): ItemMenu = rememberItemMenu(performer.id, shape, beside = true) { performerMenuItems(performer) }

@Composable
private fun performerMenuItems(p: PerformerRef): List<ContextMenuItem> {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    // The cached detail call knows the favorite flag (list calls may not).
    val favorite by produceState<Boolean?>(null, p.id) { value = runCatching { c.stash.performer(p.id)?.isFavorite }.getOrNull() }
    val bookmarked by remember(p.id) { c.bookmarks.isBookmarked(BookmarkKind.PERFORMER, p.id) }.collectAsState(false)
    val image = p.thumbnail?.sized(300)
    return listOfNotNull(
        favorite?.let { on ->
            ContextMenuItem(stringResource(if (on) R.string.detail_unfavorite else R.string.detail_favorite), if (on) BrowseIcons.FavoriteFilled else BrowseIcons.Favorite) {
                c.appScope.launch {
                    try {
                        c.stash.setFavoritePerformer(p.id, !on)
                        c.stash.invalidate("performer|${p.id}")
                        c.stash.invalidate("performers|")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        c.sceneActions.messages.emit(SwipeMessage(R.string.menu_favorite_failed))
                    }
                }
            }
        },
        if (bookmarked) {
            ContextMenuItem(stringResource(R.string.detail_unbookmark), BrowseIcons.BookmarkFilled) { c.appScope.launch { c.bookmarks.remove(BookmarkKind.PERFORMER, p.id) } }
        } else {
            ContextMenuItem(stringResource(R.string.detail_bookmark), BrowseIcons.Bookmark) {
                c.appScope.launch { c.bookmarks.add(Bookmark(BookmarkKind.PERFORMER, p.id, p.name, p.disambiguation, image, System.currentTimeMillis())) }
            }
        },
        ContextMenuItem(stringResource(R.string.library_open_stashdb), StashlingIcons.OpenExternal) { c.secrets.stash.value?.let { openLink(context, stashWebUrl(it.baseUrl, BookmarkKind.PERFORMER, p.id)) } },
        ContextMenuItem(stringResource(R.string.swipe_hide_performer), BrowseIcons.PersonRemove, destructive = true) { c.sceneActions.hidePerformers(listOf(p)) },
    )
}
