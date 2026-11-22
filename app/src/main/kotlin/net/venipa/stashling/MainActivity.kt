package net.venipa.stashling

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import net.venipa.stashling.core.data.Settings
import net.venipa.stashling.core.data.ThemeMode
import net.venipa.stashling.ds.RevealingStashlingTheme
import net.venipa.stashling.feature.lock.LockOverlay
import net.venipa.stashling.nav.AppNav
import net.venipa.stashling.ds.ThemeMode as DsThemeMode

// FragmentActivity (a ComponentActivity) because BiometricPrompt needs one.
class MainActivity : FragmentActivity() {
    private val container get() = (application as StashlingApp).container
    private var deepLinkScene by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val lock = container.appLock
        lifecycle.addObserver(lock)
        if (savedInstanceState == null) readDeepLink(intent)
        setContent {
            val settings by container.settings.settings.collectAsState(Settings())
            // FLAG_SECURE: no screenshots, blank recents.
            LaunchedEffect(settings.secureScreen) {
                if (settings.secureScreen) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            CompositionLocalProvider(LocalAppContainer provides container) {
                RevealingStashlingTheme(
                    themeMode = when (settings.theme) {
                        ThemeMode.LIGHT -> DsThemeMode.LIGHT
                        ThemeMode.DARK -> DsThemeMode.DARK
                        ThemeMode.SYSTEM -> DsThemeMode.SYSTEM
                    },
                    oledBlack = settings.oledBlack,
                    dynamicColor = settings.dynamicColor,
                ) {
                    // Sets background and LocalContentColor (onBackground).
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            AppNav(deepLinkScene, onDeepLinkHandled = { deepLinkScene = null })
                            LockOverlay(lock)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readDeepLink(intent)
    }

    // stashling://scene/<stashId>
    private fun readDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "stashling" && uri.host == "scene") deepLinkScene = uri.lastPathSegment?.takeIf { it.isNotBlank() }
    }
}
