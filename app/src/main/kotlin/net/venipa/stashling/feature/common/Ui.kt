package net.venipa.stashling.feature.common

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.filter
import net.venipa.stashling.R
import net.venipa.stashling.core.data.BookmarkKind
import net.venipa.stashling.core.data.ListMode
import net.venipa.stashling.core.stash.SceneCard
import net.venipa.stashling.core.stash.StashError
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.EmptyState
import net.venipa.stashling.ds.Loading
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.concentric
import net.venipa.stashling.ds.concentricShape
import net.venipa.stashling.nav.LocalNavigator
import net.venipa.stashling.nav.OnboardingRoute

// Material Icons (Apache-2.0), 24 dp viewport; the design-system symbol set has no bookmark/heart.
private fun icon(name: String, path: String): ImageVector = ImageVector
    .Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
    .build()

object BrowseIcons {
    val Bookmark by lazy { icon("Bookmark", "M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2zm0 15l-5-2.18L7 18V5h10v13z") }
    val BookmarkFilled by lazy { icon("BookmarkFilled", "M17 3H7c-1.1 0-1.99.9-1.99 2L5 21l7-3 7 3V5c0-1.1-.9-2-2-2z") }
    val Favorite by lazy { icon("Favorite", "M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z") }
    val FavoriteFilled by lazy { icon("FavoriteFilled", "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z") }
}

/** Padding of a grid under a Scaffold top bar: [pad] is the Scaffold padding, [dock] LocalDockInset (0 on pushed routes). */
fun listPadding(pad: PaddingValues, dock: Dp = 0.dp) = PaddingValues(
    start = Space.L,
    end = Space.L,
    top = pad.calculateTopPadding() + Space.S,
    // The dock already clears the nav bar, so only one of the two counts.
    bottom = (if (dock > 0.dp) dock else pad.calculateBottomPadding()) + Space.L,
)

fun formatDuration(sec: Int): String {
    val h = sec / 3600
    val m = sec % 3600 / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun SceneCard.meta(): String = listOfNotNull(studio?.name, releaseDate, durationSec?.let(::formatDuration)).joinToString(" · ")

/** Only http(s) links open, in a Custom Tab. */
fun openLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme == "https" || uri.scheme == "http") runCatching { CustomTabsIntent.Builder().build().launchUrl(context, uri) }
}

/** Container shape of item [index] of [count] in a group: outer radius on the group's ends, 4 dp where items join (RTL-aware start/end). */
fun groupShape(index: Int, count: Int, outer: Dp = 16.dp, inner: Dp = 4.dp): RoundedCornerShape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

/** Every URL passes through [net.venipa.stashling.core.stash.Image.sized] before it gets here. */
@Composable
fun StashImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    scale: ContentScale = ContentScale.Crop,
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (url != null) AsyncImage(url, contentDescription, Modifier.fillMaxSize(), contentScale = scale)
    }
}

private val CardOuter = 16.dp
private val CardPad = 8.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) = Column(
    modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(CardOuter))
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .combinedClickable(onLongClick = onLongClick, onClick = onClick)
        .padding(CardPad),
    verticalArrangement = Arrangement.spacedBy(Space.S),
) { content() }

