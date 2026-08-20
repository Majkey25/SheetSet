package cz.teply.sheetset.pdf

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot

const val MAX_POINTS_PER_STROKE = 4_096
const val MAX_STROKES_PER_PAGE = 10_000
// ponytail: bounded snapshots keep undo simple; use edit operations if profiling shows pressure.
private const val MAX_HISTORY_STEPS = 20

enum class AnnotationTool {
    PEN,
    HIGHLIGHTER,
}

data class NormalizedPoint(val x: Float, val y: Float) {
    init {
        require(x in 0f..1f && y in 0f..1f) { "Point must be normalized" }
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

data class Stroke(
    val tool: AnnotationTool,
    val width: Float,
    val points: List<NormalizedPoint>,
) {
    init {
        require(width > 0f && width <= 0.1f) { "Invalid stroke width" }
        require(points.isNotEmpty() && points.size <= MAX_POINTS_PER_STROKE) {
            "Invalid stroke point count"
        }
    }
}

data class DocumentAnnotations(val pages: Map<Int, List<Stroke>> = emptyMap()) {
    init {
        require(pages.all { (page, strokes) ->
            page >= 0 && strokes.size <= MAX_STROKES_PER_PAGE
        }) { "Invalid page annotations" }
    }

    fun withPage(page: Int, strokes: List<Stroke>): DocumentAnnotations {
        require(page >= 0) { "Page index must not be negative" }
        val next = pages.toMutableMap()
        if (strokes.isEmpty()) next.remove(page) else next[page] = strokes
        return DocumentAnnotations(next)
    }
}

data class AnnotationHistory(
    val strokes: List<Stroke> = emptyList(),
    private val undoStates: List<List<Stroke>> = emptyList(),
    private val redoStates: List<List<Stroke>> = emptyList(),
) {
    fun add(stroke: Stroke): AnnotationHistory {
        require(strokes.size < MAX_STROKES_PER_PAGE) { "Page annotation limit reached" }
        return replace(strokes + stroke)
    }

    fun undo(): AnnotationHistory {
        if (undoStates.isEmpty()) return this
        return AnnotationHistory(
            strokes = undoStates.last(),
            undoStates = undoStates.dropLast(1),
            redoStates = (redoStates + listOf(strokes)).takeLast(MAX_HISTORY_STEPS),
        )
    }

    fun redo(): AnnotationHistory {
        if (redoStates.isEmpty()) return this
        return AnnotationHistory(
            strokes = redoStates.last(),
            undoStates = (undoStates + listOf(strokes)).takeLast(MAX_HISTORY_STEPS),
            redoStates = redoStates.dropLast(1),
        )
    }

    fun erase(point: NormalizedPoint, radius: Float): AnnotationHistory {
        require(radius > 0f) { "Eraser radius must be positive" }
        val remaining = strokes.filterNot { it.isNear(point, radius) }
        return if (remaining.size == strokes.size) this else replace(remaining)
    }

    private fun replace(next: List<Stroke>): AnnotationHistory = AnnotationHistory(
        strokes = next,
        undoStates = (undoStates + listOf(strokes)).takeLast(MAX_HISTORY_STEPS),
    )
}

private fun Stroke.isNear(point: NormalizedPoint, radius: Float): Boolean {
    if (points.size == 1) return distance(points.single(), point) <= radius
    return points.zipWithNext().any { (start, end) ->
        distanceToSegment(point, start, end) <= radius
    }
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
    private const val VERSION = 1

    fun encode(annotations: DocumentAnnotations): String = JSONObject()
        .put("version", VERSION)
        .put(
            "pages",
            JSONObject().apply {
                annotations.pages.toSortedMap().forEach { (page, strokes) ->
                    put(page.toString(), JSONArray().apply { strokes.forEach { put(it.toJson()) } })
                }
            },
        )
        .toString()

    fun decode(raw: String): DocumentAnnotations {
        val root = JSONObject(raw)
        require(root.getInt("version") == VERSION) { "Unsupported annotation version" }
        val pagesJson = root.getJSONObject("pages")
        val pages = pagesJson.keys().asSequence().associate { pageKey ->
            val page = pageKey.toInt()
            val strokesJson = pagesJson.getJSONArray(pageKey)
            page to List(strokesJson.length()) { index -> strokesJson.getJSONObject(index).toStroke() }
        }
        return DocumentAnnotations(pages)
    }

    private fun Stroke.toJson(): JSONObject = JSONObject()
        .put("tool", tool.name)
        .put("width", width.toDouble())
        .put(
            "points",
            JSONArray().apply {
                points.forEach { point -> put(JSONArray(listOf(point.x, point.y))) }
            },
        )

    private fun JSONObject.toStroke(): Stroke {
        val pointsJson = getJSONArray("points")
        return Stroke(
            tool = AnnotationTool.valueOf(getString("tool")),
            width = getDouble("width").toFloat(),
            points = List(pointsJson.length()) { index ->
                val point = pointsJson.getJSONArray(index)
                NormalizedPoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat())
            },
        )
    }
}
