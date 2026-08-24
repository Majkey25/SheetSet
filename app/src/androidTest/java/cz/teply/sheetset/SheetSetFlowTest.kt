package cz.teply.sheetset

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
import cz.teply.sheetset.data.LibraryCatalog
import cz.teply.sheetset.data.Score
import cz.teply.sheetset.data.Setlist
import cz.teply.sheetset.pdf.DocumentAnnotations
import cz.teply.sheetset.settings.AppSettings
import cz.teply.sheetset.settings.ReaderLayout
import cz.teply.sheetset.ui.SheetSetActions
import cz.teply.sheetset.ui.SheetSetApp
import cz.teply.sheetset.ui.SheetSetTheme
import cz.teply.sheetset.ui.AutoScrollState
import cz.teply.sheetset.ui.PerformanceToolsSheet
import cz.teply.sheetset.ui.WindowLayout
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
    fun tabsExposeSelectedState() {
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(LibraryUiState(), SheetSetActions())
            }
        }

        composeRule.onNodeWithText("PDF").assertIsSelected()
        composeRule.onNodeWithText("Setlists").assertIsNotSelected().performClick()
        composeRule.onNodeWithText("PDF").assertIsNotSelected()
        composeRule.onNodeWithText("Setlists").assertIsSelected()
    }

    @Test
    fun emptyLibraryShowsVisibleImportAction() {
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(LibraryUiState(), SheetSetActions())
            }
        }

        composeRule.onNodeWithText("Import PDF").assertIsDisplayed()
    }

    @Test
    fun headerUsesMenuAndPdfActionWithoutBrandTitle() {
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(LibraryUiState(), SheetSetActions())
            }
        }

        composeRule.onNodeWithContentDescription("Menu").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Import PDF").assertIsDisplayed()
        composeRule.onAllNodesWithText("SheetSet").assertCountEquals(0)
        composeRule.onAllNodesWithText("Import PDF").assertCountEquals(1)
    }

    @Test
    fun emptySetlistsKeepsCreateOnlyInHeader() {
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(LibraryUiState(), SheetSetActions())
            }
        }

        composeRule.onNodeWithText("Setlists").performClick()

        composeRule.onNodeWithContentDescription("Create").assertIsDisplayed()
        composeRule.onAllNodesWithText("New setlist").assertCountEquals(0)
    }

    @Test
    fun setlistSearchMatchesLabels() {
        val setlist = Setlist("set-1", "Show", labels = listOf("Saturday"))
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(catalog = LibraryCatalog(setlists = listOf(setlist))),
                    SheetSetActions(),
                )
            }
        }

        composeRule.onNodeWithText("Setlists").performClick()
        composeRule.onNodeWithContentDescription("Search setlists").performClick()
        composeRule.onNodeWithText("Search setlists").performTextInput("saturday")

        composeRule.onNodeWithText("Show").assertIsDisplayed()
    }

    @Test
    fun searchOpensOnlyWhenRequested() {
        val score = Score("score-1", "Moonlight Sonata", "score-1.pdf", 3, 1L)
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(catalog = LibraryCatalog(scores = listOf(score))),
                    SheetSetActions(),
                )
            }
        }

        composeRule.onAllNodesWithText("Search PDFs").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Search PDFs").performClick()
        composeRule.onNodeWithText("Search PDFs").assertIsDisplayed()
        composeRule.onNodeWithText("Search PDFs").performTextInput("missing")
        composeRule.onAllNodesWithText("Moonlight Sonata").assertCountEquals(0)
    }

    @Test
    fun libraryAvoidsDuplicateSectionHeading() {
        val score = Score("score-1", "Song", "score-1.pdf", 2, 1L)
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(catalog = LibraryCatalog(scores = listOf(score))),
                    SheetSetActions(),
                )
            }
        }

        composeRule.onAllNodesWithText("PDF").assertCountEquals(1)
    }

    @Test
    fun searchClosesWhenLeavingLibrary() {
        val score = Score("score-1", "Song", "score-1.pdf", 2, 1L)
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(catalog = LibraryCatalog(scores = listOf(score))),
                    SheetSetActions(),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Search PDFs").performClick()
        composeRule.onNodeWithText("Search PDFs").assertIsDisplayed()
        composeRule.onNodeWithText("Setlists").performClick()
        composeRule.onNodeWithText("PDF").performClick()

        composeRule.onAllNodesWithText("Search PDFs").assertCountEquals(0)
    }

    @Test
    fun bookmarkSearchOpensExactPage() {
        var openedPage = -1
        val score = Score(
            "score-1",
            "Song",
            "score-1.pdf",
            4,
            1L,
            bookmarks = listOf(cz.teply.sheetset.data.Bookmark("chorus", "Chorus", 2)),
        )
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(catalog = LibraryCatalog(scores = listOf(score))),
                    SheetSetActions(openScoreAt = { _, page -> openedPage = page }),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Search PDFs").performClick()
        composeRule.onNodeWithText("Search PDFs").performTextInput("chorus")
        composeRule.onNodeWithText("Chorus").performClick()

        composeRule.runOnIdle { assertEquals(2, openedPage) }
    }

    @Test
    fun scoreLabelsCanBeEditedFromRowMenu() {
        var state by mutableStateOf(
            LibraryUiState(
                catalog = LibraryCatalog(
                    scores = listOf(Score("score-1", "Song", "score-1.pdf", 2, 1L)),
                ),
            ),
        )
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    state,
                    SheetSetActions(
                        updateScoreLabels = { id, labels ->
                            state = state.copy(catalog = state.catalog.updateScoreLabels(id, labels))
                        },
                    ),
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("More options")[0].performClick()
        composeRule.onNodeWithText("Labels").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Band, Encore")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.runOnIdle { assertEquals(listOf("Band", "Encore"), state.catalog.scores.single().labels) }
        composeRule.onNodeWithText("Band · Encore").assertIsDisplayed()
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
            composeRule.onNodeWithContentDescription("Create").performClick()
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

    @Test
    fun setlistEditingControlsStayOutOfBrowseMode() {
        val first = Score("score-1", "First song", "score-1.pdf", 2, 1L)
        val second = Score("score-2", "Second song", "score-2.pdf", 2, 2L)
        val setlist = Setlist("set-1", "Show", listOf(first.id, second.id))
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(
                        catalog = LibraryCatalog(
                            scores = listOf(first, second),
                            setlists = listOf(setlist),
                        ),
                    ),
                    SheetSetActions(),
                )
            }
        }

        composeRule.onNodeWithText("Setlists").performClick()
        composeRule.onNodeWithText("Show").performClick()
        composeRule.onAllNodesWithContentDescription("Remove").assertCountEquals(0)
        composeRule.onNodeWithText("Edit order").performClick()
        composeRule.onAllNodesWithContentDescription("Reorder").assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription("Move up").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Move down").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Remove").assertCountEquals(2)
    }

    @Test
    fun setlistEditorUsesIconsInsteadOfTextGlyphs() {
        val first = Score("score-1", "First song", "score-1.pdf", 2, 1L)
        val second = Score("score-2", "Second song", "score-2.pdf", 2, 2L)
        val setlist = Setlist("set-1", "Show", listOf(first.id, second.id))
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(
                        catalog = LibraryCatalog(
                            scores = listOf(first, second),
                            setlists = listOf(setlist),
                        ),
                    ),
                    SheetSetActions(),
                )
            }
        }

        composeRule.onNodeWithText("Setlists").performClick()
        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithText("Edit order").performClick()

        listOf("‹", "↑", "↓", "×").forEach { glyph ->
            composeRule.onAllNodesWithText(glyph).assertCountEquals(0)
        }
    }

    @Test
    fun readerOpensAnnotationTools() {
        val score = Score("score-1", "Song", "score-1.pdf", 2, 1L)
        val reader = ReaderUiState(
            score = score,
            file = File("missing.pdf"),
            scoreIds = listOf(score.id),
            scoreIndex = 0,
            pageIndex = 0,
            annotations = DocumentAnnotations(),
        )
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(
                        catalog = LibraryCatalog(scores = listOf(score)),
                        reader = reader,
                    ),
                    SheetSetActions(),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Close").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Export").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Annotate").performClick()

        listOf(
            "Draw",
            "Add tools",
            "Straight line",
            "Black",
            "Red",
            "Orange",
            "Yellow",
            "Green",
            "Blue",
            "Purple",
            "Pink",
            "Select",
            "Underline",
            "Strike-through",
            "Text box",
            "Line",
            "Arrow",
            "Rectangle",
            "Ellipse",
            "Undo",
            "Redo",
            "Done",
        ).forEach { label ->
            composeRule.onAllNodesWithContentDescription(label).assertCountEquals(1)
        }
        composeRule.onNodeWithContentDescription("Select").assertIsSelected()
        composeRule.onNodeWithContentDescription("Rectangle").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Rectangle").assertIsSelected()
        composeRule.onNodeWithContentDescription("Draw").performClick()
        listOf("Pen", "Highlighter", "Eraser").forEach { label ->
            composeRule.onNodeWithContentDescription(label).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("Pen").performClick()
        composeRule.onNodeWithContentDescription("Pen").assertIsSelected()
        composeRule.onNodeWithContentDescription("Straight line").assertIsNotSelected()
        composeRule.onNodeWithContentDescription("Straight line").performClick()
        composeRule.onNodeWithContentDescription("Straight line").assertIsSelected()
    }

    @Test
    fun performanceToolsChangeLayoutAndJumpToBookmark() {
        var settings by mutableStateOf(AppSettings())
        var jumpedTo = -1
        val score = Score(
            "score-1",
            "Song",
            "score-1.pdf",
            4,
            1L,
            bookmarks = listOf(cz.teply.sheetset.data.Bookmark("chorus", "Chorus", 2)),
        )
        val reader = ReaderUiState(
            score = score,
            file = File("missing.pdf"),
            scoreIds = listOf(score.id),
            scoreIndex = 0,
            pageIndex = 0,
            annotations = DocumentAnnotations(),
        )
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(
                        catalog = LibraryCatalog(scores = listOf(score)),
                        reader = reader,
                        settings = settings,
                    ),
                    SheetSetActions(
                        updateSettings = { settings = it },
                        jumpToPage = { jumpedTo = it },
                    ),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Performance tools").performClick()
        composeRule.onNodeWithText("Single page").assertIsDisplayed()
        composeRule.onNodeWithText("Half page").performClick()
        composeRule.runOnIdle { assertEquals(ReaderLayout.HALF, settings.readerLayout) }
        composeRule.onNodeWithText("Bookmarks").assertIsDisplayed()
        composeRule.onNodeWithText("Chorus").performClick()
        composeRule.runOnIdle { assertEquals(2, jumpedTo) }
        composeRule.onAllNodesWithText("Two pages").assertCountEquals(0)
    }

    @Test
    fun bookmarkCanBeAddedFromCurrentPage() {
        var title = ""
        val score = Score("score-1", "Song", "score-1.pdf", 4, 1L)
        val reader = ReaderUiState(
            score = score,
            file = File("missing.pdf"),
            scoreIds = listOf(score.id),
            scoreIndex = 0,
            pageIndex = 1,
            annotations = DocumentAnnotations(),
        )
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(catalog = LibraryCatalog(scores = listOf(score)), reader = reader),
                    SheetSetActions(addBookmark = { title = it }),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Performance tools").performClick()
        composeRule.onNodeWithText("Add bookmark").performClick()
        composeRule.onNodeWithText("Bookmark title").performTextInput("Verse")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.runOnIdle { assertEquals("Verse", title) }
    }

    @Test
    fun automaticScrollingCanStartPauseAndStop() {
        var autoScrollState by mutableStateOf(AutoScrollState.STOPPED)
        val score = Score("score-1", "Song", "score-1.pdf", 4, 1L)
        val reader = ReaderUiState(
            score = score,
            file = File("missing.pdf"),
            scoreIds = listOf(score.id),
            scoreIndex = 0,
            pageIndex = 0,
            annotations = DocumentAnnotations(),
        )
        composeRule.setContent {
            SheetSetTheme {
                PerformanceToolsSheet(
                    reader = reader,
                    settings = AppSettings(),
                    windowLayout = WindowLayout.COMPACT,
                    autoScrollState = autoScrollState,
                    onSettings = {},
                    onAutoScrollState = { autoScrollState = it },
                    onJump = {},
                    onAddBookmark = {},
                    onRenameBookmark = { _, _ -> },
                    onDeleteBookmark = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Automatic scrolling").assertIsDisplayed()
        composeRule.onNodeWithText("Start").performClick()
        composeRule.onNodeWithText("Pause").performClick()
        composeRule.onNodeWithText("Resume").assertIsDisplayed()
        composeRule.onNodeWithText("Stop").performClick()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun readerUsesIconsInsteadOfTextGlyphs() {
        val score = Score("score-1", "Song", "score-1.pdf", 2, 1L)
        val reader = ReaderUiState(
            score = score,
            file = File("missing.pdf"),
            scoreIds = listOf(score.id),
            scoreIndex = 0,
            pageIndex = 0,
            annotations = DocumentAnnotations(),
        )
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(
                        catalog = LibraryCatalog(scores = listOf(score)),
                        reader = reader,
                    ),
                    SheetSetActions(),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Annotate").performClick()

        listOf("×", "⇩", "‹", "›", "✎", "▰", "⌫", "↶", "↷", "✓").forEach { glyph ->
            composeRule.onAllNodesWithText(glyph).assertCountEquals(0)
        }
    }

    @Test
    fun systemBackClosesReader() {
        var closed = false
        val score = Score("score-1", "Song", "score-1.pdf", 2, 1L)
        val reader = ReaderUiState(
            score = score,
            file = File("missing.pdf"),
            scoreIds = listOf(score.id),
            scoreIndex = 0,
            pageIndex = 0,
            annotations = DocumentAnnotations(),
        )
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(
                        catalog = LibraryCatalog(scores = listOf(score)),
                        reader = reader,
                    ),
                    SheetSetActions(closeReader = { closed = true }),
                )
            }
        }

        Espresso.pressBack()

        composeRule.runOnIdle { assertTrue(closed) }
    }

    @Test
    fun readerPageTapTogglesControls() {
        val score = Score("score-1", "Song", "score-1.pdf", 2, 1L)
        val reader = ReaderUiState(
            score = score,
            file = File("missing.pdf"),
            scoreIds = listOf(score.id),
            scoreIndex = 0,
            pageIndex = 0,
            annotations = DocumentAnnotations(),
        )
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(
                        catalog = LibraryCatalog(scores = listOf(score)),
                        reader = reader,
                    ),
                    SheetSetActions(),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Annotate").assertIsDisplayed()
        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.onAllNodesWithContentDescription("Annotate").assertCountEquals(0)
        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.onNodeWithContentDescription("Annotate").assertIsDisplayed()
    }

    @Test
    fun readerAutoHidesControlsAfterManualReveal() {
        val score = Score("score-1", "Song", "score-1.pdf", 2, 1L)
        val reader = ReaderUiState(
            score = score,
            file = File("missing.pdf"),
            scoreIds = listOf(score.id),
            scoreIndex = 0,
            pageIndex = 0,
            annotations = DocumentAnnotations(),
        )
        composeRule.setContent {
            SheetSetTheme {
                SheetSetApp(
                    LibraryUiState(
                        catalog = LibraryCatalog(scores = listOf(score)),
                        reader = reader,
                    ),
                    SheetSetActions(),
                )
            }
        }

        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.onNodeWithContentDescription("Annotate").assertIsDisplayed()

        composeRule.waitUntil(timeoutMillis = 4_000) {
            composeRule.onAllNodesWithContentDescription("Annotate")
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun cancelledSetlistNameIsCleared() {
        composeRule.setContent {
            SheetSetTheme { SheetSetApp(LibraryUiState(), SheetSetActions()) }
        }
        composeRule.onNodeWithText("Setlists").performClick()
        composeRule.onNodeWithContentDescription("Create").performClick()
        composeRule.onNodeWithText("Setlist name").performTextInput("Old name")
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithContentDescription("Create").performClick()

        composeRule.onAllNodesWithText("Old name").assertCountEquals(0)
    }
}
