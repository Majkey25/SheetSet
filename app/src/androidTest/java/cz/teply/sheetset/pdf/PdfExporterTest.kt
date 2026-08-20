package cz.teply.sheetset.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class PdfExporterTest {
    @Test
    fun exportAddsAnnotationAndPreservesOriginal() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = createBlankPdf(context, pages = 2)
        val destination = File(context.cacheDir, "export-${UUID.randomUUID()}.pdf")
        val beforeHash = source.sha256()
        val annotations = DocumentAnnotations(
            mapOf(
                0 to listOf(
                    Stroke(
                        AnnotationTool.PEN,
                        0.01f,
                        listOf(NormalizedPoint(0.2f, 0.2f), NormalizedPoint(0.8f, 0.8f)),
                    ),
                ),
            ),
        )

        destination.outputStream().use { output ->
            PdfExporter.export(source, output, annotations)
        }

        assertEquals(beforeHash, source.sha256())
        assertEquals(2, pageCount(destination))
        assertTrue(darkPixels(destination) > darkPixels(source) + 100)
        source.delete()
        destination.delete()
    }

    private fun createBlankPdf(context: Context, pages: Int): File {
        val file = File(context.cacheDir, "source-${UUID.randomUUID()}.pdf")
        val document = PdfDocument()
        try {
            repeat(pages) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return file
    }

    private fun pageCount(file: File): Int = ParcelFileDescriptor
        .open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        .use { descriptor -> PdfRenderer(descriptor).use { it.pageCount } }

    private fun darkPixels(file: File): Int = ParcelFileDescriptor
        .open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        .use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    var count = 0
                    for (y in 0 until bitmap.height) {
                        for (x in 0 until bitmap.width) {
                            if (Color.red(bitmap.getPixel(x, y)) < 128) count++
                        }
                    }
                    bitmap.recycle()
                    count
                }
            }
        }

    private fun File.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(readBytes())
        .joinToString("") { "%02x".format(it) }
}