/** A poster tile (performer/tag 2:3, studio 16:9 contained): image inside the card at the concentric radius, text below. A null [imageUrl] draws a plain panel. */
@Composable
fun PosterTile(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    aspect: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fit: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) = Tile(onClick, modifier, onLongClick) {
    val shape = concentricShape(CardOuter, CardPad)
    if (imageUrl != null) {
        StashImage(imageUrl, Modifier.fillMaxWidth().aspectRatio(aspect).clip(shape), scale = if (fit) ContentScale.Fit else ContentScale.Crop)
    } else {
        Box(Modifier.fillMaxWidth().aspectRatio(aspect).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(Space.M), contentAlignment = Alignment.Center) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
    Column(Modifier.padding(horizontal = Space.S).padding(bottom = Space.Xs)) {
        if (imageUrl != null) Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
fun SceneCardTile(
    scene: SceneCard,
    onClick: () -> Unit,
) = Tile(onClick) {
    StashImage(scene.cover?.sized(600), Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(concentricShape(CardOuter, CardPad)))
    Column(Modifier.padding(horizontal = Space.S).padding(bottom = Space.Xs)) {
        Text(scene.title ?: stringResource(R.string.browse_untitled), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(scene.meta(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Dense row; rows in a list join at 4 dp and round at the list ends ([shape] from [groupShape]). */
@Composable
fun SceneRow(
    scene: SceneCard,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) = Row(
    Modifier
        .fillMaxWidth()
        .clip(groupShape(index, count))
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(onClick = onClick)
        .padding(CardPad),
    horizontalArrangement = Arrangement.spacedBy(Space.M),
    verticalAlignment = Alignment.CenterVertically,
) {
    // Start corners are concentric to the row's own (grouped) corners, per corner.
    val thumb = RoundedCornerShape(
        topStart = concentric(if (index == 0) CardOuter else 4.dp, CardPad),
        topEnd = concentric(CardOuter, CardPad),
        bottomEnd = concentric(CardOuter, CardPad),
        bottomStart = concentric(if (index == count - 1) CardOuter else 4.dp, CardPad),
    )
    StashImage(scene.cover?.sized(300), Modifier.width(112.dp).aspectRatio(16f / 9f).clip(thumb))
    Column(Modifier.weight(1f)) {
        Text(scene.title ?: stringResource(R.string.browse_untitled), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(scene.meta(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Unauthorized shows "Sign in again" (routes to the StashDB onboarding step, nothing is cleared); other errors show Retry. */
@Composable
fun ErrorState(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = LocalNavigator.current
    val unauthorized = error is StashError.Unauthorized
    EmptyState(
        StashlingIcons.Warning,
        stringResource(if (unauthorized) R.string.browse_error_unauthorized else R.string.browse_error_title),
        modifier,
        body = when (error) {
            is StashError.Unauthorized -> null
            is StashError.Network -> stringResource(R.string.browse_error_network)
            is StashError.Http -> stringResource(R.string.browse_error_http, error.status)
            else -> stringResource(R.string.browse_error_generic)
        },
        action = {
            if (unauthorized) {
                ActionButton(stringResource(R.string.browse_sign_in_again), { nav.push(OnboardingRoute(1)) })
            } else {
                ActionButton(stringResource(R.string.browse_retry), onRetry, tonal = true)
            }
        },
    )
}

@Composable
fun CenteredLoading() = Box(Modifier.fillMaxWidth().padding(Space.L), contentAlignment = Alignment.Center) { Loading() }

@Composable
fun LoadMoreEffect(
    state: LazyGridState,
    enabled: Boolean,
    onMore: () -> Unit,
) {
    val more by rememberUpdatedState(onMore)
    LaunchedEffect(state, enabled) {
        if (enabled) {
            snapshotFlow {
                val i = state.layoutInfo
                if ((i.visibleItemsInfo.lastOrNull()?.index ?: 0) >= i.totalItemsCount - 6) i.totalItemsCount else -1
            }.filter { it >= 0 }.collect { more() }
        }
    }
}

/** Paged grid: stable keys, next page near the end, spinner footer, ErrorState (retry or sign-in) when a page fails. */
@Composable
fun <T> PagedGrid(
    state: PagedState<T>,
    key: (T) -> String,
    columns: GridCells,
    contentPadding: PaddingValues,
    onMore: () -> Unit,
    onRetry: () -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    spacing: Dp = Space.M,
    header: (LazyGridScope.() -> Unit)? = null,
    item: @Composable (index: Int, item: T) -> Unit,
) {
    LoadMoreEffect(gridState, state.hasNext && !state.loading && state.error == null, onMore)
    LazyVerticalGrid(
        columns,
        modifier.fillMaxSize(),
        gridState,
        contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        header?.invoke(this)
        itemsIndexed(state.items, key = { _, it -> key(it) }) { i, it -> item(i, it) }
        if (state.items.isEmpty() && !state.loading && state.error == null && state.started) {
            fullSpan("empty") { EmptyState(StashlingIcons.Search, emptyText) }
        }
        if (state.loading) fullSpan("loading") { CenteredLoading() }
        state.error?.let { e -> fullSpan("error") { ErrorState(e, onRetry) } }
    }
}

fun LazyGridScope.fullSpan(
    key: String,
    content: @Composable () -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

/** The scene list of Home, Browse and the detail screens: cards (16:9 thumb on a tonal card) or dense rows, per Settings.sceneListMode. */
@Composable
fun ScenesGrid(
    state: PagedState<SceneCard>,
    mode: ListMode,
    contentPadding: PaddingValues,
    onMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    header: (LazyGridScope.() -> Unit)? = null,
) = PagedGrid(
    state,
    { it.id },
    if (mode == ListMode.CARDS) GridCells.Adaptive(300.dp) else GridCells.Fixed(1),
    contentPadding,
    onMore,
    onRetry,
    stringResource(R.string.browse_empty),
    modifier,
    gridState = gridState,
    spacing = if (mode == ListMode.CARDS) Space.M else 2.dp,
    header = header,
) { i, s ->
    if (mode == ListMode.CARDS) SceneCardTile(s) { onOpen(s.id) } else SceneRow(s, i, state.items.size) { onOpen(s.id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StashTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) = TopAppBar(
    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    navigationIcon = { onBack?.let { b -> IconButton(b) { Icon(StashlingIcons.Back, stringResource(R.string.detail_back)) } } },
    actions = { actions() },
)

@Composable
fun BackOverlay(onBack: () -> Unit) = IconButton(onBack, Modifier.statusBarsPadding().padding(Space.S).size(48.dp)) {
    Icon(StashlingIcons.Back, stringResource(R.string.detail_back))
}

@Composable
fun FullScreenState(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) = Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    BackOverlay(onBack)
}

/** Loading / not found / error (with back) around a detail screen's body. */
@Composable
fun <T> LoadGate(
    load: Load<T>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) = when (load) {
    Load.Loading -> FullScreenState(onBack) { Loading() }
    Load.NotFound -> FullScreenState(onBack) { EmptyState(StashlingIcons.Search, stringResource(R.string.detail_not_found)) }
    is Load.Failed -> FullScreenState(onBack) { ErrorState(load.error, onRetry) }
    is Load.Data -> content(load.value)
}

@StringRes
fun BookmarkKind.labelRes() = when (this) {
    BookmarkKind.SCENE -> R.string.browse_kind_scenes
    BookmarkKind.PERFORMER -> R.string.browse_kind_performers
    BookmarkKind.STUDIO -> R.string.browse_kind_studios
    BookmarkKind.TAG -> R.string.browse_kind_tags
}
