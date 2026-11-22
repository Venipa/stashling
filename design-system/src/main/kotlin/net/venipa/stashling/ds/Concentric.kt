package net.venipa.stashling.ds

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Inner radius for a shape nested [inset] inside a shape of radius [outer]; never negative. */
fun concentric(
    outer: Dp,
    inset: Dp,
): Dp = (outer - inset).coerceAtLeast(0.dp)

fun concentricShape(
    outer: Dp,
    inset: Dp,
) = RoundedCornerShape(concentric(outer, inset))
