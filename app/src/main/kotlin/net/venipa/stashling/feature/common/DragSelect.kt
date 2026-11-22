package net.venipa.stashling.feature.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** The selection while a drag runs from [anchor] to [current] (either direction) over [keys]: [base] plus that range. Moving back shrinks it again. */
fun dragSelection(
    base: Set<String>,
    keys: List<String>,
    anchor: Int,
    current: Int,
): Set<String> = base + keys.subList(minOf(anchor, current), maxOf(anchor, current) + 1)

/**
 * Google Photos style selection on a grid whose selectable item keys are [keys]: a long-press selects the item under the finger,
 * dragging extends the range across items, near the top/bottom content edge the grid scrolls by itself. While the press is held
 * every event is consumed early (Initial pass), so the grid does not scroll and the tile under the finger never gets a click.
 * [startPadding] = the grid's start content padding (item offsets exclude it).
 */
@Composable
fun Modifier.dragSelect(
    state: LazyGridState,
    keys: List<String>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    startPadding: Dp,
): Modifier {
    val currentKeys by rememberUpdatedState(keys)
    val currentSelected by rememberUpdatedState(selected)
    val change by rememberUpdatedState(onChange)
    val haptics = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val startPx = with(density) { startPadding.roundToPx() }
    val edge = with(density) { 64.dp.toPx() }
    var speed by remember { mutableFloatStateOf(0f) }
    // One drag at a time: anchor index, the selection before it, the last finger position (for re-hit-testing while auto-scrolling).
    val drag = remember { DragState() }

    fun indexAt(pos: Offset): Int? {
        val info = state.layoutInfo
        // Item offsets are start-relative and exclude the content padding.
        val x = (if (rtl) info.viewportSize.width - pos.x else pos.x).toInt() - startPx
        val y = pos.y.toInt() + info.viewportStartOffset
        val item = info.visibleItemsInfo.firstOrNull { IntRect(it.offset, it.size).contains(IntOffset(x, y)) } ?: return null
        return currentKeys.indexOf(item.key).takeIf { it >= 0 }
    }

    fun update(pos: Offset) {
        drag.pos = pos
        val i = indexAt(pos) ?: return
        if (drag.anchor >= 0 && i < currentKeys.size) change(dragSelection(drag.base, currentKeys, drag.anchor, i))
    }

    LaunchedEffect(speed) {
        if (speed != 0f) {
            while (isActive) {
                state.scrollBy(speed)
                update(drag.pos)
                delay(16)
            }
        }
    }

    return pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val long = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            val start = indexAt(long.position) ?: return@awaitEachGesture
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            drag.anchor = start
            drag.base = currentSelected
            update(long.position)
            while (true) {
                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == long.id } ?: break
                c.consume()
                if (!c.pressed) break
                update(c.position)
                val info = state.layoutInfo
                val top = info.beforeContentPadding + edge
                val bottom = size.height - info.afterContentPadding - edge
                speed =
                    when {
                        c.position.y < top -> (c.position.y - top) / 4
                        c.position.y > bottom -> (c.position.y - bottom) / 4
                        else -> 0f
                    }
            }
            speed = 0f
            drag.anchor = -1
        }
    }
}

private class DragState {
    var anchor = -1
    var base: Set<String> = emptySet()
    var pos = Offset.Zero
}
