package net.venipa.stashling.feature.common

import net.venipa.stashling.ds.indexLetter
import net.venipa.stashling.ds.letterPositions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AlphabetIndexTest {
    @Test
    fun lettersFoldDiacriticsAndBucketTheRest() {
        assertEquals('E', indexLetter("Élodie"))
        assertEquals('A', indexLetter("  anal"))
        assertEquals('#', indexLetter("69"))
        assertEquals('#', indexLetter("巨乳"))
        assertEquals('#', indexLetter(""))
    }

    @Test
    fun positionsKeepFirstIndexPerLetterPlusOffset() {
        assertEquals(mapOf('#' to 2, 'A' to 3, 'B' to 5), letterPositions(listOf("1x", "Ab", "ac", "Bo"), offset = 2) { it })
    }
}
