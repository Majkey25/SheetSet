package cz.teply.sheetset.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.teply.sheetset.R
import cz.teply.sheetset.data.Score

@Composable
fun LibraryScreen(
    scores: List<Score>,
    onOpen: (Score) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var menuScoreId by remember { mutableStateOf<String?>(null) }
    var renameScore by remember { mutableStateOf<Score?>(null) }
    var deleteScore by remember { mutableStateOf<Score?>(null) }
    val visibleScores = remember(scores, query) {
        scores.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }

    if (scores.isEmpty()) {
        AppEmptyState(R.string.no_pdfs, R.string.import_hint, modifier)
        return
    }
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.search_pdfs)) },
            singleLine = true,
        )
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(visibleScores, key = Score::id) { score ->
                ScoreRow(
                    score = score,
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
        Box(
            Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("PDF", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
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
internal fun AppEmptyState(title: Int, message: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(message), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
