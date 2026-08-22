# SheetSet typed PDF editor implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the stroke-only layer with a typed, non-destructive editor for ink, markup, text, shapes, selection, movement, resizing, undo, redo, persistence, and export.

**Architecture:** Keep `PdfPageView`, normalized coordinates, private JSON, and rasterized copy export. Migrate JSON to version 2, share one renderer between the screen and exporter, and use platform text selection on API 35 or S Extension 13. Unsupported devices and scanned PDFs use manual markup geometry.

**Tech Stack:** Kotlin 2.3.21, Android `PdfRenderer`, SDK Extensions, Android Canvas, `org.json`, Compose, JUnit 4, and Android instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-08-20-sheetset-editor-settings-adaptive-design.md`

## Global constraints

- Complete `2026-08-20-sheetset-settings-adaptive.md` first.
- The imported PDF is immutable.
- Coordinates stay normalized from 0 through 1.
- Cap one page at 10,000 objects, one ink object at 4,096 points, one text box at 4,000 characters, and active history at 100 snapshots.
- Use black, dark gray, and alpha only.
- Run text selection off the main thread and fall back to manual geometry.
- Do not add OCR, form filling, page management, or AndroidX PDF viewer.

---

### Task 1: Version 2 typed annotations and migration

**Files:**
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/Annotations.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/LibraryUiState.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/PdfExporter.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/ReaderScreen.kt`
- Test: `app/src/test/java/cz/teply/sheetset/pdf/AnnotationHistoryTest.kt`
- Test: `app/src/test/java/cz/teply/sheetset/pdf/AnnotationJsonMigrationTest.kt`

**Interfaces:**
- Produces: `PageAnnotation`, `InkAnnotation`, `MarkupAnnotation`, `TextBoxAnnotation`, `ShapeAnnotation`, `NormalizedRect`, and version 2 `AnnotationJson`.
- Replaces: `Stroke`, `AnnotationTool`, and stroke-list history.

- [ ] **Step 1: Write the failing legacy migration test**

```kotlin
@Test
fun versionOneStrokesMigrateWithoutLoss() {
    val legacy = """
        {"version":1,"pages":{"0":[
          {"tool":"PEN","width":0.004,"points":[[0.1,0.2],[0.3,0.4]]},
          {"tool":"HIGHLIGHTER","width":0.02,"points":[[0.5,0.6]]}
        ]}}
    """.trimIndent()

    val decoded = AnnotationJson.decode(legacy)
    val page = decoded.pages.getValue(0)

    assertEquals(InkKind.PEN, (page[0] as InkAnnotation).kind)
    assertEquals(InkKind.HIGHLIGHTER, (page[1] as InkAnnotation).kind)
    assertEquals(decoded, AnnotationJson.decode(AnnotationJson.encode(decoded)))
}
```

Add round-trip assertions for markup, text box, and every shape kind. Use fixed IDs.

- [ ] **Step 2: Run the RED test**

```powershell
.\gradlew.bat testDebugUnitTest --tests cz.teply.sheetset.pdf.AnnotationJsonMigrationTest --no-daemon --console=plain
```

Expected: typed annotation classes do not exist.

- [ ] **Step 3: Add the typed model**

```kotlin
const val MAX_ANNOTATIONS_PER_PAGE = 10_000
const val MAX_POINTS_PER_INK = 4_096
const val MAX_TEXT_LENGTH = 4_000
private const val MAX_HISTORY_STEPS = 100

enum class InkKind { PEN, HIGHLIGHTER }
enum class MarkupKind { HIGHLIGHT, UNDERLINE, STRIKE_THROUGH }
enum class ShapeKind { LINE, ARROW, RECTANGLE, ELLIPSE }

data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

sealed interface PageAnnotation { val id: String }

data class InkAnnotation(
    override val id: String,
    val kind: InkKind,
    val width: Float,
    val points: List<NormalizedPoint>,
) : PageAnnotation

data class MarkupAnnotation(
    override val id: String,
    val kind: MarkupKind,
    val bounds: List<NormalizedRect>,
) : PageAnnotation

data class TextBoxAnnotation(
    override val id: String,
    val bounds: NormalizedRect,
    val text: String,
    val size: AnnotationTextSize,
) : PageAnnotation

data class ShapeAnnotation(
    override val id: String,
    val kind: ShapeKind,
    val start: NormalizedPoint,
    val end: NormalizedPoint,
    val width: Float,
) : PageAnnotation
```

