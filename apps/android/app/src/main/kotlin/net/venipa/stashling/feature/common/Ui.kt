package net.venipa.stashling.feature.common

import android.content.Context
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.flow.filter
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.BookmarkKind
import net.venipa.stashling.core.data.ListMode
import net.venipa.stashling.core.stash.SceneCard
import net.venipa.stashling.core.stash.StashError
import net.venipa.stashling.core.whisparr.LibraryBadge
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.EmptyState
import net.venipa.stashling.ds.LoadingPill
import net.venipa.stashling.ds.LoadingSkeleton
import net.venipa.stashling.ds.PagingFooter
import net.venipa.stashling.ds.RefreshBox
import net.venipa.stashling.ds.SkeletonBox
import net.venipa.stashling.ds.SkeletonText
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.concentric
import net.venipa.stashling.ds.concentricShape
import net.venipa.stashling.ds.shimmer
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
    val Filter by lazy { icon("Filter", "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z") }
    val History by lazy { icon("History", "M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z") }
    val VisibilityOff by lazy { icon("VisibilityOff", "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z") }
    val PersonRemove by lazy { icon("PersonRemove", "M14 8c0-2.21-1.79-4-4-4S6 5.79 6 8s1.79 4 4 4 4-1.79 4-4zm3 2v2h6v-2h-6zM2 18v2h16v-2c0-2.66-5.33-4-8-4s-8 1.34-8 4z") }
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

/** Settings.blurImages, provided in MainActivity. */
val LocalBlurImages = staticCompositionLocalOf { false }

/** Blur (Android 12+) or hide (older: Modifier.blur is a no-op there) StashDB images when the privacy flag is on. */
@Composable
fun Modifier.privacyBlur(): Modifier = if (!LocalBlurImages.current) {
    this
} else if (Build.VERSION.SDK_INT >= 31) {
    blur(24.dp)
} else {
    alpha(0f)
}

/** Every URL passes through [net.venipa.stashling.core.stash.Image.sized] before it gets here. Shimmer while Coil loads, then a crossfade. */
@Composable
fun StashImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    scale: ContentScale = ContentScale.Crop,
) {
    var loading by remember(url) { mutableStateOf(url != null) }
    val context = LocalContext.current
    val request = remember(url) { ImageRequest.Builder(context).data(url).crossfade(true).build() }
    Box(modifier.then(if (loading) Modifier.shimmer() else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))) {
        if (url != null) {
            AsyncImage(request, contentDescription, Modifier.fillMaxSize().privacyBlur(), contentScale = scale, onState = { loading = it is AsyncImagePainter.State.Loading || it is AsyncImagePainter.State.Empty })
        }
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

/** Skeleton of [PosterTile]: the same card, concentric image block, two text lines. */
@Composable
fun PosterSkeleton(aspect: Float) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(CardOuter)).background(MaterialTheme.colorScheme.surfaceContainer).padding(CardPad),
    verticalArrangement = Arrangement.spacedBy(Space.S),
) {
    SkeletonBox(Modifier.fillMaxWidth().aspectRatio(aspect), concentricShape(CardOuter, CardPad))
    SkeletonText(Modifier.padding(horizontal = Space.S).padding(bottom = Space.S), 2)
}

/** Skeleton of [SceneCardTile] (16:9 card). */
@Composable
fun SceneCardSkeleton() = PosterSkeleton(16f / 9f)

/** Skeleton of [SceneRow]: the row's grouped corners and per-corner concentric thumb. */
@Composable
fun SceneRowSkeleton(index: Int, count: Int) = Row(
    Modifier.fillMaxWidth().clip(groupShape(index, count)).background(MaterialTheme.colorScheme.surfaceContainer).padding(CardPad),
    horizontalArrangement = Arrangement.spacedBy(Space.M),
    verticalAlignment = Alignment.CenterVertically,
) {
    val thumb = RoundedCornerShape(
        topStart = concentric(if (index == 0) CardOuter else 4.dp, CardPad),
        topEnd = concentric(CardOuter, CardPad),
        bottomEnd = concentric(CardOuter, CardPad),
        bottomStart = concentric(if (index == count - 1) CardOuter else 4.dp, CardPad),
    )
    SkeletonBox(Modifier.width(112.dp).aspectRatio(16f / 9f), thumb)
    SkeletonText(Modifier.weight(1f), 3)
}

