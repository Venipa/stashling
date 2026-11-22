package net.venipa.stashling.feature.common

import net.venipa.stashling.core.data.HiddenIds
import net.venipa.stashling.core.stash.SceneCard
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneVisibilityTest {
    private fun card(id: String, vararg p: Pair<String, String?>) = SceneCard(id, null, null, null, null, null, p.map { it.first }, p.map { it.first }, p.map { it.second })

    @Test
    fun genderNeedsOneMatchingPerformerAndKeepsUnlisted() {
        val female = SceneVisibility(HiddenIds(), "FEMALE")
        assertTrue(female.visible(card("a", "p1" to "MALE", "p2" to "FEMALE")))
        assertFalse(female.visible(card("b", "p1" to "MALE", "p3" to null)))
        assertTrue(female.visible(card("c")))
        assertTrue(SceneVisibility(HiddenIds(), null).visible(card("d", "p1" to "MALE")))
    }

    @Test
    fun hiddenSceneOrPerformerWins() {
        val rule = SceneVisibility(HiddenIds(scenes = setOf("a"), performers = setOf("p2")), "FEMALE")
        assertFalse(rule.visible(card("a", "p1" to "FEMALE")))
        assertFalse(rule.visible(card("b", "p2" to "FEMALE")))
        assertTrue(rule.visible(card("c", "p1" to "FEMALE")))
    }
}
