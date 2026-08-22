package cz.teply.sheetset.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationGeometryTest {
    private val stroke = InkAnnotation(
        id = "stroke",
        kind = InkKind.PEN,
        width = 0.004f,
        points = listOf(NormalizedPoint(0.25f, 0.25f), NormalizedPoint(0.75f, 0.75f)),
    )

    @Test
    fun `view point is normalized inside displayed page`() {
        val bounds = PageBounds(left = 100f, top = 50f, width = 200f, height = 400f)

        assertEquals(NormalizedPoint(0.25f, 0.5f), normalizePoint(150f, 250f, bounds))
        assertNull(normalizePoint(99f, 250f, bounds))
    }

    @Test
    fun `eraser removes crossing stroke and undo restores it`() {
        val erased = AnnotationHistory().add(stroke).erase(
            point = NormalizedPoint(0.5f, 0.5f),
            radius = 0.02f,
        )

        assertTrue(erased.annotations.isEmpty())
        assertEquals(listOf(stroke), erased.undo().annotations)
    }

    @Test
    fun `page replacement drops empty page`() {
        val annotations = DocumentAnnotations(mapOf(1 to listOf(stroke)))

        assertTrue(annotations.withPage(1, emptyList()).pages.isEmpty())
        assertEquals(listOf(stroke), DocumentAnnotations().withPage(2, listOf(stroke)).pages[2])
    }
}
