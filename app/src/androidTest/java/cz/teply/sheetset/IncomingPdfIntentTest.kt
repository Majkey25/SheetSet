package cz.teply.sheetset

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingPdfIntentTest {
    @Test
    fun pdfActionViewReturnsItsContentUriOnce() {
        val uri = Uri.parse("content://scanner/view.pdf")
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")

        assertEquals(listOf(uri), IncomingPdfIntent.uris(intent))
    }

    @Test
    fun acceptedSourcesKeepEncounterOrderAndRemoveDuplicates() {
        val first = Uri.parse("content://scanner/first.pdf")
        val second = Uri.parse("content://scanner/second.pdf")
        val third = Uri.parse("content://scanner/third.pdf")
        val fourth = Uri.parse("content://scanner/fourth.pdf")
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val single = Intent(Intent.ACTION_SEND)
            .setDataAndType(second, "application/pdf")
            .putExtra(Intent.EXTRA_STREAM, first)
            .apply {
                clipData = ClipData.newUri(resolver, "PDFs", third).apply {
                    addItem(ClipData.Item(first))
                }
            }
        val multiple = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setDataAndType(third, "application/pdf")
            .putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(first, second, first, fourth),
            )

        assertEquals(listOf(first, second, third), IncomingPdfIntent.uris(single))
        assertEquals(listOf(first, second, fourth, third), IncomingPdfIntent.uris(multiple))
    }

    @Test
    fun unsupportedActionsMimeTypesSchemesAndNullsAreRejected() {
        val content = Uri.parse("content://scanner/score.pdf")
        val intents = listOf(
            Intent(),
            Intent(Intent.ACTION_EDIT).setDataAndType(content, "application/pdf"),
            Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, content),
            Intent(Intent.ACTION_SEND).setType("Application/Pdf").putExtra(Intent.EXTRA_STREAM, content),
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse("file:///sdcard/score.pdf"), "application/pdf"),
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse("https://example.com/score.pdf"), "application/pdf"),
            Intent(Intent.ACTION_SEND).setType("application/pdf"),
            Intent(Intent.ACTION_SEND).setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, "not a Uri"),
        )

        intents.forEach { assertTrue(IncomingPdfIntent.uris(it).isEmpty()) }
    }
}
