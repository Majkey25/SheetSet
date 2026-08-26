package cz.teply.sheetset.data

import android.content.Context
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import cz.teply.sheetset.pdf.AnnotationColor
import cz.teply.sheetset.pdf.AnnotationEditorSettings
import cz.teply.sheetset.pdf.DocumentAnnotations
import cz.teply.sheetset.pdf.NormalizedPoint
import cz.teply.sheetset.pdf.SymbolAnnotation
import cz.teply.sheetset.settings.AppSettings
import cz.teply.sheetset.settings.PageFit
import cz.teply.sheetset.settings.ReaderLayout
import cz.teply.sheetset.settings.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
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
                        SymbolAnnotation(
                            id = "symbol",
                            symbolId = "sharp",
                            center = NormalizedPoint(0.2f, 0.3f),
                            size = 0.08f,
                            rotationDegrees = 15f,
                            color = AnnotationColor.RED,
                            opacity = 180,
                        ),
                    ),
                ),
            )
            source.saveAnnotations(score.id, annotations)
            val expectedEditor = AnnotationEditorSettings.defaults().copy(
                palmRejection = true,
                quickColors = listOf(AnnotationColor.RED, AnnotationColor.BLUE),
            )
            val settings = AppSettings(
                pageFit = PageFit.WIDTH,
                pageTurnTaps = false,
                readerLayout = ReaderLayout.HALF,
                themeMode = ThemeMode.DARK,
                editor = expectedEditor,
            )
            val archive = ByteArrayOutputStream()
            source.createBackup(archive, settings, "cs")

            val restored = LibraryRepository(restoreRoot)
            val metadata = requireNotNull(
                restored.restoreBackup(ByteArrayInputStream(archive.toByteArray())),
            )

            assertEquals(source.load(), restored.load())
            assertEquals(annotations, restored.loadAnnotations(score.id))
            assertEquals(expectedEditor, metadata.settings.editor)
            assertTrue(restored.loadAnnotations(score.id).pages.getValue(0).single() is SymbolAnnotation)
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

    @Test
    fun versionOneBackupUsesNewReaderDefaults() {
        runBlocking {
            val source = LibraryRepository(sourceRoot)
            val pdf = createPdf(pages = 1)
            source.importPdf(PdfImport("Score.pdf", "application/pdf", pdf.length(), pdf::inputStream))
            val archive = ByteArrayOutputStream()
            source.createBackup(
                archive,
                AppSettings(readerLayout = ReaderLayout.HALF),
                null,
            )

            val restored = LibraryRepository(restoreRoot).restoreBackup(
                ByteArrayInputStream(asVersionOne(archive.toByteArray())),
            )

            val settings = requireNotNull(restored).settings
            assertEquals(ReaderLayout.SINGLE, settings.readerLayout)
            assertEquals(ThemeMode.LIGHT, settings.themeMode)
            assertEquals(AnnotationEditorSettings.defaults(), settings.editor)
            pdf.delete()
        }
    }

    private fun asVersionOne(archive: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        ZipInputStream(ByteArrayInputStream(archive)).use { input ->
            ZipOutputStream(output).use { zip ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    zip.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == "manifest.json") {
                        val manifest = JSONObject(input.readBytes().toString(Charsets.UTF_8))
                        manifest.put("version", 1)
                        manifest.getJSONObject("settings")
                            .remove("readerLayout")
                        manifest.getJSONObject("settings")
                            .remove("editor")
                        manifest.getJSONObject("settings")
                            .remove("themeMode")
                        zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                    } else {
                        input.copyTo(zip)
                    }
                    zip.closeEntry()
                    input.closeEntry()
                }
            }
        }
    }.toByteArray()

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
