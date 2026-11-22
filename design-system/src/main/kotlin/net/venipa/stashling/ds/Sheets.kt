package net.venipa.stashling.ds

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The app's modal bottom sheet. Material's own back handling scales the sheet and cannot be switched off, so it is disabled
 * and the sheet slides down and dims with the gesture instead (medicatore AppBottomSheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val back = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.sheetBackPreview { back.value },
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        PredictiveBackHandler { events ->
            try {
                events.collect { back.snapTo(it.progress) }
            } catch (e: CancellationException) {
                scope.launch { back.animateTo(0f, backSpec()) }
                throw e
            }
            scope.launch { back.animateTo(1f, backSpec()) }
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (sheetState.isVisible) scope.launch { back.animateTo(0f, backSpec()) } else onDismiss()
            }
        }
        content()
    }
}

val sheetTitle: TextStyle @Composable get() = MaterialTheme.typography.headlineSmall

/** Confirmation sheet (tonal): title, body, Cancel + confirm; [destructive] paints the confirm in the error colours. */
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) = AppBottomSheet(onDismiss) {
    Column(Modifier.padding(horizontal = Space.L).padding(bottom = Space.L).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Space.S)) {
        Text(title, style = sheetTitle)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = Space.S), horizontalArrangement = Arrangement.spacedBy(Space.S)) {
            FilledTonalButton(onDismiss, Modifier.weight(1f).size(width = 0.dp, height = 56.dp), shape = concentricShape(SheetRadius, Space.L)) { Text(stringResource(R.string.ds_cancel)) }
            Button(
                onConfirm,
                Modifier.weight(1f).size(width = 0.dp, height = 56.dp),
                shape = concentricShape(SheetRadius, Space.L),
                colors = if (destructive) ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(),
            ) { Text(confirmLabel) }
        }
    }
}

/** Tonal pill snackbar; host as `SnackbarHost(state) { AppSnackbar(it) }`. */
@Composable
fun AppSnackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) = Snackbar(
    modifier = modifier.padding(12.dp),
    action = data.visuals.actionLabel?.let { label ->
        { TextButton(data::performAction, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)) { Text(label) } }
    },
    shape = CircleShape,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
    actionContentColor = MaterialTheme.colorScheme.primary,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (leadingIcon != null) {
            Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(leadingIcon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(data.visuals.message)
    }
}
