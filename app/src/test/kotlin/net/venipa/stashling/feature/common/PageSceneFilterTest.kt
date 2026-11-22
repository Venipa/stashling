package net.venipa.stashling.feature.common

import net.venipa.stashling.core.stash.SceneFilter
import net.venipa.stashling.core.stash.SceneSort
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PageSceneFilterTest {
    @Test
    fun scopeWinsAndUserFiltersStack() {
        val f = PageSceneFilter(SceneFilter(studioIds = listOf("other"), tagIds = listOf("t2", "t1"), title = "  x ", sort = SceneSort.TITLE), Region.Jav)
        val out = f.build(SceneFilter(studioIds = listOf("s"), tagIds = listOf("t1")), jav = "jav")
        assertEquals(listOf("s"), out.studioIds)
        assertEquals(listOf("t1", "t2", "jav"), out.tagIds)
        assertEquals("x", out.title)
        assertEquals(SceneSort.TITLE, out.sort)
    }

    @Test
    fun activeCount() {
        assertEquals(0, PageSceneFilter().activeCount())
        assertEquals(3, PageSceneFilter(SceneFilter(title = "a", tagIds = listOf("t")), subStudios = false).activeCount())
    }
}
