package cz.teply.sheetset.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
    fun thickInkHitsUsePhysicalPageAxesAndRemainStableAcrossScale() {
        val horizontal = stroke.copy(
            width = 0.125f,
            points = listOf(NormalizedPoint(0.25f, 0.5f), NormalizedPoint(0.75f, 0.5f)),
        )
        val vertical = horizontal.copy(
            points = listOf(NormalizedPoint(0.5f, 0.25f), NormalizedPoint(0.5f, 0.75f)),
        )

        for (scale in listOf(1f, 2f)) {
            assertTrue(horizontal.hitTest(NormalizedPoint(0.5f, 0.53125f), 0f, 1000f * scale, 2000f * scale))
            assertFalse(horizontal.hitTest(NormalizedPoint(0.5f, 0.54f), 0f, 1000f * scale, 2000f * scale))
            assertTrue(vertical.hitTest(NormalizedPoint(0.53125f, 0.5f), 0f, 2000f * scale, 1000f * scale))
            assertFalse(vertical.hitTest(NormalizedPoint(0.54f, 0.5f), 0f, 2000f * scale, 1000f * scale))
        }
    }

    @Test
    fun diagonalInkUsesDistancePerpendicularToRenderedSegment() {
        val diagonal = stroke.copy(width = 0.125f)

        assertTrue(diagonal.hitTest(NormalizedPoint(0.55f, 0.4875f), 0f, 1000f, 2000f))
        assertFalse(diagonal.hitTest(NormalizedPoint(0.5625f, 0.484375f), 0f, 1000f, 2000f))
        assertTrue(diagonal.hitTest(NormalizedPoint(0.4875f, 0.55f), 0f, 2000f, 1000f))
        assertFalse(diagonal.hitTest(NormalizedPoint(0.484375f, 0.5625f), 0f, 2000f, 1000f))
    }

    @Test
    fun inkDotsRespectRoundAndSquareCapsAndHitTolerance() {
        val pen = stroke.copy(width = 0.125f, points = listOf(NormalizedPoint(0.5f, 0.5f)))
        val highlighter = pen.copy(kind = InkKind.HIGHLIGHTER)

        assertTrue(pen.hitTest(NormalizedPoint(0.5625f, 0.5f), 0f, 1000f, 2000f))
        assertFalse(pen.hitTest(NormalizedPoint(0.5625f, 0.53125f), 0f, 1000f, 2000f))
        assertTrue(highlighter.hitTest(NormalizedPoint(0.5625f, 0.53125f), 0f, 1000f, 2000f))
        assertFalse(highlighter.hitTest(NormalizedPoint(0.565f, 0.5f), 0f, 1000f, 2000f))
        assertTrue(pen.hitTest(NormalizedPoint(0.57f, 0.5f), 0.02f, 1000f, 2000f))
        assertFalse(pen.hitTest(NormalizedPoint(0.585f, 0.5f), 0.02f, 1000f, 2000f))
    }

    @Test
    fun diagonalHighlighterIncludesSquareCapCornersWithoutOvershooting() {
        val highlighter = stroke.copy(width = 0.125f, kind = InkKind.HIGHLIGHTER)

        assertTrue(highlighter.hitTest(NormalizedPoint(0.83f, 0.75f), 0f, 1000f, 1000f))
        assertFalse(highlighter.hitTest(NormalizedPoint(0.84f, 0.75f), 0f, 1000f, 1000f))
        assertFalse(highlighter.copy(kind = InkKind.PEN).hitTest(NormalizedPoint(0.83f, 0.75f), 0f))
    }

    @Test
    fun highlighterBendsUseBevelJoinsAndIgnoreDuplicateSamples() {
        val highlighter = stroke.copy(
            width = 0.125f,
            kind = InkKind.HIGHLIGHTER,
            points = listOf(
                NormalizedPoint(0.25f, 0.5f),
                NormalizedPoint(0.5f, 0.5f),
                NormalizedPoint(0.5f, 0.5f),
                NormalizedPoint(0.5f, 0.25f),
            ),
        )

        assertTrue(highlighter.hitTest(NormalizedPoint(0.53f, 0.53f), 0f, 1000f, 1000f))
        assertFalse(highlighter.hitTest(NormalizedPoint(0.54f, 0.54f), 0f, 1000f, 1000f))
        assertTrue(highlighter.copy(kind = InkKind.PEN).hitTest(NormalizedPoint(0.54f, 0.54f), 0f))
    }

    @Test
    fun lassoIncludesPaintedWidthWithoutPaddingTheLongAxisTooFar() {
        val horizontal = stroke.copy(
            width = 0.125f,
            points = listOf(NormalizedPoint(0.25f, 0.5f), NormalizedPoint(0.75f, 0.5f)),
        )
        val annotations = listOf(horizontal)

        assertEquals(
            setOf(stroke.id),
            annotations.lassoSelection(NormalizedRect(0.4f, 0.53125f, 0.6f, 0.54f), 1000f, 2000f),
        )
        assertTrue(
            annotations.lassoSelection(NormalizedRect(0.4f, 0.55f, 0.6f, 0.56f), 1000f, 2000f).isEmpty(),
        )
        assertEquals(
            setOf(stroke.id),
            annotations.lassoSelection(NormalizedRect(0.4f, 0.55f, 0.6f, 0.56f), 2000f, 1000f),
        )
    }

    @Test
    fun lassoIncludesDiagonalSquareCapsAndPreservesBoundingBoxSelection() {
        val highlighter = stroke.copy(width = 0.125f, kind = InkKind.HIGHLIGHTER)
        val cap = NormalizedRect(0.83f, 0.74f, 0.835f, 0.76f)

        assertEquals(setOf(stroke.id), listOf(highlighter).lassoSelection(cap, 1000f, 1000f))
        assertTrue(listOf(highlighter.copy(kind = InkKind.PEN)).lassoSelection(cap).isEmpty())
        assertTrue(
            listOf(highlighter).lassoSelection(NormalizedRect(0.84f, 0.74f, 0.85f, 0.76f)).isEmpty(),
        )
        assertEquals(
            setOf(stroke.id),
            listOf(stroke).lassoSelection(NormalizedRect(0.25f, 0.7f, 0.3f, 0.75f)),
        )
    }

    @Test
    fun thickInkAtPageEdgeRemainsSelectableAndTopmostHitUsesPageSize() {
        val edge = stroke.copy(width = 0.125f, points = listOf(NormalizedPoint(1f, 1f)))
        val bottom = rectangle("bottom", 0.8f, 0.8f, 1f, 1f)
        val annotations = listOf(bottom, edge)

        assertSame(edge, annotations.topmostHit(NormalizedPoint(0.95f, 0.99f), 0f, 1000f, 2000f))
        assertSame(bottom, annotations.topmostHit(NormalizedPoint(0.99f, 0.96f), 0f, 1000f, 2000f))
        assertEquals(
            setOf(stroke.id),
            listOf(edge).lassoSelection(NormalizedRect(0.94f, 0.97f, 0.95f, 0.98f), 1000f, 2000f),
        )
    }

    @Test
    fun shapeInteriorsRemainSelectableAndLineHitIncludesOnlyHalfStrokeWidth() {
        val rectangle = rectangle("shape", 0.25f, 0.25f, 0.75f, 0.75f).copy(width = 0.125f)
        val line = rectangle.copy(
            kind = ShapeKind.LINE,
            start = NormalizedPoint(0.25f, 0.5f),
            end = NormalizedPoint(0.75f, 0.5f),
        )

        assertTrue(rectangle.hitTest(NormalizedPoint(0.5f, 0.5f), 0f, 1000f, 2000f))
        assertTrue(rectangle.copy(kind = ShapeKind.ELLIPSE).hitTest(NormalizedPoint(0.5f, 0.5f), 0f))
        assertTrue(line.hitTest(NormalizedPoint(0.5f, 0.53125f), 0f, 1000f, 2000f))
        assertFalse(line.hitTest(NormalizedPoint(0.5f, 0.54f), 0f, 1000f, 2000f))
    }

    @Test
    fun arrowHitIncludesBothPaintedHeadArmsWithoutOvershooting() {
        val arrow = rectangle("arrow", 0.25f, 0.5f, 0.75f, 0.5f).copy(kind = ShapeKind.ARROW, width = 0.08f)

        assertTrue(arrow.hitTest(NormalizedPoint(0.5f, 0.5f), 0f))
        for (y in listOf(0.444f, 0.556f)) {
            val point = NormalizedPoint(0.716f, y)
            assertTrue(arrow.hitTest(point, 0f))
            assertTrue(arrow.hitTest(point, 0.0125f))
            assertFalse(arrow.copy(kind = ShapeKind.LINE).hitTest(point, 0.0125f))
        }
        assertFalse(arrow.hitTest(NormalizedPoint(0.716f, 0.58f), 0.0125f))
        assertFalse(arrow.hitTest(NormalizedPoint(0.716f, 0.42f), 0.0125f))
    }

    @Test
    fun arrowHeadHitsUsePhysicalPageAxesAndScale() {
        val horizontal = rectangle("arrow", 0.25f, 0.5f, 0.75f, 0.5f).copy(kind = ShapeKind.ARROW, width = 0.08f)
        val vertical = horizontal.copy(start = NormalizedPoint(0.5f, 0.25f), end = NormalizedPoint(0.5f, 0.75f))

        for (scale in listOf(1f, 2f)) {
            assertTrue(horizontal.hitTest(NormalizedPoint(0.716f, 0.528f), 0.0125f, 1000f * scale, 2000f * scale))
            assertFalse(horizontal.hitTest(NormalizedPoint(0.716f, 0.556f), 0.0125f, 1000f * scale, 2000f * scale))
            assertTrue(horizontal.hitTest(NormalizedPoint(0.733f, 0.556f), 0.0125f, 2000f * scale, 1000f * scale))
            assertTrue(vertical.hitTest(NormalizedPoint(0.444f, 0.733f), 0.0125f, 1000f * scale, 2000f * scale))
            assertFalse(vertical.hitTest(NormalizedPoint(0.42f, 0.733f), 0.0125f, 1000f * scale, 2000f * scale))
        }
    }

    @Test
    fun shortArrowLimitsHeadLengthToQuarterOfShaft() {
        val arrow = rectangle("arrow", 0.65f, 0.5f, 0.75f, 0.5f).copy(kind = ShapeKind.ARROW, width = 0.008f)

        assertTrue(arrow.hitTest(NormalizedPoint(0.729f, 0.513f), 0f))
        assertFalse(arrow.hitTest(NormalizedPoint(0.716f, 0.521f), 0f))
        assertTrue(arrow.copy(start = NormalizedPoint(0.25f, 0.5f)).hitTest(NormalizedPoint(0.716f, 0.521f), 0f))
    }

    @Test
    fun hitAndLassoRejectInvalidPageGeometry() {
        assertThrows(IllegalArgumentException::class.java) {
            stroke.hitTest(NormalizedPoint(0.5f, 0.5f), 0f, 0f, 1000f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            stroke.hitTest(NormalizedPoint(0.5f, 0.5f), Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            listOf(stroke).lassoSelection(NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f), 1000f, Float.POSITIVE_INFINITY)
        }
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

    @Test
    fun cornerAndEndpointHandlesResizeExpectedGeometry() {
        val rectangle = ShapeAnnotation(
            id = "rectangle",
            kind = ShapeKind.RECTANGLE,
            start = NormalizedPoint(0.2f, 0.2f),
            end = NormalizedPoint(0.6f, 0.6f),
            width = 0.004f,
        )
        val line = ShapeAnnotation(
            id = "line",
            kind = ShapeKind.LINE,
            start = NormalizedPoint(0.1f, 0.1f),
            end = NormalizedPoint(0.5f, 0.5f),
            width = 0.004f,
        )

        assertEquals(
            NormalizedPoint(0.8f, 0.9f),
            (rectangle.resized(
                AnnotationHandle.BOTTOM_RIGHT,
                NormalizedPoint(0.8f, 0.9f),
            ) as ShapeAnnotation).end,
        )
        assertEquals(
            NormalizedPoint(0.9f, 0.2f),
            (line.resized(
                AnnotationHandle.END,
                NormalizedPoint(0.9f, 0.2f),
            ) as ShapeAnnotation).end,
        )
        assertTrue(stroke.resizeHandles().isEmpty())
    }

    @Test
    fun straightStrokeKeepsOnlyEndpoints() {
        val points = listOf(
            NormalizedPoint(0.1f, 0.2f),
            NormalizedPoint(0.4f, 0.7f),
            NormalizedPoint(0.9f, 0.3f),
        )

        assertEquals(listOf(points.first(), points.last()), strokePoints(points, true))
        assertEquals(points, strokePoints(points, false))
        assertEquals(points.take(1), strokePoints(points.take(1), true))
    }

    @Test
    fun lassoSelectsIntersectingAnnotationsAndBatchTranslationClampsTogether() {
        val first = rectangle("first", 0.1f, 0.1f, 0.2f, 0.2f)
        val second = rectangle("second", 0.8f, 0.8f, 0.95f, 0.95f)
        val annotations = listOf(first, second)

        assertEquals(
            setOf("first"),
            annotations.lassoSelection(NormalizedRect(0.05f, 0.05f, 0.25f, 0.25f)),
        )
        val moved = annotations.translateSelection(setOf("second"), 0.2f, 0.2f)
        assertEquals(1f, moved.last().normalizedBounds().right, 0.0001f)
        assertEquals(1f, moved.last().normalizedBounds().bottom, 0.0001f)
    }

    @Test
    fun batchTranslationPreservesSelectedSpacingAtPageEdge() {
        val first = rectangle("first", 0.1f, 0.1f, 0.2f, 0.2f)
        val second = rectangle("second", 0.8f, 0.8f, 0.95f, 0.95f)

        val moved = listOf(first, second).translateSelection(setOf("first", "second"), 0.2f, 0.2f)

        assertEquals(0.15f, moved.first().normalizedBounds().left, 0.0001f)
        assertEquals(1f, moved.last().normalizedBounds().right, 0.0001f)
    }

    @Test
    fun duplicateSelectionCreatesNewIdsAndOffsetCopies() {
        val duplicated = listOf(rectangle("source", 0.2f, 0.2f, 0.3f, 0.3f))
            .duplicateSelection(setOf("source")) { "copy" }

        assertEquals(listOf("source", "copy"), duplicated.map(PageAnnotation::id))
        assertEquals(0.21f, duplicated.last().normalizedBounds().left, 0.0001f)
    }

    @Test
    fun normalizedBoundsSupportsHorizontalInk() {
        val ink = InkAnnotation(
            id = "horizontal",
            kind = InkKind.PEN,
            width = 0.004f,
            points = listOf(NormalizedPoint(0.1f, 0.5f), NormalizedPoint(0.9f, 0.5f)),
        )

        assertEquals(0.01f, ink.normalizedBounds().bottom - ink.normalizedBounds().top, 0.0001f)
    }

    @Test
    fun lassoIncludesAnnotationTouchingItsEdge() {
        val annotation = rectangle("edge", 0.2f, 0.2f, 0.4f, 0.4f)

        assertEquals(
            setOf("edge"),
            listOf(annotation).lassoSelection(NormalizedRect(0.4f, 0.1f, 0.6f, 0.3f)),
        )
    }

    @Test
    fun duplicateSelectionKeepsSourceOrderAndSkipsOffsetAtPageEdge() {
        val calls = mutableListOf<Int>()
        val annotations = listOf(
            rectangle("first", 0.1f, 0.1f, 0.2f, 0.2f),
            rectangle("ignored", 0.4f, 0.4f, 0.5f, 0.5f),
            rectangle("edge", 0.9f, 0.9f, 1f, 1f),
        )

        val duplicated = annotations.duplicateSelection(setOf("edge", "first")) {
            calls += calls.size
            "copy-${calls.last()}"
        }

        assertEquals(listOf(0, 1), calls)
        assertEquals(
            listOf("first", "ignored", "edge", "copy-0", "copy-1"),
            duplicated.map(PageAnnotation::id),
        )
        assertEquals(annotations[0].normalizedBounds(), duplicated[3].normalizedBounds())
        assertEquals(annotations[2].normalizedBounds(), duplicated[4].normalizedBounds())
    }

    @Test
    fun singlePointInkBoundsStayNormalizedAtPageEdge() {
        val ink = InkAnnotation(
            id = "edge-dot",
            kind = InkKind.PEN,
            width = 0.004f,
            points = listOf(NormalizedPoint(1f, 1f)),
        )

        assertEquals(NormalizedRect(0.99f, 0.99f, 1f, 1f), ink.normalizedBounds())
    }

    @Test
    fun resizingSymbolOntoOppositeCornerKeepsMinimumSize() {
        val symbol = SymbolAnnotation(
            id = "symbol",
            symbolId = "sharp",
            center = NormalizedPoint(0.5f, 0.5f),
            size = 0.2f,
            rotationDegrees = 0f,
            color = AnnotationColor.BLACK,
            opacity = 255,
        )

        val resized = symbol.resized(
            AnnotationHandle.TOP_LEFT,
            NormalizedPoint(0.6f, 0.6f),
        ) as SymbolAnnotation

        assertEquals(0.01f, resized.size, 0f)
        assertEquals(NormalizedPoint(0.6f, 0.6f), resized.center)
    }

    @Test
    fun symbolRotationHandleRotatesAroundItsCenter() {
        val symbol = SymbolAnnotation(
            id = "symbol",
            symbolId = "sharp",
            center = NormalizedPoint(0.5f, 0.5f),
            size = 0.2f,
            rotationDegrees = 0f,
            color = AnnotationColor.BLACK,
            opacity = 255,
        )

        assertEquals(NormalizedPoint(0.5f, 0.4f), symbol.resizeHandles()[AnnotationHandle.ROTATION])
        assertEquals(
            90f,
            (symbol.resized(AnnotationHandle.ROTATION, NormalizedPoint(0.7f, 0.5f)) as SymbolAnnotation)
                .rotationDegrees,
            0.001f,
        )
    }

    private fun rectangle(
        id: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) = ShapeAnnotation(
        id = id,
        kind = ShapeKind.RECTANGLE,
        start = NormalizedPoint(left, top),
        end = NormalizedPoint(right, bottom),
        width = 0.004f,
    )
}
