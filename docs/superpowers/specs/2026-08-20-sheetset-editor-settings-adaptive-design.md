# SheetSet editor, settings, and adaptive UI design

## Status

Approved product direction. This document extends `2026-08-20-sheetset-design.md` and replaces conflicting requirements for navigation, Android support, annotations, and the next release.

## Objective

Make SheetSet a focused Android 13+ PDF score organizer and editor for phones and tablets. Keep the monochrome paper interface, offline storage, unlimited setlists, and non-destructive export.

The main screen must show a hamburger button at the upper left. The PDF destination shows `Import PDF` at the upper right. The Setlists destination shows `Create` at the upper right. The app name does not occupy the header.

## Confirmed constraints

- Set `minSdk` to 33. Keep `compileSdk` and `targetSdk` at 36.
- Keep package ID `cz.teply.sheetset`.
- Keep the app offline with no Internet permission, account, ads, analytics, or subscription.
- Preserve every imported original. Editing changes a separate annotation document. Export creates a new copy.
- Keep the interface white, black, and neutral gray. Do not add a color palette.
- Ship English, Czech, Slovak, German, and Polish. A fresh install starts in English.
- Use existing Compose and Android platform APIs before adding a dependency.
- Publish the completed work as `v0.3.0-alpha.1` with `versionCode = 3`.

## App shell

### Compact windows

Compact windows use one content pane and the existing bottom destinations: `PDF` and `Setlists`.

The top bar contains:

- A 48 dp hamburger button at the left.
- No `SheetSet` title.
- `Import PDF` at the right in the PDF destination.
- `Create` at the right in the Setlists destination.

The action stays in the same upper-right position for empty and populated states. Empty states explain the first action but do not duplicate the button.

### Medium and expanded windows

The root layout derives one width class from the available Compose width:

- Compact: less than 600 dp.
- Medium: 600 dp through 839 dp.
- Expanded: 840 dp or wider.

Medium and expanded windows replace the bottom tabs with a left navigation rail. Medium windows show one bounded content pane. Expanded windows show the Setlists list and selected setlist detail side by side. Window resizing, rotation, split screen, and folding preserve the selected destination and selected item.

The reader uses the full remaining page area. Annotation tools stay at the bottom on compact windows and become a vertical right-side palette on expanded windows.

## Menu and settings

The hamburger opens a modal drawer on compact windows and a dismissible settings panel on larger windows. The menu contains four sections only.

### Language

Choices:

- English
- Čeština
- Slovenčina
- Deutsch
- Polski
- Device language

Use one `language_initialized` preference to distinguish a fresh install from a user who selected `Device language`. On the first launch only, set the application locale to English and mark initialization complete. Later changes use Android `LocaleManager.applicationLocales`, which keeps the in-app choice synchronized with Android 13 per-app language settings. `Device language` clears the locale override without clearing the initialization marker.

Every new string must exist in all five resource sets. Czech, Slovak, German, and Polish text must keep native diacritics.

### Reader

Settings:

- Keep screen awake while a PDF is open. Default: on.
- Page fit: page or width. Default: page.
- Page-turn taps. Default: on.
- Page-turn swipes. Default: on.
- Auto-hide controls. Default: on.

### Annotation defaults

Settings:

- Default tool: pen, highlighter, or view.
- Pen width: thin, medium, or thick.
- Highlighter opacity: light, medium, or strong.
- Text size: small, medium, or large.

### About

Show the app version, Android requirement, offline and privacy statement, license, GitHub repository, and release page. External links open through an Android browser intent. SheetSet still declares no Internet permission.

## PDF editor

### Interaction modes

The reader has two explicit modes:

- View mode handles page navigation, zoom, and control visibility.
- Edit mode handles selection and annotation tools.

Edit mode provides:

- Select
- Pen
- Highlighter
- Underline
- Strike-through
- Text box
- Line
- Arrow
- Rectangle
- Ellipse
- Eraser
- Undo
- Redo
- Done

The tool palette shows recognizable monochrome icons with localized content descriptions. Every control has a minimum 48 dp target. The selected tool exposes selected-state semantics.

### Annotation model

Replace the stroke-only annotation payload with a versioned typed model:

```kotlin
sealed interface PageAnnotation {
    val id: String
}

data class InkAnnotation(
    override val id: String,
    val tool: InkTool,
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
    val size: TextSize,
) : PageAnnotation

data class ShapeAnnotation(
    override val id: String,
    val kind: ShapeKind,
    val start: NormalizedPoint,
    val end: NormalizedPoint,
    val width: Float,
) : PageAnnotation
```

`MarkupKind` contains highlight, underline, and strike-through. `ShapeKind` contains line, arrow, rectangle, and ellipse. Coordinates remain normalized so annotations survive phone, tablet, rotation, and zoom changes.

Version 2 annotation JSON stores typed objects only. Loading version 1 converts every existing pen or highlighter stroke into an `InkAnnotation`. Migration must not rewrite the original PDF or lose an existing annotation. Undo and redo history stays in memory for the active page and is not serialized.

Limits remain explicit:

- At most 10,000 annotation objects per page.
- At most 4,096 points in one freehand object.
- At most 4,000 characters in one text box.
- At most 100 undo states for the active page.

### Selection and editing

The Select tool chooses the topmost annotation under a tap. A selected object can be moved, resized when its type allows it, or deleted. Drag handles remain inside the page bounds.

Text-aware markup uses platform PDF text selection when the device exposes the required PDF SDK extension and the document has a text layer. The selected glyph bounds become normalized markup rectangles.

