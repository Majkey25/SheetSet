package cz.teply.sheetset.pdf

import cz.teply.sheetset.settings.AnnotationTextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AnnotationJsonMigrationTest {
    @Test
    fun versionOneStrokesMigrateWithoutLoss() {
        val legacy = """
            {"version":1,"pages":{"0":[
              {"tool":"PEN","width":0.004,"points":[[0.1,0.2],[0.3,0.4]]},
              {"tool":"HIGHLIGHTER","width":0.02,"points":[[0.5,0.6]]}
            ]}}
        """.trimIndent()

        val decoded = AnnotationJson.decode(legacy)
        val page = decoded.pages.getValue(0)

        assertEquals(InkKind.PEN, (page[0] as InkAnnotation).kind)
        assertEquals(InkKind.HIGHLIGHTER, (page[1] as InkAnnotation).kind)
        assertEquals("legacy-0-0", page[0].id)
        assertEquals(decoded, AnnotationJson.decode(AnnotationJson.encode(decoded)))
    }

    @Test
    fun everyVersionTwoTypeSurvivesRoundTrip() {
        val annotations = DocumentAnnotations(
            mapOf(
                2 to listOf(
                    InkAnnotation(
                        id = "ink",
                        kind = InkKind.PEN,
                        width = 0.004f,
                        points = listOf(NormalizedPoint(0.1f, 0.2f)),
                    ),
                    MarkupAnnotation(
                        id = "highlight",
                        kind = MarkupKind.HIGHLIGHT,
                        bounds = listOf(NormalizedRect(0.1f, 0.2f, 0.4f, 0.3f)),
                    ),
                    MarkupAnnotation(
                        id = "underline",
                        kind = MarkupKind.UNDERLINE,
                        bounds = listOf(NormalizedRect(0.2f, 0.3f, 0.5f, 0.4f)),
                    ),
                    MarkupAnnotation(
                        id = "strike",
                        kind = MarkupKind.STRIKE_THROUGH,
                        bounds = listOf(NormalizedRect(0.3f, 0.4f, 0.6f, 0.5f)),
                    ),
                    TextBoxAnnotation(
                        id = "text",
                        bounds = NormalizedRect(0.2f, 0.2f, 0.6f, 0.4f),
                        text = "Allegro",
                        size = AnnotationTextSize.MEDIUM,
                    ),
                    ShapeAnnotation(
                        id = "line",
                        kind = ShapeKind.LINE,
                        start = NormalizedPoint(0.1f, 0.1f),
                        end = NormalizedPoint(0.8f, 0.8f),
                        width = 0.004f,
                    ),
                    ShapeAnnotation(
                        id = "arrow",
                        kind = ShapeKind.ARROW,
                        start = NormalizedPoint(0.2f, 0.1f),
                        end = NormalizedPoint(0.7f, 0.8f),
                        width = 0.004f,
                    ),
                    ShapeAnnotation(
                        id = "rectangle",
                        kind = ShapeKind.RECTANGLE,
                        start = NormalizedPoint(0.2f, 0.2f),
                        end = NormalizedPoint(0.7f, 0.7f),
                        width = 0.004f,
                    ),
                    ShapeAnnotation(
                        id = "ellipse",
                        kind = ShapeKind.ELLIPSE,
                        start = NormalizedPoint(0.3f, 0.3f),
                        end = NormalizedPoint(0.6f, 0.6f),
                        width = 0.004f,
                    ),
                ),
            ),
        )

        assertEquals(annotations, AnnotationJson.decode(AnnotationJson.encode(annotations)))
    }

    @Test
    fun duplicateIdsAndBlankTextAreRejected() {
        val ink = InkAnnotation(
            id = "same",
            kind = InkKind.PEN,
            width = 0.004f,
            points = listOf(NormalizedPoint(0.1f, 0.1f)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            DocumentAnnotations(mapOf(0 to listOf(ink, ink)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TextBoxAnnotation(
                id = "text",
                bounds = NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f),
                text = " ",
                size = AnnotationTextSize.SMALL,
            )
        }
    }
}
