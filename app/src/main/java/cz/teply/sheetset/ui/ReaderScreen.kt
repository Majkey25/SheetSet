package cz.teply.sheetset.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
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
import cz.teply.sheetset.pdf.AnnotationColor
import cz.teply.sheetset.pdf.AnnotationHistory
import cz.teply.sheetset.pdf.InkKind
import cz.teply.sheetset.pdf.MarkupAnnotation
import cz.teply.sheetset.pdf.MAX_TEXT_LENGTH
import cz.teply.sheetset.pdf.PageAnnotation
import cz.teply.sheetset.pdf.PdfPageView
import cz.teply.sheetset.pdf.ReaderTool
import cz.teply.sheetset.pdf.TextBoxAnnotation
import cz.teply.sheetset.pdf.manualMarkup
import cz.teply.sheetset.settings.AppSettings
import cz.teply.sheetset.settings.HighlightStrength
import cz.teply.sheetset.settings.ReaderDefaultTool
import cz.teply.sheetset.settings.ToolSize
import java.util.UUID
import kotlinx.coroutines.delay

@Composable
fun ReaderScreen(
    reader: ReaderUiState,
    settings: AppSettings,
    windowLayout: WindowLayout,
    actions: SheetSetActions,
) {
    var tool by remember(reader.score.id) { mutableStateOf(ReaderTool.VIEW) }
    var controlsVisible by remember { mutableStateOf(true) }
    var autoHideRequest by remember { mutableIntStateOf(0) }
    var selectedAnnotationId by remember(reader.score.id, reader.pageIndex) {
        mutableStateOf<String?>(null)
    }
    var color by remember { mutableStateOf(AnnotationColor.BLACK) }
    var textBounds by remember { mutableStateOf<cz.teply.sheetset.pdf.NormalizedRect?>(null) }
    var textDraft by remember { mutableStateOf("") }
    var history by remember(reader.score.id, reader.pageIndex) {
        mutableStateOf(AnnotationHistory(reader.annotations.pages[reader.pageIndex].orEmpty()))
    }
    val platformView = LocalView.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> uri?.let(actions.exportPdf) }

    DisposableEffect(platformView, settings.keepScreenAwake) {
        val previous = platformView.keepScreenOn
        platformView.keepScreenOn = settings.keepScreenAwake
        onDispose { platformView.keepScreenOn = previous }
    }
    LaunchedEffect(reader.score.id, reader.pageIndex) {
        selectedAnnotationId = null
    }
    LaunchedEffect(autoHideRequest, settings.autoHideControls) {
        if (autoHideRequest > 0 && settings.autoHideControls) {
            delay(1_500)
            if (tool == ReaderTool.VIEW && controlsVisible) controlsVisible = false
        }
    }

    fun updateHistory(next: AnnotationHistory) {
        history = next
        actions.saveAnnotations(next.annotations)
    }

    fun addAnnotation(annotation: PageAnnotation) {
        updateHistory(history.add(annotation))
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
                view.selectedAnnotationId = selectedAnnotationId
                view.annotationColor = color
                view.penWidth = settings.penWidth.normalizedWidth()
                view.shapeWidth = settings.penWidth.normalizedWidth()
                view.textSize = settings.textSize
                view.highlighterAlpha = settings.highlighterStrength.alpha()
                view.pageFit = settings.pageFit
                view.pageTurnTaps = settings.pageTurnTaps
                view.pageTurnSwipes = settings.pageTurnSwipes
                view.onPreviousPage = actions.previousPage
                view.onNextPage = actions.nextPage
                view.onPageClick = {
                    if (tool == ReaderTool.VIEW) {
                        controlsVisible = !controlsVisible
                        if (controlsVisible && settings.autoHideControls) autoHideRequest++
                    }
                }
                view.onSelectAnnotation = { selectedAnnotationId = it }
                view.onAddAnnotation = ::addAnnotation
                view.onUpdateAnnotation = { updateHistory(history.update(it)) }
                view.onDeleteAnnotation = { id ->
                    if (selectedAnnotationId == id) selectedAnnotationId = null
                    updateHistory(history.delete(id))
                }
                view.onRequestText = { bounds ->
                    textBounds = bounds
                    textDraft = ""
                }
                view.onRequestMarkup = { kind, start, end ->
                    addAnnotation(
                        MarkupAnnotation(
                            id = UUID.randomUUID().toString(),
                            kind = kind,
                            bounds = manualMarkup(start, end),
                            color = color,
                        ),
                    )
                }
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
                        reader.score.title.replace(Regex("[\\/:*?\"<>|]"), "_")
                            .take(100) + "-annotated.pdf",
                    )
                },
            )
        }
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(
                if (tool != ReaderTool.VIEW && windowLayout == WindowLayout.EXPANDED) {
                    Alignment.CenterEnd
                } else {
                    Alignment.BottomCenter
                },
            ),
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(140)),
        ) {
            if (tool == ReaderTool.VIEW) {
                ReaderNavigationBar(reader, actions) {
                    val initialTool = settings.defaultTool.editorTool()
                    tool = initialTool
                    if (initialTool == ReaderTool.HIGHLIGHTER && color == AnnotationColor.BLACK) {
                        color = AnnotationColor.YELLOW
                    }
                    controlsVisible = true
                }
            } else {
                AnnotationPalette(
                    tool = tool,
                    color = color,
                    selectedAnnotationId = selectedAnnotationId,
                    expandedLayout = windowLayout == WindowLayout.EXPANDED,
                    onTool = {
                        tool = it
                        if (it == ReaderTool.HIGHLIGHTER && color == AnnotationColor.BLACK) {
                            color = AnnotationColor.YELLOW
                        }
                        selectedAnnotationId = null
                    },
                    onColor = { color = it },
                    onDelete = {
                        selectedAnnotationId?.let { id ->
                            selectedAnnotationId = null
                            updateHistory(history.delete(id))
                        }
                    },
                    onUndo = {
                        selectedAnnotationId = null
                        updateHistory(history.undo())
                    },
                    onRedo = {
                        selectedAnnotationId = null
                        updateHistory(history.redo())
                    },
                    onDone = {
                        selectedAnnotationId = null
                        tool = ReaderTool.VIEW
                        controlsVisible = true
                    },
                )
            }
        }
    }

    textBounds?.let { bounds ->
        AlertDialog(
            onDismissRequest = { textBounds = null },
            title = { Text(stringResource(R.string.text_box)) },
            text = {
                OutlinedTextField(
                    value = textDraft,
                    onValueChange = { textDraft = it.take(MAX_TEXT_LENGTH) },
                    label = { Text(stringResource(R.string.text_box)) },
                    minLines = 3,
                    maxLines = 8,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = textDraft.isNotBlank(),
                    onClick = {
                        addAnnotation(
                            TextBoxAnnotation(
                                id = UUID.randomUUID().toString(),
                                bounds = bounds,
                                text = textDraft.trim(),
                                size = settings.textSize,
                                color = color,
                            ),
                        )
                        textBounds = null
                        textDraft = ""
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { textBounds = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
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
            ReaderControl(
                stringResource(R.string.annotate),
                R.drawable.ic_edit_24,
                onClick = onAnnotate,
            )
        }
    }
}

private data class ToolDefinition(
    val tool: ReaderTool,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
)

private val editorTools = listOf(
    ToolDefinition(ReaderTool.SELECT, R.string.select, R.drawable.ic_select_24),
    ToolDefinition(ReaderTool.PEN, R.string.pen, R.drawable.ic_edit_24),
    ToolDefinition(ReaderTool.HIGHLIGHTER, R.string.highlighter, R.drawable.ic_highlighter_24),
    ToolDefinition(ReaderTool.UNDERLINE, R.string.underline, R.drawable.ic_underline_24),
    ToolDefinition(ReaderTool.STRIKE_THROUGH, R.string.strike_through, R.drawable.ic_strikethrough_24),
    ToolDefinition(ReaderTool.TEXT_BOX, R.string.text_box, R.drawable.ic_text_fields_24),
    ToolDefinition(ReaderTool.LINE, R.string.line, R.drawable.ic_line_24),
    ToolDefinition(ReaderTool.ARROW, R.string.arrow, R.drawable.ic_arrow_forward_24),
    ToolDefinition(ReaderTool.RECTANGLE, R.string.rectangle, R.drawable.ic_rectangle_24),
    ToolDefinition(ReaderTool.ELLIPSE, R.string.ellipse, R.drawable.ic_ellipse_24),
    ToolDefinition(ReaderTool.ERASER, R.string.eraser, R.drawable.ic_eraser_24),
)

@Composable
private fun AnnotationPalette(
    tool: ReaderTool,
    color: AnnotationColor,
    selectedAnnotationId: String?,
    expandedLayout: Boolean,
    onTool: (ReaderTool) -> Unit,
    onColor: (AnnotationColor) -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(color = Color.Black.copy(alpha = 0.92f), contentColor = Color.White) {
        if (expandedLayout) {
            Column(
                Modifier.fillMaxHeight().width(64.dp).verticalScroll(rememberScrollState())
                    .statusBarsPadding().navigationBarsPadding().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ToolControls(
                    tool,
                    color,
                    selectedAnnotationId,
                    onTool,
                    onColor,
                    onDelete,
                    onUndo,
                    onRedo,
                    onDone,
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .navigationBarsPadding().height(60.dp).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolControls(
                    tool,
                    color,
                    selectedAnnotationId,
                    onTool,
                    onColor,
                    onDelete,
                    onUndo,
                    onRedo,
                    onDone,
                )
            }
        }
    }
}

@Composable
private fun ToolControls(
    tool: ReaderTool,
    color: AnnotationColor,
    selectedAnnotationId: String?,
    onTool: (ReaderTool) -> Unit,
    onColor: (AnnotationColor) -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit,
) {
    editorTools.forEach { definition ->
        ReaderControl(
            label = stringResource(definition.label),
            icon = definition.icon,
            selected = tool == definition.tool,
        ) {
            onTool(definition.tool)
        }
    }
    ColorControl(color, onColor)
    if (selectedAnnotationId != null) {
        ReaderControl(
            stringResource(R.string.delete_annotation),
            R.drawable.ic_delete_24,
            onClick = onDelete,
        )
    }
    ReaderControl(stringResource(R.string.undo), R.drawable.ic_undo_24, onClick = onUndo)
    ReaderControl(stringResource(R.string.redo), R.drawable.ic_redo_24, onClick = onRedo)
    ReaderControl(stringResource(R.string.done), R.drawable.ic_done_24, onClick = onDone)
}

@Composable
private fun ColorControl(color: AnnotationColor, onColor: (AnnotationColor) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(R.string.color)
    Box {
        IconButton(
            modifier = Modifier.size(48.dp).semantics { contentDescription = label },
            onClick = { expanded = true },
        ) {
            Canvas(Modifier.size(24.dp)) {
                drawCircle(color.composeColor())
                drawCircle(Color.White, style = Stroke(width = 2.dp.toPx()))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            colorOptions().forEach { (option, name) ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Canvas(Modifier.size(20.dp)) {
                                drawCircle(option.composeColor())
                                drawCircle(Color.DarkGray, style = Stroke(width = 1.dp.toPx()))
                            }
                            Text(stringResource(name))
                        }
                    },
                    onClick = {
                        onColor(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun colorOptions(): List<Pair<AnnotationColor, Int>> = listOf(
    AnnotationColor.BLACK to R.string.color_black,
    AnnotationColor.RED to R.string.color_red,
    AnnotationColor.ORANGE to R.string.color_orange,
    AnnotationColor.YELLOW to R.string.color_yellow,
    AnnotationColor.GREEN to R.string.color_green,
    AnnotationColor.BLUE to R.string.color_blue,
    AnnotationColor.PURPLE to R.string.color_purple,
    AnnotationColor.PINK to R.string.color_pink,
)

private fun AnnotationColor.composeColor(): Color = when (this) {
    AnnotationColor.BLACK -> Color(0xFF111111)
    AnnotationColor.RED -> Color(0xFFD32F2F)
    AnnotationColor.ORANGE -> Color(0xFFF57C00)
    AnnotationColor.YELLOW -> Color(0xFFFBC02D)
    AnnotationColor.GREEN -> Color(0xFF388E3C)
    AnnotationColor.BLUE -> Color(0xFF1976D2)
    AnnotationColor.PURPLE -> Color(0xFF7B1FA2)
    AnnotationColor.PINK -> Color(0xFFC2185B)
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

private fun ReaderDefaultTool.editorTool(): ReaderTool = when (this) {
    ReaderDefaultTool.VIEW -> ReaderTool.SELECT
    ReaderDefaultTool.PEN -> ReaderTool.PEN
    ReaderDefaultTool.HIGHLIGHTER -> ReaderTool.HIGHLIGHTER
}

private fun ToolSize.normalizedWidth(): Float = when (this) {
    ToolSize.THIN -> 0.0025f
    ToolSize.MEDIUM -> 0.004f
    ToolSize.THICK -> 0.007f
}

private fun HighlightStrength.alpha(): Int = when (this) {
    HighlightStrength.LIGHT -> 70
    HighlightStrength.MEDIUM -> 105
    HighlightStrength.STRONG -> 150
}
