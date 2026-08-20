package cz.teply.sheetset.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationHistoryTest {
    private val first = Stroke(
        tool = AnnotationTool.PEN,
        width = 0.004f,
        points = listOf(NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.3f, 0.4f)),
    )
    private val second = Stroke(
        tool = AnnotationTool.HIGHLIGHTER,
        width = 0.02f,
        points = listOf(NormalizedPoint(0.5f, 0.5f)),
    )

    @Test
    fun `stroke can be undone and redone`() {
        val added = AnnotationHistory().add(first)
        val undone = added.undo()

        assertTrue(undone.strokes.isEmpty())
        assertEquals(listOf(first), undone.redo().strokes)
    }

    @Test
    fun `new stroke clears redo history`() {
        val history = AnnotationHistory().add(first).undo().add(second)

        assertEquals(listOf(second), history.redo().strokes)
    }

    @Test
    fun `annotation JSON survives round trip`() {
        val annotations = DocumentAnnotations(mapOf(2 to listOf(first, second)))

        assertEquals(
            annotations,
            AnnotationJson.decode(AnnotationJson.encode(annotations)),
        )
    }
}
