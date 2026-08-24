package cz.teply.sheetset.data

import android.content.Context
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import cz.teply.sheetset.pdf.AnnotationColor
import cz.teply.sheetset.pdf.DocumentAnnotations
import cz.teply.sheetset.pdf.InkAnnotation
import cz.teply.sheetset.pdf.InkKind
import cz.teply.sheetset.pdf.NormalizedPoint
import cz.teply.sheetset.settings.AppSettings
import cz.teply.sheetset.settings.PageFit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LibraryBackupTest {
    private lateinit var sourceRoot: File
    private lateinit var restoreRoot: File

    @Before
    fun setUp() {
        val cache = ApplicationProvider.getApplicationContext<Context>().cacheDir
        sourceRoot = File(cache, "backup-source-${UUID.randomUUID()}")
        restoreRoot = File(cache, "backup-restore-${UUID.randomUUID()}")
    }

    @After
    fun tearDown() {
        sourceRoot.deleteRecursively()
        restoreRoot.deleteRecursively()
    }

    @Test
    fun backupRestoresLibraryAnnotationsAndPreferences() {
        runBlocking {
            val source = LibraryRepository(sourceRoot)
            val pdf = createPdf(pages = 2)
            val score = source.importPdf(
                PdfImport("Score.pdf", "application/pdf", pdf.length(), pdf::inputStream),
            )
            val setlist = source.createSetlist("Show")
            source.addScoreToSetlist(setlist.id, score.id)
            val annotations = DocumentAnnotations(
                mapOf(
                    0 to listOf(
                        InkAnnotation(
                            id = "ink",
                            kind = InkKind.PEN,
                            width = 0.004f,
                            points = listOf(NormalizedPoint(0.2f, 0.3f)),
                            color = AnnotationColor.RED,
                        ),
                    ),
                ),
            )
            source.saveAnnotations(score.id, annotations)
            val settings = AppSettings(pageFit = PageFit.WIDTH, pageTurnTaps = false)
            val archive = ByteArrayOutputStream()
            source.createBackup(archive, settings, "cs")

            val restored = LibraryRepository(restoreRoot)
            val metadata = requireNotNull(
                restored.restoreBackup(ByteArrayInputStream(archive.toByteArray())),
            )

            assertEquals(source.load(), restored.load())
            assertEquals(annotations, restored.loadAnnotations(score.id))
            assertEquals(settings, metadata.settings)
            assertEquals("cs", metadata.languageTag)
            assertEquals(score.pageCount, restored.load().scores.single().pageCount)
            pdf.delete()
        }
    }

    @Test
    fun invalidArchiveCannotReplaceExistingLibrary() {
        runBlocking {
            val repository = LibraryRepository(restoreRoot)
            val pdf = createPdf(pages = 1)
            val score = repository.importPdf(
                PdfImport("Existing.pdf", "application/pdf", pdf.length(), pdf::inputStream),
            )
            val malicious = ByteArrayOutputStream().also { output ->
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry("../escape"))
                    zip.write(byteArrayOf(1))
                    zip.closeEntry()
                }
            }.toByteArray()

            assertThrows(BackupException::class.java) {
                runBlocking { repository.restoreBackup(ByteArrayInputStream(malicious)) }
            }

            assertEquals(listOf(score), repository.load().scores)
            assertFalse(File(restoreRoot.parentFile, "escape").exists())
            pdf.delete()
        }
    }

    private fun createPdf(pages: Int): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "backup-${UUID.randomUUID()}.pdf")
        val document = PdfDocument()
        try {
            repeat(pages) { index ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(300, 400, index + 1).create(),
                )
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return file
    }
}
