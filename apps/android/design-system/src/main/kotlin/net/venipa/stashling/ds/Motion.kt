package net.venipa.stashling.ds

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Motion tokens copied from medicatore Motion.kt (only what the shell uses). `reduce` = LocalReduceMotion: fades stay, slides and scales go.
val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

object Durations {
    const val SHORT3 = 150
    const val SHORT4 = 200
    const val MEDIUM2 = 300
}

object TransitionSpecs {
    const val FADE_THROUGH_OUT = 90
    const val FADE_THROUGH_IN = 210
    const val FADE_THROUGH_SCALE = 0.92f
    val SHARED_AXIS_OFFSET = 30.dp
    const val BACK_EXIT_SHIFT = 0.25f
    const val BACK_ENTER_SHIFT = 0.125f
    const val BACK_ENTER_ALPHA = 0.3f
    const val STEP_BACK_ALPHA = 0.9f
    const val SHEET_BACK_ALPHA = 0.3f
}

/** Tab changes: exit and enter run together from t = 0. */
fun fadeThroughExit(): ExitTransition = fadeOut(tween(TransitionSpecs.FADE_THROUGH_OUT, easing = StandardAccelerate))

fun fadeThroughEnter(reduce: Boolean): EnterTransition {
    val fade = fadeIn(tween(TransitionSpecs.FADE_THROUGH_IN, easing = StandardDecelerate))
    return if (reduce) fade else fade + scaleIn(tween(TransitionSpecs.FADE_THROUGH_IN, easing = StandardDecelerate), TransitionSpecs.FADE_THROUGH_SCALE)
}

fun <T> sharedAxisSpec() = tween<T>(Durations.MEDIUM2, easing = EmphasizedDecelerate)

/** Push: the new screen starts 30dp to the right while fading in. */
fun sharedAxisXEnter(
    offsetPx: Int,
    reduce: Boolean,
): EnterTransition = if (reduce) fadeIn(sharedAxisSpec()) else slideInHorizontally(sharedAxisSpec()) { offsetPx } + fadeIn(sharedAxisSpec())

/** Push: the old screen leaves 30dp to the left while fading out. */
fun sharedAxisXExit(
    offsetPx: Int,
    reduce: Boolean,
): ExitTransition = if (reduce) fadeOut(sharedAxisSpec()) else slideOutHorizontally(sharedAxisSpec()) { -offsetPx } + fadeOut(sharedAxisSpec())

fun <T> backSpec(easing: Easing = EmphasizedDecelerate) = tween<T>(Durations.MEDIUM2, easing = easing)

/** Back pair, exit: the popped screen leaves by a quarter of its width while it fades. */
fun backExit(
    ltr: Boolean,
    reduce: Boolean,
): ExitTransition {
    val fade = fadeOut(backSpec())
    if (reduce) return fade
    return slideOutHorizontally(backSpec()) { (it * TransitionSpecs.BACK_EXIT_SHIFT).toInt().let { o -> if (ltr) o else -o } } + fade
}

/** Back pair, enter: the screen below comes in from an eighth on the start side. */
fun backEnter(
    ltr: Boolean,
    reduce: Boolean,
): EnterTransition {
    val fade = fadeIn(backSpec(), initialAlpha = TransitionSpecs.BACK_ENTER_ALPHA)
    if (reduce) return fade
    return slideInHorizontally(backSpec()) { (it * TransitionSpecs.BACK_ENTER_SHIFT).toInt().let { o -> if (ltr) -o else o } } + fade
}

fun <T> navBarSpec() = tween<T>(Durations.SHORT4, easing = StandardEasing)

fun navBarEnter(reduce: Boolean): EnterTransition = if (reduce) fadeIn(navBarSpec()) else slideInVertically(navBarSpec()) { it } + fadeIn(navBarSpec())

fun navBarExit(reduce: Boolean): ExitTransition = if (reduce) fadeOut(navBarSpec()) else slideOutVertically(navBarSpec()) { it } + fadeOut(navBarSpec())

/** Generic state change (crossfades, colours): medium2 standard. */
fun <T> stateSpec() = tween<T>(Durations.MEDIUM2, easing = StandardEasing)

/** An in-screen back (onboarding step): [onBack] runs on commit; the progress follows the finger and settles to 0. */
@Composable
fun stepBackProgress(
    enabled: Boolean,
    onBack: () -> Unit,
): () -> Float {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { progress.snapTo(it.progress) }
            onBack()
        } finally {
            scope.launch { progress.animateTo(0f, backSpec()) }
        }
    }
    return { progress.value }
}

/** The outgoing step shifts along the shared axis and dims with the back gesture. */
fun Modifier.stepBackPreview(progress: () -> Float) = graphicsLayer {
    val p = progress()
    translationX = p * TransitionSpecs.SHARED_AXIS_OFFSET.toPx()
    alpha = 1f - p * (1f - TransitionSpecs.STEP_BACK_ALPHA)
}

/** A bottom sheet slides down and dims with the back gesture (no scale). */
fun Modifier.sheetBackPreview(progress: () -> Float) = graphicsLayer {
    val p = progress()
    translationY = size.height * TransitionSpecs.BACK_EXIT_SHIFT * p
    alpha = 1f - p * (1f - TransitionSpecs.SHEET_BACK_ALPHA)
    // No offscreen buffer: the sheet is drawn at its drag offset, outside this layer's bounds.
    compositingStrategy = CompositingStrategy.ModulateAlpha
}
