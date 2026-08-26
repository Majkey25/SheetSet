# SeliaLists Contextual Annotation Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current annotation controls and flat settings menu with a contextual ScorePDF-style workflow that keeps SeliaLists branding, preserves old annotations, and remains reliable on Android 10 phones and tablets.

**Architecture:** Keep the existing Compose reader and custom `PdfPageView`. Extend the typed annotation model to version 3, add bounded editor settings, and drive one contextual two-row toolbar from Compose state. Keep geometry and persistence independent from Compose so unit tests cover migration, selection, lasso, transforms, and style changes.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android `View`, `PdfRenderer`, `SharedPreferences`, `org.json`, JUnit 4, Compose UI tests, Android instrumentation, Noto Music under SIL OFL 1.1.

**Spec:** `docs/superpowers/specs/2026-08-25-selialists-contextual-annotation-editor-design.md`

## Global constraints

- Keep `applicationId = "com.majkeylab.sheetset"`.
- Keep `minSdk = 29`, `compileSdk = 36`, and `targetSdk = 36`.
- Keep imported PDFs immutable. Save annotations separately and export to a new document.
- Keep app chrome black, white, and neutral gray. Use color only for annotation swatches and annotation content.
- Keep every editor action offline and unrestricted.
- Add every user-visible string to English, Czech, Slovak, German, and Polish resources.
- Keep each touch target at least 48 dp and expose localized content descriptions and selected-state semantics.
- Preserve version 1 and version 2 annotation data.
- Do not add a navigation framework, settings framework, dependency-injection layer, cloud service, OCR, metronome, timed scrolling, page reordering, or annotation layers.
- Preserve unrelated uncommitted changes in the worktree.
- Do not modify the SeliaScan application or repository. Integrate only through package `com.majkeylab.scanit` and Android intents.
- The existing SeliaLists rename modifies files that this plan also touches. Do not stage an overlapping file until the user approves the combined diff or the earlier change is committed separately.
- Do not commit, push, open a PR, publish, or upload an artifact until the user gives explicit approval for that exact action. Commit steps below are review boundaries and run only after approval.

## File structure

Create these focused files:

- `app/src/main/java/cz/teply/sheetset/pdf/AnnotationEditorSettings.kt`: preset IDs, drawing styles, quick colors, toolbar order, validation, and JSON codec.
- `app/src/main/java/cz/teply/sheetset/ui/AnnotationToolbar.kt`: the contextual toolbar, color panel, width controls, and tool-group switch.
- `app/src/main/java/cz/teply/sheetset/IncomingPdfIntent.kt`: bounded extraction of shared PDF `content://` URIs.
- `app/src/main/res/font/noto_music_regular.ttf`: the bundled Noto Music font from the official Google Fonts repository.
- `third_party/NotoMusic-OFL.txt`: the unmodified SIL Open Font License from the same source.
- `app/src/test/java/cz/teply/sheetset/pdf/AnnotationEditorSettingsTest.kt`: preset codec and validation tests.
- `app/src/androidTest/java/cz/teply/sheetset/pdf/PdfPageViewInputTest.kt`: editor touch, pinch, pan, eyedropper, and palm-rejection tests.

Modify these files:

- `app/src/main/java/cz/teply/sheetset/pdf/Annotations.kt`: version 3 data, color encoding, symbols, and migration.
- `app/src/main/java/cz/teply/sheetset/pdf/AnnotationGeometry.kt`: public normalized bounds, lasso, batch movement, duplication, and symbol rotation.
- `app/src/main/java/cz/teply/sheetset/pdf/AnnotationRenderer.kt`: stored opacity, symbol rendering, and multi-selection.
- `app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt`: pointer state machine and editor callbacks.
- `app/src/main/java/cz/teply/sheetset/pdf/PdfExporter.kt`: version 3 appearance and symbols.
- `app/src/main/java/cz/teply/sheetset/settings/AppSettings.kt`: `AnnotationEditorSettings` and palm-rejection state.
- `app/src/main/java/cz/teply/sheetset/settings/SettingsStore.kt`: bounded editor settings persistence and legacy conversion.
- `app/src/main/java/cz/teply/sheetset/ui/ReaderScreen.kt`: editor orchestration, selection state, text editing, and symbol chooser.
- `app/src/main/java/cz/teply/sheetset/ui/SettingsDrawer.kt`: grouped drawer and focused settings pages.
- `app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt`: ordered annotation saves and export without global highlighter opacity.
- `app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt`: updated editor and settings callbacks only if signatures change.
- `app/src/main/res/values*/strings.xml`: localized editor and settings copy.
- Existing annotation, exporter, settings, backup, reader, and Compose-flow tests.

---

### Task 1: Version 3 annotation data and migration

**Files:**

- Modify: `app/src/main/java/cz/teply/sheetset/pdf/Annotations.kt:14-354`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/AnnotationRenderer.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/PdfExporter.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/ReaderScreen.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt`
- Modify: `app/src/test/java/cz/teply/sheetset/pdf/AnnotationJsonMigrationTest.kt:1-140`
- Modify: `app/src/test/java/cz/teply/sheetset/pdf/AnnotationHistoryTest.kt:1-80`

**Interfaces:**

- Produces: `AnnotationColor`, `AnnotationTextAlignment`, and version 3 appearance fields on existing `PageAnnotation` types.
- Produces: `AnnotationColor.encoded(): String` and `AnnotationColor.decode(raw: String): AnnotationColor`.
- Preserves: `AnnotationJson.decode(raw: String): DocumentAnnotations` for versions 1, 2, and 3.

- [ ] **Step 1: Write failing version 2 migration and version 3 round-trip tests**

Add focused assertions like these:

```kotlin
@Test
fun versionTwoColorAndHighlighterOpacityMigrateToVersionThree() {
    val migrated = AnnotationJson.decode(
        """{"version":2,"pages":{"0":[{"id":"old","type":"ink","kind":"HIGHLIGHTER","color":"RED","width":0.01,"points":[[0.1,0.2],[0.3,0.4]]}]}}""",
    )

    val annotation = migrated.pages.getValue(0).single() as InkAnnotation
    assertEquals(AnnotationColor.RED, annotation.color)
    assertEquals(LEGACY_HIGHLIGHTER_OPACITY, annotation.opacity)
    assertTrue(AnnotationJson.encode(migrated).contains("\"version\":3"))
    assertTrue(AnnotationJson.encode(migrated).contains("#FFD32F2F"))
}

@Test
fun textAppearanceSurvivesVersionThreeRoundTrip() {
    val source = DocumentAnnotations(
        mapOf(
            0 to listOf(
                TextBoxAnnotation(
                    id = "text",
                    bounds = NormalizedRect(0.1f, 0.1f, 0.4f, 0.25f),
                    text = "rit.",
                    size = AnnotationTextSize.MEDIUM,
                    lineHeight = 1.3f,
                    alignment = AnnotationTextAlignment.CENTER,
                    color = AnnotationColor.BLACK,
                    opacity = 255,
                ),
            ),
        ),
    )

    assertEquals(source, AnnotationJson.decode(AnnotationJson.encode(source)))
}
```

- [ ] **Step 2: Run the migration tests and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "cz.teply.sheetset.pdf.AnnotationJsonMigrationTest" --no-daemon --console=plain
```

Expected: compilation fails because version 3 appearance fields do not exist.

- [ ] **Step 3: Implement version 3 types and encoding**

Replace the enum-backed color with a validated value type and add stored opacity:

