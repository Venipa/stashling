package net.venipa.stashling.feature.common

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import net.venipa.stashling.ds.LocalReduceMotion

/** The shell's SharedTransitionLayout (null outside it, e.g. onboarding). */
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

fun sceneTransformKey(id: String) = "scene:$id"

fun performerTransformKey(id: String) = "performer:$id"

// Critically damped: the bounds never overshoot, so a shrinking page never gets smaller than its tile.
private val NoOvershoot = BoundsTransform { _, _ -> spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, Rect.VisibilityThreshold) }

/**
 * Container transform: the tile and its detail page share bounds under [key] across Nav3 entries, so the tile grows
 * into the page and predictive back shrinks it back. No partner on screen (scrolled away, deep link) = the page just fades;
 * reduced motion = no-op (the shell's fade/shared axis).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.containerTransform(
    key: String,
    shape: Shape,
): Modifier {
    val shared = LocalSharedScope.current ?: return this
    if (LocalReduceMotion.current) return this
    val scope = LocalNavAnimatedContentScope.current
    return with(shared) {
        this@containerTransform.sharedBounds(rememberSharedContentState(key), scope, boundsTransform = NoOvershoot, clipInOverlayDuringTransition = OverlayClip(shape))
    }
}
