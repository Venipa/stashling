package net.venipa.stashling.ds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** The one haptics entry point (setting "Haptic feedback"): every call is a no-op while [enabled] is false. */
@Immutable
class StashHaptics(private val feedback: HapticFeedback, private val enabled: Boolean) {
    private fun go(t: HapticFeedbackType) {
        if (enabled) feedback.performHapticFeedback(t)
    }

    /** Selection change: tabs, segments, chips, switches, menu row, rail letter. */
    fun tick() = go(HapticFeedbackType.SegmentTick)

    /** Fast scrubbing (A-Z rail). */
    fun scrub() = go(HapticFeedbackType.SegmentFrequentTick)

    /** An action went through: toggles, add/search/grab, menu action, refresh. */
    fun confirm() = go(HapticFeedbackType.Confirm)

    /** A drag crossed a threshold. */
    fun threshold() = go(HapticFeedbackType.GestureThresholdActivate)

    /** Long press recognised: menu open, selection start. */
    fun longPress() = go(HapticFeedbackType.LongPress)
}

val LocalStashHaptics = compositionLocalOf<StashHaptics> { error("LocalStashHaptics not provided") }

@Composable
fun ProvideStashHaptics(enabled: Boolean, content: @Composable () -> Unit) {
    val feedback = LocalHapticFeedback.current
    val haptics = remember(feedback, enabled) { StashHaptics(feedback, enabled) }
    CompositionLocalProvider(LocalStashHaptics provides haptics, content = content)
}

/** Material [androidx.compose.material3.Switch] that ticks on change. */
@Composable
fun StashSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val h = LocalStashHaptics.current
    androidx.compose.material3.Switch(checked, {
        h.tick()
        onCheckedChange(it)
    }, modifier, enabled = enabled)
}

/** Material [androidx.compose.material3.FilterChip] that ticks on click. */
@Composable
fun StashFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier, leadingIcon: (@Composable () -> Unit)? = null) {
    val h = LocalStashHaptics.current
    androidx.compose.material3.FilterChip(selected, {
        h.tick()
        onClick()
    }, label, modifier)
}
