package net.venipa.stashling.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.data.StashAccount
import net.venipa.stashling.core.data.WhisparrAccount
import net.venipa.stashling.core.data.WhisparrDefaults
import net.venipa.stashling.core.whisparr.QualityProfile
import net.venipa.stashling.core.whisparr.RootFolder
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.GroupRow
import net.venipa.stashling.ds.LocalReduceMotion
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.TonalGroup
import net.venipa.stashling.ds.TransitionSpecs
import net.venipa.stashling.ds.readableWidth
import net.venipa.stashling.ds.sharedAxisXEnter
import net.venipa.stashling.ds.sharedAxisXExit
import net.venipa.stashling.ds.stepBackPreview
import net.venipa.stashling.ds.stepBackProgress

/** What the steps collect. Lives only in memory: keys and passwords never reach saved state, so a killed process restarts at the first ask. */
class OnboardingState {
    var stash by mutableStateOf<StashAccount?>(null)
    var stashName by mutableStateOf<String?>(null)
    var whisparr by mutableStateOf<WhisparrAccount?>(null)
    var roots by mutableStateOf<List<RootFolder>>(emptyList())
    var profiles by mutableStateOf<List<QualityProfile>>(emptyList())
    var defaults by mutableStateOf<WhisparrDefaults?>(null)
}

private const val WELCOME = 0
private const val STASH = 1
private const val WHISPARR = 2
private const val DONE = 3

/**
 * Steps: 0 welcome, 1 StashDB (required), 2 Whisparr (optional), 3 done. [only] = 1 or 2 re-runs that single step from
 * Settings and commits it on its own; [onFinished] is called after the accounts are stored.
 */
@Composable
fun OnboardingScreen(
    only: Int,
    onFinished: () -> Unit,
    onExit: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val state = remember { OnboardingState() }
    val first = if (only > 0) only else WELCOME
    var step by rememberSaveable { mutableIntStateOf(first) }
    // After process death the in-memory account is gone: restart at the StashDB ask.
    LaunchedEffect(Unit) { if (only < 0 && step > STASH && state.stash == null) step = STASH }

    fun commit() {
        scope.launch {
            state.whisparr?.let { acc ->
                container.secrets.setWhisparr(acc)
                container.settings.update { s -> s.copy(whisparr = state.defaults ?: s.whisparr, historyCursor = System.currentTimeMillis()) }
            }
            state.stash?.let { container.secrets.setStash(it) }
            onFinished()
        }
    }

    // The last step of the flow commits; a re-run commits after its one step.
    fun next() {
        if (only > 0) {
            commit()
        } else if (step == DONE) {
            commit()
        } else {
            step++
        }
    }
    val canStepBack = only < 0 && step > WELCOME
    val progress = stepBackProgress(canStepBack) { step-- }
    BackHandler(enabled = !canStepBack) { onExit() }

    val reduce = LocalReduceMotion.current
    val offset = with(LocalDensity.current) { TransitionSpecs.SHARED_AXIS_OFFSET.roundToPx() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (only < 0) StepDots(step)
            AnimatedContent(
                step,
                Modifier.weight(1f).stepBackPreview(progress),
                transitionSpec = {
                    val forward = targetState > initialState
                    val enter = sharedAxisXEnter(if (forward) offset else -offset, reduce)
                    val exit = sharedAxisXExit(if (forward) offset else -offset, reduce)
                    enter togetherWith exit
                },
                label = "onboardingStep",
            ) { s ->
                when (s) {
                    WELCOME -> WelcomeStep(onNext = ::next)

                    STASH -> StashStep(state, finishLabel = if (only > 0) stringResource(R.string.onboarding_save) else null, onNext = ::next)

                    WHISPARR ->
                        WhisparrStep(
                            state,
                            finishLabel = if (only > 0) stringResource(R.string.onboarding_save) else null,
                            onNext = ::next,
                            onSkip = { if (only > 0) onExit() else step = DONE },
                        )

                    else -> DoneStep(state, onNext = ::next)
                }
            }
        }
    }
}

@Composable
private fun StepDots(step: Int) {
    Row(Modifier.fillMaxWidth().padding(top = Space.L), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.CenterHorizontally)) {
        repeat(4) {
            val on = it == step
            Row(Modifier.size(if (on) 24.dp else 8.dp, 8.dp).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)) {}
        }
    }
}

/** One ask per screen: scrolling [content] and a pinned action area that rides above the keyboard. */
@Composable
internal fun StepScaffold(
    title: String,
    body: String?,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.L, vertical = Space.Xl).readableWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.L),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            body?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.L, vertical = Space.M).readableWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.S),
            content = actions,
        )
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) = StepScaffold(
    stringResource(R.string.onboarding_welcome_title),
    stringResource(R.string.onboarding_welcome_body),
    actions = { ActionButton(stringResource(R.string.onboarding_get_started), onNext) },
) {
    Text(stringResource(R.string.onboarding_keys_local), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DoneStep(
    state: OnboardingState,
    onNext: () -> Unit,
) = StepScaffold(
    stringResource(R.string.onboarding_done_title),
    stringResource(R.string.onboarding_done_body),
    actions = { ActionButton(stringResource(R.string.onboarding_open), onNext) },
) {
    TonalGroup {
        GroupRow(
            stringResource(R.string.onboarding_stashdb),
            supporting = state.stashName?.let { stringResource(R.string.onboarding_signed_in_as, it) } ?: stringResource(R.string.onboarding_connected),
        )
        GroupRow(
            stringResource(R.string.onboarding_whisparr),
            supporting = state.whisparr?.baseUrl ?: stringResource(R.string.onboarding_not_connected),
        )
    }
    if (state.whisparr == null) Text(stringResource(R.string.onboarding_whisparr_later), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
