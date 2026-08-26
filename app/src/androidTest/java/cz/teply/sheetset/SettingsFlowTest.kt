package cz.teply.sheetset

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.espresso.Espresso
import cz.teply.sheetset.pdf.AnnotationEditorSettings
import cz.teply.sheetset.settings.AppSettings
import cz.teply.sheetset.settings.ReaderLayout
import cz.teply.sheetset.settings.ThemeMode
import cz.teply.sheetset.ui.SheetSetActions
import cz.teply.sheetset.ui.SheetSetApp
import cz.teply.sheetset.ui.SheetSetTheme
import cz.teply.sheetset.ui.AppDestination
import cz.teply.sheetset.ui.SettingsDrawer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@Suppress("DEPRECATION")
class SettingsFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun menuGroupsReadingDataAndAppSettings() {
        setSettingsContent()

        composeRule.onNodeWithContentDescription("Menu").performClick()

        listOf("Library", "Reading", "Data", "App").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        listOf(
            "Reader and page layout",
            "Gestures",
            "Annotation tools",
            "Backup and restore",
        ).forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        composeRule.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Share backup").assertCountEquals(0)
    }

    @Test
    fun appearanceChoiceUpdatesThemeMode() {
        var settings by mutableStateOf(AppSettings())
        setSettingsContent(
            settings = settings,
            actions = SheetSetActions(updateSettings = { settings = it }),
        )

        openMenuPage("Appearance")
        composeRule.onNodeWithText("Dark").performClick()

        composeRule.runOnIdle { assertEquals(ThemeMode.DARK, settings.themeMode) }
    }

    @Test
    fun readerChoicesShowCurrentValueAndOpenOneDialog() {
        setSettingsContent(settings = AppSettings(readerLayout = ReaderLayout.HALF))
        openMenuPage("Reader and page layout")

        composeRule.onNodeWithText("Half page").assertIsDisplayed().performClick()

        composeRule.onNodeWithText("Single page").assertIsDisplayed()
        composeRule.onAllNodesWithText("Half page").assertCountEquals(2)
        composeRule.onNodeWithText("Two pages").assertIsDisplayed()
        composeRule.onAllNodesWithText("Width").assertCountEquals(0)
    }

    @Test
    fun readerAndGestureRowsUpdateStateDirectly() {
        var settings by mutableStateOf(AppSettings())
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(settings = settings),
                    SheetSetActions(updateSettings = { settings = it }),
                )
            }
        }

        openMenuPage("Reader and page layout")
        scrollToText("Keep screen awake")
        composeRule.onNodeWithText("Keep screen awake").performClick()
        composeRule.runOnIdle { assertFalse(settings.keepScreenAwake) }
        composeRule.onNodeWithText("Single page").performClick()
        composeRule.onNodeWithText("Two pages").performClick()
        composeRule.runOnIdle { assertEquals(ReaderLayout.TWO_PAGE, settings.readerLayout) }

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Gestures").performClick()
        composeRule.onNodeWithText("Page-turn taps").performClick()
        scrollToText("Palm rejection")
        composeRule.onNodeWithText("Palm rejection").performClick()
        composeRule.runOnIdle {
            assertFalse(settings.pageTurnTaps)
            assertTrue(settings.editor.palmRejection)
        }
    }

    @Test
    fun backupActionsCallDistinctCallbacks() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            SheetSetTheme {
                SettingsDrawer(
                    drawerState = rememberDrawerState(DrawerValue.Open),
                    destination = AppDestination.PDF,
                    settings = AppSettings(),
                    onDestination = {},
                    onSettings = {},
                    onLanguage = {},
                    onBackup = { calls += "create" },
                    onShareBackup = { calls += "share" },
                    onRestore = { calls += "restore" },
                ) {}
            }
        }
        composeRule.onNodeWithText("Backup and restore").performScrollTo().performClick()

        composeRule.onNodeWithText("Create backup").performClick()
        composeRule.onNodeWithText("Share backup").performClick()
        composeRule.onNodeWithText("Restore backup").performClick()

        composeRule.runOnIdle { assertEquals(listOf("create", "share", "restore"), calls) }
    }

    @Test
    fun annotationVisibilityRejectsEmptyGroupsAndKeepsHiddenToolsRestorable() {
        val defaults = AnnotationEditorSettings.defaults()
        var settings by mutableStateOf(
            AppSettings(
                editor = defaults.copy(
                    presets = defaults.presets.map { it.copy(visible = it.id == "pen-1") },
                    visibleObjectTools = setOf("select"),
                ),
            ),
        )
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(settings = settings),
                    SheetSetActions(updateSettings = { settings = it }),
                )
            }
        }
        openMenuPage("Annotation tools")

        composeRule.onNodeWithTag("visibility-pen-1").performClick()
        composeRule.onNodeWithText("Keep at least one drawing preset visible.").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        scrollToTag("visibility-select")
        composeRule.onNodeWithTag("visibility-select").performClick()
        composeRule.onNodeWithText("Keep at least one object tool visible.").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        scrollToTag("visibility-lasso")
        composeRule.onNodeWithTag("visibility-lasso").performClick()

        composeRule.runOnIdle {
            assertEquals(setOf("select", "lasso"), settings.editor.visibleObjectTools)
            assertTrue(settings.editor.preset("pen-1").visible)
        }
    }

    @Test
    fun annotationOrderAccessibilityActionPersistsMove() {
        var settings by mutableStateOf(AppSettings())
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(settings = settings),
                    SheetSetActions(updateSettings = { settings = it }),
                )
            }
        }
        openMenuPage("Annotation tools")
        scrollToTag("reorder-row-pen-1")

        val moveDown = composeRule.onNodeWithTag("reorder-pen-1", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label == "Move down" }
        composeRule.runOnIdle { assertTrue(moveDown.action()) }

        composeRule.runOnIdle {
            assertEquals(listOf("pen-2", "pen-1", "marker", "highlighter"), settings.editor.drawOrder)
        }
    }

    @Test
    fun languagePageCallsLanguageCallback() {
        var language: String? = "unchanged"
        setSettingsContent(actions = SheetSetActions(selectLanguage = { language = it }))
        openMenuPage("Language")

        composeRule.onNodeWithText("Čeština").performClick()

        composeRule.runOnIdle { assertEquals("cs", language) }
    }

    @Test
    fun appDetailsKeepsSupportAction() {
        setSettingsContent()
        openMenuPage("App details")

        scrollToText("Version")
        composeRule.onNodeWithText("Version").assertIsDisplayed()
        scrollToText("Privacy policy")
        composeRule.onNodeWithText("Privacy policy").assertIsDisplayed()
        scrollToText("Support this app → Buy Me a Coffee")
        composeRule.onNodeWithText("Support this app → Buy Me a Coffee")
            .assertIsDisplayed()
    }

    @Test
    fun backFromPageReturnsToGroupedDrawerThenClosesIt() {
        setSettingsContent()
        openMenuPage("Reader and page layout")
        scrollToText("Keep screen awake")
        composeRule.onNodeWithText("Keep screen awake").assertIsDisplayed()

        Espresso.pressBack()

        composeRule.onNodeWithText("Library").assertIsDisplayed()
        composeRule.onAllNodesWithText("Keep screen awake").assertCountEquals(0)

        Espresso.pressBack()

        composeRule.onAllNodesWithText("Library").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Menu").assertIsDisplayed()
    }

    private fun setSettingsContent(
        settings: AppSettings = AppSettings(),
        actions: SheetSetActions = SheetSetActions(),
    ) {
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(LibraryUiState(settings = settings), actions)
            }
        }
    }

    private fun openMenuPage(name: String) {
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText(name).performScrollTo().performClick()
        composeRule.waitForIdle()
        val backCount = composeRule.onAllNodesWithContentDescription(
            "Back",
            useUnmergedTree = true,
        ).fetchSemanticsNodes().size
        val listCount = composeRule.onAllNodes(
            hasTestTag("settings-list"),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().size
        assertTrue("backCount=$backCount listCount=$listCount", backCount > 0 && listCount > 0)
    }

    private fun scrollToText(text: String) {
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasText(text))
    }

    private fun scrollToTag(tag: String) {
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag(tag))
    }
}
