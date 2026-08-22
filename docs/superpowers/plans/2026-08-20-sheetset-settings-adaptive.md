# SheetSet settings and adaptive UI implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move SheetSet to Android 13+, add the hamburger settings menu and in-app language selection, place destination actions at the upper right, and provide phone and tablet layouts.

**Architecture:** Keep the single Compose activity and current repository. Add a small `SharedPreferences` settings store, let Android `LocaleManager` own language state, and pass one pure width class into the shell. Reuse Material 3 navigation components already present in the project.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose Material 3, Android API 33 to 36, `LocaleManager`, `SharedPreferences`, JUnit 4, and Compose UI instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-08-20-sheetset-editor-settings-adaptive-design.md`

## Global constraints

- Package ID stays `cz.teply.sheetset`.
- `minSdk = 33`, `compileSdk = 36`, and `targetSdk = 36`.
- No Internet permission or new runtime dependency.
- Fresh installs start in English. Supported languages are English, Czech, Slovak, German, and Polish.
- Phone widths use bottom navigation. Widths of 600 dp or more use a navigation rail. Widths of 840 dp or more can use list-detail panes.
- The top bar contains the hamburger at the left and only the destination action at the right.
- Keep every target at least 48 dp and every user-visible string in resources.

---

### Task 1: Android 13 floor and typed settings

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/cz/teply/sheetset/settings/AppSettings.kt`
- Create: `app/src/main/java/cz/teply/sheetset/settings/SettingsStore.kt`
- Test: `app/src/androidTest/java/cz/teply/sheetset/settings/SettingsStoreTest.kt`

**Interfaces:**
- Produces: `AppSettings`, `PageFit`, `ReaderDefaultTool`, `ToolSize`, `HighlightStrength`, `AnnotationTextSize`, `SettingsStore.load()`, and `SettingsStore.save(AppSettings)`.
- Consumes: Android `SharedPreferences` only.

- [ ] **Step 1: Write the failing settings test**

```kotlin
class SettingsStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("settings-test", Context.MODE_PRIVATE)

    @After
    fun cleanUp() {
        preferences.edit().clear().commit()
    }

    @Test
    fun invalidValuesFallBackToDefaults() {
        preferences.edit()
            .putString("page_fit", "BROKEN")
            .putString("pen_width", "MISSING")
            .putBoolean("keep_screen_awake", false)
            .commit()

        assertEquals(
            AppSettings(keepScreenAwake = false),
            SettingsStore(preferences).load(),
        )
    }

    @Test
    fun settingsRoundTrip() {
        val expected = AppSettings(
            pageFit = PageFit.WIDTH,
            pageTurnTaps = false,
            pageTurnSwipes = false,
            autoHideControls = false,
            defaultTool = ReaderDefaultTool.PEN,
            penWidth = ToolSize.THICK,
            highlighterStrength = HighlightStrength.STRONG,
            textSize = AnnotationTextSize.LARGE,
        )

        val store = SettingsStore(preferences)
        store.save(expected)

        assertEquals(expected, store.load())
    }
}
```

- [ ] **Step 2: Run the test and confirm the RED state**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=cz.teply.sheetset.settings.SettingsStoreTest' --no-daemon --console=plain
```

Expected: compilation fails because `AppSettings` and `SettingsStore` do not exist.

- [ ] **Step 3: Add the typed defaults and bounded parser**

```kotlin
enum class PageFit { PAGE, WIDTH }
enum class ReaderDefaultTool { VIEW, PEN, HIGHLIGHTER }
enum class ToolSize { THIN, MEDIUM, THICK }
enum class HighlightStrength { LIGHT, MEDIUM, STRONG }
enum class AnnotationTextSize { SMALL, MEDIUM, LARGE }

data class AppSettings(
    val keepScreenAwake: Boolean = true,
    val pageFit: PageFit = PageFit.PAGE,
    val pageTurnTaps: Boolean = true,
    val pageTurnSwipes: Boolean = true,
    val autoHideControls: Boolean = true,
    val defaultTool: ReaderDefaultTool = ReaderDefaultTool.VIEW,
    val penWidth: ToolSize = ToolSize.MEDIUM,
    val highlighterStrength: HighlightStrength = HighlightStrength.MEDIUM,
    val textSize: AnnotationTextSize = AnnotationTextSize.MEDIUM,
)

