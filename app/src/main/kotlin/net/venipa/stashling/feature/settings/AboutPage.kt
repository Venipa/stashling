package net.venipa.stashling.feature.settings

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import net.venipa.stashling.BuildConfig
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.ds.GroupRow
import net.venipa.stashling.ds.Gutter
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.TonalCard
import net.venipa.stashling.ds.TonalGroup
import net.venipa.stashling.feature.common.openLink

private const val SOURCE = "https://github.com/Venipa/stashling"
private const val AUTHOR = "https://github.com/Venipa"
private const val CONTRIBUTORS = "https://github.com/Venipa/stashling/graphs/contributors"
private const val LICENCE = "https://www.gnu.org/licenses/gpl-3.0.html"

/** About (medicatore's layout): hero with the app icon, Links, App facts, then the connected servers. */
@Composable
internal fun AboutPage(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val stash by container.secrets.stash.collectAsState()
    val whisparrAccount by container.secrets.whisparr.collectAsState()
    val whisparr by container.whisparr.collectAsState()
    // null = checking; "" = not reachable.
    var whisparrVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(whisparr) {
        whisparrVersion = null
        val repo = whisparr ?: return@LaunchedEffect
        whisparrVersion = try {
            repo.status().version
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }
    val notConnected = stringResource(R.string.about_not_connected)
    SettingsPage(stringResource(R.string.settings_about_title), onBack) {
        TonalCard(Gutter, hero = true, contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 24.dp)) {
            // Decorative: the name sits right under it. Both adaptive layers, clipped like a launcher icon.
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 8.dp).size(96.dp).clip(RoundedCornerShape(28.dp))) {
                Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
            }
            Text(stringResource(R.string.app_name), Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.about_tagline),
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        SectionHeader(stringResource(R.string.about_links), Gutter.padding(top = 8.dp))
        TonalGroup(Gutter) {
            val external: @Composable () -> Unit = { Icon(StashlingIcons.OpenExternal, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            GroupRow(stringResource(R.string.about_author), supporting = stringResource(R.string.about_author_value), icon = StashlingIcons.Person, onClick = { openLink(context, AUTHOR) }, trailing = external)
            GroupRow(stringResource(R.string.about_contributors), supporting = stringResource(R.string.about_contributors_value), icon = StashlingIcons.Person, onClick = { openLink(context, CONTRIBUTORS) }, trailing = external)
            GroupRow(stringResource(R.string.about_source), supporting = stringResource(R.string.about_source_host), icon = StashlingIcons.Browse, onClick = { openLink(context, SOURCE) }, trailing = external)
            GroupRow(stringResource(R.string.settings_licence), supporting = stringResource(R.string.settings_licence_value), icon = StashlingIcons.Info, onClick = { openLink(context, LICENCE) }, trailing = external)
        }

        SectionHeader(stringResource(R.string.about_app), Gutter.padding(top = 8.dp))
        TonalGroup(Gutter) {
            GroupRow(stringResource(R.string.about_version), supporting = stringResource(R.string.about_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_TYPE), icon = StashlingIcons.Info)
            GroupRow(stringResource(R.string.about_sources), supporting = stringResource(R.string.about_sources_value), icon = StashlingIcons.Scenes)
            GroupRow(stringResource(R.string.about_privacy), supporting = stringResource(R.string.about_privacy_value), icon = StashlingIcons.Privacy)
        }

        SectionHeader(stringResource(R.string.about_servers), Gutter.padding(top = 8.dp))
        TonalGroup(Gutter) {
            GroupRow(stringResource(R.string.onboarding_stashdb), supporting = stash?.baseUrl?.host() ?: notConnected, icon = StashlingIcons.Search)
            val host = whisparrAccount?.baseUrl?.host()
            GroupRow(
                stringResource(R.string.onboarding_whisparr),
                supporting = when {
                    host == null -> notConnected
                    whisparrVersion == null -> stringResource(R.string.about_checking, host)
                    whisparrVersion == "" -> stringResource(R.string.about_unreachable, host)
                    else -> stringResource(R.string.about_whisparr_version, host, whisparrVersion!!)
                },
                icon = StashlingIcons.Download,
            )
        }
    }
}

// Host and port only: the path (urlBase) adds nothing here.
private fun String.host(): String = Uri.parse(this).let { u -> u.host?.let { h -> if (u.port > 0) "$h:${u.port}" else h } } ?: this