```kotlin
@JvmInline
value class AnnotationColor(val argb: Int) {
    init {
        require(argb ushr 24 == 0xFF) { "Annotation color must be opaque" }
    }

    fun encoded(): String = "#" + argb.toUInt().toString(16).uppercase().padStart(8, '0')

    companion object {
        val BLACK = AnnotationColor(0xFF111111.toInt())
        val RED = AnnotationColor(0xFFD32F2F.toInt())
        val ORANGE = AnnotationColor(0xFFF57C00.toInt())
        val YELLOW = AnnotationColor(0xFFFBC02D.toInt())
        val GREEN = AnnotationColor(0xFF388E3C.toInt())
        val BLUE = AnnotationColor(0xFF1976D2.toInt())
        val PURPLE = AnnotationColor(0xFF7B1FA2.toInt())
        val PINK = AnnotationColor(0xFFC2185B.toInt())

        fun decode(raw: String): AnnotationColor {
            require(raw.matches(Regex("#[0-9A-Fa-f]{8}"))) { "Invalid annotation color" }
            return AnnotationColor(raw.drop(1).toUInt(16).toInt())
        }
    }
}

enum class AnnotationTextAlignment { START, CENTER, END }
```

Add `opacity` to ink, markup, and shape annotations. Add `lineHeight`, `alignment`, and `opacity` to text. Validate opacity in `0..255` and line height in `0.8f..2f`.

Set `AnnotationJson.VERSION` to `3`. Keep explicit version 1 and version 2 decoders. Map version 2 color names to the eight constants and inject the legacy opacity defaults.

Define these constants in `Annotations.kt`:

```kotlin
const val LEGACY_HIGHLIGHTER_OPACITY = 105
const val DEFAULT_ANNOTATION_OPACITY = 255
```

Version 2 Pen, Underline, Strike-through, Text, and Shape objects receive opacity 255. Version 2 Highlighter and Highlight objects receive opacity 105.

In the same task, replace enum color conversions with `annotation.color.argb`, update Compose swatches with `Color(color.argb)`, and make the reader and exporter use each annotation's stored opacity. Remove the global `highlighterAlpha` renderer/export parameter and update `SheetSetViewModel.exportPdf`. These mechanical call-site updates keep main compilation green after `AnnotationColor` stops being an enum.

- [ ] **Step 4: Run annotation model tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "cz.teply.sheetset.pdf.AnnotationJsonMigrationTest" --tests "cz.teply.sheetset.pdf.AnnotationHistoryTest" --no-daemon --console=plain
```

Expected: both classes pass.

- [ ] **Step 5: Review the diff and conditionally commit**

Run `git diff --check`. If explicit commit approval exists, run:

```powershell
git add app/src/main/java/cz/teply/sheetset/pdf/Annotations.kt app/src/main/java/cz/teply/sheetset/pdf/AnnotationRenderer.kt app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt app/src/main/java/cz/teply/sheetset/pdf/PdfExporter.kt app/src/main/java/cz/teply/sheetset/ui/ReaderScreen.kt app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt app/src/test/java/cz/teply/sheetset/pdf/AnnotationJsonMigrationTest.kt app/src/test/java/cz/teply/sheetset/pdf/AnnotationHistoryTest.kt
git commit -m "feat: migrate annotations to version 3"
```

### Task 2: Bounded drawing presets and editor settings

**Files:**

- Create: `app/src/main/java/cz/teply/sheetset/pdf/AnnotationEditorSettings.kt`
- Create: `app/src/test/java/cz/teply/sheetset/pdf/AnnotationEditorSettingsTest.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/settings/AppSettings.kt:1-22`
- Modify: `app/src/main/java/cz/teply/sheetset/settings/SettingsStore.kt:1-40`
- Modify: `app/src/androidTest/java/cz/teply/sheetset/settings/SettingsStoreTest.kt:1-60`

**Interfaces:**

- Produces: `AnnotationToolGroup`, `DrawingPresetKind`, `DrawingPreset`, `AnnotationEditorSettings`, and `AnnotationEditorSettingsJson`.
- Produces: `AnnotationEditorSettings.preset(id: String): DrawingPreset`.
- Adds: `AppSettings.editor: AnnotationEditorSettings`.

- [ ] **Step 1: Write failing preset codec and legacy conversion tests**

```kotlin
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
fun missingEditorJsonUsesLegacyPenAndHighlighterValues() {
    preferences.edit()
        .putString("pen_width", ToolSize.THICK.name)
        .putString("highlighter_strength", HighlightStrength.LIGHT.name)
        .apply()

    val editor = SettingsStore(preferences).load().editor
    assertEquals(40, editor.preset("pen-1").width)
    assertEquals(70, editor.preset("highlighter").opacity)
}
```

- [ ] **Step 2: Run tests and verify failure**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "cz.teply.sheetset.pdf.AnnotationEditorSettingsTest" --no-daemon --console=plain
```

Expected: compilation fails because editor settings do not exist.

- [ ] **Step 3: Implement preset types and bounded JSON**

Use these exact shapes:

```kotlin
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
    val quickColors: List<AnnotationColor>,
    val recentColors: List<AnnotationColor> = emptyList(),
    val palmRejection: Boolean = false,
) {
    companion object { fun defaults(): AnnotationEditorSettings }

    fun preset(id: String): DrawingPreset = presets.single { it.id == id }
}
```

Defaults use IDs `pen-1`, `pen-2`, `marker`, and `highlighter`. Validate four unique preset IDs, width in `1..40`, opacity in `0..255`, no duplicate order entries, at most eight quick colors, at most four recent colors, and an encoded JSON length of at most 16 KiB.

Add `editor` to `AppSettings`. Persist it as `annotation_editor_json`. If the key is absent, derive initial values from `pen_width` and `highlighter_strength`. Keep writing the legacy keys until Task 8 removes the transitional fields.

- [ ] **Step 4: Run unit and instrumentation settings tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "cz.teply.sheetset.pdf.AnnotationEditorSettingsTest" :app:assembleDebugAndroidTest --no-daemon --console=plain
```

Expected: preset unit tests pass and the instrumentation APK compiles.

- [ ] **Step 5: Review the diff and conditionally commit**

If explicit commit approval exists:

```powershell
git add app/src/main/java/cz/teply/sheetset/pdf/AnnotationEditorSettings.kt app/src/test/java/cz/teply/sheetset/pdf/AnnotationEditorSettingsTest.kt app/src/main/java/cz/teply/sheetset/settings/AppSettings.kt app/src/main/java/cz/teply/sheetset/settings/SettingsStore.kt app/src/androidTest/java/cz/teply/sheetset/settings/SettingsStoreTest.kt
git commit -m "feat: add annotation tool presets"
```

### Task 3: Lasso, batch transforms, and symbol geometry

**Files:**

- Modify: `app/src/main/java/cz/teply/sheetset/pdf/AnnotationGeometry.kt:1-220`
- Modify: `app/src/test/java/cz/teply/sheetset/pdf/AnnotationGeometryTest.kt:1-150`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/Annotations.kt`

**Interfaces:**

- Produces: `PageAnnotation.normalizedBounds(): NormalizedRect`.
- Produces: `List<PageAnnotation>.lassoSelection(bounds: NormalizedRect): Set<String>`.
- Produces: `List<PageAnnotation>.translateSelection(ids: Set<String>, dx: Float, dy: Float): List<PageAnnotation>`.
- Produces: `List<PageAnnotation>.duplicateSelection(ids: Set<String>, idFactory: () -> String): List<PageAnnotation>`.
- Produces: `AnnotationHistory.commit(annotations: List<PageAnnotation>): AnnotationHistory` for one batch history step.

