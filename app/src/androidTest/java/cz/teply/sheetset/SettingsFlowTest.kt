package cz.teply.sheetset

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import cz.teply.sheetset.settings.AppSettings
import cz.teply.sheetset.settings.ReaderLayout
import cz.teply.sheetset.ui.SheetSetActions
import cz.teply.sheetset.ui.SheetSetApp
import cz.teply.sheetset.ui.SheetSetTheme
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
    fun menuChangesSettingsAndReachesEverySection() {
        var settings by mutableStateOf(AppSettings())
        var language: String? = null
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    state = LibraryUiState(settings = settings),
                    actions = SheetSetActions(
                        updateSettings = { settings = it },
                        selectLanguage = { language = it },
                    ),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Menu").performClick()
        listOf(
            "Language",
            "Reader",
            "Annotation defaults",
            "Backup",
            "Share backup",
            "Restore backup",
            "App details",
        ).forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }

        composeRule.onNodeWithText("Reader").performClick()
        composeRule.onNodeWithText("Keep screen awake").performClick()
        composeRule.runOnIdle { assertFalse(settings.keepScreenAwake) }
        composeRule.onNodeWithText("Page layout").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Half page").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(ReaderLayout.HALF, settings.readerLayout) }

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Language").performClick()
        composeRule.onNodeWithText("Čeština").performClick()
        composeRule.runOnIdle { assertEquals("cs", language) }

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Annotation defaults").performClick()
        composeRule.onNodeWithText("Pen width").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("App details").performClick()
        composeRule.onNodeWithText("Version").assertIsDisplayed()
        composeRule.onNodeWithText("Privacy policy").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Support this app → Buy Me a Coffee")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun shareBackupUsesDedicatedAction() {
        var shared = false
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    state = LibraryUiState(),
                    actions = SheetSetActions(shareBackup = { shared = true }),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Share backup").performScrollTo().performClick()

        composeRule.runOnIdle { assertTrue(shared) }
    }

    @Test
    fun systemBackClosesSettingsDrawer() {
        composeRule.setContent {
            SheetSetTheme { SheetSetApp(LibraryUiState(), SheetSetActions()) }
        }

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Backup").assertIsDisplayed()
        Espresso.pressBack()

        composeRule.onAllNodesWithText("Backup").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Menu").assertIsDisplayed()
    }

    @Test
    fun systemBackFromSettingsPageReturnsToMenu() {
        composeRule.setContent {
            SheetSetTheme { SheetSetApp(LibraryUiState(), SheetSetActions()) }
        }

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Reader").performClick()
        composeRule.onNodeWithText("Keep screen awake").assertIsDisplayed()
        Espresso.pressBack()

        composeRule.onNodeWithText("Language").assertIsDisplayed()
        composeRule.onAllNodesWithText("Keep screen awake").assertCountEquals(0)
    }
}
