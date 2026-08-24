package cz.teply.sheetset.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.createBitmap
import cz.teply.sheetset.settings.AnnotationTextSize
import cz.teply.sheetset.settings.PageFit
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class ReaderTool {
    VIEW,
    SELECT,
    PEN,
    HIGHLIGHTER,
    UNDERLINE,
    STRIKE_THROUGH,
    TEXT_BOX,
    LINE,
    ARROW,
    RECTANGLE,
    ELLIPSE,
    ERASER,
}

class PdfPageView(context: Context) : View(context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = (zoom * detector.scaleFactor).coerceIn(1f, 5f)
                clampPan()
                invalidate()
                return true
            }
        },
    )

    private var source: File? = null
    private var pageIndex = -1
    private var renderedPageIndex = -1
    private var bitmap: Bitmap? = null
    private var generation = 0
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var halfPagePart = 0
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastTapAt = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private val previewPoints = mutableListOf<NormalizedPoint>()
    private var dragStart: NormalizedPoint? = null
    private var dragCurrent: NormalizedPoint? = null
    private var gestureOriginal: PageAnnotation? = null
    private var gesturePreview: PageAnnotation? = null
    private var resizeHandle: AnnotationHandle? = null
    private val erasedIds = mutableSetOf<String>()

    init {
        isClickable = true
    }

    var tool = ReaderTool.VIEW
        set(value) {
            field = value
            resetGesture()
            invalidate()
        }
    var annotationColor = AnnotationColor.BLACK
    var penWidth = 0.004f
    var highlighterWidth = 0.016f
    var shapeWidth = 0.004f
    var straightLine = false
    var textSize = AnnotationTextSize.MEDIUM
    var highlighterAlpha = 105
    var pageFit = PageFit.PAGE
        set(value) {
            field = value
            applyHalfPagePart()
            invalidate()
        }
    var pageTurnTaps = true
    var pageTurnSwipes = true
    var annotations: List<PageAnnotation> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var selectedAnnotationId: String? = null
        set(value) {
            field = value
            invalidate()
        }
    var onPreviousPage: () -> Unit = {}
    var onNextPage: () -> Unit = {}
    var onPageClick: () -> Unit = {}
    var onSelectAnnotation: (String?) -> Unit = {}
    var onAddAnnotation: (PageAnnotation) -> Unit = {}
    var onUpdateAnnotation: (PageAnnotation) -> Unit = {}
    var onDeleteAnnotation: (String) -> Unit = {}
    var onRequestText: (NormalizedRect) -> Unit = {}
    var onRequestMarkup: (MarkupKind, NormalizedPoint, NormalizedPoint) -> Unit = { _, _, _ -> }
    var onRenderError: () -> Unit = {}

    fun showPage(file: File, index: Int) {
        if (source == file && pageIndex == index && bitmap != null) return
        source = file
        pageIndex = index
        renderPage()
    }

    fun setHalfPagePart(part: Int) {
        require(part in 0..1) { "Invalid half-page part" }
        halfPagePart = part
        applyHalfPagePart()
        invalidate()
    }

    fun scrollByPixels(pixels: Float): Boolean {
        if (renderedPageIndex != pageIndex) return false
        val page = bitmap ?: return false
        val result = scrollPan(panY, maxPanY(page), pixels)
        panY = result.panY
        invalidate()
        return result.reachedEnd
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width != oldWidth || height != oldHeight) renderPage()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(18, 18, 18))
        val page = bitmap ?: return
        val bounds = pageBounds(page)
        canvas.drawBitmap(page, null, bounds, pagePaint)
        annotations.forEach { stored ->
            val annotation = gesturePreview?.takeIf { it.id == stored.id } ?: stored
            AnnotationRenderer.draw(
                canvas = canvas,
                annotation = annotation,
                page = bounds,
                selected = annotation.id == selectedAnnotationId,
                highlighterAlpha = highlighterAlpha,
            )
        }
        previewAnnotation()?.let { preview ->
            AnnotationRenderer.draw(
                canvas = canvas,
                annotation = preview,
                page = bounds,
                highlighterAlpha = highlighterAlpha,
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount > 1 || scaleDetector.isInProgress) {
            resetGesture()
            return true
        }
        if (tool == ReaderTool.VIEW) {
            if (handleViewTouch(event)) performClick()
            return true
        }
        return handleEditorTouch(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        onPageClick()
        return true
    }

    override fun onDetachedFromWindow() {
        generation++
        executor.shutdownNow()
        bitmap?.recycle()
        bitmap = null
        renderedPageIndex = -1
        super.onDetachedFromWindow()
    }

    private fun handleViewTouch(event: MotionEvent): Boolean {
        var clicked = false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> if (canPan()) {
                panX += event.x - lastX
                panY += event.y - lastY
                lastX = event.x
                lastY = event.y
                clampPan()
                invalidate()
            }
            MotionEvent.ACTION_UP -> clicked = finishViewGesture(event)
        }
        return clicked
    }

    private fun finishViewGesture(event: MotionEvent): Boolean {
        val dx = event.x - downX
        val dy = event.y - downY
        if (
            pageTurnSwipes &&
            zoom == 1f &&
            abs(dx) > 80f &&
            abs(dx) > abs(dy) &&
            !canPanHorizontally()
        ) {
            if (dx < 0f) onNextPage() else onPreviousPage()
            return false
        }
        if (hypot(dx, dy) > 24f) return false
        val now = System.currentTimeMillis()
        if (
            zoom > 1f &&
            now - lastTapAt < 300 &&
            hypot(event.x - lastTapX, event.y - lastTapY) < 60f
        ) {
            zoom = 1f
            panX = 0f
            panY = 0f
            lastTapAt = 0L
            invalidate()
            return false
        }
        lastTapAt = now
        lastTapX = event.x
        lastTapY = event.y
        if (!pageTurnTaps) return true
        return when {
            event.x < width / 3f -> {
                onPreviousPage()
                false
            }
            event.x > width * 2f / 3f -> {
                onNextPage()
                false
            }
            else -> true
        }
    }

    private fun handleEditorTouch(event: MotionEvent): Boolean {
        val page = bitmap ?: return true
        val bounds = pageBounds(page)
        val point = normalizePoint(
            event.x,
            event.y,
            PageBounds(bounds.left, bounds.top, bounds.width(), bounds.height()),
        )
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> point?.let(::beginGesture)
            MotionEvent.ACTION_MOVE -> point?.let(::updateGesture)
            MotionEvent.ACTION_UP -> {
                point?.let(::updateGesture)
                finishGesture()
            }
            MotionEvent.ACTION_CANCEL -> resetGesture()
        }
        return true
    }

    private fun beginGesture(point: NormalizedPoint) {
        resetGesture()
        dragStart = point
        dragCurrent = point
        when (tool) {
            ReaderTool.SELECT -> {
                val selected = annotations.firstOrNull { it.id == selectedAnnotationId }
                resizeHandle = selected?.handleAt(point, 0.04f / zoom)
                val hit = if (resizeHandle != null) {
                    selected
                } else {
                    annotations.topmostHit(point, 0.025f / zoom)
                }
                selectedAnnotationId = hit?.id
                onSelectAnnotation(hit?.id)
                gestureOriginal = hit
                gesturePreview = hit
            }
            ReaderTool.PEN, ReaderTool.HIGHLIGHTER -> previewPoints.add(point)
            ReaderTool.ERASER -> eraseAt(point)
            else -> Unit
        }
        invalidate()
    }

    private fun updateGesture(point: NormalizedPoint) {
        dragCurrent = point
        when (tool) {
            ReaderTool.SELECT -> {
                val original = gestureOriginal ?: return
                val start = dragStart ?: return
                gesturePreview = resizeHandle?.let { original.resized(it, point) }
                    ?: original.translated(point.x - start.x, point.y - start.y)
            }
            ReaderTool.PEN, ReaderTool.HIGHLIGHTER -> {
                val previous = previewPoints.lastOrNull()
                if (previous == null || distance(previous, point) > 0.001f) {
                    if (previewPoints.size < MAX_POINTS_PER_INK) previewPoints.add(point)
                }
            }
            ReaderTool.ERASER -> eraseAt(point)
            else -> Unit
        }
        invalidate()
    }

    private fun finishGesture() {
        val start = dragStart
        val end = dragCurrent
        when (tool) {
            ReaderTool.SELECT -> {
                val original = gestureOriginal
                val updated = gesturePreview
                if (original != null && updated != null && updated != original) {
                    onUpdateAnnotation(updated)
                }
            }
            ReaderTool.PEN -> addInk(InkKind.PEN, penWidth)
            ReaderTool.HIGHLIGHTER -> addInk(InkKind.HIGHLIGHTER, highlighterWidth)
            ReaderTool.UNDERLINE -> requestMarkup(MarkupKind.UNDERLINE, start, end)
            ReaderTool.STRIKE_THROUGH -> requestMarkup(MarkupKind.STRIKE_THROUGH, start, end)
            ReaderTool.TEXT_BOX -> if (start != null && end != null) {
                onRequestText(manualMarkup(start, end).single())
            }
            ReaderTool.LINE -> addShape(ShapeKind.LINE, start, end)
            ReaderTool.ARROW -> addShape(ShapeKind.ARROW, start, end)
            ReaderTool.RECTANGLE -> addShape(ShapeKind.RECTANGLE, start, end)
            ReaderTool.ELLIPSE -> addShape(ShapeKind.ELLIPSE, start, end)
            ReaderTool.VIEW, ReaderTool.ERASER -> Unit
        }
        resetGesture()
    }

    private fun requestMarkup(
        kind: MarkupKind,
        start: NormalizedPoint?,
        end: NormalizedPoint?,
    ) {
        if (start != null && end != null) onRequestMarkup(kind, start, end)
    }

    private fun addInk(kind: InkKind, width: Float) {
        if (previewPoints.isEmpty()) return
        add(
            InkAnnotation(
                id = UUID.randomUUID().toString(),
                kind = kind,
                width = width,
                points = strokePoints(previewPoints.toList(), straightLine),
                color = annotationColor,
            ),
        )
    }

    private fun addShape(
        kind: ShapeKind,
        start: NormalizedPoint?,
        end: NormalizedPoint?,
    ) {
        if (start == null || end == null) return
        val raw = ShapeAnnotation(
            id = UUID.randomUUID().toString(),
            kind = kind,
            start = start,
            end = end,
            width = shapeWidth,
            color = annotationColor,
        )
        add(raw.resized(start, end))
    }

    private fun add(annotation: PageAnnotation) {
        onAddAnnotation(annotation)
    }

    private fun eraseAt(point: NormalizedPoint) {
        val hit = annotations.topmostHit(point, 0.025f / zoom) ?: return
        if (erasedIds.add(hit.id)) {
            if (selectedAnnotationId == hit.id) {
                selectedAnnotationId = null
                onSelectAnnotation(null)
            }
            onDeleteAnnotation(hit.id)
        }
    }

    private fun previewAnnotation(): PageAnnotation? {
        val start = dragStart ?: return null
        val end = dragCurrent ?: return null
        return when (tool) {
            ReaderTool.PEN -> previewInk(InkKind.PEN, penWidth)
            ReaderTool.HIGHLIGHTER -> previewInk(InkKind.HIGHLIGHTER, highlighterWidth)
            ReaderTool.UNDERLINE -> MarkupAnnotation(
                id = "preview",
                kind = MarkupKind.UNDERLINE,
                bounds = manualMarkup(start, end),
                color = annotationColor,
            )
            ReaderTool.STRIKE_THROUGH -> MarkupAnnotation(
                id = "preview",
                kind = MarkupKind.STRIKE_THROUGH,
                bounds = manualMarkup(start, end),
                color = annotationColor,
            )
            ReaderTool.TEXT_BOX -> ShapeAnnotation(
                id = "preview",
                kind = ShapeKind.RECTANGLE,
                start = start,
                end = end,
                width = shapeWidth,
                color = annotationColor,
            ).resized(start, end)
            ReaderTool.LINE, ReaderTool.ARROW, ReaderTool.RECTANGLE, ReaderTool.ELLIPSE -> {
                val kind = when (tool) {
                    ReaderTool.LINE -> ShapeKind.LINE
                    ReaderTool.ARROW -> ShapeKind.ARROW
                    ReaderTool.RECTANGLE -> ShapeKind.RECTANGLE
                    ReaderTool.ELLIPSE -> ShapeKind.ELLIPSE
                    else -> error("Unsupported shape preview")
                }
                ShapeAnnotation(
                    id = "preview",
                    kind = kind,
                    start = start,
                    end = end,
                    width = shapeWidth,
                    color = annotationColor,
                ).resized(start, end)
            }
            else -> null
        }
    }

    private fun previewInk(kind: InkKind, width: Float): InkAnnotation? =
        if (previewPoints.isEmpty()) {
            null
        } else {
            InkAnnotation(
                id = "preview",
                kind = kind,
                width = width,
                points = strokePoints(previewPoints, straightLine),
                color = annotationColor,
            )
        }

    private fun resetGesture() {
        previewPoints.clear()
        dragStart = null
        dragCurrent = null
        gestureOriginal = null
        gesturePreview = null
        resizeHandle = null
        erasedIds.clear()
        invalidate()
    }

    private fun pageBounds(page: Bitmap): RectF {
        val fit = baseScale(page)
        val drawWidth = page.width * fit * zoom
        val drawHeight = page.height * fit * zoom
        return RectF(
            (width - drawWidth) / 2f + panX,
            (height - drawHeight) / 2f + panY,
            (width + drawWidth) / 2f + panX,
            (height + drawHeight) / 2f + panY,
        )
    }

    private fun baseScale(page: Bitmap): Float = when (pageFit) {
        PageFit.PAGE -> min(width.toFloat() / page.width, height.toFloat() / page.height)
        PageFit.WIDTH -> width.toFloat() / page.width
    }

    private fun canPan(): Boolean {
        val page = bitmap ?: return false
        return maxPanX(page) > 0f || maxPanY(page) > 0f
    }

    private fun canPanHorizontally(): Boolean {
        val page = bitmap ?: return false
        return maxPanX(page) > 0f
    }

    private fun clampPan() {
        val page = bitmap ?: return
        panX = panX.coerceIn(-maxPanX(page), maxPanX(page))
        panY = panY.coerceIn(-maxPanY(page), maxPanY(page))
    }

    private fun applyHalfPagePart() {
        val page = bitmap ?: return
        panX = 0f
        panY = halfPagePan(maxPanY(page), halfPagePart)
    }

    private fun maxPanX(page: Bitmap): Float =
        max(0f, (page.width * baseScale(page) * zoom - width) / 2f)

    private fun maxPanY(page: Bitmap): Float =
        max(0f, (page.height * baseScale(page) * zoom - height) / 2f)

    private fun renderPage() {
        val file = source ?: return
        if (width < 1 || height < 1 || executor.isShutdown) return
        val requestedPage = pageIndex
        val request = ++generation
        executor.execute {
            try {
                val rendered = render(file, requestedPage)
                post {
                    if (request == generation) {
                        bitmap?.recycle()
                        bitmap = rendered
                        renderedPageIndex = requestedPage
                        zoom = 1f
                        panX = 0f
                        panY = halfPagePan(maxPanY(rendered), halfPagePart)
                        invalidate()
                    } else {
                        rendered.recycle()
                    }
                }
            } catch (_: Exception) {
                post { if (request == generation) onRenderError() }
            }
        }
    }

    private fun render(file: File, index: Int): Bitmap = ParcelFileDescriptor
        .open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        .use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(index in 0 until renderer.pageCount)
                renderer.openPage(index).use { page ->
                    val scale = minOf(
                        2f,
                        3_072f / page.width,
                        3_072f / page.height,
                        sqrt(12_000_000f / (page.width.toFloat() * page.height)),
                    )
                    createBitmap(
                        (page.width * scale).roundToInt(),
                        (page.height * scale).roundToInt(),
                        Bitmap.Config.ARGB_8888,
                    ).also { result ->
                        result.eraseColor(Color.WHITE)
                        page.render(result, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }

    private fun distance(first: NormalizedPoint, second: NormalizedPoint): Float = hypot(
        first.x - second.x,
        first.y - second.y,
    )

    private fun PageAnnotation.handleAt(
        point: NormalizedPoint,
        radius: Float,
    ): AnnotationHandle? = resizeHandles().minByOrNull { (_, handlePoint) ->
        distance(point, handlePoint)
    }?.takeIf { (_, handlePoint) -> distance(point, handlePoint) <= radius }?.key
}
