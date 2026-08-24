# SheetSet Performance Essentials v0.4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Ship v0.4.0-alpha.1 with ScorePDF backup interoperability, bookmarks, resume, labels/search/sort, pedal input, Half/Two-page layouts, and automatic scrolling.

**Architecture:** Catalog version 2 owns persistent metadata. Pure reader-navigation functions define touch, pedal, layout, and auto-scroll transitions. Existing PdfPageView remains the renderer; Compose uses one editable view or two read-only instances.

**Tech Stack:** Kotlin 2, Android API 33+, Jetpack Compose, PdfRenderer, SharedPreferences, JUnit 4, AndroidX instrumentation.

**Spec:** docs/superpowers/specs/2026-08-24-sheetset-performance-essentials-design.md

## Global Constraints

- Keep minSdk 33, compileSdk 36, targetSdk 36, package cz.teply.sheetset.
- Add no runtime dependency or Internet/storage permission.
- Preserve imported PDF bytes and v1 catalogs/backups.
- Keep EN/CS/SK/DE/PL resources complete.
- Version target is 0.4.0-alpha.1, code 5.
- Every production branch or loop gets a failing test first.

---

### Task 1: Finish verified backup interoperability

**Files:** Existing uncommitted ScorePDF import/share/Back/Coffee files plus their unit and instrumentation tests.

**Produces:** LibraryRepository.restoreBackup(InputStream): BackupMetadata? and SheetSetActions.shareBackup.

- [ ] Run focused existing checks:

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "cz.teply.sheetset.data.ScorePdfHiveTest" :app:assembleDebugAndroidTest --console=plain
~~~

- [ ] Check git status and git diff --check; exclude build output, source backup, and temp research.
- [ ] Stage only source/tests/spec/plan and commit feat(backup): import and share score libraries.

### Task 2: Catalog v2, bookmarks, labels, and migration

**Files:** Models.kt, LibraryRepository.kt, CatalogJsonTest.kt, LibraryCatalogTest.kt.

**Produces:**

~~~kotlin
data class Bookmark(val id: String, val title: String, val pageIndex: Int)
fun LibraryCatalog.addBookmark(scoreId: String, bookmark: Bookmark): LibraryCatalog
fun LibraryCatalog.updateScoreLabels(scoreId: String, labels: List<String>): LibraryCatalog
fun LibraryCatalog.saveReaderPosition(scoreId: String, page: Int, part: Int, viewedAt: Long): LibraryCatalog
~~~

- [ ] Add failing literal v1/v2 JSON fixtures and model tests for defaults, round trip, bookmark CRUD, label normalization, limits, and score deletion.
- [ ] Run RED:

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "cz.teply.sheetset.data.CatalogJsonTest" --tests "cz.teply.sheetset.data.LibraryCatalogTest" --console=plain
~~~

- [ ] Implement catalog v2 with 1,000 bookmarks, 20 labels, 40 characters per label; v1 defaults are zero/empty.
- [ ] Run GREEN and commit feat(library): add bookmarks and labels.

### Task 3: Search and sort policy

**Files:** Create ui/LibraryQuery.kt and ui/LibraryQueryTest.kt.

**Produces:** LibrarySort, SetlistSort, SortDirection, ScoreResult, BookmarkResult, queryScores, sortSetlists.

- [ ] Add failing tests for title/label/bookmark matching, direct bookmark pages, stable sorting, both directions, and setlist sorting.
- [ ] Run RED with LibraryQueryTest.
- [ ] Implement pure list transforms only; no persistent manager.
- [ ] Run GREEN and commit feat(library): search bookmarks and labels.

### Task 4: Reader transition model and resume

**Files:** Create ui/ReaderNavigation.kt; modify LibraryUiState.kt, SheetSetViewModel.kt, LibraryRepository.kt; add ReaderNavigationTest.kt.

**Produces:** ReaderPosition, nextPosition, previousPosition, spreadPages, openScoreAt, bookmark actions.

- [ ] Add failing boundary tests for Single, Half top/bottom, Two-page odd/even pages, duplicate setlist occurrences, previous-score final page, direct resume, and setlist page-zero start.
- [ ] Run RED with ReaderNavigationTest.
- [ ] Implement pure transitions, route touch actions through them, and persist validated position after transition/close.
- [ ] Run unit and repository GREEN; commit feat(reader): resume saved score position.

### Task 5: Pedal and keyboard allowlist

**Files:** MainActivity.kt, ReaderNavigation.kt, ReaderNavigationTest.kt, MainActivitySmokeTest.kt.

**Produces:** pedalDirection(keyCode, repeatCount): PageDirection?.

