package cz.teply.sheetset

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cz.teply.sheetset.settings.AppSettings
import cz.teply.sheetset.ui.SheetSetActions
import cz.teply.sheetset.ui.SheetSetApp
import cz.teply.sheetset.ui.SheetSetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            "Restore backup",
            "About",
        ).forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }

        composeRule.onNodeWithText("Reader").performClick()
        composeRule.onNodeWithText("Keep screen awake").performClick()
        composeRule.runOnIdle { assertFalse(settings.keepScreenAwake) }

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Language").performClick()
        composeRule.onNodeWithText("Čeština").performClick()
        composeRule.runOnIdle { assertEquals("cs", language) }

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Annotation defaults").performClick()
        composeRule.onNodeWithText("Pen width").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("About").performClick()
        composeRule.onNodeWithText("Version").assertIsDisplayed()
    }
}