Validate normalized rectangles, unique IDs, non-empty markup bounds, non-blank bounded text, bounded widths, and point counts.

- [ ] **Step 4: Encode version 2 and decode both versions**

Version 2 uses exact `type` values `ink`, `markup`, `text`, and `shape`. Keep a private version 1 decoder and convert legacy strokes with the Kotlin ID expression `"legacy-$page-$index"`. `encode` always emits version 2.

- [ ] **Step 5: Convert the current vertical flow**

```kotlin
data class DocumentAnnotations(
    val pages: Map<Int, List<PageAnnotation>> = emptyMap(),
)

data class AnnotationHistory(
    val annotations: List<PageAnnotation> = emptyList(),
    private val undoStates: List<List<PageAnnotation>> = emptyList(),
    private val redoStates: List<List<PageAnnotation>> = emptyList(),
)
```

Rename `saveStrokes` to `saveAnnotations`. Update the view and exporter to draw typed ink with their current appearance. Do not keep type aliases or compatibility wrappers after all callers compile.

- [ ] **Step 6: Run migration and compile gates**

```powershell
.\gradlew.bat testDebugUnitTest --tests 'cz.teply.sheetset.pdf.*' --no-daemon --console=plain
.\gradlew.bat lintDebug assembleDebug assembleDebugAndroidTest --no-daemon --console=plain
```

- [ ] **Step 7: Commit**

```powershell
git add -- app/src/main/java/cz/teply/sheetset/pdf/Annotations.kt app/src/main/java/cz/teply/sheetset/LibraryUiState.kt app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt app/src/main/java/cz/teply/sheetset/pdf/PdfExporter.kt app/src/main/java/cz/teply/sheetset/ui/ReaderScreen.kt app/src/test/java/cz/teply/sheetset/pdf/AnnotationHistoryTest.kt app/src/test/java/cz/teply/sheetset/pdf/AnnotationJsonMigrationTest.kt
git commit -m "feat(pdf): migrate typed annotations"
```

### Task 2: Annotation geometry and bounded history

**Files:**
- Create: `app/src/main/java/cz/teply/sheetset/pdf/AnnotationGeometry.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/Annotations.kt`
- Modify: `app/src/test/java/cz/teply/sheetset/pdf/AnnotationGeometryTest.kt`
- Modify: `app/src/test/java/cz/teply/sheetset/pdf/AnnotationHistoryTest.kt`

**Interfaces:**
- Produces: `PageAnnotation.hitTest`, `translated`, `resized`, `manualMarkup`, `AnnotationHistory.update`, `delete`, and `erase`.
- Consumes: typed models from Task 1.

- [ ] **Step 1: Write failing geometry tests**

```kotlin
@Test
fun rectangleTranslationClampsTheWholeObject() {
    val rectangle = ShapeAnnotation(
        id = "shape-1",
        kind = ShapeKind.RECTANGLE,
        start = NormalizedPoint(0.8f, 0.8f),
        end = NormalizedPoint(1f, 1f),
        width = 0.004f,
    )

    assertEquals(rectangle, rectangle.translated(0.4f, 0.4f))
}

@Test
fun updateDeleteUndoAndRedoUseIds() {
    val added = AnnotationHistory().add(text)
    val changed = added.update(text.copy(text = "Changed"))
    val deleted = changed.delete(text.id)

    assertEquals("Changed", (deleted.undo().annotations.single() as TextBoxAnnotation).text)
    assertTrue(deleted.redo().annotations.isEmpty())
}
```

Also assert that reverse iteration returns the topmost hit and that a resize below 0.01 normalized size clamps to 0.01.

