package cz.teply.sheetset.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class LibraryCatalogTest {
    @Test
    fun `blank setlist name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            LibraryCatalog().createSetlist("   ", "set-1")
        }
    }

    @Test
    fun `setlists are unlimited`() {
        val catalog = (1..4).fold(LibraryCatalog()) { current, number ->
            current.createSetlist("Set $number", "set-$number")
        }

        assertEquals(4, catalog.setlists.size)
    }

    @Test
    fun `deleting score removes every setlist reference`() {
        val score = Score("score-1", "Song", "score-1.pdf", 2, 1L)
        val catalog = LibraryCatalog(
            scores = listOf(score),
            setlists = listOf(
                Setlist("set-1", "First", listOf(score.id)),
                Setlist("set-2", "Second", listOf(score.id, score.id)),
            ),
        )

        val updated = catalog.deleteScore(score.id)

        assertFalse(updated.scores.any { it.id == score.id })
        assertFalse(updated.setlists.any { score.id in it.scoreIds })
    }

    @Test
    fun `move score changes only valid positions`() {
        val original = LibraryCatalog(
            setlists = listOf(Setlist("set-1", "Show", listOf("a", "b", "c"))),
        )

        assertEquals(
            listOf("b", "a", "c"),
            original.moveScore("set-1", fromIndex = 0, toIndex = 1)
                .setlists.single().scoreIds,
        )
        assertEquals(original, original.moveScore("set-1", fromIndex = -1, toIndex = 0))
        assertEquals(original, original.moveScore("missing", fromIndex = 0, toIndex = 1))
    }
}
