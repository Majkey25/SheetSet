package cz.teply.sheetset.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.createBitmap
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
    PEN,
    HIGHLIGHTER,
    ERASER,
}

class PdfPageView(context: Context) : View(context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
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
    private var bitmap: Bitmap? = null
    private var generation = 0
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastTapAt = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private val preview = mutableListOf<NormalizedPoint>()

    init {
        isClickable = true
    }

    var tool = ReaderTool.VIEW
        set(value) {
            field = value
            preview.clear()
            invalidate()
        }
    var annotations: List<PageAnnotation> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var onPreviousPage: () -> Unit = {}
    var onNextPage: () -> Unit = {}
    var onPageClick: () -> Unit = {}
    var onAddAnnotation: (PageAnnotation) -> Unit = {}
    var onErase: (NormalizedPoint) -> Unit = {}
    var onRenderError: () -> Unit = {}

    fun showPage(file: File, index: Int) {
        if (source == file && pageIndex == index && bitmap != null) return
        source = file
        pageIndex = index
        renderPage()
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
        annotations.filterIsInstance<InkAnnotation>().forEach { drawInk(canvas, it, bounds) }
        val previewTool = when (tool) {
            ReaderTool.PEN -> InkKind.PEN
            ReaderTool.HIGHLIGHTER -> InkKind.HIGHLIGHTER
            else -> null
        }
        if (previewTool != null && preview.isNotEmpty()) {
            drawInk(canvas, previewTool, widthFor(previewTool), preview, bounds)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount > 1 || scaleDetector.isInProgress) {
            preview.clear()
            return true
        }
        if (tool == ReaderTool.VIEW) {
            if (handleViewTouch(event)) performClick()
            return true
        }
        return handleAnnotationTouch(event)
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
            MotionEvent.ACTION_MOVE -> if (zoom > 1f) {
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
        if (zoom == 1f && abs(dx) > 80f && abs(dx) > abs(dy)) {
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

    private fun handleAnnotationTouch(event: MotionEvent): Boolean {
        val page = bitmap ?: return true
        val bounds = pageBounds(page)
        val point = normalizePoint(
            event.x,
            event.y,
            PageBounds(bounds.left, bounds.top, bounds.width(), bounds.height()),
        )
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                preview.clear()
                point?.let { if (tool == ReaderTool.ERASER) onErase(it) else preview.add(it) }
            }
            MotionEvent.ACTION_MOVE -> point?.let { current ->
                if (tool == ReaderTool.ERASER) {
                    onErase(current)
                } else {
                    val previous = preview.lastOrNull()
                    if (previous == null || hypot(current.x - previous.x, current.y - previous.y) > 0.001f) {
                        if (preview.size < MAX_POINTS_PER_INK) preview.add(current)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> commitPreview()
            MotionEvent.ACTION_CANCEL -> {
                preview.clear()
                invalidate()
            }
        }
        return true
    }

    private fun commitPreview() {
        val annotationTool = when (tool) {
            ReaderTool.PEN -> InkKind.PEN
            ReaderTool.HIGHLIGHTER -> InkKind.HIGHLIGHTER
            else -> null
        }
        if (annotationTool != null && preview.isNotEmpty()) {
            onAddAnnotation(
                InkAnnotation(
                    id = UUID.randomUUID().toString(),
                    kind = annotationTool,
                    width = widthFor(annotationTool),
                    points = preview.toList(),
                ),
            )
        }
        preview.clear()
        invalidate()
    }

    private fun drawInk(canvas: Canvas, annotation: InkAnnotation, bounds: RectF) {
        drawInk(canvas, annotation.kind, annotation.width, annotation.points, bounds)
    }

    private fun drawInk(
        canvas: Canvas,
        kind: InkKind,
        lineWidth: Float,
        points: List<NormalizedPoint>,
        bounds: RectF,
    ) {
        val first = points.first()
        val path = Path().apply {
            moveTo(pointX(first, bounds), pointY(first, bounds))
            points.drop(1).forEach { point -> lineTo(pointX(point, bounds), pointY(point, bounds)) }
        }
        strokePaint.color = if (kind == InkKind.PEN) Color.BLACK else Color.DKGRAY
        strokePaint.alpha = if (kind == InkKind.PEN) 255 else 95
        strokePaint.strokeWidth = lineWidth * min(bounds.width(), bounds.height())
        if (points.size == 1) {
            canvas.drawPoint(pointX(first, bounds), pointY(first, bounds), strokePaint)
        } else {
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun pageBounds(page: Bitmap): RectF {
        val fit = min(width.toFloat() / page.width, height.toFloat() / page.height)
        val drawWidth = page.width * fit * zoom
        val drawHeight = page.height * fit * zoom
        return RectF(
            (width - drawWidth) / 2f + panX,
            (height - drawHeight) / 2f + panY,
            (width + drawWidth) / 2f + panX,
            (height + drawHeight) / 2f + panY,
        )
    }

    private fun clampPan() {
        val page = bitmap ?: return
        val fit = min(width.toFloat() / page.width, height.toFloat() / page.height)
        val maxX = max(0f, (page.width * fit * zoom - width) / 2f)
        val maxY = max(0f, (page.height * fit * zoom - height) / 2f)
        panX = panX.coerceIn(-maxX, maxX)
        panY = panY.coerceIn(-maxY, maxY)
    }

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
                        zoom = 1f
                        panX = 0f
                        panY = 0f
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

    private fun widthFor(kind: InkKind): Float = if (kind == InkKind.PEN) 0.004f else 0.02f

    private fun pointX(point: NormalizedPoint, bounds: RectF): Float = bounds.left + point.x * bounds.width()

    private fun pointY(point: NormalizedPoint, bounds: RectF): Float = bounds.top + point.y * bounds.height()
}
