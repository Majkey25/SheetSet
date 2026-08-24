# ScorePDF Backup Import and Sharing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import ScorePDF PDFs and ordered setlists safely, share SheetSet backups, keep root Back inside the app, and add the ScanIt support button.

**Architecture:** A small read-only Hive parser produces a validated import plan. `LibraryRepository` stages the foreign archive, writes generated score files, and atomically publishes one merged catalog. UI work reuses Compose `BackHandler`, Android `FileProvider`, and the existing settings drawer.

**Tech Stack:** Kotlin 2, Android 13+, Jetpack Compose, `java.util.zip`, `PdfRenderer`, AndroidX `FileProvider`.

**Spec:** `docs/superpowers/specs/2026-08-23-sheetset-scorepdf-backup-share-design.md`

## Global Constraints

- Minimum Android version remains API 33.
- No new dependency or broad storage permission.
- Existing SheetSet backup restore remains compatible and replacing.
- ScorePDF import merges and must not modify current data on failure.
- Do not commit, push, or release without explicit approval.

---

### Task 1: Legacy Hive parser

**Files:**
- Create: `app/src/main/java/cz/teply/sheetset/data/ScorePdfHive.kt`
- Test: `app/src/test/java/cz/teply/sheetset/data/ScorePdfHiveTest.kt`

**Interfaces:**
- Produces: `ScorePdfHive.readScores(ByteArray): List<ScorePdfScore>` and `ScorePdfHive.readSetlists(ByteArray): List<ScorePdfSetlist>`.

- [ ] Write tests that encode verified Hive frames and assert latest-frame replay, deletion, duplicate PDF filenames, exact setlist ordering, bad CRC rejection, and missing-reference rejection at the importer boundary.
- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests "cz.teply.sheetset.data.ScorePdfHiveTest" --console=plain` and verify the missing parser causes failure.
- [ ] Implement bounded little-endian frame parsing, CRC32 validation, required adapter fields, and exact integer conversion.
- [ ] Re-run the focused unit test and verify it passes.

### Task 2: Atomic ScorePDF ZIP merge

**Files:**
- Create: `app/src/main/java/cz/teply/sheetset/data/ScorePdfBackup.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/data/LibraryBackup.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/data/LibraryRepository.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt`
- Test: `app/src/androidTest/java/cz/teply/sheetset/data/LibraryBackupTest.kt`

**Interfaces:**
- Consumes: `ScorePdfHive.readScores` and `ScorePdfHive.readSetlists`.
- Produces: `BackupImportResult` with optional SheetSet metadata and ScorePDF import counts.

- [ ] Add Android tests for merging an existing library with a foreign ZIP, preserving duplicate score records and setlist order, rejecting an unsafe/malformed ZIP without catalog changes, and preserving normal SheetSet restore.
- [ ] Run `./gradlew.bat :app:connectedDebugAndroidTest --console=plain` and verify the new tests fail because foreign import is unsupported.
- [ ] Add bounded ZIP spooling/detection, staging, unique file generation, PDF validation, reference validation, cleanup, and one final atomic catalog write.
- [ ] Update the ViewModel so SheetSet metadata is applied only for native restores.
- [ ] Re-run the Android tests and verify all pass.

### Task 3: Share, Back, and About support

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/share_paths.xml`
- Create: `app/src/main/res/drawable/ic_share_24.xml`
- Create: `app/src/main/res/drawable/ic_coffee.xml`
- Modify: `app/src/main/java/cz/teply/sheetset/MainActivity.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/SettingsDrawer.kt`
- Modify: all five `strings.xml` locale files.
- Test: `app/src/androidTest/java/cz/teply/sheetset/SheetSetFlowTest.kt`
- Test: `app/src/androidTest/java/cz/teply/sheetset/SettingsFlowTest.kt`

**Interfaces:**
- Produces: `SheetSetViewModel.createSharedBackup((Uri) -> Unit)` and `SheetSetActions.shareBackup`.

- [ ] Add Compose tests for a visible Share backup menu item, support button, reader/detail Back navigation, and root Back remaining in SheetSet.
- [ ] Run focused instrumentation tests and verify the new assertions fail.
- [ ] Configure a cache-only `FileProvider`, generate one atomic shared ZIP, launch `ACTION_SEND`, add root/nested `BackHandler` behavior, and add the localized ScanIt support button.
- [ ] Re-run focused tests and verify they pass.

### Task 4: Release-grade verification without publication

**Files:**
- No production files unless verification exposes a defect.

- [ ] Run `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain`.
- [ ] Run `./gradlew.bat :app:connectedDebugAndroidTest --console=plain` on an isolated API 35 emulator.
- [ ] Install the debug APK, import the supplied ScorePDF ZIP, and verify 64 imported score rows, 8 imported setlists, exact setlist navigation, root Back containment, reader Back, Share chooser, and Coffee browser intent.
- [ ] Review `git diff --check`, `git diff --stat`, and the full diff; remove scratch parser artifacts and report that no commit/push/release occurred.
