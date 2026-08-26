package cz.teply.sheetset.ui

import androidx.compose.ui.graphics.Color
import cz.teply.sheetset.pdf.PERSISTED_OBJECT_TOOLS
import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationToolMetadataTest {
    @Test
    fun darkPresetIconUsesReadableContentColorOnlyWhenNeeded() {
        val darkSurface = Color(0xFF292929)

        assertEquals(Color.White, readablePresetIconColor(Color.Black, darkSurface, Color.White))
        assertEquals(Color.Red, readablePresetIconColor(Color.Red, darkSurface, Color.White))
        assertEquals(Color.Black, readablePresetIconColor(Color.Black, Color.White, Color.Black))
    }

    @Test
    fun uiMetadataCoversEveryPersistedObjectToolExactlyOnce() {
        val persisted = PERSISTED_OBJECT_TOOLS.map { it.id to it.readerTool }
        val ui = objectToolMetadata.map { it.id to it.tool }
        val persistedIds = persisted.map { it.first }
        val persistedTools = persisted.map { it.second }
        val uiIds = ui.map { it.first }
        val uiTools = ui.map { it.second }

        assertEquals(persisted, ui)
        assertEquals(persistedIds.size, persistedIds.distinct().size)
        assertEquals(persistedTools.size, persistedTools.distinct().size)
        assertEquals(uiIds.size, uiIds.distinct().size)
        assertEquals(uiTools.size, uiTools.distinct().size)
        persistedIds.forEach { id -> assertEquals(1, uiIds.count { it == id }) }
    }
}
