package cz.teply.sheetset.pdf

import cz.teply.sheetset.settings.AnnotationTextSize
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot

const val MAX_ANNOTATIONS_PER_PAGE = 10_000
const val MAX_POINTS_PER_INK = 4_096
const val MAX_TEXT_LENGTH = 4_000
private const val MAX_MARKUP_RECTS = 512
private const val MAX_ID_LENGTH = 128
private const val MAX_HISTORY_STEPS = 100

enum class InkKind { PEN, HIGHLIGHTER }
enum class MarkupKind { HIGHLIGHT, UNDERLINE, STRIKE_THROUGH }
enum class ShapeKind { LINE, ARROW, RECTANGLE, ELLIPSE }

data class NormalizedPoint(val x: Float, val y: Float) {
    init {
        require(x in 0f..1f && y in 0f..1f) { "Point must be normalized" }
    }
}

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f) {
            "Rectangle must be normalized"
        }
        require(left < right && top < bottom) { "Rectangle must have positive size" }
    }
}

data class PageBounds(val left: Float, val top: Float, val width: Float, val height: Float) {
    init {
        require(width > 0f && height > 0f) { "Page bounds must be positive" }
    }
}

fun normalizePoint(x: Float, y: Float, bounds: PageBounds): NormalizedPoint? {
    if (x !in bounds.left..(bounds.left + bounds.width)) return null
    if (y !in bounds.top..(bounds.top + bounds.height)) return null
    return NormalizedPoint(
        x = (x - bounds.left) / bounds.width,
        y = (y - bounds.top) / bounds.height,
    )
}

sealed interface PageAnnotation {
    val id: String
}

data class InkAnnotation(
    override val id: String,
    val kind: InkKind,
    val width: Float,
    val points: List<NormalizedPoint>,
) : PageAnnotation {
    init {
        requireValidId(id)
        require(width > 0f && width <= 0.1f) { "Invalid ink width" }
        require(points.isNotEmpty() && points.size <= MAX_POINTS_PER_INK) {
            "Invalid ink point count"
        }
    }
}

data class MarkupAnnotation(
    override val id: String,
    val kind: MarkupKind,
    val bounds: List<NormalizedRect>,
) : PageAnnotation {
    init {
        requireValidId(id)
        require(bounds.isNotEmpty() && bounds.size <= MAX_MARKUP_RECTS) {
            "Invalid markup bounds"
        }
    }
}

data class TextBoxAnnotation(
    override val id: String,
    val bounds: NormalizedRect,
    val text: String,
    val size: AnnotationTextSize,
) : PageAnnotation {
    init {
        requireValidId(id)
        require(text.isNotBlank() && text.length <= MAX_TEXT_LENGTH) { "Invalid annotation text" }
    }
}

data class ShapeAnnotation(
    override val id: String,
    val kind: ShapeKind,
    val start: NormalizedPoint,
    val end: NormalizedPoint,
    val width: Float,
) : PageAnnotation {
    init {
        requireValidId(id)
        require(width > 0f && width <= 0.1f) { "Invalid shape width" }
    }
}

data class DocumentAnnotations(
    val pages: Map<Int, List<PageAnnotation>> = emptyMap(),
) {
    init {
        require(pages.all { (page, annotations) ->
            page >= 0 && annotations.size <= MAX_ANNOTATIONS_PER_PAGE
        }) { "Invalid page annotations" }
        val ids = pages.values.flatten().map(PageAnnotation::id)
        require(ids.size == ids.distinct().size) { "Duplicate annotation ID" }
    }

    fun withPage(page: Int, annotations: List<PageAnnotation>): DocumentAnnotations {
        require(page >= 0) { "Page index must not be negative" }
        val next = pages.toMutableMap()
        if (annotations.isEmpty()) next.remove(page) else next[page] = annotations
        return DocumentAnnotations(next)
    }
}

data class AnnotationHistory(
    val annotations: List<PageAnnotation> = emptyList(),
    private val undoStates: List<List<PageAnnotation>> = emptyList(),
    private val redoStates: List<List<PageAnnotation>> = emptyList(),
) {
    init {
        require(annotations.size <= MAX_ANNOTATIONS_PER_PAGE) { "Page annotation limit reached" }
        require(annotations.map(PageAnnotation::id).distinct().size == annotations.size) {
            "Duplicate annotation ID"
        }
    }

    fun add(annotation: PageAnnotation): AnnotationHistory {
        require(annotations.size < MAX_ANNOTATIONS_PER_PAGE) { "Page annotation limit reached" }
        require(annotations.none { it.id == annotation.id }) { "Duplicate annotation ID" }
        return replace(annotations + annotation)
    }

    fun undo(): AnnotationHistory {
        if (undoStates.isEmpty()) return this
        return AnnotationHistory(
            annotations = undoStates.last(),
            undoStates = undoStates.dropLast(1),
            redoStates = (redoStates + listOf(annotations)).takeLast(MAX_HISTORY_STEPS),
        )
    }

    fun redo(): AnnotationHistory {
        if (redoStates.isEmpty()) return this
        return AnnotationHistory(
            annotations = redoStates.last(),
            undoStates = (undoStates + listOf(annotations)).takeLast(MAX_HISTORY_STEPS),
            redoStates = redoStates.dropLast(1),
        )
    }

    fun erase(point: NormalizedPoint, radius: Float): AnnotationHistory {
        require(radius > 0f) { "Eraser radius must be positive" }
        val remaining = annotations.filterNot { annotation ->
            annotation is InkAnnotation && annotation.isNear(point, radius)
        }
        return if (remaining.size == annotations.size) this else replace(remaining)
    }

    private fun replace(next: List<PageAnnotation>): AnnotationHistory = AnnotationHistory(
        annotations = next,
        undoStates = (undoStates + listOf(annotations)).takeLast(MAX_HISTORY_STEPS),
    )
}

