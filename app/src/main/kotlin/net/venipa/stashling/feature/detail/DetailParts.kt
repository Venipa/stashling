package net.venipa.stashling.feature.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.venipa.stashling.R
import net.venipa.stashling.core.stash.Image
import net.venipa.stashling.core.stash.TagRef
import net.venipa.stashling.ds.GroupRow
import net.venipa.stashling.ds.LocalStashHaptics
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.feature.common.StashImage

/**
 * The hero pages through [images] (first image = cover, so the container transform lands on it). Tap opens the gallery
 * at that page. Back is a system edge gesture that the pager never receives (no gesture exclusion), so the two do not fight.
 */
@Composable
internal fun HeroPager(
    images: List<Image>,
    width: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    state: PagerState = rememberPagerState { images.size },
    onOpen: (Int) -> Unit,
) {
    if (images.isEmpty()) {
        StashImage(null, modifier, contentDescription)
        return
    }
    HorizontalPager(state, modifier, key = { images[it].id }, beyondViewportPageCount = 1) { i ->
        StashImage(images[i].sized(width), Modifier.fillMaxSize().clickable { onOpen(i) }, contentDescription)
    }
}

/** Page dots for [HeroPager]; nothing for one image. White on the hero scrim. */
@Composable
internal fun PagerDots(
    state: PagerState,
    modifier: Modifier = Modifier,
) {
    val n = state.pageCount
    if (n < 2) return
    val label = stringResource(R.string.detail_image_n, state.currentPage + 1, n)
    // Long galleries: at most 9 dots, the current one clamped into range.
    val shown = n.coerceAtMost(9)
    val current = if (n <= 9) state.currentPage else (state.currentPage * (shown - 1) / (n - 1)).coerceIn(0, shown - 1)
    Row(modifier.semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(shown) { i ->
            Box(Modifier.size(if (i == current) 8.dp else 6.dp).background(Color.White.copy(alpha = if (i == current) 1f else 0.5f), CircleShape))
        }
    }
}

/** Body text clamped to [lines] with a More / Less toggle when it overflows (1C). */
@Composable
internal fun ClampedText(
    text: String,
    modifier: Modifier = Modifier,
    lines: Int = 3,
) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    val haptic = LocalStashHaptics.current
    Column(modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else lines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            TextButton({
                haptic.tick()
                expanded = !expanded
            }) { Text(stringResource(if (expanded) R.string.detail_less else R.string.detail_more)) }
        }
    }
}

/**
 * Tags on one line, then a "+N more" chip that shows all of them (1C). Own layout: FlowRow's overflow indicator throws when its
 * shownItemCount is read during composition. N is found at layout time and fed back through state (one extra frame).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagsLine(
    tags: List<TagRef>,
    onOpenTag: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var hidden by remember(tags) { mutableIntStateOf(0) }
    val haptic = LocalStashHaptics.current
    val chips: @Composable () -> Unit = { tags.forEach { t -> SuggestionChip({ onOpenTag(t.id) }, { Text(t.name) }) } }
    if (expanded) {
        FlowRow(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()), horizontalArrangement = Arrangement.spacedBy(Space.S)) { chips() }
        return
    }
    Layout({
        chips()
        SuggestionChip({
            haptic.tick()
            expanded = true
        }, { Text(stringResource(R.string.detail_tags_more, hidden)) })
    }) { ms, c ->
        val gap = Space.S.roundToPx()
        val free = c.copy(minWidth = 0, minHeight = 0)
        val more = ms.last().measure(free)
        val ps = ms.dropLast(1).map { it.measure(free) }
        var x = 0
        var n = 0
        for (p in ps) {
            // Room for this chip, plus the "+N" chip unless this is the last tag.
            val need = x + p.width + if (n < ps.size - 1) gap + more.width else 0
            if (need > c.maxWidth) break
            x += p.width + gap
            n++
        }
        hidden = ps.size - n
        val shown = ps.take(n) + if (hidden > 0) listOf(more) else emptyList()
        layout(c.maxWidth, shown.maxOfOrNull { it.height } ?: 0) {
            var px = 0
            shown.forEach {
                it.placeRelative(px, 0)
                px += it.width + gap
            }
        }
    }
}

/** A [GroupRow] that opens [content] (more rows of the same group) in place; the arrow turns (1C links / production info). */
@Composable
internal fun ExpandRow(
    title: String,
    supporting: String?,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val haptic = LocalStashHaptics.current
    val turn by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "expandArrow")
    Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        GroupRow(title, supporting = supporting, onClick = {
            haptic.tick()
            expanded = !expanded
        }, trailing = { Icon(StashlingIcons.ArrowDropDown, null, Modifier.rotate(turn.coerceIn(0f, 180f)), tint = MaterialTheme.colorScheme.onSurfaceVariant) })
        if (expanded) content()
    }
}

/** "Parent › Child" with each part tappable (5C, scene hero studio line). [color] for text over the hero scrim. */
@Composable
internal fun Breadcrumb(
    parts: List<Pair<String, (() -> Unit)?>>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) = Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    parts.forEachIndexed { i, (name, onClick) ->
        if (i > 0) Icon(StashlingIcons.ChevronRight, null, Modifier.size(16.dp), tint = color)
        Text(
            name,
            Modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = Space.Xs),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