- [ ] **Step 1: Write failing lasso and batch tests**

```kotlin
@Test
fun lassoSelectsIntersectingAnnotationsAndBatchTranslationClampsTogether() {
    val first = rectangle("first", 0.1f, 0.1f, 0.2f, 0.2f)
    val second = rectangle("second", 0.8f, 0.8f, 0.95f, 0.95f)
    val annotations = listOf(first, second)

    assertEquals(
        setOf("first"),
        annotations.lassoSelection(NormalizedRect(0.05f, 0.05f, 0.25f, 0.25f)),
    )
    val moved = annotations.translateSelection(setOf("second"), 0.2f, 0.2f)
    assertEquals(1f, moved.last().normalizedBounds().right, 0.0001f)
    assertEquals(1f, moved.last().normalizedBounds().bottom, 0.0001f)
}

@Test
fun duplicateSelectionCreatesNewIdsAndOffsetCopies() {
    val duplicated = listOf(rectangle("source", 0.2f, 0.2f, 0.3f, 0.3f))
        .duplicateSelection(setOf("source")) { "copy" }
    assertEquals(listOf("source", "copy"), duplicated.map(PageAnnotation::id))
    assertEquals(0.21f, duplicated.last().normalizedBounds().left, 0.0001f)
}
```

- [ ] **Step 2: Run geometry tests and verify failure**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "cz.teply.sheetset.pdf.AnnotationGeometryTest" --no-daemon --console=plain
```

Expected: compilation fails on the new geometry functions.

- [ ] **Step 3: Implement normalized bounds and selection operations**

Promote the existing private bounds calculation to `normalizedBounds()`. Add rectangle intersection with inclusive edges. Batch translation must calculate one safe delta for the union of all selected bounds so selected objects retain their spacing.

Duplication must preserve source order, create one new ID per source, and apply a `0.01f` offset on both axes when the union remains in page bounds.

Expose the existing private history replacement through:

```kotlin
fun commit(next: List<PageAnnotation>): AnnotationHistory =
    if (next == annotations) this else replace(next)
```

- [ ] **Step 4: Run geometry and history tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "cz.teply.sheetset.pdf.AnnotationGeometryTest" --tests "cz.teply.sheetset.pdf.AnnotationHistoryTest" --no-daemon --console=plain
```

Expected: both classes pass.

- [ ] **Step 5: Review the diff and conditionally commit**

If explicit commit approval exists:

```powershell
git add app/src/main/java/cz/teply/sheetset/pdf/AnnotationGeometry.kt app/src/main/java/cz/teply/sheetset/pdf/Annotations.kt app/src/test/java/cz/teply/sheetset/pdf/AnnotationGeometryTest.kt
git commit -m "feat: add annotation lasso and batch transforms"
```

### Task 4: Version 3 renderer, musical symbols, and export

**Files:**

- Create: `app/src/main/res/font/noto_music_regular.ttf`
- Create: `third_party/NotoMusic-OFL.txt`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/Annotations.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/AnnotationGeometry.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/AnnotationRenderer.kt:1-260`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/PdfExporter.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt`
- Modify: `app/src/androidTest/java/cz/teply/sheetset/pdf/PdfExporterTest.kt:1-205`

**Interfaces:**

- Consumes: version 3 annotations from Task 1.
- Produces: `SymbolAnnotation`, stable supported-symbol IDs, and JSON version 3 symbol encoding.
- Produces: `SymbolAnnotation.rotated(degrees: Float): SymbolAnnotation`.
- Produces: `AnnotationRenderer.draw(..., symbolTypeface: Typeface)`.
- Produces: identical stored appearance in the reader and exported PDF.

- [ ] **Step 1: Extend the exporter test with stored opacity and a symbol**

Add a `SymbolAnnotation(symbolId = "sharp", ...)`, a half-opacity red pen, and a low-opacity blue highlight to `exportRendersEveryAnnotationTypeAndPreservesOriginal`. Assert dark pixels in the symbol bounds, red pixels in the pen bounds, and blue-tinted pixels in the highlight bounds. Keep the original-file SHA-256 assertion.

- [ ] **Step 2: Run the exporter test build and verify failure**

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --console=plain
```

Expected: compilation fails until the renderer and exporter accept `SymbolAnnotation` and stored opacity.

- [ ] **Step 3: Add the licensed font and implement rendering**

Use these official sources:

- `https://github.com/google/fonts/raw/main/ofl/notomusic/NotoMusic-Regular.ttf`
- `https://raw.githubusercontent.com/google/fonts/main/ofl/notomusic/OFL.txt`

Store the files at the paths above without modification. Load the font once with `ResourcesCompat.getFont(context, R.font.noto_music_regular)` and pass the resulting `Typeface` to both reader and exporter rendering.

Add the symbol model and its validation:

```kotlin
val SUPPORTED_SYMBOL_IDS = setOf(
    "sharp", "flat", "natural", "fermata", "accent", "breath",
    "crescendo", "decrescendo", "p", "mf", "f", "ff",
)

data class SymbolAnnotation(
    override val id: String,
    val symbolId: String,
    val center: NormalizedPoint,
    val size: Float,
    val rotationDegrees: Float,
    val color: AnnotationColor,
    val opacity: Int,
) : PageAnnotation

fun SymbolAnnotation.rotated(degrees: Float): SymbolAnnotation = copy(
    rotationDegrees = ((degrees % 360f) + 360f) % 360f,
)
```

Validate symbol ID membership, size in `0.01f..0.5f`, rotation in `-360f..360f`, and opacity in `0..255`. Add the `symbol` branch to version 3 JSON. Add exhaustive symbol branches to history, geometry, renderer, and every `PageAnnotation` `when` expression in this task.

Render symbols by stable ID through one exhaustive map:

```kotlin
private val symbolGlyphs = mapOf(
    "sharp" to "♯",
    "flat" to "♭",
    "natural" to "♮",
    "fermata" to "𝄐",
    "accent" to "𝆓",
    "breath" to "𝄒",
    "crescendo" to "<",
    "decrescendo" to ">",
    "p" to "p",
    "mf" to "mf",
    "f" to "f",
    "ff" to "ff",
)
```

Apply symbol rotation around its center. Draw selection bounds and handles after content so handles remain visible.

Load the font in `PdfPageView` with `requireNotNull(ResourcesCompat.getFont(context, R.font.noto_music_regular))`. Change `PdfExporter.export` to accept a `Typeface`; load and pass the same resource from `SheetSetViewModel.exportPdf`. Update `PdfExporterTest` to pass the resource font.

- [ ] **Step 4: Run exporter instrumentation on a dedicated emulator during Task 11**

For this task, run compilation plus unit tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest --no-daemon --console=plain
```

Expected: compilation and unit tests pass. Task 11 runs the real exporter instrumentation.

- [ ] **Step 5: Review the license and conditionally commit**

Confirm that `third_party/NotoMusic-OFL.txt` contains the SIL Open Font License 1.1 and that the font file is below 1 MiB. If explicit commit approval exists:

```powershell
git add app/src/main/res/font/noto_music_regular.ttf third_party/NotoMusic-OFL.txt app/src/main/java/cz/teply/sheetset/pdf/Annotations.kt app/src/main/java/cz/teply/sheetset/pdf/AnnotationGeometry.kt app/src/main/java/cz/teply/sheetset/pdf/AnnotationRenderer.kt app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt app/src/main/java/cz/teply/sheetset/pdf/PdfExporter.kt app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt app/src/androidTest/java/cz/teply/sheetset/pdf/PdfExporterTest.kt
git commit -m "feat: render styled annotations and music symbols"
```

### Task 5: Reliable editor pointer state machine

**Files:**

- Modify: `app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt:27-640`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/AnnotationGeometry.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/PdfViewport.kt:1-10`
- Modify: `app/src/test/java/cz/teply/sheetset/pdf/PdfViewportTest.kt`
- Create: `app/src/androidTest/java/cz/teply/sheetset/pdf/PdfPageViewInputTest.kt`

