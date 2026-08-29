package cz.teply.sheetset.ui

import androidx.compose.ui.graphics.Color
import cz.teply.sheetset.pdf.AnnotationColor
import cz.teply.sheetset.pdf.AnnotationEditorSettings
import cz.teply.sheetset.pdf.PERSISTED_OBJECT_TOOLS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnotationToolMetadataTest {
    @Test
    fun selectionToolsUseDistinctIcons() {
        val select = objectToolMetadata.single { it.id == "select" }
        val lasso = objectToolMetadata.single { it.id == "lasso" }

        assertNotEquals(select.icon, lasso.icon)
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

    @Test
    fun presetIconUsesTheExactSelectedColor() {
        val custom = AnnotationColor(0xFF123456.toInt())
        val preset = AnnotationEditorSettings.defaults().preset("pen-1").copy(color = custom)

        assertEquals(Color(custom.argb), presetIconColor(preset))
    }

    @Test
    fun hexColorAcceptsRgbAndRejectsInvalidInput() {
        val expected = AnnotationColor(0xFF12ABEF.toInt())

        assertEquals(expected, parseHexAnnotationColor("#12ABEF"))
        assertEquals(expected, parseHexAnnotationColor("12abef"))
        assertEquals("#12ABEF", expected.rgbHex())
        assertNull(parseHexAnnotationColor("#12ABEG"))
        assertNull(parseHexAnnotationColor("#FF12ABEF"))
    }
}