/** Detail screen skeleton: hero image block (16:9) and text lines. */
@Composable
fun DetailSkeleton() = Column(Modifier.fillMaxSize().padding(Space.L), verticalArrangement = Arrangement.spacedBy(Space.L)) {
    Spacer(Modifier.statusBarsPadding().height(48.dp))
    SkeletonBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(28.dp))
    SkeletonBox(Modifier.fillMaxWidth(0.7f).height(24.dp), CircleShape)
    SkeletonText(lines = 4)
}

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
    badge: LibraryBadge? = null,
) = Tile(onClick, modifier, onLongClick) {
    val shape = concentricShape(CardOuter, CardPad)
    if (imageUrl != null) {
        BadgedImage(badge) { StashImage(imageUrl, Modifier.fillMaxWidth().aspectRatio(aspect).clip(shape), scale = if (fit) ContentScale.Fit else ContentScale.Crop) }
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
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    badge: LibraryBadge? = null,
    onClick: () -> Unit,
) = Tile(onClick, modifier, onLongClick) {
    BadgedImage(badge) { StashImage(scene.cover?.sized(600), Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(concentricShape(CardOuter, CardPad))) }
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
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    badge: LibraryBadge? = null,
    onClick: () -> Unit,
) = MediaRow(scene.title ?: stringResource(R.string.browse_untitled), scene.meta(), scene.cover?.sized(300), index, count, onClick, modifier, onLongClick = onLongClick, badge = badge)

/** [SceneRow] from plain values (bookmarks store a sized url already). */
@Composable
fun MediaRow(
    title: String,
    meta: String?,
    imageUrl: String?,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scale: ContentScale = ContentScale.Crop,
    onLongClick: (() -> Unit)? = null,
    badge: LibraryBadge? = null,
) = Row(
    modifier
        .fillMaxWidth()
        .clip(groupShape(index, count))
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .combinedClickable(onLongClick = onLongClick, onClick = onClick)
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
    StashImage(imageUrl, Modifier.width(112.dp).aspectRatio(16f / 9f).clip(thumb), scale = scale)
    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (meta != null || badge != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.Xs), verticalAlignment = Alignment.CenterVertically) {
                meta?.let { Text(it, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                badge?.let { BadgeIcon(it, Modifier.size(16.dp)) }
            }
        }
    }
}

private fun LibraryBadge.icon() = when (this) {
    LibraryBadge.DOWNLOADED -> StashlingIcons.CheckCircle
    LibraryBadge.DOWNLOADING -> StashlingIcons.Download
    LibraryBadge.IN_LIBRARY -> BrowseIcons.Bookmark
}

private fun LibraryBadge.label() = when (this) {
    LibraryBadge.DOWNLOADED -> R.string.badge_downloaded
    LibraryBadge.DOWNLOADING -> R.string.badge_downloading
    LibraryBadge.IN_LIBRARY -> R.string.badge_in_library
}

/** Whisparr state of a scene: downloaded = primary check, downloading = download, wanted = bookmark outline. */
@Composable
private fun BadgeIcon(
    badge: LibraryBadge,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val tint = when (badge) {
        LibraryBadge.DOWNLOADED -> cs.primary
        LibraryBadge.DOWNLOADING -> cs.onSurface
        LibraryBadge.IN_LIBRARY -> cs.onSurfaceVariant
    }
    Icon(badge.icon(), stringResource(badge.label()), modifier, tint = tint)
}

// The badge's top-end corner is concentric to the card image's corner (8dp) at the 6dp inset; the other corners are round.
private val BadgeInset = 6.dp
private val BadgeShape = RoundedCornerShape(topStart = 13.dp, topEnd = concentric(concentric(CardOuter, CardPad), BadgeInset), bottomEnd = 13.dp, bottomStart = 13.dp)

/** [image] with the Whisparr badge as a tonal pill on its top-end corner; nothing extra when [badge] is null. */
@Composable
private fun BadgedImage(
    badge: LibraryBadge?,
    image: @Composable () -> Unit,
) = Box {
    image()
    if (badge != null) {
        Box(Modifier.align(Alignment.TopEnd).padding(BadgeInset).clip(BadgeShape).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)).padding(4.dp)) {
            BadgeIcon(badge, Modifier.size(18.dp))
        }
    }
}

/**
 * A performer as a small 2:3 portrait tile with the name below (no circles); standalone, so shapes.medium. The tile's clip
 * (ripple, lifted menu copy) has the same shape, so the text is inset by [PortraitTextInset] to stay clear of its bottom corners.
 */
