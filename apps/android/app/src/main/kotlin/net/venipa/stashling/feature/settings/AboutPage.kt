package net.venipa.stashling.feature.settings

import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.venipa.stashling.BuildConfig
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.ds.GroupRow
import net.venipa.stashling.ds.Gutter
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.StashSwitch
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.TonalCard
import net.venipa.stashling.ds.TonalGroup
import net.venipa.stashling.feature.common.openLink
import net.venipa.stashling.update.UpdateState

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
            GroupRow(stringResource(R.string.about_source), supporting = stringResource(R.string.about_source_host), icon = StashlingIcons.Browse, onClick = { openLink(context, SOURCE) }, trailing = external)
            GroupRow(stringResource(R.string.settings_licence), supporting = stringResource(R.string.settings_licence_value), icon = StashlingIcons.Info, onClick = { openLink(context, LICENCE) }, trailing = external)
        }

        // Written at build time (release: GitHub contributors API, else the committed list); no network for the list itself.
        val contributors by produceState(emptyList<Contributor>()) { value = withContext(Dispatchers.IO) { readContributors(context) } }
        SectionHeader(stringResource(R.string.about_contributors), Gutter.padding(top = 8.dp))
        TonalGroup(Gutter) {
            contributors.forEach { c ->
                GroupRow(
                    c.login,
                    supporting = if (c.contributions > 0) pluralStringResource(R.plurals.about_contributions, c.contributions, c.contributions) else null,
                    onClick = { openLink(context, c.htmlUrl) },
                    leading = {
                        // Decorative: the login is the row's label.
                        AsyncImage(
                            ImageRequest.Builder(context).data(c.avatarUrl + (if ('?' in c.avatarUrl) "&s=120" else "?s=120")).size(120).build(),
                            null,
                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                        )
                    },
                )
            }
            GroupRow(
                stringResource(R.string.about_contributors_all),
                supporting = stringResource(R.string.about_contributors_all_value),
                icon = StashlingIcons.Person,
                onClick = { openLink(context, CONTRIBUTORS) },
                trailing = { Icon(StashlingIcons.OpenExternal, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        }

        SectionHeader(stringResource(R.string.about_app), Gutter.padding(top = 8.dp))
        TonalGroup(Gutter) {
            GroupRow(stringResource(R.string.about_version), supporting = stringResource(R.string.about_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_TYPE, BuildConfig.GIT_HASH), icon = StashlingIcons.Info)
            UpdateRows()
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

@Serializable
internal data class Contributor(
    val login: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("avatar_url") val avatarUrl: String,
    val contributions: Int = 0,
)

private val contributorsJson = Json { ignoreUnknownKeys = true }

/** The build-time list (assets/contributors.json); only GitHub https links survive, a broken file shows none. */
internal fun parseContributors(text: String): List<Contributor> = runCatching { contributorsJson.decodeFromString<List<Contributor>>(text) }
    .getOrDefault(emptyList())
    .filter { it.htmlUrl.startsWith("https://github.com/") && it.avatarUrl.startsWith("https://avatars.githubusercontent.com/") }

private fun readContributors(context: android.content.Context): List<Contributor> = runCatching { context.assets.open("contributors.json").use { parseContributors(it.readBytes().decodeToString()) } }.getOrDefault(emptyList())

// Host and port only: the path (urlBase) adds nothing here.
private fun String.host(): String = Uri.parse(this).let { u -> u.host?.let { h -> if (u.port > 0) "$h:${u.port}" else h } } ?: this

/** Updater controls under About → App. */
@Composable
private fun UpdateRows() {
    val container = LocalAppContainer.current
    val updater = container.updater
    val state by updater.state.collectAsState()
    val checking by updater.checking.collectAsState()
    val checkFailed by updater.checkFailed.collectAsState()
    val settings = container.settingsState.collectAsState().value
    val scope = rememberCoroutineScope()
    val release = state.release
    val lastCheck = settings?.updateLastCheck
    GroupRow(
        stringResource(R.string.update_check),
        supporting =
        when {
            checking -> stringResource(R.string.update_checking)

            release != null -> stringResource(R.string.update_available_title, release.version)

            checkFailed -> stringResource(R.string.update_check_failed)

            lastCheck != null -> {
                val now = System.currentTimeMillis()
                val ago =
                    if (now - lastCheck < DateUtils.MINUTE_IN_MILLIS) {
                        stringResource(R.string.update_just_now)
                    } else {
                        DateUtils.getRelativeTimeSpanString(lastCheck, now, DateUtils.MINUTE_IN_MILLIS).toString().replaceFirstChar(Char::lowercaseChar)
                    }
                stringResource(R.string.update_up_to_date, ago)
            }

            else -> stringResource(R.string.update_never_checked)
        },
        icon = StashlingIcons.Download,
        onClick = if (release != null) updater::openSheet else null,
        trailing = {
            OutlinedButton({ scope.launch { if (updater.check(manual = true) != null) updater.openSheet() } }, enabled = !checking) {
                Text(stringResource(R.string.update_check_now))
            }
        },
    )
    val auto = settings?.updateAuto ?: true
    val pre = settings?.updatePrerelease ?: false
    GroupRow(
        stringResource(R.string.update_auto),
        supporting = stringResource(R.string.update_auto_body),
        icon = StashlingIcons.CheckCircle,
        onClick = { scope.launch { container.settings.update { it.copy(updateAuto = !auto) } } },
        trailing = { StashSwitch(auto, { v -> scope.launch { container.settings.update { it.copy(updateAuto = v) } } }) },
    )
    GroupRow(
        stringResource(R.string.update_pre),
        supporting = stringResource(R.string.update_pre_body),
        icon = StashlingIcons.Tag,
        onClick = { scope.launch { container.settings.update { it.copy(updatePrerelease = !pre) } } },
        trailing = { StashSwitch(pre, { v -> scope.launch { container.settings.update { it.copy(updatePrerelease = v) } } }) },
    )
}
