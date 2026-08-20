package cz.teply.sheetset.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cz.teply.sheetset.R
import cz.teply.sheetset.data.Score

@Composable
fun LibraryScreen(
    scores: List<Score>,
    onOpen: (Score) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onImport: () -> Unit,
    searching: Boolean,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var menuScoreId by remember { mutableStateOf<String?>(null) }
    var renameScore by remember { mutableStateOf<Score?>(null) }
    var deleteScore by remember { mutableStateOf<Score?>(null) }
    val visibleScores = remember(scores, query) {
        scores.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }

    LaunchedEffect(searching) {
        if (!searching) query = ""
    }

    if (scores.isEmpty()) {
        AppEmptyState(
            R.string.no_pdfs,
            R.string.import_hint,
            modifier,
            action = R.string.import_pdf,
            onAction = onImport,
        )
        return
    }
    Column(modifier.fillMaxSize()) {
        if (searching) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_pdfs)) },
                singleLine = true,
            )
        }
        if (visibleScores.isEmpty()) {
            AppEmptyState(R.string.no_search_results, R.string.search_pdfs)
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                itemsIndexed(visibleScores, key = { _, score -> score.id }) { index, score ->
                    ScoreRow(
                        score = score,
                        index = index,
                        expanded = menuScoreId == score.id,
                        onOpen = onOpen,
                        onMore = { menuScoreId = score.id },
                        onDismissMenu = { menuScoreId = null },
                        onRename = { menuScoreId = null; renameScore = score },
                        onDelete = { menuScoreId = null; deleteScore = score },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    renameScore?.let { score ->
        NameDialog(
            title = stringResource(R.string.rename_score),
            label = stringResource(R.string.name),
            initialValue = score.title,
            onDismiss = { renameScore = null },
            onSave = { onRename(score.id, it); renameScore = null },
        )
    }
    deleteScore?.let { score ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_score),
            message = stringResource(R.string.delete_score_message),
            onDismiss = { deleteScore = null },
            onConfirm = { onDelete(score.id); deleteScore = null },
        )
    }
}

@Composable
private fun ScoreRow(
    score: Score,
    index: Int,
    expanded: Boolean,
    onOpen: (Score) -> Unit,
    onMore: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val more = stringResource(R.string.more_options)
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(score) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            (index + 1).toString().padStart(2, '0'),
            modifier = Modifier.width(32.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(score.title, style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(R.plurals.page_count, score.pageCount, score.pageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(
                modifier = Modifier.semantics { contentDescription = more },
                onClick = onMore,
            ) { Text("⋮") }
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
internal fun AppEmptyState(
    title: Int,
    message: Int,
    modifier: Modifier = Modifier,
    action: Int? = null,
    onAction: () -> Unit = {},
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp)
                .padding(horizontal = 24.dp, vertical = 72.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(width = 44.dp, height = 58.dp)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.width(24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(3) { HorizontalDivider() }
                }
            }
            Spacer(Modifier.size(4.dp))
            Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (action != null) {
                val actionDescription = stringResource(action)
                Button(
                    modifier = Modifier.semantics { contentDescription = actionDescription },
                    shape = MaterialTheme.shapes.small,
                    onClick = onAction,
                ) { Text(actionDescription) }
            }
        }
    }
}

@Composable
internal fun NameDialog(
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onSave(value) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
internal fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