@Composable
fun PortraitAvatar(
    name: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onLongClick: (() -> Unit)? = null,
) = Column(
    modifier.width(72.dp).clip(MaterialTheme.shapes.medium).combinedClickable(onLongClick = onLongClick, onClick = onClick).padding(bottom = PortraitTextInset),
    verticalArrangement = Arrangement.spacedBy(Space.Xs),
) {
    StashImage(imageUrl, Modifier.size(72.dp, 108.dp).clip(MaterialTheme.shapes.medium), name)
    Column(Modifier.padding(horizontal = PortraitTextInset)) {
        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        supporting?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** A 12 dp corner cuts at most 12·(1 − 1/√2) ≈ 3.5 dp into a text box's corner; 4 dp from both edges keeps it out. */
val PortraitTextInset = Space.Xs

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

/**
 * Paged grid: stable keys, next page near the end, [PagingFooter] at the end, ErrorState (retry or sign-in) when the first page fails.
 * First load with no data: [skeleton] tiles in the same grid plus the loading pill. [onRefresh] adds pull to refresh over the existing items.
 */
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
    onRefresh: (() -> Unit)? = null,
    /** false: only [header] (a detail page's other tab); paging pauses. */
    showItems: Boolean = true,
    skeleton: @Composable (index: Int, count: Int) -> Unit = { _, _ -> PosterSkeleton(2f / 3f) },
    item: @Composable (index: Int, item: T) -> Unit,
) {
    LoadMoreEffect(gridState, showItems && state.hasNext && !state.loading && state.error == null, onMore)
    val firstLoad = showItems && state.items.isEmpty() && state.error == null && (state.loading || !state.started)
    val grid: @Composable () -> Unit = {
        LazyVerticalGrid(
            columns,
            Modifier.fillMaxSize(),
            gridState,
            contentPadding,
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            header?.invoke(this)
            if (!showItems) return@LazyVerticalGrid
            if (state.loadingPrevious) fullSpan("previous") { PagingFooter(true, false, onRetry) }
            if (firstLoad) items(SKELETON_COUNT, key = { "skeleton$it" }) { skeleton(it, SKELETON_COUNT) }
            itemsIndexed(state.items, key = { _, it -> key(it) }) { i, it -> item(i, it) }
            if (state.items.isEmpty() && !state.loading && state.error == null && state.started) {
                fullSpan("empty") { EmptyState(StashlingIcons.Search, emptyText) }
            }
            if (state.items.isNotEmpty() && (state.loading || state.error != null)) {
                fullSpan("footer") { PagingFooter(state.loading, state.error != null, onRetry) }
            }
            if (state.items.isEmpty()) state.error?.let { e -> fullSpan("error") { ErrorState(e, onRetry) } }
        }
    }
    Box(modifier.fillMaxSize()) {
        if (onRefresh != null) RefreshBox(state.refreshing, onRefresh, Modifier.fillMaxSize(), indicatorTop = contentPadding.calculateTopPadding()) { grid() } else grid()
        LoadingPill(firstLoad, Modifier.align(Alignment.Center))
    }
}

private const val SKELETON_COUNT = 8

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
    onRefresh: (() -> Unit)? = null,
    showItems: Boolean = true,
) {
    // One collector per grid for the Whisparr tile badges (stashId -> state); read per tile, so a change recomposes only its tiles.
    val badges = LocalAppContainer.current.whisparrIndex.collectAsState()
    ScenesPagedGrid(state, mode, contentPadding, onMore, onRetry, onOpen, modifier, gridState, header, onRefresh, badges, showItems)
}

@Composable
private fun ScenesPagedGrid(
    state: PagedState<SceneCard>,
    mode: ListMode,
    contentPadding: PaddingValues,
    onMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier,
    gridState: LazyGridState,
    header: (LazyGridScope.() -> Unit)?,
    onRefresh: (() -> Unit)?,
    badges: State<Map<String, LibraryBadge>>,
    showItems: Boolean,
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
    onRefresh = onRefresh,
    showItems = showItems,
    skeleton = { i, n -> if (mode == ListMode.CARDS) SceneCardSkeleton() else SceneRowSkeleton(i, n) },
) { i, s ->
    val shape = if (mode == ListMode.CARDS) RoundedCornerShape(CardOuter) else groupShape(i, state.items.size)
    // Long-press menu and swipe actions on every scene list (Library uses its own rows, so neither there).
    val menu = rememberSceneMenu(s, shape)
    val badge by remember(s.id, badges) { derivedStateOf { badges.value[s.id] } }
    val tile: @Composable () -> Unit = {
        val m = menu.modifier.containerTransform(sceneTransformKey(s.id), shape)
        if (mode == ListMode.CARDS) SceneCardTile(s, m, menu.open, badge) { onOpen(s.id) } else SceneRow(s, i, state.items.size, m, menu.open, badge) { onOpen(s.id) }
    }
    SwipeableScene(s, shape, tile)
}

/** Filter button with the count of non-default filters as a badge (Home dock, Browse search row). */
@Composable
fun FilterButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = BadgedBox(badge = { if (count > 0) Badge { Text("$count") } }, modifier) {
    FilledTonalIconButton(onClick, Modifier.size(48.dp)) { Icon(BrowseIcons.Filter, stringResource(R.string.browse_filters)) }
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
    Load.Loading -> Box(Modifier.fillMaxSize()) {
        LoadingSkeleton(Modifier.fillMaxSize()) { DetailSkeleton() }
        BackOverlay(onBack)
    }

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

/** StashDB web page of an entity: `<baseUrl>/scenes|performers|studios|tags/<id>`. */
fun stashWebUrl(
    baseUrl: String,
    kind: BookmarkKind,
    id: String,
): String {
    val path =
        when (kind) {
            BookmarkKind.SCENE -> "scenes"
            BookmarkKind.PERFORMER -> "performers"
            BookmarkKind.STUDIO -> "studios"
            BookmarkKind.TAG -> "tags"
        }
    return "${baseUrl.trimEnd('/')}/$path/$id"
}
