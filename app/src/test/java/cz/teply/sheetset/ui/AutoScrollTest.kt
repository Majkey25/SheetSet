package cz.teply.sheetset.ui

import cz.teply.sheetset.settings.AutoScrollSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AutoScrollTest {
    @Test
    fun `scroll speed maps to bounded pixels per second`() {
        assertEquals(24f, autoScrollPixels(AutoScrollSpeed.SLOW, 1_000, density = 1f))
        assertEquals(48f, autoScrollPixels(AutoScrollSpeed.MEDIUM, 1_000, density = 1f))
        assertEquals(96f, autoScrollPixels(AutoScrollSpeed.FAST, 1_000, density = 1f))
        assertEquals(48f, autoScrollPixels(AutoScrollSpeed.MEDIUM, 500, density = 2f))
    }

    @Test
    fun `invalid timing input is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            autoScrollPixels(AutoScrollSpeed.MEDIUM, -1, density = 1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            autoScrollPixels(AutoScrollSpeed.MEDIUM, 1_000, density = 0f)
        }
    }
}