- [ ] **Step 2: Run RED tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests cz.teply.sheetset.pdf.AnnotationGeometryTest --tests cz.teply.sheetset.pdf.AnnotationHistoryTest --no-daemon --console=plain
```

- [ ] **Step 3: Implement one exhaustive geometry file**

Use exhaustive `when` expressions. Translation preserves object size and clamps the complete object inside the page. Resize supports text, markup, rectangle, ellipse, line, and arrow. Ink exposes move but no resize.

```kotlin
fun manualMarkup(start: NormalizedPoint, end: NormalizedPoint): List<NormalizedRect> = listOf(
    NormalizedRect(
        left = minOf(start.x, end.x),
        top = minOf(start.y, end.y),
        right = maxOf(start.x, end.x),
        bottom = maxOf(start.y, end.y),
    ),
)
```

- [ ] **Step 4: Route every history mutation through `replace`**

`add`, `update`, `delete`, and `erase` call one private `replace(next)` method. Reject duplicate IDs and cap both undo and redo lists with `.takeLast(MAX_HISTORY_STEPS)`.

- [ ] **Step 5: Run and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests 'cz.teply.sheetset.pdf.*' --no-daemon --console=plain
git add -- app/src/main/java/cz/teply/sheetset/pdf/AnnotationGeometry.kt app/src/main/java/cz/teply/sheetset/pdf/Annotations.kt app/src/test/java/cz/teply/sheetset/pdf/AnnotationGeometryTest.kt app/src/test/java/cz/teply/sheetset/pdf/AnnotationHistoryTest.kt
git commit -m "feat(pdf): add editor geometry"
```

### Task 3: Shared screen and export renderer

**Files:**
- Create: `app/src/main/java/cz/teply/sheetset/pdf/AnnotationRenderer.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/PdfExporter.kt`
- Modify: `app/src/androidTest/java/cz/teply/sheetset/pdf/PdfExporterTest.kt`

**Interfaces:**
- Produces: `AnnotationRenderer.draw(Canvas, PageAnnotation, RectF, Boolean)`.
- Consumes: every typed annotation and normalized geometry.

- [ ] **Step 1: Extend the failing export test**

Create one object of every type on page zero, export two pages, and assert:

```kotlin
assertEquals(beforeHash, source.sha256())
assertEquals(2, pageCount(destination))
assertTrue(darkPixels(destination) > darkPixels(source) + 500)
```

Assert one dark pixel region for ink, markup, text, and shapes so a single unrelated mark cannot pass the test.

- [ ] **Step 2: Run the RED exporter test**

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=cz.teply.sheetset.pdf.PdfExporterTest' --no-daemon --console=plain
```

- [ ] **Step 3: Implement one drawing dispatcher**

```kotlin
fun draw(canvas: Canvas, annotation: PageAnnotation, page: RectF, selected: Boolean = false) {
    when (annotation) {
        is InkAnnotation -> drawInk(canvas, annotation, page)
        is MarkupAnnotation -> drawMarkup(canvas, annotation, page)
        is TextBoxAnnotation -> drawTextBox(canvas, annotation, page)
        is ShapeAnnotation -> drawShape(canvas, annotation, page)
    }
    if (selected) drawSelection(canvas, annotation, page)
}
```

Use black pen, alpha gray highlighter, filled alpha gray highlight, black underline or strike-through, bordered white text boxes with `StaticLayout`, and black line, arrow, rectangle, or ellipse strokes. Selection draws dashed gray bounds and handles only on screen.

- [ ] **Step 4: Delete duplicate drawing code**

`PdfPageView` passes `selected = annotation.id == selectedAnnotationId`. `PdfExporter` always passes false. Remove the old independent stroke drawing implementations from both files.

- [ ] **Step 5: Run and commit**

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=cz.teply.sheetset.pdf.PdfExporterTest' --no-daemon --console=plain
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon --console=plain
git add -- app/src/main/java/cz/teply/sheetset/pdf/AnnotationRenderer.kt app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt app/src/main/java/cz/teply/sheetset/pdf/PdfExporter.kt app/src/androidTest/java/cz/teply/sheetset/pdf/PdfExporterTest.kt
git commit -m "feat(pdf): render typed annotations"
```

### Task 4: Editor gestures and adaptive tool palettes

