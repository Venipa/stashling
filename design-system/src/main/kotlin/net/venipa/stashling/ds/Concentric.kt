package net.venipa.stashling.ds

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Every nested shape is concentric. inner = outer - padding (min 0). Corner params are start/end, so RTL flips by itself.

/** Inner radius for a shape nested [inset] inside a shape of radius [outer]; never negative. */
fun concentric(
    outer: Dp,
    inset: Dp,
): Dp = (outer - inset).coerceAtLeast(0.dp)

fun concentricShape(
    outer: Dp,
    inset: Dp,
) = RoundedCornerShape(concentric(outer, inset))

/** Radii of the design-system containers (M3 Expressive large / extra large). Nest with [concentricShape]. */
val CardRadius = 16.dp
val HeroCardRadius = 28.dp
val GroupRadius = 16.dp
val SheetRadius = 28.dp

/** Shape for an image or button placed [padding] inside a [TonalCard] (hero = the 28dp card). */
fun cardInnerShape(
    padding: Dp = 16.dp,
    hero: Boolean = false,
) = concentricShape(if (hero) HeroCardRadius else CardRadius, padding)

/**
 * Corners of item [index] of [count] in a connected group: the first item takes the container's [outer] radius on its
 * leading side, the last on its trailing side, joins between items are [inner]. [vertical] = a column (top / bottom),
 * otherwise a row (start / end; RoundedCornerShape's start/end already follow the layout direction).
 */
fun groupItemShape(
    index: Int,
    count: Int,
    vertical: Boolean = true,
    outer: Dp = GroupRadius,
    inner: Dp = 4.dp,
): Shape {
    val first = index == 0
    val last = index == count - 1
    val lead = if (first) outer else inner
    val trail = if (last) outer else inner
    return if (vertical) {
        RoundedCornerShape(topStart = lead, topEnd = lead, bottomEnd = trail, bottomStart = trail)
    } else {
        RoundedCornerShape(topStart = lead, bottomStart = lead, topEnd = trail, bottomEnd = trail)
    }
}
