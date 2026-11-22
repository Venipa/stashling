package net.venipa.stashling.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey

/** What screens use to move around; the shell provides it. */
class Navigator(
    val push: (NavKey) -> Unit,
    val pop: () -> Unit,
)

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }

/** Bottom space the floating dock takes on a tab root (0 on pushed routes); add it to list content padding. */
val LocalDockInset = compositionLocalOf { 0.dp }

fun PaddingValues.withDock(dock: androidx.compose.ui.unit.Dp) = PaddingValues(
    start = 0.dp,
    top = calculateTopPadding(),
    end = 0.dp,
    bottom = calculateBottomPadding() + dock,
)
