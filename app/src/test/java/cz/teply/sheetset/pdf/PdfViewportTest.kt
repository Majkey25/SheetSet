package cz.teply.sheetset.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfViewportTest {
    @Test
    fun `half page uses exact top and bottom pan`() {
        assertEquals(200f, halfPagePan(maxPanY = 200f, part = 0))
        assertEquals(-200f, halfPagePan(maxPanY = 200f, part = 1))
    }
}