**Files:**
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/ReaderScreen.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/LibraryUiState.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-cs/strings.xml`
- Modify: `app/src/main/res/values-sk/strings.xml`
- Modify: `app/src/main/res/values-de/strings.xml`
- Modify: `app/src/main/res/values-pl/strings.xml`
- Modify: `app/src/androidTest/java/cz/teply/sheetset/SheetSetFlowTest.kt`

**Interfaces:**
- Produces: expanded `ReaderTool`, compact bottom palette, expanded right palette, text dialog, and typed edit callbacks.
- Consumes: `WindowLayout`, `AppSettings`, `AnnotationHistory`, and `AnnotationRenderer`.

- [ ] **Step 1: Write failing tool and accessibility tests**

```kotlin
listOf(
    "Select", "Pen", "Highlighter", "Underline", "Strike-through",
    "Text box", "Line", "Arrow", "Rectangle", "Ellipse", "Eraser",
    "Undo", "Redo", "Done",
).forEach { label ->
    composeRule.onNodeWithContentDescription(label).assertExists()
}

composeRule.onNodeWithContentDescription("Rectangle").performScrollTo().performClick().assertIsSelected()
composeRule.onNodeWithContentDescription("Pen").assertIsNotSelected()
```

Add an expanded-layout assertion that the palette is right of the PDF content.

- [ ] **Step 2: Run the reader class and confirm RED**

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=cz.teply.sheetset.SheetSetFlowTest' --no-daemon --console=plain
```

- [ ] **Step 3: Expand tools and callbacks**

```kotlin
enum class ReaderTool {
    VIEW, SELECT, PEN, HIGHLIGHTER, UNDERLINE, STRIKE_THROUGH,
    TEXT_BOX, LINE, ARROW, RECTANGLE, ELLIPSE, ERASER,
}
```

`PdfPageView` exposes:

```kotlin
var annotations: List<PageAnnotation> = emptyList()
var selectedAnnotationId: String? = null
var onAddAnnotation: (PageAnnotation) -> Unit = {}
var onUpdateAnnotation: (PageAnnotation) -> Unit = {}
var onDeleteAnnotation: (String) -> Unit = {}
var onRequestText: (NormalizedRect) -> Unit = {}
var onRequestMarkup: (MarkupKind, NormalizedPoint, NormalizedPoint) -> Unit = { _, _, _ -> }
```

Pen collects bounded ink points. Highlighter, underline, and strike-through request markup bounds. Shapes use drag start and end. Text requests one dialog after a non-empty drag. Eraser removes the topmost hit. Select chooses and drags the topmost hit. `InkKind.HIGHLIGHTER` remains only for migrated version 1 freehand highlights.

- [ ] **Step 4: Add bounded text input**

Use one multiline `AlertDialog`. Keep at most 4,000 characters, disable Save for blank trimmed text, and create `TextBoxAnnotation` with the configured text size.

- [ ] **Step 5: Render one shared tool list in two placements**

Compact and medium layouts use a horizontally scrollable 60 dp bottom row. Expanded layout uses a vertically scrollable 64 dp right rail. Reuse one tool definition list and one callback dispatcher.

Apply page fit, taps, swipes, auto-hide, default tool, pen width, highlighter strength, and text size from `AppSettings`. Set and restore `LocalView.current.keepScreenOn` through `DisposableEffect`.

- [ ] **Step 6: Add localized tool keys**

| Key | English | Czech | Slovak | German | Polish |
|---|---|---|---|---|---|
| `select` | Select | Vybrat | Vybrať | Auswählen | Wybierz |
| `underline` | Underline | Podtržení | Podčiarknutie | Unterstreichen | Podkreślenie |
| `strike_through` | Strike-through | Přeškrtnutí | Prečiarknutie | Durchstreichen | Przekreślenie |
| `text_box` | Text box | Textové pole | Textové pole | Textfeld | Pole tekstowe |
| `line` | Line | Čára | Čiara | Linie | Linia |
| `arrow` | Arrow | Šipka | Šípka | Pfeil | Strzałka |
| `rectangle` | Rectangle | Obdélník | Obdĺžnik | Rechteck | Prostokąt |
| `ellipse` | Ellipse | Elipsa | Elipsa | Ellipse | Elipsa |
| `move` | Move | Přesunout | Presunúť | Verschieben | Przenieś |
| `resize` | Resize | Změnit velikost | Zmeniť veľkosť | Größe ändern | Zmień rozmiar |
| `delete_annotation` | Delete annotation | Smazat anotaci | Odstrániť anotáciu | Annotation löschen | Usuń adnotację |
| `annotation_save_failed` | Annotation could not be saved. | Anotaci se nepodařilo uložit. | Anotáciu sa nepodarilo uložiť. | Annotation konnte nicht gespeichert werden. | Nie udało się zapisać adnotacji. |
| `retry` | Retry | Opakovat | Znova | Wiederholen | Ponów |
| `selected_annotation` | Selected: %1$s | Vybráno: %1$s | Vybrané: %1$s | Ausgewählt: %1$s | Wybrano: %1$s |

