package cz.teply.sheetset.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.OutputStream
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PdfExporter {
    fun export(
        source: File,
        destination: OutputStream,
        annotations: DocumentAnnotations,
        highlighterAlpha: Int = 105,
    ) {
        require(highlighterAlpha in 0..255) { "Invalid highlighter alpha" }
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
                                    annotations.pages[index].orEmpty().forEach { annotation ->
                                        AnnotationRenderer.draw(
                                            outputPage.canvas,
                                            annotation,
                                            bounds,
                                            highlighterAlpha = highlighterAlpha,
                                        )
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
        return createBitmap(
            (page.width * scale).roundToInt().coerceAtLeast(1),
            (page.height * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        ).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        }
    }

}
