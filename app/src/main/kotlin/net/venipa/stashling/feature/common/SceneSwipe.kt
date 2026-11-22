package net.venipa.stashling.feature.common

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.venipa.stashling.AppContainer
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.Hidden
import net.venipa.stashling.core.data.HiddenIds
import net.venipa.stashling.core.data.HiddenKind
import net.venipa.stashling.core.stash.PerformerRef
import net.venipa.stashling.core.stash.SceneCard
import net.venipa.stashling.core.stash.StashError
import net.venipa.stashling.core.whisparr.AddMode
import net.venipa.stashling.core.whisparr.PanelState
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.AppBottomSheet
import net.venipa.stashling.ds.AppSnackbar
import net.venipa.stashling.ds.SkeletonText
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.concentric
import net.venipa.stashling.ds.sheetTitle
import kotlin.math.roundToInt

private enum class SwipeStage { None, Add, HideScene, HidePerformer }

private val LabelWidth = 180.dp

/**
 * Swipe actions on a scene item (Home): start→end = add ([addLabel] null = no such swipe), end→start = hide scene,
 * past the second threshold = hide performer ([onHidePerformer] null = no second threshold). Directions follow the layout
 * direction. The item always settles back (no dismiss); the drag is clamped and settles without bounce.
 */
@Composable
fun SwipeActions(
    shape: Shape,
    addLabel: String?,
    onAdd: () -> Unit,
    onHideScene: () -> Unit,
    onHidePerformer: (() -> Unit)?,
    modifier: Modifier = Modifier,
    addEnabled: Boolean = true,
    onPress: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val press by rememberUpdatedState(onPress)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptic = LocalHapticFeedback.current
    var width by remember { mutableIntStateOf(0) }
    // Logical offset: positive = towards the end.
    var dx by remember { mutableFloatStateOf(0f) }
    val first = width * 0.28f
    val second = width * 0.58f
    fun stageOf(x: Float) = when {
        width == 0 -> SwipeStage.None
        x >= first && addLabel != null -> SwipeStage.Add
        x <= -second && onHidePerformer != null -> SwipeStage.HidePerformer
        x <= -first -> SwipeStage.HideScene
        else -> SwipeStage.None
    }
    val stage = stageOf(dx)
    LaunchedEffect(stage) { if (stage != SwipeStage.None) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    val settle = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (stage) {
        SwipeStage.Add -> if (addEnabled) cs.primary to cs.onPrimary else cs.surfaceContainerHighest to cs.onSurface
        SwipeStage.HideScene -> cs.tertiary to cs.onTertiary
        SwipeStage.HidePerformer -> cs.error to cs.onError
        SwipeStage.None -> cs.surfaceContainerHighest to cs.onSurfaceVariant
    }
    val bgColor by animateColorAsState(bg, MaterialTheme.motionScheme.fastEffectsSpec(), label = "swipeBg")
    val hideScene = stringResource(R.string.swipe_hide_scene)
    val hidePerformer = stringResource(R.string.swipe_hide_performer)
    Box(
        modifier
            .onSizeChanged { width = it.width }
            // First touch, before any swipe or long-press threshold: warm the Whisparr state for the label and the menu.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).also { press() } } }
            .semantics {
                customActions = listOfNotNull(
                    addLabel?.let { CustomAccessibilityAction(it) { onAdd().let { true } } },
                    CustomAccessibilityAction(hideScene) { onHideScene().let { true } },
                    onHidePerformer?.let { CustomAccessibilityAction(hidePerformer) { it().let { true } } },
                )
            }
            .draggable(
                rememberDraggableState { d ->
                    val max = if (addLabel != null) width * 0.45f else 0f
                    val min = -width * (if (onHidePerformer != null) 0.8f else 0.45f)
                    dx = (dx + if (rtl) -d else d).coerceIn(min, max)
                },
                Orientation.Horizontal,
                onDragStopped = {
                    when (stageOf(dx)) {
                        SwipeStage.Add -> onAdd()
                        SwipeStage.HideScene -> onHideScene()
                        SwipeStage.HidePerformer -> onHidePerformer?.invoke()
                        SwipeStage.None -> Unit
                    }
                    animate(dx, 0f, animationSpec = settle) { v, _ -> dx = v }
                },
            ),
    ) {
        if (dx != 0f) {
            val toEnd = dx > 0
            val label = when {
                toEnd -> addLabel.orEmpty()
                stage == SwipeStage.HidePerformer -> hidePerformer
                else -> hideScene
            }
            val icon = when {
                toEnd -> StashlingIcons.Add
                stage == SwipeStage.HidePerformer -> BrowseIcons.PersonRemove
                else -> BrowseIcons.VisibilityOff
            }
            Box(Modifier.matchParentSize().clip(shape).background(bgColor).padding(horizontal = Space.L), contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.S), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = fg)
                    // Stable slot: a fixed-width one-line label; a state change crossfades in place.
                    Crossfade(label, Modifier.width(LabelWidth), label = "swipeLabel") { t ->
                        Text(t, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (toEnd) TextAlign.Start else TextAlign.End)
                    }
                }
            }
        }
        Box(Modifier.offset { IntOffset((if (rtl) -dx else dx).roundToInt(), 0) }) { content() }
    }
}

