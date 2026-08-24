@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cz.teply.sheetset.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.teply.sheetset.R

enum class AppDestination { PDF, SETLISTS }

enum class WindowLayout {
    COMPACT,
    MEDIUM,
    EXPANDED;

    companion object {
        fun fromWidth(width: Dp): WindowLayout = when {
            width < 600.dp -> COMPACT
            width < 840.dp -> MEDIUM
            else -> EXPANDED
        }
    }
}

@Composable
fun SheetHeader(
    actionLabel: Int,
    actionDescription: Int,
    @DrawableRes actionIcon: Int,
    onMenu: () -> Unit,
    onAction: () -> Unit,
) {
    val menuDescription = stringResource(R.string.menu)
    val resolvedActionDescription = stringResource(actionDescription)
    Surface(color = MaterialTheme.colorScheme.surface) {
        androidx.compose.foundation.layout.Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = menuDescription },
                    onClick = onMenu,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu_24),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
                TextButton(
                    modifier = Modifier.height(48.dp).semantics {
                        contentDescription = resolvedActionDescription
                    },
                    onClick = onAction,
                ) {
                    Icon(
                        painter = painterResource(actionIcon),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(actionLabel),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
fun SheetNavigation(
    windowLayout: WindowLayout,
    destination: AppDestination,
    modifier: Modifier = Modifier,
    onDestination: (AppDestination) -> Unit,
) {
    val items = listOf(
        Triple(AppDestination.PDF, R.string.tab_pdf, R.drawable.ic_pdf_24),
        Triple(AppDestination.SETLISTS, R.string.tab_setlists, R.drawable.ic_setlist_24),
    )
    if (windowLayout == WindowLayout.COMPACT) {
        NavigationBar(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) {
            items.forEach { (item, label, icon) ->
                NavigationBarItem(
                    modifier = Modifier.semantics { role = Role.Tab },
                    selected = destination == item,
                    onClick = { onDestination(item) },
                    icon = { Icon(painterResource(icon), contentDescription = null) },
                    label = { Text(stringResource(label)) },
                )
            }
        }
    } else {
        val navigationDescription = stringResource(R.string.pdf_navigation)
        NavigationRail(
            modifier = modifier.fillMaxHeight().semantics {
                contentDescription = navigationDescription
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Spacer(Modifier.weight(1f))
            items.forEach { (item, label, icon) ->
                NavigationRailItem(
                    modifier = Modifier.semantics { role = Role.Tab },
                    selected = destination == item,
                    onClick = { onDestination(item) },
                    icon = { Icon(painterResource(icon), contentDescription = null) },
                    label = { Text(stringResource(label)) },
                    alwaysShowLabel = true,
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun ImportSourceSheet(
    onDismiss: () -> Unit,
    onFiles: () -> Unit,
    onScan: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.import_pdf),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.headlineSmall,
        )
        ListItem(
            modifier = Modifier.clickable {
                onDismiss()
                onFiles()
            },
            headlineContent = { Text(stringResource(R.string.files)) },
            supportingContent = { Text(stringResource(R.string.files_hint)) },
            leadingContent = {
                Icon(painterResource(R.drawable.ic_folder_open_24), contentDescription = null)
            },
        )
        ListItem(
            modifier = Modifier.clickable {
                onDismiss()
                onScan()
            },
            headlineContent = { Text(stringResource(R.string.scan_with_scanit)) },
            supportingContent = { Text(stringResource(R.string.scan_with_scanit_hint)) },
            leadingContent = {
                Icon(painterResource(R.drawable.ic_scan_document_24), contentDescription = null)
            },
        )
        Spacer(Modifier.navigationBarsPadding())
    }
}
