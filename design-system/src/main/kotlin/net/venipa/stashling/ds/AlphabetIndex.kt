package net.venipa.stashling.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.Normalizer
import kotlin.math.roundToInt

/** Rail order: '#' (digits, symbols, other scripts) first, then A–Z. */
const val INDEX_LETTERS = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ"

/** Index letter of a name: first character with diacritics folded (É → E), uppercase; anything outside A–Z is '#'. */
fun indexLetter(name: String): Char {
    val c = Normalizer.normalize(name.trimStart().take(1), Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: return '#'
    return if (c in 'A'..'Z') c else '#'
}

/** First item position per letter, in list order; [offset] = items placed before [items] in the list (headers). */
fun <T> letterPositions(
    items: List<T>,
    offset: Int = 0,
    name: (T) -> String,
): Map<Char, Int> {
    val m = LinkedHashMap<Char, Int>()
    items.forEachIndexed { i, t -> m.putIfAbsent(indexLetter(name(t)), i + offset) }
    return m
}

/** Width of the rail; lists add it to their end padding so it never covers a card. */
val AlphabetRailWidth = 28.dp

/**
 * Vertical letter index on the end edge (start in RTL): tap or drag to jump. Letters missing from [positions] are dimmed and
 * skipped (the next present letter wins); a bubble shows the letter while the finger is down, one light tick per letter.
 * [onJump] gets the item index, e.g. `listState::scrollToItem` or `gridState::scrollToItem`.
 */
@Composable
fun AlphabetRail(
    positions: Map<Char, Int>,
    onJump: suspend (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(positions)
    val jump by rememberUpdatedState(onJump)
    var active by remember { mutableStateOf<Char?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(1) }
    val cs = MaterialTheme.colorScheme

    fun pick(y: Float) {
        fingerY = y.coerceIn(0f, height.toFloat())
        val i = (fingerY / height * INDEX_LETTERS.length).toInt().coerceIn(0, INDEX_LETTERS.length - 1)
        val letter = INDEX_LETTERS.drop(i).firstOrNull { it in current } ?: INDEX_LETTERS.take(i).lastOrNull { it in current } ?: return
        if (letter != active) {
            active = letter
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            scope.launch { jump(current.getValue(letter)) }
        }
    }

    Box(modifier.width(AlphabetRailWidth).fillMaxHeight()) {
        Column(
            Modifier
                .fillMaxSize()
                .onSizeChanged { height = it.height.coerceAtLeast(1) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        pick(awaitFirstDown().also { it.consume() }.position.y)
                        do {
                            val e = awaitPointerEvent()
                            e.changes.forEach { if (it.pressed) pick(it.position.y) }
                            e.changes.forEach { it.consume() }
                        } while (e.changes.any { it.pressed })
                        active = null
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            INDEX_LETTERS.forEach { l ->
                Text(l.toString(), style = MaterialTheme.typography.labelSmall, color = if (l in positions) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.38f))
            }
        }
        active?.let { l ->
            // offset (not absoluteOffset) mirrors in RTL, so the bubble always sits on the content side of the rail.
            Box(
                Modifier
                    .offset { IntOffset(-64.dp.roundToPx(), (fingerY - 28.dp.toPx()).roundToInt()) }
                    .requiredSize(56.dp)
                    .background(cs.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(l.toString(), style = MaterialTheme.typography.headlineSmall, color = cs.onPrimaryContainer) }
        }
    }
}
