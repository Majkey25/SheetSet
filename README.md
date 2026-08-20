# SheetSet

Offline Android PDF organizer for musicians. Import scores, build unlimited setlists, write annotations, and export a marked-up copy. No account, ads, analytics, cloud service, or Internet permission.

[![Android CI](https://github.com/Majkey25/SheetSet/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Majkey25/SheetSet/actions/workflows/android-ci.yml)
[![Latest release](https://img.shields.io/github/v/release/Majkey25/SheetSet?include_prereleases)](https://github.com/Majkey25/SheetSet/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-111111.svg)](LICENSE)

<p align="center">
  <img src="site/assets/home.png" alt="SheetSet PDF library empty state" width="280">
  <img src="site/assets/reader.png" alt="SheetSet reader with a handwritten annotation" width="280">
</p>

## What it does

- Imports one or more PDFs through the Android file picker.
- Stores private offline copies and validates each file before adding it.
- Creates unlimited ordered setlists without duplicating PDFs.
- Reads a setlist continuously across score boundaries.
- Supports pen, highlighter, eraser, undo, redo, page taps, swipes, and pinch zoom.
- Preserves the imported original and exports a new annotated PDF.
- Uses Android per-app languages: English, Czech, Slovak, German, and Polish.

## Install

Download the preview APK from [GitHub Releases](https://github.com/Majkey25/SheetSet/releases). Preview builds are debug-signed and intended for testing. Google Play publishing is not part of this repository yet.

SheetSet requires Android 8.0 or newer.

## Build

Requirements: JDK 17 and Android SDK 36.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon --console=plain
.\gradlew.bat assembleDebugAndroidTest --no-daemon --console=plain
```

Install the debug APK:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Design

SheetSet uses one Android application module, Jetpack Compose, and platform PDF APIs. `LibraryRepository` stores PDFs and an atomic JSON catalog. `PdfPageView` renders and annotates pages. `PdfExporter` writes an annotated copy one page at a time.

The app declares no permissions. Imported files stay in app-private storage. Export writes only to the location selected in the Android document picker.

## Current limits

- Android only.
- PDF text and vector objects are not edited. Annotations are a separate layer.
- Export rasterizes source pages at up to 144 dpi with a 12 MP memory cap.
- No cloud sync, backup, scanner, metronome, or Bluetooth pedal settings.

See the [design specification](docs/superpowers/specs/2026-08-20-sheetset-design.md) and [implementation plan](docs/superpowers/plans/2026-08-20-sheetset.md).

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). Report security problems through [GitHub private vulnerability reporting](https://github.com/Majkey25/SheetSet/security/advisories/new).

## License

[MIT](LICENSE)