private inline fun <reified T : Enum<T>> SharedPreferences.enum(
    key: String,
    fallback: T,
): T = getString(key, null)?.let { raw -> enumValues<T>().firstOrNull { it.name == raw } }
    ?: fallback
```

`SettingsStore.load()` must read every key with a documented default. `save()` must write the complete `AppSettings` object in one editor transaction and use `apply()`.

- [ ] **Step 4: Raise the platform floor**

Change only:

```kotlin
defaultConfig {
    minSdk = 33
}
```

Do not change package ID, compile SDK, target SDK, or dependencies.

- [ ] **Step 5: Run focused and local gates**

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=cz.teply.sheetset.settings.SettingsStoreTest' --no-daemon --console=plain
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon --console=plain
```

Expected: all commands succeed and lint reports no invalid enum persistence or SDK-level calls.

- [ ] **Step 6: Commit**

```powershell
git add -- app/build.gradle.kts app/src/main/java/cz/teply/sheetset/settings/AppSettings.kt app/src/main/java/cz/teply/sheetset/settings/SettingsStore.kt app/src/androidTest/java/cz/teply/sheetset/settings/SettingsStoreTest.kt
git commit -m "feat(settings): require Android 13"
```

### Task 2: English-first per-app languages

**Files:**
- Create: `app/src/main/java/cz/teply/sheetset/settings/AppLanguages.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/MainActivity.kt`
- Test: `app/src/androidTest/java/cz/teply/sheetset/settings/AppLanguagesTest.kt`

**Interfaces:**
- Produces: `AppLanguages.initialize(Context)`, `AppLanguages.select(Context, String?)`, and `AppLanguages.supportedTags`.
- Consumes: `LocaleManager.applicationLocales` and the `language_initialized` preference.

- [ ] **Step 1: Write the failing locale test**

```kotlin
private val context = ApplicationProvider.getApplicationContext<Context>()

@Test
fun freshInstallSelectsEnglishOnlyOnce() {
    val preferences = context.getSharedPreferences("sheetset-settings", Context.MODE_PRIVATE)
    val localeManager = context.getSystemService(LocaleManager::class.java)
    preferences.edit().clear().commit()
    localeManager.applicationLocales = LocaleList.getEmptyLocaleList()

    AppLanguages.initialize(context)
    assertEquals("en", localeManager.applicationLocales.toLanguageTags())

    AppLanguages.select(context, null)
    AppLanguages.initialize(context)
    assertTrue(localeManager.applicationLocales.isEmpty)
}

@Test
fun unsupportedLanguageIsRejected() {
    assertThrows(IllegalArgumentException::class.java) {
        AppLanguages.select(context, "fr")
    }
}
```

The test cleanup must restore an empty locale list and clear `language_initialized`.

- [ ] **Step 2: Run the test and confirm the RED state**

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=cz.teply.sheetset.settings.AppLanguagesTest' --no-daemon --console=plain
```

Expected: compilation fails because `AppLanguages` does not exist.

- [ ] **Step 3: Implement the framework-backed selector**

```kotlin
object AppLanguages {
    val supportedTags = setOf("en", "cs", "sk", "de", "pl")

    fun initialize(context: Context) {
        val preferences = context.getSharedPreferences("sheetset-settings", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("language_initialized", false)) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags("en")
            preferences.edit().putBoolean("language_initialized", true).apply()
        }
    }

    fun select(context: Context, languageTag: String?) {
        require(languageTag == null || languageTag in supportedTags) { "Unsupported language" }
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (languageTag == null) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(languageTag)
        context.getSharedPreferences("sheetset-settings", Context.MODE_PRIVATE)
            .edit().putBoolean("language_initialized", true).apply()
    }
}
```

Call `AppLanguages.initialize(this)` after `super.onCreate(savedInstanceState)` and before `setContent` in `MainActivity`.

- [ ] **Step 4: Run locale, lint, and smoke tests**

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=cz.teply.sheetset.settings.AppLanguagesTest' --no-daemon --console=plain
.\gradlew.bat lintDebug assembleDebug --no-daemon --console=plain
```

- [ ] **Step 5: Commit**

```powershell
git add -- app/src/main/java/cz/teply/sheetset/settings/AppLanguages.kt app/src/main/java/cz/teply/sheetset/MainActivity.kt app/src/androidTest/java/cz/teply/sheetset/settings/AppLanguagesTest.kt
git commit -m "feat(settings): add per-app languages"
```

### Task 3: Single modern header and destination navigation

