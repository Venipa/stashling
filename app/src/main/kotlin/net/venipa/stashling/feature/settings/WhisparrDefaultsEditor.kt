package net.venipa.stashling.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.venipa.stashling.R
import net.venipa.stashling.core.data.WhisparrDefaults
import net.venipa.stashling.core.whisparr.QualityProfile
import net.venipa.stashling.core.whisparr.RootFolder
import net.venipa.stashling.ds.AppBottomSheet
import net.venipa.stashling.ds.GroupRow
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.TonalGroup
import net.venipa.stashling.ds.sheetTitle

private enum class Picker { Root, Quality, Monitor }

private val MONITORS = listOf("movieOnly" to R.string.settings_monitor_movie_only, "movieAndScene" to R.string.settings_monitor_movie_and_scene, "sceneOnly" to R.string.settings_monitor_scene_only, "none" to R.string.settings_monitor_none)

/** The four editable rows: root folder, quality profile, monitor, search on add. Used by onboarding and Settings. */
@Composable
fun WhisparrDefaultsEditor(
    roots: List<RootFolder>,
    profiles: List<QualityProfile>,
    defaults: WhisparrDefaults,
    onChange: (WhisparrDefaults) -> Unit,
) {
    var picker by remember { mutableStateOf<Picker?>(null) }
    val unset = stringResource(R.string.settings_not_set)
    TonalGroup {
        GroupRow(stringResource(R.string.settings_root_folder), supporting = defaults.rootFolder ?: unset, onClick = { picker = Picker.Root })
        GroupRow(stringResource(R.string.settings_quality_profile), supporting = profiles.firstOrNull { it.id == defaults.qualityProfileId }?.name ?: unset, onClick = { picker = Picker.Quality })
        GroupRow(
            stringResource(R.string.settings_monitor),
            supporting = stringResource(MONITORS.firstOrNull { it.first == defaults.monitor }?.second ?: R.string.settings_monitor_movie_only),
            onClick = { picker = Picker.Monitor },
        )
        GroupRow(
            stringResource(R.string.settings_search_on_add),
            supporting = stringResource(R.string.settings_search_on_add_body),
            trailing = { Switch(defaults.searchOnAdd, { onChange(defaults.copy(searchOnAdd = it)) }) },
            onClick = { onChange(defaults.copy(searchOnAdd = !defaults.searchOnAdd)) },
        )
    }
    picker?.let { p ->
        val (title, options, selected) =
            when (p) {
                Picker.Root -> Triple(stringResource(R.string.settings_root_folder), roots.map { it.path to it.path }, defaults.rootFolder)
                Picker.Quality -> Triple(stringResource(R.string.settings_quality_profile), profiles.map { it.name to it.id.toString() }, defaults.qualityProfileId?.toString())
                Picker.Monitor -> Triple(stringResource(R.string.settings_monitor), MONITORS.map { stringResource(it.second) to it.first }, defaults.monitor)
            }
        AppBottomSheet({ picker = null }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = Space.L)) {
                Text(title, Modifier.padding(horizontal = Space.L, vertical = Space.S), style = sheetTitle)
                if (options.isEmpty()) Text(stringResource(R.string.settings_picker_empty), Modifier.padding(Space.L), color = MaterialTheme.colorScheme.onSurfaceVariant)
                options.forEach { (label, value) ->
                    ListItem(
                        { Text(label) },
                        Modifier.clickable {
                            onChange(
                                when (p) {
                                    Picker.Root -> defaults.copy(rootFolder = value)
                                    Picker.Quality -> defaults.copy(qualityProfileId = value.toInt())
                                    Picker.Monitor -> defaults.copy(monitor = value)
                                },
                            )
                            picker = null
                        },
                        trailingContent = if (value == selected) ({ Icon(StashlingIcons.Check, null, tint = MaterialTheme.colorScheme.primary) }) else null,
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
        }
    }
}
