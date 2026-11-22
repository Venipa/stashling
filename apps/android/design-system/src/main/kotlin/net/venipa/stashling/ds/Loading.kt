package net.venipa.stashling.ds

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Loading: skeleton of the real layout + a delayed glass pill, list-end footer, pull to refresh.

/** A moving highlight over the tonal base; one infinite transition, read only in the draw phase. Reduce motion: the static base. */
@Composable
fun Modifier.shimmer(): Modifier {
    val cs = MaterialTheme.colorScheme
    val base = cs.surfaceContainerHigh
    val high = cs.surfaceContainerHighest
    if (LocalReduceMotion.current) return background(base)
    val phase by rememberInfiniteTransition("shimmer").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), "phase")
    return background(base).drawWithCache {
        val band = size.width * 0.6f
        onDrawBehind {
            // Sweeps start to end, so RTL mirrors it.
            val x = -band + phase * (size.width + band)
            val x0 = if (layoutDirection == LayoutDirection.Rtl) size.width - x - band else x
            drawRect(Brush.linearGradient(listOf(base, high, base), Offset(x0, 0f), Offset(x0 + band, 0f)))
        }
    }
}

/** A skeleton block: pass the concentric shape of the real element. */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(CardRadius),
) = Box(modifier.clip(shape).shimmer())

/** [lines] text lines (the last one shorter). */
@Composable
fun SkeletonText(
    modifier: Modifier = Modifier,
    lines: Int = 2,
) = Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.S)) {
    repeat(lines) { i -> SkeletonBox(Modifier.fillMaxWidth(if (i == lines - 1 && lines > 1) 0.6f else 1f).height(12.dp), CircleShape) }
}

/** Glass pill (tonal when glass is absent) with an expressive indicator; fades in only after ~400 ms so quick loads show no flash. [onClick] makes it a button. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingPill(
    visible: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
    onClick: (() -> Unit)? = null,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(400)
            shown = true
        } else {
            shown = false
        }
    }
    AnimatedVisibility(shown, modifier, enter = fadeIn(stateSpec()), exit = fadeOut(stateSpec())) {
        val glass = LocalGlass.current
        val name = label ?: stringResource(R.string.ds_loading)
        Row(
            Modifier
                .then(if (glass != null) Modifier.glass(glass, GlassRole.Button) else Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).blockPointerPassThrough())
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .semantics { contentDescription = name }
                .padding(Space.S),
            horizontalArrangement = Arrangement.spacedBy(Space.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContainedLoadingIndicator(Modifier.size(40.dp))
            if (label != null) Text(label, Modifier.padding(end = Space.M), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Pick 1C: the skeleton of the real layout with the pill over it. */
@Composable
fun LoadingSkeleton(
    modifier: Modifier = Modifier,
    label: String? = null,
    skeleton: @Composable () -> Unit,
) = Box(modifier) {
    skeleton()
    LoadingPill(true, Modifier.align(Alignment.Center), label)
}

/** Pick 3A: a long operation inline; elapsed mm:ss ticks, Cancel stops it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LongOperationCard(
    title: String,
    startedAt: Long,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val sec = ((now - startedAt) / 1000).coerceAtLeast(0)
    TonalCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.M), verticalAlignment = Alignment.CenterVertically) {
            ContainedLoadingIndicator(Modifier.size(48.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text("%d:%02d".format(sec / 60, sec % 60), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onCancel) { Text(stringResource(R.string.ds_cancel)) }
        }
    }
}

/** List end: the indicator while a page loads, a retry line when it failed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PagingFooter(
    loading: Boolean,
    error: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier.fillMaxWidth().padding(Space.L), contentAlignment = Alignment.Center) {
    if (error) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ds_load_more_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onRetry) { Text(stringResource(R.string.ds_retry)) }
        }
    } else if (loading) {
        ContainedLoadingIndicator(Modifier.size(48.dp))
    }
}

/** Pick 4C: pull to refresh with the expressive indicator. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    // The list's top content padding, so the indicator lands below a header drawn over the list.
    indicatorTop: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val haptic = LocalStashHaptics.current
    PullToRefreshBox(
        isRefreshing,
        {
            haptic.confirm()
            onRefresh()
        },
        modifier,
        state,
        indicator = { PullToRefreshDefaults.LoadingIndicator(state, isRefreshing, Modifier.align(Alignment.TopCenter).padding(top = indicatorTop)) },
        content = content,
    )
}
