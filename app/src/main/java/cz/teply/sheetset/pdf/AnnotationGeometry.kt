package cz.teply.sheetset.pdf

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val MIN_ANNOTATION_SIZE = 0.01f

fun List<PageAnnotation>.topmostHit(
    point: NormalizedPoint,
    radius: Float,
): PageAnnotation? = asReversed().firstOrNull { it.hitTest(point, radius) }

fun PageAnnotation.hitTest(point: NormalizedPoint, radius: Float): Boolean {
    require(radius >= 0f) { "Hit radius must not be negative" }
    return when (this) {
        is InkAnnotation -> when {
            points.size == 1 -> distance(points.single(), point) <= radius
            else -> points.zipWithNext().any { (start, end) ->
                distanceToSegment(point, start, end) <= radius
            }
        }

        is MarkupAnnotation -> bounds.any { it.contains(point, radius) }
        is TextBoxAnnotation -> bounds.contains(point, radius)
        is ShapeAnnotation -> when (kind) {
            ShapeKind.LINE, ShapeKind.ARROW ->
                distanceToSegment(point, start, end) <= radius + width

            ShapeKind.RECTANGLE -> annotationBounds().contains(point, radius)
            ShapeKind.ELLIPSE -> ellipseContains(point, radius)
        }
    }
}

fun PageAnnotation.translated(dx: Float, dy: Float): PageAnnotation {
    val bounds = annotationBounds()
    val safeDx = dx.coerceIn(-bounds.left, 1f - bounds.right)
    val safeDy = dy.coerceIn(-bounds.top, 1f - bounds.bottom)
    if (safeDx == 0f && safeDy == 0f) return this
    return when (this) {
        is InkAnnotation -> copy(points = points.map { it.translated(safeDx, safeDy) })
        is MarkupAnnotation -> copy(bounds = this.bounds.map { it.translated(safeDx, safeDy) })
        is TextBoxAnnotation -> copy(bounds = this.bounds.translated(safeDx, safeDy))
        is ShapeAnnotation -> copy(
            start = start.translated(safeDx, safeDy),
            end = end.translated(safeDx, safeDy),
        )
    }
}

fun PageAnnotation.resized(start: NormalizedPoint, end: NormalizedPoint): PageAnnotation {
    if (this is InkAnnotation) return this
    val target = minimumRect(start, end)
    return when (this) {
        is InkAnnotation -> this
        is MarkupAnnotation -> {
            val source = annotationBounds()
            copy(bounds = bounds.map { it.scaled(source, target) })
        }
        is TextBoxAnnotation -> copy(bounds = target)
        is ShapeAnnotation -> when (kind) {
            ShapeKind.LINE, ShapeKind.ARROW -> {
                val line = minimumLine(start, end)
                copy(start = line.first, end = line.second)
            }
            ShapeKind.RECTANGLE, ShapeKind.ELLIPSE -> copy(
                start = NormalizedPoint(target.left, target.top),
                end = NormalizedPoint(target.right, target.bottom),
            )
        }
    }
}

fun manualMarkup(start: NormalizedPoint, end: NormalizedPoint): List<NormalizedRect> =
    listOf(minimumRect(start, end))

private data class Bounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(point: NormalizedPoint, radius: Float): Boolean =
        point.x in (left - radius).coerceAtLeast(0f)..(right + radius).coerceAtMost(1f) &&
            point.y in (top - radius).coerceAtLeast(0f)..(bottom + radius).coerceAtMost(1f)
}

private fun PageAnnotation.annotationBounds(): Bounds = when (this) {
    is InkAnnotation -> points.bounds()
    is MarkupAnnotation -> Bounds(
        left = bounds.minOf(NormalizedRect::left),
        top = bounds.minOf(NormalizedRect::top),
        right = bounds.maxOf(NormalizedRect::right),
        bottom = bounds.maxOf(NormalizedRect::bottom),
    )
    is TextBoxAnnotation -> bounds.toBounds()
    is ShapeAnnotation -> Bounds(
        left = min(start.x, end.x),
        top = min(start.y, end.y),
        right = max(start.x, end.x),
        bottom = max(start.y, end.y),
    )
}

private fun List<NormalizedPoint>.bounds(): Bounds = Bounds(
    left = minOf(NormalizedPoint::x),
    top = minOf(NormalizedPoint::y),
    right = maxOf(NormalizedPoint::x),
    bottom = maxOf(NormalizedPoint::y),
)

