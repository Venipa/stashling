package net.venipa.stashling.feature.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.venipa.stashling.AppContainer
import net.venipa.stashling.LocalAppContainer
import net.venipa.stashling.R
import net.venipa.stashling.core.stash.Image
import net.venipa.stashling.ds.GlassIconButton
import net.venipa.stashling.ds.Space
import net.venipa.stashling.ds.StashlingIcons
import net.venipa.stashling.ds.blockPointerPassThrough
import net.venipa.stashling.ds.glassSource
import net.venipa.stashling.ds.rememberGlassState
import net.venipa.stashling.feature.common.Load
import net.venipa.stashling.feature.common.LoadGate
import net.venipa.stashling.feature.common.loadOf

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

class GalleryViewModel(private val kind: String, private val id: String, private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<Load<List<Image>>>(Load.Loading)
    val state: StateFlow<Load<List<Image>>> = _state

    init {
        load()
    }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = loadOf {
                when (kind) {
                    "scene" -> c.stash.scene(id)?.images
                    "performer" -> c.stash.performer(id)?.images
                    else -> c.stash.studio(id)?.images
                }?.takeIf { it.isNotEmpty() }
            }
        }
    }
}

/** Fullscreen gallery: pager of 1280 px images with pinch and double-tap zoom. [kind] is "scene", "performer" or "studio". */
@Composable
fun GalleryScreen(
    kind: String,
    id: String,
    index: Int,
    onBack: () -> Unit,
) {
    val c = LocalAppContainer.current
    val vm = viewModel(key = "gallery-$kind-$id") { GalleryViewModel(kind, id, c) }
    val load by vm.state.collectAsState()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        LoadGate(load, onBack, vm::load) { images -> Pager(images, index.coerceIn(0, images.lastIndex), onBack) }
    }
}

@Composable
private fun Pager(
    images: List<Image>,
    start: Int,
    onBack: () -> Unit,
) {
    val pager = rememberPagerState(start) { images.size }
    val glass = rememberGlassState()
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize().glassSource(glass), beyondViewportPageCount = 1, key = { images[it].id }) { page ->
            ZoomPage(images[page].sized(1280), current = page == pager.settledPage)
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(Space.S), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(glass, StashlingIcons.Back, stringResource(R.string.detail_back), onBack)
            Text(
                stringResource(R.string.gallery_counter, pager.currentPage + 1, images.size),
                Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).blockPointerPassThrough().padding(horizontal = Space.M, vertical = Space.S),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Zoom state is reset when the page leaves; the transform detector only runs while zoomed or with two pointers, so the pager still gets one-finger drags at 1x and at the pan limits. */
@Composable
private fun ZoomPage(
    url: String,
    current: Boolean,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(current) {
        if (!current) {
            scale = 1f
            offset = Offset.Zero
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        val around = tap - Offset(size.width / 2f, size.height / 2f)
                        val max = Offset(size.width * (DOUBLE_TAP_ZOOM - 1f) / 2f, size.height * (DOUBLE_TAP_ZOOM - 1f) / 2f)
                        scale = DOUBLE_TAP_ZOOM
                        offset = Offset((-around.x * (DOUBLE_TAP_ZOOM - 1f)).coerceIn(-max.x, max.x), (-around.y * (DOUBLE_TAP_ZOOM - 1f)).coerceIn(-max.y, max.y))
                    }
                })
            }.pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        val multi = ev.changes.count { it.pressed } > 1
                        if (multi || scale > 1f) {
                            val centroid = ev.calculateCentroid()
                            val around = if (centroid.isSpecified) centroid - Offset(size.width / 2f, size.height / 2f) else Offset.Zero
                            val next = (scale * ev.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            val maxX = size.width * (next - 1f) / 2f
                            val maxY = size.height * (next - 1f) / 2f
                            val raw = (offset - around) * (next / scale) + around + ev.calculatePan()
                            val moved = Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
                            val changed = moved != offset || next != scale
                            scale = next
                            offset = moved
                            // Consume only while the image moves, so the pager gets the drag at the edges.
                            if (multi || changed) ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            url,
            null,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
            contentScale = ContentScale.Fit,
        )
    }
}
