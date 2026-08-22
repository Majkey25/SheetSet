package cz.teply.sheetset.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cz.teply.sheetset.R
import cz.teply.sheetset.ReaderUiState
import cz.teply.sheetset.pdf.AnnotationHistory
import cz.teply.sheetset.pdf.PdfPageView
import cz.teply.sheetset.pdf.ReaderTool

@Composable
fun ReaderScreen(reader: ReaderUiState, actions: SheetSetActions) {
    var tool by remember { mutableStateOf(ReaderTool.VIEW) }
    var controlsVisible by remember { mutableStateOf(true) }
    var history by remember(reader.score.id, reader.pageIndex) {
        mutableStateOf(AnnotationHistory(reader.annotations.pages[reader.pageIndex].orEmpty()))
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> uri?.let(actions.exportPdf) }
    fun updateHistory(next: AnnotationHistory) {
        history = next
        actions.saveAnnotations(next.annotations)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                view.annotations = history.annotations
                view.onPreviousPage = actions.previousPage
                view.onNextPage = actions.nextPage
                view.onPageClick = {
                    if (tool == ReaderTool.VIEW) controlsVisible = !controlsVisible
                }
                view.onAddAnnotation = { updateHistory(history.add(it)) }
                view.onErase = { point -> updateHistory(history.erase(point, 0.025f)) }
                view.showPage(reader.file, reader.pageIndex)
            },
        )
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(140)),
        ) {
            ReaderTopBar(
                title = reader.score.title,
                onClose = actions.closeReader,
                onExport = {
                    exportLauncher.launch(
                        reader.score.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            .take(100) + "-annotated.pdf",
                    )
                },
            )
        }
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(140)),
        ) {
            if (tool == ReaderTool.VIEW) {
                ReaderNavigationBar(reader, actions) {
                    tool = ReaderTool.PEN
                    controlsVisible = true
                }
            } else {
                AnnotationBar(
                    tool = tool,
                    onTool = { tool = it },
                    onUndo = { updateHistory(history.undo()) },
                    onRedo = { updateHistory(history.redo()) },
                    onDone = {
                        tool = ReaderTool.VIEW
                        controlsVisible = true
                    },
                )
            }
        }
    }
}

@Composable
private fun ReaderTopBar(title: String, onClose: () -> Unit, onExport: () -> Unit) {
    Surface(color = Color.Black.copy(alpha = 0.9f), contentColor = Color.White) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderControl(stringResource(R.string.close), R.drawable.ic_close_24, onClick = onClose)
            Text(
                title,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ReaderControl(stringResource(R.string.export), R.drawable.ic_download_24, onClick = onExport)
        }
    }
}

@Composable
private fun ReaderNavigationBar(
    reader: ReaderUiState,
    actions: SheetSetActions,
    onAnnotate: () -> Unit,
) {
    val previousEnabled = reader.pageIndex > 0 || reader.scoreIndex > 0
    val nextEnabled = reader.pageIndex < reader.score.pageCount - 1 ||
        reader.scoreIndex < reader.scoreIds.lastIndex
    Surface(color = Color.Black.copy(alpha = 0.9f), contentColor = Color.White) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderControl(
                stringResource(R.string.previous),
                R.drawable.ic_chevron_left_24,
                enabled = previousEnabled,
                onClick = actions.previousPage,
            )
            Text(
                stringResource(
                    R.string.page_position,
                    reader.pageIndex + 1,
                    reader.score.pageCount,
                ),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            ReaderControl(
                stringResource(R.string.next),
                R.drawable.ic_chevron_right_24,
                enabled = nextEnabled,
                onClick = actions.nextPage,
            )
            ReaderControl(stringResource(R.string.annotate), R.drawable.ic_edit_24, onClick = onAnnotate)
        }
    }
}

@Composable
private fun AnnotationBar(
    tool: ReaderTool,
    onTool: (ReaderTool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(color = Color.Black.copy(alpha = 0.9f), contentColor = Color.White) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(60.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderControl(
                stringResource(R.string.pen),
                R.drawable.ic_edit_24,
                selected = tool == ReaderTool.PEN,
            ) { onTool(ReaderTool.PEN) }
            ReaderControl(
                stringResource(R.string.highlighter),
                R.drawable.ic_highlighter_24,
                selected = tool == ReaderTool.HIGHLIGHTER,
            ) { onTool(ReaderTool.HIGHLIGHTER) }
            ReaderControl(
                stringResource(R.string.eraser),
                R.drawable.ic_eraser_24,
                selected = tool == ReaderTool.ERASER,
            ) { onTool(ReaderTool.ERASER) }
            ReaderControl(stringResource(R.string.undo), R.drawable.ic_undo_24, onClick = onUndo)
            ReaderControl(stringResource(R.string.redo), R.drawable.ic_redo_24, onClick = onRedo)
            ReaderControl(stringResource(R.string.done), R.drawable.ic_done_24, onClick = onDone)
        }
    }
}

@Composable
private fun ReaderControl(
    label: String,
    @DrawableRes icon: Int,
    selected: Boolean? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        modifier = Modifier.size(48.dp)
            .background(
                if (selected == true) Color.White else Color.Transparent,
                RoundedCornerShape(2.dp),
            )
            .semantics {
                contentDescription = label
                selected?.let { this.selected = it }
            },
        enabled = enabled,
        onClick = onClick,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = when {
                !enabled -> Color.Gray
                selected == true -> Color.Black
                else -> Color.White
            },
        )
    }
}
