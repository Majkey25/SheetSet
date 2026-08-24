# SheetSet Performance Essentials v0.4 Design

## Status

Approved product direction. This specification defines the musician-focused feature set for `v0.4.0-alpha.1` and includes the already verified ScorePDF backup import, backup sharing, Android Back behavior, and Buy Me a Coffee work.

## Goal

Make SheetSet reliable for rehearsal and live performance without turning its monochrome PDF/setlist interface into a general music suite. Add the features shared by ScorePDF, MobileSheets, forScore, and Piascore that directly improve finding a score, resuming it, reading it hands-free, and navigating repeats.

## Constraints

- Keep one offline Android application module with no Internet permission, account, ads, analytics, or subscription.
- Keep `minSdk = 33`, `compileSdk = 36`, `targetSdk = 36`, and package ID `cz.teply.sheetset`.
- Use Android and existing Compose APIs. Add no runtime dependency.
- Preserve imported PDFs byte-for-byte. New behavior stores only SheetSet metadata and annotations.
- Keep English, Czech, Slovak, German, and Polish resources complete.
- Keep the paper-like monochrome interface. Annotation colors and the Buy Me a Coffee brand button remain the only color exceptions.
- Existing v1 catalogs, v1 SheetSet backups, annotations, PDFs, and setlists must migrate without loss.

## Release scope

### Existing completed work

- Import ScorePDF backup ZIP files as an atomic merge of PDFs and ordered setlists.
- Share a generated SheetSet backup through Android's share sheet and cache-only `FileProvider`.
- Keep root Back inside SheetSet while preserving nested Back navigation.
- Show ScanIt's optional Buy Me a Coffee button in About.

### Bookmarks and resume

Each score stores zero or more bookmarks with a stable ID, title, and zero-based page index. A bookmark title is optional in the editor; an empty title becomes the localized page label. Bookmark titles are limited to 120 characters, each score is limited to 1,000 bookmarks, and every page index must remain inside the score.

The reader's performance-tools sheet lists bookmarks for the current score, adds the current page, renames or deletes a bookmark, and jumps directly to one. Library search returns both score and bookmark matches; selecting a bookmark opens its score at that page.

Opening a score directly from the library resumes its last page and half-page position. Opening a score from a setlist starts at its first page so a performance remains deterministic. Reader position is saved after a page or half-page transition and when the reader closes. Deleting a score deletes its bookmarks and saved position with the score record.

### Reader layouts

`ReaderLayout` has three values:

- `SINGLE`: the current one-page reader using the selected page-fit setting.
- `HALF`: a fit-width page shown as top and bottom segments. Next advances top → bottom → next page top; Previous reverses that sequence.
- `TWO_PAGE`: two adjacent pages on medium or expanded windows. Next and Previous advance one spread. Entering annotation mode temporarily shows the selected page as a single page; leaving annotation mode restores the spread.

Compact windows offer Single and Half. Medium and expanded windows offer all three layouts. Rotation or resizing falls back to Single only while Two-page cannot fit; the saved preference remains Two-page and is restored when space becomes available.

The current page indicator displays `page / total`, `page top|bottom / total`, or `first–second / total` according to the active layout. Page changes across a setlist continue to the next or previous score without losing occurrences of duplicate scores.

`PdfPageView` keeps single-page rendering and annotations. Half layout adds a bounded programmatic vertical offset. Two-page view composes two read-only `PdfPageView` instances; annotation mode uses one existing editable instance. This avoids a second PDF renderer implementation and keeps annotation geometry unchanged.

### Hands-free input

While the reader is open, `MainActivity` maps common pedal/keyboard key-down events with `repeatCount == 0`:

- Previous: Page Up, Left, Up, Space.
- Next: Page Down, Right, Down, Enter and Numpad Enter.

Keys are not intercepted outside the reader. Volume, media, Back, Home, and unknown keys retain Android behavior. The same layout-aware Previous and Next actions serve touch, keyboard, and pedal input.

### Automatic scrolling

Automatic scrolling is available from the reader's performance-tools sheet. Starting it temporarily uses fit-width rendering. A main-thread frame loop moves the current page downward at one of three bounded speeds: Slow, Medium, or Fast. Reaching the bottom advances to the next page or score and continues from the top. Pause keeps the current position; Stop restores normal touch navigation.

Auto-scroll stops when the reader closes, annotation mode opens, a dialog or tools sheet opens, the app leaves the foreground, the user manually changes page, or the end of the final score is reached. The chosen speed persists as a fixed app setting; running state never persists.

### Labels, sort, and search

Scores and setlists store up to 20 labels, each normalized by trimming whitespace, removing empty values, preserving first occurrence order, and limiting text to 40 characters. The existing row menu opens a small label editor. Labels are plain user data; there is no global label-management subsystem.

The PDF library can sort by title, import time, or last viewed time in ascending or descending order. Setlists can sort by title or creation time. The default remains current creation order. Search matches score/setlist title, label, and bookmark title. Sorting and active search stay UI state and are not added to backup settings.

## Data model and migration

Catalog JSON version 2 adds:

```kotlin
data class Bookmark(
    val id: String,
    val title: String,
    val pageIndex: Int,
)

data class Score(
    // existing fields
    val labels: List<String> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val lastPageIndex: Int = 0,
    val lastPagePart: Int = 0,
    val lastViewedAtEpochMs: Long = 0,
)

data class Setlist(
    // existing fields
    val labels: List<String> = emptyList(),
    val createdAtEpochMs: Long = 0,
)
```

