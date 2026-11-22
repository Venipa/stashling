package net.venipa.stashling

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import net.venipa.stashling.ds.StashlingTheme
import net.venipa.stashling.nav.AppNav

class MainActivity : ComponentActivity() {
    private val container get() = (application as StashlingApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Read theme, OLED and secure-screen flags from SettingsStore before setContent.
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                StashlingTheme {
                    // Sets background and LocalContentColor (onBackground).
                    Surface(Modifier.fillMaxSize()) { AppNav() }
                }
            }
        }
    }
}