/** A snackbar line from [SceneSwipeController]; [undo] non-empty adds an Undo action that unhides those rows. */
data class SwipeMessage(
    @StringRes val res: Int,
    val arg: String = "",
    val undo: List<Hidden> = emptyList(),
    /** Shown ~2 s (back-to-exit hint) instead of the short snackbar duration. */
    val brief: Boolean = false,
)

/** The start→end swipe's label: [res] with [arg]; not [enabled] = only a message (already downloading, …). */
data class AddLabel(@StringRes val res: Int, val arg: String = "", val enabled: Boolean = true)

/** "Hide performer" on a scene with several performers: the pick sheet input ([fallback] = the card names). */
data class PerformerPick(val sceneId: String, val fallback: List<PerformerRef>)

/**
 * App-wide scene actions: swipe on every scene list but Library, and the detail "⋮" menus. Whisparr add with the
 * resolved preset, hide / unhide scene and performers (+ undo). Messages and the pick sheet render in [SceneActionsHost].
 */
class SceneSwipeController(private val c: AppContainer, private val scope: CoroutineScope) {
    val messages = MutableSharedFlow<SwipeMessage>(extraBufferCapacity = 8)

    val picking = MutableStateFlow<PerformerPick?>(null)

    /** Whisparr state per scene id with its fetch time; read by swipe labels and the context menu (ready before the gesture). */
    val states = MutableStateFlow<Map<String, Pair<PanelState, Long>>>(emptyMap())

    /** On pointer-down: one state call per scene, cached [STATE_TTL_MS]; [invalidate]d by add / search / hide. */
    fun prefetch(sceneId: String) {
        val repo = c.whisparr.value ?: return
        val hit = states.value[sceneId]
        if (hit != null && System.currentTimeMillis() - hit.second < STATE_TTL_MS) return
        scope.launch {
            val st = repo.panelState(sceneId)
            states.update { it + (sceneId to (st to System.currentTimeMillis())) }
        }
    }

    fun invalidate(sceneId: String) = states.update { it - sceneId }

    fun add(sceneId: String, tagIds: List<String>) {
        val repo = c.whisparr.value ?: return
        scope.launch {
            // In Whisparr already → search when missing, else only say so; never a second add or search.
            when (val now = repo.panelState(sceneId)) {
                is PanelState.Missing, is PanelState.Failed -> {
                    val scene = if (now is PanelState.Missing) now.scene else (now as PanelState.Failed).scene
                    val s = repo.search(scene.id)
                    messages.emit(if (s is PanelState.Error) SwipeMessage(R.string.library_whisparr_error, s.message) else SwipeMessage(R.string.swipe_searching))
                    invalidate(sceneId)
                    return@launch
                }

                is PanelState.Searching -> return@launch messages.emit(SwipeMessage(R.string.swipe_already_searching))

                is PanelState.InQueue -> return@launch messages.emit(SwipeMessage(R.string.swipe_already_downloading))

                is PanelState.Downloaded -> return@launch messages.emit(SwipeMessage(R.string.swipe_already_downloaded))

                is PanelState.NotInLibrary, is PanelState.Error -> Unit

                else -> return@launch messages.emit(SwipeMessage(R.string.library_already_whisparr))
            }
            val preset = repo.resolvePreset(tagIds).first
            val mode = if (preset.searchOnAdd) AddMode.QUEUE else AddMode.REQUEST
            messages.emit(
                when (val s = repo.add(sceneId, mode, tagIds)) {
                    is PanelState.Error -> SwipeMessage(R.string.library_whisparr_error, s.message)
                    is PanelState.Searching, is PanelState.Missing -> SwipeMessage(R.string.swipe_added, preset.name)
                    else -> SwipeMessage(R.string.library_already_whisparr)
                },
            )
            invalidate(sceneId)
        }
    }

    fun hideScene(id: String, title: String?, imageUrl: String?) {
        scope.launch {
            val item = Hidden(HiddenKind.SCENE, id, title.orEmpty(), imageUrl, System.currentTimeMillis())
            c.hidden.hide(item)
            invalidate(id)
            messages.emit(SwipeMessage(R.string.swipe_scene_hidden, undo = listOf(item)))
        }
    }