**Files:**
- Create: `app/src/main/java/cz/teply/sheetset/ui/AppShell.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/LibraryScreen.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/SetlistScreens.kt`
- Test: `app/src/androidTest/java/cz/teply/sheetset/SheetSetFlowTest.kt`
- Test: `app/src/androidTest/java/cz/teply/sheetset/MainActivitySmokeTest.kt`

**Interfaces:**
- Produces: `AppDestination`, `WindowLayout.fromWidth(Dp)`, `SheetHeader`, and `SheetNavigation`.
- Consumes: existing `SheetSetActions.importPdfs` and `createSetlist`.

- [ ] **Step 1: Add failing header tests**

```kotlin
private fun setEmptyApp() {
    composeRule.setContent {
        SheetSetTheme {
            SheetSetApp(
                state = LibraryUiState(),
                actions = SheetSetActions(),
                windowLayout = WindowLayout.COMPACT,
            )
        }
    }
}

@Test
fun headerUsesMenuAndPdfActionWithoutBrandTitle() {
    setEmptyApp()

    composeRule.onNodeWithContentDescription("Menu").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Import PDF").assertIsDisplayed()
    composeRule.onAllNodesWithText("SheetSet").assertCountEquals(0)
}

@Test
fun setlistsKeepsCreateAtUpperRightWhenEmpty() {
    setEmptyApp()
    composeRule.onNodeWithText("Setlists").performClick()

    composeRule.onNodeWithContentDescription("Create").assertIsDisplayed()
    composeRule.onAllNodesWithText("New setlist").assertCountEquals(0)
}
```

Use the existing Compose test setup instead of adding a new fixture framework.

- [ ] **Step 2: Run the two tests and confirm the RED state**

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=cz.teply.sheetset.SheetSetFlowTest' --no-daemon --console=plain
```

Expected: menu and `Create` nodes are missing and `SheetSet` is still displayed.

- [ ] **Step 3: Add pure shell types**

```kotlin
enum class AppDestination { PDF, SETLISTS }

enum class WindowLayout {
    COMPACT,
    MEDIUM,
    EXPANDED;

    companion object {
        fun fromWidth(width: Dp): WindowLayout = when {
            width < 600.dp -> COMPACT
            width < 840.dp -> MEDIUM
            else -> EXPANDED
        }
    }
}
```

`SheetHeader` takes `actionLabel`, `actionDescription`, `onMenu`, and `onAction`. It renders exactly two 48 dp controls and no title text. `SheetNavigation` takes `WindowLayout`, `AppDestination`, and `onDestination` and chooses the existing bottom tab treatment for compact windows or Material 3 `NavigationRail` otherwise.

- [ ] **Step 4: Remove duplicated empty-state actions**

`AppEmptyState` keeps the document illustration, title, and explanatory text. Remove its action parameter and button. The header owns `Import PDF` and `Create` for both empty and populated destinations.

- [ ] **Step 5: Run the focused and full shell tests**

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=cz.teply.sheetset.SheetSetFlowTest' --no-daemon --console=plain
```

Update old assertions only where the approved header deliberately changed visible copy. Do not weaken setlist-limit, search, reader, or accessibility assertions.

Change `MainActivitySmokeTest.appLaunches` to assert the `Import PDF` content description because the approved header no longer renders `SheetSet` text.

- [ ] **Step 6: Commit**

```powershell
git add -- app/src/main/java/cz/teply/sheetset/ui/AppShell.kt app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt app/src/main/java/cz/teply/sheetset/ui/LibraryScreen.kt app/src/main/java/cz/teply/sheetset/ui/SetlistScreens.kt app/src/androidTest/java/cz/teply/sheetset/SheetSetFlowTest.kt app/src/androidTest/java/cz/teply/sheetset/MainActivitySmokeTest.kt
git commit -m "feat(ui): add contextual app shell"
```

### Task 4: Settings drawer and state wiring

**Files:**
- Create: `app/src/main/java/cz/teply/sheetset/ui/SettingsDrawer.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/LibraryUiState.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/MainActivity.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-cs/strings.xml`
- Modify: `app/src/main/res/values-sk/strings.xml`
- Modify: `app/src/main/res/values-de/strings.xml`
- Modify: `app/src/main/res/values-pl/strings.xml`
- Test: `app/src/androidTest/java/cz/teply/sheetset/SettingsFlowTest.kt`

