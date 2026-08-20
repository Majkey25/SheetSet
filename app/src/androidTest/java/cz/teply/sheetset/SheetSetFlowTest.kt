package cz.teply.sheetset

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cz.teply.sheetset.data.LibraryCatalog
import cz.teply.sheetset.data.Score
import cz.teply.sheetset.data.Setlist
import cz.teply.sheetset.ui.SheetSetActions
import cz.teply.sheetset.ui.SheetSetApp
import cz.teply.sheetset.ui.SheetSetTheme
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("DEPRECATION")
class SheetSetFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shellShowsOnlyPdfAndSetlistTabs() {
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(LibraryUiState(), SheetSetActions())
            }
        }

        composeRule.onNodeWithText("PDF").assertIsDisplayed()
        composeRule.onNodeWithText("Setlists").performClick()
        composeRule.onNodeWithText("No setlists yet").assertIsDisplayed()
    }

    @Test
    fun moreThanThreeSetlistsCanBeCreated() {
        var state by mutableStateOf(LibraryUiState())
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    state = state,
                    actions = SheetSetActions(
                        createSetlist = { name ->
                            val number = state.catalog.setlists.size + 1
                            state = state.copy(
                                catalog = state.catalog.createSetlist(name, "set-$number"),
                            )
                        },
                    ),
                )
            }
        }
        composeRule.onNodeWithText("Setlists").performClick()

        repeat(4) { index ->
            composeRule.onNodeWithContentDescription("New setlist").performClick()
            composeRule.onNodeWithText("Setlist name").performTextInput("Set ${index + 1}")
            composeRule.onNodeWithText("Save").performClick()
        }

        composeRule.onNodeWithText("Set 4").assertIsDisplayed()
    }

    @Test
    fun pdfRowOpensReaderAction() {
        var opened = false
        val score = Score("score-1", "Song", "score-1.pdf", 2, 1L)
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(catalog = LibraryCatalog(scores = listOf(score))),
                    SheetSetActions(openScore = { opened = true }),
                )
            }
        }

        composeRule.onNodeWithText("Song").performClick()

        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun pdfCanBeAddedInsideSetlistEditor() {
        val score = Score("score-1", "Song", "score-1.pdf", 2, 1L)
        var state by mutableStateOf(
            LibraryUiState(
                catalog = LibraryCatalog(
                    scores = listOf(score),
                    setlists = listOf(Setlist("set-1", "Show")),
                ),
            ),
        )
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    state,
                    SheetSetActions(
                        addScores = { setlistId, scoreIds ->
                            state = state.copy(
                                catalog = scoreIds.fold(state.catalog) { catalog, scoreId ->
                                    catalog.addScoreToSetlist(setlistId, scoreId)
                                },
                            )
                        },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Setlists").performClick()
        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithText("Add PDFs").performClick()
        composeRule.onNodeWithText("Song").performClick()
        composeRule.onNodeWithText("Add").performClick()

        composeRule.onNodeWithText("Song").assertIsDisplayed()
    }
}
