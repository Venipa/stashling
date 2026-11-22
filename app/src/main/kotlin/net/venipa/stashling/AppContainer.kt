package net.venipa.stashling

import android.content.Context
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual DI: one instance per process, created by [StashlingApp]. */
@Suppress("UNUSED_PARAMETER")
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

val LocalAppContainer: ProvidableCompositionLocal<AppContainer> =
    staticCompositionLocalOf { error("AppContainer not provided") }