**Interfaces:**
- Produces: `LibraryUiState.settings`, `SheetSetViewModel.updateSettings(AppSettings)`, `SettingsDrawer`, and `SheetSetActions.selectLanguage(String?)`.
- Consumes: `SettingsStore` and `AppLanguages` from Tasks 1 and 2.

- [ ] **Step 1: Write the failing menu flow**

```kotlin
@Test
fun menuChangesReaderSettingsAndLanguage() {
    var settings by mutableStateOf(AppSettings())
    composeRule.setContent {
        SheetSetTheme {
            SheetSetApp(
                state = LibraryUiState(settings = settings),
                actions = SheetSetActions(
                    updateSettings = { settings = it },
                    selectLanguage = {},
                ),
                windowLayout = WindowLayout.COMPACT,
            )
        }
    }

    composeRule.onNodeWithContentDescription("Menu").performClick()
    composeRule.onNodeWithText("Reader").performClick()
    composeRule.onNodeWithText("Keep screen awake").performClick()

    composeRule.runOnIdle { assertFalse(settings.keepScreenAwake) }
}
```

Add one assertion each for `Language`, `Annotation defaults`, and `About` being reachable from the drawer.

- [ ] **Step 2: Run the test and confirm the RED state**

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=cz.teply.sheetset.SettingsFlowTest' --no-daemon --console=plain
```

- [ ] **Step 3: Wire settings through existing state**

Add:

```kotlin
data class LibraryUiState(
    // existing fields
    val settings: AppSettings = AppSettings(),
)

