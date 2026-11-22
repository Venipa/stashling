package net.venipa.stashling.ds

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** 48dp glass circle for floating chrome (back, heart, download). */
@Composable
fun GlassIconButton(
    glass: GlassState,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    iconScale: Float = 1f,
) {
    IconButton(onClick, modifier.size(48.dp).glass(glass, GlassRole.Button)) {
        Icon(
            icon,
            contentDescription,
            Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            },
            tint = tint.takeOrElse { MaterialTheme.colorScheme.onSurface },
        )
    }
}
