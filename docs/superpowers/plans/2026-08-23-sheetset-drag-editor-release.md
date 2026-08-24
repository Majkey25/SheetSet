# SheetSet Drag Editor Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add direct setlist drag ordering and a ScorePDF-style two-row annotation dock, then publish `v0.3.0-alpha.2`.

**Architecture:** Keep catalog persistence unchanged. Reorder a local keyed list during the gesture and call the existing `moveScore` action once on drop. Keep annotation data models unchanged; add a straight-stroke point reducer and expose existing tools through a new two-row Compose dock.

**Tech Stack:** Kotlin 2.3, Jetpack Compose Material 3, Android View/PdfRenderer, JUnit 4, AndroidX Compose UI tests, Gradle 8.13.

---

### Task 1: Drag setlist rows

**Files:**
- Modify: `app/src/androidTest/java/cz/teply/sheetset/SheetSetFlowTest.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/SetlistScreens.kt`
- Create: `app/src/main/res/drawable/ic_drag_handle_24.xml`
- Modify: localized `strings.xml` files

- [ ] Replace the old test assertion for Move down with assertions that each edit row has a Reorder handle and no visible Move up/Move down controls.
- [ ] Run the focused instrumentation test and verify it fails against the arrow implementation.
- [ ] Add a keyed local list, pointer drag state, edge scrolling, row translation, and one `moveScore(setlistId, fromIndex, toIndex)` call on drop.
- [ ] Add invisible screen-reader custom actions for moving an item one position up or down.
- [ ] Run the focused test and `LibraryCatalogTest`; verify both pass.

### Task 2: Build the two-row annotation dock

**Files:**
- Modify: `app/src/test/java/cz/teply/sheetset/pdf/AnnotationGeometryTest.kt`
- Modify: `app/src/androidTest/java/cz/teply/sheetset/SheetSetFlowTest.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/AnnotationGeometry.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/pdf/PdfPageView.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/ReaderScreen.kt`
- Create: `app/src/main/res/drawable/ic_straighten_24.xml`
- Modify: localized `strings.xml` files

- [ ] Add a failing unit test proving straight mode keeps only the first and last stroke points.
- [ ] Add failing Compose assertions for Draw/Add modes, Straight line, direct color buttons, and fixed Undo/Redo/Done controls.
- [ ] Implement `strokePoints(points, straightLine)` and use it for pen and highlighter previews and commits.
- [ ] Make highlighter use `InkKind.HIGHLIGHTER` with configured opacity and a wider freehand stroke.
- [ ] Replace the one-row/side palette with two bottom rows: page/width/straight/colors and modes/tools/history/done.
- [ ] Persist width changes through the existing `updateSettings` action and keep all color changes one tap away.
- [ ] Run focused unit and instrumentation tests and verify the old text, shape, and selection tools remain exposed.

### Task 3: Verify and release

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `CHANGELOG.md`
- Modify: `README.md` only if the documented version or editor description is stale

- [ ] Set `versionCode = 4` and `versionName = "0.3.0-alpha.2"`.
- [ ] Add a concise changelog entry for drag ordering and the annotation dock.
- [ ] Run `gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease --console=plain`.
- [ ] Install the debug and androidTest APKs only on `emulator-5560`; run focused instrumentation with `adb -s emulator-5560 shell am instrument`.
- [ ] Verify live setlist drag, edge behavior, freehand pen, highlighter, straight line, colors, undo/redo, page navigation, and one nearby reader workflow.
- [ ] Review `git diff`, check for secrets and unrelated changes, then commit the scoped release changes.
- [ ] Push the current feature branch, tag `v0.3.0-alpha.2`, and publish a GitHub release with APK and SHA-256 checksum.
- [ ] Download the release asset, verify its hash and package metadata, install it on `emulator-5560`, and repeat the critical smoke flow.
