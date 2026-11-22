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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
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

/** Rail letter of an item with aliases (JAV): the name's if it starts with a Latin letter, else the first such alias's, else '#'. */
fun indexLetter(
    name: String,
    aliases: List<String>,
): Char = sequenceOf(name).plus(aliases).map(::indexLetter).firstOrNull { it != '#' } ?: '#'

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

/** Touch width of the rail (≥ 40dp): the extra reaches over the content-side gap, letters stay where they are. */
private val RailTouchWidth = 40.dp

/** Measures [extra] wider than the slot and hangs the excess off the start side (mirrored in RTL), so layout doesn't change. */
private fun Modifier.widenTouchStart(extra: Dp): Modifier = layout { m, c ->
    val e = extra.roundToPx()
    val p = m.measure(c.copy(minWidth = c.maxWidth + e, maxWidth = c.maxWidth + e))
    layout(c.maxWidth, p.height) { p.placeRelative(-e, 0) }
}

/**
 * Vertical letter index on the end edge (start in RTL): tap or drag to jump. Letters missing from [positions] are dimmed and
 * skipped (the next present letter wins); a bubble shows the letter while the finger is down, one light tick per letter.
 * [onJump] gets the item index, e.g. `listState::scrollToItem` or `gridState::scrollToItem`.
 * With [seek] (server-side jump, big lists) every letter is live and [seek] gets the letter instead; the bubble stays with a loading
 * indicator until it returns. [current] highlights the letter of the first visible item.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlphabetRail(
    positions: Map<Char, Int>,
    onJump: suspend (Int) -> Unit,
    modifier: Modifier = Modifier,
    seek: (suspend (Char) -> Unit)? = null,
    current: Char? = null,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalStashHaptics.current
    val present by rememberUpdatedState(positions)
    val jump by rememberUpdatedState(onJump)
    val seekBy by rememberUpdatedState(seek)
    var active by remember { mutableStateOf<Char?>(null) }
    var seeking by remember { mutableStateOf<Char?>(null) }
    var seekJob by remember { mutableStateOf<Job?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(1) }
    val cs = MaterialTheme.colorScheme

    fun pick(y: Float) {
        fingerY = y.coerceIn(0f, height.toFloat())
        val i = (fingerY / height * INDEX_LETTERS.length).toInt().coerceIn(0, INDEX_LETTERS.length - 1)
        val s = seekBy
        val letter = if (s != null) INDEX_LETTERS[i] else INDEX_LETTERS.drop(i).firstOrNull { it in present } ?: INDEX_LETTERS.take(i).lastOrNull { it in present } ?: return
        if (letter != active) {
            active = letter
            haptic.scrub()
            if (s != null) {
                // Dragging over letters: only the last one's seek runs to the end.
                seekJob?.cancel()
                seeking = letter
                seekJob = scope.launch {
                    try {
                        s(letter)
                    } finally {
                        if (seeking == letter) seeking = null
                    }
                }
            } else {
                scope.launch { jump(present.getValue(letter)) }
            }
        }
    }

    val railLabel = stringResource(R.string.ds_alphabet_index)
    val jumpLabel = stringResource(R.string.ds_jump_to_letter)
    Box(modifier.width(AlphabetRailWidth).fillMaxHeight()) {
        Column(
            Modifier
                .fillMaxSize()
                .widenTouchStart(RailTouchWidth - AlphabetRailWidth)
                .semantics {
                    contentDescription = railLabel
                    // TalkBack can't drag the rail: one action per live letter.
                    customActions = INDEX_LETTERS.filter { seekBy != null || it in present }.map { l ->
                        CustomAccessibilityAction(jumpLabel.format(l.toString())) {
                            val s = seekBy
                            scope.launch { if (s != null) s(l) else present[l]?.let { jump(it) } }
                            true
                        }
                    }
                }
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
                }.padding(start = RailTouchWidth - AlphabetRailWidth),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            INDEX_LETTERS.forEach { l ->
                val on = seek != null || l in positions
                Text(
                    l.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (l == current) FontWeight.Bold else null,
                    color = when {
                        l == current -> cs.onSurface
                        on -> cs.primary
                        else -> cs.onSurfaceVariant.copy(alpha = 0.38f)
                    },
                )
            }
        }
        (active ?: seeking)?.let { l ->
            // offset (not absoluteOffset) mirrors in RTL, so the bubble always sits on the content side of the rail.
            Box(
                Modifier
                    .offset { IntOffset(-64.dp.roundToPx(), (fingerY - 28.dp.toPx()).roundToInt()) }
                    .requiredSize(56.dp)
                    .background(cs.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (seeking == l) LoadingIndicator(Modifier.requiredSize(48.dp), color = cs.onPrimaryContainer.copy(alpha = 0.5f))
                Text(l.toString(), style = MaterialTheme.typography.headlineSmall, color = cs.onPrimaryContainer)
            }
        }
    }
}