**Interfaces:**

- Produces: `ReaderTool.LASSO`, `ReaderTool.SYMBOL`, and existing drawing or object tools.
- Produces callbacks: `onSelectionChange: (Set<String>) -> Unit`, `onUpdateAnnotations: (List<PageAnnotation>) -> Unit`, `onDeleteAnnotations: (Set<String>) -> Unit`, and `onSampleColor: (AnnotationColor) -> Unit`.
- Consumes: `AnnotationEditorSettings`, active `DrawingPreset`, and geometry from Task 3.

- [ ] **Step 1: Write failing viewport and touch tests**

Add a pure focus-anchored viewport test:

```kotlin
@Test
fun pinchKeepsTheContentUnderTheFocusPointStable() {
    val before = PdfViewport(zoom = 1f, panX = 0f, panY = 0f)
    val after = before.scaledAround(factor = 2f, focusX = 300f, focusY = 500f)
    assertEquals(2f, after.zoom)
    assertEquals(-300f, after.panX, 0.001f)
    assertEquals(-500f, after.panY, 0.001f)
}
```

In `PdfPageViewInputTest`, create a one-page PDF and verify:

- a pen stroke commits one annotation;
- a canceled stroke commits none;
- a two-finger gesture while Pen is active changes the viewport and commits no stroke;
- palm rejection ignores a finger draw after a stylus-down event;
- eyedropper sampling inside the page returns the bitmap color;
- lasso returns the expected ID set.

- [ ] **Step 2: Run the pure viewport test and verify failure**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "cz.teply.sheetset.pdf.PdfViewportTest" --no-daemon --console=plain
```

Expected: compilation fails because `PdfViewport.scaledAround` does not exist.

- [ ] **Step 3: Implement focus-anchored zoom and two-finger pan**

Add this pure state type next to `halfPagePan`:

```kotlin
data class PdfViewport(val zoom: Float, val panX: Float, val panY: Float) {
    fun scaledAround(factor: Float, focusX: Float, focusY: Float): PdfViewport {
        val nextZoom = (zoom * factor).coerceIn(1f, 5f)
        val ratio = nextZoom / zoom
        return copy(
            zoom = nextZoom,
            panX = focusX - (focusX - panX) * ratio,
            panY = focusY - (focusY - panY) * ratio,
        )
    }
}
```

`PdfPageView` must track the two-pointer centroid and apply its delta to pan while scaling. Any multi-pointer gesture cancels the pending draw preview without committing it.

- [ ] **Step 4: Implement editor gestures and callbacks**

Replace the single selected ID with a set. Keep the existing topmost-hit behavior for Select. Add lasso preview and commit selection on up. Add one eyedropper state that consumes the next in-page tap and returns to the previous tool.

Use `MotionEvent.getToolType(index)` for stylus detection. If palm rejection is enabled and a stylus is active, ignore one-finger `TOOL_TYPE_FINGER` drawing. Never ignore two-finger zoom and pan.

Commit one `onUpdateAnnotations` call after each move, resize, rotation, or batch move. Do not emit updates on every move event.

- [ ] **Step 5: Compile the input test and run unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest --no-daemon --console=plain
```

Expected: unit tests pass and `PdfPageViewInputTest` compiles. Task 11 runs it on the emulator.

- [ ] **Step 6: Review the diff and conditionally commit**

If explicit commit approval exists:

```powershell
git add app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt app/src/main/java/cz/teply/sheetset/pdf/AnnotationGeometry.kt app/src/main/java/cz/teply/sheetset/pdf/PdfViewport.kt app/src/test/java/cz/teply/sheetset/pdf/PdfViewportTest.kt app/src/androidTest/java/cz/teply/sheetset/pdf/PdfPageViewInputTest.kt
git commit -m "feat: make editor gestures predictable"
```

### Task 6: Contextual annotation toolbar and editor dialogs

**Files:**

- Create: `app/src/main/java/cz/teply/sheetset/ui/AnnotationToolbar.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/ReaderScreen.kt:87-944`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-cs/strings.xml`
- Modify: `app/src/main/res/values-sk/strings.xml`
- Modify: `app/src/main/res/values-de/strings.xml`
- Modify: `app/src/main/res/values-pl/strings.xml`
- Modify: `app/src/androidTest/java/cz/teply/sheetset/SheetSetFlowTest.kt:386-477`

**Interfaces:**

- Consumes: `AnnotationEditorSettings`, `DrawingPreset`, `ReaderTool`, and Task 5 callbacks.
- Produces: `AnnotationToolbar` with typed callbacks and `ColorPanel`.
- Produces: text create and edit dialogs plus a musical-symbol chooser.

- [ ] **Step 1: Replace the existing toolbar test with failing contextual-state tests**

Update `readerOpensAnnotationTools` and add:

```kotlin
@Test
fun selectedTextShowsObjectActionsInsteadOfDrawingProperties() {
    setReaderContent(textAnnotation = sampleText())
    composeRule.onNodeWithContentDescription("Annotate").performClick()
    composeRule.onNodeWithContentDescription("Objects").performClick()
    composeRule.onNodeWithContentDescription("Select").performClick()
    composeRule.onNodeWithContentDescription("PDF page 1 of 1").performTouchInput {
        click(Offset(center.x, center.y))
    }

    composeRule.onNodeWithContentDescription("Edit text").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Duplicate").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Delete annotation").assertIsDisplayed()
    composeRule.onAllNodesWithContentDescription("Straight line").assertCountEquals(0)
}

@Test
fun compactToolbarShowsOnePersistentColorControl() {
    setReaderContent()
    composeRule.onNodeWithContentDescription("Annotate").performClick()
    composeRule.onAllNodesWithContentDescription("Color").assertCountEquals(1)
    listOf("Red", "Orange", "Yellow", "Green", "Blue", "Purple", "Pink").forEach {
        composeRule.onAllNodesWithContentDescription(it).assertCountEquals(0)
    }
}
```

Add these concrete helpers to `SheetSetFlowTest`:

```kotlin
private fun sampleText(): TextBoxAnnotation = TextBoxAnnotation(
    id = "text",
    bounds = NormalizedRect(0.4f, 0.4f, 0.6f, 0.6f),
    text = "rit.",
    size = AnnotationTextSize.MEDIUM,
    lineHeight = 1.2f,
    alignment = AnnotationTextAlignment.CENTER,
    color = AnnotationColor.BLACK,
    opacity = 255,
)

private fun setReaderContent(
    textAnnotation: TextBoxAnnotation? = null,
    settings: AppSettings = AppSettings(),
) {
    val score = Score("score-1", "Song", "score-1.pdf", 1, 1L)
    val annotations = textAnnotation?.let { DocumentAnnotations(mapOf(0 to listOf(it))) }
        ?: DocumentAnnotations()
    composeRule.setContent {
        SheetSetTheme {
            SheetSetApp(
                LibraryUiState(
                    catalog = LibraryCatalog(scores = listOf(score)),
                    reader = ReaderUiState(
                        score = score,
                        file = File("missing.pdf"),
                        scoreIds = listOf(score.id),
                        scoreIndex = 0,
                        pageIndex = 0,
                        annotations = annotations,
                    ),
                    settings = settings,
                ),
                SheetSetActions(),
            )
        }
    }
}
```

- [ ] **Step 2: Run the targeted instrumentation compile and verify failure**

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --console=plain
```

