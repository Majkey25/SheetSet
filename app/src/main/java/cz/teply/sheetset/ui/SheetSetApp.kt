package cz.teply.sheetset.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cz.teply.sheetset.LibraryUiState
import cz.teply.sheetset.R
import cz.teply.sheetset.data.Score

data class SheetSetActions(
    val importPdfs: (List<Uri>) -> Unit = {},
    val createSetlist: (String) -> Unit = {},
    val openScore: (Score) -> Unit = {},
    val renameScore: (String, String) -> Unit = { _, _ -> },
    val deleteScore: (String) -> Unit = {},
    val renameSetlist: (String, String) -> Unit = { _, _ -> },
    val deleteSetlist: (String) -> Unit = {},
    val addScores: (String, List<String>) -> Unit = { _, _ -> },
    val removeScore: (String, Int) -> Unit = { _, _ -> },
    val moveScore: (String, Int, Int) -> Unit = { _, _, _ -> },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetSetApp(state: LibraryUiState, actions: SheetSetActions) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var activeSetlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var createSetlist by rememberSaveable { mutableStateOf(false) }
    var setlistName by rememberSaveable { mutableStateOf("") }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
        actions.importPdfs,
    )
    val newSetlistDescription = stringResource(R.string.new_setlist)
    val importDescription = stringResource(R.string.import_pdf)
    val errorMessage = stringResource(R.string.action_failed)
    val snackbarHost = remember { SnackbarHostState() }
    val activeSetlist = state.catalog.setlists.firstOrNull { it.id == activeSetlistId }

    LaunchedEffect(state.error) {
        if (state.error) snackbarHost.showSnackbar(errorMessage)
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
                TopAppBar(title = { Text(stringResource(R.string.app_name)) })
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text(stringResource(R.string.tab_pdf), fontWeight = FontWeight.SemiBold) },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text(stringResource(R.string.tab_setlists), fontWeight = FontWeight.SemiBold) },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.semantics {
                    contentDescription = if (selectedTab == 0) {
                        importDescription
                    } else {
                        newSetlistDescription
                    }
                },
                onClick = {
                    if (selectedTab == 0) importLauncher.launch(arrayOf("application/pdf"))
                    else createSetlist = true
                },
            ) {
                Text("+", fontSize = 28.sp)
            }
        },
    ) { padding ->
        if (selectedTab == 0) {
            LibraryScreen(
                scores = state.catalog.scores,
                onOpen = actions.openScore,
                onRename = actions.renameScore,
                onDelete = actions.deleteScore,
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

    if (createSetlist) {
        AlertDialog(
            onDismissRequest = { createSetlist = false },
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
                        setlistName = ""
                        createSetlist = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { createSetlist = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
