package cz.teply.sheetset.data

import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import cz.teply.sheetset.pdf.AnnotationTool
import cz.teply.sheetset.pdf.DocumentAnnotations
import cz.teply.sheetset.pdf.NormalizedPoint
import cz.teply.sheetset.pdf.Stroke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class LibraryRepositoryTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        root = File(context.cacheDir, "library-${UUID.randomUUID()}")
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun validPdfIsImportedAndPersists() = runBlocking {
        val source = createPdf("valid.pdf", pages = 2)
        val repository = LibraryRepository(root)

        val score = repository.importPdf(
            PdfImport(
                displayName = "Practice.pdf",
                mimeType = "application/pdf",
                declaredSize = source.length(),
                open = source::inputStream,
            ),
        )

        assertEquals("Practice", score.title)
        assertEquals(2, score.pageCount)
        assertEquals(
            File(root, "scores/${score.fileName}").canonicalPath,
            repository.pdfFile(score).canonicalPath,
        )
        assertEquals(listOf(score), LibraryRepository(root).load().scores)
    }

    @Test
    fun malformedPdfLeavesLibraryEmpty() {
        root.mkdirs()
        val source = File(root, "invalid-${UUID.randomUUID()}.pdf").apply {
            writeText("not a PDF")
        }
        val repository = LibraryRepository(root)

        assertThrows(PdfImportException::class.java) {
            runBlocking {
                repository.importPdf(
                    PdfImport(
                        displayName = "Broken.pdf",
                        mimeType = "application/pdf",
                        declaredSize = source.length(),
                        open = source::inputStream,
                    ),
                )
            }
        }
        assertEquals(LibraryCatalog(), runBlocking { repository.load() })
        source.delete()
    }

    @Test
    fun annotationsPersist() = runBlocking {
        val annotations = DocumentAnnotations(
            mapOf(
                0 to listOf(
                    Stroke(
                        AnnotationTool.PEN,
                        0.004f,
                        listOf(NormalizedPoint(0.25f, 0.75f)),
                    ),
                ),
            ),
        )
        val repository = LibraryRepository(root)

        repository.saveAnnotations("score-1", annotations)

        assertEquals(annotations, LibraryRepository(root).loadAnnotations("score-1"))
    }

    @Test
    fun importCancellationPropagates() {
        val cancellation = CancellationException("cancel import")

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                LibraryRepository(root).importPdf(
                    PdfImport("Cancelled.pdf", "application/pdf", 1L) { throw cancellation },
                )
            }
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun setlistsPersistAndScoreDeletionCleansReferences() = runBlocking {
        val source = createPdf("song.pdf", pages = 1)
        val repository = LibraryRepository(root)
        val score = repository.importPdf(
            PdfImport("Song.pdf", "application/pdf", source.length(), source::inputStream),
        )
        repeat(4) { index -> repository.createSetlist("Set ${index + 1}") }
        val firstSetlist = repository.load().setlists.first()
        repository.addScoreToSetlist(firstSetlist.id, score.id)

        repository.deleteScore(score.id)

        val reloaded = LibraryRepository(root).load()
        assertEquals(4, reloaded.setlists.size)
        assertTrue(reloaded.setlists.all { score.id !in it.scoreIds })
        assertTrue(reloaded.scores.isEmpty())
        assertTrue(!File(root, "scores/${score.fileName}").exists())
    }

    private fun createPdf(name: String, pages: Int): File {
        root.mkdirs()
        val file = File(root, "${UUID.randomUUID()}-$name")
        val document = PdfDocument()
        try {
            repeat(pages) { index ->
                val info = PdfDocument.PageInfo.Builder(600, 800, index + 1).create()
                val page = document.startPage(info)
                page.canvas.drawText("SheetSet ${index + 1}", 40f, 60f, android.graphics.Paint())
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return file
    }
}
