package net.venipa.stashling.feature.common

import kotlinx.coroutines.test.runTest
import net.venipa.stashling.core.stash.Page
import net.venipa.stashling.ds.indexLetter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SeekLetterTest {
    // 260 names, 10 per letter A–Z, server NAME ASC order; 40 per page = 7 pages.
    private val names = ('A'..'Z').flatMap { l -> (0 until 10).map { "$l$it" } }

    private fun loader(scope: kotlinx.coroutines.CoroutineScope, calls: MutableList<Int>) = PageLoader<String>(scope, { it }) { p ->
        calls += p
        Page(names.drop((p - 1) * 40).take(40), p, 40, names.size)
    }

    @Test
    fun jumpsToTheFirstItemOfTheLetter() = runTest {
        val calls = mutableListOf<Int>()
        val l = loader(this, calls)
        for (letter in listOf('A', 'D', 'E', 'M', 'Z')) {
            val i = l.seekLetter(letter) { it }
            assertEquals("${letter}0", l.state.value.items[i], "letter $letter")
            assertEquals(l.state.value.items.first() != "A0", l.state.value.hasPrevious)
        }
        // '#' goes to page 1.
        assertEquals(0, l.seekLetter('#') { it })
        assertEquals("A0", l.state.value.items.first())
    }

    @Test
    fun loadPreviousPrepends() = runTest {
        val l = loader(this, mutableListOf())
        l.seekLetter('M') { it }
        val firstBefore = l.state.value.items.first()
        l.loadPrevious()
        testScheduler.advanceUntilIdle()
        assertEquals(80, l.state.value.items.size)
        assertEquals(firstBefore, l.state.value.items[40])
    }

    @Test
    fun ranksAndAliasLetters() {
        assertEquals(0, seekRank("2 Chainz"))
        assertEquals(5, seekRank("Élodie"))
        assertEquals(27, seekRank("三上悠亜"))
        assertEquals('Y', indexLetter("三上悠亜", listOf("三上", "Yua Mikami")))
        assertEquals('#', indexLetter("三上悠亜", emptyList()))
        assertEquals('A', indexLetter("anna", listOf("Bea")))
    }
}