If text selection is unavailable, empty, or the PDF is scanned, the same tools use manual drag geometry. The user still gets underline, strike-through, and highlight, but the markup does not snap to glyphs. SheetSet does not claim OCR support.

Text insertion is a movable `TextBoxAnnotation`. It does not rewrite a PDF text object in the imported original.

### Save and export

Annotation edits save to the app-private annotation JSON after each committed action. Rapid changes are serialized through the existing annotation mutex.

Export keeps the current page-by-page bounded-memory design. The exporter draws every annotation type into a new rasterized PDF copy. It never overwrites the imported source. Export failure leaves both the source and stored annotations intact and shows a localized error.

## Settings persistence

Use Android `SharedPreferences` for the small fixed settings set. Do not add DataStore or a settings framework. Parse every stored value through a bounded enum or numeric range and fall back to the documented default on invalid data.

Language persistence stays owned by Android `LocaleManager`, not `SharedPreferences`.

## State and architecture

Keep one Android application module.

- `SheetSetViewModel` owns destination, selected setlist, reader state, editor state, and settings actions.
- `LibraryRepository` continues to own PDFs, catalog data, and annotation files.
- `SettingsStore` owns only the fixed non-language settings.
- `PdfPageView` renders the page, maps gestures, and draws editor overlays.
- `AnnotationHistory` owns bounded undo and redo for typed annotations.
- `PdfExporter` draws typed annotations into the exported copy.
- Compose owns the app shell, menu, adaptive pane decisions, dialogs, and tool palettes.

Do not introduce a navigation framework, dependency-injection framework, PDF viewer replacement, or generic settings framework.

## Error handling

- Unsupported text selection falls back to manual markup without an error dialog.
- Invalid or oversized annotation JSON is rejected and reported without deleting the original PDF.
- Unknown future annotation types fail closed and do not produce a partial rewrite.
- Invalid stored settings use defaults.
- Blank text boxes are discarded.
- A text box that exceeds 4,000 characters cannot be saved.
- Export and persistence failures use localized user-facing messages and keep the current editor state available for retry.

## Accessibility

- All controls have localized descriptions and at least 48 dp touch targets.
- Hamburger, import, create, tool selection, drawer state, and selected annotations expose correct semantics.
- Keyboard and D-pad focus order follows header, navigation, content, and editor tools.
- Tool selection never relies only on background color.
- Text scales without hiding the contextual action.
- Tablet panes keep readable widths and do not stretch row text across the full display.

## Testing

### Unit tests

- Version 1 to version 2 annotation migration.
- Round trips for every annotation type.
- Bounds, move, resize, hit testing, and page clamping.
- Bounded undo and redo.
- Settings defaults and invalid-value fallback.
- Export geometry for ink, markup, text, and shapes.

### Instrumentation tests

- Hamburger opens and closes the menu.
- PDF always exposes `Import PDF` at the upper right.
- Setlists always exposes `Create` at the upper right.
- English is selected on a fresh install.
- Each supported language changes visible strings.
- Every editor tool opens and reports selected state.
- Old annotations remain visible after migration.
- Compact and expanded layouts expose the correct navigation and panes.

### Runtime QA

Test on an Android 13 phone AVD, an Android 16 phone AVD, and an Android 16 tablet AVD.

Scenarios:

- Import, search, rename, and delete a valid PDF.
- Reject malformed and oversized input.
- Create more than three setlists and edit ordering.
- Draw, highlight, underline, strike through, insert text, add shapes, move, resize, erase, undo, and redo.
- Use a text PDF and a scanned PDF to verify text-aware and manual markup paths.
- Rotate and resize while a PDF and setlist detail are open.
- Restart and verify settings and annotations.
- Export, reopen every page, and confirm the imported source SHA-256 is unchanged.

## GitHub and release

The repository update includes:

- README feature and limitation updates.
- Phone and tablet screenshots.
- Existing Android CI, release, and license badges, plus an Android 13+ badge.
- Changelog entry for `0.3.0-alpha.1`.
- Version code 3 and version name `0.3.0-alpha.1`.
- Green local unit, lint, build, and connected-device gates.
- Green GitHub `quality` and release workflows.
- Tag `v0.3.0-alpha.1`, prerelease APK, and `.sha256` asset.
- Downloaded release verification for hash, manifest version, APK signature, install, launch, UI tree, and crash buffer.

The protected `main` branch requires one approving review from another GitHub user with write access. SheetSet must not disable or weaken that rule. If approval is unavailable, the PR remains open and the prerelease tag can point to the verified feature commit, matching the `v0.2.0-alpha.1` publication process.

## Non-goals

- OCR for scanned PDFs.
- Rewriting text or vector objects in imported originals.
- Page insertion, deletion, extraction, rotation, or reordering.
- Redaction, digital signatures, encryption, password removal, or form filling.
- Colored tools or theme variants.
- Cloud storage, sync, collaboration, or account features.
- Replacing the custom reader with the experimental AndroidX PDF viewer.

## Acceptance criteria

- Android 13 is the minimum supported version.
- The header contains only the hamburger and the correct contextual action.
- All five languages are complete and selectable from the menu.
- Phone and tablet layouts meet the specified width behavior.
- Every listed annotation tool works, persists, migrates, and exports.
- Text markup falls back cleanly when semantic text selection is unavailable.
- Existing PDFs, setlists, and version 1 annotations survive the upgrade.
- The original PDF hash never changes.
- Tests, lint, builds, AVD scenarios, GitHub CI, and published APK verification pass.