fun updateSettings(settings: AppSettings) {
    settingsStore.save(settings)
    mutableState.update { it.copy(settings = settings) }
}
```

Construct `SettingsStore` from `application.getSharedPreferences("sheetset-settings", MODE_PRIVATE)` in `SheetSetViewModel`. Preserve loaded settings whenever `launchAction` refreshes the catalog; do not replace the whole state with default settings.

- [ ] **Step 4: Build the drawer without a navigation framework**

Use `ModalNavigationDrawer` and one sealed local screen state: menu, language, reader, annotation defaults, or about. Reuse Material 3 `RadioButton`, `Switch`, `ListItem`, `HorizontalDivider`, and `TextButton`. The top-level hamburger toggles the drawer. A back row returns from a settings section to the menu.

Read the displayed version from `PackageManager.getPackageInfo(context.packageName, 0).versionName`. Open the repository and release URLs with `Intent(Intent.ACTION_VIEW, uri)`; do not add Internet permission.

Wire `selectLanguage` in `MainActivity`:

```kotlin
selectLanguage = { languageTag -> AppLanguages.select(this, languageTag) }
```

- [ ] **Step 5: Add exact resource keys in all five locales**

Add these keys with direct native-language equivalents:

| Key | English | Czech | Slovak | German | Polish |
|---|---|---|---|---|---|
| `menu` | Menu | Nabídka | Ponuka | Menü | Menu |
| `create` | Create | Vytvořit | Vytvoriť | Erstellen | Utwórz |
| `language` | Language | Jazyk | Jazyk | Sprache | Język |
| `reader_settings` | Reader | Čtečka | Čítačka | Reader | Czytnik |
| `annotation_defaults` | Annotation defaults | Výchozí anotace | Predvolené anotácie | Annotationsvorgaben | Domyślne adnotacje |
| `about` | About | O aplikaci | O aplikácii | Über die App | O aplikacji |
| `device_language` | Device language | Jazyk zařízení | Jazyk zariadenia | Gerätesprache | Język urządzenia |
| `keep_screen_awake` | Keep screen awake | Nevypínat obrazovku | Nevypínať obrazovku | Bildschirm aktiv lassen | Nie wygaszaj ekranu |
| `page_fit` | Page fit | Přizpůsobení stránky | Prispôsobenie strany | Seitenanpassung | Dopasowanie strony |
| `fit_page` | Page | Stránka | Strana | Seite | Strona |
| `fit_width` | Width | Šířka | Šírka | Breite | Szerokość |
| `page_turn_taps` | Page-turn taps | Otáčení klepnutím | Otáčanie ťuknutím | Tippen zum Blättern | Zmiana strony dotknięciem |
| `page_turn_swipes` | Page-turn swipes | Otáčení přejetím | Otáčanie potiahnutím | Wischen zum Blättern | Zmiana strony przesunięciem |
| `auto_hide_controls` | Auto-hide controls | Automaticky skrýt ovládání | Automaticky skryť ovládanie | Bedienelemente automatisch ausblenden | Automatycznie ukrywaj sterowanie |
| `default_tool` | Default tool | Výchozí nástroj | Predvolený nástroj | Standardwerkzeug | Domyślne narzędzie |
| `pen_width` | Pen width | Tloušťka pera | Hrúbka pera | Stiftbreite | Grubość pióra |
| `highlighter_opacity` | Highlighter opacity | Krytí zvýrazňovače | Krytie zvýrazňovača | Textmarker-Deckkraft | Krycie zakreślacza |
| `text_size` | Text size | Velikost textu | Veľkosť textu | Textgröße | Rozmiar tekstu |
| `view` | View | Prohlížení | Prezeranie | Ansicht | Podgląd |
| `small` | Small | Malé | Malé | Klein | Małe |
| `thin` | Thin | Tenké | Tenké | Dünn | Cienkie |
| `medium` | Medium | Střední | Stredné | Mittel | Średnie |
| `thick` | Thick | Silné | Hrubé | Dick | Grube |
| `large` | Large | Velké | Veľké | Groß | Duże |
| `light` | Light | Nízké | Nízke | Leicht | Lekkie |
| `strong` | Strong | Vysoké | Vysoké | Stark | Mocne |
| `app_version` | Version | Verze | Verzia | Version | Wersja |
| `android_requirement` | Requires Android 13 or newer | Vyžaduje Android 13 nebo novější | Vyžaduje Android 13 alebo novší | Erfordert Android 13 oder neuer | Wymaga Androida 13 lub nowszego |
| `privacy_offline` | Offline. No account or data collection. | Offline. Bez účtu a sběru dat. | Offline. Bez účtu a zberu dát. | Offline. Kein Konto und keine Datenerfassung. | Offline. Bez konta i zbierania danych. |
| `license` | License | Licence | Licencia | Lizenz | Licencja |
| `github_repository` | GitHub repository | GitHub repozitář | GitHub repozitár | GitHub-Repository | Repozytorium GitHub |
| `release_page` | Releases | Vydání | Vydania | Releases | Wydania |

Language names remain autonyms: `English`, `Čeština`, `Slovenčina`, `Deutsch`, and `Polski` in every locale.

- [ ] **Step 6: Run resource and settings gates**

```powershell
.\gradlew.bat lintDebug assembleDebug connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=cz.teply.sheetset.SettingsFlowTest' --no-daemon --console=plain
```

- [ ] **Step 7: Commit**

```powershell
git add -- app/src/main/java/cz/teply/sheetset/ui/SettingsDrawer.kt app/src/main/java/cz/teply/sheetset/LibraryUiState.kt app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt app/src/main/java/cz/teply/sheetset/MainActivity.kt app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt app/src/main/res/values/strings.xml app/src/main/res/values-cs/strings.xml app/src/main/res/values-sk/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-pl/strings.xml app/src/androidTest/java/cz/teply/sheetset/SettingsFlowTest.kt
git commit -m "feat(settings): add in-app preferences"
```

### Task 5: Tablet rail and list-detail layout

**Files:**
- Modify: `app/src/main/java/cz/teply/sheetset/ui/AppShell.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/LibraryScreen.kt`
- Modify: `app/src/main/java/cz/teply/sheetset/ui/SetlistScreens.kt`
- Test: `app/src/test/java/cz/teply/sheetset/ui/WindowLayoutTest.kt`
- Test: `app/src/androidTest/java/cz/teply/sheetset/AdaptiveLayoutTest.kt`

**Interfaces:**
- Produces: deterministic compact, medium, and expanded layouts.
- Consumes: `WindowLayout`, `AppDestination`, and existing setlist selection callbacks.

- [ ] **Step 1: Write failing breakpoint tests**

```kotlin
@Test
fun widthBreakpointsAreStable() {
    assertEquals(WindowLayout.COMPACT, WindowLayout.fromWidth(599.dp))
    assertEquals(WindowLayout.MEDIUM, WindowLayout.fromWidth(600.dp))
    assertEquals(WindowLayout.MEDIUM, WindowLayout.fromWidth(839.dp))
    assertEquals(WindowLayout.EXPANDED, WindowLayout.fromWidth(840.dp))
}
```

- [ ] **Step 2: Write failing expanded UI test**

```kotlin
@Test
fun expandedSetlistsShowsRailAndDetailTogether() {
    composeRule.setContent {
        SheetSetTheme {
            SheetSetApp(
                state = populatedState,
                actions = SheetSetActions(),
                windowLayout = WindowLayout.EXPANDED,
            )
        }
    }

    composeRule.onNodeWithContentDescription("PDF navigation").assertIsDisplayed()
    composeRule.onNodeWithText("Setlists").performClick()
    composeRule.onNodeWithText("Show").performClick()
    composeRule.onNodeWithText("Show").assertIsDisplayed()
    composeRule.onNodeWithText("Add PDFs").assertIsDisplayed()
}
```

- [ ] **Step 3: Run RED tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests cz.teply.sheetset.ui.WindowLayoutTest --no-daemon --console=plain
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=cz.teply.sheetset.AdaptiveLayoutTest' --no-daemon --console=plain
```

