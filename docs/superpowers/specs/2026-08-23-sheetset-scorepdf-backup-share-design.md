# ScorePDF Backup Import and Sharing Design

## Scope

SheetSet accepts its own backup ZIP and ScorePDF backup ZIP files. A SheetSet backup keeps its existing replace-and-restore behavior. A ScorePDF backup merges every score record, every PDF, and every setlist into the current library without deleting current content.

This slice also adds direct backup sharing, safe Android Back behavior, and the same optional Buy Me a Coffee link used by ScanIt. Broader ScorePDF feature parity remains separate work.

## ScorePDF compatibility

The supplied archive contains 61 PDF files, 64 live score records, 8 live setlists, and 310 ordered setlist references. Three score records intentionally share an archive PDF. SheetSet imports each score record as a separate score so setlist identity and ordering remain exact.

ScorePDF stores metadata in legacy Hive 2.x frames. The importer implements only the verified read-only prefix needed from two adapters:

- `newscorebox.hive`, adapter `32`: score key, title field `1`, PDF filename field `5`.
- `setlistbox.hive`, adapter `36`: setlist name field `0`, ordered score-key list field `1`.

Frames are replayed in file order. Later values replace earlier values and deletion frames remove keys. CRC32, lengths, expected field types, UTF-8, reference integrity, and archive PDF names are validated. Other Hive boxes and lock files are ignored because SheetSet cannot safely map ScorePDF settings or proprietary annotation models in this slice.

## Data safety

ZIPs are untrusted input. Entry names must be flat and unique, entry count and byte totals are bounded, PDF content is validated with `PdfRenderer`, and Hive files have dedicated size limits. Import is staged. New score files are written with generated internal names; the catalog is atomically replaced only after every file and setlist is valid. Any failure removes staged/new files and leaves the current catalog unchanged.

ScorePDF imports merge. Existing scores and setlists remain untouched. Duplicate titles and duplicate score occurrences are preserved.

## Android behavior

- `Backup` continues to save through the Storage Access Framework.
- `Share backup` creates one bounded ZIP in app cache and opens Android's share sheet through `FileProvider`; the previous shared ZIP is replaced.
- Back closes the reader or compact setlist detail. Settings subpages return to the settings menu. Back on the root library is consumed and does not finish the activity.
- About contains ScanIt's yellow support button and opens `https://www.buymeacoffee.com/majkey` only after a user tap.

## Verification

Unit tests cover Hive frame replay, deletion, malformed CRC, and setlist order. Android repository tests cover merge, duplicate shared PDFs, malformed archives, rollback, and existing SheetSet restore. Emulator QA covers root Back, reader Back, Share backup chooser, Coffee link, and importing the supplied ScorePDF backup.