- [ ] **Step 7: Run and commit**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest --no-daemon --console=plain
git add -- app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt app/src/main/java/cz/teply/sheetset/ui/ReaderScreen.kt app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt app/src/main/java/cz/teply/sheetset/LibraryUiState.kt app/src/main/res/values/strings.xml app/src/main/res/values-cs/strings.xml app/src/main/res/values-sk/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-pl/strings.xml app/src/androidTest/java/cz/teply/sheetset/SheetSetFlowTest.kt
git commit -m "feat(pdf): add editor tools"
```

### Task 5: Text-aware markup with manual fallback

**Files:**
- Create: `app/src/main/java/cz/teply/sheetset/pdf/PdfTextSelector.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt`
- Test: `app/src/test/java/cz/teply/sheetset/pdf/PdfTextSelectorTest.kt`
- Test: `app/src/androidTest/java/cz/teply/sheetset/pdf/PdfTextSelectorInstrumentedTest.kt`

**Interfaces:**
- Produces: `PdfTextSelector.select(File, Int, NormalizedPoint, NormalizedPoint): List<NormalizedRect>?` and `textSelectionSupported(Int, Int): Boolean`.
- Consumes: platform `selectContent`, `selectedTextContents`, and manual markup geometry.

- [ ] **Step 1: Write failing support tests**

```kotlin
@Test
fun supportRequiresApi35OrSExtension13() {
    assertFalse(textSelectionSupported(sdkInt = 33, sExtension = 12))
    assertTrue(textSelectionSupported(sdkInt = 33, sExtension = 13))
    assertTrue(textSelectionSupported(sdkInt = 35, sExtension = 0))
}
```

The instrumentation fixture contains real text. Drag across its title and assert non-empty normalized bounds. Use an image-only page and assert null.

- [ ] **Step 2: Run RED tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests cz.teply.sheetset.pdf.PdfTextSelectorTest --no-daemon --console=plain
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=cz.teply.sheetset.pdf.PdfTextSelectorInstrumentedTest' --no-daemon --console=plain
```

- [ ] **Step 3: Implement exact platform gating**

```kotlin
internal fun textSelectionSupported(sdkInt: Int, sExtension: Int): Boolean =
    sdkInt >= 35 || sExtension >= 13
```

Read `SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S)` and return null before touching extension APIs when unsupported. Run file, renderer, page, and selection work on `Dispatchers.IO`.

```kotlin
val selection = page.selectContent(
    SelectionBoundary(Point(startX, startY)),
    SelectionBoundary(Point(stopX, stopY)),
)
val bounds = selection?.selectedTextContents.orEmpty().flatMap { it.bounds }
```

Normalize each `RectF` by page width and height and reject empty or invalid rectangles.

- [ ] **Step 4: Wire fallback through the ViewModel**

```kotlin
fun addMarkup(kind: MarkupKind, start: NormalizedPoint, end: NormalizedPoint)
```

The function asks `PdfTextSelector` and uses `manualMarkup(start, end)` when selection is unsupported or empty. It adds one `MarkupAnnotation` through the same history and mutex save path.

- [ ] **Step 5: Run and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests 'cz.teply.sheetset.pdf.*' --no-daemon --console=plain
.\gradlew.bat connectedDebugAndroidTest --no-daemon --console=plain
git add -- app/src/main/java/cz/teply/sheetset/pdf/PdfTextSelector.kt app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt app/src/test/java/cz/teply/sheetset/pdf/PdfTextSelectorTest.kt app/src/androidTest/java/cz/teply/sheetset/pdf/PdfTextSelectorInstrumentedTest.kt
git commit -m "feat(pdf): add text-aware markup"
```

### Task 6: Selection resize and retryable persistence

**Files:**
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/ReaderScreen.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/LibraryUiState.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-cs/strings.xml`
- Modify: `app/src/main/res/values-sk/strings.xml`
- Modify: `app/src/main/res/values-de/strings.xml`
- Modify: `app/src/main/res/values-pl/strings.xml`
- Test: `app/src/androidTest/java/cz/teply/sheetset/EditorFlowTest.kt`

