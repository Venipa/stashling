package net.venipa.stashling.ds

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Compile-only proof that the M3 Expressive APIs the plan relies on exist in the BOM's material3.
 * Not called anywhere; delete once real components use these. The loading indicators still need the opt-in.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveApiCheck() {
    LoadingIndicator()
    ContainedLoadingIndicator()
    Button(onClick = {}, shapes = ButtonDefaults.shapes()) { Text("Add") }
    ButtonGroup(overflowIndicator = {}) {
        clickableItem(onClick = {}, label = "Search")
    }
}
