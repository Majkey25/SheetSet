package cz.teply.sheetset.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
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

    @Test
    fun rectangleTranslationClampsTheWholeObject() {
        val rectangle = ShapeAnnotation(
            id = "shape-1",
            kind = ShapeKind.RECTANGLE,
            start = NormalizedPoint(0.8f, 0.8f),
            end = NormalizedPoint(1f, 1f),
            width = 0.004f,
        )

        assertEquals(rectangle, rectangle.translated(0.4f, 0.4f))
    }

    @Test
    fun reverseIterationReturnsTopmostHit() {
        val bottom = ShapeAnnotation(
            id = "bottom",
            kind = ShapeKind.RECTANGLE,
            start = NormalizedPoint(0.1f, 0.1f),
            end = NormalizedPoint(0.9f, 0.9f),
            width = 0.004f,
        )
        val top = TextBoxAnnotation(
            id = "top",
            bounds = NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f),
            text = "Top",
            size = cz.teply.sheetset.settings.AnnotationTextSize.MEDIUM,
        )

        assertSame(top, listOf(bottom, top).topmostHit(NormalizedPoint(0.5f, 0.5f), 0.01f))
    }

    @Test
    fun resizeClampsToMinimumSizeAndManualMarkupAcceptsReverseDrag() {
        val rectangle = ShapeAnnotation(
            id = "shape-1",
            kind = ShapeKind.RECTANGLE,
            start = NormalizedPoint(0.2f, 0.2f),
            end = NormalizedPoint(0.8f, 0.8f),
            width = 0.004f,
        )
        val resized = rectangle.resized(
            NormalizedPoint(0.5f, 0.5f),
            NormalizedPoint(0.501f, 0.501f),
        ) as ShapeAnnotation

        assertEquals(0.01f, resized.end.x - resized.start.x, 0.0001f)
        assertEquals(0.01f, resized.end.y - resized.start.y, 0.0001f)
        assertEquals(
            listOf(NormalizedRect(0.2f, 0.3f, 0.8f, 0.9f)),
            manualMarkup(NormalizedPoint(0.8f, 0.9f), NormalizedPoint(0.2f, 0.3f)),
        )
    }
}