The decoder accepts catalog versions 1 and 2. Version 1 receives empty labels/bookmarks, page zero, part zero, last-viewed zero, and uses list position as a stable fallback when creation timestamps are absent. The encoder writes version 2 only. Invalid labels, bookmark IDs, page indexes, page parts, or timestamps fail closed before replacing current data.

ScorePDF import initializes the new fields with validated defaults. Native SheetSet restore accepts backup manifest versions 1 and 2; v1 settings use defaults for new values. New backups write manifest version 2 and catalog version 2.

`AppSettings` adds `readerLayout` and `autoScrollSpeed`. `SettingsStore` and backup settings parse both as bounded enums and fall back to Single and Medium.

## UI

The reader navigation bar remains one row:

- Previous
- centered page position
- Next
- Performance tools
- Annotate

Performance tools opens one bottom sheet containing layout, auto-scroll start/pause/stop and speed, then bookmarks. It does not add permanent bars, floating cards, or a dashboard.

Library and setlist headers add one sort icon next to search. Active sort direction is exposed to accessibility services. Labels appear as compact text below a title only when present. Bookmark search results use the existing row style and show their page number.

All controls keep 48 dp targets, localized content descriptions, selected-state semantics, D-pad focus, and tablet-safe widths.

## Error handling

- A bookmark outside its score is rejected without changing the catalog.
- Two-page rendering failure falls back to Single and reports the existing localized action error.
- Auto-scroll render failure stops scrolling and leaves the current page available.
- Unsupported pedal keys pass through untouched.
- Catalog or backup migration validates in staging and preserves current data on failure.
- Labels over limits are normalized in the editor and validated again by the catalog decoder.

## Testing

### Unit tests

- Catalog v1 → v2 migration and v2 round trip.
- Bookmark add, rename, delete, page validation, and search matching.
- Label normalization and limits.
- Sort comparators and direction.
- Half-page Previous/Next state transitions across score and setlist boundaries.
- Two-page spread indexes at the first, middle, and final odd page.
- Pedal key mapping allowlist.
- Auto-scroll speed and end-of-page transition logic.
- Backup v1/v2 settings migration.

### Instrumentation tests

- Bookmark creation, rename, jump, deletion, and library search result.
- Direct score resume versus deterministic setlist start.
- Single, Half, and tablet Two-page layouts.
- Physical Page Up/Down, arrows, Space, and Enter in the reader; the same keys remain untouched in the library.
- Auto-scroll start, pause, stop, and automatic next-page transition.
- Score and setlist labels, search, and sorting.
- Existing import, setlist, annotation, backup, ScorePDF import, share, Back, language, phone, and tablet flows remain green.

### Runtime acceptance

- API 33 phone emulator: import, bookmarks, Half, pedal keys, auto-scroll, resume, labels, backup round trip.
- API 35 tablet emulator: Two-page, odd final page, annotation single-page fallback, rotation, setlist continuation.
- Supplied ScorePDF backup: 64 score records, 64 internal PDFs, 8 setlists, ordered duplicate occurrences.
- Shared backup passes `unzip -t`, restores into an empty install, and reproduces the catalog and settings.
- Crash buffer is empty after all scenarios.

## GitHub and release

- Version: `0.4.0-alpha.1`; version code: `5`.
- Update changelog, README feature/limitation lists, Pages copy, release notes, screenshots only where visible UI changed, and existing release verification script inputs.
- Commit verified implementation on `feat/adaptive-pdf-editor/20-08-2026` and push the branch.
- Open a new PR to `main`; PR #7 is closed as superseded only after the new PR contains all of its commits and checks.
- Merge the new PR through GitHub. Do not bypass branch protection.
- Tag merged `main` as `v0.4.0-alpha.1` and publish a prerelease with signed APK and SHA-256 asset.
- Download release assets and verify hash, manifest version, APK signature, clean install, launch, core UI, supplied ScorePDF import, shared backup, and crash buffer.

## Deferred to v0.5

- Page reorder, duplicate, delete, blank-page insertion, rotate, crop, split, and merge.
- Jump links/link points and configurable action buttons.
- Annotation layers, stamps, musical symbols, tool favorites, pressure sensitivity, and palm rejection controls.
- Metronome, audio tracks, MIDI actions, tuner, recorder, cloud synchronization, and multi-device collaboration.

These remain deferred because they modify PDF structure, introduce audio/MIDI state, or require a second annotation subsystem. None is needed to prove the v0.4 performance workflow.

## Acceptance criteria

- Existing user data migrates and restores without loss.
- Bookmarks, resume, labels, sorting, and search persist and behave as specified.
- Touch, pedal, Half, Two-page, and auto-scroll use one consistent page-transition model.
- Reader UI stays one navigation row plus one bottom sheet.
- All five languages remain complete.
- Unit tests, Android lint, debug/release builds, all instrumentation tests, API 33/35 runtime scenarios, GitHub checks, PR merge, and downloaded release verification pass.

## References

- ScorePDF features and v13-v15 update notes: `https://enoiu.com/en/app/scorepdf/`
- MobileSheets library, annotation, and performance utilities: `https://www.zubersoft.com/mobilesheets/features/`
- forScore basics, bookmarks, annotation, links, and tools: `https://forscore.co/documentation/`
- Piascore user manual: `https://piascore.com/manual/`
