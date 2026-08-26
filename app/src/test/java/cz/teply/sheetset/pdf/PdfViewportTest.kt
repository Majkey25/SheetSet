package cz.teply.sheetset.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfViewportTest {
    @Test
    fun `half page uses exact top and bottom pan`() {
        assertEquals(200f, halfPagePan(maxPanY = 200f, part = 0))
        assertEquals(-200f, halfPagePan(maxPanY = 200f, part = 1))
    }

    @Test
    fun pinchKeepsTheContentUnderTheFocusPointStable() {
        val before = PdfViewport(zoom = 1f, panX = 0f, panY = 0f)

        val after = before.scaledAround(factor = 2f, focusX = 300f, focusY = 500f)

        assertEquals(2f, after.zoom)
        assertEquals(-300f, after.panX, 0.001f)
        assertEquals(-500f, after.panY, 0.001f)
    }

    @Test
    fun movingPinchKeepsTheOriginalContentUnderTheMovingFocus() {
        val before = PdfViewport(zoom = 1f, panX = 0f, panY = 0f)

        val after = before.scaledAndMoved(
            factor = 2f,
            previousFocusX = 100f,
            previousFocusY = 200f,
            focusX = 120f,
            focusY = 230f,
        )

        assertEquals(2f, after.zoom)
        assertEquals(-80f, after.panX, 0.001f)
        assertEquals(-170f, after.panY, 0.001f)
    }
}
