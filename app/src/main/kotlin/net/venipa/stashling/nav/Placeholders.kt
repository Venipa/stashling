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

@Composable fun HomePlaceholder() = Placeholder("Home")

@Composable fun BrowsePlaceholder() = Placeholder("Browse")

@Composable fun SavedPlaceholder() = Placeholder("Saved")

@Composable fun DownloadsPlaceholder() = Placeholder("Downloads")

@Composable fun SceneDetailPlaceholder(id: String) = Placeholder("Scene $id")

@Composable fun PerformerDetailPlaceholder(id: String) = Placeholder("Performer $id")

@Composable fun StudioDetailPlaceholder(id: String) = Placeholder("Studio $id")

@Composable fun TagDetailPlaceholder(id: String) = Placeholder("Tag $id")

@Composable fun GalleryPlaceholder(route: GalleryRoute) = Placeholder("Gallery ${route.kind} ${route.id} #${route.index}")
