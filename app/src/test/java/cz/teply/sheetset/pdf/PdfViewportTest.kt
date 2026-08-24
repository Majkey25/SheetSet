package cz.teply.sheetset.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfViewportTest {
    @Test
    fun `half page uses exact top and bottom pan`() {
        assertEquals(200f, halfPagePan(maxPanY = 200f, part = 0))
        assertEquals(-200f, halfPagePan(maxPanY = 200f, part = 1))
    }

    @Test
    fun `scrolling clamps at page bottom`() {
        val middle = scrollPan(currentPanY = 200f, maxPanY = 200f, pixels = 50f)
        val bottom = scrollPan(currentPanY = -180f, maxPanY = 200f, pixels = 50f)

        assertEquals(150f, middle.panY)
        assertFalse(middle.reachedEnd)
        assertEquals(-200f, bottom.panY)
        assertTrue(bottom.reachedEnd)
    }

    @Test
    fun `page without overflow is already at scroll end`() {
        assertEquals(ViewportScroll(0f, true), scrollPan(0f, 0f, 20f))
    }
}
