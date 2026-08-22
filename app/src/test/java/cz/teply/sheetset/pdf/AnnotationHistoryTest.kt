package cz.teply.sheetset.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationHistoryTest {
    private val first = InkAnnotation(
        id = "first",
        kind = InkKind.PEN,
        width = 0.004f,
        points = listOf(NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.3f, 0.4f)),
    )
    private val second = InkAnnotation(
        id = "second",
        kind = InkKind.HIGHLIGHTER,
        width = 0.02f,
        points = listOf(NormalizedPoint(0.5f, 0.5f)),
    )

    @Test
    fun `annotation can be undone and redone`() {
        val added = AnnotationHistory().add(first)
        val undone = added.undo()

        assertTrue(undone.annotations.isEmpty())
        assertEquals(listOf(first), undone.redo().annotations)
    }

    @Test
    fun `new annotation clears redo history`() {
        val history = AnnotationHistory().add(first).undo().add(second)

        assertEquals(listOf(second), history.redo().annotations)
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
