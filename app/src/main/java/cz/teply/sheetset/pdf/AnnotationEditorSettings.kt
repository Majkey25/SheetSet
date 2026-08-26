package cz.teply.sheetset.pdf

import org.json.JSONArray
import org.json.JSONObject

private const val MAX_EDITOR_JSON_BYTES = 16 * 1024

val DEFAULT_DRAWING_PRESET_IDS = listOf("pen-1", "pen-2", "marker", "highlighter")

private val LEGACY_OBJECT_TOOL_ORDER = listOf(
    "select",
    "eraser",
    "underline",
    "strike-through",
    "text-box",
    "line",
    "arrow",
    "rectangle",
    "ellipse",
)

enum class AnnotationToolGroup { DRAW, OBJECTS }

enum class DrawingPresetKind { PEN, MARKER, HIGHLIGHTER }

data class DrawingPreset(
    val id: String,
    val kind: DrawingPresetKind,
    val color: AnnotationColor,
    val width: Int,
    val opacity: Int,
    val visible: Boolean = true,
)

data class AnnotationEditorSettings(
    val presets: List<DrawingPreset>,
    val drawOrder: List<String>,
    val objectOrder: List<String>,
    val visibleObjectTools: Set<String> = objectOrder.toSet(),
    val quickColors: List<AnnotationColor>,
    val recentColors: List<AnnotationColor> = emptyList(),
    val palmRejection: Boolean = false,
) {
    init {
        require(
            presets.size == DEFAULT_DRAWING_PRESET_IDS.size &&
                presets.map(DrawingPreset::id).toSet() == DEFAULT_DRAWING_PRESET_IDS.toSet(),
        ) {
            "Every known drawing preset is required exactly once"
        }
        require(presets.all { it.id.isNotBlank() && it.width in 1..40 && it.opacity in 0..255 }) {
            "Invalid drawing preset"
        }
        require(drawOrder.size == presets.size && drawOrder.toSet() == presets.map(DrawingPreset::id).toSet()) {
            "Drawing order must contain every preset exactly once"
        }
        require(presets.any(DrawingPreset::visible)) { "At least one drawing tool must remain visible" }
        require(
            objectOrder.size == DEFAULT_OBJECT_TOOL_ORDER.size &&
                objectOrder.toSet() == DEFAULT_OBJECT_TOOL_ORDER.toSet(),
        ) {
            "Object order must contain every object tool exactly once"
        }
        require(visibleObjectTools.isNotEmpty() && visibleObjectTools.all(objectOrder::contains)) {
            "At least one known object tool must remain visible"
        }
        require(quickColors.isNotEmpty() && quickColors.size <= 8 && quickColors.distinct() == quickColors) {
            "Quick colors must contain one to eight unique colors"
        }
        require(recentColors.size <= 4) { "Too many recent colors" }
        require(AnnotationEditorSettingsJson.encodedByteSize(this) <= MAX_EDITOR_JSON_BYTES) {
            "Editor settings are too large"
        }
    }

    companion object {
        fun defaults(): AnnotationEditorSettings = AnnotationEditorSettings(
            presets = listOf(
                DrawingPreset("pen-1", DrawingPresetKind.PEN, AnnotationColor.BLACK, 20, 255),
                DrawingPreset("pen-2", DrawingPresetKind.PEN, AnnotationColor.BLUE, 20, 255),
                DrawingPreset("marker", DrawingPresetKind.MARKER, AnnotationColor.GREEN, 30, 255),
                DrawingPreset("highlighter", DrawingPresetKind.HIGHLIGHTER, AnnotationColor.YELLOW, 40, 105),
            ),
            drawOrder = listOf("pen-1", "pen-2", "marker", "highlighter"),
            objectOrder = DEFAULT_OBJECT_TOOL_ORDER,
            quickColors = listOf(
                AnnotationColor.BLACK,
                AnnotationColor.RED,
                AnnotationColor.ORANGE,
                AnnotationColor.YELLOW,
                AnnotationColor.GREEN,
                AnnotationColor.BLUE,
                AnnotationColor.PURPLE,
                AnnotationColor.PINK,
            ),
        )
    }

    fun preset(id: String): DrawingPreset = presets.single { it.id == id }
}

object AnnotationEditorSettingsJson {
    fun encode(settings: AnnotationEditorSettings): String = settings.toJson().toString().also {
        require(it.toByteArray(Charsets.UTF_8).size <= MAX_EDITOR_JSON_BYTES) {
            "Editor settings are too large"
        }
    }

    fun decode(raw: String): AnnotationEditorSettings {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_EDITOR_JSON_BYTES) {
            "Editor settings are too large"
        }
        val root = JSONObject(raw)
        val objectOrder = root.getJSONArray("objectOrder").strings().migrateObjectOrder()
        return AnnotationEditorSettings(
            presets = root.getJSONArray("presets").presets(),
            drawOrder = root.getJSONArray("drawOrder").strings(),
            objectOrder = objectOrder,
            visibleObjectTools = root.optJSONArray("visibleObjectTools")
                ?.strings()
                ?.toSet()
                ?: objectOrder.toSet(),
            quickColors = root.getJSONArray("quickColors").colors(),
            recentColors = root.getJSONArray("recentColors").colors(),
            palmRejection = root.getBoolean("palmRejection"),
        )
    }

    internal fun encodedByteSize(settings: AnnotationEditorSettings): Int =
        settings.toJson().toString().toByteArray(Charsets.UTF_8).size

    private fun AnnotationEditorSettings.toJson(): JSONObject = JSONObject()
        .put("presets", JSONArray().apply { presets.forEach { put(it.toJson()) } })
        .put("drawOrder", JSONArray().apply { drawOrder.forEach(::put) })
        .put("objectOrder", JSONArray().apply { objectOrder.forEach(::put) })
        .put(
            "visibleObjectTools",
            JSONArray().apply { objectOrder.filter(visibleObjectTools::contains).forEach(::put) },
        )
        .put("quickColors", JSONArray().apply { quickColors.forEach { put(it.encoded()) } })
        .put("recentColors", JSONArray().apply { recentColors.forEach { put(it.encoded()) } })
        .put("palmRejection", palmRejection)

    private fun DrawingPreset.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("kind", kind.name)
        .put("color", color.encoded())
        .put("width", width)
        .put("opacity", opacity)
        .put("visible", visible)

    private fun JSONArray.presets(): List<DrawingPreset> = List(length()) { index ->
        getJSONObject(index).let { preset ->
            DrawingPreset(
                id = preset.getString("id"),
                kind = DrawingPresetKind.valueOf(preset.getString("kind")),
                color = AnnotationColor.decode(preset.getString("color")),
                width = preset.getInt("width"),
                opacity = preset.getInt("opacity"),
                visible = preset.getBoolean("visible"),
            )
        }
    }

    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

    private fun List<String>.migrateObjectOrder(): List<String> = when (this) {
        LEGACY_OBJECT_TOOL_ORDER -> filterNot { it == "eraser" } + listOf("lasso", "symbol")
        else -> this
    }

    private fun JSONArray.colors(): List<AnnotationColor> = List(length()) {
        AnnotationColor.decode(getString(it))
    }
}
