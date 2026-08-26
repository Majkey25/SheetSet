# SeliaLists contextual annotation editor design

## Status

Approved product direction. This document is ready for user review before implementation planning.

This design supersedes the annotation editor and settings sections in `2026-08-20-sheetset-editor-settings-adaptive-design.md`. Current product facts remain authoritative: the product name is SeliaLists, the application ID is `com.majkeylab.sheetset`, and Android 10 is the minimum supported version.

## Objective

Make PDF annotation predictable during rehearsal and performance. A musician must be able to choose a tool, understand its active style, draw or place an object, edit the result, and return to reading without losing the page position.

The editor adopts the interaction model verified in ScorePDF 16.0.2. SeliaLists keeps its own monochrome visual language, icons, strings, and implementation. It does not copy ScorePDF branding, image assets, or exact screen composition.

## Evidence

The design uses these references:

- The local ScorePDF 16.0.2 audit in `.reference/tmp/scorepdf-audit/FEATURE_INVENTORY.md`.
- [ScorePDF v15 update notes](https://enoiu.com/en/app-update/scorepdf-15-0/), including drawing and object modes, selection, lasso, tool settings, text, symbols, and shapes.
- [ScorePDF current feature summary](https://enoiu.com/en/app/scorepdf/), including annotation tools, colors, width, straight lines, eyedropper, and export.

The local audit also found an input-dispatch ANR after rapid editor transitions and a lost final local-layer stroke after process death. SeliaLists must commit each completed edit before another editor transition starts.

## Product rules

- Keep the PDF page dominant. Controls must not cover more page area than necessary.
- Keep app chrome black, white, and neutral gray. Annotation swatches may use color.
- Use recognizable vector icons with localized accessibility labels.
- Keep every touch target at least 48 dp.
- Preserve imported PDFs. Store annotations separately and export to a new PDF.
- Keep all editor functions offline and unrestricted.
- Use Android and existing project dependencies before adding a library.
- Keep phone and tablet behavior consistent. Change density, not the mental model.

## Scope

This change includes:

- A contextual annotation toolbar.
- Drawing presets with independent styles.
- Object placement, selection, editing, lasso selection, duplication, and deletion.
- Text and a bounded starter set of musical-symbol stamps.
- Arbitrary annotation colors, quick colors, and an eyedropper.
- Reliable pinch zoom and pan while editing.
- Optional stylus palm rejection.
- Version 3 annotation migration.
- A sectioned settings hierarchy.
- Editor and settings acceptance tests.
- Installed-app handoff to SeliaScan and Android PDF share/open intake.
- Exact reuse of the current first-party SeliaScan launcher and Play Store icon in SeliaLists.

This change does not include page reordering, duplicate-page common and local layers, jump links, metronome, timed page turning, cloud sync, OCR, or collaboration. Those features require separate reader or storage designs and do not solve the current editor usability problem.

## Editor state model

The reader keeps two top-level modes:

- **View**: page turning, zoom, pan, and control visibility.
- **Edit**: annotation tools and object manipulation.

Edit mode contains two tool groups:

- **Draw**: Pen 1, Pen 2, Marker, Highlighter, and Eraser.
- **Objects**: Select, Lasso, Text, Musical symbol, Line, Arrow, Rectangle, and Ellipse.

The selected group and tool remain active until the user chooses another tool or taps **Done**. Opening Edit mode restores the last editor tool from the current session. A new installation starts with Select so opening the editor does not immediately draw on the page.

Compose owns an `AnnotationEditorState` with these transient values:

- the active group and tool;
- the active drawing preset;
- the selected annotation IDs;
- the current color and width controls;
- whether the palette or symbol chooser is open;
- the active lasso bounds;
- the undo and redo history for the current page.

Transient selection and open-panel state are not serialized.

## Contextual toolbar

The editor uses a two-row bottom toolbar on phones and tablets. The toolbar replaces the current unrelated settings and tools rows.

### Property row

The property row changes with the active tool or selection.

For a drawing tool, the row contains:

- previous page;
- width decrease, numeric width, and width increase;
- straight-line mode where supported;
- the current color swatch;
- eyedropper;
- next page.

For a selected object, the row contains:

- previous page;
- object size or stroke-width controls;
- the current color swatch;
- edit text when the object is text;
- duplicate;
- delete;
- next page.

Unavailable controls stay hidden instead of disabled. The remaining controls close the gap so the row does not look broken.

### Tool row

The tool row contains:

- a drag grip that makes horizontal scrolling discoverable;
- a Draw or Objects group switch;
- the tools in the selected group;
- undo;
- redo;
- Done.

The group switch uses different icons and text semantics. Color alone never indicates the group or selected tool.

The central tool area scrolls horizontally. Previous page, next page, undo, redo, and Done remain pinned when width permits. A compact phone may move undo and redo into the scroll area only when necessary to retain 48 dp targets.

### Phone and tablet adaptation

Phones show one current-color circle. Tapping the circle opens the color panel.

Tablets show the current circle followed by up to eight quick swatches when space permits. The palette button remains available for custom colors.

Both layouts keep the toolbar at the bottom. Tablets use more horizontal space instead of switching to a separate side toolbar.

## Drawing presets

SeliaLists provides four independent presets:

- Pen 1
- Pen 2
- Marker
- Highlighter

Each preset stores:

- a stable preset ID;
- a localized label;
- an ARGB color;
- a normalized width;
- opacity from 0 through 255;
- visibility and toolbar order.

Pen and Marker use the same freehand engine but use distinct icons and defaults. Highlighter uses a square cap and translucent rendering. Changing one preset never changes annotations that were already committed.

The width control displays a direct integer from 1 through 40. The integer maps to a normalized page-relative width. Each tap changes the value by one. Long press is not required.

Straight-line mode converts the current freehand gesture into a line between the first and last sampled points.

## Color panel and eyedropper

The color panel is a bottom sheet with:

- the eight verified quick colors: black, red, orange, yellow, green, blue, purple, and pink;
- up to four recent custom colors;
- hue, saturation, and value controls for a custom color;
- an opacity control when the active tool supports opacity;
- a clear selected state and a live preview.

The current-color circle is the only persistent color control on a compact phone.

The eyedropper samples the rendered PDF bitmap under the next tap. It does not sample editor chrome. The sampled pixel becomes the active preset or selected-object color. Canceling the eyedropper restores the previous tool without changing the color.

## Object placement and editing

Object tools create these annotation types:

- Text
- Musical symbol
- Line
- Arrow
- Rectangle
- Ellipse

Text insertion starts with one tap or a short drag. SeliaLists opens the text dialog after placement. The dialog edits text, size, line height, alignment, color, and opacity. Selecting existing text and tapping **Edit** reopens the same dialog.

Musical symbols use stable symbol IDs. The renderer maps each ID to a bundled, OFL-licensed music font. The starter set includes sharp, flat, natural, fermata, accent, breath mark, crescendo, decrescendo, and common dynamic marks. The JSON never stores an implementation-specific glyph as the identity.

Lines and arrows use endpoint handles. Rectangles, ellipses, text, and symbols use corner handles. Symbols also expose a rotation handle. Every transform remains inside normalized page bounds.

Tapping an object in Select mode selects the topmost hit. Dragging the body moves it. Dragging a handle resizes or rotates it. Tapping empty page space clears the selection.

The lasso selects every annotation whose bounds intersect the lasso. A multiple selection can move, duplicate, or delete the selected objects. Multi-object resize and rotation are out of scope.

Duplicating generates new IDs and offsets the copies by 1 percent of page width and height when the offset remains in bounds.

## Gestures and input

Edit mode uses these input rules:

- A stylus draws with one pointer.
- A finger draws when palm rejection is off and no stylus is active.
- Two fingers always pan and pinch zoom, regardless of the active tool.
- Select mode uses one pointer to choose, move, or resize an object.
- A canceled gesture commits no annotation.
- A completed gesture commits exactly one history step.

Pinch zoom uses the detector focus point as its anchor. Two-finger movement updates both zoom and pan. Opening Edit mode must preserve the current zoom and pan. Closing Edit mode must not reset the page position.

When palm rejection is on, finger contacts do not draw while a stylus is active. Finger contacts still support two-finger zoom and pan. The setting defaults to off because OEM stylus reporting differs.

## Annotation data version 3

Version 3 stores the appearance of every annotation on the annotation itself. Later preset changes do not change old annotations.

All annotation types store `color: AnnotationColor` and `opacity`. `AnnotationColor.argb` must use an opaque `0xFF` alpha byte; the separate opacity field controls transparency. Ink and stroked shapes also store normalized width. JSON encodes each color as an eight-digit uppercase `#FFRRGGBB` string so signed `Int` formatting cannot change the stored value.

Version 3 adds:

```kotlin
data class SymbolAnnotation(
    override val id: String,
    val symbolId: String,
    val center: NormalizedPoint,
    val size: Float,
    val rotationDegrees: Float,
    val color: AnnotationColor,
    val opacity: Int,
) : PageAnnotation
```

`TextBoxAnnotation` adds line height, alignment, color, and opacity. Shapes remain axis-aligned in this scope. `SymbolAnnotation` supports rotation. Existing IDs and normalized coordinates remain unchanged.

The decoder supports versions 1, 2, and 3:

- Version 1 strokes migrate through the existing typed-stroke migration.
- Version 2 color enum names map to their current ARGB values.
- Version 2 highlighters and highlights receive the current legacy default opacity.
- Missing new fields receive bounded defaults.
- Unknown future versions fail without replacing stored annotations.

The encoder writes version 3 only.

## Commit and history rules

`AnnotationHistory` remains bounded to 100 undo steps for the active page.

The editor commits after:

- a completed stroke;
- an eraser gesture;
- object placement;
- move, resize, or rotation;
- text edit;
- color or style update;
- duplicate;
- delete;
- a lasso batch action.

The UI updates history synchronously. `SheetSetViewModel` serializes the resulting file write on its existing I/O path. A tool switch, page turn, dialog close, reader close, or process lifecycle transition must not discard the latest committed state.

## Settings hierarchy

The drawer uses section labels and flat rows. It does not use cards.

### Library

- PDF
- Setlists

### Reading

- Reader and page layout
- Gestures
- Annotation tools

### Data

- Backup and restore

### App

- Language
- App details

Each navigation row contains an icon, a headline, and a one-line summary or current value. The complete row is clickable.

### Reader and page layout

This page groups settings under:

- Layout: single page, half page, two pages, page fit.
- Page turning: taps and swipes.
- Display: keep screen awake and auto-hide controls.

A choice row shows the current value and opens a compact selection dialog. It does not expand every radio choice inline.

### Gestures

The first implementation keeps existing tap and swipe behavior but explains each switch. The page also contains zoom and input settings, including palm rejection. Arbitrary per-edge action mapping is not part of this editor redesign.

### Annotation tools

This page groups:

- Default editor tool.
- Drawing presets.
- Visible tools and order.
- Quick colors and order.
- Text defaults.
- Stylus and palm rejection.

Preset and toolbar-order screens use drag handles. Hidden tools remain available in settings and can be restored.

### Backup and restore

This page groups the existing actions:

- Create backup.
- Share backup.
- Restore backup.

Each row explains whether the action creates, shares, merges, or replaces data. The existing restore confirmation and ScorePDF merge behavior remain unchanged.

### Language and app details

Language remains a separate page with English, Czech, Slovak, German, Polish, and Device language.

App details retains the version, Android requirement, privacy statement, links, and Buy Me a Coffee button.

## Settings persistence

`SettingsStore` continues to use `SharedPreferences`.

Primitive settings keep their existing keys. If no preset JSON exists, `SettingsStore` converts the existing `pen_width` and `highlighter_strength` values into the default presets. Drawing presets, visible tool order, and quick colors use bounded JSON strings with explicit size limits. Invalid JSON, unknown tools, duplicate IDs, invalid widths, or invalid colors fall back to documented defaults.

The native backup must include the expanded settings. Restoring an older backup supplies defaults for fields that do not exist.

## SeliaScan and Android PDF intake

SeliaLists integrates with the installed scanner without changing SeliaScan.

The scanner package is `com.majkeylab.scanit`. The manifest declares this package in `<queries>` so Android 11 and newer can resolve it.

The **Import PDF** source sheet keeps two actions:

- **Files** opens Android's multiple-document picker for `application/pdf`.
- **Scan with SeliaScan** opens the installed app's launch activity. If the package is absent, SeliaLists opens its Google Play listing with a web fallback.

SeliaLists declares exported PDF intake filters for `ACTION_SEND`, `ACTION_SEND_MULTIPLE`, and `ACTION_VIEW` with `application/pdf` and the default category. `MainActivity` uses `singleTop` so a PDF shared while SeliaLists is open arrives through `onNewIntent`.

One intent parser extracts `Intent.EXTRA_STREAM`, multiple stream extras, and PDF items from `ClipData`. It accepts only unique `content://` URIs from an intent whose declared type is `application/pdf`. The existing repository validates the actual file before adding it to the library.

`MainActivity` sends accepted URIs to `SheetSetViewModel.importPdfs`. It consumes the external intent once so activity recreation does not import the same PDF again. Unsupported, empty, or malformed intents leave the library unchanged and show the existing localized failure state when import validation fails.

SeliaLists does not call a private SeliaScan activity, read SeliaScan storage, add a shared SDK, or modify the SeliaScan repository. SeliaScan returns a scan through the Android Sharesheet like any other PDF-producing app.

## Accessibility

- Every toolbar control has a localized content description.
- Selected tools expose selected-state semantics.
- Width and opacity controls expose state descriptions.
- Color swatches expose localized names or an ARGB description for custom colors.
- The lasso and selection actions do not rely on color alone.
- Focus order follows the visual order.
- TalkBack can reach every pinned and horizontally scrolled action.
- Reduced-motion users receive no decorative toolbar movement.

## File boundaries

Expected production changes are limited to these areas:

- `pdf/Annotations.kt`: version 3 annotation data and migration.
- `pdf/AnnotationGeometry.kt`: selection, lasso, transforms, and bounds.
- `pdf/AnnotationRenderer.kt`: stored opacity, symbols, rotation, and multi-selection.
- `pdf/PdfPageView.kt`: pointer state machine, eyedropper, zoom, pan, and object editing callbacks.
- `ui/ReaderScreen.kt`: editor orchestration and dialogs.
- `ui/AnnotationToolbar.kt`: contextual toolbar and color panel.
- `ui/SettingsDrawer.kt`: sectioned navigation and settings pages.
- `MainActivity.kt` and `AndroidManifest.xml`: installed SeliaScan launch plus Android PDF share/open intake.
- `IncomingPdfIntent.kt`: bounded extraction and one-time consumption of shared PDF URIs.
- `settings/AppSettings.kt` and `settings/SettingsStore.kt`: presets and input settings.
- localized string resources and focused unit or instrumentation tests.

`AnnotationToolbar.kt` is justified because `ReaderScreen.kt` already mixes reader layout, editor state, dialogs, and more than 300 lines of toolbar code. No navigation framework, dependency-injection layer, generic settings framework, or editor service hierarchy is added.

## Error handling

- Reject invalid annotation values at decode boundaries.
- Keep the last valid stored annotations when decoding fails.
- Keep the imported PDF and stored annotations when export fails.
- Ignore an eyedropper tap outside the rendered page.
- Keep the previous style when a custom-color edit is canceled.
- Keep the previous toolbar configuration when its stored JSON is invalid.
- Show the existing localized failure message when a persistence action fails.

## Verification

Unit tests must cover:

- version 1 and version 2 migration to version 3;
- version 3 round trips for every annotation type;
- invalid colors, opacity, widths, symbol IDs, and preset JSON;
- lasso intersection and batch actions;
- move, resize, symbol rotation, duplication, and page-bound clamping;
- bounded undo and redo;
- settings defaults and persistence.

Instrumentation tests must cover:

1. Draw with each preset, then undo and redo.
2. Change one preset without changing a committed annotation.
3. Place, select, move, resize, recolor, duplicate, and delete each object class. Rotate a musical symbol.
4. Create and edit text.
5. Select multiple objects with the lasso and delete them.
6. Pick a quick color, a custom color, and a PDF color with the eyedropper.
7. Pinch zoom and pan while Pen is active without adding an accidental stroke.
8. Preserve zoom and pan when entering and leaving Edit mode.
9. Complete a stroke, rapidly close the text dialog, close Edit mode, and open Tools without ANR or data loss.
10. Navigate every settings section and verify the displayed current values.
11. Restore an older native backup and retain safe editor defaults.
12. Open installed SeliaScan, use the Play fallback when absent, and import one or multiple shared PDF `content://` URIs exactly once.

Run the full existing Android test suite after the focused tests. Perform phone QA on the explicitly authorized Huawei YAL-L21 running Android 10 when replacement installation is signature-safe. Run the full data test suite and expanded-layout QA on a disposable tablet AVD. Do not uninstall the Huawei app or clear its data on a signature conflict.

## Acceptance criteria

- A first-time user can draw, change color, undo, and finish without opening a secondary dialog other than the color panel.
- The active tool, color, width, and mode are visible at all times in Edit mode.
- Selecting an existing object changes the property row to object actions.
- Text can be edited after insertion.
- Two-finger pan and zoom work while any editor tool is active.
- No completed edit is lost during a page turn, mode switch, dialog close, reader close, or tested process restart.
- Settings show the Library, Reading, Data, and App groups without mixing backup actions with reader controls.
- The UI remains usable at 320 dp width and on an expanded tablet.
- Existing version 1 and version 2 annotations render with their previous appearance after migration.
- Existing backup, restore, ScorePDF import, export, setlist, Back, and language flows continue to pass.
- SeliaScan remains unchanged, and SeliaLists appears as a target for Android PDF sharing and opening.
- SeliaLists launcher and Play artwork match the current first-party SeliaScan assets byte-for-byte.
