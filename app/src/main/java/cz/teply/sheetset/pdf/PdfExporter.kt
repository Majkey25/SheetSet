package cz.teply.sheetset.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.OutputStream
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PdfExporter {
    fun export(source: File, destination: OutputStream, annotations: DocumentAnnotations) {
        val document = PdfDocument()
        try {
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    repeat(renderer.pageCount) { index ->
                        renderer.openPage(index).use { page ->
                            val bitmap = renderPage(page)
                            try {
                                val outputPage = document.startPage(
                                    PdfDocument.PageInfo.Builder(page.width, page.height, index + 1).create(),
                                )
                                try {
                                    val bounds = RectF(0f, 0f, page.width.toFloat(), page.height.toFloat())
                                    outputPage.canvas.drawBitmap(bitmap, null, bounds, null)
                                    annotations.pages[index].orEmpty().forEach { stroke ->
                                        drawStroke(outputPage.canvas, stroke, bounds)
                                    }
                                } finally {
                                    document.finishPage(outputPage)
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
            document.writeTo(destination)
            destination.flush()
        } finally {
            document.close()
        }
    }

    private fun renderPage(page: PdfRenderer.Page): Bitmap {
        val scale = minOf(
            2f,
            4_096f / page.width,
            4_096f / page.height,
            sqrt(12_000_000f / (page.width.toFloat() * page.height)),
        )
        return Bitmap.createBitmap(
            (page.width * scale).roundToInt().coerceAtLeast(1),
            (page.height * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        ).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        }
    }

    private fun drawStroke(canvas: android.graphics.Canvas, stroke: Stroke, bounds: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = if (stroke.tool == AnnotationTool.PEN) Color.BLACK else Color.DKGRAY
            alpha = if (stroke.tool == AnnotationTool.PEN) 255 else 95
            strokeWidth = stroke.width * min(bounds.width(), bounds.height())
        }
        val first = stroke.points.first()
        val path = Path().apply {
            moveTo(bounds.left + first.x * bounds.width(), bounds.top + first.y * bounds.height())
            stroke.points.drop(1).forEach { point ->
                lineTo(bounds.left + point.x * bounds.width(), bounds.top + point.y * bounds.height())
            }
        }
        if (stroke.points.size == 1) {
            canvas.drawPoint(
                bounds.left + first.x * bounds.width(),
                bounds.top + first.y * bounds.height(),
                paint,
            )
        } else {
            canvas.drawPath(path, paint)
        }
    }
}