**Interfaces:**
- Produces: selection handles, move, resize, delete, localized save failures, and retryable state.
- Consumes: geometry and history APIs from Task 2.

- [ ] **Step 1: Write the failing editor flow**

The test creates a real one-page PDF fixture, adds a rectangle, switches to Select, selects it, drags it, resizes its lower-right handle, deletes it, and verifies undo restores it. Assert callback state, not screenshot pixels.

```kotlin
composeRule.onNodeWithContentDescription("Rectangle").performClick()
composeRule.onNodeWithContentDescription("PDF page 1 of 1").performTouchInput {
    down(Offset(200f, 300f)); moveTo(Offset(500f, 700f)); up()
}
composeRule.onNodeWithContentDescription("Select").performClick()
composeRule.runOnIdle { assertEquals(1, annotations.size) }
```

Finish the test with move, resize, delete, and undo assertions against the injected annotation list.

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=cz.teply.sheetset.EditorFlowTest' --no-daemon --console=plain
```

- [ ] **Step 3: Implement handles and semantics**

Text, markup, rectangle, and ellipse use four corner handles. Lines and arrows use endpoint handles. Ink supports move and delete only. Draw handles at 24 dp with a 48 dp hit area. Expose the selected annotation type through localized state description.

- [ ] **Step 4: Preserve failed edits for retry**

Replace the Boolean UI error with:

```kotlin
enum class UiError { ACTION_FAILED, ANNOTATION_SAVE_FAILED }
```

Update `ReaderUiState` before persistence. On repository failure, retain the edited annotations, set `LibraryUiState.error = UiError.ANNOTATION_SAVE_FAILED`, and retry the complete current document on the next edit or a snackbar Retry action. Map both enum values to localized resources in Compose.

- [ ] **Step 5: Run and commit**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest --no-daemon --console=plain
git add -- app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt app/src/main/java/cz/teply/sheetset/ui/ReaderScreen.kt app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt app/src/main/java/cz/teply/sheetset/LibraryUiState.kt app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt app/src/main/res/values/strings.xml app/src/main/res/values-cs/strings.xml app/src/main/res/values-sk/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-pl/strings.xml app/src/androidTest/java/cz/teply/sheetset/EditorFlowTest.kt
git commit -m "feat(pdf): finish object editing"
```

### Task 7: PDF editor acceptance gate

**Files:**
- Modify only editor files with verified findings.

**Interfaces:**
- Produces: a migration-safe typed editor ready for release work.
- Consumes: Tasks 1 through 6.

- [ ] **Step 1: Run the complete automated gate**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest --no-daemon --console=plain
```

- [ ] **Step 2: Run six live scenarios**

1. Open a version 1 fixture and confirm old strokes remain visible.
2. Draw, erase, undo, redo, restart, and recheck pen and highlighter.
3. Add underline, strike-through, text, line, arrow, rectangle, and ellipse.
4. Select, move, resize, delete, undo, and redo each applicable type.
5. Verify text-aware markup on a text PDF and manual fallback on a scanned PDF.
6. Export, reopen every page, compare the original SHA-256, and inspect the crash buffer.

Run scenarios 2 through 4 on phone and tablet AVDs. Keep artifacts under `.reference/tmp`.

- [ ] **Step 3: Review performance and bounds**

Confirm one rendered bitmap per page, executor shutdown on detach, bounded objects and history, no main-thread selection, one shared renderer, and stable state after rotation.

- [ ] **Step 4: Commit only verified review fixes**

If tracked editor files changed, stage the complete bounded editor set and commit:

```powershell
git add -- app/src/main/java/cz/teply/sheetset/pdf/Annotations.kt app/src/main/java/cz/teply/sheetset/pdf/AnnotationGeometry.kt app/src/main/java/cz/teply/sheetset/pdf/AnnotationRenderer.kt app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt app/src/main/java/cz/teply/sheetset/pdf/PdfExporter.kt app/src/main/java/cz/teply/sheetset/pdf/PdfTextSelector.kt app/src/main/java/cz/teply/sheetset/ui/ReaderScreen.kt app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt
git commit -m "fix(pdf): address editor QA"
```

Skip the commit when the tree is unchanged.
