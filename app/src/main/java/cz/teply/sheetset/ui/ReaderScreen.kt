package cz.teply.sheetset.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cz.teply.sheetset.R
import cz.teply.sheetset.ReaderUiState
import cz.teply.sheetset.pdf.AnnotationHistory
import cz.teply.sheetset.pdf.PdfPageView
import cz.teply.sheetset.pdf.ReaderTool

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(reader: ReaderUiState, actions: SheetSetActions) {
    var tool by remember { mutableStateOf(ReaderTool.VIEW) }
    var history by remember(reader.score.id, reader.pageIndex) {
        mutableStateOf(AnnotationHistory(reader.annotations.pages[reader.pageIndex].orEmpty()))
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> uri?.let(actions.exportPdf) }
    fun updateHistory(next: AnnotationHistory) {
        history = next
        actions.saveStrokes(next.strokes)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(reader.score.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    TextButton(onClick = actions.closeReader) {
                        Text(stringResource(R.string.close))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            exportLauncher.launch(
                                reader.score.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                    .take(100) + "-annotated.pdf",
                            )
                        },
                    ) { Text(stringResource(R.string.export)) }
                },
            )
        },
        bottomBar = {
            Surface {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (tool == ReaderTool.VIEW) {
                        TextButton(onClick = actions.previousPage) {
                            Text(stringResource(R.string.previous))
                        }
                        Text(
                            stringResource(
                                R.string.page_position,
                                reader.pageIndex + 1,
                                reader.score.pageCount,
                            ),
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                        TextButton(onClick = actions.nextPage) {
                            Text(stringResource(R.string.next))
                        }
                        TextButton(onClick = { tool = ReaderTool.PEN }) {
                            Text(stringResource(R.string.annotate))
                        }
                    } else {
                        TextButton(onClick = { tool = ReaderTool.PEN }) {
                            Text(stringResource(R.string.pen))
                        }
                        TextButton(onClick = { tool = ReaderTool.HIGHLIGHTER }) {
                            Text(stringResource(R.string.highlighter))
                        }
                        TextButton(onClick = { tool = ReaderTool.ERASER }) {
                            Text(stringResource(R.string.eraser))
                        }
                        TextButton(onClick = { updateHistory(history.undo()) }) {
                            Text(stringResource(R.string.undo))
                        }
                        TextButton(onClick = { updateHistory(history.redo()) }) {
                            Text(stringResource(R.string.redo))
                        }
                        TextButton(onClick = { tool = ReaderTool.VIEW }) {
                            Text(stringResource(R.string.done))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context -> PdfPageView(context) },
                update = { view ->
                    view.contentDescription = view.context.getString(
                        R.string.pdf_page,
                        reader.pageIndex + 1,
                        reader.score.pageCount,
                    )
                    view.tool = tool
                    view.strokes = history.strokes
                    view.onPreviousPage = actions.previousPage
                    view.onNextPage = actions.nextPage
                    view.onAddStroke = { updateHistory(history.add(it)) }
                    view.onErase = { point -> updateHistory(history.erase(point, 0.025f)) }
                    view.showPage(reader.file, reader.pageIndex)
                },
            )
        }
    }
}
