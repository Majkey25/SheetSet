package cz.teply.sheetset.data

import android.content.Context
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ScorePdfBackupTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        val cache = ApplicationProvider.getApplicationContext<Context>().cacheDir
        root = File(cache, "scorepdf-import-${UUID.randomUUID()}")
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun scorePdfBackupMergesScoresAndKeepsSetlistOccurrences() {
        runBlocking {
            val repository = LibraryRepository(root)
            val existingPdf = createPdf(1)
            repository.importPdf(
                PdfImport("Existing.pdf", "application/pdf", existingPdf.length(), existingPdf::inputStream),
            )
            val sharedPdf = createPdf(2)
            val archive = scorePdfArchive(
                pdf = sharedPdf,
                scores = listOf(
                    Triple(7, "First", "shared.pdf"),
                    Triple(9, "Duplicate", "shared.pdf"),
                ),
                setlistKeys = listOf(7, 9, 7),
            )

            val metadata = repository.restoreBackup(ByteArrayInputStream(archive))

            val catalog = repository.load()
            assertNull(metadata)
            assertEquals(listOf("Existing", "First", "Duplicate"), catalog.scores.map(Score::title))
            assertEquals(1, catalog.setlists.size)
            val titlesById = catalog.scores.associate { score -> score.id to score.title }
            assertEquals(
                listOf("First", "Duplicate", "First"),
                catalog.setlists.single().scoreIds.map(titlesById::get),
            )
            val imported = catalog.scores.drop(1)
            assertNotEquals(imported[0].fileName, imported[1].fileName)
            assertEquals(listOf(2, 2), imported.map(Score::pageCount))
            existingPdf.delete()
            sharedPdf.delete()
        }
    }

    @Test
    fun missingScoreReferenceLeavesExistingLibraryUntouched() {
        runBlocking {
            val repository = LibraryRepository(root)
            val existingPdf = createPdf(1)
            val existing = repository.importPdf(
                PdfImport("Existing.pdf", "application/pdf", existingPdf.length(), existingPdf::inputStream),
            )
            val sharedPdf = createPdf(1)
            val archive = scorePdfArchive(
                pdf = sharedPdf,
                scores = listOf(Triple(7, "First", "shared.pdf")),
                setlistKeys = listOf(7, 99),
            )

            assertThrows(BackupException::class.java) {
                runBlocking { repository.restoreBackup(ByteArrayInputStream(archive)) }
            }

            assertEquals(listOf(existing), repository.load().scores)
            assertEquals(1, File(root, "scores").listFiles().orEmpty().size)
            existingPdf.delete()
            sharedPdf.delete()
        }
    }

    private fun scorePdfArchive(
        pdf: File,
        scores: List<Triple<Int, String, String>>,
        setlistKeys: List<Int>,
    ): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            zip.entry("newscorebox.hive", scores.fold(ByteArray(0)) { bytes, score ->
                bytes + frame(score.first, score(score.second, score.third))
            })
            zip.entry("setlistbox.hive", frame(0, setlist("Show", setlistKeys)))
            zip.putNextEntry(ZipEntry("shared.pdf"))
            pdf.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }.toByteArray()

    private fun score(title: String, fileName: String): ByteArray = bytes {
        byte(32)
        byte(6)
        field(0) { string("2026-08-23 12:00:00") }
        field(1) { string(title) }
        field(2) {
            byte(9)
            uint32(0)
        }
        field(3) { dateTime() }
        field(4) { dateTime() }
        field(5) { string(fileName) }
    }

    private fun setlist(name: String, keys: List<Int>): ByteArray = bytes {
        byte(36)
        byte(2)
        field(0) { string(name) }
        field(1) {
            byte(10)
            uint32(keys.size)
            keys.forEach { key ->
                byte(1)
                double(key.toDouble())
            }
        }
    }

    private fun frame(key: Int, value: ByteArray?): ByteArray {
        val payload = bytes {
            byte(0)
            uint32(key)
            value?.let(::raw)
        }
        val length = payload.size + 8
        val withoutCrc = bytes {
            uint32(length)
            raw(payload)
        }
        val crc = CRC32().apply { update(withoutCrc) }.value.toInt()
        return bytes {
            raw(withoutCrc)
            uint32(crc)
        }
    }

    private fun createPdf(pages: Int): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "scorepdf-${UUID.randomUUID()}.pdf")
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

    private fun ZipOutputStream.entry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun bytes(write: HiveWriter.() -> Unit): ByteArray =
        HiveWriter().apply(write).toByteArray()

    private class HiveWriter {
        private val output = ByteArrayOutputStream()

        fun byte(value: Int) = output.write(value)

        fun uint32(value: Int) = raw(
            ByteBuffer.allocate(Int.SIZE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value)
                .array(),
        )

        fun double(value: Double) = raw(
            ByteBuffer.allocate(Double.SIZE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putDouble(value)
                .array(),
        )

        fun string(value: String) {
            val encoded = value.toByteArray(Charsets.UTF_8)
            byte(4)
            uint32(encoded.size)
            raw(encoded)
        }

        fun field(id: Int, write: HiveWriter.() -> Unit) {
            byte(id)
            write()
        }

        fun dateTime() {
            byte(18)
            double(0.0)
            byte(0)
        }

        fun raw(value: ByteArray) = output.write(value)

        fun toByteArray(): ByteArray = output.toByteArray()
    }
}
