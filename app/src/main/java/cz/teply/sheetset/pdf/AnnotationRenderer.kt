package cz.teply.sheetset.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import cz.teply.sheetset.settings.AnnotationTextSize
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object AnnotationRenderer {
    fun draw(
        canvas: Canvas,
        annotation: PageAnnotation,
        page: RectF,
        selected: Boolean = false,
        highlighterAlpha: Int = 105,
    ) {
        require(highlighterAlpha in 0..255) { "Invalid highlighter alpha" }
        when (annotation) {
            is InkAnnotation -> drawInk(canvas, annotation, page, highlighterAlpha)
            is MarkupAnnotation -> drawMarkup(canvas, annotation, page, highlighterAlpha)
            is TextBoxAnnotation -> drawTextBox(canvas, annotation, page)
            is ShapeAnnotation -> drawShape(canvas, annotation, page)
        }
        if (selected) drawSelection(canvas, annotation, page)
    }

    private fun drawInk(
        canvas: Canvas,
        annotation: InkAnnotation,
        page: RectF,
        highlighterAlpha: Int,
    ) {
        val paint = strokePaint(
            annotation.color.argb(),
            annotation.width * min(page.width(), page.height()),
            if (annotation.kind == InkKind.HIGHLIGHTER) highlighterAlpha else 255,
        ).apply {
            if (annotation.kind == InkKind.HIGHLIGHTER) {
                strokeCap = Paint.Cap.SQUARE
                strokeJoin = Paint.Join.BEVEL
            }
        }
        val first = annotation.points.first()
        if (annotation.points.size == 1) {
            canvas.drawPoint(first.x(page), first.y(page), paint)
            return
        }
        val path = Path().apply {
            moveTo(first.x(page), first.y(page))
            annotation.points.drop(1).forEach { point -> lineTo(point.x(page), point.y(page)) }
        }
        canvas.drawPath(path, paint)
    }

    private fun drawMarkup(
        canvas: Canvas,
        annotation: MarkupAnnotation,
        page: RectF,
        highlighterAlpha: Int,
    ) {
        val color = annotation.color.argb()
        annotation.bounds.forEach { normalized ->
            val bounds = normalized.toRectF(page)
            when (annotation.kind) {
                MarkupKind.HIGHLIGHT -> canvas.drawRect(
                    bounds,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        this.color = color
                        alpha = highlighterAlpha
                    },
                )
                MarkupKind.UNDERLINE -> canvas.drawLine(
                    bounds.left,
                    bounds.bottom,
                    bounds.right,
                    bounds.bottom,
                    strokePaint(color, max(2f, page.height() * 0.003f), 255),
                )
                MarkupKind.STRIKE_THROUGH -> canvas.drawLine(
                    bounds.left,
                    bounds.centerY(),
                    bounds.right,
                    bounds.centerY(),
                    strokePaint(color, max(2f, page.height() * 0.003f), 255),
                )
            }
        }
    }

    private fun drawTextBox(canvas: Canvas, annotation: TextBoxAnnotation, page: RectF) {
        val bounds = annotation.bounds.toRectF(page)
        val padding = max(4f, min(page.width(), page.height()) * 0.008f)
        canvas.drawRect(
            bounds,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
                alpha = 235
            },
        )
        canvas.drawRect(
            bounds,
            strokePaint(annotation.color.argb(), max(1.5f, page.height() * 0.002f), 255),
        )
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color.argb()
            textSize = when (annotation.size) {
                AnnotationTextSize.SMALL -> page.height() * 0.028f
                AnnotationTextSize.MEDIUM -> page.height() * 0.036f
                AnnotationTextSize.LARGE -> page.height() * 0.048f
            }.coerceAtLeast(10f)
        }
        val layout = StaticLayout.Builder.obtain(
            annotation.text,
            0,
            annotation.text.length,
            textPaint,
            (bounds.width() - padding * 2f).toInt().coerceAtLeast(1),
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(bounds.left + padding, bounds.top + padding)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawShape(canvas: Canvas, annotation: ShapeAnnotation, page: RectF) {
        val startX = annotation.start.x(page)
        val startY = annotation.start.y(page)
        val endX = annotation.end.x(page)
        val endY = annotation.end.y(page)
        val paint = strokePaint(
            annotation.color.argb(),
            annotation.width * min(page.width(), page.height()),
            255,
        )
        when (annotation.kind) {
            ShapeKind.LINE -> canvas.drawLine(startX, startY, endX, endY, paint)
            ShapeKind.ARROW -> {
                canvas.drawLine(startX, startY, endX, endY, paint)
                val angle = atan2(endY - startY, endX - startX)
                val length = hypot(endX - startX, endY - startY)
                val head = min(length * 0.25f, min(page.width(), page.height()) * 0.04f)
                val spread = 0.55f
                canvas.drawLine(
                    endX,
                    endY,
                    endX - cos(angle - spread) * head,
                    endY - sin(angle - spread) * head,
                    paint,
                )
                canvas.drawLine(
                    endX,
                    endY,
                    endX - cos(angle + spread) * head,
                    endY - sin(angle + spread) * head,
                    paint,
                )
            }
            ShapeKind.RECTANGLE -> canvas.drawRect(shapeBounds(annotation, page), paint)
            ShapeKind.ELLIPSE -> canvas.drawOval(shapeBounds(annotation, page), paint)
        }
    }

    private fun drawSelection(canvas: Canvas, annotation: PageAnnotation, page: RectF) {
        val bounds = selectionBounds(annotation, page)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.GRAY
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
        }
        canvas.drawRect(bounds, paint)
        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.DKGRAY
            strokeWidth = 2f
        }
        val radius = max(6f, min(page.width(), page.height()) * 0.007f)
        selectionHandles(annotation, bounds, page).forEach { (x, y) ->
            canvas.drawCircle(x, y, radius, handlePaint)
            canvas.drawCircle(x, y, radius, handleStroke)
        }
    }

    private fun selectionBounds(annotation: PageAnnotation, page: RectF): RectF {
        val result = when (annotation) {
            is InkAnnotation -> RectF(
                annotation.points.minOf { it.x }.normalizedX(page),
                annotation.points.minOf { it.y }.normalizedY(page),
                annotation.points.maxOf { it.x }.normalizedX(page),
                annotation.points.maxOf { it.y }.normalizedY(page),
            )
            is MarkupAnnotation -> RectF(
                annotation.bounds.minOf { it.left }.normalizedX(page),
                annotation.bounds.minOf { it.top }.normalizedY(page),
                annotation.bounds.maxOf { it.right }.normalizedX(page),
                annotation.bounds.maxOf { it.bottom }.normalizedY(page),
            )
            is TextBoxAnnotation -> annotation.bounds.toRectF(page)
            is ShapeAnnotation -> shapeBounds(annotation, page)
        }
        if (result.width() < 2f) result.inset(-6f, 0f)
        if (result.height() < 2f) result.inset(0f, -6f)
        return result
    }

    private fun selectionHandles(
        annotation: PageAnnotation,
        bounds: RectF,
        page: RectF,
    ): List<Pair<Float, Float>> = when (annotation) {
        is InkAnnotation -> emptyList()
        is ShapeAnnotation -> when (annotation.kind) {
            ShapeKind.LINE, ShapeKind.ARROW -> listOf(
                annotation.start.x(page) to annotation.start.y(page),
                annotation.end.x(page) to annotation.end.y(page),
            )
            ShapeKind.RECTANGLE, ShapeKind.ELLIPSE -> bounds.corners()
        }
        is MarkupAnnotation, is TextBoxAnnotation -> bounds.corners()
    }

    private fun RectF.corners(): List<Pair<Float, Float>> = listOf(
        left to top,
        right to top,
        right to bottom,
        left to bottom,
    )

    private fun shapeBounds(annotation: ShapeAnnotation, page: RectF): RectF = RectF(
        min(annotation.start.x, annotation.end.x).normalizedX(page),
        min(annotation.start.y, annotation.end.y).normalizedY(page),
        max(annotation.start.x, annotation.end.x).normalizedX(page),
        max(annotation.start.y, annotation.end.y).normalizedY(page),
    )

    private fun strokePaint(color: Int, width: Float, alpha: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
            this.alpha = alpha
            strokeWidth = width.coerceAtLeast(1f)
        }

    private fun AnnotationColor.argb(): Int = when (this) {
        AnnotationColor.BLACK -> Color.rgb(17, 17, 17)
        AnnotationColor.RED -> Color.rgb(211, 47, 47)
        AnnotationColor.ORANGE -> Color.rgb(245, 124, 0)
        AnnotationColor.YELLOW -> Color.rgb(251, 192, 45)
        AnnotationColor.GREEN -> Color.rgb(56, 142, 60)
        AnnotationColor.BLUE -> Color.rgb(25, 118, 210)
        AnnotationColor.PURPLE -> Color.rgb(123, 31, 162)
        AnnotationColor.PINK -> Color.rgb(194, 24, 91)
    }

    private fun NormalizedPoint.x(page: RectF): Float = x.normalizedX(page)

    private fun NormalizedPoint.y(page: RectF): Float = y.normalizedY(page)

    private fun Float.normalizedX(page: RectF): Float = page.left + this * page.width()

    private fun Float.normalizedY(page: RectF): Float = page.top + this * page.height()

    private fun NormalizedRect.toRectF(page: RectF): RectF = RectF(
        left.normalizedX(page),
        top.normalizedY(page),
        right.normalizedX(page),
        bottom.normalizedY(page),
    )
}