    /** One performer hides at once; several open the pick sheet. */
    fun hidePerformerOf(sceneId: String, performers: List<PerformerRef>) {
        if (performers.size > 1) picking.value = PerformerPick(sceneId, performers) else hidePerformers(performers)
    }

    fun hidePerformerOf(scene: SceneCard) = hidePerformerOf(scene.id, scene.performerIds.mapIndexed { i, id -> PerformerRef(id, scene.performers.getOrElse(i) { "" }, null, null, null) })

    /** The scene's performers with portrait and `as` name (cached scene call); [PerformerPick.fallback] when StashDB fails. */
    suspend fun performersOf(pick: PerformerPick): List<PerformerRef> = try {
        c.stash.scene(pick.sceneId)?.performers
    } catch (_: StashError) {
        null
    } ?: pick.fallback

    fun hidePerformers(performers: List<PerformerRef>) {
        picking.value = null
        if (performers.isEmpty()) return
        scope.launch {
            val items = performers.map { p ->
                // A card has no portrait: the cached performer call fills it for the Settings list; the hide works without it.
                val image = p.thumbnail?.sized(300) ?: try {
                    c.stash.performer(p.id)?.let { (it.images.firstOrNull() ?: it.thumbnail)?.sized(300) }
                } catch (_: StashError) {
                    null
                }
                Hidden(HiddenKind.PERFORMER, p.id, p.name, image, System.currentTimeMillis())
            }
            items.forEach { c.hidden.hide(it) }
            messages.emit(SwipeMessage(R.string.swipe_performer_hidden, items.joinToString(", ") { it.name }, items))
        }
    }

    fun unhide(kind: HiddenKind, id: String) {
        scope.launch {
            c.hidden.unhide(kind, id)
            messages.emit(SwipeMessage(R.string.swipe_unhidden))
        }
    }

    fun undo(items: List<Hidden>) {
        scope.launch { items.forEach { c.hidden.unhide(it.kind, it.stashId) } }
    }

    companion object {
        const val STATE_TTL_MS = 60_000L
    }
}

/** The add action's label for a known (or still unknown = null) Whisparr state: unknown shows "Add · preset" so nothing jumps. */
fun addLabelFor(
    state: PanelState?,
    preset: String,
): AddLabel = when (state) {
    is PanelState.Missing, is PanelState.Failed -> AddLabel(R.string.swipe_search_again)
    is PanelState.Searching -> AddLabel(R.string.swipe_already_searching, enabled = false)
    is PanelState.InQueue -> AddLabel(R.string.swipe_already_downloading, enabled = false)
    is PanelState.Downloaded -> AddLabel(R.string.swipe_already_downloaded, enabled = false)
    else -> AddLabel(R.string.swipe_add, preset)
}

/** The preset a card resolves to, from its own tag ids and the current settings (local, instant). */
@Composable
fun presetNameOf(scene: SceneCard): String {
    val s by LocalAppContainer.current.settingsNow.collectAsState()
    return remember(s, scene.tagIds) { s?.resolvePreset(scene.tagIds)?.first?.name } ?: "Default"
}

/** [SwipeActions] for one scene of a [ScenesGrid]; no add swipe while Whisparr isn't connected. */
@Composable
fun SwipeableScene(
    scene: SceneCard,
    shape: Shape,
    content: @Composable () -> Unit,
) {
    val c = LocalAppContainer.current
    val ctrl = c.sceneActions
    val whisparr by c.whisparr.collectAsState()
    val states by ctrl.states.collectAsState()
    val known = addLabelFor(states[scene.id]?.first, presetNameOf(scene))
    val addLabel = if (whisparr == null) null else stringResource(known.res, known.arg)
    SwipeActions(
        shape,
        addLabel,
        { ctrl.add(scene.id, scene.tagIds) },
        { ctrl.hideScene(scene.id, scene.title, scene.cover?.sized(300)) },
        if (scene.performerIds.isEmpty()) null else ({ ctrl.hidePerformerOf(scene) }),
        addEnabled = known.enabled,
        onPress = { ctrl.prefetch(scene.id) },
        content = content,
    )
}