private fun InkAnnotation.isNear(point: NormalizedPoint, radius: Float): Boolean {
    if (points.size == 1) return distance(points.single(), point) <= radius
    return points.zipWithNext().any { (start, end) ->
        distanceToSegment(point, start, end) <= radius
    }
}

private fun requireValidId(id: String) {
    require(id.isNotBlank() && id.length <= MAX_ID_LENGTH) { "Invalid annotation ID" }
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

object AnnotationJson {
    private const val VERSION = 2

    fun encode(annotations: DocumentAnnotations): String = JSONObject()
        .put("version", VERSION)
        .put(
            "pages",
            JSONObject().apply {
                annotations.pages.toSortedMap().forEach { (page, pageAnnotations) ->
                    put(
                        page.toString(),
                        JSONArray().apply {
                            pageAnnotations.forEach { annotation -> put(annotation.toJson()) }
                        },
                    )
                }
            },
        )
        .toString()

    fun decode(raw: String): DocumentAnnotations {
        val root = JSONObject(raw)
        return when (root.getInt("version")) {
            1 -> decodeVersionOne(root)
            VERSION -> decodeVersionTwo(root)
            else -> throw IllegalArgumentException("Unsupported annotation version")
        }
    }

    private fun decodeVersionOne(root: JSONObject): DocumentAnnotations {
        val pagesJson = root.getJSONObject("pages")
        val pages = pagesJson.keys().asSequence().associate { pageKey ->
            val page = pageKey.toInt()
            val strokes = pagesJson.getJSONArray(pageKey)
            page to List(strokes.length()) { index ->
                strokes.getJSONObject(index).toLegacyInk("legacy-$page-$index")
            }
        }
        return DocumentAnnotations(pages)
    }

    private fun decodeVersionTwo(root: JSONObject): DocumentAnnotations {
        val pagesJson = root.getJSONObject("pages")
        val pages = pagesJson.keys().asSequence().associate { pageKey ->
            val page = pageKey.toInt()
            val annotations = pagesJson.getJSONArray(pageKey)
            page to List(annotations.length()) { index ->
                annotations.getJSONObject(index).toAnnotation()
            }
        }
        return DocumentAnnotations(pages)
    }

    private fun PageAnnotation.toJson(): JSONObject = when (this) {
        is InkAnnotation -> JSONObject()
            .put("id", id)
            .put("type", "ink")
            .put("kind", kind.name)
            .put("width", width.toDouble())
            .put("points", JSONArray().apply { points.forEach { put(it.toJson()) } })

        is MarkupAnnotation -> JSONObject()
            .put("id", id)
            .put("type", "markup")
            .put("kind", kind.name)
            .put("bounds", JSONArray().apply { bounds.forEach { put(it.toJson()) } })

        is TextBoxAnnotation -> JSONObject()
            .put("id", id)
            .put("type", "text")
            .put("bounds", bounds.toJson())
            .put("text", text)
            .put("size", size.name)

        is ShapeAnnotation -> JSONObject()
            .put("id", id)
            .put("type", "shape")
            .put("kind", kind.name)
            .put("start", start.toJson())
            .put("end", end.toJson())
            .put("width", width.toDouble())
    }

    private fun JSONObject.toAnnotation(): PageAnnotation = when (getString("type")) {
        "ink" -> InkAnnotation(
            id = getString("id"),
            kind = InkKind.valueOf(getString("kind")),
            width = getDouble("width").toFloat(),
            points = getJSONArray("points").toPoints(),
        )

        "markup" -> MarkupAnnotation(
            id = getString("id"),
            kind = MarkupKind.valueOf(getString("kind")),
            bounds = getJSONArray("bounds").toRects(),
        )

        "text" -> TextBoxAnnotation(
            id = getString("id"),
            bounds = getJSONArray("bounds").toRect(),
            text = getString("text"),
            size = AnnotationTextSize.valueOf(getString("size")),
        )

        "shape" -> ShapeAnnotation(
            id = getString("id"),
            kind = ShapeKind.valueOf(getString("kind")),
            start = getJSONArray("start").toPoint(),
            end = getJSONArray("end").toPoint(),
            width = getDouble("width").toFloat(),
        )

        else -> throw IllegalArgumentException("Unsupported annotation type")
    }

    private fun JSONObject.toLegacyInk(id: String): InkAnnotation = InkAnnotation(
        id = id,
        kind = InkKind.valueOf(getString("tool")),
        width = getDouble("width").toFloat(),
        points = getJSONArray("points").toPoints(),
    )

    private fun NormalizedPoint.toJson(): JSONArray = JSONArray(listOf(x, y))

    private fun NormalizedRect.toJson(): JSONArray = JSONArray(listOf(left, top, right, bottom))

    private fun JSONArray.toPoints(): List<NormalizedPoint> =
        List(length()) { index -> getJSONArray(index).toPoint() }

    private fun JSONArray.toRects(): List<NormalizedRect> =
        List(length()) { index -> getJSONArray(index).toRect() }

    private fun JSONArray.toPoint(): NormalizedPoint = NormalizedPoint(
        getDouble(0).toFloat(),
        getDouble(1).toFloat(),
    )

    private fun JSONArray.toRect(): NormalizedRect = NormalizedRect(
        getDouble(0).toFloat(),
        getDouble(1).toFloat(),
        getDouble(2).toFloat(),
        getDouble(3).toFloat(),
    )
}
