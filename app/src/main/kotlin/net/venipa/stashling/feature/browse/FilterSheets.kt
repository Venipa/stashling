package net.venipa.stashling.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import net.venipa.stashling.R
import net.venipa.stashling.core.stash.DateBound
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.AppBottomSheet
import net.venipa.stashling.ds.SectionHeader
import net.venipa.stashling.ds.SkeletonText
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashSegmented
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.fieldColors
import net.venipa.stashling.ds.fieldShape
import net.venipa.stashling.ds.sheetTitle
import net.venipa.stashling.ds.stableFieldHeight
import net.venipa.stashling.feature.common.groupShape
import java.time.LocalDate

// Sheet content sits 16 dp inside the 28 dp sheet corner, so grouped rows use a 12 dp outer radius (concentric), 4 dp where rows join.
private val SheetGroupOuter = 12.dp

@Composable
private fun SheetColumn(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) = Column(Modifier.padding(horizontal = Space.L).padding(bottom = Space.L).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Space.S)) {
    Text(title, style = sheetTitle)
    content()
}

@Composable
private fun SheetRow(
    label: String,
    supporting: String?,
    selected: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) = Row(
    Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick).padding(horizontal = Space.L, vertical = Space.M),
    verticalAlignment = Alignment.CenterVertically,
) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (selected) Icon(StashlingIcons.Check, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
}

/** Single choice; [onDirection] adds the Ascending | Descending switch (sort sheets). */
@Composable
fun OptionsSheet(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    desc: Boolean = true,
    onDirection: ((Boolean) -> Unit)? = null,
) = AppBottomSheet(onDismiss) {
    SheetColumn(title) {
        onDirection?.let {
            StashSegmented(listOf(stringResource(R.string.browse_ascending), stringResource(R.string.browse_descending)), if (desc) 1 else 0, { i -> it(i == 1) })
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEachIndexed { i, o -> SheetRow(o, null, i == selected, groupShape(i, options.size, SheetGroupOuter)) { onSelect(i) } }
        }
    }
}

data class PickItem(val id: String, val title: String, val subtitle: String? = null, val group: String? = null)

private sealed interface PickResult {
    data object Loading : PickResult

    data object Failed : PickResult

    data class Items(val items: List<PickItem>) : PickResult
}

/** Search field plus results; [search] runs 400 ms after typing (and for [searchKey] changes). Results with a group are listed under group headers. */
@Composable
fun PickerSheet(
    title: String,
    selected: Set<String>,
    search: suspend (String) -> List<PickItem>,
    onToggle: (PickItem) -> Unit,
    onDismiss: () -> Unit,
    searchKey: Any? = null,
    header: @Composable () -> Unit = {},
) = AppBottomSheet(onDismiss) {
    var q by remember { mutableStateOf("") }
    val result by produceState<PickResult>(PickResult.Loading, q, searchKey) {
        value = PickResult.Loading
        if (q.isNotBlank()) delay(400)
        value = try {
            PickResult.Items(search(q.trim()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PickResult.Failed
        }
    }
    SheetColumn(title) {
        TextField(q, { q = it }, Modifier.fillMaxWidth().stableFieldHeight(), singleLine = true, placeholder = { Text(stringResource(R.string.browse_search_hint)) }, shape = fieldShape(), colors = fieldColors())
        header()
        when (val r = result) {
            PickResult.Loading -> SkeletonText(Modifier.padding(vertical = Space.M), 3)

            PickResult.Failed -> Text(stringResource(R.string.browse_error_generic), color = MaterialTheme.colorScheme.error)

            is PickResult.Items -> LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val groups = r.items.groupBy { it.group }
                groups.forEach { (g, items) ->
                    if (g != null) item(key = "g:$g") { SectionHeader(g, Modifier.padding(top = Space.S)) }
                    itemsIndexed(items, key = { _, it -> it.id }) { i, it -> SheetRow(it.title, it.subtitle, it.id in selected, groupShape(i, items.size, SheetGroupOuter)) { onToggle(it) } }
                }
                if (r.items.isEmpty()) item { Text(stringResource(R.string.browse_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** One date bound (stash-box takes no range): Before | After plus YYYY-MM-DD. */
@Composable
fun DateSheet(
    current: DateBound?,
    onApply: (DateBound?) -> Unit,
    onDismiss: () -> Unit,
) = AppBottomSheet(onDismiss) {
    var before by remember { mutableStateOf(current?.before ?: false) }
    var text by remember { mutableStateOf(current?.date ?: "") }
    val valid = runCatching { LocalDate.parse(text) }.isSuccess
    SheetColumn(stringResource(R.string.browse_filter_date)) {
        StashSegmented(listOf(stringResource(R.string.browse_date_after), stringResource(R.string.browse_date_before)), if (before) 1 else 0, { before = it == 1 })
        TextField(
            text,
            { text = it },
            Modifier.fillMaxWidth().stableFieldHeight(),
            singleLine = true,
            label = { Text(stringResource(R.string.browse_date_hint)) },
            isError = text.isNotEmpty() && !valid,
            shape = fieldShape(),
            colors = fieldColors(),
        )
        ActionButton(stringResource(R.string.browse_apply), { onApply(DateBound(text, before)) }, enabled = valid)
        if (current != null) ActionButton(stringResource(R.string.browse_clear), { onApply(null) }, tonal = true)
    }
}
