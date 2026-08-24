package cz.teply.sheetset.ui

import android.view.KeyEvent
import cz.teply.sheetset.settings.ReaderLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderNavigationTest {
    @Test
    fun `single pages cross score boundaries`() {
        val counts = listOf(2, 3)

        assertEquals(
            ReaderPosition(0, 1),
            nextPosition(ReaderPosition(0, 0), counts, ReaderLayout.SINGLE),
        )
        assertEquals(
            ReaderPosition(1, 0),
            nextPosition(ReaderPosition(0, 1), counts, ReaderLayout.SINGLE),
        )
        assertEquals(
            ReaderPosition(0, 1),
            previousPosition(ReaderPosition(1, 0), counts, ReaderLayout.SINGLE),
        )
        assertNull(previousPosition(ReaderPosition(0, 0), counts, ReaderLayout.SINGLE))
        assertNull(nextPosition(ReaderPosition(1, 2), counts, ReaderLayout.SINGLE))
    }

    @Test
    fun `half pages advance top bottom and score`() {
        val counts = listOf(2, 1)

        assertEquals(
            ReaderPosition(0, 0, 1),
            nextPosition(ReaderPosition(0, 0, 0), counts, ReaderLayout.HALF),
        )
        assertEquals(
            ReaderPosition(0, 1, 0),
            nextPosition(ReaderPosition(0, 0, 1), counts, ReaderLayout.HALF),
        )
        assertEquals(
            ReaderPosition(1, 0, 0),
            nextPosition(ReaderPosition(0, 1, 1), counts, ReaderLayout.HALF),
        )
        assertEquals(
            ReaderPosition(0, 1, 1),
            previousPosition(ReaderPosition(1, 0, 0), counts, ReaderLayout.HALF),
        )
    }

    @Test
    fun `two page spreads handle odd final page`() {
        val counts = listOf(5, 2)

        assertEquals(listOf(2, 3), spreadPages(ReaderPosition(0, 3), 5))
        assertEquals(listOf(4), spreadPages(ReaderPosition(0, 4), 5))
        assertEquals(
            ReaderPosition(0, 4),
            nextPosition(ReaderPosition(0, 2), counts, ReaderLayout.TWO_PAGE),
        )
        assertEquals(
            ReaderPosition(1, 0),
            nextPosition(ReaderPosition(0, 4), counts, ReaderLayout.TWO_PAGE),
        )
        assertEquals(
            ReaderPosition(0, 4),
            previousPosition(ReaderPosition(1, 0), counts, ReaderLayout.TWO_PAGE),
        )
    }

    @Test
    fun `duplicate score occurrences keep their index`() {
        val counts = listOf(2, 2, 2)

        assertEquals(
            ReaderPosition(1, 0),
            nextPosition(ReaderPosition(0, 1), counts, ReaderLayout.SINGLE),
        )
        assertEquals(
            ReaderPosition(2, 0),
            nextPosition(ReaderPosition(1, 1), counts, ReaderLayout.SINGLE),
        )
    }

    @Test
    fun `pedal keys use strict page direction allowlist`() {
        listOf(
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_SPACE,
        ).forEach { key -> assertEquals(PageDirection.PREVIOUS, pedalDirection(key, 0)) }
        listOf(
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
        ).forEach { key -> assertEquals(PageDirection.NEXT, pedalDirection(key, 0)) }
        assertNull(pedalDirection(KeyEvent.KEYCODE_VOLUME_UP, 0))
        assertNull(pedalDirection(KeyEvent.KEYCODE_MEDIA_PLAY, 0))
        assertNull(pedalDirection(KeyEvent.KEYCODE_PAGE_DOWN, 1))
    }

    @Test
    fun `two page layout falls back only on compact windows`() {
        assertEquals(
            ReaderLayout.SINGLE,
            effectiveReaderLayout(ReaderLayout.TWO_PAGE, supportsTwoPage = false),
        )
        assertEquals(
            ReaderLayout.TWO_PAGE,
            effectiveReaderLayout(ReaderLayout.TWO_PAGE, supportsTwoPage = true),
        )
        assertEquals(
            ReaderLayout.HALF,
            effectiveReaderLayout(ReaderLayout.HALF, supportsTwoPage = false),
        )
    }
}
