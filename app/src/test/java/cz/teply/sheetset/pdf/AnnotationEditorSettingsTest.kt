package cz.teply.sheetset.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets.UTF_8
import org.json.JSONArray
import org.json.JSONObject

class AnnotationEditorSettingsTest {
    @Test
    fun presetsRoundTripAndRejectDuplicateIds() {
        val settings = AnnotationEditorSettings.defaults().copy(
            quickColors = listOf(AnnotationColor.RED, AnnotationColor.BLUE),
            palmRejection = true,
        )

        assertEquals(settings, AnnotationEditorSettingsJson.decode(AnnotationEditorSettingsJson.encode(settings)))

        val duplicate = settings.presets.first()
        assertThrows(IllegalArgumentException::class.java) {
            settings.copy(presets = listOf(duplicate, duplicate))
        }
    }

    @Test
    fun invalidPresetBoundsAreRejected() {
        val settings = AnnotationEditorSettings.defaults()

        assertThrows(IllegalArgumentException::class.java) {
            settings.copy(presets = settings.presets.map { it.copy(width = 0) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            settings.copy(quickColors = List(9) { AnnotationColor.BLACK })
        }
        assertThrows(IllegalArgumentException::class.java) {
            settings.copy(drawOrder = listOf("pen-1", "pen-1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            settings.copy(
                presets = settings.presets.map {
                    if (it.id == "pen-1") it.copy(id = "unknown") else it
                },
                drawOrder = settings.drawOrder.map { if (it == "pen-1") "unknown" else it },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            settings.copy(objectOrder = settings.objectOrder + "select")
        }
        assertThrows(IllegalArgumentException::class.java) {
            settings.copy(presets = settings.presets.map { it.copy(visible = false) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            settings.copy(visibleObjectTools = emptySet())
        }
    }

    @Test
    fun orderAndVisibilityRoundTripExactly() {
        val defaults = AnnotationEditorSettings.defaults()
        val expected = defaults.copy(
            drawOrder = defaults.drawOrder.reversed(),
            objectOrder = defaults.objectOrder.reversed(),
            visibleObjectTools = setOf("select", "text-box"),
            quickColors = defaults.quickColors.reversed(),
        )

        assertEquals(expected, AnnotationEditorSettingsJson.decode(AnnotationEditorSettingsJson.encode(expected)))
    }

    @Test
    fun legacyObjectOrderKeepsOldOrderAndAddsNewTools() {
        val root = JSONObject(AnnotationEditorSettingsJson.encode(AnnotationEditorSettings.defaults()))
        root.put(
                "objectOrder",
                JSONArray(
                    listOf(
                        "select",
                        "eraser",
                        "underline",
                        "strike-through",
                        "text-box",
                        "line",
                        "arrow",
                        "rectangle",
                        "ellipse",
                    ),
                ),
            )
        root.remove("visibleObjectTools")
        val raw = root.toString()

        val migrated = AnnotationEditorSettingsJson.decode(raw)

        assertEquals(
            listOf(
                "select",
                "underline",
                "strike-through",
                "text-box",
                "line",
                "arrow",
                "rectangle",
                "ellipse",
                "lasso",
                "symbol",
            ),
            migrated.objectOrder,
        )
        assertEquals(migrated.objectOrder.toSet(), migrated.visibleObjectTools)
    }

    @Test
    fun multiByteJsonOverByteLimitIsRejected() {
        val multiByteEntry = "á".repeat(8_000)
        val raw = JSONObject(AnnotationEditorSettingsJson.encode(AnnotationEditorSettings.defaults()))
            .put("objectOrder", JSONArray().put(multiByteEntry))
            .toString()

        assertTrue(raw.length < 16 * 1024)
        assertTrue(raw.toByteArray(UTF_8).size > 16 * 1024)
        assertThrows(IllegalArgumentException::class.java) {
            AnnotationEditorSettingsJson.decode(raw)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnnotationEditorSettings.defaults().copy(objectOrder = listOf(multiByteEntry))
        }
    }
}
