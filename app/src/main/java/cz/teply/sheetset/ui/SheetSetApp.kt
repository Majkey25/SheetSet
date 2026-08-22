package cz.teply.sheetset.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
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
    var importOptions by rememberSaveable { mutableStateOf(false) }
    var navigationMenu by rememberSaveable { mutableStateOf(false) }
    var setlistName by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
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
                SheetHeader(
                    actionLabel = if (selectedTab == 0) R.string.import_pdf else R.string.create,
                    actionDescription = if (selectedTab == 0) R.string.import_pdf else R.string.create,
                    actionIcon = if (selectedTab == 0) R.drawable.ic_upload_file_24 else R.drawable.ic_add_24,
                    onMenu = { navigationMenu = true },
                    onAction = {
                        if (selectedTab == 0) importOptions = true
                        else createSetlist = true
                    },
                )
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            SheetNavigation(
                destination = if (selectedTab == 0) AppDestination.PDF else AppDestination.SETLISTS,
            ) { destination ->
                selectedTab = if (destination == AppDestination.PDF) 0 else 1
                if (destination != AppDestination.PDF) librarySearching = false
            }
        },
    ) { padding ->
        if (selectedTab == 0) {
            LibraryScreen(
                scores = state.catalog.scores,
                onOpen = actions.openScore,
                onRename = actions.renameScore,
                onDelete = actions.deleteScore,
                searching = librarySearching,
                onSearchingChange = { librarySearching = it },
                modifier = Modifier.padding(padding),
            )
        } else {
            SetlistsScreen(
                setlists = state.catalog.setlists,
                onOpen = { activeSetlistId = it.id },
                onRename = actions.renameSetlist,
                onDelete = actions.deleteSetlist,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (navigationMenu) {
        NavigationMenuSheet(
            destination = if (selectedTab == 0) AppDestination.PDF else AppDestination.SETLISTS,
            onDismiss = { navigationMenu = false },
            onDestination = { destination ->
                selectedTab = if (destination == AppDestination.PDF) 0 else 1
                if (destination != AppDestination.PDF) librarySearching = false
            },
        )
    }

    if (importOptions) {
        ImportSourceSheet(
            onDismiss = { importOptions = false },
            onFiles = { importLauncher.launch(arrayOf("application/pdf")) },
            onScan = { openScanIt(context) },
        )
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

private const val SCAN_IT_PACKAGE = "com.majkeylab.scanit"

private fun openScanIt(context: Context) {
    val market = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$SCAN_IT_PACKAGE"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$SCAN_IT_PACKAGE"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(market) }
        .getOrElse { context.startActivity(web) }
}
