package cz.teply.sheetset.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SetlistReorderTest {
    @Test
    fun dragDistanceCanCrossMultipleRowsAndClampsAtEdges() {
        assertEquals(0, targetIndexForDrag(0, 0f, 100f, 4))
        assertEquals(3, targetIndexForDrag(0, 260f, 100f, 4))
        assertEquals(0, targetIndexForDrag(2, -500f, 100f, 4))
        assertEquals(4, targetIndexForDrag(2, 500f, 100f, 4))
    }

    @Test
    fun occurrenceKeysStayStableAcrossReorderAndDuplicates() {
        assertEquals(listOf("a-0", "b-0", "c-0"), setlistOccurrenceKeys(listOf("a", "b", "c")))
        assertEquals(listOf("b-0", "c-0", "a-0"), setlistOccurrenceKeys(listOf("b", "c", "a")))
        assertEquals(listOf("a-0", "b-0", "a-1"), setlistOccurrenceKeys(listOf("a", "b", "a")))
    }

    @Test
    fun draggingScalesTheWholeRow() {
        assertEquals(1f, setlistDragScale(false))
        assertEquals(0.96f, setlistDragScale(true))
    }
}