Expected: the new semantic controls do not exist.

- [ ] **Step 3: Extract the toolbar from `ReaderScreen.kt`**

Create a small state object and one toolbar entry point:

```kotlin
internal data class AnnotationToolbarState(
    val group: AnnotationToolGroup,
    val tool: ReaderTool,
    val preset: DrawingPreset,
    val selectedIds: Set<String>,
    val selectedAnnotation: PageAnnotation?,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val previousEnabled: Boolean,
    val nextEnabled: Boolean,
    val expanded: Boolean,
)

@Composable
internal fun AnnotationToolbar(
    state: AnnotationToolbarState,
    onGroup: (AnnotationToolGroup) -> Unit,
    onTool: (ReaderTool) -> Unit,
    onPreset: (String) -> Unit,
    onWidth: (Int) -> Unit,
    onColor: () -> Unit,
    onEyedropper: () -> Unit,
    onEditText: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
)
```

Use two 56 dp rows. Pin page navigation and Done when width permits. Keep the tool region horizontally scrollable. Show one current-color circle on compact layouts and quick colors on expanded layouts.

- [ ] **Step 4: Implement the color panel, text editor, and symbol chooser**

The color panel contains eight quick colors, four recent colors, hue, saturation, value, opacity, and a live preview. Use Compose controls already in Material 3. Do not add a color-picker dependency. A custom or eyedropper color moves to the front of the recent-color list, removes duplicates, and trims the list to four entries.

The text dialog edits create and existing-text cases through one function. The symbol chooser maps stable IDs to preview glyphs and localized labels. On confirm, return a `SymbolAnnotation` with a stable ID, not the glyph string.

- [ ] **Step 5: Wire selection and style actions in `ReaderScreen`**

Keep `AnnotationHistory` as the single in-memory source for the active page. Duplicate and delete act on the selected ID set. Editing color or width updates selected annotations in one history step. Switching tools clears selection. Entering Edit mode preserves zoom and pan in `PdfPageView`.

- [ ] **Step 6: Add all localized strings**

Add translations for Draw, Objects, Pen 1, Pen 2, Marker, Lasso, Musical symbol, Eyedropper, Duplicate, Edit text, Recent colors, Custom color, Opacity, Line height, Alignment, Palm rejection, and toolbar customization. Keep native diacritics.

- [ ] **Step 7: Compile and run unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest --no-daemon --console=plain
```

Expected: unit tests pass and all five resource sets compile.

- [ ] **Step 8: Review the diff and conditionally commit**

If explicit commit approval exists:

```powershell
git add app/src/main/java/cz/teply/sheetset/ui/AnnotationToolbar.kt app/src/main/java/cz/teply/sheetset/ui/ReaderScreen.kt app/src/main/res/values app/src/main/res/values-cs app/src/main/res/values-sk app/src/main/res/values-de app/src/main/res/values-pl app/src/androidTest/java/cz/teply/sheetset/SheetSetFlowTest.kt
git commit -m "feat: add contextual annotation toolbar"
```

### Task 7: Sectioned settings hierarchy

**Files:**

- Modify: `app/src/main/java/cz/teply/sheetset/ui/SettingsDrawer.kt:60-434`
- Modify: `app/src/main/java/cz/teply/sheetset/settings/AppSettings.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/settings/SettingsStore.kt`
- Modify: all five `strings.xml` resource files
- Modify: `app/src/androidTest/java/cz/teply/sheetset/SettingsFlowTest.kt:1-135`

**Interfaces:**

- Produces drawer screens: `READER`, `GESTURES`, `ANNOTATIONS`, `BACKUP`, `LANGUAGE`, and `APP_DETAILS`.
- Produces reusable flat components: `SettingsSectionTitle`, `SettingsNavigationRow`, `SettingsSwitchRow`, and `SettingsChoiceRow`.
- Consumes: `AnnotationEditorSettings` from Task 2.

- [ ] **Step 1: Write failing section and current-value tests**

```kotlin
@Test
fun menuGroupsReadingDataAndAppSettings() {
    setSettingsContent()
    composeRule.onNodeWithContentDescription("Menu").performClick()

    listOf("Library", "Reading", "Data", "App").forEach {
        composeRule.onNodeWithText(it).assertIsDisplayed()
    }
    listOf("Reader and page layout", "Gestures", "Annotation tools", "Backup and restore")
        .forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
    composeRule.onAllNodesWithText("Share backup").assertCountEquals(0)
}

@Test
fun readerChoicesShowCurrentValueAndOpenOneDialog() {
    setSettingsContent(settings = AppSettings(readerLayout = ReaderLayout.HALF))
    openMenuPage("Reader and page layout")
    composeRule.onNodeWithText("Half page").assertIsDisplayed().performClick()
    composeRule.onNodeWithText("Single page").assertIsDisplayed()
    composeRule.onNodeWithText("Two pages").assertIsDisplayed()
}
```

Add these helpers to `SettingsFlowTest`:

```kotlin
private fun setSettingsContent(settings: AppSettings = AppSettings()) {
    composeRule.setContent {
        SheetSetTheme {
            SheetSetApp(LibraryUiState(settings = settings), SheetSetActions())
        }
    }
}

private fun openMenuPage(name: String) {
    composeRule.onNodeWithContentDescription("Menu").performClick()
    composeRule.onNodeWithText(name).performClick()
}
```

- [ ] **Step 2: Run instrumentation compilation and verify failure**

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --console=plain
```

Expected: new section labels and pages do not exist.

- [ ] **Step 3: Group the drawer**

Replace the flat menu with four section labels and flat rows. Keep PDF and Setlists under Library. Add Reader and page layout, Gestures, and Annotation tools under Reading. Add one Backup and restore row under Data. Keep Language and App details under App.

The backup page calls the existing `onBackup`, `onShareBackup`, and `onRestore` callbacks. Each row contains a one-line description of create, share, or replace/merge behavior.

- [ ] **Step 4: Replace inline radio floods with current-value rows**

Use a row with headline, current-value supporting text, and chevron. Clicking the row opens one `AlertDialog` with the bounded options. Switches remain directly interactive.

Reader groups are Layout, Page turning, and Display. Gesture groups are Page turning, Zoom, and Input. Annotation groups are Default tool, Drawing presets, Toolbar tools, Quick colors, Text, and Stylus.

- [ ] **Step 5: Implement preset, tool-order, and color-order editors**

Use existing `LazyColumn` plus the drag pattern already used by setlist ordering. Persist the exact order through `AnnotationEditorSettings`. A hidden tool remains in the list with an off switch. Reject attempts to hide all drawing tools or all object tools.

- [ ] **Step 6: Update strings and settings tests**

Translate all section names, row summaries, and validation messages. Update existing Back tests so Back returns from a settings page to the grouped drawer, then closes the drawer.

