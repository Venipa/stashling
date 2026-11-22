package net.venipa.stashling.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.DEFAULT_PRESET_ID
import net.venipa.stashling.core.data.Settings
import net.venipa.stashling.core.data.TagRule
import net.venipa.stashling.core.data.WhisparrAccount
import net.venipa.stashling.core.data.WhisparrDefaults
import net.venipa.stashling.core.data.WhisparrPreset
import net.venipa.stashling.core.data.asDefaults
import net.venipa.stashling.core.stash.StashError
import net.venipa.stashling.core.stash.Tag
import net.venipa.stashling.core.stash.TagCategory
import net.venipa.stashling.core.stash.TagFilter
import net.venipa.stashling.core.whisparr.QualityProfile
import net.venipa.stashling.core.whisparr.RootFolder
import net.venipa.stashling.core.whisparr.WhisparrClients
import net.venipa.stashling.core.whisparr.WhisparrError
import net.venipa.stashling.core.whisparr.WhisparrTag
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.AppBottomSheet
import net.venipa.stashling.ds.ConfirmSheet
import net.venipa.stashling.ds.EmptyState
import net.venipa.stashling.ds.GroupRow
import net.venipa.stashling.ds.Gutter
import net.venipa.stashling.ds.LoadingSkeleton
import net.venipa.stashling.ds.LocalReduceMotion
import net.venipa.stashling.ds.LocalStashHaptics
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.SheetRadius
import net.venipa.stashling.ds.SkeletonBox
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashFilterChip
import net.venipa.stashling.ds.StashSwitch
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.TonalGroup
import net.venipa.stashling.ds.TransitionSpecs
import net.venipa.stashling.ds.backEnter
import net.venipa.stashling.ds.backExit
import net.venipa.stashling.ds.concentric
import net.venipa.stashling.ds.fieldColors
import net.venipa.stashling.ds.fieldShape
import net.venipa.stashling.ds.groupItemShape
import net.venipa.stashling.ds.sharedAxisXEnter
import net.venipa.stashling.ds.sharedAxisXExit
import net.venipa.stashling.ds.sheetTitle
import net.venipa.stashling.ds.stableFieldHeight
import java.util.UUID

// Sub-pages of Settings → Whisparr. A nested back stack: SettingsSection lives in nav/, and
// a NavDisplay here gives the same push transitions and predictive back as the app's own pages.
@Serializable internal data object WMenu : NavKey

@Serializable internal data object WConnection : NavKey

@Serializable internal data object WPresets : NavKey

/** [id] null = a new preset. */
@Serializable internal data class WPresetEdit(val id: String?) : NavKey

@Serializable internal data object WRules : NavKey

@Serializable internal data object WRuleNew : NavKey

/** Lists from Whisparr for the preset editor; [failed] = unreachable (lists empty). */
private class WhisparrLists(
    val roots: List<RootFolder>,
    val profiles: List<QualityProfile>,
    val tags: List<WhisparrTag>,
    val failed: Boolean,
)

