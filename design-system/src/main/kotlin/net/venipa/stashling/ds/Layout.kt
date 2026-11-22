package net.venipa.stashling.ds

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Medicatore layout pieces (DESIGN.md): tonal surfaces, no borders, 16dp gutters, 56dp actions.
object Space {
    val Xs = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val Xl = 24.dp
}

val Gutter = Modifier.padding(horizontal = Space.L)
val ReadableWidth = 600.dp

fun Modifier.readableWidth() = fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = ReadableWidth).fillMaxWidth()

/** A scrolling page body: the Scaffold [padding], 16dp above, 16dp between sections. */
@Composable
fun ScreenColumn(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    bottom: Dp = Space.L,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(top = Space.L, bottom = bottom).readableWidth(),
    verticalArrangement = Arrangement.spacedBy(Space.L),
    content = content,
)

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) = Box(modifier.padding(vertical = Space.Xs), contentAlignment = Alignment.BottomStart) {
    Text(text, Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
}

enum class CardTone { Plain, Now, Error }

/** A filled tonal card; with [onClick] the whole card is one button. */
@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Plain,
    hero: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        when (tone) {
            CardTone.Plain -> scheme.surfaceContainer
            CardTone.Now -> scheme.primaryContainer
            CardTone.Error -> scheme.errorContainer
        },
        stateSpec(),
        label = "cardContainer",
    )
    val onContainer =
        when (tone) {
            CardTone.Now -> scheme.onPrimaryContainer
            CardTone.Error -> scheme.onErrorContainer
            else -> scheme.onSurface
        }
    val shape = if (hero) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large
    val colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(contentPadding ?: PaddingValues(if (hero) 20.dp else 16.dp)), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
    if (onClick != null) {
        Card(onClick, modifier.fillMaxWidth().semantics { role = Role.Button }, shape = shape, colors = colors, content = body)
    } else {
        Card(modifier.fillMaxWidth(), shape = shape, colors = colors, content = body)
    }
}

/** Rows 2dp apart inside one large shape. */
@Composable
fun TonalGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Column(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)

/** A row of a [TonalGroup]: optional leading icon circle, headline, supporting line, [trailing] (value, switch) or a chevron when clickable. */
@Composable
fun GroupRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(headline) },
        modifier = modifier.clip(MaterialTheme.shapes.extraSmall).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = icon?.let {
            {
                Box(Modifier.size(40.dp).background(cs.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(it, null, tint = cs.onSecondaryContainer)
                }
            }
        },
        trailingContent = trailing ?: onClick?.let { { Icon(StashlingIcons.ChevronRight, null, tint = cs.onSurfaceVariant) } },
        colors = ListItemDefaults.colors(containerColor = cs.surfaceContainer),
    )
}

/** The 56dp primary action; [tonal] for the secondary one. */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tonal: Boolean = false,
    icon: ImageVector? = null,
) {
    val content: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, Modifier.size(20.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
    val m = modifier.fillMaxWidth().heightIn(min = 56.dp)
    if (tonal) FilledTonalButton(onClick, m, enabled) { content() } else Button(onClick, m, enabled) { content() }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) = Column(
    modifier.fillMaxWidth().padding(Space.Xl),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Space.S),
) {
    Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
    action?.invoke()
}

enum class PillTone { Ok, Neutral, Warn, Error }

/** A state as a small label; the text is the state, tone and icon only repeat it. */
@Composable
fun StatusPill(
    text: String,
    tone: PillTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val cs = MaterialTheme.colorScheme
    val (container, content) =
        when (tone) {
            PillTone.Ok -> cs.primaryContainer to cs.onPrimaryContainer
            PillTone.Neutral -> cs.surfaceContainerHighest to cs.onSurfaceVariant
            PillTone.Warn -> cs.tertiaryContainer to cs.onTertiaryContainer
            PillTone.Error -> cs.errorContainer to cs.onErrorContainer
        }
    Row(
        modifier.heightIn(min = 24.dp).background(container, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, Modifier.size(16.dp), content) }
        Text(text, color = content, style = MaterialTheme.typography.labelMedium)
    }
}