- [ ] **Step 7: Run settings tests**

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest :app:testDebugUnitTest --no-daemon --console=plain
```

Expected: resources compile and unit tests pass. Task 11 runs `SettingsFlowTest` on-device.

- [ ] **Step 8: Review the diff and conditionally commit**

If explicit commit approval exists:

```powershell
git add app/src/main/java/cz/teply/sheetset/ui/SettingsDrawer.kt app/src/main/java/cz/teply/sheetset/settings/AppSettings.kt app/src/main/java/cz/teply/sheetset/settings/SettingsStore.kt app/src/main/res/values app/src/main/res/values-cs app/src/main/res/values-sk app/src/main/res/values-de app/src/main/res/values-pl app/src/androidTest/java/cz/teply/sheetset/SettingsFlowTest.kt
git commit -m "feat: group reader and app settings"
```

### Task 8: Ordered persistence, backup compatibility, and legacy cleanup

**Files:**

- Modify: `app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt:35-250`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt:68-100`
- Modify: `app/src/main/java/cz/teply/sheetset/settings/AppSettings.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/settings/SettingsStore.kt`
- Create: `app/src/test/java/cz/teply/sheetset/AnnotationSaveVersionsTest.kt`
- Modify: `app/src/androidTest/java/cz/teply/sheetset/data/LibraryBackupTest.kt:35-140`
- Modify: `app/src/androidTest/java/cz/teply/sheetset/SheetSetFlowTest.kt`

**Interfaces:**

- Produces ordered annotation writes that cannot overwrite a newer page state with an older request.
- Produces backup and restore coverage for editor settings and version 3 annotations.
- Removes transitional `penWidth` and `highlighterStrength` fields from `AppSettings` after all callers use `editor`.

- [ ] **Step 1: Write failing per-score version and backup tests**

Create a pure test for the save-version guard:

```kotlin
class AnnotationSaveVersionsTest {
    @Test
    fun newerRequestInvalidatesOnlyTheSameScore() {
        val versions = AnnotationSaveVersions()
        val firstA = versions.next("score-a")
        val firstB = versions.next("score-b")
        val secondA = versions.next("score-a")

        assertFalse(versions.isLatest("score-a", firstA))
        assertTrue(versions.isLatest("score-a", secondA))
        assertTrue(versions.isLatest("score-b", firstB))
    }
}
```

Extend `backupRestoresLibraryAnnotationsAndPreferences`:

```kotlin
val expectedSettings = AppSettings(
    editor = AnnotationEditorSettings.defaults().copy(
        palmRejection = true,
        quickColors = listOf(AnnotationColor.RED, AnnotationColor.BLUE),
    ),
)
assertEquals(expectedSettings.editor, restoredSettings.editor)
assertTrue(restoredAnnotations.pages.getValue(0).single() is SymbolAnnotation)
```

- [ ] **Step 2: Run targeted test compilation and verify failure**

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest :app:testDebugUnitTest --no-daemon --console=plain
```

Expected: tests fail until persistence and backup models include the new data.

- [ ] **Step 3: Order annotation writes**

Use one monotonic counter per score and skip stale writers inside the existing mutex:

```kotlin
internal class AnnotationSaveVersions {
    private val values = ConcurrentHashMap<String, AtomicLong>()

    fun next(scoreId: String): Long =
        values.computeIfAbsent(scoreId) { AtomicLong() }.incrementAndGet()

    fun isLatest(scoreId: String, version: Long): Boolean =
        values[scoreId]?.get() == version
}

private val annotationSaveVersions = AnnotationSaveVersions()
private val annotationSaveJobs = ConcurrentHashMap<String, Job>()

fun saveAnnotations(pageAnnotations: List<PageAnnotation>) {
    val reader = state.value.reader ?: return
    val scoreId = reader.score.id
    val annotations = reader.annotations.withPage(reader.pageIndex, pageAnnotations)
    val request = annotationSaveVersions.next(scoreId)
    mutableState.update { it.copy(reader = reader.copy(annotations = annotations)) }
    annotationSaveJobs[scoreId] = viewModelScope.launch {
        annotationSaveMutex.withLock {
            if (!annotationSaveVersions.isLatest(scoreId, request)) return@withLock
            repository.saveAnnotations(scoreId, annotations)
        }
    }
}
```

Join a snapshot of `annotationSaveJobs.values` before creating a backup. Export uses the in-memory `reader.annotations`, so it does not wait for disk persistence.

- [ ] **Step 4: Update backup and remove global appearance**

Ensure backup settings encode `AnnotationEditorSettings`. Version 1 backups receive defaults. Version 3 annotation JSON remains inside the existing annotations directory without a backup schema fork.

Remove transitional `AppSettings.penWidth` and `AppSettings.highlighterStrength`. Keep reading their old preference keys only when `annotation_editor_json` is absent.

- [ ] **Step 5: Add the rapid-transition regression flow**

In `SheetSetFlowTest`, complete one stroke, open and dismiss text, tap Done, open Tools, and assert that no crash or missing annotation occurs. The test must verify the callback received the completed stroke before the next transition.

- [ ] **Step 6: Run focused tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest --no-daemon --console=plain
```

Expected: unit tests pass and Android tests compile.

- [ ] **Step 7: Review the diff and conditionally commit**

If explicit commit approval exists:

```powershell
git add app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt app/src/main/java/cz/teply/sheetset/settings/AppSettings.kt app/src/main/java/cz/teply/sheetset/settings/SettingsStore.kt app/src/test/java/cz/teply/sheetset/AnnotationSaveVersionsTest.kt app/src/androidTest/java/cz/teply/sheetset/data/LibraryBackupTest.kt app/src/androidTest/java/cz/teply/sheetset/SheetSetFlowTest.kt
git commit -m "fix: serialize annotation persistence"
```

### Task 9: SeliaScan launch and Android PDF share intake

**Files:**

- Create: `app/src/main/java/cz/teply/sheetset/IncomingPdfIntent.kt`
- Modify: `app/src/main/AndroidManifest.xml:1-38`
- Modify: `app/src/main/java/cz/teply/sheetset/MainActivity.kt:1-86`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt:100-365`
- Modify: all five `strings.xml` resource files
- Create: `app/src/androidTest/java/cz/teply/sheetset/IncomingPdfIntentTest.kt`
- Modify: `app/src/androidTest/java/cz/teply/sheetset/MainActivitySmokeTest.kt`
- Modify: `app/src/androidTest/java/cz/teply/sheetset/SheetSetFlowTest.kt`

**Interfaces:**

- Produces: `IncomingPdfIntent.uris(intent: Intent): List<Uri>`.
- Produces: `MainActivity.handleIncomingPdfIntent(intent: Intent)` for cold start and `onNewIntent`.
- Produces: installed-app launch for package `com.majkeylab.scanit`, with Play and web fallbacks.
- Consumes: existing `SheetSetViewModel.importPdfs(uris: List<Uri>)` and repository PDF validation.

- [ ] **Step 1: Write failing intent parser tests**

Create instrumentation tests with synthetic `content://` URIs:

```kotlin
@Test
fun singleAndMultiplePdfSharesReturnUniqueContentUris() {
    val first = Uri.parse("content://scanner/first.pdf")
    val second = Uri.parse("content://scanner/second.pdf")
    val multiple = Intent(Intent.ACTION_SEND_MULTIPLE)
        .setType("application/pdf")
        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second, first))

    assertEquals(listOf(first, second), IncomingPdfIntent.uris(multiple))
}

@Test
fun wrongMimeFileUrisAndEmptyIntentsAreRejected() {
    val wrongMime = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://scanner/image.png"))
    val fileUri = Intent(Intent.ACTION_VIEW, Uri.parse("file:///sdcard/score.pdf"))
        .setType("application/pdf")

    assertTrue(IncomingPdfIntent.uris(wrongMime).isEmpty())
    assertTrue(IncomingPdfIntent.uris(fileUri).isEmpty())
    assertTrue(IncomingPdfIntent.uris(Intent()).isEmpty())
}
```

