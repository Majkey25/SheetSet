package cz.teply.sheetset.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.teply.sheetset.LibraryUiState
import cz.teply.sheetset.R
import cz.teply.sheetset.data.Score
import cz.teply.sheetset.pdf.Stroke

data class SheetSetActions(
    val importPdfs: (List<Uri>) -> Unit = {},
    val createSetlist: (String) -> Unit = {},
    val openScore: (Score) -> Unit = {},
    val openSetlistScore: (String, Int) -> Unit = { _, _ -> },
    val closeReader: () -> Unit = {},
    val previousPage: () -> Unit = {},
    val nextPage: () -> Unit = {},
    val saveStrokes: (List<Stroke>) -> Unit = {},
    val exportPdf: (Uri) -> Unit = {},
    val renameScore: (String, String) -> Unit = { _, _ -> },
    val deleteScore: (String) -> Unit = {},
    val renameSetlist: (String, String) -> Unit = { _, _ -> },
    val deleteSetlist: (String) -> Unit = {},
    val addScores: (String, List<String>) -> Unit = { _, _ -> },
    val removeScore: (String, Int) -> Unit = { _, _ -> },
    val moveScore: (String, Int, Int) -> Unit = { _, _, _ -> },
)

@Composable
fun SheetSetApp(state: LibraryUiState, actions: SheetSetActions) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var activeSetlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var createSetlist by rememberSaveable { mutableStateOf(false) }
    var librarySearching by rememberSaveable { mutableStateOf(false) }
    var setlistName by rememberSaveable { mutableStateOf("") }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
        actions.importPdfs,
    )
    val errorMessage = stringResource(R.string.action_failed)
    val snackbarHost = remember { SnackbarHostState() }
    val activeSetlist = state.catalog.setlists.firstOrNull { it.id == activeSetlistId }
    val closeCreateDialog = {
        setlistName = ""
        createSetlist = false
    }

    LaunchedEffect(state.error) {
        if (state.error) snackbarHost.showSnackbar(errorMessage)
    }

    state.reader?.let { reader ->
        ReaderScreen(reader, actions)
        return
    }

    if (activeSetlist != null) {
        SetlistDetail(
            setlist = activeSetlist,
            scores = state.catalog.scores,
            actions = actions,
            onBack = { activeSetlistId = null },
        )
        return
    }

    Scaffold(
        topBar = {
            Column {
                AppHeader(
                    action = if (selectedTab == 0) R.string.import_pdf else R.string.new_setlist,
                    actionVisible = if (selectedTab == 0) {
                        state.catalog.scores.isNotEmpty()
                    } else {
                        state.catalog.setlists.isNotEmpty()
                    },
                    onAction = {
                        if (selectedTab == 0) importLauncher.launch(arrayOf("application/pdf"))
                        else createSetlist = true
                    },
                    secondaryAction = if (selectedTab == 0 && state.catalog.scores.isNotEmpty()) {
                        R.string.search_pdfs
                    } else {
                        null
                    },
                    secondaryGlyph = if (librarySearching) "×" else "⌕",
                    onSecondaryAction = { librarySearching = !librarySearching },
                )
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            SheetTabs(selectedTab) { tab ->
                selectedTab = tab
                if (tab != 0) librarySearching = false
            }
        },
    ) { padding ->
        if (selectedTab == 0) {
            LibraryScreen(
                scores = state.catalog.scores,
                onOpen = actions.openScore,
                onRename = actions.renameScore,
                onDelete = actions.deleteScore,
                onImport = { importLauncher.launch(arrayOf("application/pdf")) },
                searching = librarySearching,
                modifier = Modifier.padding(padding),
            )
        } else {
            SetlistsScreen(
                setlists = state.catalog.setlists,
                onOpen = { activeSetlistId = it.id },
                onRename = actions.renameSetlist,
                onDelete = actions.deleteSetlist,
                onCreate = { createSetlist = true },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (createSetlist) {
        AlertDialog(
            onDismissRequest = closeCreateDialog,
            title = { Text(stringResource(R.string.new_setlist)) },
            text = {
                OutlinedTextField(
                    value = setlistName,
                    onValueChange = { setlistName = it },
                    label = { Text(stringResource(R.string.setlist_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = setlistName.isNotBlank(),
                    onClick = {
                        actions.createSetlist(setlistName)
                        closeCreateDialog()
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = closeCreateDialog) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AppHeader(
    action: Int,
    actionVisible: Boolean,
    onAction: () -> Unit,
    secondaryAction: Int? = null,
    secondaryGlyph: String = "",
    onSecondaryAction: () -> Unit = {},
) {
    val actionDescription = stringResource(action)
    val secondaryDescription = secondaryAction?.let { stringResource(it) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(68.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.app_name),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (secondaryDescription != null) {
                    IconButton(
                        modifier = Modifier.semantics {
                            contentDescription = secondaryDescription
                        },
                        onClick = onSecondaryAction,
                    ) { Text(secondaryGlyph) }
                }
                if (actionVisible) {
                    Button(
                        modifier = Modifier.semantics { contentDescription = actionDescription },
                        shape = MaterialTheme.shapes.small,
                        onClick = onAction,
                    ) {
                        Text(stringResource(action))
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun SheetTabs(selected: Int, onSelect: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(60.dp).selectableGroup(),
            ) {
                listOf(R.string.tab_pdf, R.string.tab_setlists).forEachIndexed { index, label ->
                    val active = selected == index
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .selectable(
                                selected = active,
                                role = Role.Tab,
                                onClick = { onSelect(index) },
                            )
                            .semantics(mergeDescendants = true) {},
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(3.dp).background(
                                if (active) Color.Black else Color.Transparent,
                            ),
                        )
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(label),
                                color = if (active) Color.Black else Color.DarkGray,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}
