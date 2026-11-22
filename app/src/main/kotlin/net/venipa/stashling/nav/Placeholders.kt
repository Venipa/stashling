package net.venipa.stashling.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun Placeholder(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.titleMedium) }
}
