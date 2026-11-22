package net.venipa.stashling.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged

/**
 * A header (top bar, search, chips) that slides away on scroll down and returns on any scroll up (enterAlways).
 * The Scaffold still reserves its full height, so list padding does not change; a surface strip keeps the status bar covered.
 * Put [connection] on the Scaffold (`Modifier.nestedScroll`) and wrap the topBar content in [CollapsingHeader].
 */
@Stable
class CollapsingHeaderState internal constructor() {
    internal var height by mutableFloatStateOf(0f)
    internal var offset by mutableFloatStateOf(0f)

    val connection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val next = (offset + available.y).coerceIn(-height, 0f)
                val used = next - offset
                offset = next
                return Offset(0f, used)
            }
        }

    /** For [FloatingHeader]: moves by exactly what the list scrolled and takes nothing, so header and content travel together. */
    val follow =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                offset = (offset + consumed.y).coerceIn(-height, 0f)
                return Offset.Zero
            }
        }
}

@Composable
fun rememberCollapsingHeader(): CollapsingHeaderState = remember { CollapsingHeaderState() }

/** Tonal surface background (not transparent) that follows the collapse. */
@Composable
fun Modifier.collapsingHeader(state: CollapsingHeaderState): Modifier = onSizeChanged { state.height = it.height.toFloat() }
    .graphicsLayer { translationY = state.offset.coerceAtLeast(-state.height) }
    .background(MaterialTheme.colorScheme.surface)

/** The topBar of a Scaffold that collapses; the status-bar strip stays solid so content never shows under the clock. */
@Composable
fun CollapsingHeader(
    state: CollapsingHeaderState,
    content: @Composable ColumnScope.() -> Unit,
) = Box {
    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.surface))
    Column(Modifier.collapsingHeader(state), content = content)
}

/**
 * A transparent header over the content (under the status-bar edge band). [collapsible] slides up under the status bar
 * and fades on scroll down, [pinned] follows it and always stays (never fully hidden). Put [CollapsingHeaderState.follow] on the
 * scrolling parent; give the list a top padding of this header's measured height.
 */
@Composable
fun FloatingHeader(
    state: CollapsingHeaderState,
    modifier: Modifier = Modifier,
    collapsible: @Composable ColumnScope.() -> Unit,
    pinned: @Composable ColumnScope.() -> Unit,
) = Column(modifier.statusBarsPadding().graphicsLayer { translationY = state.offset }) {
    Column(
        Modifier.onSizeChanged { state.height = it.height.toFloat() }.graphicsLayer { alpha = if (state.height > 0f) 1f + state.offset / state.height else 1f },
        content = collapsible,
    )
    pinned()
}
