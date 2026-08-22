# Changelog

## [0.3.0-alpha.1] - 2026-08-22

### Added

- Hamburger menu with reader, annotation, language, backup, restore, and about settings.
- English, Czech, Slovak, German, Polish, and device-language selection.
- Tablet navigation rail and expanded setlist list-detail layout.
- Colored pen, highlight, underline, strike-through, text box, line, arrow, rectangle, and ellipse annotations.
- Selection, move, resize, delete, eraser, undo, redo, pinch zoom, and horizontal panning.
- Versioned ZIP backup and validated rollback-safe restore for PDFs, setlists, annotations, settings, and language.
- Optional ScanIt handoff from the PDF import sheet.

### Changed

- Android 13 is now the minimum supported version.
- Reader tools use a horizontally scrollable phone palette and a vertical tablet palette.
- Import and Create remain visible as contextual actions in the upper-right corner.

### Fixed

- Version 1 pen and highlighter annotations migrate without data loss.
- Replaced text glyph controls with accessible vector icons.
- Hidden drawer destinations no longer leak into screen-reader semantics.

## [0.2.0-alpha.1] - 2026-08-20

### Changed

- Reworked the library, setlist, dialogs, and reader controls into one restrained monochrome interface.
- Moved import and search actions into one header and removed the duplicate PDF heading.
- Replaced the filled tab bar with a lighter paper-style selection indicator.
- Tightened spacing and control bars so scores keep more of the screen.

### Fixed

- Tabs and annotation tools now report their selected state to screen readers.

## [0.1.0-alpha.1] - 2026-08-20

### Added

- Offline PDF import with MIME, size, signature, and `PdfRenderer` validation.
- Unlimited setlists with accessible ordering controls.
- Full-page reader with setlist continuation, zoom, taps, and swipes.
- Persistent pen and highlighter annotations with eraser, undo, and redo.
- Export of annotated PDF copies without changing imported originals.
- English, Czech, Slovak, German, and Polish resources.
- Android CI, release automation, and a static GitHub Pages site.
