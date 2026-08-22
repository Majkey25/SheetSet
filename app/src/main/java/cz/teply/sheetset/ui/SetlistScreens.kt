package cz.teply.sheetset.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.teply.sheetset.R
import cz.teply.sheetset.data.Score
import cz.teply.sheetset.data.Setlist

@Composable
fun SetlistsScreen(
    setlists: List<Setlist>,
    onOpen: (Setlist) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuSetlistId by remember { mutableStateOf<String?>(null) }
    var renameSetlist by remember { mutableStateOf<Setlist?>(null) }
    var deleteSetlist by remember { mutableStateOf<Setlist?>(null) }
    if (setlists.isEmpty()) {
        AppEmptyState(
            R.string.no_setlists,
            R.string.setlist_hint,
            modifier,
        )
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        itemsIndexed(setlists, key = { _, setlist -> setlist.id }) { index, setlist ->
            SetlistRow(
                setlist = setlist,
                index = index,
                expanded = menuSetlistId == setlist.id,
                onOpen = onOpen,
                onMore = { menuSetlistId = setlist.id },
                onDismissMenu = { menuSetlistId = null },
                onRename = { menuSetlistId = null; renameSetlist = setlist },
                onDelete = { menuSetlistId = null; deleteSetlist = setlist },
            )
            HorizontalDivider()
        }
    }
    renameSetlist?.let { setlist ->
        NameDialog(
            title = stringResource(R.string.rename_setlist),
            label = stringResource(R.string.setlist_name),
            initialValue = setlist.name,
            onDismiss = { renameSetlist = null },
            onSave = { onRename(setlist.id, it); renameSetlist = null },
        )
    }
    deleteSetlist?.let { setlist ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_setlist),
            message = stringResource(R.string.delete_setlist_message),
            onDismiss = { deleteSetlist = null },
            onConfirm = { onDelete(setlist.id); deleteSetlist = null },
        )
    }
}

@Composable
private fun SetlistRow(
    setlist: Setlist,
    index: Int,
    expanded: Boolean,
    onOpen: (Setlist) -> Unit,
    onMore: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val more = stringResource(R.string.more_options)
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(setlist) }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (index + 1).toString().padStart(2, '0'),
            modifier = Modifier.width(44.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(setlist.name, style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(
                    R.plurals.score_count,
                    setlist.scoreIds.size,
                    setlist.scoreIds.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(
                modifier = Modifier.semantics { contentDescription = more },
                onClick = onMore,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert_24),
                    contentDescription = null,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    onClick = onRename,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
fun SetlistDetail(
    setlist: Setlist,
    scores: List<Score>,
    actions: SheetSetActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    var addDialog by remember { mutableStateOf(false) }
    var editing by rememberSaveable(setlist.id) { mutableStateOf(false) }
    val scoreById = remember(scores) { scores.associateBy(Score::id) }
    val detail: @Composable (Modifier) -> Unit = { contentModifier ->
        Column(contentModifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    shape = MaterialTheme.shapes.small,
                    onClick = { addDialog = true },
                ) {
                    Text(stringResource(R.string.add_pdfs))
                }
                if (setlist.scoreIds.isNotEmpty()) {
                    TextButton(onClick = { editing = !editing }) {
                        Text(stringResource(if (editing) R.string.done else R.string.edit_order))
                    }
                }
            }
            HorizontalDivider()
            if (setlist.scoreIds.isEmpty()) {
                AppEmptyState(
                    R.string.no_scores_in_setlist,
                    if (scores.isEmpty()) R.string.import_first else R.string.choose_pdfs,
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    itemsIndexed(
                        setlist.scoreIds,
                        key = { index, id -> "$id-$index" },
                    ) { index, id ->
                        scoreById[id]?.let { score ->
                            SetlistScoreRow(setlist, score, index, editing, actions)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
    if (embedded) {
        Column(modifier.fillMaxSize()) {
            SetlistHeader(setlist.name, onBack, statusBarPadding = false)
            HorizontalDivider()
            detail(Modifier.weight(1f))
        }
    } else {
        Scaffold(
            modifier = modifier,
            topBar = { SetlistHeader(setlist.name, onBack) },
        ) { padding ->
            detail(Modifier.padding(padding))
        }
    }
    if (addDialog) {
        AddScoresDialog(
            scores = scores,
            onDismiss = { addDialog = false },
            onAdd = { selected ->
                actions.addScores(setlist.id, selected)
                addDialog = false
            },
        )
    }
}

@Composable
private fun SetlistHeader(
    title: String,
    onBack: () -> Unit,
    statusBarPadding: Boolean = true,
) {
    val back = stringResource(R.string.back)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (statusBarPadding) Modifier.statusBarsPadding() else Modifier)
                .height(68.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                modifier = Modifier.semantics { contentDescription = back },
                onClick = onBack,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left_24),
                    contentDescription = null,
                )
            }
            Text(
                title,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

@Composable
private fun SetlistScoreRow(
    setlist: Setlist,
    score: Score,
    index: Int,
    editing: Boolean,
    actions: SheetSetActions,
) {
    val up = stringResource(R.string.move_up)
    val down = stringResource(R.string.move_down)
    val remove = stringResource(R.string.remove)
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !editing) {
            actions.openSetlistScore(setlist.id, index)
        }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (index + 1).toString().padStart(2, '0'),
            modifier = Modifier.width(40.dp),
            fontWeight = FontWeight.Bold,
        )
        Text(score.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (editing) {
            if (index > 0) {
                IconButton(
                    modifier = Modifier.semantics { contentDescription = up },
                    onClick = { actions.moveScore(setlist.id, index, index - 1) },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_up_24),
                        contentDescription = null,
                    )
                }
            }
            if (index < setlist.scoreIds.lastIndex) {
                IconButton(
                    modifier = Modifier.semantics { contentDescription = down },
                    onClick = { actions.moveScore(setlist.id, index, index + 1) },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_down_24),
                        contentDescription = null,
                    )
                }
            }
            IconButton(
                modifier = Modifier.semantics { contentDescription = remove },
                onClick = { actions.removeScore(setlist.id, index) },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_24),
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun AddScoresDialog(
    scores: List<Score>,
    onDismiss: () -> Unit,
    onAdd: (List<String>) -> Unit,
) {
    val selected = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_pdfs)) },
        text = {
            if (scores.isEmpty()) {
                Text(stringResource(R.string.import_first))
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    itemsIndexed(scores, key = { _, score -> score.id }) { _, score ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (score.id in selected) selected.remove(score.id)
                                else selected.add(score.id)
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = score.id in selected,
                                onCheckedChange = { checked ->
                                    if (checked) selected.add(score.id) else selected.remove(score.id)
                                },
                            )
                            Text(score.title)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty(), onClick = { onAdd(selected.toList()) }) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