private fun NormalizedRect.toBounds(): Bounds = Bounds(left, top, right, bottom)

private fun NormalizedRect.contains(point: NormalizedPoint, radius: Float): Boolean =
    toBounds().contains(point, radius)

private fun ShapeAnnotation.ellipseContains(point: NormalizedPoint, radius: Float): Boolean {
    val bounds = annotationBounds()
    val radiusX = bounds.width / 2f + radius
    val radiusY = bounds.height / 2f + radius
    if (radiusX <= 0f || radiusY <= 0f) return false
    val centerX = (bounds.left + bounds.right) / 2f
    val centerY = (bounds.top + bounds.bottom) / 2f
    return ((point.x - centerX) / radiusX).pow(2) +
        ((point.y - centerY) / radiusY).pow(2) <= 1f
}

private fun NormalizedPoint.translated(dx: Float, dy: Float): NormalizedPoint = NormalizedPoint(
    x = (x + dx).coerceIn(0f, 1f),
    y = (y + dy).coerceIn(0f, 1f),
)

private fun NormalizedRect.translated(dx: Float, dy: Float): NormalizedRect = NormalizedRect(
    left = (left + dx).coerceIn(0f, 1f),
    top = (top + dy).coerceIn(0f, 1f),
    right = (right + dx).coerceIn(0f, 1f),
    bottom = (bottom + dy).coerceIn(0f, 1f),
)

private fun NormalizedRect.scaled(source: Bounds, target: NormalizedRect): NormalizedRect {
    val scaleX = target.width() / source.width
    val scaleY = target.height() / source.height
    return NormalizedRect(
        left = target.left + (left - source.left) * scaleX,
        top = target.top + (top - source.top) * scaleY,
        right = target.left + (right - source.left) * scaleX,
        bottom = target.top + (bottom - source.top) * scaleY,
    )
}

private fun NormalizedRect.width(): Float = right - left

private fun NormalizedRect.height(): Float = bottom - top

private fun minimumRect(start: NormalizedPoint, end: NormalizedPoint): NormalizedRect {
    val rawLeft = min(start.x, end.x)
    val rawTop = min(start.y, end.y)
    val rawRight = max(start.x, end.x)
    val rawBottom = max(start.y, end.y)
    val left = if (rawRight - rawLeft < MIN_ANNOTATION_SIZE) {
        ((rawLeft + rawRight) / 2f - MIN_ANNOTATION_SIZE / 2f)
            .coerceIn(0f, 1f - MIN_ANNOTATION_SIZE)
    } else {
        rawLeft
    }
    val top = if (rawBottom - rawTop < MIN_ANNOTATION_SIZE) {
        ((rawTop + rawBottom) / 2f - MIN_ANNOTATION_SIZE / 2f)
            .coerceIn(0f, 1f - MIN_ANNOTATION_SIZE)
    } else {
        rawTop
    }
    return NormalizedRect(
        left = left,
        top = top,
        right = if (rawRight - rawLeft < MIN_ANNOTATION_SIZE) left + MIN_ANNOTATION_SIZE else rawRight,
        bottom = if (rawBottom - rawTop < MIN_ANNOTATION_SIZE) top + MIN_ANNOTATION_SIZE else rawBottom,
    )
}

private fun minimumLine(
    start: NormalizedPoint,
    end: NormalizedPoint,
): Pair<NormalizedPoint, NormalizedPoint> {
    if (distance(start, end) >= MIN_ANNOTATION_SIZE) return start to end
    val endX = if (start.x <= 1f - MIN_ANNOTATION_SIZE) {
        start.x + MIN_ANNOTATION_SIZE
    } else {
        start.x - MIN_ANNOTATION_SIZE
    }
    return start to NormalizedPoint(endX, start.y)
}

private fun distance(first: NormalizedPoint, second: NormalizedPoint): Float = hypot(
    first.x - second.x,
    first.y - second.y,
)

private fun distanceToSegment(
    point: NormalizedPoint,
    start: NormalizedPoint,
    end: NormalizedPoint,
): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0f) return distance(point, start)
    val projection = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared)
        .coerceIn(0f, 1f)
    return hypot(
        point.x - (start.x + projection * dx),
        point.y - (start.y + projection * dy),
    )
}
