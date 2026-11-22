package net.venipa.stashling.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.PerformerGender
import net.venipa.stashling.core.data.Settings
import net.venipa.stashling.ds.GroupRow
import net.venipa.stashling.ds.Gutter
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.TonalGroup
import net.venipa.stashling.feature.common.BrowseIcons
import net.venipa.stashling.feature.common.ListModeSegmented
import net.venipa.stashling.nav.SettingsSection

/** Menu value of the gender setting ("Female performers"). */
fun PerformerGender.menuRes() = when (this) {
    PerformerGender.FEMALE -> R.string.settings_browsing_female
    PerformerGender.MALE -> R.string.settings_browsing_male
    PerformerGender.BOTH -> R.string.settings_browsing_both
}

/** Settings → Browsing & search: performer gender, scene layout, recent searches, link to Hidden. */
@Composable
internal fun BrowsingPage(
    settings: Settings,
    hiddenCount: Int,
    onBack: () -> Unit,
    onOpen: (SettingsSection) -> Unit,
) {
    val store = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    SettingsPage(stringResource(R.string.settings_browsing), onBack) {
        SectionHeader(stringResource(R.string.settings_performers), Gutter)
        val genders = PerformerGender.entries
        StashSegmented(
            listOf(R.string.settings_performers_female, R.string.settings_performers_male, R.string.settings_performers_both).map { stringResource(it) },
            genders.indexOf(settings.performerGender),
            { i -> scope.launch { store.update { it.copy(performerGender = genders[i]) } } },
            Gutter,
        )
        Text(stringResource(R.string.settings_gender_scenes_note), Gutter, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionHeader(stringResource(R.string.settings_scene_layout), Gutter)
        ListModeSegmented(settings.sceneListMode, Gutter)
        SectionHeader(stringResource(R.string.settings_recent_searches), Gutter)
        TonalGroup(Gutter) {
            val n = settings.recentSearches.size
            GroupRow(
                stringResource(R.string.settings_clear_recents),
                supporting = if (n == 0) stringResource(R.string.settings_recents_none) else stringResource(R.string.settings_recents_count, n),
                icon = StashlingIcons.Delete,
                onClick = if (n == 0) null else ({ scope.launch { store.update { it.copy(recentSearches = emptyList()) } } }),
                trailing = {},
            )
            GroupRow(stringResource(R.string.settings_hidden_link, hiddenCount), icon = BrowseIcons.VisibilityOff, onClick = { onOpen(SettingsSection.Hidden) })
        }
    }
}
