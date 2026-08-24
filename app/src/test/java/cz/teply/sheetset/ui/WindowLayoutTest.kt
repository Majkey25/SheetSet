package cz.teply.sheetset.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowLayoutTest {
    @Test
    fun widthBreakpointsAreStable() {
        assertEquals(WindowLayout.COMPACT, WindowLayout.fromWidth(599.dp))
        assertEquals(WindowLayout.MEDIUM, WindowLayout.fromWidth(600.dp))
        assertEquals(WindowLayout.MEDIUM, WindowLayout.fromWidth(839.dp))
        assertEquals(WindowLayout.EXPANDED, WindowLayout.fromWidth(840.dp))
    }
}
