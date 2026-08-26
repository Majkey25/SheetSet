package cz.teply.sheetset.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import cz.teply.sheetset.R
import cz.teply.sheetset.settings.AnnotationTextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class PdfExporterTest {
    @Test
    fun exportRendersEveryAnnotationTypeAndPreservesOriginal() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = createBlankPdf(context, pages = 2)
        val destination = File(context.cacheDir, "export-${UUID.randomUUID()}.pdf")
        val beforeHash = source.sha256()
        val annotations = DocumentAnnotations(
            mapOf(
                0 to listOf(
                    InkAnnotation(
                        id = "ink",
                        kind = InkKind.PEN,
                        width = 0.008f,
                        points = listOf(
                            NormalizedPoint(0.05f, 0.08f),
                            NormalizedPoint(0.35f, 0.18f),
                        ),
                        color = AnnotationColor.RED,
                        opacity = 128,
                    ),
                    MarkupAnnotation(
                        id = "highlight",
                        kind = MarkupKind.HIGHLIGHT,
                        bounds = listOf(NormalizedRect(0.05f, 0.25f, 0.4f, 0.34f)),
                        color = AnnotationColor.BLUE,
                        opacity = 64,
                    ),
                    MarkupAnnotation(
                        id = "underline",
                        kind = MarkupKind.UNDERLINE,
                        bounds = listOf(NormalizedRect(0.05f, 0.36f, 0.4f, 0.42f)),
                        color = AnnotationColor.BLUE,
                    ),
                    MarkupAnnotation(
                        id = "strike",
                        kind = MarkupKind.STRIKE_THROUGH,
                        bounds = listOf(NormalizedRect(0.05f, 0.44f, 0.4f, 0.5f)),
                        color = AnnotationColor.RED,
                    ),
                    TextBoxAnnotation(
                        id = "text",
                        bounds = NormalizedRect(0.05f, 0.55f, 0.45f, 0.7f),
                        text = "Allegro",
                        size = AnnotationTextSize.MEDIUM,
                    ),
                    ShapeAnnotation(
                        id = "line",
                        kind = ShapeKind.LINE,
                        start = NormalizedPoint(0.55f, 0.1f),
                        end = NormalizedPoint(0.9f, 0.1f),
                        width = 0.008f,
                    ),
                    ShapeAnnotation(
                        id = "arrow",
                        kind = ShapeKind.ARROW,
                        start = NormalizedPoint(0.55f, 0.22f),
                        end = NormalizedPoint(0.9f, 0.3f),
                        width = 0.008f,
                    ),
                    ShapeAnnotation(
                        id = "rectangle",
                        kind = ShapeKind.RECTANGLE,
                        start = NormalizedPoint(0.55f, 0.38f),
                        end = NormalizedPoint(0.9f, 0.52f),
                        width = 0.008f,
                    ),
                    ShapeAnnotation(
                        id = "ellipse",
                        kind = ShapeKind.ELLIPSE,
                        start = NormalizedPoint(0.55f, 0.6f),
                        end = NormalizedPoint(0.9f, 0.76f),
                        width = 0.008f,
                    ),
                    SymbolAnnotation(
                        id = "symbol",
                        symbolId = "sharp",
                        center = NormalizedPoint(0.75f, 0.88f),
                        size = 0.15f,
                        rotationDegrees = 0f,
                        color = AnnotationColor.BLACK,
                        opacity = 255,
                    ),
                ),
            ),
        )

        destination.outputStream().use { output ->
            PdfExporter.export(
                source,
                output,
                annotations,
                requireNotNull(ResourcesCompat.getFont(context, R.font.noto_music_regular)),
            )
        }

        assertEquals(beforeHash, source.sha256())
        assertEquals(2, pageCount(destination))
        assertTrue(darkPixels(destination) > darkPixels(source) + 500)
        assertTrue(
            pixelsNearColorIn(
                destination,
                0,
                0,
                140,
                90,
                Color.rgb(233, 151, 151),
                tolerance = 16,
            ) > 50,
        )
        assertTrue(
            pixelsNearColorIn(
                destination,
                0,
                85,
                140,
                145,
                Color.rgb(197, 221, 244),
                tolerance = 16,
            ) > 50,
        )
        assertTrue(darkPixelsIn(destination, 0, 200, 150, 300) > 50)
        assertTrue(darkPixelsIn(destination, 150, 0, 300, 320) > 200)
        assertTrue(darkPixelsIn(destination, 180, 320, 270, 400) > 20)
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

    private fun darkPixelsIn(
        file: File,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Int = pixelsIn(file, left, top, right, bottom) { color ->
        Color.red(color) < 128 && Color.green(color) < 128 && Color.blue(color) < 128
    }

    private fun pixelsNearColorIn(
        file: File,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        expected: Int,
        tolerance: Int,
    ): Int = pixelsIn(file, left, top, right, bottom) { color ->
        Color.red(color) in Color.red(expected) - tolerance..Color.red(expected) + tolerance &&
            Color.green(color) in Color.green(expected) - tolerance..Color.green(expected) + tolerance &&
            Color.blue(color) in Color.blue(expected) - tolerance..Color.blue(expected) + tolerance
    }

    private fun pixelsIn(
        file: File,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        matches: (Int) -> Boolean,
    ): Int = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(0).use { page ->
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                var count = 0
                for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(bitmap.height)) {
                    for (x in left.coerceAtLeast(0) until right.coerceAtMost(bitmap.width)) {
                        if (matches(bitmap.getPixel(x, y))) count++
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
