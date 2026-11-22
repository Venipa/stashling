package net.venipa.stashling.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import net.venipa.stashling.R
import net.venipa.stashling.ds.StashlingIcons

/** What screens use to move around; the shell provides it. */
class Navigator(
    val push: (NavKey) -> Unit,
    val pop: () -> Unit,
) {
    fun openSettings() = push(SettingsRoute())
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }

/** Bottom space the floating dock takes on a tab root (0 on pushed routes); add it to list content padding. */
val LocalDockInset = compositionLocalOf { 0.dp }

fun PaddingValues.withDock(dock: androidx.compose.ui.unit.Dp) = PaddingValues(
    start = 0.dp,
    top = calculateTopPadding(),
    end = 0.dp,
    bottom = calculateBottomPadding() + dock,
)

/** The Settings entry for a top bar's actions. */
@Composable
fun SettingsAction() {
    val nav = LocalNavigator.current
    IconButton(nav::openSettings) { Icon(StashlingIcons.Settings, stringResource(R.string.nav_settings)) }
}