- [ ] **Step 2: Run instrumentation compilation and verify failure**

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --console=plain
```

Expected: compilation fails because `IncomingPdfIntent` does not exist.

- [ ] **Step 3: Implement the bounded parser**

Use `IntentCompat.getParcelableExtra` and `IntentCompat.getParcelableArrayListExtra`. Accept only actions `ACTION_SEND`, `ACTION_SEND_MULTIPLE`, or `ACTION_VIEW`, exact MIME type `application/pdf`, and URI scheme `content`. Include `ClipData` items, preserve encounter order, and remove duplicates.

```kotlin
internal object IncomingPdfIntent {
    fun uris(intent: Intent): List<Uri> {
        if (intent.type != "application/pdf") return emptyList()
        if (intent.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW)) {
            return emptyList()
        }
        return buildList {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(::add)
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.let(::addAll)
            intent.data?.let(::add)
            intent.clipData?.let { clip ->
                repeat(clip.itemCount) { index -> clip.getItemAt(index).uri?.let(::add) }
            }
        }.filter { it.scheme == ContentResolver.SCHEME_CONTENT }.distinct()
    }
}
```

- [ ] **Step 4: Add Android package visibility and PDF intent filters**

Add this package query directly under `<manifest>`:

```xml
<queries>
    <package android:name="com.majkeylab.scanit" />
</queries>
```

Set `MainActivity` to `android:launchMode="singleTop"`. Add separate exported intent filters for `ACTION_SEND`, `ACTION_SEND_MULTIPLE`, and `ACTION_VIEW`. Each filter uses `CATEGORY_DEFAULT` and `<data android:mimeType="application/pdf" />`. Do not add broad file extensions, wildcard MIME types, storage permissions, or `CATEGORY_BROWSABLE`.

- [ ] **Step 5: Consume incoming PDFs once**

Call `handleIncomingPdfIntent(intent)` after ViewModel creation in `onCreate` and from `onNewIntent`:

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingPdfIntent(intent)
}

private fun handleIncomingPdfIntent(source: Intent) {
    val uris = IncomingPdfIntent.uris(source)
    if (uris.isEmpty()) return
    viewModel.importPdfs(uris)
    setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
}
```

Do not copy URI contents in the Activity. Keep the existing repository import boundary and error state.

- [ ] **Step 6: Launch installed SeliaScan before Play fallback**

Replace `openScanIt` with `openSeliaScan`:

```kotlin
private const val SELIA_SCAN_PACKAGE = "com.majkeylab.scanit"

private fun openSeliaScan(context: Context) {
    context.packageManager.getLaunchIntentForPackage(SELIA_SCAN_PACKAGE)?.let {
        context.startActivity(it)
        return
    }
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SELIA_SCAN_PACKAGE"))
    val web = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$SELIA_SCAN_PACKAGE"),
    )
    runCatching { context.startActivity(market) }.getOrElse { context.startActivity(web) }
}
```

Rename the visible ScanIt strings to SeliaScan in every locale. The hint explains that an installed app opens directly and Google Play opens only when absent.

- [ ] **Step 7: Add focused activity and Compose tests**

Extend `MainActivitySmokeTest` to launch an `ACTION_SEND` PDF intent and assert that the Activity remains active without crashing. Extend `SheetSetFlowTest` to assert that the Import PDF sheet shows Files and Scan with SeliaScan exactly once.

- [ ] **Step 8: Run focused checks**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
```

Expected: unit tests pass, both APKs compile, and no manifest merge error occurs. Task 11 runs installed/missing/share paths on dedicated emulators.

- [ ] **Step 9: Review the diff and conditionally commit**

If explicit commit approval exists:

```powershell
git add app/src/main/AndroidManifest.xml app/src/main/java/cz/teply/sheetset/IncomingPdfIntent.kt app/src/main/java/cz/teply/sheetset/MainActivity.kt app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt app/src/main/res/values/strings.xml app/src/main/res/values-cs/strings.xml app/src/main/res/values-sk/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-pl/strings.xml app/src/androidTest/java/cz/teply/sheetset/IncomingPdfIntentTest.kt app/src/androidTest/java/cz/teply/sheetset/MainActivitySmokeTest.kt app/src/androidTest/java/cz/teply/sheetset/SheetSetFlowTest.kt
git commit -m "feat: connect SeliaScan PDF sharing"
```

### Task 10: Copy the current SeliaScan launcher branding

- Copy the first-party SeliaScan launcher vector byte-for-byte into all SeliaLists normal, round, and API 33 launcher resources.
- Copy the first-party 512 px RGBA Play icon byte-for-byte into the SeliaLists Play asset.
- Remove only the now-unreferenced SeliaLists foreground, monochrome, and icon-background resources.
- Verify source and target hashes, Android 10/API 33 resource resolution, build, and lint.
- Keep the SeliaScan repository and installed app read-only.

### Task 11: Full verification and live acceptance

**Files:**

- Modify only if verification finds a scoped defect: files already listed in Tasks 1 through 8.
- Update: `CHANGELOG.md` after all checks pass.
- Preserve: `.reference/artifacts/` release artifacts from earlier versions.

**Interfaces:**

- Consumes every prior task.
- Produces fresh build, lint, unit-test, instrumentation, visual-QA, and artifact evidence.

- [ ] **Step 1: Run static and build gates**

```powershell
git diff --check
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug bundleRelease --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`, no lint errors, and no unit-test failures.

- [ ] **Step 2: Build the instrumentation APK**

```powershell
.\gradlew.bat assembleDebugAndroidTest --no-daemon --console=plain
```

Expected files:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

- [ ] **Step 3: Verify on the authorized Android 10 Huawei**

The user explicitly authorized HUAWEI YAL-L21, serial `BQLDU19927002646`, Android 10/API 29. Verify the target before installation:

```powershell
adb -s BQLDU19927002646 shell getprop ro.product.manufacturer
adb -s BQLDU19927002646 shell getprop ro.product.model
adb -s BQLDU19927002646 shell getprop ro.build.version.sdk
adb -s BQLDU19927002646 shell pm list packages | rg '^package:com\.majkeylab\.sheetset$'
adb -s BQLDU19927002646 install -r -t 'app\build\outputs\apk\debug\app-debug.apk'
```

Expected identity: `HUAWEI`, `YAL-L21`, API `29`. The existing app is already installed. Use only replacement install. If Android reports an incompatible signature, do not uninstall, clear data, or remove another package. Record the blocker and use a disposable API 29 phone AVD for the new build instead.

If replacement succeeds, install the test APK and run only non-destructive UI/editor classes:

```powershell
adb -s BQLDU19927002646 install -r -t 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
adb -s BQLDU19927002646 shell am instrument -w -r -e class 'cz.teply.sheetset.MainActivitySmokeTest,cz.teply.sheetset.SheetSetFlowTest,cz.teply.sheetset.SettingsFlowTest,cz.teply.sheetset.pdf.PdfPageViewInputTest' com.majkeylab.sheetset.test/androidx.test.runner.AndroidJUnitRunner
```

Do not run repository, backup, restore, or migration classes on the physical phone. Do not uninstall either APK after QA unless the user asks.

- [ ] **Step 4: Run the tablet AVD**

Create the disposable tablet. Do not alter the Huawei while tablet tests run:

```powershell
if (adb devices | Select-String '^emulator-5604\s') { throw 'emulator-5604 is already in use' }
'no' | & 'C:\Users\mates\AppData\Local\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat' create avd --name SeliaLists_Tablet_API_35 --package 'system-images;android-35;google_apis;x86_64' --device pixel_tablet --force
$seliaTablet = Start-Process -FilePath 'C:\Users\mates\AppData\Local\Android\Sdk\emulator\emulator.exe' -ArgumentList @('-avd','SeliaLists_Tablet_API_35','-port','5604','-no-window','-no-audio','-no-boot-anim','-no-snapshot-load','-no-snapshot-save') -WindowStyle Hidden -PassThru
adb -s emulator-5604 wait-for-device
adb -s emulator-5604 shell getprop sys.boot_completed
adb -s emulator-5604 install -r -t 'app\build\outputs\apk\debug\app-debug.apk'
adb -s emulator-5604 install -r -t 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
adb -s emulator-5604 shell cmd locale set-app-locales com.majkeylab.sheetset --locales en-US
adb -s emulator-5604 shell am instrument -w -r com.majkeylab.sheetset.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: final instrumentation output contains `OK (` and zero failures.