@Composable
internal fun WhisparrSettings(
    onBack: () -> Unit,
    onRerun: (Int) -> Unit,
) {
    val container = LocalAppContainer.current
    val account by container.secrets.whisparr.collectAsState()
    val settings by container.settings.settings.collectAsState(Settings())
    var lists by remember { mutableStateOf<WhisparrLists?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(account, attempt) { lists = account?.let { loadLists(it) } }
    val retry: () -> Unit = {
        lists = null
        attempt++
    }

    val stack = rememberNavBackStack(WMenu)
    val reduce = LocalReduceMotion.current
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val offsetPx = with(LocalDensity.current) { TransitionSpecs.SHARED_AXIS_OFFSET.roundToPx() }
    val push: (NavKey) -> Unit = { stack.add(it) }
    val pop: () -> Unit = { stack.removeLastOrNull() }
    NavDisplay(
        backStack = stack,
        onBack = { stack.removeLastOrNull() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        transitionSpec = { sharedAxisXEnter(offsetPx, reduce) togetherWith sharedAxisXExit(offsetPx, reduce) },
        popTransitionSpec = { backEnter(ltr, reduce) togetherWith backExit(ltr, reduce) },
        predictivePopTransitionSpec = { backEnter(ltr, reduce) togetherWith backExit(ltr, reduce) },
        entryProvider =
        entryProvider {
            entry<WMenu> { MenuPage(account, settings, onBack, onRerun, push) }
            entry<WConnection> { ConnectionPage(pop, onRerun) }
            entry<WPresets> { PresetsPage(settings, lists, pop, push) }
            entry<WPresetEdit> { PresetEditPage(it.id, lists, retry, pop) }
            entry<WRules> { RulesPage(settings, pop, push) }
            entry<WRuleNew> { RuleNewPage(settings, pop) }
        },
    )
}

private suspend fun loadLists(account: WhisparrAccount): WhisparrLists = try {
    val api = WhisparrClients.create(account)
    WhisparrLists(api.rootFolders().filter { it.accessible }, api.qualityProfiles(), api.tags().sortedBy { it.label.lowercase() }, failed = false)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    WhisparrLists(emptyList(), emptyList(), emptyList(), failed = true)
}

@Composable
private fun presetName(p: WhisparrPreset) = if (p.id == DEFAULT_PRESET_ID) stringResource(R.string.settings_preset_default_name) else p.name

@Composable
private fun MenuPage(
    account: WhisparrAccount?,
    settings: Settings,
    onBack: () -> Unit,
    onRerun: (Int) -> Unit,
    push: (NavKey) -> Unit,
) = SettingsPage(stringResource(R.string.settings_whisparr), onBack) {
    if (account == null) {
        EmptyState(
            StashlingIcons.Download,
            stringResource(R.string.settings_whisparr_not_connected),
            body = stringResource(R.string.settings_whisparr_not_connected_body),
            action = { ActionButton(stringResource(R.string.settings_connect_whisparr), { onRerun(2) }, Modifier.widthIn(max = 280.dp)) },
        )
        return@SettingsPage
    }
    TonalGroup(Gutter) {
        GroupRow(stringResource(R.string.settings_whisparr_connection), supporting = account.baseUrl, icon = StashlingIcons.Download, onClick = { push(WConnection) })
        GroupRow(stringResource(R.string.settings_whisparr_presets), supporting = settings.allPresets.map { presetName(it) }.joinToString(", "), icon = StashlingIcons.ViewList, onClick = { push(WPresets) })
        GroupRow(
            stringResource(R.string.settings_whisparr_rules),
            supporting = if (settings.tagRules.isEmpty()) stringResource(R.string.settings_rules_empty_title) else pluralStringResource(R.plurals.settings_whisparr_rules_value, settings.tagRules.size, settings.tagRules.size),
            icon = StashlingIcons.Browse,
            onClick = { push(WRules) },
        )
    }
}

@Composable
private fun ConnectionPage(
    onBack: () -> Unit,
    onRerun: (Int) -> Unit,
) = SettingsPage(stringResource(R.string.settings_whisparr_connection), onBack) {
    WhisparrAccountGroup(onRerun)
}

/** Reconnect, ignore certificate errors, disconnect (with confirm). Shared by Accounts and Whisparr → Connection. */
@Composable
internal fun WhisparrAccountGroup(onRerun: (Int) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val whisparr by container.secrets.whisparr.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    TonalGroup(Gutter) {
        val w = whisparr
        if (w == null) {
            GroupRow(stringResource(R.string.settings_connect_whisparr), supporting = stringResource(R.string.onboarding_not_connected), onClick = { onRerun(2) })
        } else {
            GroupRow(stringResource(R.string.settings_reconnect), supporting = w.baseUrl, onClick = { onRerun(2) })
            GroupRow(
                stringResource(R.string.onboarding_ignore_cert),
                supporting = stringResource(R.string.settings_ignore_cert_body),
                trailing = { StashSwitch(w.ignoreCertErrors, { v -> scope.launch { container.secrets.setWhisparr(w.copy(ignoreCertErrors = v)) } }) },
                onClick = { scope.launch { container.secrets.setWhisparr(w.copy(ignoreCertErrors = !w.ignoreCertErrors)) } },
            )
            GroupRow(stringResource(R.string.settings_disconnect), supporting = stringResource(R.string.settings_disconnect_whisparr_body), onClick = { confirm = true })
        }
    }
    if (confirm) {
        ConfirmSheet(
            stringResource(R.string.settings_disconnect_whisparr_title),
            stringResource(R.string.settings_disconnect_whisparr_confirm),
            stringResource(R.string.settings_disconnect),
            onConfirm = {
                confirm = false
                scope.launch { container.secrets.setWhisparr(null) }
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun PresetsPage(
    settings: Settings,
    lists: WhisparrLists?,
    onBack: () -> Unit,
    push: (NavKey) -> Unit,
) = SettingsPage(stringResource(R.string.settings_whisparr_presets), onBack) {
    val unset = stringResource(R.string.settings_not_set)
    TonalGroup(Gutter) {
        settings.allPresets.forEach { p ->
            val profile = lists?.profiles?.firstOrNull { it.id == p.qualityProfileId }?.name
            GroupRow(presetName(p), supporting = listOfNotNull(p.rootFolder ?: unset, profile).joinToString(" · "), onClick = { push(WPresetEdit(p.id)) })
        }
    }
    if (settings.presets.isEmpty()) {
        EmptyState(
            StashlingIcons.ViewList,
            stringResource(R.string.settings_presets_empty_title),
            body = stringResource(R.string.settings_presets_empty_body),
            action = { ActionButton(stringResource(R.string.settings_preset_new), { push(WPresetEdit(null)) }, Modifier.widthIn(max = 280.dp), icon = StashlingIcons.Add) },
        )
    } else {
        ActionButton(stringResource(R.string.settings_preset_new), { push(WPresetEdit(null)) }, Gutter, tonal = true, icon = StashlingIcons.Add)
    }
}

/** 2A: full-screen editor. A draft until Save; Default is edited in place of the old defaults page (no name, no delete). */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PresetEditPage(
    id: String?,
    lists: WhisparrLists?,
    retry: () -> Unit,
    onBack: () -> Unit,
) {
    val store = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<WhisparrPreset?>(null) }
    var rulesUsing by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val account by LocalAppContainer.current.secrets.whisparr.collectAsState()
    var created by remember { mutableStateOf<List<WhisparrTag>>(emptyList()) }
    var newTag by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var tagError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        val s = store.settings.first()
        rulesUsing = s.tagRules.count { it.presetId == id }
        // A new preset starts from Default's folder and profile.
        draft = s.allPresets.firstOrNull { it.id == id } ?: s.whisparr.asPresetDraft()
    }
    val isDefault = id == DEFAULT_PRESET_ID
    val title =
        when {
            isDefault -> stringResource(R.string.settings_preset_default_name)
            id == null -> stringResource(R.string.settings_preset_new)
            else -> stringResource(R.string.settings_preset_edit)
        }
    SettingsPage(title, onBack) {
        val d = draft
        if (d == null || lists == null) {
            LoadingSkeleton(Gutter) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth().height(56.dp).padding(bottom = Space.L), fieldShape())
                    repeat(4) { SkeletonBox(Modifier.fillMaxWidth().height(72.dp), groupItemShape(it, 4)) }
                }
            }
            return@SettingsPage
        }
        if (lists.failed) {
            Text(stringResource(R.string.settings_whisparr_unreachable), Gutter, color = MaterialTheme.colorScheme.error)
            ActionButton(stringResource(R.string.settings_retry), retry, Gutter, tonal = true)
        }
        if (isDefault) {
            Text(stringResource(R.string.settings_preset_default_body), Gutter, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            TextField(
                d.name,
                { draft = d.copy(name = it) },
                Gutter.fillMaxWidth().stableFieldHeight(),
                label = { Text(stringResource(R.string.settings_preset_name)) },
                singleLine = true,
                shape = fieldShape(),
                colors = fieldColors(),
            )
        }
        Box(Gutter) {
            WhisparrDefaultsEditor(lists.roots, lists.profiles, d.asDefaults()) { v ->
                draft = d.copy(rootFolder = v.rootFolder, qualityProfileId = v.qualityProfileId, monitor = v.monitor, searchOnAdd = v.searchOnAdd)
            }
        }
        val tags = (lists.tags + created).distinctBy { it.id }

        // An existing label (case-insensitive) is selected instead of created; Whisparr stores it lowercased.
        fun addTag() {
            val label = newTag.trim()
            if (label.isEmpty() || creating) return
            val existing = tags.firstOrNull { it.label.equals(label, ignoreCase = true) }
            if (existing != null) {
                draft = d.copy(whisparrTagIds = (d.whisparrTagIds + existing.id).distinct())
                newTag = ""
                return
            }
            val acc = account ?: return
            creating = true
            tagError = null
            scope.launch {
                try {
                    val t = WhisparrClients.create(acc).createTag(label)
                    created = created + t
                    draft = draft?.let { it.copy(whisparrTagIds = (it.whisparrTagIds + t.id).distinct()) }
                    newTag = ""
                } catch (e: WhisparrError) {
                    tagError = e.message
                } finally {
                    creating = false
                }
            }
        }
        Column(Gutter, verticalArrangement = Arrangement.spacedBy(Space.S)) {
            SectionHeader(stringResource(R.string.settings_preset_tags))
            if (tags.isEmpty()) {
                Text(stringResource(R.string.settings_preset_tags_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                    tags.forEach { t ->
                        val on = t.id in d.whisparrTagIds
                        StashFilterChip(
                            on,
                            { draft = d.copy(whisparrTagIds = if (on) d.whisparrTagIds - t.id else d.whisparrTagIds + t.id) },
                            { Text(t.label) },
                            leadingIcon = if (on) ({ Icon(StashlingIcons.Check, null, Modifier.size(18.dp)) }) else null,
                        )
                    }
                }
            }
            if (!lists.failed) {
                TextField(
                    newTag,
                    {
                        newTag = it
                        tagError = null
                    },
                    Modifier.fillMaxWidth().stableFieldHeight(),
                    label = { Text(stringResource(R.string.settings_preset_tag_new)) },
                    trailingIcon = {
                        if (creating) {
                            LoadingIndicator(Modifier.size(24.dp))
                        } else {
                            IconButton(::addTag, enabled = newTag.isNotBlank()) { Icon(StashlingIcons.Add, stringResource(R.string.settings_preset_tag_create)) }
                        }
                    },
                    isError = tagError != null,
                    supportingText = tagError?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTag() }),
                    shape = fieldShape(),
                    colors = fieldColors(),
                )
            }
        }
        ActionButton(
            stringResource(R.string.settings_preset_save),
            {
                saving = true
                scope.launch {
                    // Tags deleted in Whisparr since are dropped on save.
                    val known = (lists.tags + created).map { it.id }.toSet()
                    val clean = d.copy(name = d.name.trim(), whisparrTagIds = if (lists.failed) d.whisparrTagIds else d.whisparrTagIds.filter { it in known })
                    store.update { it.withPreset(clean) }
                    onBack()
                }
            },
            Gutter,
            enabled = isDefault || d.name.isNotBlank(),
            loading = saving,
        )
        if (!isDefault && id != null) {
            ActionButton(stringResource(R.string.settings_preset_delete), { confirmDelete = true }, Gutter, tonal = true, icon = StashlingIcons.Delete)
        }
    }
    val d = draft
    if (confirmDelete && d != null) {
        ConfirmSheet(
            stringResource(R.string.settings_preset_delete_title, d.name),
            if (rulesUsing == 0) stringResource(R.string.settings_preset_delete_body) else pluralStringResource(R.plurals.settings_preset_delete_body_rules, rulesUsing, rulesUsing),
            stringResource(R.string.settings_preset_delete_confirm),
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    store.update { it.withoutPreset(d.id) }
                    onBack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun WhisparrDefaults.asPresetDraft() = WhisparrPreset(UUID.randomUUID().toString(), "", rootFolder, qualityProfileId)

/** 3A: ordered rules with priority numbers; long-press the handle and drag to reorder (or Move up/down in the row menu). */
@Composable
private fun RulesPage(
    settings: Settings,
    onBack: () -> Unit,
    push: (NavKey) -> Unit,
) {
    val store = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val deleted = stringResource(R.string.settings_rule_deleted)
    val undo = stringResource(R.string.settings_rule_undo)
    fun save(rules: List<TagRule>) = scope.launch { store.update { it.copy(tagRules = rules) } }
    SettingsPage(stringResource(R.string.settings_whisparr_rules), onBack, snackbar) {
        if (settings.tagRules.isEmpty()) {
            val noPreset = settings.presets.isEmpty()
            EmptyState(
                StashlingIcons.Browse,
                stringResource(R.string.settings_rules_empty_title),
                body = stringResource(if (noPreset) R.string.settings_rules_empty_body_no_preset else R.string.settings_rules_empty_body),
                action = {
                    if (noPreset) {
                        ActionButton(stringResource(R.string.settings_preset_new), { push(WPresetEdit(null)) }, Modifier.widthIn(max = 280.dp), icon = StashlingIcons.Add)
                    } else {
                        ActionButton(stringResource(R.string.settings_rule_new), { push(WRuleNew) }, Modifier.widthIn(max = 280.dp), icon = StashlingIcons.Add)
                    }
                },
            )
            return@SettingsPage
        }
        Text(stringResource(R.string.settings_rules_hint), Gutter, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RuleList(settings.tagRules, settings.allPresets, onReorder = ::save, onDelete = { rule ->
            val before = settings.tagRules
            save(before - rule)
            scope.launch { if (snackbar.showSnackbar(deleted, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) save(before) }
        })
        ActionButton(stringResource(R.string.settings_rule_new), { push(WRuleNew) }, Gutter, tonal = true, icon = StashlingIcons.Add)
    }
}

@Composable
private fun RuleList(
    rules: List<TagRule>,
    presets: List<WhisparrPreset>,
    onReorder: (List<TagRule>) -> Unit,
    onDelete: (TagRule) -> Unit,
) {
    var order by remember(rules) { mutableStateOf(rules) }
    // The drag detectors outlive recompositions: read the latest saved list through this.
    val saved by rememberUpdatedState(rules)
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var rowPx by remember { mutableIntStateOf(1) }
    val gapPx = with(LocalDensity.current) { 2.dp.toPx() }
    val haptic = LocalStashHaptics.current
    val cs = MaterialTheme.colorScheme
    val dragLabel = stringResource(R.string.settings_rule_drag)

    fun move(
        from: Int,
        to: Int,
    ): List<TagRule> = order.toMutableList().apply { add(to, removeAt(from)) }
    TonalGroup(Gutter) {
        order.forEachIndexed { i, rule ->
            key(rule.stashTagId) {
                val lifted = dragging == rule.stashTagId
                var menu by remember { mutableStateOf(false) }
                val target = presets.firstOrNull { it.id == rule.presetId } ?: presets.first()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_rule_row, rule.stashTagName), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier =
                    Modifier
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (lifted) offset else 0f
                            shadowElevation = if (lifted) 8.dp.toPx() else 0f
                        }.onSizeChanged { rowPx = it.height }
                        .clip(RoundedCornerShape4),
                    supportingContent = { Text(stringResource(R.string.settings_rule_target, presetName(target)), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .semantics { contentDescription = dragLabel }
                                    .pointerInput(rule.stashTagId) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                haptic.longPress()
                                                dragging = rule.stashTagId
                                                offset = 0f
                                            },
                                            onDragEnd = {
                                                dragging = null
                                                offset = 0f
                                                if (order != saved) onReorder(order)
                                            },
                                            onDragCancel = {
                                                dragging = null
                                                offset = 0f
                                                order = saved
                                            },
                                        ) { change, drag ->
                                            change.consume()
                                            offset += drag.y
                                            val at = order.indexOfFirst { it.stashTagId == dragging }
                                            val step = rowPx + gapPx
                                            // Swap with the neighbour once the row is half over it; the offset follows so the row stays under the finger.
                                            if (offset > step / 2 && at in 0 until order.lastIndex) {
                                                order = move(at, at + 1)
                                                offset -= step
                                            } else if (offset < -step / 2 && at > 0) {
                                                order = move(at, at - 1)
                                                offset += step
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Icon(StashlingIcons.DragHandle, null, tint = cs.onSurfaceVariant) }
                            Box(Modifier.size(28.dp).background(cs.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer)
                            }
                        }
                    },
                    trailingContent = {
                        Box {
                            IconButton({ menu = true }) { Icon(StashlingIcons.More, stringResource(R.string.settings_rule_more)) }
                            DropdownMenu(menu, { menu = false }) {
                                if (i > 0) {
                                    DropdownMenuItem({ Text(stringResource(R.string.settings_rule_move_up)) }, {
                                        menu = false
                                        onReorder(move(i, i - 1))
                                    })
                                }
                                if (i < order.lastIndex) {
                                    DropdownMenuItem({ Text(stringResource(R.string.settings_rule_move_down)) }, {
                                        menu = false
                                        onReorder(move(i, i + 1))
                                    })
                                }
                                DropdownMenuItem({ Text(stringResource(R.string.settings_rule_delete), color = cs.error) }, {
                                    menu = false
                                    onDelete(rule)
                                }, leadingIcon = { Icon(StashlingIcons.Delete, null, tint = cs.error) })
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = if (lifted) cs.surfaceContainerHighest else cs.surfaceContainer),
                )
            }
        }
    }
}

private val RoundedCornerShape4 = RoundedCornerShape(4.dp)

/** 3D: pick a StashDB tag (search, or browse categories with counts), then the preset. One rule per tag: re-adding replaces. */
@Composable
private fun RuleNewPage(
    settings: Settings,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val stash = container.stash
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<TagCategory?>(null) }
    var categories by remember { mutableStateOf<List<TagCategory>?>(null) }
    var results by remember { mutableStateOf<List<Tag>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Tag?>(null) }
    var presetId by remember { mutableStateOf(settings.presets.firstOrNull()?.id ?: DEFAULT_PRESET_ID) }
    var presetSheet by remember { mutableStateOf(false) }
    val browsing = query.isBlank() && category == null

    LaunchedEffect(attempt) {
        categories =
            try {
                stash.tagCategories()
            } catch (e: StashError) {
                failed = true
                emptyList()
            }
    }
    LaunchedEffect(query, category, attempt) {
        if (browsing) return@LaunchedEffect
        results = null
        failed = false
        delay(300) // debounce typing
        results =
            try {
                stash.tags(TagFilter(names = query.trim().ifEmpty { null }, categoryId = category?.id), 1).items
            } catch (e: StashError) {
                failed = true
                emptyList()
            }
    }

    // Back from the preset step returns to the tag list, not out of the page.
    BackHandler(picked != null) { picked = null }
    SettingsPage(stringResource(R.string.settings_rule_new), onBack) {
        val tag = picked
        if (tag != null) {
            val preset = settings.allPresets.firstOrNull { it.id == presetId } ?: settings.allPresets.first()
            TonalGroup(Gutter) {
                GroupRow(stringResource(R.string.settings_rule_tag), supporting = tag.name, onClick = { picked = null })
                GroupRow(stringResource(R.string.settings_rule_preset), supporting = presetName(preset), onClick = { presetSheet = true })
            }
            settings.tagRules.firstOrNull { it.stashTagId == tag.id }?.let { old ->
                val oldPreset = settings.allPresets.firstOrNull { it.id == old.presetId } ?: settings.allPresets.first()
                Text(stringResource(R.string.settings_rule_replaces, presetName(oldPreset)), Gutter, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ActionButton(stringResource(R.string.settings_rule_add), {
                scope.launch {
                    container.settings.update { s ->
                        val rule = TagRule(tag.id, tag.name, preset.id)
                        val at = s.tagRules.indexOfFirst { it.stashTagId == tag.id }
                        // Replacing keeps the old priority; a new rule goes last.
                        s.copy(tagRules = if (at >= 0) s.tagRules.toMutableList().apply { set(at, rule) } else s.tagRules + rule)
                    }
                    onBack()
                }
            }, Gutter)
            if (presetSheet) {
                PresetPickerSheet(settings.allPresets, preset.id, onPick = {
                    presetId = it
                    presetSheet = false
                }, onDismiss = { presetSheet = false })
            }
            return@SettingsPage
        }

        TextField(
            query,
            { query = it },
            Gutter.fillMaxWidth().stableFieldHeight(),
            label = { Text(stringResource(R.string.settings_rule_search)) },
            leadingIcon = { Icon(StashlingIcons.Search, null) },
            trailingIcon = if (query.isNotEmpty()) ({ IconButton({ query = "" }) { Icon(StashlingIcons.Close, stringResource(R.string.settings_rule_clear)) } }) else null,
            singleLine = true,
            shape = fieldShape(),
            colors = fieldColors(),
        )
        category?.let { c ->
            InputChip(true, { category = null }, { Text(c.name) }, Gutter, trailingIcon = { Icon(StashlingIcons.Close, null, Modifier.size(18.dp)) })
        }
        if (failed) {
            Text(stringResource(R.string.settings_stash_unreachable), Gutter, color = MaterialTheme.colorScheme.error)
            ActionButton(stringResource(R.string.settings_retry), { attempt++ }, Gutter, tonal = true)
        }
        if (browsing) {
            val cats = categories
            if (cats == null) {
                RowsSkeleton()
            } else {
                // Categories by group; the header counts them.
                cats.groupBy { it.group }.toSortedMap().forEach { (group, list) ->
                    SectionHeader(stringResource(R.string.settings_rule_group_count, group.lowercase().replaceFirstChar { it.uppercase() }, list.size), Gutter)
                    TonalGroup(Gutter) {
                        list.sortedBy { it.name }.forEach { c -> GroupRow(c.name, supporting = c.description?.takeIf { it.isNotBlank() }, onClick = { category = c }) }
                    }
                }
            }
        } else {
            val tags = results
            when {
                tags == null -> RowsSkeleton()

                tags.isEmpty() && !failed -> EmptyState(StashlingIcons.Search, stringResource(R.string.settings_rule_no_tags))

                else -> {
                    val other = stringResource(R.string.settings_rule_uncategorised)
                    // Matches grouped by category; the header counts the tags in it.
                    tags.groupBy { it.category ?: other }.toSortedMap().forEach { (cat, list) ->
                        SectionHeader(stringResource(R.string.settings_rule_group_count, cat, list.size), Gutter)
                        TonalGroup(Gutter) {
                            list.forEach { t ->
                                val ruled = settings.tagRules.any { it.stashTagId == t.id }
                                GroupRow(
                                    t.name,
                                    supporting = t.aliases.take(3).joinToString(", ").ifEmpty { null },
                                    onClick = { picked = t },
                                    trailing = if (ruled) ({ Icon(StashlingIcons.Check, null, tint = MaterialTheme.colorScheme.primary) }) else null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowsSkeleton() = LoadingSkeleton(Gutter) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(5) { SkeletonBox(Modifier.fillMaxWidth().height(56.dp), groupItemShape(it, 5)) }
    }
}

@Composable
private fun PresetPickerSheet(
    presets: List<WhisparrPreset>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) = AppBottomSheet(onDismiss) {
    Column(Modifier.padding(horizontal = Space.L).padding(bottom = Space.L).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Space.S)) {
        Text(stringResource(R.string.settings_rule_preset), style = sheetTitle)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            presets.forEachIndexed { i, p ->
                ListItem(
                    { Text(presetName(p)) },
                    Modifier.clip(groupItemShape(i, presets.size, outer = concentric(SheetRadius, Space.L))).clickable { onPick(p.id) },
                    supportingContent = { Text(p.rootFolder ?: stringResource(R.string.settings_not_set), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = if (p.id == selected) ({ Icon(StashlingIcons.Check, null, tint = MaterialTheme.colorScheme.primary) }) else null,
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        }
    }
}
