# SheetSet design

## Objective

Build a native Android app for musicians who keep scores as PDF files. The app has two primary destinations: the PDF library and setlists. Users can import PDFs, create unlimited ordered setlists, read scores, add handwritten annotations, and export a new annotated PDF copy.

SheetSet is independent software. It does not copy the ScorePDF name, assets, source code, or screen layouts.

## Product scope

### PDF library

- Import one or more local PDF files with the Android system picker or Android share sheet.
- Copy each accepted PDF into app-private storage so later access does not depend on a provider permission.
- Show the title and page count in a compact row list.
- Open, rename, or delete a score.
- Search scores by title.
- Warn before deletion and remove the score from every setlist.

### Setlists

- Create, rename, and delete unlimited setlists.
- Add library scores to a setlist.
- Reorder scores with explicit accessible move controls.
- Open the setlist as one continuous reading flow.
- Keep one score record and one annotation layer. Setlists store score IDs, not PDF copies.

### Reader and annotations

- Render one PDF page at a time with Android `PdfRenderer`.
- Turn pages by tapping the left or right side or by horizontal swipe.
- Pinch to zoom and double-tap to reset the page fit.
- Enter an explicit annotation mode.
- Provide a black pen, a gray highlighter, an eraser, undo, redo, and Done.
- Store strokes as normalized page coordinates in a separate JSON file. Never modify the imported original.
- Export a rasterized PDF copy with annotations through the Android create-document picker.
- Continue to the next score when a setlist reaches the last page.

### Languages and accessibility

- Ship English, Czech, Slovak, German, and Polish resources.
- Use Android system and per-app language selection through `android:localeConfig`.
- Keep all touch targets at least 48 dp.
- Add content descriptions and semantic roles for TalkBack.
- Support font scaling and portrait or landscape layouts.

## Non-goals

- Accounts, cloud sync, analytics, ads, subscriptions, or network access.
- Metronome, tuner, labels, folders, bookmarks, OCR, page reordering, musical stamps, collaboration, and Bluetooth pedal configuration.
- Editing PDF text or vector objects. "Edit" means handwritten annotation and export of an annotated copy.
- iOS, desktop, or web app builds in the first release.

## Visual system

- White paper surface, near-black text, and neutral gray separators.
- System typography. Page title 24 sp, row title 16 sp, metadata 13 sp.
- No gradients, colored accent, decorative cards, or heavy shadows.
- Maximum 8 dp corner radius. Use 1 dp borders where separation is needed.
- Use only 120 to 180 ms transitions for toolbar visibility and list movement.
- Use a bottom navigation bar with exactly `PDF` and `Setlists`.

## Architecture

The app is a single Android application module written in Kotlin and Jetpack Compose. A small `LibraryRepository` owns app-private PDFs, an atomic JSON catalog, and annotation JSON files. `SheetSetViewModel` exposes immutable UI state and performs file work on `Dispatchers.IO`. A custom Android `View` renders pages and maps gestures and normalized annotation strokes without a third-party PDF dependency.

The catalog contains typed score and setlist records. A setlist contains ordered score IDs. Each annotation file contains bounded per-page stroke lists. The repository serializes writes with one coroutine mutex and writes catalog changes through `AtomicFile`.

## Data model

```kotlin
data class Score(
    val id: String,
    val title: String,
    val fileName: String,
    val pageCount: Int,
    val importedAtEpochMs: Long,
)

data class Setlist(
    val id: String,
    val name: String,
    val scoreIds: List<String>,
)

data class Stroke(
    val tool: AnnotationTool,
    val width: Float,
    val points: List<NormalizedPoint>,
)
```

## Security and failure handling

The system picker is the only import boundary. The importer accepts `application/pdf`, caps each source at 250 MiB, checks the `%PDF-` signature, copies into a temporary private file, and opens the copy with `PdfRenderer` before it becomes library data. A failed import removes the temporary file and shows a short user-facing error.

The app never requests Internet, broad storage, camera, microphone, contacts, or location permissions. Export writes only to the URI returned by the create-document picker. App-private PDFs remain private to SheetSet.

The annotation store caps one stroke at 4,096 points and one page at 10,000 strokes. The exporter renders one page at a time and recycles its bitmap before opening the next page.

## Tech stack

- JDK 17
- Gradle 8.13
- Android Gradle Plugin 8.13.2
- Kotlin 2.3.21
- compileSdk and targetSdk 36
- minSdk 26
- Jetpack Compose BOM 2026.06.01
- AndroidX Activity and Lifecycle
- Android `PdfRenderer`, `PdfDocument`, `AtomicFile`, and Storage Access Framework
- JUnit 4 and Compose UI instrumentation tests

## Commands

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon
.\gradlew.bat connectedDebugAndroidTest --no-daemon
adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
```

## Project structure

```text
app/src/main/java/cz/teply/sheetset/
  MainActivity.kt             App entry and navigation host
  SheetSetViewModel.kt        UI state and actions
  data/LibraryRepository.kt   Import, catalog, annotations, and deletion
  data/Models.kt              Typed persistent records
  pdf/PdfPageView.kt          Rendering, zoom, page gestures, and drawing
  pdf/PdfExporter.kt          Annotated PDF copy export
  ui/LibraryScreen.kt         PDF library
  ui/SetlistScreens.kt        Setlist list and editor
  ui/ReaderScreen.kt          Reader chrome and annotation tools
  ui/Theme.kt                 Monochrome theme
```

## Code style

Use immutable data at UI boundaries. Validate names and files where they enter the system. Keep file I/O out of composables and off the main thread.

```kotlin
suspend fun createSetlist(name: String): Setlist {
    val cleanName = name.trim().take(MAX_TITLE_LENGTH)
    require(cleanName.isNotEmpty()) { "Setlist name must not be blank" }
    return updateCatalog { catalog -> catalog.addSetlist(cleanName) }
}
```

## Testing strategy

- Unit tests cover catalog invariants, title validation, deletion cleanup, setlist ordering, annotation history, normalized coordinate mapping, and JSON round trips.
- Instrumentation tests cover the two-tab shell, empty states, setlist creation, score import through an injected fixture, reader launch, annotation toolbar, locale resources, and delete confirmation.
- Emulator QA covers a valid multipage PDF, a malformed `.pdf`, an empty setlist, a score used by two setlists, drawing plus undo and redo, annotated export, app restart, and the original PDF remaining unchanged.
- CI runs unit tests, lint, and `assembleDebug` on pushes and pull requests.

## Boundaries

- Always: keep the app offline, preserve imported originals, validate PDF input, use string resources, and run local and emulator gates before release.
- Ask first: add a runtime dependency, add a permission, change the package ID, add cloud features, or publish to Google Play.
- Never: copy ScorePDF branding or assets, collect user data, hardcode secrets, silently overwrite a PDF, or claim release verification without checking the published APK.

## Success criteria

- A user imports valid PDFs and sees them after app restart.
- A malformed or oversized file is rejected without a partial library record.
- A user creates more than three setlists and reorders their scores.
- A setlist opens scores continuously in its stored order.
- Pen, highlighter, eraser, undo, and redo work on normalized annotations.
- Annotations survive restart and the imported original file hash does not change.
- Export creates a readable PDF containing the visible annotations.
- English, Czech, Slovak, German, and Polish resources build without missing strings.
- Unit tests, lint, debug build, and connected emulator tests pass.
- GitHub hosts the source, CI, a static Pages site, and a tagged alpha release with a verified APK.