Do not install until `getprop sys.boot_completed` returns `1`. Before deleting the tablet AVD in Step 8, run `adb -s emulator-5604 emu avd name` and resolve both `C:\Users\mates\.android\avd\SeliaLists_Tablet_API_35.avd` and `C:\Users\mates\.android\avd\SeliaLists_Tablet_API_35.ini`.

- [ ] **Step 5: Perform live editor scenarios on the tablet**

Use the synthetic PDF from `.reference/tmp/scorepdf-audit/emulator/test-pdfs/sheetset-qa-score.pdf`. Drive controls from UI-tree bounds, not screenshot coordinates. Verify:

1. Pen 1 draw, Undo, and Redo.
2. Pen 2 retains a different color and width.
3. Pinch and pan while Pen is selected adds no stroke.
4. Text creation, selection, edit, move, and delete.
5. Rectangle creation, resize, duplicate, recolor, and lasso delete.
6. Eyedropper picks one of the synthetic page swatches.
7. Done returns to View without changing the page position.
8. The rapid text-close, Done, and Tools sequence produces no ANR in logcat.
9. Settings show Library, Reading, Data, and App sections with current-value summaries.
10. Import PDF shows Files and Scan with SeliaScan.
11. With a verified local SeliaScan APK installed, Scan with SeliaScan opens package `com.majkeylab.scanit`.
12. Without SeliaScan installed, the same action opens its Google Play listing.
13. `IncomingPdfIntentTest` imports unique single and multiple PDF streams and rejects wrong MIME or `file://` input.

Capture one screenshot for Draw, Objects, selected object, color panel, and sectioned settings under `.reference/qa/selialists-editor/`.

For the installed SeliaScan path, locate an existing first-party APK with:

```powershell
rg --files 'C:\Users\mates\Documents\Codex' | rg '(?i)(scanit|seliascan).*\.apk$'
```

Record its SHA-256, install it only on the dedicated QA emulator, and verify the focused package after tapping the scanner action. Do not build or edit SeliaScan and do not download an APK from a third-party mirror. If no first-party APK exists, use the official Google Play listing on a Play-enabled disposable AVD; if authentication blocks installation, report that exact live-path blocker while retaining parser, resolver, and missing-app evidence.

- [ ] **Step 6: Review crash and ANR evidence**

```powershell
adb -s emulator-5604 logcat -b crash -d
adb -s emulator-5604 logcat -d | rg 'ANR in com\.majkeylab\.sheetset|FATAL EXCEPTION'
```

Expected: no matching crash or ANR.

- [ ] **Step 7: Update the changelog and perform hostile self-review**

Add concise entries for the contextual editor, object editing, colors, gestures, and grouped settings. Review the complete diff for:

- stale version 2 assumptions;
- global highlighter opacity;
- duplicate toolbar state;
- missing localized strings;
- accidental physical-device commands;
- copied ScorePDF assets or branding;
- unbounded preset or selection state;
- main-thread PDF or file I/O.

Fix every scoped defect and rerun the affected gate.

- [ ] **Step 8: Preserve the AAB and clean test resources**

After the release bundle passes signature verification, copy it to:

```text
.reference/artifacts/SeliaLists-contextual-editor-com.majkeylab.sheetset.aab
```

Then stop and delete only `SeliaLists_Tablet_API_35` and run `gradlew clean`. Keep the preserved AAB and QA screenshots.

- [ ] **Step 9: Conditionally commit the final verified change**

Only after explicit commit approval:

```powershell
git add CHANGELOG.md app/src/main/AndroidManifest.xml app/src/main/java/cz/teply/sheetset/IncomingPdfIntent.kt app/src/main/java/cz/teply/sheetset/MainActivity.kt app/src/main/java/cz/teply/sheetset/pdf/Annotations.kt app/src/main/java/cz/teply/sheetset/pdf/AnnotationEditorSettings.kt app/src/main/java/cz/teply/sheetset/pdf/AnnotationGeometry.kt app/src/main/java/cz/teply/sheetset/pdf/AnnotationRenderer.kt app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt app/src/main/java/cz/teply/sheetset/pdf/PdfViewport.kt app/src/main/java/cz/teply/sheetset/pdf/PdfExporter.kt app/src/main/java/cz/teply/sheetset/settings/AppSettings.kt app/src/main/java/cz/teply/sheetset/settings/SettingsStore.kt app/src/main/java/cz/teply/sheetset/ui/AnnotationToolbar.kt app/src/main/java/cz/teply/sheetset/ui/ReaderScreen.kt app/src/main/java/cz/teply/sheetset/ui/SettingsDrawer.kt app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt app/src/main/res/font/noto_music_regular.ttf app/src/main/res/values/strings.xml app/src/main/res/values-cs/strings.xml app/src/main/res/values-sk/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-pl/strings.xml app/src/test/java/cz/teply/sheetset/AnnotationSaveVersionsTest.kt app/src/test/java/cz/teply/sheetset/pdf/AnnotationEditorSettingsTest.kt app/src/test/java/cz/teply/sheetset/pdf/AnnotationGeometryTest.kt app/src/test/java/cz/teply/sheetset/pdf/AnnotationHistoryTest.kt app/src/test/java/cz/teply/sheetset/pdf/AnnotationJsonMigrationTest.kt app/src/test/java/cz/teply/sheetset/pdf/PdfViewportTest.kt app/src/androidTest/java/cz/teply/sheetset/IncomingPdfIntentTest.kt app/src/androidTest/java/cz/teply/sheetset/MainActivitySmokeTest.kt app/src/androidTest/java/cz/teply/sheetset/SettingsFlowTest.kt app/src/androidTest/java/cz/teply/sheetset/SheetSetFlowTest.kt app/src/androidTest/java/cz/teply/sheetset/data/LibraryBackupTest.kt app/src/androidTest/java/cz/teply/sheetset/pdf/PdfExporterTest.kt app/src/androidTest/java/cz/teply/sheetset/pdf/PdfPageViewInputTest.kt docs/superpowers/specs/2026-08-25-selialists-contextual-annotation-editor-design.md docs/superpowers/plans/2026-08-25-selialists-contextual-annotation-editor.md third_party/NotoMusic-OFL.txt
git commit -m "feat: rebuild the annotation editor"
```

Do not push, open a PR, create a GitHub release, or upload to Google Play without separate explicit approval.
