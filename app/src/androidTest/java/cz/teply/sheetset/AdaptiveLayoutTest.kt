package cz.teply.sheetset

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cz.teply.sheetset.data.LibraryCatalog
import cz.teply.sheetset.data.Score
import cz.teply.sheetset.data.Setlist
import cz.teply.sheetset.ui.SheetSetActions
import cz.teply.sheetset.ui.SheetSetApp
import cz.teply.sheetset.ui.SheetSetTheme
import cz.teply.sheetset.ui.WindowLayout
import org.junit.Rule
import org.junit.Test

@Suppress("DEPRECATION")
class AdaptiveLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun expandedSetlistsShowsRailAndDetailTogether() {
        val score = Score("score-1", "Song", "score-1.pdf", 2, 1L)
        val setlist = Setlist("set-1", "Show", listOf(score.id))
        val state = LibraryUiState(
            catalog = LibraryCatalog(scores = listOf(score), setlists = listOf(setlist)),
        )
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.WindowSize(DpSize(1000.dp, 800.dp)),
            ) {
                SheetSetTheme {
                    SheetSetApp(
                        state = state,
                        actions = SheetSetActions(),
                        windowLayout = WindowLayout.EXPANDED,
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("PDF navigation").assertIsDisplayed()
        composeRule.onNodeWithText("Setlists").performClick()
        composeRule.onNodeWithText("Show").performClick()

        composeRule.onAllNodesWithText("Show").assertCountEquals(2)
        composeRule.onNodeWithText("Add PDFs").assertIsDisplayed()
    }
}