- [ ] **Step 4: Implement responsive placement**

At the activity root, use `BoxWithConstraints` once and pass `WindowLayout.fromWidth(maxWidth)` into `SheetSetApp`. Do not read screen pixels or orientation elsewhere.

For medium and expanded layouts:

- Place `NavigationRail` at the left.
- Cap library and setlist list content at 720 dp.
- Keep the medium layout single-pane.
- On expanded Setlists, render the setlist list in a 360 dp pane and `SetlistDetail` in the remaining pane with one divider.
- Keep the reader full-screen; the PDF editor plan owns its expanded tool placement.

- [ ] **Step 5: Run all shell and adaptive gates**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon --console=plain
.\gradlew.bat connectedDebugAndroidTest --no-daemon --console=plain
```

- [ ] **Step 6: Commit**

```powershell
git add -- app/src/main/java/cz/teply/sheetset/ui/AppShell.kt app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt app/src/main/java/cz/teply/sheetset/ui/LibraryScreen.kt app/src/main/java/cz/teply/sheetset/ui/SetlistScreens.kt app/src/test/java/cz/teply/sheetset/ui/WindowLayoutTest.kt app/src/androidTest/java/cz/teply/sheetset/AdaptiveLayoutTest.kt
git commit -m "feat(ui): adapt SheetSet for tablets"
```

### Task 6: Settings and shell acceptance gate

**Files:**
- Modify only files required by findings from this plan.

**Interfaces:**
- Produces: a buildable Android 13+ app with a tested settings menu and adaptive shell.
- Consumes: Tasks 1 through 5.

- [ ] **Step 1: Run the complete local gate**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest --no-daemon --console=plain
```

- [ ] **Step 2: Run four live scenarios**

Use UI-tree coordinates, not screenshot guesses:

1. Phone empty library: hamburger opens, closes, and `Import PDF` remains at the upper right.
2. Phone Setlists: `Create` remains at the upper right before and after one setlist exists.
3. Language: switch English to Czech, restart, verify Czech, then select Device language and restart.
4. Tablet: verify rail navigation and expanded Setlists list-detail after rotation.

Capture `.reference/tmp/settings-phone.png` and `.reference/tmp/settings-tablet.png`.

- [ ] **Step 3: Review the diff**

Check state preservation, locale restart behavior, no duplicate actions, all 48 dp targets, no missing translations, no new permissions, no new dependencies, and no unrelated formatting.

- [ ] **Step 4: Commit only review fixes**

```powershell
git add -- app/build.gradle.kts app/src/main/java/cz/teply/sheetset/settings/AppSettings.kt app/src/main/java/cz/teply/sheetset/settings/SettingsStore.kt app/src/main/java/cz/teply/sheetset/settings/AppLanguages.kt app/src/main/java/cz/teply/sheetset/ui/AppShell.kt app/src/main/java/cz/teply/sheetset/ui/SettingsDrawer.kt app/src/main/java/cz/teply/sheetset/ui/SheetSetApp.kt app/src/main/java/cz/teply/sheetset/ui/LibraryScreen.kt app/src/main/java/cz/teply/sheetset/ui/SetlistScreens.kt app/src/main/java/cz/teply/sheetset/LibraryUiState.kt app/src/main/java/cz/teply/sheetset/SheetSetViewModel.kt app/src/main/java/cz/teply/sheetset/MainActivity.kt app/src/main/res/values/strings.xml app/src/main/res/values-cs/strings.xml app/src/main/res/values-sk/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-pl/strings.xml
git commit -m "fix(ui): address settings QA"
```

Skip this commit if review finds no required change.