/** The scene actions' snackbar (with Undo) and performer pick sheet, over every screen; [modifier] places the snackbar. */
@Composable
fun SceneActionsHost(modifier: Modifier) {
    val ctrl = LocalAppContainer.current.sceneActions
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(ctrl) {
        ctrl.messages.collect { m ->
            val text = context.getString(m.res, m.arg)
            // A new message replaces the shown one instead of queueing behind it.
            snackbar.currentSnackbarData?.dismiss()
            launch {
                if (m.brief) {
                    launch {
                        kotlinx.coroutines.delay(2000)
                        snackbar.currentSnackbarData?.takeIf { it.visuals.message == text }?.dismiss()
                    }
                    snackbar.showSnackbar(text)
                } else if (m.undo.isEmpty()) {
                    snackbar.showSnackbar(text)
                } else if (snackbar.showSnackbar(text, context.getString(R.string.library_undo), duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                    ctrl.undo(m.undo)
                }
            }
        }
    }
    SnackbarHost(snackbar, modifier) { AppSnackbar(it) }
    val picking by ctrl.picking.collectAsState()
    picking?.let { pick -> PerformerPickSheet(ctrl, pick, { ctrl.picking.value = null }, ctrl::hidePerformers) }
}

/** Multi-select with 2:3 portraits: nothing preselected, "Hide N performers" confirms, back / outside cancels. */
@Composable
private fun PerformerPickSheet(
    ctrl: SceneSwipeController,
    pick: PerformerPick,
    onDismiss: () -> Unit,
    onPick: (List<PerformerRef>) -> Unit,
) = AppBottomSheet(onDismiss) {
    val performers by produceState<List<PerformerRef>?>(null, pick) { value = ctrl.performersOf(pick) }
    var picked by remember(pick) { mutableStateOf(emptySet<String>()) }
    Column(Modifier.padding(horizontal = Space.L).padding(bottom = Space.L).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Space.S)) {
        Text(stringResource(R.string.swipe_pick_performer), style = sheetTitle)
        val list = performers
        if (list == null) {
            SkeletonText(Modifier.padding(vertical = Space.M), 3)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                list.forEachIndexed { i, p ->
                    val on = p.id in picked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(groupShape(i, list.size))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .toggleable(on, role = Role.Checkbox) { picked = if (on) picked - p.id else picked + p.id }
                            .padding(PickPad),
                        horizontalArrangement = Arrangement.spacedBy(Space.M),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Portrait corners concentric to the row's own (grouped) corners.
                        val thumb = RoundedCornerShape(
                            topStart = concentric(if (i == 0) 16.dp else 4.dp, PickPad),
                            topEnd = concentric(4.dp, PickPad),
                            bottomEnd = concentric(4.dp, PickPad),
                            bottomStart = concentric(if (i == list.size - 1) 16.dp else 4.dp, PickPad),
                        )
                        StashImage(p.thumbnail?.sized(300), Modifier.size(40.dp, 60.dp).clip(thumb), p.name)
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            (p.asName?.let { stringResource(R.string.detail_as, it) } ?: p.disambiguation)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Checkbox(on, null)
                    }
                }
            }
        }
        ActionButton(
            pluralStringResource(R.plurals.swipe_hide_n, picked.size, picked.size),
            { onPick(performers.orEmpty().filter { it.id in picked }) },
            Modifier.fillMaxWidth().padding(top = Space.S),
            enabled = picked.isNotEmpty(),
        )
    }
}

private val PickPad = 8.dp

/**
 * Which scenes a list shows: not hidden (scene id or a performer id), and with the gender setting ([gender], a GenderEnum name;
 * null = both) at least one performer of that gender. Scenes without listed performers always show.
 */
data class SceneVisibility(val hidden: HiddenIds, val gender: String?) {
    fun visible(card: SceneCard): Boolean = !hidden.hides(card.id, card.performerIds) &&
        (gender == null || card.performerIds.isEmpty() || card.performerGenders.any { it.equals(gender, ignoreCase = true) })
}

/**
 * The loader's state filtered by [SceneVisibility] ([keepPerformer] is never hidden, e.g. on that performer's own page).
 * Tops up with the next page while few scenes are left visible, so a page of hidden scenes never ends the list.
 */
@Composable
fun PageLoader<SceneCard>.collectVisible(keepPerformer: String? = null): PagedState<SceneCard> {
    val raw by state.collectAsState()
    val c = LocalAppContainer.current
    val all by c.hiddenIds.collectAsState()
    val gender by c.sceneGender.collectAsState()
    val rule = SceneVisibility(if (keepPerformer == null) all else all.copy(performers = all.performers - keepPerformer), gender)
    val items = remember(raw.items, rule) { raw.items.filter(rule::visible) }
    // shortcut: tops up only while fewer than 12 scenes are visible; a fully hidden page deeper down waits for the next scroll.
    val starving = items.size < 12 && items.size < raw.items.size && raw.hasNext && raw.error == null
    LaunchedEffect(starving, raw.loading, raw.items.size) { if (starving && !raw.loading) loadMore() }
    return raw.copy(items = items, loading = raw.loading || starving)
}
