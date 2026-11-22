package net.venipa.stashling.feature.common

import kotlin.test.Test
import kotlin.test.assertEquals

class DragSelectionTest {
    private val keys = listOf("a", "b", "c", "d", "e")

    @Test
    fun forwardBackwardAndShrink() {
        assertEquals(setOf("b"), dragSelection(emptySet(), keys, 1, 1))
        assertEquals(setOf("b", "c", "d"), dragSelection(emptySet(), keys, 1, 3))
        assertEquals(setOf("a", "b"), dragSelection(emptySet(), keys, 1, 0))
        // Dragging back toward the anchor drops what the drag added, but never what was selected before it.
        assertEquals(setOf("e", "b"), dragSelection(setOf("e"), keys, 1, 1))
    }
}
