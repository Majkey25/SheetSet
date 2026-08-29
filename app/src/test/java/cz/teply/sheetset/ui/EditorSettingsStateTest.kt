package cz.teply.sheetset.ui

import cz.teply.sheetset.pdf.AnnotationColor
import cz.teply.sheetset.pdf.AnnotationEditorSettings
import cz.teply.sheetset.pdf.ReaderTool
import cz.teply.sheetset.settings.ReaderDefaultTool
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorSettingsStateTest {
    @Test
    fun consecutiveTransformsUseTheLatestLocalEditor() {
        val state = EditorSettingsState(AnnotationEditorSettings.defaults())
        state.update { editor ->
            editor.copy(
                presets = editor.presets.map { preset ->
                    if (preset.id == "pen-1") preset.copy(width = preset.width + 1) else preset
                },
            )
        }
        val custom = AnnotationColor(0xFF123456.toInt())

        val result = state.update { editor -> editor.copy(recentColors = listOf(custom)) }

        assertEquals(3, result.preset("pen-1").width)
        assertEquals(listOf(custom), result.recentColors)
    }

    @Test
    fun hiddenDefaultSelectUsesFirstVisibleObjectInPersistedOrder() {
        val defaults = AnnotationEditorSettings.defaults()
        val editor = defaults.copy(
            objectOrder = defaults.objectOrder.reversed(),
            visibleObjectTools = setOf("ellipse", "lasso"),
        )

        val resolved = editor.resolveVisibleSelection(ReaderDefaultTool.VIEW.requestedReaderTool())

        assertEquals(ReaderTool.ELLIPSE, resolved.tool)
        assertEquals("pen-1", resolved.preset.id)
    }

    @Test
    fun hiddenDefaultHighlighterUsesFirstVisibleDrawingPreset() {
        val defaults = AnnotationEditorSettings.defaults()
        val editor = defaults.copy(
            drawOrder = listOf("pen-2", "pen-1", "marker", "highlighter"),
            presets = defaults.presets.map { preset ->
                preset.copy(visible = preset.id in setOf("pen-1", "pen-2"))
            },
        )

        val resolved = editor.resolveVisibleSelection(
            ReaderDefaultTool.HIGHLIGHTER.requestedReaderTool(),
        )

        assertEquals(ReaderTool.PEN, resolved.tool)
        assertEquals("pen-2", resolved.preset.id)
    }
}