- [ ] Add failing tests for Page Up/Down, arrows, Space, Enter/Numpad Enter, repeats, volume/media/unknown keys, and library pass-through.
- [ ] Run RED.
- [ ] Implement one dispatchKeyEvent guard: ACTION_DOWN, active reader, repeat zero, mapped key. Delegate everything else.
- [ ] Run GREEN and commit feat(reader): support page-turn pedals.

### Task 6: Reader settings and backup v2

**Files:** AppSettings.kt, SettingsStore.kt, LibraryBackup.kt, SettingsStoreTest.kt, LibraryBackupTest.kt.

**Produces:** ReaderLayout, AutoScrollSpeed, v2 backup writer, v1/v2 restore.

- [ ] Add failing tests for defaults/round trip/invalid enums, v1 restore defaults, and v2 restore.
- [ ] Run focused instrumentation RED.
- [ ] Persist bounded enums; write manifest v2 and accept v1/v2.
- [ ] Run GREEN and commit feat(backup): migrate reader settings.

### Task 7: Half-page viewport and scroll primitive

**Files:** PdfPageView.kt; create PdfViewport.kt and PdfViewportTest.kt.

**Produces:** halfPagePan, scrollPan, PdfPageView.setHalfPagePart, scrollByPixels.

- [ ] Add failing geometry tests for top/bottom pan, clamping, bottom detection, and fit-page zero scrolling.
- [ ] Run RED.
- [ ] Implement pure viewport math then minimal view methods.
- [ ] Run GREEN and commit feat(reader): add half-page viewport.

### Task 8: Two-page and performance-tools UI

**Files:** Create PerformanceToolsSheet.kt; modify ReaderScreen.kt and SheetSetApp.kt; add required vector icons; update SheetSetFlowTest.kt and AdaptiveLayoutTest.kt.

**Consumes:** ReaderPosition, spreadPages, ReaderLayout, bookmark actions.

- [ ] Add failing Compose tests for navigation controls, layouts, bookmark CRUD/jump, compact Two-page omission, expanded spread, and annotation fallback.
- [ ] Run focused instrumentation RED.
- [ ] Reuse PdfPageView for spread pages and pass each page annotations independently.
- [ ] Run GREEN and commit feat(reader): add performance tools.

### Task 9: Automatic scrolling

**Files:** Create AutoScroll.kt; modify ReaderScreen.kt and PerformanceToolsSheet.kt; add AutoScrollTest.kt and UI tests.

**Produces:** autoScrollPixels(speed, elapsedMs, density) and lifecycle-safe running state.

- [ ] Add failing tests for three speeds, pause, bottom transition, and final-score stop.
- [ ] Run RED.
- [ ] Implement one withFrameNanos loop active only while resumed/viewing; stop on every event named by the spec.
- [ ] Run GREEN and commit feat(reader): add automatic scrolling.

### Task 10: Labels/search/sort UI

**Files:** LibraryScreen.kt, SetlistScreens.kt, SheetSetApp.kt, SheetSetViewModel.kt, SheetSetFlowTest.kt.

- [ ] Add failing Compose flows for score/setlist labels, bookmark search, bookmark page opening, sort modes/directions, and accessibility state.
- [ ] Run focused instrumentation RED.
- [ ] Implement one comma-separated label dialog and one sort menu per destination.
- [ ] Run GREEN and commit feat(library): add labels and sorting.

### Task 11: i18n, version, docs, and release assets

**Files:** All five strings.xml sets, app/build.gradle.kts, CHANGELOG.md, README.md, site/index.html, release notes under .reference/tmp.

- [ ] Add every key to EN/CS/SK/DE/PL and verify resource parity.
- [ ] Set version 0.4.0-alpha.1/code 5 and document exact shipped/deferred behavior.
- [ ] Run unit, resource, lint, and assemble gates.
- [ ] Commit docs: prepare v0.4 preview.

### Task 12: Review, runtime acceptance, PR, merge, and release

- [ ] Run hostile review for correctness, migration, security, performance, accessibility, and dead code.
- [ ] Run fresh local gates:

~~~powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --console=plain
git diff --check
git status --short --branch
~~~

- [ ] Run all instrumentation tests on isolated API 33 and API 35 plus supplied ScorePDF import/share/restore.
- [ ] Push feature branch, create PR to main, wait for checks/review, and merge without bypassing protection.
- [ ] Verify merged main, tag v0.4.0-alpha.1, build from that commit, calculate SHA-256, and publish prerelease.
- [ ] Download assets, compare hashes, verify manifest/signature, clean install, launch, core UI, ScorePDF import, shared restore, and crash buffer.
- [ ] Close PR #7 as superseded only after the new PR is merged and its ancestry is in main.
