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
}
